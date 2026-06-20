package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AuthenticationService
import com.example.data.firebase.AuthResult
import com.example.data.firebase.FirestoreService
import com.example.data.firebase.FirestoreUser
import com.example.data.firebase.PhoneAuthCallback
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Authentication states for the app.
 */
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    object OtpSent : AuthState() // Phone OTP has been sent
    object OtpVerifying : AuthState() // Verifying OTP
    data class Error(val message: String, val code: String) : AuthState()
}

/**
 * User role enumeration for role-based access control.
 * Supported roles: customer, restaurant, driver, admin
 */
enum class UserRole(val displayName: String, val value: String) {
    CUSTOMER("Customer", "customer"),
    RESTAURANT("Restaurant", "restaurant"),
    DRIVER("Delivery Driver", "driver"),
    ADMIN("Administrator", "admin");

    companion object {
        fun fromString(value: String): UserRole {
            return entries.find { it.value == value } ?: CUSTOMER
        }
    }
}

/**
 * Vehicle types for drivers.
 */
enum class VehicleType(val displayName: String, val value: String) {
    BICYCLE("Bicycle", "Bicycle"),
    MOTORBIKE("Motorbike", "Motorbike"),
    CAR("Car", "Car");

    companion object {
        fun fromString(value: String): VehicleType {
            return entries.find { it.value == value } ?: MOTORBIKE
        }
    }
}

/**
 * ViewModel for authentication functionality.
 * Manages sign-in, sign-up, password reset, phone OTP, and auth state.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authService = AuthenticationService()
    private val firestoreService = FirestoreService()

    // Auth state
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Current user Firestore profile
    private val _currentFirestoreUser = MutableStateFlow<FirestoreUser?>(null)
    val currentFirestoreUser: StateFlow<FirestoreUser?> = _currentFirestoreUser.asStateFlow()

    // Loading state for UI
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Form states
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword: StateFlow<String> = _confirmPassword.asStateFlow()

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _address = MutableStateFlow("")
    val address: StateFlow<String> = _address.asStateFlow()

    // Driver vehicle type
    private val _vehicleType = MutableStateFlow(VehicleType.MOTORBIKE)
    val vehicleType: StateFlow<VehicleType> = _vehicleType.asStateFlow()

    // Selected role during sign up
    private val _selectedRole = MutableStateFlow(UserRole.CUSTOMER)
    val selectedRole: StateFlow<UserRole> = _selectedRole.asStateFlow()

    // Error messages
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Success messages
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // Phone auth state
    private var pendingVerificationId: String? = null
    private var pendingResendToken: PhoneAuthProvider.ForceResendingToken? = null

    init {
        // Check if user is already authenticated - session persistence
        checkAuthState()
    }

    /**
     * Check current authentication state.
     * Called on app start to restore session.
     */
    fun checkAuthState() {
        val user = authService.getCurrentUser()
        if (user != null) {
            _authState.value = AuthState.Authenticated
            loadFirestoreUserProfile(user.uid)
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    /**
     * Update email field.
     */
    fun updateEmail(value: String) {
        _email.value = value
        clearError()
    }

    /**
     * Update password field.
     */
    fun updatePassword(value: String) {
        _password.value = value
        clearError()
    }

    /**
     * Update confirm password field.
     */
    fun updateConfirmPassword(value: String) {
        _confirmPassword.value = value
        clearError()
    }

    /**
     * Update display name field.
     */
    fun updateDisplayName(value: String) {
        _displayName.value = value
        clearError()
    }

    /**
     * Update phone field.
     */
    fun updatePhone(value: String) {
        _phone.value = value
        clearError()
    }

    /**
     * Update address field.
     */
    fun updateAddress(value: String) {
        _address.value = value
        clearError()
    }

    /**
     * Update vehicle type for drivers.
     */
    fun updateVehicleType(type: VehicleType) {
        _vehicleType.value = type
    }

    /**
     * Update selected role.
     */
    fun updateSelectedRole(role: UserRole) {
        _selectedRole.value = role
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Clear success message.
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    /**
     * Sign in with email and password.
     */
    fun signIn(onSuccess: ((String) -> Unit)? = null) {
        if (!validateSignInForm()) return

        viewModelScope.launch {
            _isLoading.value = true
            _authState.value = AuthState.Loading

            when (val result = authService.signIn(_email.value.trim(), _password.value)) {
                is AuthResult.Success -> {
                    _authState.value = AuthState.Authenticated
                    loadFirestoreUserProfile(result.userId)
                    onSuccess?.invoke(result.userId)
                }
                is AuthResult.Error -> {
                    _authState.value = AuthState.Error(result.message, result.code)
                    _errorMessage.value = getUserFriendlyErrorMessage(result.code)
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Send phone OTP for verification (+263 Zimbabwe support).
     */
    fun sendPhoneOtp(onSuccess: (() -> Unit)? = null) {
        val phoneValue = _phone.value.trim()
        
        if (phoneValue.isEmpty()) {
            _errorMessage.value = "Please enter your phone number"
            return
        }

        // Basic validation for Zimbabwe phone numbers
        val digitsOnly = phoneValue.filter { it.isDigit() }
        if (digitsOnly.length < 9) {
            _errorMessage.value = "Please enter a valid phone number"
            return
        }

        _isLoading.value = true
        _authState.value = AuthState.Loading

        authService.signInWithPhone(phoneValue, object : PhoneAuthCallback {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-retrieval succeeded, sign in directly
                viewModelScope.launch {
                    verifyOtpCredential(credential)
                    onSuccess?.invoke()
                }
            }

            override fun onVerificationFailed(error: AuthResult) {
                _isLoading.value = false
                _authState.value = AuthState.Unauthenticated
                _errorMessage.value = getUserFriendlyErrorMessage(error.code)
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                pendingVerificationId = verificationId
                pendingResendToken = token
                _isLoading.value = false
                _authState.value = AuthState.OtpSent
                onSuccess?.invoke()
            }
        })
    }

    /**
     * Verify OTP code from phone authentication.
     */
    fun verifyOtp(code: String, onSuccess: ((String) -> Unit)? = null) {
        val verificationId = pendingVerificationId
        if (verificationId == null) {
            _errorMessage.value = "Verification expired. Please request a new code."
            return
        }

        if (code.length != 6) {
            _errorMessage.value = "Please enter the 6-digit code"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _authState.value = AuthState.OtpVerifying

            when (val result = authService.verifyPhoneOtp(verificationId, code)) {
                is AuthResult.Success -> {
                    // Create or get Firestore user
                    ensureFirestoreUserForPhone(result.userId)
                    _authState.value = AuthState.Authenticated
                    onSuccess?.invoke(result.userId)
                }
                is AuthResult.Error -> {
                    _authState.value = AuthState.OtpSent
                    _errorMessage.value = getUserFriendlyErrorMessage(result.code)
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Verify OTP with credential (auto-retrieval).
     */
    private suspend fun verifyOtpCredential(credential: PhoneAuthCredential) {
        _authState.value = AuthState.OtpVerifying
        // Sign in with the credential
        try {
            val user = authService.getCurrentUser()
            if (user != null) {
                _authState.value = AuthState.Authenticated
                loadFirestoreUserProfile(user.uid)
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Unauthenticated
            _errorMessage.value = "Verification failed"
        }
    }

    /**
     * Ensure Firestore user document exists for phone auth users.
     */
    private fun ensureFirestoreUserForPhone(uid: String) {
        viewModelScope.launch {
            var user = firestoreService.getUser(uid)
            if (user == null) {
                // Create new user document for phone-only auth
                val newUser = FirestoreUser(
                    id = uid,
                    uid = uid,
                    phone = _phone.value.trim(),
                    role = _selectedRole.value.value,
                    displayName = _displayName.value.trim().ifEmpty { "User" }
                )
                firestoreService.createUser(newUser)
                _currentFirestoreUser.value = newUser
            } else {
                _currentFirestoreUser.value = user
            }
        }
    }

    /**
     * Resend OTP code.
     */
    fun resendOtp() {
        val phoneValue = _phone.value.trim()
        if (phoneValue.isEmpty()) {
            _errorMessage.value = "Please enter your phone number"
            return
        }

        _isLoading.value = true

        authService.signInWithPhone(phoneValue, object : PhoneAuthCallback {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                viewModelScope.launch {
                    verifyOtpCredential(credential)
                }
            }

            override fun onVerificationFailed(error: AuthResult) {
                _isLoading.value = false
                _errorMessage.value = getUserFriendlyErrorMessage(error.code)
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                pendingVerificationId = verificationId
                pendingResendToken = token
                _isLoading.value = false
                _successMessage.value = "New verification code sent!"
            }
        })
    }

    /**
     * Cancel phone auth and go back.
     */
    fun cancelPhoneAuth() {
        pendingVerificationId = null
        pendingResendToken = null
        _authState.value = AuthState.Unauthenticated
        clearFormFields()
    }

    /**
     * Sign up a new user with email/password.
     */
    fun signUp(onSuccess: ((String) -> Unit)? = null) {
        if (!validateSignUpForm()) return

        viewModelScope.launch {
            _isLoading.value = true
            _authState.value = AuthState.Loading

            when (val result = authService.signUp(
                _email.value.trim(),
                _password.value,
                _displayName.value.trim()
            )) {
                is AuthResult.Success -> {
                    // Create Firestore user document
                    val firestoreUser = FirestoreUser(
                        id = result.userId,
                        uid = result.userId,
                        email = _email.value.trim(),
                        displayName = _displayName.value.trim(),
                        phone = _phone.value.trim(),
                        address = _address.value.trim(),
                        role = _selectedRole.value.value,
                        restaurantName = if (_selectedRole.value == UserRole.RESTAURANT) _displayName.value.trim() else "",
                        vehicleType = if (_selectedRole.value == UserRole.DRIVER) _vehicleType.value.value else ""
                    )
                    
                    val updateResult = firestoreService.updateUser(firestoreUser)
                    if (!updateResult) {
                        // User created in Firebase Auth but Firestore update failed
                    }

                    _authState.value = AuthState.Authenticated
                    _currentFirestoreUser.value = firestoreUser
                    _successMessage.value = "Account created successfully!"
                    onSuccess?.invoke(result.userId)
                }
                is AuthResult.Error -> {
                    _authState.value = AuthState.Error(result.message, result.code)
                    _errorMessage.value = getUserFriendlyErrorMessage(result.code)
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Send password reset email.
     */
    fun sendPasswordReset(onSuccess: (() -> Unit)? = null) {
        val emailValue = _email.value.trim()
        if (emailValue.isEmpty() || !emailValue.contains("@")) {
            _errorMessage.value = "Please enter a valid email address"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true

            when (val result = authService.sendPasswordResetEmail(emailValue)) {
                is AuthResult.Success -> {
                    _successMessage.value = "Password reset email sent. Check your inbox."
                    onSuccess?.invoke()
                }
                is AuthResult.Error -> {
                    _errorMessage.value = getUserFriendlyErrorMessage(result.code)
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Sign out the current user.
     */
    fun signOut() {
        authService.signOut()
        _authState.value = AuthState.Unauthenticated
        _currentFirestoreUser.value = null
        clearFormFields()
    }

    /**
     * Load Firestore user profile by UID.
     */
    private fun loadFirestoreUserProfile(uid: String) {
        viewModelScope.launch {
            var user = firestoreService.getUser(uid)
            
            // If user not found by UID, try to find by email
            if (user == null) {
                val currentUser = authService.getCurrentUser()
                currentUser?.email?.let { email ->
                    user = firestoreService.getUserByEmail(email)
                }
                // Also try phone number
                if (user == null) {
                    currentUser?.phoneNumber?.let { phone ->
                        user = firestoreService.getUserByPhone(phone)
                    }
                }
            }
            
            _currentFirestoreUser.value = user
        }
    }

    /**
     * Update user profile in Firestore.
     */
    fun updateProfile(
        displayName: String? = null,
        phone: String? = null,
        address: String? = null,
        ecoCashNumber: String? = null,
        oneMoneyNumber: String? = null,
        vehicleType: String? = null,
        restaurantName: String? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        val currentUser = _currentFirestoreUser.value ?: return

        viewModelScope.launch {
            _isLoading.value = true

            val updatedUser = currentUser.copy(
                displayName = displayName ?: currentUser.displayName,
                phone = phone ?: currentUser.phone,
                address = address ?: currentUser.address,
                ecoCashNumber = ecoCashNumber ?: currentUser.ecoCashNumber,
                oneMoneyNumber = oneMoneyNumber ?: currentUser.oneMoneyNumber,
                vehicleType = vehicleType ?: currentUser.vehicleType,
                restaurantName = restaurantName ?: currentUser.restaurantName
            )

            val success = firestoreService.updateUser(updatedUser)
            if (success) {
                _currentFirestoreUser.value = updatedUser
                _successMessage.value = "Profile updated successfully"
                onSuccess?.invoke()
            } else {
                _errorMessage.value = "Failed to update profile"
            }

            _isLoading.value = false
        }
    }

    /**
     * Update user's role (admin function).
     */
    fun updateUserRole(userId: String, newRole: UserRole, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true

            val success = firestoreService.updateUserField(userId, "role", newRole.value)
            if (success) {
                _successMessage.value = "User role updated to ${newRole.displayName}"
                onSuccess?.invoke()
            } else {
                _errorMessage.value = "Failed to update user role"
            }

            _isLoading.value = false
        }
    }

    /**
     * Check if current user has admin privileges.
     */
    fun isAdmin(): Boolean {
        return _currentFirestoreUser.value?.role == UserRole.ADMIN.value
    }

    /**
     * Check if current user is a restaurant owner.
     */
    fun isRestaurantOwner(): Boolean {
        return _currentFirestoreUser.value?.role == UserRole.RESTAURANT.value
    }

    /**
     * Check if current user is a driver.
     */
    fun isDriver(): Boolean {
        return _currentFirestoreUser.value?.role == UserRole.DRIVER.value
    }

    /**
     * Check if current user is a customer.
     */
    fun isCustomer(): Boolean {
        return _currentFirestoreUser.value?.role == UserRole.CUSTOMER.value
    }

    /**
     * Get current user's role.
     */
    fun getCurrentRole(): UserRole {
        val roleValue = _currentFirestoreUser.value?.role ?: UserRole.CUSTOMER.value
        return UserRole.fromString(roleValue)
    }

    /**
     * Get current Firebase user ID.
     */
    fun getCurrentUserId(): String? {
        return authService.getCurrentUserId()
    }

    /**
     * Check if user is authenticated.
     */
    fun isAuthenticated(): Boolean {
        return _authState.value is AuthState.Authenticated
    }

    private fun validateSignInForm(): Boolean {
        val emailValue = _email.value.trim()
        val passwordValue = _password.value

        if (emailValue.isEmpty()) {
            _errorMessage.value = "Please enter your email"
            return false
        }

        if (!emailValue.contains("@") || !emailValue.contains(".")) {
            _errorMessage.value = "Please enter a valid email address"
            return false
        }

        if (passwordValue.isEmpty()) {
            _errorMessage.value = "Please enter your password"
            return false
        }

        if (passwordValue.length < 6) {
            _errorMessage.value = "Password must be at least 6 characters"
            return false
        }

        return true
    }

    private fun validateSignUpForm(): Boolean {
        val emailValue = _email.value.trim()
        val passwordValue = _password.value
        val confirmPasswordValue = _confirmPassword.value
        val displayNameValue = _displayName.value.trim()

        if (emailValue.isEmpty()) {
            _errorMessage.value = "Please enter your email"
            return false
        }

        if (!emailValue.contains("@") || !emailValue.contains(".")) {
            _errorMessage.value = "Please enter a valid email address"
            return false
        }

        if (displayNameValue.isEmpty()) {
            _errorMessage.value = "Please enter your name"
            return false
        }

        if (displayNameValue.length < 2) {
            _errorMessage.value = "Name must be at least 2 characters"
            return false
        }

        if (passwordValue.isEmpty()) {
            _errorMessage.value = "Please enter a password"
            return false
        }

        if (passwordValue.length < 6) {
            _errorMessage.value = "Password must be at least 6 characters"
            return false
        }

        if (passwordValue != confirmPasswordValue) {
            _errorMessage.value = "Passwords do not match"
            return false
        }

        return true
    }

    private fun clearFormFields() {
        _email.value = ""
        _password.value = ""
        _confirmPassword.value = ""
        _displayName.value = ""
        _phone.value = ""
        _address.value = ""
        _selectedRole.value = UserRole.CUSTOMER
        _vehicleType.value = VehicleType.MOTORBIKE
        _errorMessage.value = null
        _successMessage.value = null
        pendingVerificationId = null
        pendingResendToken = null
    }

    private fun getUserFriendlyErrorMessage(code: String): String {
        return when (code) {
            AuthenticationService.ERROR_INVALID_EMAIL -> "Invalid email address format"
            AuthenticationService.ERROR_WRONG_PASSWORD -> "Incorrect password"
            AuthenticationService.ERROR_USER_NOT_FOUND -> "No account found with this email"
            AuthenticationService.ERROR_EMAIL_ALREADY_IN_USE -> "An account with this email already exists"
            AuthenticationService.ERROR_WEAK_PASSWORD -> "Password must be at least 6 characters"
            AuthenticationService.ERROR_NETWORK -> "Network error. Please check your connection"
            AuthenticationService.ERROR_USER_DISABLED -> "This account has been disabled"
            AuthenticationService.ERROR_TOO_MANY_REQUESTS -> "Too many attempts. Please try again later"
            AuthenticationService.ERROR_INVALID_PHONE -> "Invalid phone number. Use format: 0771234567"
            AuthenticationService.ERROR_INVALID_OTP -> "Incorrect verification code"
            else -> "An error occurred. Please try again"
        }
    }
}
