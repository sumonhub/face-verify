package com.sumonlogs.faceverify.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FaceMeteringTest {
    private val config = FaceConfig()

    @Test
    fun `aims at the face as soon as one appears`() {
        val spot = FaceMetering(config).next(listOf(face(cx = 200f, cy = 300f)), FRAME_W, FRAME_H, nowMs = 0)
        assertNotNull(spot)
        assertEquals(200f, spot!!.x, 1e-3f)
        assertEquals(300f, spot.y, 1e-3f)
        assertEquals(220f * config.meteringFaceShare, spot.size, 1e-3f)
    }

    @Test
    fun `no face, no aim`() {
        assertNull(FaceMetering(config).next(emptyList(), FRAME_W, FRAME_H, nowMs = 0))
    }

    @Test
    fun `a face that holds still is not re-aimed`() {
        val metering = FaceMetering(config)
        metering.next(listOf(face()), FRAME_W, FRAME_H, nowMs = 0)
        assertNull(metering.next(listOf(face(cx = FRAME_W / 2f + 3f)), FRAME_W, FRAME_H, nowMs = 5_000))
    }

    @Test
    fun `a face that moves is re-aimed, but not faster than the minimum interval`() {
        val metering = FaceMetering(config)
        metering.next(listOf(face()), FRAME_W, FRAME_H, nowMs = 0)
        val moved = listOf(face(cx = FRAME_W / 2f + 120f))
        assertNull(metering.next(moved, FRAME_W, FRAME_H, nowMs = config.meteringMinIntervalMs - 1))
        assertNotNull(metering.next(moved, FRAME_W, FRAME_H, nowMs = config.meteringMinIntervalMs))
    }

    @Test
    fun `upright to raw undoes raw to upright for every rotation`() {
        val w = 640
        val h = 480
        val p = Point(123f, 45f)
        for (rotation in listOf(0, 90, 180, 270)) {
            val back = FrameGeometry.uprightToRaw(rotation, w, h, FrameGeometry.rawToUpright(rotation, w, h).apply(p))
            assertEquals("$rotation", p.x, back.x, 1e-3f)
            assertEquals("$rotation", p.y, back.y, 1e-3f)
        }
    }
}
