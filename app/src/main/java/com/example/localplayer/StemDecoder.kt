package com.example.localplayer

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Причина, по которой трек нельзя разобрать: показывается пользователю с переводом. */
class StemException(val reason: Reason, detail: String? = null) : IOException(detail ?: reason.name) {
    enum class Reason { NO_AUDIO, EMPTY, NO_SPACE, NO_DECODER }
}

/**
 * Декодирует любой трек, который понимает Android (MediaCodec), в 44,1 кГц, стерео, float32 и пишет во временный файл.
 * Моно дублируется в оба канала, у многоканальных берутся два первых. Весь трек в памяти не держится.
 * Возвращает число кадров (отсчётов на канал).
 */
internal object StemDecoder {

    suspend fun decode(ctx: Context, uri: Uri, out: File, onProgress: (Float) -> Unit): Long {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val writer = FloatPcmWriter(out)
        try {
            extractor.setDataSource(ctx, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw StemException(StemException.Reason.NO_AUDIO)
            val format = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw StemException(StemException.Reason.NO_AUDIO)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            val c = try {
                MediaCodec.createDecoderByType(mime).also { codec = it }
            } catch (_: Exception) {
                throw StemException(StemException.Reason.NO_DECODER, mime)
            }
            c.configure(format, null, null, 0)
            c.start()

            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var resampler: StemResampler? = null
            var rate = 0
            val sink = { a: FloatArray, b: FloatArray, n: Int -> writer.write(a, b, n) }

            fun applyFormat(f: MediaFormat) {
                val newRate = if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                encoding = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                if (newRate != rate) {
                    resampler?.finish(sink) // хвост прежней частоты дискретизации
                    rate = newRate
                    resampler = if (rate == StemSpec.SAMPLE_RATE) null else StemResampler(rate)
                }
            }
            applyFormat(format)

            var l = FloatArray(8192)
            var r = FloatArray(8192)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var lastReported = -1f

            while (!outputDone) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val ii = c.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = c.getInputBuffer(ii) ?: throw IOException("No input buffer")
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(ii, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val oi = c.dequeueOutputBuffer(info, 10_000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    applyFormat(c.outputFormat)
                } else if (oi >= 0) {
                    val buf = c.getOutputBuffer(oi)
                    if (buf != null && info.size > 0) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.order(ByteOrder.nativeOrder())
                        val ch = channels.coerceAtLeast(1)
                        val frames = info.size / ((if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2) * ch)
                        if (l.size < frames) {
                            l = FloatArray(frames)
                            r = FloatArray(frames)
                        }
                        readFrames(buf, encoding, ch, frames, l, r)
                        val rs = resampler
                        if (rs == null) writer.write(l, r, frames) else rs.process(l, r, frames, sink)
                    }
                    c.releaseOutputBuffer(oi, false)
                    if (durationUs > 0 && info.presentationTimeUs > 0) {
                        val p = (info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f)
                        if (p - lastReported >= 0.01f) {
                            lastReported = p
                            onProgress(p)
                        }
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            resampler?.finish(sink)
            onProgress(1f)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
            writer.close()
        }
        return FloatPcmFile(out).use { it.frames }
    }

    /** Из PCM-буфера (16 бит или float) в два массива float: каналы сверх второго отбрасываются, моно дублируется. */
    private fun readFrames(buf: ByteBuffer, encoding: Int, ch: Int, frames: Int, l: FloatArray, r: FloatArray) {
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val fb = buf.asFloatBuffer()
            for (i in 0 until frames) {
                var a = 0f
                var b = 0f
                for (c in 0 until ch) {
                    val v = fb.get()
                    if (c == 0) a = v else if (c == 1) b = v
                }
                l[i] = a
                r[i] = if (ch == 1) a else b
            }
        } else {
            val sb = buf.asShortBuffer()
            val k = 1f / 32768f
            for (i in 0 until frames) {
                var a = 0f
                var b = 0f
                for (c in 0 until ch) {
                    val v = sb.get() * k
                    if (c == 0) a = v else if (c == 1) b = v
                }
                l[i] = a
                r[i] = if (ch == 1) a else b
            }
        }
    }
}
