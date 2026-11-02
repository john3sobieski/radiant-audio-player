package com.example.localplayer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Три сети Open-Unmix (umxhq), экспортированные в ONNX (MIT, © 2019 Inria): по одной на вокал, ударные и бас.
 * Вход каждой — амплитуды [1, 2, 2049, кадры], выход — той же формы (см. [StemPass]).
 * Файлы не лежат в APK (по ~34 МБ каждый): их скачивают в настройках или подставляют из файла. Подлинность
 * проверяется по SHA-256, поэтому подменённый или битый файл приложение не примет.
 */
enum class StemModel(
    @StringRes val label: Int,
    val id: String,
    private val repo: String,
    val sha256: String,
    val bytes: Long,
) {
    VOCALS(
        R.string.stem_vocals, "vocals", "MixDirective/open-unmix-umxhq-vocals-onnx",
        "f27742bb52b24cb039614dc76d0764e279df3eb2a9d6daa82c6daee3369ec747", 35_623_512L,
    ),
    DRUMS(
        R.string.stem_drums, "drums", "MixDirective/open-unmix-umxhq-drums-onnx",
        "471628e1e48138a634f962a89f622929980cf5485ed0a4e6e648e9dfcd1145d9", 35_623_507L,
    ),
    BASS(
        R.string.stem_bass, "bass", "MixDirective/open-unmix-umxhq-bass-onnx",
        "8931033f04666fdcf300d610579351d74275a6c55dd4346009290c844033726f", 35_623_502L,
    );

    val fileName: String get() = "umxhq-$id.onnx"
    val url: String get() = "https://huggingface.co/$repo/resolve/main/model.onnx"
}

/** Файлы моделей на устройстве. Все функции блокирующие: вызывать не из главного потока. */
internal object StemModelStore {

    fun dir(ctx: Context): File = File(ctx.filesDir, "stem_models").apply { mkdirs() }

    fun file(ctx: Context, m: StemModel): File = File(dir(ctx), m.fileName)

    fun isInstalled(ctx: Context, m: StemModel): Boolean = file(ctx, m).let { it.isFile && it.length() == m.bytes }

    fun deleteAll(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }

    /** Скачивает модель. [onBytes] получает число уже загруженных байт этой модели. Недокачанное удаляется. */
    suspend fun download(ctx: Context, m: StemModel, onBytes: (Long) -> Unit) {
        val part = File(dir(ctx), m.fileName + ".part")
        part.delete()
        val conn = (URL(m.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Radiant")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val md = MessageDigest.getInstance("SHA-256")
            var total = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        total += n
                        onBytes(total)
                        currentCoroutineContext().ensureActive()
                    }
                }
            }
            if (hex(md.digest()) != m.sha256) throw IOException("Checksum mismatch (${m.fileName})")
            val dest = file(ctx, m)
            dest.delete()
            if (!part.renameTo(dest)) throw IOException("Cannot save ${m.fileName}")
        } finally {
            conn.disconnect()
            part.delete() // после успешного переименования его уже нет
        }
    }

    /**
     * Копирует выбранный файл и определяет по SHA-256, какая это модель. Возвращает её или null, если файл — не umxhq.
     * [onBytes] получает число скопированных байт.
     */
    suspend fun import(ctx: Context, uri: Uri, onBytes: (Long) -> Unit): StemModel? {
        val part = File(dir(ctx), "import.part")
        part.delete()
        try {
            val md = MessageDigest.getInstance("SHA-256")
            val limit = StemModel.entries.maxOf { it.bytes }
            var total = 0L
            val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the file")
            input.use { ins ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > limit) return null // слишком большой, чтобы быть моделью
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        onBytes(total)
                        currentCoroutineContext().ensureActive()
                    }
                }
            }
            val digest = hex(md.digest())
            val model = StemModel.entries.firstOrNull { it.sha256 == digest } ?: return null
            val dest = file(ctx, model)
            dest.delete()
            if (!part.renameTo(dest)) throw IOException("Cannot save ${model.fileName}")
            return model
        } finally {
            part.delete()
        }
    }

    fun displayName(ctx: Context, uri: Uri): String = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    } ?: (uri.lastPathSegment ?: "file")

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}
