package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.screens.BiteDashMainApp
import com.example.ui.screens.auth.AuthenticationGate
import com.example.ui.screens.SplashScreen
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.BiteDashViewModel
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.UserRole

class MainActivity : ComponentActivity() {
  
  // Track if we've shown the splash screen
  private var showSplash by mutableStateOf(true)
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val authViewModel: AuthViewModel = viewModel()
        val authState by authViewModel.authState.collectAsState()
        
        // Show splash screen first, then navigate to main content
        if (showSplash) {
          SplashScreen(
            onNavigateToMain = { showSplash = false }
          )
        } else {
          Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (authState) {
              is AuthState.Authenticated -> {
                val currentUser by authViewModel.currentFirestoreUser.collectAsState()
                val userRole = currentUser?.role?.let { UserRole.fromString(it) } ?: UserRole.CUSTOMER
                
                BiteDashMainApp(
                  viewModel = viewModel(),
                  authViewModel = authViewModel,
                  userRole = userRole,
                  modifier = Modifier.padding(innerPadding)
                )
              }
              else -> {
                // AuthenticationGate handles Loading/OtpSent/OtpVerifying itself.
                // Intercepting OtpSent here used to hide the OTP entry screen
                // behind a permanent "Please wait..." spinner, and swapping the
                // gate out during Loading reset its screen state (so a failed
                // phone sign-in dumped the user back on the email login screen).
                AuthenticationGate(
                  authViewModel = authViewModel,
                  onAuthenticated = { vm ->
                    val currentUser by vm.currentFirestoreUser.collectAsState()
                    val userRole = currentUser?.role?.let { UserRole.fromString(it) } ?: UserRole.CUSTOMER
                    
                    BiteDashMainApp(
                      viewModel = viewModel(),
                      authViewModel = authViewModel,
                      userRole = userRole,
                      modifier = Modifier.padding(innerPadding)
                    )
                  }
                )
              }
            }
          }
        }
      }
    }
  }
}

