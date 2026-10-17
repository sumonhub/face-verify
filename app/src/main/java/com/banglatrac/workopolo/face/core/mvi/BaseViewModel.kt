package com.banglatrac.workopolo.face.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BaseViewModel<I : UiIntent, S : UiState, E : UiEffect>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    protected val currentState: S get() = _state.value

    abstract fun onIntent(intent: I)

    protected fun setState(reduce: S.() -> S) = _state.update(reduce)

    protected fun sendEffect(effect: E) {
        _effects.trySend(effect)
    }

    /** The only place a coroutine is started on viewModelScope; failures go to [onError], never crash. */
    protected fun launchSafely(
        onError: (Failure) -> Unit = {},
        block: suspend CoroutineScope.() -> Unit,
    ): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            onError(Failure.Unexpected(t))
        }
    }
}
