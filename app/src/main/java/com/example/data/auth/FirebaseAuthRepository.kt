package com.example.data.auth

import android.util.Log
import com.example.data.model.OjasUser
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Real Firebase-backed authentication repository for OJAS.
 *
 * Authentication state is driven by FirebaseAuth's auth-state listener. User profile
 * state is persisted in Cloud Firestore at users/{uid}; no in-memory auth or profile
 * store is used as the source of truth.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthRepository {

    companion object {
        private const val TAG = "OJAS_AUTH"
        private const val USERS = "users"
        private const val USERNAMES = "usernames"
    }

    override val providerName: String = "Firebase Authentication"
    override val isCloudBacked: Boolean = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _authState = kotlinx.coroutines.flow.MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: kotlinx.coroutines.flow.StateFlow<AuthState> = _authState

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        Log.d(TAG, "onAuthStateChanged currentUser=${user?.uid ?: "null"}")
        if (user == null) {
            _authState.value = AuthState.Unauthenticated
        } else {
            scope.launch {
                reconcileAuthenticatedUser(user)
            }
        }
    }

    init {
        try {
            auth.addAuthStateListener(authListener)
            Log.d(TAG, "FirebaseAuth listener attached")
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseAuth initialization failed", e)
            _authState.value = AuthState.InitializationFailed(
                e.message ?: "Firebase Authentication initialization failed."
            )
        }
    }

    override fun checkSession() {
        val user = auth.currentUser
        Log.d(TAG, "checkSession currentUser=${user?.uid ?: "null"}")
        if (user == null) {
            _authState.value = AuthState.Unauthenticated
        } else {
            scope.launch { reconcileAuthenticatedUser(user) }
        }
    }

    override suspend fun signup(email: String, password: String): Result<OjasUser> {
        val normalizedEmail = email.trim().lowercase()
        if (!EMAIL_REGEX.matches(normalizedEmail)) {
            return failure("Enter a valid email address.")
        }
        if (password.length < 6) {
            return failure("Password must be at least 6 characters.")
        }

        Log.d(TAG, "signup submitting email=$normalizedEmail")
        return try {
            val result = auth.createUserWithEmailAndPassword(normalizedEmail, password)
                .awaitLogged("createUserWithEmailAndPassword")
            val user = result.user ?: return failure("Firebase created no user.")
            val profile = ensureProfile(user)
            _authState.value = AuthState.SetupRequired(profile)
            Log.d(TAG, "signup success uid=${user.uid}")
            Result.success(profile)
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.e(TAG, "signup collision", e)
            failure("An account with this email already exists. Please log in.", e)
        } catch (e: FirebaseAuthWeakPasswordException) {
            Log.e(TAG, "signup weak password", e)
            failure("Password is too weak.", e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e(TAG, "signup invalid credentials", e)
            failure("The email address is not valid.", e)
        } catch (e: FirebaseNetworkException) {
            Log.e(TAG, "signup network failure", e)
            failure("Network error. Check your internet connection.", e)
        } catch (e: Exception) {
            Log.e(TAG, "signup failure", e)
            Result.failure(e)
        }
    }

    override suspend fun login(emailOrUsername: String, password: String): Result<OjasUser> {
        val normalizedEmail = emailOrUsername.trim().lowercase()
        if (!EMAIL_REGEX.matches(normalizedEmail)) {
            return failure("Sign in with the email address registered on your account.")
        }
        if (password.isBlank()) return failure("Enter your password.")

        Log.d(TAG, "login submitting email=$normalizedEmail")
        return try {
            val result = auth.signInWithEmailAndPassword(normalizedEmail, password)
                .awaitLogged("signInWithEmailAndPassword")
            val user = result.user ?: return failure("Firebase returned no signed-in user.")
            val profile = ensureProfile(user)
            publishProfile(profile)
            Log.d(TAG, "login success uid=${user.uid}")
            Result.success(profile)
        } catch (e: FirebaseAuthInvalidUserException) {
            Log.e(TAG, "login invalid user", e)
            failure("No account found with this email.", e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e(TAG, "login invalid credentials", e)
            failure("Incorrect email or password.", e)
        } catch (e: FirebaseNetworkException) {
            Log.e(TAG, "login network failure", e)
            failure("Network error. Check your internet connection.", e)
        } catch (e: Exception) {
            Log.e(TAG, "login failure", e)
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(
        idToken: String,
        email: String?,
        displayName: String?
    ): Result<OjasUser> {
        if (idToken.isBlank()) return failure("Google did not return an ID token.")

        Log.d(TAG, "Submitting Google credential to Firebase")
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            signInWithCredential(credential, "signInWithCredential(Google)")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e(TAG, "Google credential rejected by Firebase", e)
            failure("Google authentication failed. Verify the Firebase Google provider and SHA fingerprints.", e)
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.e(TAG, "Google account collision", e)
            failure("This account is already linked to another sign-in method.", e)
        } catch (e: FirebaseNetworkException) {
            Log.e(TAG, "Google sign-in network failure", e)
            failure("Network error while contacting Firebase.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failure", e)
            Result.failure(e)
        }
    }

    private suspend fun signInWithCredential(
        credential: AuthCredential,
        operation: String
    ): Result<OjasUser> {
        val result = auth.signInWithCredential(credential).awaitLogged(operation)
        val user = result.user ?: return failure("Firebase returned no signed-in user.")
        val profile = ensureProfile(user)
        publishProfile(profile)
        Log.d(TAG, "$operation success uid=${user.uid}")
        return Result.success(profile)
    }

    override suspend fun completeSetup(
        displayName: String,
        username: String,
        avatarUrl: String?
    ): Result<OjasUser> {
        val firebaseUser = auth.currentUser ?: return failure("No active Firebase session.")
        val cleanName = displayName.trim()
        val cleanUsername = username.trim().removePrefix("@").lowercase()

        if (cleanName.isBlank()) return failure("Display name cannot be empty.")
        if (!USERNAME_REGEX.matches(cleanUsername)) {
            return failure("Username must be 3-30 letters, numbers, or underscores.")
        }
        if (RESERVED_USERNAMES.contains(cleanUsername)) return failure("That username is reserved.")

        val userRef = firestore.collection(USERS).document(firebaseUser.uid)
        val usernameRef = firestore.collection(USERNAMES).document(cleanUsername)
        Log.d(TAG, "completeSetup start uid=${firebaseUser.uid} username=$cleanUsername")

        return try {
            val profile = firestore.runTransaction { transaction ->
                val usernameSnapshot = transaction.get(usernameRef)
                val ownerUid = usernameSnapshot.getString("uid")
                if (usernameSnapshot.exists() && ownerUid != firebaseUser.uid) {
                    throw FirebaseFirestoreException(
                        "Username already taken.",
                        FirebaseFirestoreException.Code.ALREADY_EXISTS
                    )
                }

                val now = System.currentTimeMillis()
                val userData = hashMapOf<String, Any?>(
                    "userId" to firebaseUser.uid,
                    "username" to cleanUsername,
                    "displayName" to cleanName,
                    "bio" to "",
                    "avatarUrl" to avatarUrl,
                    "createdAt" to now,
                    "updatedAt" to now,
                    "isSetupComplete" to true,
                    "email" to firebaseUser.email,
                    "phoneNumber" to firebaseUser.phoneNumber,
                    "isEmailVerified" to firebaseUser.isEmailVerified
                )
                transaction.set(userRef, userData, SetOptions.merge())
                transaction.set(usernameRef, mapOf("uid" to firebaseUser.uid, "username" to cleanUsername))
                mapToUser(userData)
            }.awaitLogged("completeSetup Firestore transaction")

            val profileUpdate = UserProfileChangeRequest.Builder()
                .setDisplayName(cleanName)
                .apply { if (!avatarUrl.isNullOrBlank()) setPhotoUri(android.net.Uri.parse(avatarUrl)) }
                .build()
            firebaseUser.updateProfile(profileUpdate).awaitLogged("updateProfile")
            _authState.value = AuthState.Authenticated(profile)
            Log.d(TAG, "completeSetup success uid=${firebaseUser.uid}")
            Result.success(profile)
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "completeSetup Firestore failure code=${e.code}", e)
            if (e.code == FirebaseFirestoreException.Code.ALREADY_EXISTS) {
                failure("Username is already taken.", e)
            } else {
                failure("Profile setup failed: ${e.message ?: "Firestore error"}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "completeSetup failure", e)
            Result.failure(e)
        }
    }

    override suspend fun getUserProfile(userId: String): Result<OjasUser> = try {
        val snapshot = firestore.collection(USERS).document(userId).get().awaitLogged("getUserProfile")
        if (!snapshot.exists()) failure("User profile not found.")
        else Result.success(mapToUser(snapshot.data.orEmpty()))
    } catch (e: Exception) {
        Log.e(TAG, "getUserProfile failure uid=$userId", e)
        Result.failure(e)
    }

    override suspend fun updateUserProfile(
        userId: String,
        displayName: String?,
        bio: String?,
        avatarUrl: String?
    ): Result<OjasUser> {
        if (auth.currentUser?.uid != userId) return failure("You can only update your own profile.")
        val current = getUserProfile(userId).getOrElse { return Result.failure(it) }
        val updated = current.copy(
            displayName = displayName?.trim()?.takeIf { it.isNotBlank() } ?: current.displayName,
            bio = bio?.trim() ?: current.bio,
            avatarUrl = avatarUrl ?: current.avatarUrl,
            updatedAt = System.currentTimeMillis(),
            isEmailVerified = auth.currentUser?.isEmailVerified ?: current.isEmailVerified
        )
        return try {
            firestore.collection(USERS).document(userId).set(userToMap(updated), SetOptions.merge())
                .awaitLogged("updateUserProfile")
            _authState.value = if (updated.isSetupComplete) AuthState.Authenticated(updated) else AuthState.SetupRequired(updated)
            Log.d(TAG, "updateUserProfile success uid=$userId")
            Result.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserProfile failure uid=$userId", e)
            Result.failure(e)
        }
    }

    override suspend fun checkUsernameAvailability(username: String, excludeUserId: String?): Result<Boolean> {
        val clean = username.trim().removePrefix("@").lowercase()
        if (!USERNAME_REGEX.matches(clean) || RESERVED_USERNAMES.contains(clean)) return Result.success(false)
        return try {
            val snapshot = firestore.collection(USERNAMES).document(clean).get().awaitLogged("checkUsernameAvailability")
            val ownerUid = snapshot.getString("uid")
            Result.success(!snapshot.exists() || ownerUid == excludeUserId)
        } catch (e: Exception) {
            Log.e(TAG, "checkUsernameAvailability failure username=$clean", e)
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val normalized = email.trim().lowercase()
        if (!EMAIL_REGEX.matches(normalized)) return failure("Enter a valid email address.")
        return try {
            auth.sendPasswordResetEmail(normalized).awaitLogged("sendPasswordResetEmail")
            Result.success(Unit)
        } catch (e: FirebaseNetworkException) {
            Log.e(TAG, "password reset network failure", e)
            failure("Network error. Check your internet connection.", e)
        } catch (e: Exception) {
            Log.e(TAG, "password reset failure", e)
            Result.failure(e)
        }
    }

    override suspend fun recoverOjasId(email: String): Result<OjasUser?> {
        val normalized = email.trim().lowercase()
        if (!EMAIL_REGEX.matches(normalized)) return failure("Enter a valid email address.")
        return try {
            val snapshot = firestore.collection(USERS).whereEqualTo("email", normalized).limit(1).get()
                .awaitLogged("recoverOjasId")
            val document = snapshot.documents.firstOrNull()
            Result.success(document?.data?.let(::mapToUser))
        } catch (e: Exception) {
            Log.e(TAG, "recoverOjasId failure", e)
            Result.failure(e)
        }
    }

    override suspend fun sendEmailVerification(): Result<Unit> {
        val user = auth.currentUser ?: return failure("No active Firebase session.")
        return try {
            user.sendEmailVerification().awaitLogged("sendEmailVerification")
            Log.d(TAG, "verification email requested uid=${user.uid}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendEmailVerification failure", e)
            Result.failure(e)
        }
    }

    override fun isEmailVerified(): Boolean = auth.currentUser?.isEmailVerified ?: false

    override suspend fun reloadUser(): Result<Boolean> {
        val user = auth.currentUser ?: return failure("No active Firebase session.")
        return try {
            user.reload().awaitLogged("reloadUser")
            val refreshed = auth.currentUser
            if (refreshed != null) publishProfile(ensureProfile(refreshed))
            Result.success(refreshed?.isEmailVerified ?: false)
        } catch (e: Exception) {
            Log.e(TAG, "reloadUser failure", e)
            Result.failure(e)
        }
    }

    override suspend fun getLinkedProviders(): List<String> =
        auth.currentUser?.providerData?.map { it.providerId }?.distinct().orEmpty()

    override suspend fun linkWithGoogle(idToken: String, email: String?): Result<OjasUser> {
        val user = auth.currentUser ?: return failure("No active Firebase session.")
        if (idToken.isBlank()) return failure("Google ID token is missing.")
        return try {
            val result = user.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .awaitLogged("linkWithGoogle")
            val linked = result.user ?: auth.currentUser ?: return failure("Firebase returned no linked user.")
            val profile = ensureProfile(linked)
            publishProfile(profile)
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "linkWithGoogle failure", e)
            Result.failure(e)
        }
    }

    override suspend fun linkWithEmailPassword(email: String, password: String): Result<OjasUser> {
        val user = auth.currentUser ?: return failure("No active Firebase session.")
        return try {
            val credential = EmailAuthProvider.getCredential(email.trim(), password)
            val result = user.linkWithCredential(credential).awaitLogged("linkWithEmailPassword")
            val linked = result.user ?: user
            val profile = ensureProfile(linked)
            publishProfile(profile)
            Result.success(profile)
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.e(TAG, "linkWithEmailPassword collision", e)
            failure("This email is already linked to another account.", e)
        } catch (e: Exception) {
            Log.e(TAG, "linkWithEmailPassword failure", e)
            Result.failure(e)
        }
    }

    override suspend fun unlinkProvider(providerId: String): Result<OjasUser> {
        val user = auth.currentUser ?: return failure("No active Firebase session.")
        return try {
            val result = user.unlink(providerId).awaitLogged("unlinkProvider")
            val updatedUser = result.user ?: auth.currentUser ?: return failure("Firebase returned no user after unlink.")
            val profile = ensureProfile(updatedUser)
            publishProfile(profile)
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "unlinkProvider failure provider=$providerId", e)
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        try {
            auth.signOut()
            _authState.value = AuthState.Unauthenticated
            Log.d(TAG, "logout success")
        } catch (e: Exception) {
            Log.e(TAG, "logout failure", e)
            _authState.value = AuthState.InitializationFailed(e.message ?: "Logout failed.")
        }
    }

    // Phone authentication is intentionally disabled in OJAS. The interface remains only for
    // source compatibility with existing callers; it never performs a Firebase phone request.
    override suspend fun sendPhoneOtp(
        phoneNumber: String,
        activity: android.app.Activity?,
        onCodeSent: (verificationId: String, resendToken: Any?) -> Unit,
        onVerificationFailed: (Exception) -> Unit,
        onAutoVerified: ((OjasUser) -> Unit)?
    ) {
        val error = UnsupportedOperationException("Phone number authentication is disabled in OJAS.")
        Log.e(TAG, "sendPhoneOtp called while phone auth is disabled", error)
        onVerificationFailed(error)
    }

    override suspend fun verifyPhoneOtp(
        verificationId: String,
        otpCode: String,
        phoneNumber: String?
    ): Result<OjasUser> = failure("Phone number authentication is disabled in OJAS.")

    override suspend fun linkWithPhone(
        verificationId: String,
        otpCode: String,
        phoneNumber: String?
    ): Result<OjasUser> = failure("Phone number authentication is disabled in OJAS.")

    private suspend fun reconcileAuthenticatedUser(firebaseUser: FirebaseUser) {
        try {
            val profile = ensureProfile(firebaseUser)
            publishProfile(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reconcile Firebase session uid=${firebaseUser.uid}", e)
            _authState.value = AuthState.InitializationFailed(
                "Signed in to Firebase, but profile synchronization failed: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    private suspend fun ensureProfile(firebaseUser: FirebaseUser): OjasUser {
        val ref = firestore.collection(USERS).document(firebaseUser.uid)
        val snapshot = ref.get().awaitLogged("loadUserProfile")
        if (snapshot.exists()) {
            return mapToUser(snapshot.data.orEmpty()).copy(
                email = firebaseUser.email,
                phoneNumber = firebaseUser.phoneNumber,
                isEmailVerified = firebaseUser.isEmailVerified
            )
        }

        val now = System.currentTimeMillis()
        val profile = OjasUser(
            userId = firebaseUser.uid,
            username = "",
            displayName = firebaseUser.displayName.orEmpty(),
            bio = "",
            avatarUrl = firebaseUser.photoUrl?.toString(),
            createdAt = firebaseUser.metadata?.creationTimestamp ?: now,
            updatedAt = now,
            isSetupComplete = false,
            email = firebaseUser.email,
            phoneNumber = firebaseUser.phoneNumber,
            isEmailVerified = firebaseUser.isEmailVerified
        )
        ref.set(userToMap(profile)).awaitLogged("createUserProfile")
        Log.d(TAG, "Created Firestore profile uid=${firebaseUser.uid}")
        return profile
    }

    private fun publishProfile(profile: OjasUser) {
        _authState.value = if (profile.isSetupComplete) {
            AuthState.Authenticated(profile)
        } else {
            AuthState.SetupRequired(profile)
        }
        Log.d(TAG, "publishProfile uid=${profile.userId} setup=${profile.isSetupComplete}")
    }

    private fun userToMap(user: OjasUser): HashMap<String, Any?> = hashMapOf(
        "userId" to user.userId,
        "username" to user.username,
        "displayName" to user.displayName,
        "bio" to user.bio,
        "avatarUrl" to user.avatarUrl,
        "createdAt" to user.createdAt,
        "updatedAt" to user.updatedAt,
        "isSetupComplete" to user.isSetupComplete,
        "email" to user.email,
        "phoneNumber" to user.phoneNumber,
        "isEmailVerified" to user.isEmailVerified
    )

    private fun mapToUser(data: Map<String, Any?>): OjasUser = OjasUser(
        userId = data["userId"] as? String ?: "",
        username = data["username"] as? String ?: "",
        displayName = data["displayName"] as? String ?: "",
        bio = data["bio"] as? String ?: "",
        avatarUrl = data["avatarUrl"] as? String,
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        isSetupComplete = data["isSetupComplete"] as? Boolean ?: false,
        email = data["email"] as? String,
        phoneNumber = data["phoneNumber"] as? String,
        isEmailVerified = data["isEmailVerified"] as? Boolean ?: false
    )

    private fun <T> failure(message: String, cause: Throwable? = null): Result<T> {
        val exception = IllegalStateException(message, cause)
        Log.e(TAG, message, exception)
        return Result.failure(exception)
    }

    private suspend fun <T> Task<T>.awaitLogged(operation: String): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "$operation onComplete success")
            } else {
                Log.e(TAG, "$operation onComplete failure", task.exception)
            }
        }
        addOnSuccessListener { result ->
            Log.d(TAG, "$operation onSuccess")
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { exception ->
            Log.e(TAG, "$operation onFailure", exception)
            if (continuation.isActive) continuation.resumeWithException(exception)
        }
        addOnCanceledListener {
            Log.e(TAG, "$operation cancelled")
            continuation.cancel()
        }
    }

    private companion object {
        val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        val USERNAME_REGEX = Regex("^[a-z0-9_]{3,30}$")
        val RESERVED_USERNAMES = setOf(
            "admin", "administrator", "ojas", "official", "support", "root",
            "system", "null", "api", "security", "moderator", "mod", "staff", "help"
        )
    }
}
