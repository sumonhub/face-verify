package com.sumonlogs.faceverify.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameGeometryTest {
    private val w = 640
    private val h = 480

    private fun corners(rotation: Int): List<Point> {
        val fit = FrameGeometry.rawToUpright(rotation, w, h)
        return listOf(Point(0f, 0f), Point(w.toFloat(), 0f), Point(0f, h.toFloat()), Point(w.toFloat(), h.toFloat())).map(fit::apply)
    }

    private fun assertPoint(expectedX: Float, expectedY: Float, actual: Point) {
        assertEquals(expectedX, actual.x, 1e-3f)
        assertEquals(expectedY, actual.y, 1e-3f)
    }

    @Test
    fun `no rotation leaves the frame as it is`() {
        val c = corners(0)
        assertPoint(0f, 0f, c[0])
        assertPoint(w.toFloat(), h.toFloat(), c[3])
    }

    @Test
    fun `90 degrees clockwise moves the top-left corner to the top-right`() {
        val c = corners(90)
        assertPoint(h.toFloat(), 0f, c[0])
        assertPoint(h.toFloat(), w.toFloat(), c[1])
        assertPoint(0f, 0f, c[2])
        assertPoint(0f, w.toFloat(), c[3])
    }

    @Test
    fun `180 degrees swaps opposite corners`() {
        val c = corners(180)
        assertPoint(w.toFloat(), h.toFloat(), c[0])
        assertPoint(0f, 0f, c[3])
    }

    @Test
    fun `270 degrees moves the top-left corner to the bottom-left`() {
        val c = corners(270)
        assertPoint(0f, w.toFloat(), c[0])
        assertPoint(0f, 0f, c[1])
        assertPoint(h.toFloat(), w.toFloat(), c[2])
        assertPoint(h.toFloat(), 0f, c[3])
    }

    @Test
    fun `every rotation keeps the frame inside the upright bounds`() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val uprightW = if (rotation % 180 == 0) w else h
            val uprightH = if (rotation % 180 == 0) h else w
            corners(rotation).forEach {
                assertTrue("$rotation: $it", it.x in -1e-3f..uprightW + 1e-3f && it.y in -1e-3f..uprightH + 1e-3f)
            }
        }
    }

    @Test
    fun `enlarged box is the face scaled around its centre`() {
        val box = FrameGeometry.enlargedBox(face(width = 100f), scale = 2.7f, frameWidth = 480, frameHeight = 640)
        assertEquals(270f, box.width, 1e-3f)
        assertEquals(351f, box.height, 1e-3f)
        assertEquals(240f - 135f, box.left, 1e-3f)
        assertEquals(320f - 175.5f, box.top, 1e-3f)
    }

    @Test
    fun `enlarged box slides back inside the frame near an edge`() {
        val box = FrameGeometry.enlargedBox(face(width = 100f, cx = 30f), scale = 2.7f, frameWidth = 480, frameHeight = 640)
        assertEquals(0f, box.left, 1e-3f)
        assertEquals("size is kept, not squeezed", 270f, box.width, 1e-3f)
    }

    @Test
    fun `enlarged box of a huge face shrinks to the frame instead of padding`() {
        val box = FrameGeometry.enlargedBox(face(width = 400f), scale = 4f, frameWidth = 480, frameHeight = 640)
        assertTrue(box.left >= 0f && box.left + box.width <= 479f + 1e-3f)
        assertTrue(box.top >= 0f && box.top + box.height <= 639f + 1e-3f)
    }
}
