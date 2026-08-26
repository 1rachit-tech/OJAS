package com.example.data.auth

import android.app.Activity
import android.net.Uri
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
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthRepository {

    companion object {
        private const val TAG = "OJAS_AUTH"
        private const val USERS = "users"
        private const val USERNAMES = "usernames"
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        private val USERNAME_REGEX = Regex("^[a-z0-9_]{3,30}$")
        private val RESERVED_USERNAMES = setOf(
            "admin", "administrator", "ojas", "official", "support", "root",
            "system", "null", "api", "security", "moderator", "mod", "staff", "help"
        )
    }

    override val providerName = "Firebase Authentication"
    override val isCloudBacked = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        Log.d(TAG, "onAuthStateChanged uid=${user?.uid ?: "null"}")
        if (user == null) {
            _authState.value = AuthState.Unauthenticated
        } else {
            scope.launch { reconcileAuthenticatedUser(user) }
        }
    }

    init {
        try {
            auth.addAuthStateListener(authListener)
            Log.d(TAG, "FirebaseAuth listener attached")
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseAuth initialization failed", e)
            _authState.value = AuthState.InitializationFailed(e.message ?: "Firebase initialization failed.")
        }
    }

    override fun checkSession() {
        val user = auth.currentUser
        Log.d(TAG, "checkSession uid=${user?.uid ?: "null"}")
        if (user == null) {
            _authState.value = AuthState.Unauthenticated
        } else {
            scope.launch { reconcileAuthenticatedUser(user) }
        }
    }

    override suspend fun signup(email: String, password: String): Result<OjasUser> {
        val normalized = email.trim().lowercase()
        if (!EMAIL_REGEX.matches(normalized)) return fail("Enter a valid email address.")
        if (password.length < 6) return fail("Password must be at least 6 characters.")
        return try {
            Log.d(TAG, "signup submitting email=$normalized")
            val result = auth.createUserWithEmailAndPassword(normalized, password).awaitLogged("createUserWithEmailAndPassword")
            val user = result.user ?: return fail("Firebase created no user.")
            val profile = ensureProfile(user)
            publishProfile(profile)
            Log.d(TAG, "signup success uid=${user.uid}")
            Result.success(profile)
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.e(TAG, "signup collision", e); fail("An account with this email already exists.", e)
        } catch (e: FirebaseAuthWeakPasswordException) {
            Log.e(TAG, "signup weak password", e); fail("Password is too weak.", e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e(TAG, "signup invalid credentials", e); fail("The email address is invalid.", e)
        } catch (e: FirebaseNetworkException) {
            Log.e(TAG, "signup network failure", e); fail("Network error. Check your internet connection.", e)
        } catch (e: Exception) {
            Log.e(TAG, "signup failure", e); Result.failure(e)
        }
    }

    override suspend fun login(emailOrUsername: String, password: String): Result<OjasUser> {
        val normalized = emailOrUsername.trim().lowercase()
        if (!EMAIL_REGEX.matches(normalized)) return fail("Sign in with your registered email address.")
        if (password.isBlank()) return fail("Enter your password.")
        return try {
            Log.d(TAG, "login submitting email=$normalized")
            val result = auth.signInWithEmailAndPassword(normalized, password).awaitLogged("signInWithEmailAndPassword")
            val user = result.user ?: return fail("Firebase returned no signed-in user.")
            val profile = ensureProfile(user)
            publishProfile(profile)
            Log.d(TAG, "login success uid=${user.uid}")
            Result.success(profile)
        } catch (e: FirebaseAuthInvalidUserException) {
            Log.e(TAG, "login invalid user", e); fail("No account found with this email.", e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e(TAG, "login invalid credentials", e); fail("Incorrect email or password.", e)
        } catch (e: FirebaseNetworkException) {
            Log.e(TAG, "login network failure", e); fail("Network error. Check your internet connection.", e)
        } catch (e: Exception) {
            Log.e(TAG, "login failure", e); Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(idToken: String, email: String?, displayName: String?): Result<OjasUser> {
        if (idToken.isBlank()) return fail("Google did not return an ID token.")
        return try {
            Log.d(TAG, "Submitting Google credential to Firebase")
            signInWithCredential(GoogleAuthProvider.getCredential(idToken, null), "signInWithCredential(Google)")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e(TAG, "Google credential rejected", e)
            fail("Google authentication failed. Verify Firebase Google sign-in and SHA fingerprints.", e)
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.e(TAG, "Google account collision", e); fail("This account is linked to another sign-in method.", e)
        } catch (e: FirebaseNetworkException) {
            Log.e(TAG, "Google sign-in network failure", e); fail("Network error while contacting Firebase.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failure", e); Result.failure(e)
        }
    }

    private suspend fun signInWithCredential(credential: AuthCredential, operation: String): Result<OjasUser> {
        val result = auth.signInWithCredential(credential).awaitLogged(operation)
        val user = result.user ?: return fail("Firebase returned no signed-in user.")
        val profile = ensureProfile(user)
        publishProfile(profile)
        Log.d(TAG, "$operation success uid=${user.uid}")
        return Result.success(profile)
    }

    override suspend fun completeSetup(displayName: String, username: String, avatarUrl: String?): Result<OjasUser> {
        val user = auth.currentUser ?: return fail("No active Firebase session.")
        val cleanName = displayName.trim()
        val cleanUsername = username.trim().removePrefix("@").lowercase()
        if (cleanName.isBlank()) return fail("Display name cannot be empty.")
        if (!USERNAME_REGEX.matches(cleanUsername)) return fail("Username must be 3-30 letters, numbers, or underscores.")
        if (cleanUsername in RESERVED_USERNAMES) return fail("That username is reserved.")

        return try {
            val userRef = firestore.collection(USERS).document(user.uid)
            val usernameRef = firestore.collection(USERNAMES).document(cleanUsername)
            Log.d(TAG, "completeSetup uid=${user.uid} username=$cleanUsername")
            
            val profile = suspendCancellableCoroutine<OjasUser> { cont ->
                firestore.runTransaction { transaction ->
                    val usernameSnapshot = transaction.get(usernameRef)
                    if (usernameSnapshot.exists() && usernameSnapshot.getString("uid") != user.uid) {
                        throw FirebaseFirestoreException("Username already taken.", FirebaseFirestoreException.Code.ALREADY_EXISTS)
                    }
                    val now = System.currentTimeMillis()
                    val data = hashMapOf<String, Any?>(
                        "userId" to user.uid, "username" to cleanUsername, "displayName" to cleanName,
                        "bio" to "", "avatarUrl" to avatarUrl, "createdAt" to now, "updatedAt" to now,
                        "isSetupComplete" to true, "email" to user.email, "phoneNumber" to user.phoneNumber,
                        "isEmailVerified" to user.isEmailVerified
                    )
                    transaction.set(userRef, data, SetOptions.merge())
                    transaction.set(usernameRef, mapOf("uid" to user.uid, "username" to cleanUsername))
                    mapToUser(data)
                }.addOnSuccessListener { userProfile ->
                    cont.resume(userProfile)
                }.addOnFailureListener { exception ->
                    cont.resumeWithException(exception)
                }
            }

            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(cleanName).apply {
                if (!avatarUrl.isNullOrBlank()) setPhotoUri(Uri.parse(avatarUrl))
            }.build()).awaitLogged("updateProfile")

            _authState.value = AuthState.Authenticated(profile)
            Log.d(TAG, "completeSetup success uid=${user.uid}")
            Result.success(profile)
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "completeSetup Firestore failure code=${e.code}", e)
            if (e.code == FirebaseFirestoreException.Code.ALREADY_EXISTS) fail("Username is already taken.", e)
            else fail("Profile setup failed: ${e.message ?: "Firestore error"}", e)
        } catch (e: Exception) {
            Log.e(TAG, "completeSetup failure", e); Result.failure(e)
        }
    }

    override suspend fun getUserProfile(userId: String): Result<OjasUser> = try {
        val snapshot = firestore.collection(USERS).document(userId).get().awaitLogged("getUserProfile")
        if (!snapshot.exists()) fail("User profile not found.") else Result.success(mapToUser(snapshot.data.orEmpty()))
    } catch (e: Exception) {
        Log.e(TAG, "getUserProfile failure uid=$userId", e); Result.failure(e)
    }

    override suspend fun updateUserProfile(userId: String, displayName: String?, bio: String?, avatarUrl: String?): Result<OjasUser> {
        if (auth.currentUser?.uid != userId) return fail("You can only update your own profile.")
        val current = getUserProfile(userId).getOrElse { return Result.failure(it) }
        val updated = current.copy(
            displayName = displayName?.trim()?.takeIf { it.isNotBlank() } ?: current.displayName,
            bio = bio?.trim() ?: current.bio,
            avatarUrl = avatarUrl ?: current.avatarUrl,
            updatedAt = System.currentTimeMillis(),
            isEmailVerified = auth.currentUser?.isEmailVerified ?: current.isEmailVerified
        )
        return try {
            firestore.collection(USERS).document(userId).set(userToMap(updated), SetOptions.merge()).awaitLogged("updateUserProfile")
            publishProfile(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserProfile failure uid=$userId", e); Result.failure(e)
        }
    }

    override suspend fun checkUsernameAvailability(username: String, excludeUserId: String?): Result<Boolean> {
        val clean = username.trim().removePrefix("@").lowercase()
        if (!USERNAME_REGEX.matches(clean) || clean in RESERVED_USERNAMES) return Result.success(false)
        return try {
            val snapshot = firestore.collection(USERNAMES).document(clean).get().awaitLogged("checkUsernameAvailability")
            Result.success(!snapshot.exists() || snapshot.getString("uid") == excludeUserId)
        } catch (e: Exception) {
            Log.e(TAG, "checkUsernameAvailability failure", e); Result.failure(e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val normalized = email.trim().lowercase()
        if (!EMAIL_REGEX.matches(normalized)) return fail("Enter a valid email address.")
        return try {
            auth.sendPasswordResetEmail(normalized).awaitLogged("sendPasswordResetEmail")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendPasswordResetEmail failure", e); Result.failure(e)
        }
    }

    override suspend fun recoverOjasId(email: String): Result<OjasUser?> {
        val normalized = email.trim().lowercase()
        if (!EMAIL_REGEX.matches(normalized)) return fail("Enter a valid email address.")
        return try {
            val snapshot = firestore.collection(USERS).whereEqualTo("email", normalized).limit(1).get().awaitLogged("recoverOjasId")
            Result.success(snapshot.documents.firstOrNull()?.data?.let(::mapToUser))
        } catch (e: Exception) {
            Log.e(TAG, "recoverOjasId failure", e); Result.failure(e)
        }
    }

    override suspend fun sendEmailVerification(): Result<Unit> {
        val user = auth.currentUser ?: return fail("No active Firebase session.")
        return try {
            user.sendEmailVerification().awaitLogged("sendEmailVerification")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendEmailVerification failure", e); Result.failure(e)
        }
    }

    override fun isEmailVerified(): Boolean = auth.currentUser?.isEmailVerified ?: false

    override suspend fun reloadUser(): Result<Boolean> {
        val user = auth.currentUser ?: return fail("No active Firebase session.")
        return try {
            user.reload().awaitLogged("reloadUser")
            auth.currentUser?.let { publishProfile(ensureProfile(it)) }
            Result.success(auth.currentUser?.isEmailVerified ?: false)
        } catch (e: Exception) {
            Log.e(TAG, "reloadUser failure", e); Result.failure(e)
        }
    }

    override suspend fun getLinkedProviders(): List<String> = auth.currentUser?.providerData?.map { it.providerId }?.distinct().orEmpty()

    override suspend fun linkWithGoogle(idToken: String, email: String?): Result<OjasUser> {
        val user = auth.currentUser ?: return fail("No active Firebase session.")
        if (idToken.isBlank()) return fail("Google ID token is missing.")
        return try {
            val result = user.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null)).awaitLogged("linkWithGoogle")
            val linked = result.user ?: user
            val profile = ensureProfile(linked)
            publishProfile(profile)
            Result.success(profile)
        } catch (e: Exception) { 
            Log.e(TAG, "linkWithGoogle failure", e)
            Result.failure(e) 
        }
    }

    override suspend fun linkWithEmailPassword(email: String, password: String): Result<OjasUser> {
        val user = auth.currentUser ?: return fail("No active Firebase session.")
        return try {
            val result = user.linkWithCredential(EmailAuthProvider.getCredential(email.trim(), password)).awaitLogged("linkWithEmailPassword")
            val linked = result.user ?: user
            val profile = ensureProfile(linked)
            publishProfile(profile)
            Result.success(profile)
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.e(TAG, "linkWithEmailPassword collision", e); fail("This email is already linked to another account.", e)
        } catch (e: Exception) { 
            Log.e(TAG, "linkWithEmailPassword failure", e)
            Result.failure(e) 
        }
    }

    override suspend fun unlinkProvider(providerId: String): Result<OjasUser> {
        val user = auth.currentUser ?: return fail("No active Firebase session.")
        return try {
            val result = user.unlink(providerId).awaitLogged("unlinkProvider")
            val updated = result.user ?: user
            val profile = ensureProfile(updated)
            publishProfile(profile)
            Result.success(profile)
        } catch (e: Exception) { 
            Log.e(TAG, "unlinkProvider failure", e)
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

    override suspend fun sendPhoneOtp(phoneNumber: String, activity: Activity?, onCodeSent: (String, Any?) -> Unit, onVerificationFailed: (Exception) -> Unit, onAutoVerified: ((OjasUser) -> Unit)?) {
        val error = UnsupportedOperationException("Phone number authentication is disabled in OJAS.")
        Log.e(TAG, "sendPhoneOtp called while disabled", error)
        onVerificationFailed(error)
    }

    override suspend fun verifyPhoneOtp(verificationId: String, otpCode: String, phoneNumber: String?): Result<OjasUser> = fail("Phone number authentication is disabled in OJAS.")
    override suspend fun linkWithPhone(verificationId: String, otpCode: String, phoneNumber: String?): Result<OjasUser> = fail("Phone number authentication is disabled in OJAS.")

    private suspend fun reconcileAuthenticatedUser(user: FirebaseUser) {
        try { 
            publishProfile(ensureProfile(user)) 
        } catch (e: Exception) {
            Log.e(TAG, "profile synchronization failed uid=${user.uid}", e)
            _authState.value = AuthState.InitializationFailed("Signed in to Firebase, but profile synchronization failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun ensureProfile(user: FirebaseUser): OjasUser {
        val ref = firestore.collection(USERS).document(user.uid)
        val snapshot = ref.get().awaitLogged("loadUserProfile")
        if (snapshot.exists()) {
            return mapToUser(snapshot.data.orEmpty()).copy(
                email = user.email ?: "",
                phoneNumber = user.phoneNumber,
                isEmailVerified = user.isEmailVerified
            )
        }
        val now = System.currentTimeMillis()
        val profile = OjasUser(
            userId = user.uid,
            displayName = user.displayName.orEmpty(),
            avatarUrl = user.photoUrl?.toString(),
            createdAt = user.metadata?.creationTimestamp ?: now,
            updatedAt = now,
            isSetupComplete = false,
            email = user.email ?: "",
            phoneNumber = user.phoneNumber,
            isEmailVerified = user.isEmailVerified
        )
        ref.se
