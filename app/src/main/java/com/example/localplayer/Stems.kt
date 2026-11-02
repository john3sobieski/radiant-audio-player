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
 * Стемы, которые умеет делать плеер. Вокал, ударные и бас дают сети Open-Unmix напрямую; инструментал — это остаток
 * «микс − вокал», поэтому для него нужна только сеть вокала (и его сумма с вокалом в точности равна оригиналу).
 * [tag] попадает в имя файла и от языка не зависит, чтобы повторный запуск перезаписывал те же файлы.
 */
enum class Stem(@StringRes val label: Int, val model: StemModel, val tag: String, val complement: Boolean = false) {
    VOCALS(R.string.stem_vocals, StemModel.VOCALS, "Vocals"),
    INSTRUMENTAL(R.string.stem_instrumental, StemModel.VOCALS, "Instrumental", complement = true),
    DRUMS(R.string.stem_drums, StemModel.DRUMS, "Drums"),
    BASS(R.string.stem_bass, StemModel.BASS, "Bass"),
}

/** Итог работы (диалог). */
class StemNotice(
    val kind: Kind,
    val detail: String = "",
    val saved: List<Stem> = emptyList(),
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
        val list = saved.joinToString(", ") { c.getString(it.label) }
        return when (kind) {
            Kind.DONE -> c.getString(R.string.stems_done_body, folder, list)
            Kind.CANCELLED ->
                if (saved.isEmpty()) c.getString(R.string.stems_cancelled_body) else c.getString(R.string.stems_done_body, folder, list)
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

    /** Какая сеть работает (проход [passIndex] из [passTotal]); блок [chunkDone] из [chunkTotal] в этом проходе. */
    var currentModel by mutableStateOf<StemModel?>(null)
        private set
    var passIndex by mutableIntStateOf(0)
        private set
    var passTotal by mutableIntStateOf(0)
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

    /** Каких моделей не хватает для выбранных стемов. */
    fun missingFor(stems: Set<Stem>): List<StemModel> =
        StemModel.entries.filter { m -> stems.any { it.model == m } && m !in installed }

    fun deleteModels(ctx: Context) {
        if (busy) return
        StemModelStore.deleteAll(ctx.applicationContext)
        refreshModels(ctx)
    }

    fun downloadModels(ctx: Context, models: List<StemModel>) {
        if (busy || models.isEmpty()) return
        begin(ctx, Request.Download(models), Phase.MODELS)
    }

    fun importModels(ctx: Context, uris: List<Uri>) {
        if (busy || uris.isEmpty()) return
        begin(ctx, Request.Import(uris), Phase.MODELS)
    }

    // ---------- Выбор стемов ----------

    fun loadSelection(ctx: Context): Set<Stem> {
        val saved = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("stem_selection", null)
            ?: return setOf(Stem.VOCALS, Stem.INSTRUMENTAL)
        return saved.split(',').mapNotNull { n -> Stem.entries.firstOrNull { it.name == n } }.toSet()
    }

    fun saveSelection(ctx: Context, stems: Set<Stem>) {
        ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("stem_selection", stems.joinToString(",") { it.name }).apply()
    }

    // ---------- Запуск ----------

    /** Запускает разбор трека. false, если запуск невозможен (причина показана в [notice] или не хватает моделей). */
    fun start(ctx: Context, track: Track, stems: Set<Stem>, tree: Uri): Boolean {
        if (busy || stems.isEmpty()) return false
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
        passIndex = 0
        passTotal = 0
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
                passIndex + 1,
                passTotal,
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

    private fun threads(): Int = if (PowerSave.intensiveNow) 2 else Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

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
        val saved = ArrayList<Stem>()
        try {
            // Временный файл с исходным звуком: 8 байт на отсчёт
            val need = (t.durationMs / 1000.0 * StemSpec.SAMPLE_RATE * 8 * 1.1).toLong() + 64L * 1024 * 1024
            if (cache.usableSpace < need) throw StemException(StemException.Reason.NO_SPACE)

            phase = Phase.DECODING
            val frames = StemDecoder.decode(app, Uri.parse(t.uri), mixFile) { p -> setProgress(p * DECODE_SHARE, tick) }
            if (frames < 1) throw StemException(StemException.Reason.EMPTY)

            phase = Phase.SEPARATING
            val models = StemModel.entries.filter { m -> req.stems.any { it.model == m } }
            val chunks = StemSpec.chunkCount(frames)
            val totalUnits = models.size * chunks
            passTotal = models.size
            chunkTotal = chunks
            val started = SystemClock.elapsedRealtime()
            val coroutine = currentCoroutineContext()

            FloatPcmFile(mixFile).use { src ->
                for ((pi, m) in models.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    passIndex = pi
                    currentModel = m
                    chunkDone = 0
                    val wanted = req.stems.filter { it.model == m }
                    val targets = ArrayList<LocalMirror.Target>()
                    val writers = HashMap<Stem, WavWriter>()
                    try {
                        for (s in wanted) {
                            val rel = listOf(base, "$base - ${s.tag}.wav")
                            val target = mirror.target(rel, overwrite = mirror.existingSize(rel) != null)
                            targets += target
                            writers[s] = WavWriter(BufferedOutputStream(mirror.openOut(target, true), 1 shl 18), frames)
                        }
                        val direct = writers.entries.firstOrNull { !it.key.complement }?.value
                        val rest = writers.entries.firstOrNull { it.key.complement }?.value
                        OrtMaskModel(StemModelStore.file(app, m), threads()).use { model ->
                            StemPass(src, model).run(
                                stem = direct,
                                complement = rest,
                                onChunk = { done, total ->
                                    chunkDone = done
                                    chunkTotal = total
                                    val units = pi * total + done
                                    setProgress(DECODE_SHARE + (1f - DECODE_SHARE) * units / totalUnits, tick)
                                    if (units > 0) {
                                        val elapsed = SystemClock.elapsedRealtime() - started
                                        etaSeconds = elapsed * (totalUnits - units) / units / 1000
                                    }
                                },
                                cancelled = { !coroutine.isActive },
                            )
                        }
                        writers.values.forEach { it.close() }
                        saved += wanted
                    } catch (e: Throwable) {
                        // Недописанные файлы этого прохода не оставляем; готовые проходы остаются
                        writers.values.forEach { runCatching { it.close() } }
                        targets.forEach { mirror.discard(it) }
                        throw e
                    }
                }
            }
            notice = StemNotice(StemNotice.Kind.DONE, saved = saved.sortedBy { it.ordinal }, folder = "$FOLDER/$base")
        } catch (e: CancellationException) {
            notice = StemNotice(StemNotice.Kind.CANCELLED, saved = saved.sortedBy { it.ordinal }, folder = "$FOLDER/$base")
        } catch (e: Throwable) {
            notice = StemNotice(StemNotice.Kind.ERROR, describe(app, e), saved.sortedBy { it.ordinal }, "$FOLDER/$base")
        } finally {
            cache.deleteRecursively()
            if (saved.isNotEmpty()) onCreated?.invoke()
        }
    }
}
