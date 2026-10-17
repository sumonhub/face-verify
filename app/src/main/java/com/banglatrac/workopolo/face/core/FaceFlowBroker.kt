package com.banglatrac.workopolo.face.core

import com.banglatrac.workopolo.face.core.api.FaceFailure
import com.banglatrac.workopolo.face.core.api.FaceMatchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hands the result of [FaceCheckActivity] back to the suspended verify() call.
 * One check at a time; if the caller is cancelled, [waiting] turns false and the activity closes.
 */
class FaceFlowBroker {
    private val mutex = Mutex()
    private var pending: CompletableDeferred<FaceMatchResult>? = null

    private val _waiting = MutableStateFlow(false)
    val waiting: StateFlow<Boolean> = _waiting.asStateFlow()

    suspend fun run(launch: suspend () -> Boolean): FaceMatchResult = mutex.withLock {
        val deferred = CompletableDeferred<FaceMatchResult>()
        pending = deferred
        _waiting.value = true
        try {
            if (!launch()) return@withLock FaceMatchResult.failed(FaceFailure.CameraUnavailable)
            deferred.await()
        } finally {
            pending = null
            _waiting.value = false
        }
    }

    fun deliver(result: FaceMatchResult) {
        pending?.complete(result)
    }
}
