package com.sumonlogs.faceverify.harness.telemetry

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

private val Context.testerDataStore by preferencesDataStore(name = "tester")

/** A random id that tells one phone's rows from another's. */
class TesterPrefs(private val context: Context) {
    private val idKey = stringPreferencesKey("install_id")

    private val data = context.testerDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    /** Null until the first record is sent. Not linked to the person, the phone's hardware or any account. */
    val installId: Flow<String?> = data.map { it[idKey] }

    /** Creates the random id on first use. */
    suspend fun installId(): String {
        val prefs = context.testerDataStore.edit { if (it[idKey] == null) it[idKey] = UUID.randomUUID().toString() }
        return checkNotNull(prefs[idKey])
    }
}
