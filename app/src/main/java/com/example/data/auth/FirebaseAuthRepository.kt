package com.example.data.auth

import android.app.Activity
import android.net.Uri
import android.util.Log
import com.example.data.model.OjasUser
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val authListener = FirebaseAuth.AuthStateListener { fbAuth ->
        val user = fbAuth.currentUser
        Log.d(TAG, "onAuthStateChanged uid=${user?.uid ?: "null"}")
        if (user == null) _authState.value = AuthState.Unauthenticated
        else scope.launch { reconcileAuthenticatedUser(user) }
    }

    init {
        try {
            auth.addAuthStateListener(authListener)
        } catch (e: Exception) {
            _authState.value = AuthState.InitializationFailed(e.message ?: "Firebase initialization failed.")
        }
    }

    override fun checkSession() {
        val user = auth.currentUser
        if (user == null) _authState.value = AuthState.Unauthenticated
        else scope.launch { reconcileAuthenticatedUser(user) }
    }

    override suspend fun signup(email: String, password: String): Result<OjasUser> {
        val norm = email.trim().lowercase()
        if (!EMAIL_REGEX.matches(norm)) return fail("Enter a valid email address.")
        if (password.length < 6) return fail("Password must be at least 6 characters.")
        return try {
            val res = auth.createUserWithEmailAndPassword(norm, password).awaitTask("signup")
            val user = res.user ?: return fail("Firebase created no user.")
            val profile = ensureProfile(user)
            publishProfile(profile)
            Result.success(profile)
        } catch (e: FirebaseAuthUserCollisionException) {
            fail("An account with this email already exists.", e)
        } catch (e: FirebaseAuthWeakPasswordException) {
            fail("Password is too weak.", e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            fail("The email address is invalid.", e)
        } catch (e: FirebaseNetworkException) {
            fail("Network error. Check internet connection.", e)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun login(emailOrUsername: String, password: String): Result<OjasUser> {
        val norm = emailOrUsername.trim().lowercase()
        if (!EMAIL_REGEX.matches(norm)) return fail("Sign in with your registered email address.")
        if (password.isBlank()) return fail("Enter your password.")
        return try {
            val res = auth.signInWithEmailAndPassword(norm, password).awaitTask("login")
            val user = res.user ?: return fail("Firebase returned no signed-in user.")
            val profile = ensureProfile(user)
            publishProfile(profile)
            Result.success(profile)
        } catch (e: FirebaseAuthInvalidUserException) {
            fail("No account found with this email.", e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            fail("Incorrect email or password.", e)
        } catch (e: FirebaseNetworkException) {
            fail("Network error. Check internet connection.", e)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun signInWithGoogle(idToken: String, email: String?, displayName: String?): Result<OjasUser> {
        if (idToken.isBlank()) return fail("Google did not return an ID token.")
        return try {
            signInWithCredential(GoogleAuthProvider.getCredential(idToken, null), "signInWithCredential(Google)")
        } catch (e: Exception) { Result.failure(e) }
    }

    private suspend fun signInWithCredential(cred: AuthCredential, op: String): Result<OjasUser> {
        val res = auth.signInWithCredential(cred).awaitTask(op)
        val user = res.user ?: return fail("Firebase returned no signed-in user.")
        val profile = ensureProfile(user)
        publishProfile(profile)
        return Result.success(profile)
    }

    override suspend fun completeSetup(displayName: String, username: String, avatarUrl: String?): Result<OjasUser> {
        val user = auth.currentUser ?: return fail("No active Firebase session.")
        val cName = displayName.trim()
        val cUser = username.trim().removePrefix("@").lowercase()
        if (cName.isBlank()) return fail("Display name cannot be empty.")
        if (!USERNAME_REGEX.matches(cUser)) return fail("Username must be 3-30 letters, numbers, or underscores.")
        if (cUser in RESERVED_USERNAMES) return fail("That username is reserved.")

        return try {
            val uRef = firestore.collection(USERS).document(user.uid)
            val unRef = firestore.collection(USERNAMES).document(cUser)
            val profile = suspendCancellableCoroutine<OjasUser> { cont ->
                firestore.runTransaction { tx ->
                    val unSnap = tx.get(unRef)
                    if (unSnap.exists() && unSnap.getString("uid") != user.uid) {
                        throw FirebaseFirestoreException("Username already taken.", FirebaseFirestoreException.Code.ALREADY_EXISTS)
                    }
                    val now = System.currentTimeMillis()
                    val data = hashMapOf<String, Any?>(
                        "userId" to user.uid, "username" to cUser, "displayName" to cName,
                        "bio" to "", "avatarUrl" to avatarUrl, "createdAt" to now, "updatedAt" to now,
                        "isSetupComplete" to true, "email" to user.email, "phoneNumber" to user.phoneNumber,
                        "isEmailVerified" to user.isEmailVerified
                    )
                    tx.set(uRef, data, SetOptions.merge())
                    tx.set(unRef, mapOf("uid" to user.uid, "username" to cUser))
                    mapToUser(data)
                }.addOnSuccessListener { cont.resume(it) }
                 .addOnFailureListener { cont.resumeWithException(it) }
            }
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(cName).apply {
                if (!avatarUrl.isNullOrBlank()) setPhotoUri(Uri.parse(avatarUrl))
            }.build()).awaitVoidTask("updateProfile")
            _authState.value = AuthState.Authenticated(profile)
            Result.success(profile)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.ALREADY_EXISTS) fail("Username is already taken.", e)
            else fail("Profile setup failed: ${e.message ?: "Firestore error"}", e)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun getUserProfile(userId: String): Result<OjasUser> = try {
        val snap = firestore.collection(USERS).document(userId).get().awaitTask("getUserProfile")
        if (!snap.exists()) fail("User profile not found.") else Result.success(mapToUser(snap.data.orEmpty()))
    } catch (e: Exception) { Result.failure(e) }

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
            firestore.collection(USERS).document(userId).set(userToMap(updated), SetOptions.merge()).awaitVoidTask("updateUserProfile")
            publishProfile(updated)
            Result.success(updated)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun checkUsernameAvailability(username: String, excludeUserId: String?): Result<Boolean> {
        val clean = username.trim().removePrefix("@").lowercase()
        if (!USERNAME_REGEX.matches(clean) || clean in RESERVED_USERNAMES) return Result.success(false)
        return try {
            val snap = firestore.collection(USERNAMES).document(clean).get().awaitTask("checkUsernameAvailability")
            Result.success(!snap.exists() || snap.getString("uid") == excludeUserId)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val norm = email.trim().lowercase()
        if (!EMAIL_REGEX.matches(norm)) return fail("Enter a valid email address.")
        return try {
            auth.sendPasswordResetEmail(norm).awaitVoidTask("sendPasswordResetEmail")
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun recoverOjasId(email: String): Result<OjasUser?> {
        val norm = email.trim().lowercase()
        if (!EMAIL_REGEX.matches(norm)) return fail("Enter a valid email address.")
        return try {
            val snap = firestore.collection(USERS).whereEqualTo("email", norm).limit(1).get().awaitTask("recoverOjasId")
            Result.success(snap.documents.firstOrNull()?.data?.let(::mapToUser))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun sendEmailVerification(): Result<Unit> {
        val user = auth.currentUser ?: return fail("No active Firebase session.")
        return try {
            user.sendEmailVerification().awaitVoidTask("sendEmailVerification")
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override fun isEmailVerified(): Boolean = auth.currentUser?.isEmailVerified ?: false

    override suspend fun reloadUser(): Result<Boolean> {
        val user = auth.currentUser ?: return fail("No active Firebase session.")
        return try {
            user.reload().awaitVoidTask("reloadUser")
            auth.currentUser?.let { publishProfile(ensureProfile(it)) }
            Result.success(auth.currentUser?.isEmailVerified ?: false)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun getLinkedProviders(): List<String> = auth.currentUser?.providerData?.map { it.providerId }?.distinct().orEmpty()

    override suspend fun linkWithGoogle(idToken: String, email: String?): Result<OjasUser> {
        val user = auth.currentUser ?: return fail("No active Firebase session.")
        if (idToken.isBlank()) return fail("Google ID token is missing.")
        return try {
            val res = user.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null)).awaitTask("linkWithGoogle")
            val profile = ensureProfile(res.user ?: user)
            publishProfile(profile)
            Result.success(profile)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun linkWithEmailPassword(email: String, password: String): Result<OjasUser> {
        val user = auth.currentUser ?: return fail("No active Firebase session.")
        return try {
            val res = user.linkWithCredential(EmailAuthProvider.getCredential(email.trim(), password)).awaitTask("linkWithEmailPassword")
            val profile = ensureProfile(res.user ?: user)
            publishProfile(profile)
            Result.success(profile)
        } catch (e: FirebaseAuthUserCollisionException) {
            fail("This email is already linked to another account.", e)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun unlinkProvider(providerId: String): Result<OjasUser> {
        val user = auth.currentUser ?: return fail("No active Firebase session.")
        return try {
            val res = user.unlink(providerId).awaitTask("unlinkProvider")
            val profile = ensureProfile(res.user ?: user)
            publishProfile(profile)
            Result.success(profile)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun logout() {
        try {
            auth.signOut()
            _authState.value = AuthState.Unauthenticated
        } catch (e: Exception) {
            _authState.value = AuthState.InitializationFailed(e.message ?: "Logout failed.")
        }
    }

    override suspend fun sendPhoneOtp(phoneNumber: String, activity: Activity?, onCodeSent: (String, Any?) -> Unit, onVerificationFailed: (Exception) -> Unit, onAutoVerified: ((OjasUser) -> Unit)?) {
        val error = UnsupportedOperationException("Phone authentication is disabled in OJAS.")
        onVerificationFailed(error)
    }

    override suspend fun verifyPhoneOtp(verificationId: String, otpCode: String, phoneNumber: String?): Result<OjasUser> = fail("Phone authentication is disabled.")
    override suspend fun linkWithPhone(verificationId: String, otpCode: String, phoneNumber: String?): Result<OjasUser> = fail("Phone authentication is disabled.")

    private suspend fun reconcileAuthenticatedUser(user: FirebaseUser) {
        try { publishProfile(ensureProfile(user)) }
        catch (e: Exception) {
            _authState.value = AuthState.InitializationFailed("Profile synchronization failed: ${e.message}")
        }
    }

    private suspend fun ensureProfile(user: FirebaseUser): OjasUser {
        val ref = firestore.collection(USERS).document(user.uid)
        val snap = ref.get().awaitTask("loadUserProfile")
        if (snap.exists()) {
            return mapToUser(snap.data.orEmpty()).copy(
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
        ref.set(userToMap(profile)).awaitVoidTask("createUserProfile")
        return profile
    }

    private fun publishProfile(profile: OjasUser) {
        _authState.value = if (profile.isSetupComplete) AuthState.Authenticated(profile) else AuthState.SetupRequired(profile)
    }

    private fun userToMap(user: OjasUser): HashMap<String, Any?> = hashMapOf(
        "userId" to user.userId, "username" to user.username, "displayName" to user.displayName,
        "bio" to user.bio, "avatarUrl" to user.avatarUrl, "createdAt" to user.createdAt,
        "updatedAt" to user.updatedAt, "isSetupComplete" to user.isSetupComplete,
        "email" to user.email, "phoneNumber" to user.phoneNumber,
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
        email = data["email"] as? String ?: "",
        phoneNumber = data["phoneNumber"] as? String,
        isEmailVerified = data["isEmailVerified"] as? Boolean ?: false
    )

    private fun <T> fail(message: String, cause: Throwable? = null): Result<T> = Result.failure(Exception(message, cause))

    private suspend fun <T> Task<T>.awaitTask(op: String): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val res = task.result
                if (res != null) {
                    cont.resume(res)
                } else {
                    cont.resumeWithException(Exception("Operation $op returned null result"))
                }
            } else {
                cont.resumeWithException(task.exception ?: Exception("Operation $op failed"))
            }
        }
    }

    private suspend fun Task<*>.awaitVoidTask(op: String): Unit = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(Unit)
            } else {
                cont.resumeWithException(task.exception ?: Exception("Operation $op failed"))
            }
        }
    }
}
