package com.sumonlogs.faceverify.core.engine

import kotlinx.coroutines.flow.Flow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Version of everything that turns a camera frame into the pixels a model sees (detector mode, which
 * landmarks align, the crop). Embeddings from different versions are close but not the same, and
 * compare lower, which looks like false rejects. Bump it with any change to that path; people enrolled
 * under another version are listed but not used, and must enrol again. History: 1 = ML Kit fast mode,
 * 5-point fit including the nose; 2 = ML Kit accurate mode, eyes and mouth corners only.
 */
object FaceTemplates {
    const val PIPELINE_VERSION = 2
}

/** One enrolled person: a display name and the front-facing embeddings made by [modelId] under [pipelineVersion]. */
class FaceRecord(
    val id: String,
    val name: String,
    val enrolledAtMillis: Long,
    val modelId: String,
    val templates: List<FloatArray>,
    val pipelineVersion: Int = FaceTemplates.PIPELINE_VERSION,
) {
    /** Only embeddings of the same model AND the same pipeline can be compared. */
    fun isUsableWith(activeModelId: String?): Boolean =
        modelId == activeModelId && pipelineVersion == FaceTemplates.PIPELINE_VERSION
}

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
 * version · count · per record: id · name · enrolledAt (long) · modelId · pipelineVersion (int, v4 on) ·
 * templates · dimension · floats. Strings are modified UTF-8. Version 3 blobs (made before the pipeline
 * version existed) read as pipeline 1, so they show up as "enrol again" instead of vanishing; anything
 * older is not read.
 */
object FaceRecordCodec {
    private const val VERSION = 4
    private const val V3_PIPELINE = 1
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
                out.writeUTF(record.modelId)
                out.writeInt(record.pipelineVersion)
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
            val version = input.readUnsignedByte()
            if (version != VERSION && version != 3) return null
            val count = input.readInt()
            if (count !in 0..MAX_RECORDS) return null
            val records = List(count) {
                val id = input.readUTF()
                val name = input.readUTF()
                val at = input.readLong()
                val modelId = input.readUTF()
                val pipeline = if (version == VERSION) input.readInt() else V3_PIPELINE
                val templateCount = input.readInt()
                val dim = input.readInt()
                if (templateCount !in 1..MAX_TEMPLATES || dim !in 1..MAX_DIMENSION) return null
                FaceRecord(id, name, at, modelId, List(templateCount) { FloatArray(dim) { input.readFloat() } }, pipeline)
            }
            if (input.available() != 0) return null
            records
        }
    } catch (e: IOException) {
        null
    }
}
