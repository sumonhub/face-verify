package com.sumonlogs.faceverify.core.engine.android

import android.content.Context
import com.sumonlogs.faceverify.core.engine.FaceEmbedder
import com.sumonlogs.faceverify.core.engine.FaceMatcher
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * MobileFaceNet on LiteRT. The bundled model has a fixed batch of 2, so the same face is written
 * into every batch slot and the first output row is used.
 */
class LiteRtFaceEmbedder(private val context: Context) : FaceEmbedder {

    override val inputSize: Int = INPUT_SIZE

    private val interpreter: Interpreter by lazy {
        val model = context.assets.openFd(MODEL_FILE).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
        Interpreter(model, Interpreter.Options().setNumThreads(THREADS))
    }

    private val batch: Int by lazy { interpreter.getInputTensor(0).shape().first() }
    private val dimension: Int by lazy { interpreter.getOutputTensor(0).shape().last() }
    private val input: ByteBuffer by lazy {
        ByteBuffer.allocateDirect(batch * INPUT_SIZE * INPUT_SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    }

    @Synchronized
    override fun embed(argb: IntArray): FloatArray {
        require(argb.size == INPUT_SIZE * INPUT_SIZE) { "Expected ${INPUT_SIZE}x$INPUT_SIZE pixels" }
        input.rewind()
        repeat(batch) {
            for (pixel in argb) {
                input.putFloat(((pixel shr 16 and 0xFF) - MEAN) / STD)
                input.putFloat(((pixel shr 8 and 0xFF) - MEAN) / STD)
                input.putFloat(((pixel and 0xFF) - MEAN) / STD)
            }
        }
        input.rewind()
        val output = Array(batch) { FloatArray(dimension) }
        interpreter.run(input, output)
        return FaceMatcher.l2Normalize(output[0])
    }

    private companion object {
        const val MODEL_FILE = "MobileFaceNet.tflite"
        const val INPUT_SIZE = 112
        const val MEAN = 127.5f
        const val STD = 128f
        const val THREADS = 2
    }
}
