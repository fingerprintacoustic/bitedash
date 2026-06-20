package com.example.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * BiteDash Authentication Service
 * Handles all Firebase Authentication operations.
 * 
 * Features:
 * - Email/password sign up, sign in, sign out
 * - Phone number authentication with OTP (+263 Zimbabwe support)
 * - Password reset
 * - Auth state observation
 * - User profile updates
 * - Session persistence
 */
class AuthenticationService {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    init {
        // Enable session persistence - users remain logged in after app restart
        auth.setLanguageCode("en")
    }

    companion object {
        // Auth error codes mapped to user-friendly messages
        const val ERROR_INVALID_EMAIL = "ERROR_INVALID_EMAIL"
        const val ERROR_WRONG_PASSWORD = "ERROR_WRONG_PASSWORD"
        const val ERROR_USER_NOT_FOUND = "ERROR_USER_NOT_FOUND"
        const val ERROR_EMAIL_ALREADY_IN_USE = "ERROR_EMAIL_EMAIL_ALREADY_IN_USE"
        const val ERROR_WEAK_PASSWORD = "ERROR_WEAK_PASSWORD"
        const val ERROR_NETWORK = "ERROR_NETWORK"
        const val ERROR_UNKNOWN = "ERROR_UNKNOWN"
        const val ERROR_USER_DISABLED = "ERROR_USER_DISABLED"
        const val ERROR_TOO_MANY_REQUESTS = "ERROR_TOO_MANY_REQUESTS"
        const val ERROR_INVALID_PHONE = "ERROR_INVALID_PHONE"
        const val ERROR_INVALID_OTP = "ERROR_INVALID_OTP"
        const val ERROR_QUOTA_EXCEEDED = "ERROR_QUOTA_EXCEEDED"
        
        // Phone number prefixes for Zimbabwe
        const val ZIMBABWE_PHONE_PREFIX = "+263"
    }

    /**
     * Get the current authenticated Firebase user, if any.
     */
    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    /**
     * Get the current user's UID, if authenticated.
     */
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    /**
     * Check if a user is currently authenticated.
     */
    fun isAuthenticated(): Boolean = auth.currentUser != null

    /**
     * Observe authentication state changes as a Flow.
     * Emits the current user (or null) whenever auth state changes.
     */
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        // Emit current state immediately
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Sign up a new user with email and password.
     * @return Result containing the new user's UID on success.
     */
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                // Update display name
                val profileUpdates = userProfileChangeRequest {
                    this.displayName = displayName
                }
                user.updateProfile(profileUpdates).await()
                AuthResult.Success(user.uid)
            } else {
                AuthResult.Error(ERROR_UNKNOWN, "User creation failed")
            }
        } catch (e: Exception) {
            mapAuthException(e)
        }
    }

    /**
     * Sign in an existing user with email and password.
     * @return Result containing the user's UID on success.
     */
    suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                AuthResult.Success(user.uid)
            } else {
                AuthResult.Error(ERROR_UNKNOWN, "Sign in failed")
            }
        } catch (e: Exception) {
            mapAuthException(e)
        }
    }

    /**
     * Sign in with phone number - sends OTP.
     * @param phoneNumber Phone number in +263 format
     * @param callback Callback for OTP verification
     */
    fun signInWithPhone(
        phoneNumber: String,
        callback: PhoneAuthCallback
    ) {
        val formattedPhone = formatPhoneNumber(phoneNumber)
        
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(formattedPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Auto-retrieval succeeded, sign in directly
                    callback.onVerificationCompleted(credential)
                }

                override fun onVerificationFailed(e: Exception) {
                    callback.onVerificationFailed(mapAuthException(e as Exception))
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    callback.onCodeSent(verificationId, token)
                }
            })
            .build()
        
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /**
     * Verify phone OTP and sign in.
     * @param verificationId The verification ID from onCodeSent
     * @param code The OTP code entered by user
     */
    suspend fun verifyPhoneOtp(
        verificationId: String,
        code: String
    ): AuthResult {
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user
            if (user != null) {
                AuthResult.Success(user.uid)
            } else {
                AuthResult.Error(ERROR_UNKNOWN, "Phone verification failed")
            }
        } catch (e: Exception) {
            mapAuthException(e)
        }
    }

    /**
     * Format phone number to Zimbabwe +263 format.
     */
    private fun formatPhoneNumber(phone: String): String {
        val digitsOnly = phone.filter { it.isDigit() }
        return when {
            digitsOnly.startsWith("263") -> "+$digitsOnly"
            digitsOnly.startsWith("0") -> "+263${digitsOnly.removePrefix("0")}"
            digitsOnly.length == 9 -> "+263$digitsOnly"
            else -> if (phone.startsWith("+")) phone else "+$digitsOnly"
        }
    }

    /**
     * Send a password reset email to the given address.
     */
    suspend fun sendPasswordResetEmail(email: String): AuthResult {
        return try {
            auth.sendPasswordResetEmail(email).await()
            AuthResult.Success("")
        } catch (e: Exception) {
            mapAuthException(e)
        }
    }

    /**
     * Sign out the current user.
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Update the current user's display name.
     */
    suspend fun updateDisplayName(displayName: String): AuthResult {
        return try {
            val user = auth.currentUser
            if (user != null) {
                val profileUpdates = userProfileChangeRequest {
                    this.displayName = displayName
                }
                user.updateProfile(profileUpdates).await()
                AuthResult.Success(user.uid)
            } else {
                AuthResult.Error(ERROR_UNKNOWN, "No user signed in")
            }
        } catch (e: Exception) {
            mapAuthException(e)
        }
    }

    /**
     * Update the current user's email address.
     */
    suspend fun updateEmail(newEmail: String): AuthResult {
        return try {
            val user = auth.currentUser
            if (user != null) {
                user.updateEmail(newEmail).await()
                AuthResult.Success(user.uid)
            } else {
                AuthResult.Error(ERROR_UNKNOWN, "No user signed in")
            }
        } catch (e: Exception) {
            mapAuthException(e)
        }
    }

    /**
     * Re-authenticate user before sensitive operations.
     */
    suspend fun reauthenticate(email: String, password: String): AuthResult {
        return try {
            val user = auth.currentUser
            if (user != null && user.email != null) {
                val credential = com.google.firebase.auth.EmailAuthProvider
                    .getCredential(user.email!!, password)
                user.reauthenticate(credential).await()
                AuthResult.Success(user.uid)
            } else {
                AuthResult.Error(ERROR_UNKNOWN, "No user signed in")
            }
        } catch (e: Exception) {
            mapAuthException(e)
        }
    }

    /**
     * Delete the current user's account.
     */
    suspend fun deleteAccount(): AuthResult {
        return try {
            val user = auth.currentUser
            if (user != null) {
                user.delete().await()
                AuthResult.Success("")
            } else {
                AuthResult.Error(ERROR_UNKNOWN, "No user signed in")
            }
        } catch (e: Exception) {
            mapAuthException(e)
        }
    }

    /**
     * Get user profile data synchronously.
     */
    fun getUserProfile(): UserProfileData? {
        val user = auth.currentUser ?: return null
        return UserProfileData(
            uid = user.uid,
            email = user.email ?: "",
            displayName = user.displayName ?: "",
            photoUrl = user.photoUrl?.toString() ?: "",
            isEmailVerified = user.isEmailVerified,
            phoneNumber = user.phoneNumber ?: ""
        )
    }

    private fun mapAuthException(e: Exception): AuthResult {
        val errorCode = when {
            e.message?.contains("ERROR_INVALID_EMAIL", ignoreCase = true) == true ||
                e.message?.contains("invalid email", ignoreCase = true) == true -> ERROR_INVALID_EMAIL
            e.message?.contains("ERROR_WRONG_PASSWORD", ignoreCase = true) == true ||
                e.message?.contains("wrong password", ignoreCase = true) == true -> ERROR_WRONG_PASSWORD
            e.message?.contains("ERROR_USER_NOT_FOUND", ignoreCase = true) == true ||
                e.message?.contains("user not found", ignoreCase = true) == true -> ERROR_USER_NOT_FOUND
            e.message?.contains("ERROR_EMAIL_ALREADY_IN_USE", ignoreCase = true) == true ||
                e.message?.contains("email already in use", ignoreCase = true) == true -> ERROR_EMAIL_ALREADY_IN_USE
            e.message?.contains("ERROR_WEAK_PASSWORD", ignoreCase = true) == true ||
                e.message?.contains("weak password", ignoreCase = true) == true ||
                e.message?.contains("least 6 characters", ignoreCase = true) == true -> ERROR_WEAK_PASSWORD
            e.message?.contains("ERROR_NETWORK", ignoreCase = true) == true ||
                e.message?.contains("network", ignoreCase = true) == true ||
                e.message?.contains("connection", ignoreCase = true) == true -> ERROR_NETWORK
            e.message?.contains("ERROR_USER_DISABLED", ignoreCase = true) == true ||
                e.message?.contains("user disabled", ignoreCase = true) == true -> ERROR_USER_DISABLED
            e.message?.contains("ERROR_TOO_MANY_REQUESTS", ignoreCase = true) == true ||
                e.message?.contains("too many requests", ignoreCase = true) == true ||
                e.message?.contains("quota exceeded", ignoreCase = true) == true -> ERROR_TOO_MANY_REQUESTS
            e.message?.contains("invalid phone", ignoreCase = true) == true ||
                e.message?.contains("phone number", ignoreCase = true) == true -> ERROR_INVALID_PHONE
            e.message?.contains("invalid verification", ignoreCase = true) == true ||
                e.message?.contains("invalid code", ignoreCase = true) == true ||
                e.message?.contains("wrong", ignoreCase = true) == true -> ERROR_INVALID_OTP
            else -> ERROR_UNKNOWN
        }
        return AuthResult.Error(errorCode, e.message ?: "Unknown error")
    }
}

/**
 * Sealed class representing authentication results.
 */
sealed class AuthResult {
    data class Success(val userId: String) : AuthResult()
    data class Error(val code: String, val message: String) : AuthResult()
}

/**
 * Data class representing basic user profile from Firebase Auth.
 */
data class UserProfileData(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String,
    val isEmailVerified: Boolean,
    val phoneNumber: String = ""
)

/**
 * Callback interface for phone authentication.
 */
interface PhoneAuthCallback {
    fun onVerificationCompleted(credential: PhoneAuthCredential)
    fun onVerificationFailed(error: AuthResult)
    fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken)
}
