package com.sumonlogs.faceverify.core.api

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sumonlogs.faceverify.core.engine.FaceModelSpec
import com.sumonlogs.faceverify.core.engine.FaceModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** The model face checks and enrollments use right now, with its match threshold. */
data class ActiveFaceModel(val model: FaceModelSpec, val threshold: Float) {
    val isDefaultThreshold: Boolean get() = threshold == model.defaultThreshold
}

/**
 * Which face model is used and how strict it is. Meant for testing and calibration; an app that
 * ships one model can ignore it and get [FaceModels.default] with its default threshold.
 */
interface FaceModelSettings {
    val active: Flow<ActiveFaceModel>

    suspend fun current(): ActiveFaceModel

    suspend fun selectModel(modelId: String)

    /** Overrides the threshold of [modelId]; null restores the model's default. */
    suspend fun setThreshold(modelId: String, threshold: Float?)
}

private val Context.faceSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "face_settings")

class DataStoreFaceModelSettings(context: Context) : FaceModelSettings {
    private val dataStore = context.faceSettingsDataStore

    override val active: Flow<ActiveFaceModel> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val model = FaceModels.byId(prefs[MODEL]) ?: FaceModels.default
            ActiveFaceModel(model, prefs[thresholdKey(model.id)] ?: model.defaultThreshold)
        }

    override suspend fun current(): ActiveFaceModel = active.first()

    override suspend fun selectModel(modelId: String) {
        if (FaceModels.byId(modelId) == null) return
        dataStore.edit { it[MODEL] = modelId }
    }

    override suspend fun setThreshold(modelId: String, threshold: Float?) {
        dataStore.edit { prefs ->
            if (threshold == null) prefs.remove(thresholdKey(modelId)) else prefs[thresholdKey(modelId)] = threshold.coerceIn(0f, 1f)
        }
    }

    private companion object {
        val MODEL = stringPreferencesKey("model")

        fun thresholdKey(modelId: String) = floatPreferencesKey("threshold_$modelId")
    }
}
