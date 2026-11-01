package com.sumonlogs.faceverify.core.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/** The anti-spoof crop runs on every capture frame, so it must never throw for any real face position. */
class EnlargedBoxRobustnessTest {

    @Test
    fun `no face size or position makes the box throw or leave the frame`() {
        val frames = listOf(480 to 640, 640 to 480, 720 to 1280)
        for ((fw, fh) in frames) {
            for (scale in listOf(2.7f, 4.0f)) {
                var width = 60f
                while (width <= fw.toFloat()) {
                    for (cx in listOf(width / 2, fw * 0.3f, fw / 2f, fw * 0.8f, fw - width / 2)) {
                        for (cy in listOf(fh * 0.2f, fh / 2f, fh * 0.9f)) {
                            val box = FrameGeometry.enlargedBox(face(width = width, cx = cx, cy = cy), scale, fw, fh)
                            val where = "frame ${fw}x$fh scale $scale width $width at ($cx,$cy): $box"
                            assertTrue(where, box.left >= 0f && box.top >= 0f)
                            assertTrue(where, box.left + box.width <= fw.toFloat() && box.top + box.height <= fh.toFloat())
                        }
                    }
                    width += 1.7f
                }
            }
        }
    }
}
