package com.click.recapai

/** A sealed hierarchy describing the state of the text generation. */
sealed interface UiState {

    object Initial : UiState

    object Loading : UiState

    data class Success(val outputText: String) : UiState

    data class Error(val errorMessage: String) : UiState
}
