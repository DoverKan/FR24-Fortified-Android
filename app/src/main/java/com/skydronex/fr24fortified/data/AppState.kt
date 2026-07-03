package com.skydronex.fr24fortified.data

sealed class AppState {
    data object Loading : AppState()
    data object NeedsSetup : AppState()
    data class Ready(val config: AppConfig) : AppState()
}
