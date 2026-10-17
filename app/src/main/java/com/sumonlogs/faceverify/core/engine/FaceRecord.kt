package com.sumonlogs.faceverify.core.engine

import kotlinx.coroutines.flow.Flow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** One enrolled person: a display name and one embedding per enrollment pose. */
class FaceRecord(
    val id: String,
    val name: String,
    val enrolledAtMillis: Long,
    val templates: List<FloatArray>,
)

/** Holds the enrolled people. Implementations must keep names and embeddings encrypted at rest. */
interface FaceRecordStore {
    /** Everyone enrolled, oldest first; empty if nothing is stored or it can't be decrypted. */
    val records: Flow<List<FaceRecord>>

    suspend fun load(): List<FaceRecord>

    suspend fun add(record: FaceRecord)

    suspend fun delete(id: String)
}

/**
 * Binary layout of the enrolled people before encryption (big-endian):
 * version · count · per record: id (UTF) · name (UTF) · enrolledAt (long) · templates · dimension · floats.
 */
object FaceRecordCodec {
    private const val VERSION = 2
    private const val MAX_RECORDS = 10_000
    private const val MAX_TEMPLATES = 16
    private const val MAX_DIMENSION = 4096

    fun encode(records: List<FaceRecord>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(VERSION)
            out.writeInt(records.size)
            records.forEach { record ->
                require(record.templates.isNotEmpty()) { "A record needs at least one template" }
                val dim = record.templates.first().size
                require(record.templates.all { it.size == dim }) { "Templates must share one dimension" }
                out.writeUTF(record.id)
                out.writeUTF(record.name)
                out.writeLong(record.enrolledAtMillis)
                out.writeInt(record.templates.size)
                out.writeInt(dim)
                record.templates.forEach { t -> t.forEach(out::writeFloat) }
            }
        }
        return bytes.toByteArray()
    }

    /** Returns null for anything that isn't a well-formed current-version blob. */
    fun decode(bytes: ByteArray): List<FaceRecord>? = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readUnsignedByte() != VERSION) return null
            val count = input.readInt()
            if (count !in 0..MAX_RECORDS) return null
            val records = List(count) {
                val id = input.readUTF()
                val name = input.readUTF()
                val at = input.readLong()
                val templateCount = input.readInt()
                val dim = input.readInt()
                if (templateCount !in 1..MAX_TEMPLATES || dim !in 1..MAX_DIMENSION) return null
                FaceRecord(id, name, at, List(templateCount) { FloatArray(dim) { input.readFloat() } })
            }
            if (input.available() != 0) return null
            records
        }
    } catch (e: IOException) {
        null
    }
}
