package com.example.localplayer

import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Разделение на стемы: вся математика без единого вызова Android API (поэтому её можно проверять обычным тестом на JVM).
 *
 * Open-Unmix (umxhq) работает с амплитудной спектрограммой: STFT 4096/1024 при 44,1 кГц, на выходе сети та же сетка
 * [канал, бин, кадр], а маска получается как «выход / вход». Сама сеть (ONNX Runtime) подключается через [MaskModel],
 * а здесь — всё остальное: STFT/ISTFT, нарезка на блоки с контекстом, ресемплер и запись WAV.
 */

internal object StemSpec {
    const val SAMPLE_RATE = 44100
    const val N_FFT = 4096
    const val HOP = 1024
    const val BINS = N_FFT / 2 + 1

    /** Полезная часть блока: 860 шагов STFT ≈ 19,97 с. Блок целиком проходит через сеть за один вызов. */
    const val CORE = HOP * 860

    /** Контекст с каждой стороны блока (≈ 2 с): двунаправленная LSTM к краям куска работает хуже, поэтому края отбрасываются. */
    const val CONTEXT = HOP * 86

    const val MAX_SEGMENT = CORE + 2 * CONTEXT

    /** Наибольшее число кадров STFT в одном блоке (центрированный STFT: 1 + длина / шаг). */
    const val MAX_FRAMES = 1 + MAX_SEGMENT / HOP

    fun chunkCount(totalFrames: Long): Int = ((totalFrames + CORE - 1) / CORE).toInt()
}

// =====================================================================
// STFT / ISTFT
// =====================================================================

/**
 * Центрированный STFT с периодическим окном Хэннинга и отражением на краях (как `torch.stft(center=True)`).
 * Оба канала считаются одним комплексным БПФ: левый кладётся в действительную часть, правый — в мнимую,
 * а потом спектры разделяются по симметрии. Объект хранит рабочие буферы: использовать из одного потока.
 */
internal class StemStft {
    private val n = StemSpec.N_FFT
    private val hop = StemSpec.HOP
    private val bins = StemSpec.BINS

    private val window = DoubleArray(n) { 0.5 - 0.5 * cos(2.0 * PI * it / n) }
    private val twCos = DoubleArray(n / 2) { cos(2.0 * PI * it / n) }
    private val twSin = DoubleArray(n / 2) { -sin(2.0 * PI * it / n) }
    private val rev = IntArray(n).also { r ->
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            r[i] = j
        }
    }
    private val re = DoubleArray(n)
    private val im = DoubleArray(n)

    /** Спектр последнего разобранного кадра (бины 0..N/2). После [analyze] их можно менять (маска) и отдавать в [synthesize]. */
    val lRe = DoubleArray(bins)
    val lIm = DoubleArray(bins)
    val rRe = DoubleArray(bins)
    val rIm = DoubleArray(bins)

    /** Прямое БПФ на месте. */
    private fun fft(xr: DoubleArray, xi: DoubleArray) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                val tr = xr[i]; xr[i] = xr[j]; xr[j] = tr
                val ti = xi[i]; xi[i] = xi[j]; xi[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val half = size shr 1
            val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + half) {
                    val wr = twCos[k]
                    val wi = twSin[k]
                    val a = j + half
                    val tr = xr[a] * wr - xi[a] * wi
                    val ti = xr[a] * wi + xi[a] * wr
                    xr[a] = xr[j] - tr
                    xi[a] = xi[j] - ti
                    xr[j] += tr
                    xi[j] += ti
                    k += step
                }
                i += size
            }
            size = size shl 1
        }
    }

    /** Отражение индекса за краями сигнала длиной [len] (без повтора крайнего отсчёта). */
    private fun reflect(idx: Int, len: Int): Int {
        if (len == 1) return 0
        val period = 2 * (len - 1)
        var j = idx % period
        if (j < 0) j += period
        return if (j < len) j else period - j
    }

    /** Число кадров STFT для сигнала длиной [len]. */
    fun frameCount(len: Int): Int = 1 + len / hop

    /** Разбирает кадр [frame] сигнала ([l], [r], длина [len]) в [lRe]..[rIm]. */
    fun analyze(l: FloatArray, r: FloatArray, len: Int, frame: Int) {
        val start = frame * hop - n / 2
        for (i in 0 until n) {
            val idx = reflect(start + i, len)
            val w = window[i]
            re[i] = l[idx] * w
            im[i] = r[idx] * w
        }
        fft(re, im)
        for (k in 0 until bins) {
            val m = (n - k) and (n - 1)
            lRe[k] = 0.5 * (re[k] + re[m])
            lIm[k] = 0.5 * (im[k] - im[m])
            rRe[k] = 0.5 * (im[k] + im[m])
            rIm[k] = 0.5 * (re[m] - re[k])
        }
    }

    /**
     * Обратное преобразование кадра из [lRe]..[rIm] с наложением в [accL]/[accR] (оконная функция применяется ещё раз).
     * В [env] копится сумма квадратов окна: на неё делится результат в конце блока.
     */
    fun synthesize(frame: Int, len: Int, accL: FloatArray, accR: FloatArray, env: FloatArray?) {
        val half = n / 2
        // Z = L + i·R для всех бинов; верхняя половина восстанавливается из эрмитовой симметрии
        for (k in 0..half) {
            re[k] = lRe[k] - rIm[k]
            im[k] = lIm[k] + rRe[k]
        }
        for (k in half + 1 until n) {
            val j = n - k
            re[k] = lRe[j] + rIm[j]
            im[k] = rRe[j] - lIm[j]
        }
        // Обратное БПФ через прямое со сменой местами действительной и мнимой частей
        fft(im, re)
        val scale = 1.0 / n
        val start = frame * hop - n / 2
        for (i in 0 until n) {
            val idx = start + i
            if (idx < 0 || idx >= len) continue
            val w = window[i]
            accL[idx] += (re[i] * scale * w).toFloat()
            accR[idx] += (im[i] * scale * w).toFloat()
            if (env != null) env[idx] += (w * w).toFloat()
        }
    }
}

// =====================================================================
// Обработка блоками
// =====================================================================

/** Сеть, превращающая амплитуды в амплитуды стема. Тензор: [1, 2, 2049, frames], индекс = (канал · 2049 + бин) · frames + кадр. */
internal interface MaskModel {
    /** Запускает сеть на [mag] (ровно 2·2049·[frames] чисел) и отдаёт выход в [consume] (буфер действителен только внутри вызова). */
    fun run(mag: FloatBuffer, frames: Int, consume: (FloatBuffer) -> Unit)
}

/** Исходный звук: 44,1 кГц, стерео, float, весь трек. */
internal interface PcmSource {
    val frames: Long
    fun read(start: Long, count: Int, l: FloatArray, r: FloatArray)
}

/** Приёмник готового звука: куски приходят подряд, без пропусков. */
internal fun interface StemOutput {
    fun write(l: FloatArray, r: FloatArray, offset: Int, count: Int)
}

/**
 * Один проход одной сетью по всему треку. Память не зависит от длины трека: в каждый момент в работе один блок.
 *
 * Блок = [StemSpec.CORE] отсчётов полезной части + [StemSpec.CONTEXT] контекста с обеих сторон. Спектр микса считается
 * дважды (до сети и после), зато комплексный спектр не нужно хранить: так блоку нужно вдвое меньше памяти.
 */
internal class StemPass(private val src: PcmSource, private val model: MaskModel) {

    private val stft = StemStft()
    private val segL = FloatArray(StemSpec.MAX_SEGMENT)
    private val segR = FloatArray(StemSpec.MAX_SEGMENT)
    private val accL = FloatArray(StemSpec.MAX_SEGMENT)
    private val accR = FloatArray(StemSpec.MAX_SEGMENT)
    private val env = FloatArray(StemSpec.MAX_SEGMENT)
    private val tensor: FloatBuffer =
        ByteBuffer.allocateDirect(2 * StemSpec.BINS * StemSpec.MAX_FRAMES * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    /**
     * [stem] получает «выход сети × микс», [complement] — остаток «микс − стем» (так получается инструментал из вокала).
     * [onChunk] вызывается после каждого блока: (сделано, всего). [cancelled] проверяется перед каждым блоком.
     */
    fun run(
        stem: StemOutput?,
        complement: StemOutput?,
        onChunk: (done: Int, total: Int) -> Unit,
        cancelled: () -> Boolean,
    ) {
        val total = src.frames
        val chunks = StemSpec.chunkCount(total)
        onChunk(0, chunks)
        for (c in 0 until chunks) {
            if (cancelled()) throw java.util.concurrent.CancellationException()
            val coreStart = c.toLong() * StemSpec.CORE
            val coreLen = min(StemSpec.CORE.toLong(), total - coreStart).toInt()
            val segStart = maxOf(0L, coreStart - StemSpec.CONTEXT)
            val segEnd = min(total, coreStart + coreLen + StemSpec.CONTEXT)
            val len = (segEnd - segStart).toInt()
            val coreOffset = (coreStart - segStart).toInt()
            processSegment(segStart, len, coreOffset, coreLen, stem, complement)
            onChunk(c + 1, chunks)
        }
    }

    private fun processSegment(
        segStart: Long, len: Int, coreOffset: Int, coreLen: Int,
        stem: StemOutput?, complement: StemOutput?,
    ) {
        val bins = StemSpec.BINS
        src.read(segStart, len, segL, segR)
        val frames = stft.frameCount(len)
        val count = 2 * bins * frames

        // 1. Амплитудная спектрограмма -> сеть
        tensor.clear()
        tensor.limit(count)
        for (f in 0 until frames) {
            stft.analyze(segL, segR, len, f)
            for (k in 0 until bins) {
                tensor.put(k * frames + f, sqrt(stft.lRe[k] * stft.lRe[k] + stft.lIm[k] * stft.lIm[k]).toFloat())
                tensor.put((bins + k) * frames + f, sqrt(stft.rRe[k] * stft.rRe[k] + stft.rIm[k] * stft.rIm[k]).toFloat())
            }
        }

        java.util.Arrays.fill(accL, 0, len, 0f)
        java.util.Arrays.fill(accR, 0, len, 0f)
        java.util.Arrays.fill(env, 0, len, 0f)

        // 2. Маска = выход / вход (0..1) применяется к комплексному спектру микса, затем обратное преобразование
        model.run(tensor, frames) { out ->
            for (f in 0 until frames) {
                stft.analyze(segL, segR, len, f)
                for (k in 0 until bins) {
                    val inL = tensor.get(k * frames + f)
                    val inR = tensor.get((bins + k) * frames + f)
                    val mL = ratio(out.get(k * frames + f), inL)
                    val mR = ratio(out.get((bins + k) * frames + f), inR)
                    stft.lRe[k] *= mL
                    stft.lIm[k] *= mL
                    stft.rRe[k] *= mR
                    stft.rIm[k] *= mR
                }
                stft.synthesize(f, len, accL, accR, env)
            }
        }

        // 3. Нормировка на сумму квадратов окна
        for (i in 0 until len) {
            val e = env[i]
            if (e > 1e-6f) {
                accL[i] /= e
                accR[i] /= e
            }
        }

        stem?.write(accL, accR, coreOffset, coreLen)
        if (complement != null) {
            for (i in coreOffset until coreOffset + coreLen) {
                accL[i] = segL[i] - accL[i]
                accR[i] = segR[i] - accR[i]
            }
            complement.write(accL, accR, coreOffset, coreLen)
        }
    }

    private fun ratio(out: Float, input: Float): Float =
        if (input > 1e-9f) (out / input).coerceIn(0f, 1f) else 0f
}

// =====================================================================
// Файлы
// =====================================================================

/** Звук во временном файле: float32, стерео, чередуется (L R L R…), порядок байт — родной. */
internal class FloatPcmFile(private val file: File) : PcmSource, Closeable {

    private val raf = RandomAccessFile(file, "r")
    private val channel = raf.channel
    override val frames: Long = file.length() / 8
    private val bytes = ByteBuffer.allocate(1 shl 16).order(ByteOrder.nativeOrder())

    override fun read(start: Long, count: Int, l: FloatArray, r: FloatArray) {
        var done = 0
        while (done < count) {
            val n = min(count - done, bytes.capacity() / 8)
            bytes.clear()
            bytes.limit(n * 8)
            var pos = (start + done) * 8
            while (bytes.hasRemaining()) {
                val got = channel.read(bytes, pos)
                if (got < 0) throw java.io.EOFException("Temporary audio file is shorter than expected")
                pos += got
            }
            bytes.flip()
            val fb = bytes.asFloatBuffer()
            for (i in 0 until n) {
                l[done + i] = fb.get()
                r[done + i] = fb.get()
            }
            done += n
        }
    }

    override fun close() {
        raf.close()
    }
}

/** Запись временного файла для [FloatPcmFile]. */
internal class FloatPcmWriter(file: File) : Closeable {
    private val out = java.io.FileOutputStream(file)
    private val bytes = ByteBuffer.allocate(1 shl 16).order(ByteOrder.nativeOrder())
    var frames = 0L
        private set

    fun write(l: FloatArray, r: FloatArray, count: Int) {
        var i = 0
        while (i < count) {
            if (bytes.remaining() < 8) flush()
            bytes.putFloat(l[i])
            bytes.putFloat(r[i])
            i++
        }
        frames += count
    }

    private fun flush() {
        out.write(bytes.array(), 0, bytes.position())
        bytes.clear()
    }

    override fun close() {
        try {
            flush()
        } finally {
            out.close()
        }
    }
}

/** WAV, 16 бит, стерео, 44,1 кГц. Размер данных известен заранее (длина трека не меняется), поэтому заголовок пишется сразу. */
internal class WavWriter(private val out: OutputStream, frames: Long) : StemOutput, Closeable {

    private var scratch = ByteArray(0)

    init {
        val dataBytes = frames * 4
        require(dataBytes + 36 <= 0xFFFF_FFFFL) { "Track is too long for a WAV file" }
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray(Charsets.US_ASCII))
        h.putInt((dataBytes + 36).toInt())
        h.put("WAVE".toByteArray(Charsets.US_ASCII))
        h.put("fmt ".toByteArray(Charsets.US_ASCII))
        h.putInt(16)
        h.putShort(1) // PCM
        h.putShort(2) // каналы
        h.putInt(StemSpec.SAMPLE_RATE)
        h.putInt(StemSpec.SAMPLE_RATE * 4)
        h.putShort(4) // байт на кадр
        h.putShort(16)
        h.put("data".toByteArray(Charsets.US_ASCII))
        h.putInt(dataBytes.toInt())
        out.write(h.array())
    }

    override fun write(l: FloatArray, r: FloatArray, offset: Int, count: Int) {
        if (scratch.size < count * 4) scratch = ByteArray(count * 4)
        var p = 0
        for (i in offset until offset + count) {
            val a = (l[i] * 32767f).roundToInt().coerceIn(-32768, 32767)
            val b = (r[i] * 32767f).roundToInt().coerceIn(-32768, 32767)
            scratch[p++] = a.toByte()
            scratch[p++] = (a shr 8).toByte()
            scratch[p++] = b.toByte()
            scratch[p++] = (b shr 8).toByte()
        }
        out.write(scratch, 0, count * 4)
    }

    override fun close() {
        out.flush()
        out.close()
    }
}

// =====================================================================
// Ресемплер
// =====================================================================

/**
 * Потоковый ресемплер на оконном sinc (полифазный, по 14 нулей ядра с каждой стороны, окно Блэкмана), стерео.
 * Нужен только если у трека не 44,1 кГц: сеть обучена на 44,1 кГц. Срез — 95% от половины меньшей из частот.
 */
internal class StemResampler(private val inRate: Int, private val outRate: Int = StemSpec.SAMPLE_RATE) {

    private val g = gcd(inRate, outRate)
    private val upR = outRate / g
    private val downR = inRate / g
    private val exact = upR <= MAX_PHASES
    private val phases = if (exact) upR else MAX_PHASES
    private val fc = min(1.0, outRate.toDouble() / inRate) * 0.95
    private val half = ceil(ZERO_CROSSINGS / fc).toInt()
    private val taps = 2 * half
    private val table = FloatArray(phases * taps)

    private var hl = FloatArray(1 shl 14)
    private var hr = FloatArray(1 shl 14)
    private var hStart = -half.toLong() // абсолютный индекс hl[0]; отсчёты до начала трека — нули
    private var hCount = half
    private var outIdx = 0L

    private val oL = FloatArray(OUT_BLOCK)
    private val oR = FloatArray(OUT_BLOCK)

    init {
        for (p in 0 until phases) {
            val frac = p.toDouble() / phases
            var sum = 0.0
            val row = DoubleArray(taps)
            for (t in 0 until taps) {
                val x = (t - half + 1) - frac // расстояние от отсчёта до момента выхода, в отсчётах входа
                val s = if (x == 0.0) fc else sin(PI * fc * x) / (PI * x)
                val u = x / half
                val w = if (u <= -1.0 || u >= 1.0) 0.0 else 0.42 + 0.5 * cos(PI * u) + 0.08 * cos(2 * PI * u)
                row[t] = s * w
                sum += row[t]
            }
            for (t in 0 until taps) table[p * taps + t] = (row[t] / sum).toFloat()
        }
    }

    /** Добавляет [n] входных отсчётов; готовые выходные отдаёт в [sink]. */
    fun process(l: FloatArray, r: FloatArray, n: Int, sink: (FloatArray, FloatArray, Int) -> Unit) {
        append(l, r, n)
        produce(sink)
    }

    /** Конец трека: дописывает тишину, чтобы вышли последние отсчёты. */
    fun finish(sink: (FloatArray, FloatArray, Int) -> Unit) {
        val z = FloatArray(half)
        append(z, z, half)
        produce(sink)
    }

    private fun append(l: FloatArray, r: FloatArray, n: Int) {
        if (hCount + n > hl.size) {
            val cap = maxOf(hl.size * 2, hCount + n)
            hl = hl.copyOf(cap)
            hr = hr.copyOf(cap)
        }
        System.arraycopy(l, 0, hl, hCount, n)
        System.arraycopy(r, 0, hr, hCount, n)
        hCount += n
    }

    private fun produce(sink: (FloatArray, FloatArray, Int) -> Unit) {
        var filled = 0
        while (true) {
            var i0: Long
            var p: Int
            if (exact) {
                val num = outIdx * downR
                i0 = num / upR
                p = (num % upR).toInt()
            } else {
                val pos = outIdx.toDouble() * inRate / outRate
                i0 = floor(pos).toLong()
                p = ((pos - i0) * phases).roundToInt()
                if (p >= phases) {
                    p = 0
                    i0++
                }
            }
            if (i0 + half >= hStart + hCount) break
            val base = (i0 - half + 1 - hStart).toInt()
            val row = p * taps
            var accL = 0f
            var accR = 0f
            for (t in 0 until taps) {
                val c = table[row + t]
                accL += c * hl[base + t]
                accR += c * hr[base + t]
            }
            oL[filled] = accL
            oR[filled] = accR
            filled++
            outIdx++
            if (filled == OUT_BLOCK) {
                sink(oL, oR, filled)
                filled = 0
            }
        }
        if (filled > 0) sink(oL, oR, filled)
        // Забываем то, что следующим выходным отсчётам уже не понадобится
        val nextI0 = if (exact) (outIdx * downR) / upR else floor(outIdx.toDouble() * inRate / outRate).toLong()
        val drop = (nextI0 - half + 1 - hStart).toInt()
        if (drop > 0 && drop <= hCount) {
            System.arraycopy(hl, drop, hl, 0, hCount - drop)
            System.arraycopy(hr, drop, hr, 0, hCount - drop)
            hStart += drop
            hCount -= drop
        }
    }

    private companion object {
        const val MAX_PHASES = 2048
        const val ZERO_CROSSINGS = 14.0
        const val OUT_BLOCK = 16384

        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}
