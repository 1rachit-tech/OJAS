package com.example.data.auth

import android.app.Activity
import android.util.Log
import com.example.data.model.OjasUser
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.StateFlow

/**
 * OJAS authentication contract. Production builds use FirebaseAuthRepository only.
 * There is intentionally no local/in-memory fallback because it can make UI appear
 * authenticated while Firebase is not actually connected.
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>
    val providerName: String
        get() = "Firebase Authentication"
    val isCloudBacked: Boolean
        get() = true

    suspend fun login(emailOrUsername: String, password: String): Result<OjasUser>
    suspend fun signup(email: String, password: String): Result<OjasUser>
    suspend fun completeSetup(displayName: String, username: String, avatarUrl: String? = null): Result<OjasUser>
    suspend fun logout()
    fun checkSession()
    suspend fun getUserProfile(userId: String): Result<OjasUser>
    suspend fun updateUserProfile(
        userId: String,
        displayName: String? = null,
        bio: String? = null,
        avatarUrl: String? = null
    ): Result<OjasUser>
    suspend fun checkUsernameAvailability(username: String, excludeUserId: String? = null): Result<Boolean>

    suspend fun signInWithGoogle(
        idToken: String,
        email: String? = null,
        displayName: String? = null
    ): Result<OjasUser>

    // Kept for binary/source compatibility only. OJAS intentionally disables phone auth.
    suspend fun sendPhoneOtp(
        phoneNumber: String,
        activity: Activity?,
        onCodeSent: (verificationId: String, resendToken: Any?) -> Unit,
        onVerificationFailed: (Exception) -> Unit,
        onAutoVerified: ((OjasUser) -> Unit)? = null
    )

    suspend fun verifyPhoneOtp(
        verificationId: String,
        otpCode: String,
        phoneNumber: String? = null
    ): Result<OjasUser>

    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun recoverOjasId(email: String): Result<OjasUser?>
    suspend fun sendEmailVerification(): Result<Unit>
    fun isEmailVerified(): Boolean
    suspend fun reloadUser(): Result<Boolean>
    suspend fun getLinkedProviders(): List<String>
    suspend fun linkWithGoogle(idToken: String, email: String? = null): Result<OjasUser>
    suspend fun linkWithEmailPassword(email: String, password: String): Result<OjasUser>
    suspend fun linkWithPhone(verificationId: String, otpCode: String, phoneNumber: String? = null): Result<OjasUser>
    suspend fun unlinkProvider(providerId: String): Result<OjasUser>

    companion object {
        private const val TAG = "OJAS_AUTH"

        fun createDefault(): AuthRepository {
            return try {
                val app = FirebaseApp.getInstance()
                FirebaseAuth.getInstance(app)
                Log.d(TAG, "FirebaseApp verified; using FirebaseAuthRepository")
                FirebaseAuthRepository()
            } catch (e: Exception) {
                Log.e(TAG, "Firebase configuration unavailable; local auth fallback is disabled", e)
                FirebaseUnavailableAuthRepository(
                    e.message ?: "Firebase is not initialized. Check app/google-services.json and Firebase configuration."
                )
            }
        }
    }
}

/** Explicit failure state used when Firebase cannot initialize; never authenticates locally. */
private class FirebaseUnavailableAuthRepository(
    private val reason: String
) : AuthRepository {
    private val state = kotlinx.coroutines.flow.MutableStateFlow<AuthState>(AuthState.ConfigMissing(reason))
    override val authState: StateFlow<AuthState> = state
    override val providerName = "Firebase unavailable"
    override val isCloudBacked = false

    private fun <T> unavailable(): Result<T> = Result.failure(IllegalStateException(reason))
    override suspend fun login(emailOrUsername: String, password: String) = unavailable<OjasUser>()
    override suspend fun signup(email: String, password: String) = unavailable<OjasUser>()
    override suspend fun completeSetup(displayName: String, username: String, avatarUrl: String?) = unavailable<OjasUser>()
    override suspend fun logout() { state.value = AuthState.ConfigMissing(reason) }
    override fun checkSession() { state.value = AuthState.ConfigMissing(reason) }
    override suspend fun getUserProfile(userId: String) = unavailable<OjasUser>()
    override suspend fun updateUserProfile(userId: String, displayName: String?, bio: String?, avatarUrl: String?) = unavailable<OjasUser>()
    override suspend fun checkUsernameAvailability(username: String, excludeUserId: String?) = unavailable<Boolean>()
    override suspend fun signInWithGoogle(idToken: String, email: String?, displayName: String?) = unavailable<OjasUser>()
    override suspend fun sendPhoneOtp(phoneNumber: String, activity: Activity?, onCodeSent: (String, Any?) -> Unit, onVerificationFailed: (Exception) -> Unit, onAutoVerified: ((OjasUser) -> Unit)?) { onVerificationFailed(IllegalStateException(reason)) }
    override suspend fun verifyPhoneOtp(verificationId: String, otpCode: String, phoneNumber: String?) = unavailable<OjasUser>()
    override suspend fun sendPasswordResetEmail(email: String) = unavailable<Unit>()
    override suspend fun recoverOjasId(email: String) = unavailable<OjasUser?>()
    override suspend fun sendEmailVerification() = unavailable<Unit>()
    override fun isEmailVerified() = false
    override suspend fun reloadUser() = unavailable<Boolean>()
    override suspend fun getLinkedProviders() = emptyList<String>()
    override suspend fun linkWithGoogle(idToken: String, email: String?) = unavailable<OjasUser>()
    override suspend fun linkWithEmailPassword(email: String, password: String) = unavailable<OjasUser>()
    override suspend fun linkWithPhone(verificationId: String, otpCode: String, phoneNumber: String?) = unavailable<OjasUser>()
    override suspend fun unlinkProvider(providerId: String) = unavailable<OjasUser>()
}
