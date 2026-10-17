package com.banglatrac.workopolo.face.core.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateCodecTest {

    @Test
    fun `round trip keeps every value`() {
        val templates = listOf(floatArrayOf(0.1f, -0.5f, 2f), floatArrayOf(1f, 2f, 3f))
        val decoded = TemplateCodec.decode(TemplateCodec.encode(templates))
        requireNotNull(decoded)
        assertEquals(2, decoded.size)
        assertArrayEquals(templates[0], decoded[0], 0f)
        assertArrayEquals(templates[1], decoded[1], 0f)
    }

    @Test
    fun `truncated or foreign bytes decode to null`() {
        val bytes = TemplateCodec.encode(listOf(floatArrayOf(1f, 2f)))
        assertNull(TemplateCodec.decode(bytes.copyOf(bytes.size - 1)))
        assertNull(TemplateCodec.decode(byteArrayOf(9, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0)))
        assertNull(TemplateCodec.decode(ByteArray(3)))
    }
}
