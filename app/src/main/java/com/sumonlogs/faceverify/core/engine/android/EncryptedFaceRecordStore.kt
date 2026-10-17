package com.sumonlogs.faceverify.core.engine.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sumonlogs.faceverify.core.engine.FaceRecord
import com.sumonlogs.faceverify.core.engine.FaceRecordCodec
import com.sumonlogs.faceverify.core.engine.FaceRecordStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Base64

/** File name is referenced by res/xml backup rules: datastore/face_templates.preferences_pb. */
val Context.faceTemplateDataStore: DataStore<Preferences> by preferencesDataStore(name = "face_templates")

/**
 * All enrolled people (names and embeddings) as one Keystore-encrypted blob, Base64 in DataStore.
 * Unreadable data (e.g. the Keystore key was invalidated) reads as "nobody enrolled".
 */
class EncryptedFaceRecordStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: KeystoreCipher,
) : FaceRecordStore {

    override val records: Flow<List<FaceRecord>> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[RECORDS] }
        .distinctUntilChanged()
        .map { decrypt(it) }
        .flowOn(Dispatchers.IO)

    override suspend fun load(): List<FaceRecord> = records.first()

    override suspend fun add(record: FaceRecord) = withContext(Dispatchers.IO) {
        dataStore.edit { prefs ->
            prefs[RECORDS] = encrypt(decrypt(prefs[RECORDS]) + record)
            prefs.remove(LEGACY_TEMPLATES)
        }
        Unit
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dataStore.edit { prefs ->
            val remaining = decrypt(prefs[RECORDS]).filterNot { it.id == id }
            if (remaining.isEmpty()) prefs.remove(RECORDS) else prefs[RECORDS] = encrypt(remaining)
        }
        Unit
    }

    private fun encrypt(records: List<FaceRecord>): String =
        Base64.getEncoder().encodeToString(cipher.encrypt(FaceRecordCodec.encode(records)))

    private fun decrypt(encoded: String?): List<FaceRecord> {
        if (encoded == null) return emptyList()
        return try {
            FaceRecordCodec.decode(cipher.decrypt(Base64.getDecoder().decode(encoded))).orEmpty()
        } catch (e: GeneralSecurityException) {
            emptyList()
        } catch (e: IllegalArgumentException) {
            emptyList()
        } catch (e: IllegalStateException) {
            emptyList()
        }
    }

    private companion object {
        val RECORDS = stringPreferencesKey("faces_v2")

        /** Single-person templates from the first testbed build; no longer readable. */
        val LEGACY_TEMPLATES = stringPreferencesKey("templates_v1")
    }
}
