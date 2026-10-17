package com.banglatrac.workopolo.face.core.engine

/** Holds the enrolled embeddings. Implementations must keep them encrypted at rest. */
interface FaceTemplateStore {
    /** The enrolled templates, or null if there are none or they can no longer be decrypted. */
    suspend fun load(): List<FloatArray>?

    suspend fun save(templates: List<FloatArray>)

    suspend fun clear()
}
