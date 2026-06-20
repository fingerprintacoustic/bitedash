package com.example.ui.screens.auth

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.AuthViewModel

/**
 * Authentication wrapper that handles sign-in state and displays appropriate screens.
 */
@Composable
fun AuthenticationGate(
    authViewModel: AuthViewModel,
    onAuthenticated: @Composable (authViewModel: AuthViewModel) -> Unit
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    var showSignUp by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }
    var showPhoneLogin by remember { mutableStateOf(false) }

    when (authState) {
        is AuthState.Authenticated -> {
            onAuthenticated(authViewModel)
        }
        is AuthState.Loading -> {
            AuthLoadingScreen()
        }
        is AuthState.OtpSent, is AuthState.OtpVerifying -> {
            OtpScreen(
                authViewModel = authViewModel,
                onNavigateBack = { 
                    authViewModel.cancelPhoneAuth()
                    showPhoneLogin = false
                }
            )
        }
        else -> {
            when {
                showSignUp -> {
                    SignupScreen(
                        authViewModel = authViewModel,
                        onNavigateToSignIn = { showSignUp = false },
                        onNavigateToForgotPassword = { showForgotPassword = true },
                        onNavigateToPhoneLogin = { 
                            showSignUp = false
                            showPhoneLogin = true
                        }
                    )
                }
                showPhoneLogin -> {
                    PhoneLoginScreen(
                        authViewModel = authViewModel,
                        onNavigateBack = { showPhoneLogin = false },
                        onNavigateToSignUp = { 
                            showPhoneLogin = false
                            showSignUp = true
                        }
                    )
                }
                showForgotPassword -> {
                    ForgotPasswordScreen(
                        authViewModel = authViewModel,
                        onNavigateBack = { showForgotPassword = false }
                    )
                }
                else -> {
                    LoginScreen(
                        authViewModel = authViewModel,
                        onNavigateToSignUp = { showSignUp = true },
                        onNavigateToForgotPassword = { showForgotPassword = true },
                        onNavigateToPhoneLogin = { showPhoneLogin = true }
                    )
                }
            }
        }
    }
}
