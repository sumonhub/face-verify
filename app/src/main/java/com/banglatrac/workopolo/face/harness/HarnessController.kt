package com.banglatrac.workopolo.face.harness

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.banglatrac.workopolo.face.core.api.FaceMatchResult
import com.banglatrac.workopolo.face.core.api.FaceMismatchSummary
import com.banglatrac.workopolo.face.core.api.FaceReenrollmentGateway
import com.banglatrac.workopolo.face.core.api.FaceVerifier
import com.banglatrac.workopolo.face.core.api.ReenrollReason
import com.banglatrac.workopolo.face.core.api.ReenrollRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private val Context.harnessDataStore by preferencesDataStore(name = "harness")

/**
 * Stands in for the main app's attendance records: remembers when face checks didn't match,
 * so the "repeated mismatch" screen can be shown after [REPEATED_MISMATCH_DAYS] such days.
 */
class MismatchLog(private val context: Context) {
    private val key = stringPreferencesKey("mismatch_times")

    val times: Flow<List<Long>> = context.harnessDataStore.data.map { prefs ->
        prefs[key].orEmpty().split(',').mapNotNull { it.toLongOrNull() }
    }

    suspend fun record(at: Long) {
        context.harnessDataStore.edit { prefs ->
            val kept = prefs[key].orEmpty().split(',').mapNotNull { it.toLongOrNull() }
                .filter { at - it < TimeUnit.DAYS.toMillis(WINDOW_DAYS.toLong()) }
            prefs[key] = (listOf(at) + kept).take(10).joinToString(",")
        }
    }

    suspend fun clear() {
        context.harnessDataStore.edit { it.remove(key) }
    }

    companion object {
        const val WINDOW_DAYS = 5
        const val REPEATED_MISMATCH_DAYS = 3

        fun summarise(times: List<Long>, now: Long): FaceMismatchSummary {
            val recent = times.filter { now - it < TimeUnit.DAYS.toMillis(WINDOW_DAYS.toLong()) }.sortedDescending()
            val days = recent.map { TimeUnit.MILLISECONDS.toDays(it) }.distinct().size
            return FaceMismatchSummary(mismatchDays = days, windowDays = WINDOW_DAYS, recentMismatches = recent.take(3))
        }
    }
}

/** Pretends to send the request to an admin. */
class FakeReenrollmentGateway : FaceReenrollmentGateway {
    override suspend fun sendRequest(reason: ReenrollReason, note: String?): ReenrollRequest {
        delay(600)
        return ReenrollRequest(id = "FR-${Random.nextInt(1000, 9999)}", sentAtMillis = System.currentTimeMillis())
    }
}

/** Plain harness state holder: runs the FaceVerifier calls and exposes their outcome. */
class HarnessController(
    private val verifier: FaceVerifier,
    private val mismatchLog: MismatchLog,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _enrolled = MutableStateFlow<Boolean?>(null)
    val enrolled: StateFlow<Boolean?> = _enrolled.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastResult = MutableStateFlow<FaceMatchResult?>(null)
    val lastResult: StateFlow<FaceMatchResult?> = _lastResult.asStateFlow()

    val mismatchSummary: StateFlow<FaceMismatchSummary> = mismatchLog.times
        .map { MismatchLog.summarise(it, System.currentTimeMillis()) }
        .stateIn(scope, SharingStarted.Eagerly, FaceMismatchSummary(0, MismatchLog.WINDOW_DAYS, emptyList()))

    private val _repeatedMismatch = Channel<Unit>(Channel.CONFLATED)

    /** Fires when the latest check was the Nth mismatch in the window, like the main app would. */
    val repeatedMismatch: Flow<Unit> = _repeatedMismatch.receiveAsFlow()

    fun refresh() {
        scope.launch { _enrolled.value = verifier.isEnrolled() }
    }

    fun verify() {
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            try {
                val result = verifier.verify()
                _lastResult.value = result
                if (!result.matched && result.failure == null) {
                    mismatchLog.record(System.currentTimeMillis())
                    val summary = MismatchLog.summarise(mismatchLog.times.first(), System.currentTimeMillis())
                    if (summary.mismatchDays >= MismatchLog.REPEATED_MISMATCH_DAYS) _repeatedMismatch.trySend(Unit)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    /** Debug only: adds a mismatch on an earlier day so the repeated-mismatch screen can be reached quickly. */
    fun addPastMismatch(daysAgo: Int) {
        scope.launch { mismatchLog.record(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysAgo.toLong())) }
    }

    fun onEnrollmentFinished(enrolled: Boolean) {
        scope.launch {
            if (enrolled) mismatchLog.clear()
            refresh()
        }
    }

    fun reset() {
        scope.launch {
            verifier.clearEnrollment()
            mismatchLog.clear()
            _lastResult.value = null
            refresh()
        }
    }
}
