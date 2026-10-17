package com.sumonlogs.faceverify.core.mvi

sealed interface Failure {
    data class Unexpected(val cause: Throwable) : Failure
}

sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val failure: Failure) : Result<Nothing>
}
