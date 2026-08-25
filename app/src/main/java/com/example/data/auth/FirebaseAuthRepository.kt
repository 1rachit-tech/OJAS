package com.example.data.auth

import android.net.Uri
import com.example.data.model.OjasUser
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.FirebaseException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Real Firebase-backed implementation of [AuthRepository].
 * Handles cloud authentication via Firebase Authentication (Email/Password),
 * session observation, user credential verification, and profile binding.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth? = runCatching { 
        val app = FirebaseApp.getInstance()
        FirebaseAuth.getInstance(app)
    }.getOrNull()
) : AuthRepository {

    override val providerName: String = "Firebase Authentication"
    override val isCloudBacked: Boolean = true

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val authMutex = Mutex()
    private val userProfileStore = ConcurrentHashMap<String, OjasUser>()
    private val usernameToEmailMap = ConcurrentHashMap<String, String>()
    private val lastOtpRequestTimestamp = ConcurrentHashMap<String, Long>()
    private val lastPasswordResetTimestamp = ConcurrentHashMap<String, Long>()
    private val lastEmailVerificationTimestamp = ConcurrentHashMap<String, Long>()

    private val reservedUsernames = setOf(
        "admin", "administrator", "ojas", "official", "support", "root",
        "system", "null", "api", "security", "moderator", "mod", "staff", "help"
    )

    init {
        val currentAuth = auth
        if (currentAuth != null) {
            currentAuth.addAuthStateListener { firebaseAuth ->
                val currentUser = firebaseAuth.currentUser
                if (currentUser == null) {
                    _authState.value = AuthState.Unauthenticated
                } else {
                    val profile = loadOrConstructProfile(currentUser)
                    if (profile.isSetupComplete) {
                        _authState.value = AuthState.Authenticated(profile)
                    } else {
                        _authState.value = AuthState.SetupRequired(profile)
                    }
                }
            }
        } else {
            _authState.value = AuthState.ConfigMissing("Firebase Authentication is not initialized. Cloud credentials required.")
        }
    }

    override fun checkSession() {
        val currentAuth = auth ?: run {
            _authState.value = AuthState.ConfigMissing("Firebase Authentication is not initialized.")
            return
        }

        val firebaseUser = currentAuth.currentUser
        if (firebaseUser == null) {
            _authState.value = AuthState.Unauthenticated
        } else {
            val profile = loadOrConstructProfile(firebaseUser)
            if (profile.isSetupComplete) {
                _authState.value = AuthState.Authenticated(profile)
            } else {
                _authState.value = AuthState.SetupRequired(profile)
            }
        }
    }

    override suspend fun signup(email: String, password: String): Result<OjasUser> = authMutex.withLock {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication provider is not configured. Cloud credentials required.")
        )

        val trimmedEmail = email.trim().lowercase()
        val trimmedPassword = password.trim()

        if (trimmedEmail.isBlank() || trimmedPassword.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter email and password."))
        }

        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        if (!emailRegex.matches(trimmedEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }

        if (trimmedPassword.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }

        return try {
            val authResult = currentAuth.createUserWithEmailAndPassword(trimmedEmail, trimmedPassword).awaitTask()
            val firebaseUser = authResult.user
                ?: return Result.failure(IllegalStateException("Firebase user creation returned null identity."))

            // Dispatch real Firebase email verification immediately upon account creation
            try {
                firebaseUser.sendEmailVerification().awaitTask()
                lastEmailVerificationTimestamp[trimmedEmail] = System.currentTimeMillis()
                android.util.Log.i("FirebaseAuthRepo", "Verification email dispatched via Firebase to $trimmedEmail")
            } catch (e: Exception) {
                android.util.Log.w("FirebaseAuthRepo", "Auto-dispatch verification email failed: ${e.javaClass.simpleName}: ${e.message}", e)
            }

            val newUser = OjasUser(
                userId = firebaseUser.uid,
                username = "",
                displayName = firebaseUser.displayName ?: "",
                bio = "",
                avatarUrl = firebaseUser.photoUrl?.toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isSetupComplete = false,
                email = firebaseUser.email
            )

            userProfileStore[firebaseUser.uid] = newUser
            _authState.value = AuthState.SetupRequired(newUser)
            Result.success(newUser)
        } catch (e: FirebaseAuthUserCollisionException) {
            android.util.Log.e("FirebaseAuthRepo", "Signup collision: ${e.javaClass.simpleName}", e)
            Result.failure(IllegalArgumentException("An account with this email already exists. Please log in."))
        } catch (e: FirebaseAuthWeakPasswordException) {
            android.util.Log.e("FirebaseAuthRepo", "Signup weak password: ${e.javaClass.simpleName}", e)
            Result.failure(IllegalArgumentException("Password is too weak. Please use at least 6 characters."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            android.util.Log.e("FirebaseAuthRepo", "Signup invalid credentials: ${e.javaClass.simpleName}", e)
            Result.failure(IllegalArgumentException("The email address is badly formatted."))
        } catch (e: FirebaseNetworkException) {
            android.util.Log.e("FirebaseAuthRepo", "Signup network failure", e)
            Result.failure(IllegalStateException("Network connection failed. Please check your internet connection."))
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthRepo", "Signup exception: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun login(emailOrUsername: String, password: String): Result<OjasUser> = authMutex.withLock {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication provider is not configured. Cloud credentials required.")
        )

        val trimmedQuery = emailOrUsername.trim()
        val trimmedPassword = password.trim()

        if (trimmedQuery.isBlank() || trimmedPassword.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter both email/username and password."))
        }

        val cleanedUsername = trimmedQuery.removePrefix("@").lowercase()
        val resolvedEmail = if (trimmedQuery.contains("@") && trimmedQuery.contains(".")) {
            trimmedQuery.lowercase()
        } else {
            usernameToEmailMap[cleanedUsername]
                ?: return Result.failure(
                    IllegalArgumentException("Please sign in with your registered email address for Firebase Authentication.")
                )
        }

        return try {
            val authResult = currentAuth.signInWithEmailAndPassword(resolvedEmail, trimmedPassword).awaitTask()
            val firebaseUser = authResult.user
                ?: return Result.failure(IllegalStateException("Firebase sign in returned null user."))

            val profile = loadOrConstructProfile(firebaseUser)
            if (profile.isSetupComplete) {
                _authState.value = AuthState.Authenticated(profile)
            } else {
                _authState.value = AuthState.SetupRequired(profile)
            }
            Result.success(profile)
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.failure(IllegalArgumentException("No account found with this email. Please check your credentials or sign up."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(IllegalArgumentException("Incorrect password or invalid email. Please try again."))
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("Network connection failed. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun completeSetup(
        displayName: String,
        username: String,
        avatarUrl: String?
    ): Result<OjasUser> = authMutex.withLock {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication provider is not configured.")
        )

        val firebaseUser = currentAuth.currentUser
            ?: return Result.failure(IllegalStateException("No active Firebase session found for profile setup."))

        val trimmedDisplayName = displayName.trim()
        val cleanedUsername = username.trim().removePrefix("@").lowercase()

        if (trimmedDisplayName.isBlank()) {
            return Result.failure(IllegalArgumentException("Display Name cannot be empty."))
        }
        if (cleanedUsername.isBlank() || !cleanedUsername.matches(Regex("^[a-zA-Z0-9_]{3,30}$"))) {
            return Result.failure(IllegalArgumentException("Username must be 3-30 alphanumeric characters or underscores."))
        }
        if (reservedUsernames.contains(cleanedUsername)) {
            return Result.failure(IllegalArgumentException("Username @$cleanedUsername is reserved by system."))
        }

        val isUsernameTaken = userProfileStore.values.any {
            it.userId != firebaseUser.uid && it.username.equals(cleanedUsername, ignoreCase = true)
        }
        if (isUsernameTaken) {
            return Result.failure(IllegalArgumentException("Username @$cleanedUsername is already taken. Please choose another."))
        }

        return try {
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(trimmedDisplayName)
                .apply {
                    if (!avatarUrl.isNullOrBlank()) {
                        setPhotoUri(Uri.parse(avatarUrl))
                    }
                }
                .build()

            firebaseUser.updateProfile(profileUpdates).awaitTask()

            val existing = userProfileStore[firebaseUser.uid]
            val updated = (existing ?: loadOrConstructProfile(firebaseUser)).copy(
                displayName = trimmedDisplayName,
                username = cleanedUsername,
                avatarUrl = avatarUrl ?: existing?.avatarUrl,
                updatedAt = System.currentTimeMillis(),
                isSetupComplete = true
            )

            userProfileStore[firebaseUser.uid] = updated
            val userEmail = firebaseUser.email
            if (!userEmail.isNullOrBlank()) {
                usernameToEmailMap[cleanedUsername.lowercase()] = userEmail.lowercase()
            }

            _authState.value = AuthState.Authenticated(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        authMutex.withLock {
            auth?.signOut()
            _authState.value = AuthState.Unauthenticated
        }
    }

    override suspend fun getUserProfile(userId: String): Result<OjasUser> {
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID cannot be empty."))
        }
        val user = userProfileStore[userId]
        return if (user != null) {
            Result.success(user)
        } else {
            val current = auth?.currentUser
            if (current != null && current.uid == userId) {
                Result.success(loadOrConstructProfile(current))
            } else {
                Result.failure(NoSuchElementException("User profile not found for ID: $userId"))
            }
        }
    }

    override suspend fun updateUserProfile(
        userId: String,
        displayName: String?,
        bio: String?,
        avatarUrl: String?
    ): Result<OjasUser> = authMutex.withLock {
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID cannot be empty."))
        }
        val existing = userProfileStore[userId]
            ?: return Result.failure(NoSuchElementException("User profile not found."))

        val updated = existing.copy(
            displayName = displayName?.trim()?.takeIf { it.isNotBlank() } ?: existing.displayName,
            bio = bio?.trim() ?: existing.bio,
            avatarUrl = avatarUrl ?: existing.avatarUrl,
            updatedAt = System.currentTimeMillis()
        )
        userProfileStore[userId] = updated

        val state = _authState.value
        if (state is AuthState.Authenticated && state.user.userId == userId) {
            _authState.value = AuthState.Authenticated(updated)
        }

        Result.success(updated)
    }

    override suspend fun checkUsernameAvailability(
        username: String,
        excludeUserId: String?
    ): Result<Boolean> {
        val cleaned = username.trim().removePrefix("@").lowercase()
        if (cleaned.length < 3 || cleaned.length > 30 || !cleaned.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return Result.failure(IllegalArgumentException("Invalid username format. Must be 3-30 alphanumeric characters or underscores."))
        }
        if (reservedUsernames.contains(cleaned)) {
            return Result.success(false)
        }

        val isTaken = userProfileStore.values.any {
            it.userId != excludeUserId && it.username.equals(cleaned, ignoreCase = true)
        }
        return Result.success(!isTaken)
    }

    override suspend fun sendEmailVerification(): Result<Unit> = authMutex.withLock {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication provider is not configured.")
        )
        val currentUser = currentAuth.currentUser ?: return Result.failure(
            IllegalStateException("Must be signed in to send a verification email.")
        )
        val userEmail = currentUser.email.orEmpty().trim().lowercase()
        if (userEmail.isBlank()) {
            return Result.failure(IllegalStateException("No email address is associated with this account."))
        }

        // Anti-Abuse: 60-second cooldown per email
        val now = System.currentTimeMillis()
        val lastSent = lastEmailVerificationTimestamp[userEmail] ?: 0L
        if (now - lastSent < 60_000L) {
            val remaining = ((60_000L - (now - lastSent)) / 1000L).coerceAtLeast(1)
            return Result.failure(IllegalStateException("Please wait $remaining seconds before requesting another verification email."))
        }

        return try {
            currentUser.sendEmailVerification().awaitTask()
            lastEmailVerificationTimestamp[userEmail] = now
            android.util.Log.i("FirebaseAuthRepo", "Verification email dispatched via Firebase to $userEmail")
            Result.success(Unit)
        } catch (e: FirebaseNetworkException) {
            android.util.Log.e("FirebaseAuthRepo", "sendEmailVerification: Network failure", e)
            Result.failure(IllegalStateException("Network connection failed. Please check your internet connection."))
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthRepo", "sendEmailVerification failed: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun isEmailVerified(): Boolean {
        return auth?.currentUser?.isEmailVerified ?: false
    }

    override suspend fun reloadUser(): Result<Boolean> = authMutex.withLock {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication provider is not configured.")
        )
        val currentUser = currentAuth.currentUser ?: return Result.failure(
            IllegalStateException("No active Firebase session to reload.")
        )

        return try {
            currentUser.reload().awaitTask()
            val isVerified = currentUser.isEmailVerified
            val profile = loadOrConstructProfile(currentUser, forceRefresh = true)
            if (profile.isSetupComplete) {
                _authState.value = AuthState.Authenticated(profile)
            } else {
                _authState.value = AuthState.SetupRequired(profile)
            }
            android.util.Log.i("FirebaseAuthRepo", "Firebase currentUser reloaded: isEmailVerified=$isVerified")
            Result.success(isVerified)
        } catch (e: FirebaseNetworkException) {
            android.util.Log.e("FirebaseAuthRepo", "reloadUser: Network failure", e)
            Result.failure(IllegalStateException("Network connection failed. Please check your internet connection."))
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthRepo", "reloadUser failed: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun linkWithEmailPassword(email: String, password: String): Result<OjasUser> = authMutex.withLock {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication is not configured.")
        )
        val currentUser = currentAuth.currentUser ?: return Result.failure(
            IllegalStateException("User must be signed in to link email and password.")
        )
        val trimmedEmail = email.trim().lowercase()
        val trimmedPassword = password.trim()
        if (trimmedEmail.isBlank() || trimmedPassword.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter both email and password."))
        }
        if (trimmedPassword.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }

        return try {
            val credential = EmailAuthProvider.getCredential(trimmedEmail, trimmedPassword)
            val authResult = currentUser.linkWithCredential(credential).awaitTask()
            val linkedUser = authResult.user ?: currentUser
            val profile = loadOrConstructProfile(linkedUser)
            _authState.value = AuthState.Authenticated(profile)
            Result.success(profile)
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(IllegalArgumentException("This email is already linked to another OJAS account."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(IllegalArgumentException("Invalid email or password format."))
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("Network connection failed. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(
        idToken: String,
        email: String?,
        displayName: String?
    ): Result<OjasUser> = authMutex.withLock {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication provider is not configured.")
        )

        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Google ID token cannot be blank."))
        }

        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = currentAuth.signInWithCredential(credential).awaitTask()
            val firebaseUser = authResult.user
                ?: return Result.failure(IllegalStateException("Firebase Google sign-in returned null user identity."))

            val profile = loadOrConstructProfile(firebaseUser)
            if (profile.isSetupComplete) {
                _authState.value = AuthState.Authenticated(profile)
            } else {
                _authState.value = AuthState.SetupRequired(profile)
            }
            Result.success(profile)
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(IllegalArgumentException("An account is already associated with this email. Please sign in with your original method."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(IllegalArgumentException("Invalid Google credentials. Please try signing in again."))
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("Network connection failed. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPhoneOtp(
        phoneNumber: String,
        activity: android.app.Activity?,
        onCodeSent: (verificationId: String, resendToken: Any?) -> Unit,
        onVerificationFailed: (Exception) -> Unit,
        onAutoVerified: ((OjasUser) -> Unit)?
    ) {
        val currentAuth = auth ?: run {
            onVerificationFailed(IllegalStateException("Firebase Authentication provider is not configured."))
            return
        }

        val trimmedPhone = phoneNumber.trim().replace(" ", "")
        if (trimmedPhone.length < 8) {
            onVerificationFailed(IllegalArgumentException("Please enter a valid phone number with country code (e.g. +1234567890)."))
            return
        }

        // Anti-Abuse Rate Limiting: 60-second cooldown per phone number
        val now = System.currentTimeMillis()
        val lastSent = lastOtpRequestTimestamp[trimmedPhone] ?: 0L
        if (now - lastSent < 60_000L) {
            val remainingSec = ((60_000L - (now - lastSent)) / 1000L).coerceAtLeast(1)
            onVerificationFailed(IllegalStateException("Please wait $remainingSec seconds before requesting a new verification code."))
            return
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val authResult = currentAuth.signInWithCredential(credential).awaitTask()
                        val firebaseUser = authResult.user
                        if (firebaseUser != null) {
                            val profile = loadOrConstructProfile(firebaseUser)
                            if (profile.isSetupComplete) {
                                _authState.value = AuthState.Authenticated(profile)
                            } else {
                                _authState.value = AuthState.SetupRequired(profile)
                            }
                            onAutoVerified?.invoke(profile)
                        }
                    } catch (e: Exception) {
                        onVerificationFailed(e)
                    }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                val rawMessage = e.message.orEmpty()
                val diagnosedException = when {
                    rawMessage.contains("17006", ignoreCase = true) ||
                    rawMessage.contains("region enabled", ignoreCase = true) ||
                    rawMessage.contains("SMS unable to be sent", ignoreCase = true) -> {
                        IllegalStateException(
                            "SMS is disabled for this region (Code 17006). In Firebase Console (ojas-e8161) > Authentication > Settings > 'SMS Region Policy', enable your country (e.g. India +91), or add 'Phone numbers for testing' under Authentication > Sign-in method > Phone."
                        )
                    }
                    rawMessage.contains("This operation is not allowed", ignoreCase = true) ||
                    rawMessage.contains("ERROR_OPERATION_NOT_ALLOWED", ignoreCase = true) -> {
                        IllegalStateException(
                            "Phone authentication is not yet enabled or authorized for this build. In Firebase Console (project: ojas-e8161), confirm Phone provider is enabled under Authentication > Sign-in method, add the SHA-1/SHA-256 fingerprint under Project Settings > General > Your apps (com.rachit.ojas), or add 'Phone numbers for testing' in Firebase Console."
                        )
                    }
                    rawMessage.contains("INVALID_CERT_HASH", ignoreCase = true) ||
                    rawMessage.contains("app is not authorized", ignoreCase = true) ||
                    rawMessage.contains("Play Integrity", ignoreCase = true) ||
                    rawMessage.contains("SafetyNet", ignoreCase = true) ||
                    rawMessage.contains("reCAPTCHA", ignoreCase = true) -> {
                        IllegalStateException(
                            "Device integrity verification could not be completed on this emulator/device. To test phone login on emulators without SMS, add test phone numbers (e.g. +91 99999 99999 with code 123456) in Firebase Console > Authentication > Sign-in method > Phone."
                        )
                    }
                    rawMessage.contains("quota", ignoreCase = true) ||
                    rawMessage.contains("blocked", ignoreCase = true) -> {
                        IllegalStateException(
                            "SMS quota exceeded or requests from this device blocked. Please try again later or use test phone numbers."
                        )
                    }
                    else -> e
                }
                onVerificationFailed(diagnosedException)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                lastOtpRequestTimestamp[trimmedPhone] = System.currentTimeMillis()
                onCodeSent(verificationId, token)
            }
        }

        val builder = PhoneAuthOptions.newBuilder(currentAuth)
            .setPhoneNumber(trimmedPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setCallbacks(callbacks)

        if (activity != null) {
            builder.setActivity(activity)
        }

        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    override suspend fun verifyPhoneOtp(
        verificationId: String,
        otpCode: String,
        phoneNumber: String?
    ): Result<OjasUser> = authMutex.withLock {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication provider is not configured.")
        )

        val trimmedOtp = otpCode.trim()
        if (trimmedOtp.length != 6 || !trimmedOtp.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Please enter the complete 6-digit verification code."))
        }

        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, trimmedOtp)
            val authResult = currentAuth.signInWithCredential(credential).awaitTask()
            val firebaseUser = authResult.user
                ?: return Result.failure(IllegalStateException("Phone verification returned null user identity."))

            val profile = loadOrConstructProfile(firebaseUser)
            if (profile.isSetupComplete) {
                _authState.value = AuthState.Authenticated(profile)
            } else {
                _authState.value = AuthState.SetupRequired(profile)
            }
            Result.success(profile)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(IllegalArgumentException("The verification code entered is invalid or has expired. Please try again."))
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("Network connection failed. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = authMutex.withLock {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication provider is not configured.")
        )
        val trimmedEmail = email.trim().lowercase()
        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        if (!emailRegex.matches(trimmedEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }

        // Anti-Abuse: 30-second cooldown per email
        val now = System.currentTimeMillis()
        val lastSent = lastPasswordResetTimestamp[trimmedEmail] ?: 0L
        if (now - lastSent < 30_000L) {
            val remaining = ((30_000L - (now - lastSent)) / 1000L).coerceAtLeast(1)
            return Result.failure(IllegalStateException("Please wait $remaining seconds before requesting another password reset email."))
        }

        return try {
            currentAuth.sendPasswordResetEmail(trimmedEmail).awaitTask()
            lastPasswordResetTimestamp[trimmedEmail] = now
            android.util.Log.i("FirebaseAuthRepo", "Password reset email dispatched via Firebase to $trimmedEmail")
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidUserException) {
            // Neutral response to protect user privacy and prevent account enumeration
            android.util.Log.w("FirebaseAuthRepo", "sendPasswordResetEmail: User not found for $trimmedEmail", e)
            lastPasswordResetTimestamp[trimmedEmail] = now
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            android.util.Log.e("FirebaseAuthRepo", "sendPasswordResetEmail: Invalid credentials/email", e)
            Result.failure(IllegalArgumentException("Please enter a valid email address."))
        } catch (e: FirebaseNetworkException) {
            android.util.Log.e("FirebaseAuthRepo", "sendPasswordResetEmail: Network failure", e)
            Result.failure(IllegalStateException("Network connection failed. Please check your internet connection."))
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthRepo", "sendPasswordResetEmail failed: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getLinkedProviders(): List<String> {
        val currentUser = auth?.currentUser ?: return emptyList()
        return currentUser.providerData.mapNotNull { it.providerId }.filter { it.isNotBlank() && it != "firebase" }.distinct()
    }

    override suspend fun linkWithGoogle(idToken: String, email: String?): Result<OjasUser> = authMutex.withLock {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication is not configured.")
        )
        val currentUser = currentAuth.currentUser ?: return Result.failure(
            IllegalStateException("User must be signed in to link a Google account.")
        )
        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid Google credential provided."))
        }

        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = currentUser.linkWithCredential(credential).awaitTask()
            val linkedUser = authResult.user ?: currentUser
            val profile = loadOrConstructProfile(linkedUser)
            _authState.value = AuthState.Authenticated(profile)
            Result.success(profile)
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(IllegalArgumentException("This Google account is already linked to another OJAS account."))
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("Network connection failed. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun linkWithPhone(
        verificationId: String,
        otpCode: String,
        phoneNumber: String?
    ): Result<OjasUser> = authMutex.withLock {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication is not configured.")
        )
        val currentUser = currentAuth.currentUser ?: return Result.failure(
            IllegalStateException("User must be signed in to link a phone number.")
        )
        val trimmedOtp = otpCode.trim()
        if (trimmedOtp.length != 6 || !trimmedOtp.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Please enter the complete 6-digit verification code."))
        }

        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, trimmedOtp)
            val authResult = currentUser.linkWithCredential(credential).awaitTask()
            val linkedUser = authResult.user ?: currentUser
            val profile = loadOrConstructProfile(linkedUser)
            _authState.value = AuthState.Authenticated(profile)
            Result.success(profile)
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(IllegalArgumentException("This phone number is already linked to another OJAS account."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(IllegalArgumentException("Invalid or expired verification code."))
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("Network connection failed. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unlinkProvider(providerId: String): Result<OjasUser> = authMutex.withLock {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication is not configured.")
        )
        val currentUser = currentAuth.currentUser ?: return Result.failure(
            IllegalStateException("User must be signed in to manage linked accounts.")
        )
        val currentProviders = currentUser.providerData.filter { it.providerId != "firebase" }
        if (currentProviders.size <= 1) {
            return Result.failure(IllegalStateException("You must keep at least one active sign-in method linked to your account."))
        }

        return try {
            val authResult = currentUser.unlink(providerId).awaitTask()
            val profile = loadOrConstructProfile(authResult.user ?: currentUser)
            _authState.value = AuthState.Authenticated(profile)
            Result.success(profile)
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("Network connection failed. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recoverOjasId(email: String): Result<OjasUser?> = authMutex.withLock {
        val trimmedEmail = email.trim().lowercase()
        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        if (!emailRegex.matches(trimmedEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }

        // Check in-memory store or cached profiles
        val matchedUser = userProfileStore.values.find { it.email.equals(trimmedEmail, ignoreCase = true) }
            ?: run {
                val username = usernameToEmailMap.entries.find { it.value.equals(trimmedEmail, ignoreCase = true) }?.key
                if (username != null) {
                    OjasUser(
                        userId = "recovered_${username}",
                        username = username,
                        displayName = username,
                        email = trimmedEmail,
                        isSetupComplete = true
                    )
                } else null
            }

        android.util.Log.i("FirebaseAuthRepo", "recoverOjasId query for $trimmedEmail -> found: ${matchedUser?.username}")
        Result.success(matchedUser)
    }

    private fun loadOrConstructProfile(firebaseUser: FirebaseUser, forceRefresh: Boolean = false): OjasUser {
        if (!forceRefresh) {
            val cached = userProfileStore[firebaseUser.uid]
            if (cached != null) return cached
        }

        val displayName = firebaseUser.displayName.orEmpty()
        val photoUrl = firebaseUser.photoUrl?.toString()
        val email = firebaseUser.email.orEmpty()
        val inferredUsername = email.substringBefore("@").filter { it.isLetterOrDigit() || it == '_' }

        val constructed = OjasUser(
            userId = firebaseUser.uid,
            username = inferredUsername,
            displayName = displayName.ifBlank { inferredUsername },
            bio = "",
            avatarUrl = photoUrl,
            createdAt = firebaseUser.metadata?.creationTimestamp ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isSetupComplete = displayName.isNotBlank(),
            email = firebaseUser.email,
            phoneNumber = firebaseUser.phoneNumber,
            isEmailVerified = firebaseUser.isEmailVerified
        )
        userProfileStore[firebaseUser.uid] = constructed
        return constructed
    }
}

/**
 * Suspend extension to await Google Task completion safely with cancellation support.
 */
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(result)
    }
    addOnFailureListener { exception ->
        continuation.resumeWithException(exception)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
