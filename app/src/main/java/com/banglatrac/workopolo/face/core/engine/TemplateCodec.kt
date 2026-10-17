package com.banglatrac.workopolo.face.core.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary layout of the enrolled embeddings before encryption:
 * version (1 byte) · count (int) · dimension (int) · count × dimension floats, little-endian.
 */
object TemplateCodec {
    private const val VERSION: Byte = 1

    fun encode(templates: List<FloatArray>): ByteArray {
        require(templates.isNotEmpty()) { "No templates" }
        val dim = templates.first().size
        require(templates.all { it.size == dim }) { "Templates must share one dimension" }
        val buffer = ByteBuffer.allocate(1 + 4 + 4 + templates.size * dim * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(VERSION).putInt(templates.size).putInt(dim)
        templates.forEach { t -> t.forEach { buffer.putFloat(it) } }
        return buffer.array()
    }

    /** Returns null for anything that isn't a well-formed current-version blob. */
    fun decode(bytes: ByteArray): List<FloatArray>? {
        if (bytes.size < 9) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.get() != VERSION) return null
        val count = buffer.int
        val dim = buffer.int
        if (count <= 0 || dim <= 0 || bytes.size.toLong() != 9L + count.toLong() * dim * 4) return null
        return List(count) { FloatArray(dim) { buffer.float } }
    }
}
