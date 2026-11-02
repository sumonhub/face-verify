package com.sumonlogs.faceverify.core.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceRecordCodecTest {

    @Test
    fun `round trip keeps every person and value`() {
        val records = listOf(
            FaceRecord("id-1", "Alex", 1_000L, "sface-2021dec", listOf(floatArrayOf(0.1f, -0.5f, 2f), floatArrayOf(1f, 2f, 3f))),
            FaceRecord("id-2", "সাম", 2_000L, "mobilefacenet", listOf(floatArrayOf(4f, 5f, 6f))),
        )
        val decoded = FaceRecordCodec.decode(FaceRecordCodec.encode(records))
        requireNotNull(decoded)
        assertEquals(2, decoded.size)
        assertEquals("Alex", decoded[0].name)
        assertEquals("সাম", decoded[1].name)
        assertEquals(2_000L, decoded[1].enrolledAtMillis)
        assertEquals("mobilefacenet", decoded[1].modelId)
        assertArrayEquals(records[0].templates[1], decoded[0].templates[1], 0f)
        assertArrayEquals(records[1].templates[0], decoded[1].templates[0], 0f)
    }

    @Test
    fun `the pipeline version round trips and old blobs read as pipeline 1`() {
        val current = FaceRecord("a", "Alex", 1L, "sface-2021dec", listOf(floatArrayOf(1f, 2f)))
        assertEquals(FaceTemplates.PIPELINE_VERSION, FaceRecordCodec.decode(FaceRecordCodec.encode(listOf(current)))!![0].pipelineVersion)

        val old = FaceRecord("b", "Sam", 2L, "sface-2021dec", listOf(floatArrayOf(3f)), pipelineVersion = 7)
        assertEquals(7, FaceRecordCodec.decode(FaceRecordCodec.encode(listOf(old)))!![0].pipelineVersion)

        // A version 3 blob, written the way the app did before the pipeline version existed.
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { out ->
            out.writeByte(3); out.writeInt(1)
            out.writeUTF("c"); out.writeUTF("Old"); out.writeLong(3L); out.writeUTF("sface-2021dec")
            out.writeInt(1); out.writeInt(2); out.writeFloat(1f); out.writeFloat(2f)
        }
        val legacy = FaceRecordCodec.decode(bytes.toByteArray())!!
        assertEquals(1, legacy[0].pipelineVersion)
        assertFalse("an older pipeline's person is not matched", legacy[0].isUsableWith("sface-2021dec"))
        assertTrue(current.isUsableWith("sface-2021dec"))
        assertFalse(current.isUsableWith("mobilefacenet"))
    }

    @Test
    fun `empty list round trips`() {
        assertTrue(FaceRecordCodec.decode(FaceRecordCodec.encode(emptyList())).orEmpty().isEmpty())
    }

    @Test
    fun `truncated, padded or foreign bytes decode to null`() {
        val bytes = FaceRecordCodec.encode(listOf(FaceRecord("a", "b", 0L, "m", listOf(floatArrayOf(1f, 2f)))))
        assertNull(FaceRecordCodec.decode(bytes.copyOf(bytes.size - 1)))
        assertNull(FaceRecordCodec.decode(bytes + 0))
        assertNull(FaceRecordCodec.decode(byteArrayOf(1, 0, 0, 0, 1)))
        assertNull(FaceRecordCodec.decode(ByteArray(0)))
    }
}
