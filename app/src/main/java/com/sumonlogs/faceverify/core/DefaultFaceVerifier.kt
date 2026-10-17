package com.sumonlogs.faceverify.core

import android.content.Intent
import com.sumonlogs.faceverify.core.api.EnrolledFace
import com.sumonlogs.faceverify.core.api.FaceDiagnostics
import com.sumonlogs.faceverify.core.api.FaceFailure
import com.sumonlogs.faceverify.core.api.FaceMatchResult
import com.sumonlogs.faceverify.core.api.FaceModelSettings
import com.sumonlogs.faceverify.core.api.FaceVerifier
import com.sumonlogs.faceverify.core.engine.FaceRecordStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DefaultFaceVerifier(
    private val store: FaceRecordStore,
    private val settings: FaceModelSettings,
    private val broker: FaceFlowBroker,
    private val activities: ActivityTracker,
    private val diagnostics: FaceDiagnostics,
) : FaceVerifier {

    override val enrolledFaces: Flow<List<EnrolledFace>> = store.records
        .map { records -> records.map { EnrolledFace(it.id, it.name, it.enrolledAtMillis, it.modelId) } }
        .catch { emit(emptyList()) }

    override suspend fun verify(): FaceMatchResult = try {
        val modelId = settings.current().model.id
        if (store.load().none { it.modelId == modelId }) {
            FaceMatchResult.failed(FaceFailure.NotEnrolled).also { report(it) }
        } else {
            broker.run { launchFaceCheck() }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        FaceMatchResult.failed(FaceFailure.EngineError).also { report(it) }
    }

    override suspend fun deleteEnrollment(id: String) {
        try {
            store.delete(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing useful to do: enrolledFaces keeps showing the real state.
        }
    }

    private suspend fun launchFaceCheck(): Boolean = withContext(Dispatchers.Main) {
        val activity = activities.current ?: return@withContext false
        activity.startActivity(Intent(activity, FaceCheckActivity::class.java))
        true
    }

    private fun report(result: FaceMatchResult) {
        diagnostics.update { copy(lastFailure = result.failure, lastScore = result.score, lastMatched = result.matched) }
    }
}
