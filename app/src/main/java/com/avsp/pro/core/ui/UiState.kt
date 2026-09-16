package com.avsp.pro.core.ui

/**
 * Predictable async UI state used by ViewModels / Compose screens.
 */
sealed class UiState<out T> {
    data object Idle : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(
        val message: String,
        val code: String? = null,
        val recoverable: Boolean = true
    ) : UiState<Nothing>()

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isIdle: Boolean get() = this is Idle

    fun getOrNull(): T? = (this as? Success)?.data
}
