package com.banglatrac.workopolo.face.core.ui.reenroll

import com.banglatrac.workopolo.face.core.api.ReenrollReason
import com.banglatrac.workopolo.face.core.api.ReenrollRequest
import com.banglatrac.workopolo.face.core.mvi.UiEffect
import com.banglatrac.workopolo.face.core.mvi.UiIntent
import com.banglatrac.workopolo.face.core.mvi.UiState

sealed interface ReenrollRequestIntent : UiIntent {
    data class SelectReason(val reason: ReenrollReason) : ReenrollRequestIntent
    data class NoteChanged(val note: String) : ReenrollRequestIntent
    data object Send : ReenrollRequestIntent
}

data class ReenrollRequestState(
    val reason: ReenrollReason = ReenrollReason.LookChanged,
    val note: String = "",
    val sending: Boolean = false,
    val sendFailed: Boolean = false,
) : UiState

sealed interface ReenrollRequestEffect : UiEffect {
    data class Sent(val request: ReenrollRequest) : ReenrollRequestEffect
}
