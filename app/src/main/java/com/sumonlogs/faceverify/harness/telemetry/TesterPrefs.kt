package com.sumonlogs.faceverify.harness.telemetry

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

enum class ConsentState { Loading, NotAsked, Granted, Declined }

private val Context.testerDataStore by preferencesDataStore(name = "tester")

/** The tester's choice about sharing data, and a random id that tells one phone's rows from another's. */
class TesterPrefs(private val context: Context) {
    private val consentKey = booleanPreferencesKey("share_consent")
    private val idKey = stringPreferencesKey("install_id")
    private val cropKey = booleanPreferencesKey("share_face_crops")

    private val data = context.testerDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val consent: Flow<ConsentState> = data.map { prefs ->
        when (prefs[consentKey]) {
            null -> ConsentState.NotAsked
            true -> ConsentState.Granted
            false -> ConsentState.Declined
        }
    }

    /** Sharing aligned face crops is its own choice, off until the tester turns it on. */
    val cropConsent: Flow<ConsentState> = data.map { prefs ->
        if (prefs[cropKey] == true) ConsentState.Granted else ConsentState.Declined
    }

    suspend fun setCropConsent(granted: Boolean) {
        context.testerDataStore.edit { it[cropKey] = granted }
    }

    /** Null until the tester has agreed once. Not linked to the person, the phone's hardware or any account. */
    val installId: Flow<String?> = data.map { it[idKey] }

    suspend fun setConsent(granted: Boolean) {
        context.testerDataStore.edit { it[consentKey] = granted }
    }

    /** Creates the random id on first use. */
    suspend fun installId(): String {
        val prefs = context.testerDataStore.edit { if (it[idKey] == null) it[idKey] = UUID.randomUUID().toString() }
        return checkNotNull(prefs[idKey])
    }
}
