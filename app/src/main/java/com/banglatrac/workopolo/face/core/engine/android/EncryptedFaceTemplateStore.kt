package com.banglatrac.workopolo.face.core.engine.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.banglatrac.workopolo.face.core.engine.FaceTemplateStore
import com.banglatrac.workopolo.face.core.engine.TemplateCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.util.Base64

/** File name is referenced by res/xml backup rules: datastore/face_templates.preferences_pb. */
val Context.faceTemplateDataStore: DataStore<Preferences> by preferencesDataStore(name = "face_templates")

/** Keystore-encrypted embeddings kept as Base64 ciphertext in DataStore Preferences. */
class EncryptedFaceTemplateStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: KeystoreCipher,
) : FaceTemplateStore {

    override suspend fun load(): List<FloatArray>? = withContext(Dispatchers.IO) {
        val encoded = dataStore.data.first()[TEMPLATES] ?: return@withContext null
        try {
            TemplateCodec.decode(cipher.decrypt(Base64.getDecoder().decode(encoded)))
        } catch (e: GeneralSecurityException) {
            // Key invalidated or replaced: the old ciphertext is useless, treat as not enrolled.
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
    }

    override suspend fun save(templates: List<FloatArray>) = withContext(Dispatchers.IO) {
        val encoded = Base64.getEncoder().encodeToString(cipher.encrypt(TemplateCodec.encode(templates)))
        dataStore.edit { it[TEMPLATES] = encoded }
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        dataStore.edit { it.remove(TEMPLATES) }
        cipher.deleteKey()
    }

    private companion object {
        val TEMPLATES = stringPreferencesKey("templates_v1")
    }
}
