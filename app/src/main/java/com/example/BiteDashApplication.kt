package com.example

import android.app.Application
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.BiteDashViewModel

/**
 * BiteDash Application class for managing app-wide ViewModels.
 * Provides a centralized way to access shared ViewModels across the app.
 */
class BiteDashApplication : Application() {

    // Lazy initialization of ViewModels to avoid creating them too early
    val biteDashViewModel: BiteDashViewModel by lazy {
        BiteDashViewModel(this)
    }

    val authViewModel: AuthViewModel by lazy {
        AuthViewModel(this)
    }
}

/**
 * Sealed class representing combined app state with authentication.
 */
sealed class AppState {
    object Loading : AppState()
    object AuthRequired : AppState()
    data class Authenticated(val isAuthenticated: Boolean) : AppState()
    data class Error(val message: String) : AppState()
}
