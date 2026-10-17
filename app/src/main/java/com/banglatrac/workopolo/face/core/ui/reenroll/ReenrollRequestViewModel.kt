package com.banglatrac.workopolo.face.core.ui.reenroll

import com.banglatrac.workopolo.face.core.api.FaceReenrollmentGateway
import com.banglatrac.workopolo.face.core.mvi.BaseViewModel

class ReenrollRequestViewModel(
    private val gateway: FaceReenrollmentGateway,
) : BaseViewModel<ReenrollRequestIntent, ReenrollRequestState, ReenrollRequestEffect>(ReenrollRequestState()) {

    override fun onIntent(intent: ReenrollRequestIntent) {
        when (intent) {
            is ReenrollRequestIntent.SelectReason -> setState { copy(reason = intent.reason) }
            is ReenrollRequestIntent.NoteChanged -> setState { copy(note = intent.note.take(MAX_NOTE)) }
            ReenrollRequestIntent.Send -> send()
        }
    }

    private fun send() {
        if (currentState.sending) return
        setState { copy(sending = true, sendFailed = false) }
        launchSafely(onError = { setState { copy(sending = false, sendFailed = true) } }) {
            val request = gateway.sendRequest(currentState.reason, currentState.note.trim().ifEmpty { null })
            if (request == null) {
                setState { copy(sending = false, sendFailed = true) }
            } else {
                setState { copy(sending = false) }
                sendEffect(ReenrollRequestEffect.Sent(request))
            }
        }
    }

    private companion object {
        const val MAX_NOTE = 500
    }
}
