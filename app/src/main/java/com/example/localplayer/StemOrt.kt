package com.example.localplayer

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer

/**
 * Одна сеть Open-Unmix в ONNX Runtime (CPU). Вход — амплитуды [1, 2, 2049, кадры], выход — той же формы.
 * Имя входа берётся из самой модели, поэтому не важно, как оно называется при экспорте.
 */
internal class OrtMaskModel(file: File, threads: Int) : MaskModel, AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setInterOpNumThreads(1)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val session = env.createSession(file.absolutePath, options)
    private val inputName = session.inputNames.first()

    override fun run(mag: FloatBuffer, frames: Int, consume: (FloatBuffer) -> Unit) {
        val shape = longArrayOf(1, 2, StemSpec.BINS.toLong(), frames.toLong())
        // slice(): отдельный вид ровно на нужные числа (позиция 0, предел = размер тензора)
        OnnxTensor.createTensor(env, mag.slice(), shape).use { input ->
            session.run(mapOf(inputName to input)).use { result ->
                val out = result[0] as OnnxTensor
                consume(out.floatBuffer)
            }
        }
    }

    override fun close() {
        session.close()
        options.close()
    }
}
