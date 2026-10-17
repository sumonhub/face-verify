package com.banglatrac.workopolo.face.core

import android.content.Intent
import com.banglatrac.workopolo.face.core.api.FaceDiagnostics
import com.banglatrac.workopolo.face.core.api.FaceFailure
import com.banglatrac.workopolo.face.core.api.FaceMatchResult
import com.banglatrac.workopolo.face.core.api.FaceVerifier
import com.banglatrac.workopolo.face.core.engine.FaceTemplateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultFaceVerifier(
    private val store: FaceTemplateStore,
    private val broker: FaceFlowBroker,
    private val activities: ActivityTracker,
    private val diagnostics: FaceDiagnostics,
) : FaceVerifier {

    override suspend fun isEnrolled(): Boolean = try {
        store.load() != null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    override suspend fun verify(): FaceMatchResult = try {
        if (!isEnrolled()) {
            FaceMatchResult.failed(FaceFailure.NotEnrolled).also { report(it) }
        } else {
            broker.run { launchFaceCheck() }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        FaceMatchResult.failed(FaceFailure.EngineError).also { report(it) }
    }

    override suspend fun clearEnrollment() {
        try {
            store.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing useful to do: the next isEnrolled() reports the real state.
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
