package com.example.localplayer

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File

/**
 * Компоненты трека: четыре непересекающихся стема Open-Unmix, которые в сумме дают оригинал. Пользователь отмечает любые
 * из них, и они сводятся в ОДИН трек: «всё, кроме баса» — полный трек без баса, «всё, кроме вокала» — инструментал.
 */
enum class Stem(@StringRes val label: Int, val model: StemModel, val tag: String) {
    VOCALS(R.string.stem_vocals, StemModel.VOCALS, "Vocals"),
    DRUMS(R.string.stem_drums, StemModel.DRUMS, "Drums"),
    BASS(R.string.stem_bass, StemModel.BASS, "Bass"),
    OTHER(R.string.stem_other, StemModel.OTHER, "Other");

    companion object {
        /** Быстрый выбор «инструментал»: всё, кроме вокала. */
        val INSTRUMENTAL = setOf(DRUMS, BASS, OTHER)

        /** Имя результата по набору: «No Bass», «Vocals + Drums»… Не зависит от языка, чтобы повтор перезаписывал тот же файл. */
        fun fileTag(selected: Set<Stem>): String {
            val missing = entries.filter { it !in selected }
            return when {
                missing.isEmpty() -> "Full mix"
                missing.size == 1 -> "No ${missing[0].tag}"
                else -> entries.filter { it in selected }.joinToString(" + ") { it.tag }
            }
        }
    }
}

/** Итог работы (диалог). [file] — имя созданного файла. */
class StemNotice(
    val kind: Kind,
    val detail: String = "",
    val file: String = "",
    val folder: String = "",
) {
    enum class Kind { DONE, CANCELLED, ERROR, NEED_WRITE, IMPORT_REJECTED }

    fun title(ctx: Context): String {
        val c = AppLocale.localized(ctx)
        return c.getString(
            when (kind) {
                Kind.DONE -> R.string.stems_done_title
                Kind.CANCELLED -> R.string.stems_cancelled_title
                Kind.ERROR, Kind.NEED_WRITE, Kind.IMPORT_REJECTED -> R.string.stems_error_title
            },
        )
    }

    fun message(ctx: Context): String {
        val c = AppLocale.localized(ctx)
        return when (kind) {
            Kind.DONE -> c.getString(R.string.stems_done_body, folder, file)
            Kind.CANCELLED -> c.getString(R.string.stems_cancelled_body)
            Kind.ERROR -> c.getString(R.string.cloud_error, detail)
            Kind.NEED_WRITE -> c.getString(R.string.stems_need_write)
            Kind.IMPORT_REJECTED -> c.getString(R.string.stems_import_bad, detail)
        }
    }
}

/**
 * Создание стемов и управление моделями. Состояние наблюдается из Compose (как у [CloudSync]); сама работа идёт
 * в сервисе переднего плана ([StemService]), чтобы не обрываться, когда приложение уходит в фон.
 */
object Stems {

    enum class Phase { IDLE, MODELS, DECODING, SEPARATING }

    /** Папка внутри папки с музыкой, куда складываются результаты: «Stems/<исполнитель - название>/…». */
    const val FOLDER = "Stems"

    private sealed interface Request {
        class Separate(val track: Track, val stems: Set<Stem>, val tree: Uri) : Request
        class Download(val models: List<StemModel>) : Request
        class Import(val uris: List<Uri>) : Request
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * ИИ заблокирован в режимах энергосбережения «Средний» и «Интенсивный»: разбор и загрузка моделей сильно нагружают
     * процессор и батарею. Читается в Compose (перерисовка при смене режима) и в обычном коде.
     */
    val blocked: Boolean get() = PowerSave.mode != PowerMode.OFF

    init {
        // Включили энергосбережение, пока идёт работа: останавливаем её (недописанные файлы удаляются)
        PowerSave.addListener { if (blocked && busy) cancel() }
    }
    private var job: Job? = null
    private var pending: Request? = null

    var phase by mutableStateOf(Phase.IDLE)
        private set
    val busy: Boolean get() = phase != Phase.IDLE

    /** Общий прогресс текущей работы, 0..1. */
    var progress by mutableFloatStateOf(0f)
        private set

    /** Трек, который сейчас разбирается. */
    var track by mutableStateOf<Track?>(null)
        private set

    /** Какая сеть работает сейчас; полностью готово блоков [chunkDone] из [chunkTotal]. */
    var currentModel by mutableStateOf<StemModel?>(null)
        private set
    var chunkDone by mutableIntStateOf(0)
        private set
    var chunkTotal by mutableIntStateOf(0)
        private set

    /** Сколько секунд осталось (оценка по скорости уже сделанного); -1 — пока неизвестно. */
    var etaSeconds by mutableLongStateOf(-1L)
        private set

    /** Окно с прогрессом открыто. Если закрыто, поверх нижней панели показывается тонкая полоса. */
    var showProgress by mutableStateOf(false)

    /** Что показать пользователю (диалог). Сбрасывается интерфейсом. */
    var notice by mutableStateOf<StemNotice?>(null)

    /** Модели, которые уже лежат на устройстве. */
    var installed by mutableStateOf<Set<StemModel>>(emptySet())
        private set

    /** Приложение на экране: по окончании отдельное уведомление не нужно. */
    @Volatile
    var appVisible = false

    /** Вызывается, когда появились новые файлы: плеер пересканирует папку. */
    @Volatile
    var onCreated: (() -> Unit)? = null

    private const val DECODE_SHARE = 0.04f
    private var lastTick = -1f

    // ---------- Модели ----------

    fun refreshModels(ctx: Context) {
        val app = ctx.applicationContext
        installed = StemModel.entries.filter { StemModelStore.isInstalled(app, it) }.toSet()
    }

    /**
     * Каких моделей не хватает. Нужны всегда все четыре: маски нормируются между сетями (v² / Σv²), поэтому выбранные
     * стемы в сумме дают точный оригинал, и ни один не получается тише, чем должен.
     */
    fun missingFor(stems: Set<Stem>): List<StemModel> =
        if (stems.isEmpty()) emptyList() else StemModel.entries.filter { it !in installed }

    fun deleteModels(ctx: Context) {
        if (busy) return
        StemModelStore.deleteAll(ctx.applicationContext)
        refreshModels(ctx)
    }

    fun downloadModels(ctx: Context, models: List<StemModel>) {
        if (blocked || busy || models.isEmpty()) return
        begin(ctx, Request.Download(models), Phase.MODELS)
    }

    fun importModels(ctx: Context, uris: List<Uri>) {
        if (blocked || busy || uris.isEmpty()) return
        begin(ctx, Request.Import(uris), Phase.MODELS)
    }

    // ---------- Выбор стемов ----------

    fun loadSelection(ctx: Context): Set<Stem> {
        val saved = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("stem_selection", null)
            ?: return Stem.INSTRUMENTAL
        return saved.split(',').mapNotNull { n -> Stem.entries.firstOrNull { it.name == n } }.toSet().ifEmpty { Stem.INSTRUMENTAL }
    }

    fun saveSelection(ctx: Context, stems: Set<Stem>) {
        ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("stem_selection", stems.joinToString(",") { it.name }).apply()
    }

    // ---------- Запуск ----------

    /** Запускает разбор трека. false, если запуск невозможен (причина показана в [notice] или не хватает моделей). */
    fun start(ctx: Context, track: Track, stems: Set<Stem>, tree: Uri): Boolean {
        if (blocked || busy || stems.isEmpty()) return false
        val app = ctx.applicationContext
        val canWrite = app.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        if (!canWrite) {
            notice = StemNotice(StemNotice.Kind.NEED_WRITE)
            return false
        }
        refreshModels(app)
        if (missingFor(stems).isNotEmpty()) return false
        this.track = track
        return begin(app, Request.Separate(track, stems, tree), Phase.DECODING)
    }

    private fun begin(ctx: Context, req: Request, first: Phase): Boolean {
        pending = req
        notice = null
        progress = 0f
        etaSeconds = -1L
        chunkDone = 0
        chunkTotal = 0
        currentModel = null
        phase = first
        return try {
            StemService.start(ctx.applicationContext)
            true
        } catch (e: Exception) {
            pending = null
            phase = Phase.IDLE
            track = null
            notice = StemNotice(StemNotice.Kind.ERROR, e.message ?: e.toString())
            false
        }
    }

    fun cancel() {
        if (pending != null && job == null) {
            // сервис ещё не успел взять задачу
            pending = null
            reset()
            return
        }
        job?.cancel()
    }

    /** Вызывается сервисом. false, если задачи нет или она уже идёт. */
    fun execute(ctx: Context, onProgress: () -> Unit, onEnd: () -> Unit): Boolean {
        val req = pending ?: return false
        pending = null
        val app = ctx.applicationContext
        lastTick = -1f
        // Ленивый старт: поле job уже заполнено, когда задача начинает (и может сразу закончить) работу
        val j = scope.launch(start = CoroutineStart.LAZY) {
            try {
                when (req) {
                    is Request.Separate -> separate(app, req, onProgress)
                    is Request.Download -> download(app, req.models, onProgress)
                    is Request.Import -> import(app, req.uris, onProgress)
                }
            } finally {
                reset()
                job = null
                onEnd()
            }
        }
        job = j
        j.start()
        return true
    }

    private fun reset() {
        phase = Phase.IDLE
        progress = 0f
        etaSeconds = -1L
        currentModel = null
        track = null
        showProgress = false
    }

    private fun setProgress(p: Float, tick: () -> Unit) {
        progress = p.coerceIn(0f, 1f)
        if (progress - lastTick >= 0.005f || progress >= 1f) {
            lastTick = progress
            tick()
        }
    }

    /** Строка о том, что происходит сейчас: для окна прогресса и уведомления. */
    fun statusText(ctx: Context): String {
        val c = AppLocale.localized(ctx)
        return when (phase) {
            Phase.IDLE -> ""
            Phase.MODELS -> c.getString(R.string.stems_stage_models)
            Phase.DECODING -> c.getString(R.string.stems_stage_decode)
            Phase.SEPARATING -> c.getString(
                R.string.stems_stage_separate,
                currentModel?.let { c.getString(it.label) }.orEmpty(),
                (chunkDone + 1).coerceAtMost(chunkTotal.coerceAtLeast(1)),
                chunkTotal,
            )
        }
    }

    // ---------- Модели: скачивание и импорт ----------

    private suspend fun download(app: Context, models: List<StemModel>, tick: () -> Unit) {
        val total = models.sumOf { it.bytes }.toFloat()
        var base = 0L
        try {
            for (m in models) {
                currentCoroutineContext().ensureActive()
                currentModel = m
                StemModelStore.download(app, m) { got -> setProgress((base + got) / total, tick) }
                base += m.bytes
                refreshModels(app)
            }
        } catch (e: CancellationException) {
            // отмена пользователем: недокачанный файл уже удалён
        } catch (e: Exception) {
            notice = StemNotice(StemNotice.Kind.ERROR, e.message ?: e.toString())
        } finally {
            refreshModels(app)
        }
    }

    private suspend fun import(app: Context, uris: List<Uri>, tick: () -> Unit) {
        val rejected = ArrayList<String>()
        try {
            for ((i, uri) in uris.withIndex()) {
                currentCoroutineContext().ensureActive()
                val model = StemModelStore.import(app, uri) { got ->
                    setProgress((i + got.toFloat() / StemModel.VOCALS.bytes) / uris.size, tick)
                }
                if (model == null) rejected += StemModelStore.displayName(app, uri)
                refreshModels(app)
            }
            if (rejected.isNotEmpty()) notice = StemNotice(StemNotice.Kind.IMPORT_REJECTED, rejected.joinToString(", "))
        } catch (e: CancellationException) {
            // отмена пользователем
        } catch (e: Exception) {
            notice = StemNotice(StemNotice.Kind.ERROR, e.message ?: e.toString())
        } finally {
            refreshModels(app)
        }
    }

    // ---------- Разбор трека ----------

    private fun threads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    private fun trackBaseName(t: Track): String =
        safeName(if (t.artist.isBlank() || t.artist == NO_ARTIST) t.title else "${t.artist} - ${t.title}")

    private fun describe(app: Context, e: Throwable): String {
        val c = AppLocale.localized(app)
        return when ((e as? StemException)?.reason) {
            StemException.Reason.NO_AUDIO -> c.getString(R.string.stems_err_no_audio)
            StemException.Reason.EMPTY -> c.getString(R.string.stems_err_empty)
            StemException.Reason.NO_SPACE -> c.getString(R.string.stems_err_no_space)
            StemException.Reason.NO_DECODER -> c.getString(R.string.stems_err_no_decoder, e.message.orEmpty())
            null -> e.message ?: e.toString()
        }
    }

    private suspend fun separate(app: Context, req: Request.Separate, tick: () -> Unit) {
        val t = req.track
        val cache = File(app.cacheDir, "stems").also { it.deleteRecursively(); it.mkdirs() }
        val mixFile = File(cache, "mix.f32")
        val mirror = LocalMirror(app, req.tree, FOLDER)
        val base = trackBaseName(t)
        val fileTag = Stem.fileTag(req.stems)
        var saved = false
        try {
            // Временный файл с исходным звуком: 8 байт на отсчёт
            val need = (t.durationMs / 1000.0 * StemSpec.SAMPLE_RATE * 8 * 1.1).toLong() + 64L * 1024 * 1024
            if (cache.usableSpace < need) throw StemException(StemException.Reason.NO_SPACE)

            phase = Phase.DECODING
            val frames = StemDecoder.decode(app, Uri.parse(t.uri), mixFile) { p -> setProgress(p * DECODE_SHARE, tick) }
            if (frames < 1) throw StemException(StemException.Reason.EMPTY)

            phase = Phase.SEPARATING
            val models = StemModel.entries.toList()
            val chunks = StemSpec.chunkCount(frames)
            val steps = models.size * chunks
            chunkTotal = chunks
            val started = SystemClock.elapsedRealtime()
            val coroutine = currentCoroutineContext()

            // Один файл: сумма выбранных стемов
            val rel = listOf(base, "$base - $fileTag.wav")
            val target = mirror.target(rel, overwrite = mirror.existingSize(rel) != null)
            var writer: WavWriter? = null
            val sessions = ArrayList<OrtMaskModel>()
            try {
                val w = WavWriter(BufferedOutputStream(mirror.openOut(target, true), 1 shl 18), frames)
                writer = w
                for (m in models) {
                    coroutine.ensureActive()
                    sessions += OrtMaskModel(StemModelStore.file(app, m), threads())
                }
                val select = models.map { m -> req.stems.any { it.model == m } }
                FloatPcmFile(mixFile).use { src ->
                    StemPass(src, sessions, normalize = true, residual = models.indexOf(StemModel.OTHER)).run(
                        select = select,
                        output = w,
                        onStep = { done, total ->
                            chunkDone = done / models.size
                            currentModel = models[done.coerceAtMost(total - 1) % models.size]
                            setProgress(DECODE_SHARE + (1f - DECODE_SHARE) * done / steps, tick)
                            if (done > 0) {
                                val elapsed = SystemClock.elapsedRealtime() - started
                                etaSeconds = elapsed * (steps - done) / done / 1000
                            }
                        },
                        cancelled = { !coroutine.isActive },
                    )
                }
                w.close()
                saved = true
            } catch (e: Throwable) {
                // Недописанный файл не оставляем
                runCatching { writer?.close() }
                mirror.discard(target)
                throw e
            } finally {
                sessions.forEach { runCatching { it.close() } }
            }
            notice = StemNotice(StemNotice.Kind.DONE, file = "$base - $fileTag.wav", folder = "$FOLDER/$base")
        } catch (e: CancellationException) {
            notice = StemNotice(StemNotice.Kind.CANCELLED)
        } catch (e: Throwable) {
            notice = StemNotice(StemNotice.Kind.ERROR, describe(app, e))
        } finally {
            cache.deleteRecursively()
            if (saved) onCreated?.invoke()
        }
    }
}
