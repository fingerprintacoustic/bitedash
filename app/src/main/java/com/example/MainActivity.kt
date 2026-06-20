package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.screens.BiteDashMainApp
import com.example.ui.screens.AuthenticationGate
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.BiteDashViewModel
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.UserRole

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val authViewModel: AuthViewModel = viewModel()
        val authState by authViewModel.authState.collectAsState()

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          when (authState) {
            is AuthState.Authenticated -> {
              val currentUser = authViewModel.currentFirestoreUser.value
              val userRole = currentUser?.role?.let { UserRole.fromString(it) } ?: UserRole.CUSTOMER
              
              BiteDashMainApp(
                viewModel = viewModel(),
                authViewModel = authViewModel,
                userRole = userRole
              )
            }
            is AuthState.Loading -> {
              // Loading state handled by AuthenticationGate
              BiteDashMainApp(
                viewModel = viewModel(),
                authViewModel = authViewModel,
                userRole = UserRole.CUSTOMER
              )
            }
            else -> {
              // Show authentication gate
              AuthenticationGate(
                authViewModel = authViewModel,
                onAuthenticated = { vm ->
                  val currentUser = vm.currentFirestoreUser.value
                  val userRole = currentUser?.role?.let { UserRole.fromString(it) } ?: UserRole.CUSTOMER
                  
                  BiteDashMainApp(
                    viewModel = viewModel(),
                    authViewModel = authViewModel,
                    userRole = userRole
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

