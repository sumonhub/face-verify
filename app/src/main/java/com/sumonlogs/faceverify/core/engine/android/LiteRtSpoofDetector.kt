package com.sumonlogs.faceverify.core.engine.android

import android.content.Context
import android.os.SystemClock
import com.sumonlogs.faceverify.core.api.TimingSink
import com.sumonlogs.faceverify.core.engine.SpoofCrops
import com.sumonlogs.faceverify.core.engine.SpoofDetector
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp

/**
 * MiniFASNet anti-spoofing on LiteRT: two small models that look at the face at different
 * enlargements, their softmax outputs added. Class 1 is "real", the other classes are attacks.
 * The models take raw 0..255 BGR pixels; layout (NHWC or NCHW) is read from the model.
 */
class LiteRtSpoofDetector(
    private val context: Context,
    private val threads: Int = DEFAULT_THREADS,
    private val onTiming: TimingSink = { _, _ -> },
) : SpoofDetector, AutoCloseable {

    override val inputSize: Int = INPUT_SIZE

    private class Branch(val interpreter: Interpreter) {
        val channelsLast: Boolean = interpreter.getInputTensor(0).shape().last() == 3
        val classes: Int = interpreter.getOutputTensor(0).shape().last()
        val input: ByteBuffer =
            ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    }

    private val nearLazy = lazy { Branch(load(NEAR_ASSET)) }
    private val farLazy = lazy { Branch(load(FAR_ASSET)) }
    private val near get() = nearLazy.value
    private val far get() = farLazy.value

    /** Frees the native models. Only for detectors made on the side, such as a benchmark. */
    override fun close() {
        if (nearLazy.isInitialized()) nearLazy.value.interpreter.close()
        if (farLazy.isInitialized()) farLazy.value.interpreter.close()
    }

    @Synchronized
    override fun realProbability(crops: SpoofCrops): Float {
        val a = softmax(timed("spoof.2.7") { run(near, crops.near) })
        val b = softmax(timed("spoof.4.0") { run(far, crops.far) })
        return (a[REAL_CLASS] + b[REAL_CLASS]) / 2f
    }

    private inline fun <T> timed(stage: String, block: () -> T): T {
        val t0 = SystemClock.elapsedRealtimeNanos()
        return block().also { onTiming(stage, (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000f) }
    }

    private fun run(branch: Branch, argb: IntArray): FloatArray {
        require(argb.size == INPUT_SIZE * INPUT_SIZE) { "Expected ${INPUT_SIZE}x$INPUT_SIZE pixels" }
        val input = branch.input
        input.rewind()
        if (branch.channelsLast) {
            for (pixel in argb) {
                input.putFloat((pixel and 0xFF).toFloat())
                input.putFloat((pixel shr 8 and 0xFF).toFloat())
                input.putFloat((pixel shr 16 and 0xFF).toFloat())
            }
        } else {
            for (shift in intArrayOf(0, 8, 16)) {
                for (pixel in argb) input.putFloat((pixel shr shift and 0xFF).toFloat())
            }
        }
        input.rewind()
        val output = Array(1) { FloatArray(branch.classes) }
        branch.interpreter.run(input, output)
        return output[0]
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exps = FloatArray(logits.size) { exp(logits[it] - max) }
        val sum = exps.sum()
        return FloatArray(exps.size) { exps[it] / sum }
    }

    private fun load(asset: String): Interpreter {
        val buffer = context.assets.openFd(asset).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
        return Interpreter(buffer, Interpreter.Options().setNumThreads(threads))
    }

    companion object {
        const val INPUT_SIZE = 80
        const val NEAR_SCALE = 2.7f
        const val FAR_SCALE = 4.0f
        private const val NEAR_ASSET = "spoof_model_scale_2_7.tflite"
        private const val FAR_ASSET = "spoof_model_scale_4_0.tflite"
        private const val REAL_CLASS = 1
        const val DEFAULT_THREADS = 2
    }
}
