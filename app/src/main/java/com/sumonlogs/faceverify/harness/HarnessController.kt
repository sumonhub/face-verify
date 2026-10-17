package com.sumonlogs.faceverify.harness

import com.sumonlogs.faceverify.core.api.EnrolledFace
import com.sumonlogs.faceverify.core.api.FaceMatchResult
import com.sumonlogs.faceverify.core.api.FaceVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Plain harness state holder: runs the FaceVerifier calls and exposes their outcome. */
class HarnessController(private val verifier: FaceVerifier) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Null until the first read from storage. */
    val faces: StateFlow<List<EnrolledFace>?> = verifier.enrolledFaces
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastResult = MutableStateFlow<FaceMatchResult?>(null)
    val lastResult: StateFlow<FaceMatchResult?> = _lastResult.asStateFlow()

    fun verify() {
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            try {
                _lastResult.value = verifier.verify()
            } finally {
                _busy.value = false
            }
        }
    }

    fun delete(face: EnrolledFace) {
        scope.launch {
            verifier.deleteEnrollment(face.id)
            if (_lastResult.value?.face?.id == face.id) _lastResult.value = null
        }
    }
}
