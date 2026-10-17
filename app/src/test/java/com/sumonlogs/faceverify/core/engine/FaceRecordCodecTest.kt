package com.sumonlogs.faceverify.core.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceRecordCodecTest {

    @Test
    fun `round trip keeps every person and value`() {
        val records = listOf(
            FaceRecord("id-1", "Alex", 1_000L, listOf(floatArrayOf(0.1f, -0.5f, 2f), floatArrayOf(1f, 2f, 3f))),
            FaceRecord("id-2", "সাম", 2_000L, listOf(floatArrayOf(4f, 5f, 6f))),
        )
        val decoded = FaceRecordCodec.decode(FaceRecordCodec.encode(records))
        requireNotNull(decoded)
        assertEquals(2, decoded.size)
        assertEquals("Alex", decoded[0].name)
        assertEquals("সাম", decoded[1].name)
        assertEquals(2_000L, decoded[1].enrolledAtMillis)
        assertArrayEquals(records[0].templates[1], decoded[0].templates[1], 0f)
        assertArrayEquals(records[1].templates[0], decoded[1].templates[0], 0f)
    }

    @Test
    fun `empty list round trips`() {
        assertTrue(FaceRecordCodec.decode(FaceRecordCodec.encode(emptyList())).orEmpty().isEmpty())
    }

    @Test
    fun `truncated, padded or foreign bytes decode to null`() {
        val bytes = FaceRecordCodec.encode(listOf(FaceRecord("a", "b", 0L, listOf(floatArrayOf(1f, 2f)))))
        assertNull(FaceRecordCodec.decode(bytes.copyOf(bytes.size - 1)))
        assertNull(FaceRecordCodec.decode(bytes + 0))
        assertNull(FaceRecordCodec.decode(byteArrayOf(1, 0, 0, 0, 1)))
        assertNull(FaceRecordCodec.decode(ByteArray(0)))
    }
}
