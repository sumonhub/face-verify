package com.sumonlogs.faceverify.core.engine.android

import android.content.Context
import com.sumonlogs.faceverify.core.engine.FaceEmbedder
import com.sumonlogs.faceverify.core.engine.FaceMatcher
import com.sumonlogs.faceverify.core.engine.FaceModelSpec
import com.sumonlogs.faceverify.core.engine.PixelNormalization
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs one face model on LiteRT. Reads batch size, layout (NHWC or NCHW) and embedding size from
 * the model itself. A model with a fixed batch > 1 (MobileFaceNet has 2) gets several faces per run.
 */
class LiteRtFaceEmbedder(private val context: Context, override val model: FaceModelSpec) : FaceEmbedder {

    override val inputSize: Int = INPUT_SIZE

    private val interpreter: Interpreter by lazy {
        val buffer = context.assets.openFd(model.assetFile).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
        Interpreter(buffer, Interpreter.Options().setNumThreads(THREADS))
    }

    private val inputShape: IntArray by lazy { interpreter.getInputTensor(0).shape() }
    private val batch: Int by lazy { inputShape.first() }
    private val channelsLast: Boolean by lazy { inputShape.last() == 3 }
    private val dimension: Int by lazy { interpreter.getOutputTensor(0).shape().last() }
    private val input: ByteBuffer by lazy {
        ByteBuffer.allocateDirect(batch * INPUT_SIZE * INPUT_SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    }

    @Synchronized
    override fun embed(faces: List<IntArray>): List<FloatArray> {
        faces.forEach { require(it.size == INPUT_SIZE * INPUT_SIZE) { "Expected ${INPUT_SIZE}x$INPUT_SIZE pixels" } }
        return faces.chunked(batch).flatMap { chunk -> run(chunk) }
    }

    /** One inference; unused batch slots repeat the last face and their output is dropped. */
    private fun run(chunk: List<IntArray>): List<FloatArray> {
        input.rewind()
        repeat(batch) { slot -> write(chunk[minOf(slot, chunk.lastIndex)]) }
        input.rewind()
        val output = Array(batch) { FloatArray(dimension) }
        interpreter.run(input, output)
        return List(chunk.size) { FaceMatcher.l2Normalize(output[it]) }
    }

    private fun write(argb: IntArray) {
        if (channelsLast) {
            for (pixel in argb) {
                input.putFloat(norm(pixel shr 16 and 0xFF))
                input.putFloat(norm(pixel shr 8 and 0xFF))
                input.putFloat(norm(pixel and 0xFF))
            }
        } else {
            for (shift in intArrayOf(16, 8, 0)) {
                for (pixel in argb) input.putFloat(norm(pixel shr shift and 0xFF))
            }
        }
    }

    private fun norm(value: Int): Float = when (model.normalization) {
        PixelNormalization.MinusOneToOne -> (value - 127.5f) / 128f
        PixelNormalization.Raw -> value.toFloat()
    }

    private companion object {
        const val INPUT_SIZE = 112
        const val THREADS = 2
    }
}

/** One embedder per model, created on first use. */
class FaceEmbedders(private val context: Context) {
    private val cache = ConcurrentHashMap<String, FaceEmbedder>()

    fun forModel(model: FaceModelSpec): FaceEmbedder =
        cache.getOrPut(model.id) { LiteRtFaceEmbedder(context, model) }
}
