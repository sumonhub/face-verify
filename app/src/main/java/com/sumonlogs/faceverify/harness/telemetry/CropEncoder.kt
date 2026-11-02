package com.sumonlogs.faceverify.harness.telemetry

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.sumonlogs.faceverify.core.api.AlignedPreview
import java.io.ByteArrayOutputStream
import java.util.Base64

/** Aligned face pixels to the lossless PNG text a [CropRecord] carries. */
object CropEncoder {
    fun png(crop: AlignedPreview): String {
        val bitmap = createBitmap(crop.size, crop.size)
        try {
            bitmap.setPixels(crop.pixels, 0, crop.size, 0, 0, crop.size, crop.size)
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            return Base64.getEncoder().encodeToString(bytes)
        } finally {
            bitmap.recycle()
        }
    }
}
