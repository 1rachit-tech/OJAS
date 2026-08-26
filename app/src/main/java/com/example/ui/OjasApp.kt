package com.example.ui

import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthState
import com.example.data.auth.OjasAuthRepository
import com.example.data.repository.MediaStorageService
import com.example.data.repository.OjRecommendationRepository
import com.example.data.repository.OjRepository
import com.example.data.repository.OjWatchAnalyticsRepository
import com.example.data.repository.OjasMediaStorageService
import com.example.data.repository.OjasOjRecommendationRepository
import com.example.data.repository.OjasOjRepository
import com.example.data.repository.OjasOjWatchAnalyticsRepository
import com.example.data.repository.OjasPostRepository
import com.example.data.repository.OjasSocialInteractionRepository
import com.example.data.repository.PostRepository
import com.example.data.repository.SocialInteractionRepository
import com.example.ui.components.CategoryFilterSheet
import com.example.ui.components.CreateActionSheet
import com.example.ui.components.OjasBottomBar
import com.example.ui.navigation.OjasDestination
import com.example.ui.screens.CreateOjScreen
import com.example.ui.screens.CreatePostScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.OjScreen
import com.example.ui.screens.PhoneAuthScreen
import com.example.ui.screens.PhoneAuthStep
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.SignupScreen
import com.example.ui.screens.YouScreen
import com.example.ui.theme.OjasTheme
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private enum class AuthScreen {
    LOGIN,
    SIGNUP,
    PHONE_OTP
}

@Composable
fun OjasApp(
    initialDeepLinkOjId: String? = null,
    onDeepLinkConsumed: (() -> Unit)? = null,
    authRepository: AuthRepository = remember { AuthRepository.createDefault() },
    socialRepository: SocialInteractionRepository = remember { OjasSocialInteractionRepository() },
    mediaStorageService: MediaStorageService = remember { OjasMediaStorageService() },
    postRepository: PostRepository = remember(mediaStorageService) { OjasPostRepository(mediaStorageService) },
    ojRepository: OjRepository = remember(mediaStorageService) { OjasOjRepository(mediaStorageService) },
    watchAnalyticsRepository: OjWatchAnalyticsRepository = remember(ojRepository) { OjasOjWatchAnalyticsRepository(ojRepository) },
    recommendationRepository: OjRecommendationRepository = remember(ojRepository, socialRepository, watchAnalyticsRepository) {
        OjasOjRecommendationRepository(ojRepository, socialRepository, watchAnalyticsRepository)
    }
) {
    OjasTheme {
        val authState by authRepository.authState.collectAsStateWithLifecycle()
        var currentDestination by remember { mutableStateOf(OjasDestination.HOME) }
        var deepLinkTargetOjId by remember { mutableStateOf<String?>(null) }
        var isAuthGateOpen by remember { mutableStateOf(false) }
        var currentAuthScreen by remember { mutableStateOf(AuthScreen.LOGIN) }
        var pendingAuthorizedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        var authPromptMessage by remember { mutableStateOf<String?>(null) }
        var isCreateSheetOpen by remember { mutableStateOf(false) }
        var isCreatePostOpen by remember { mutableStateOf(false) }
        var isCreateOjOpen by remember { mutableStateOf(false) }
        var isFilterSheetOpen by remember { mutableStateOf(false) }
        var selectedOjCategory by remember { mutableStateOf<String?>(null) }
        var authErrorMessage by remember { mutableStateOf<String?>(null) }
        var isAuthProcessing by remember { mutableStateOf(false) }

        // Forgot Password State
        var isForgotPasswordLoading by remember { mutableStateOf(false) }
        var forgotPasswordError by remember { mutableStateOf<String?>(null) }
        var forgotPasswordSuccess by remember { mutableStateOf(false) }

        // Phone Authentication State
        var phoneAuthStep by remember { mutableStateOf(PhoneAuthStep.ENTER_PHONE) }
        var phoneVerificationId by remember { mutableStateOf("") }
        var phoneTargetNumber by remember { mutableStateOf("") }

        val context = LocalContext.current
        val activity = context as? Activity
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        // Handle incoming deep link on launch or background resume
        androidx.compose.runtime.LaunchedEffect(initialDeepLinkOjId) {
            if (!initialDeepLinkOjId.isNullOrBlank()) {
                deepLinkTargetOjId = initialDeepLinkOjId
                currentDestination = OjasDestination.OJ
                onDeepLinkConsumed?.invoke()
            }
        }

        val showNotice: (String) -> Unit = { message ->
            coroutineScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }

        fun triggerGoogleSignIn() {
            isAuthProcessing = true
            authErrorMessage = null
            coroutineScope.launch {
                try {
                    val credentialManager = CredentialManager.create(context)
                    
                    // Attempt dynamic resolution of default_web_client_id from resources
                    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                    val rawClientId = if (resId != 0) {
                        context.getString(resId)
                    } else {
                        try {
                            context.getString(com.example.R.string.default_web_client_id)
                        } catch (e: Exception) {
                            ""
                        }
                    }.trim()

                    // Ensure the Web Client ID is a valid Google OAuth Client ID before querying CredentialManager
                    val isValidWebClientId = rawClientId.isNotBlank() &&
                        rawClientId.contains(".apps.googleusercontent.com") &&
                        rawClientId.matches(Regex("^[0-9]+-[a-zA-Z0-9_.-]+\\.apps\\.googleusercontent\\.com$"))

                    if (!isValidWebClientId) {
                        isAuthProcessing = false
                        authErrorMessage = "Google Sign-In configuration is missing. Enable the Google provider in Firebase Authentication and verify the Web client ID in google-services.json."
                        return@launch
                    }

                    val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(rawClientId)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(signInWithGoogleOption)
                        .build()

                    val result = credentialManager.getCredential(context, request)
                    val credential = result.credential
                    if (credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        val userResult = authRepository.signInWithGoogle(
                            idToken = idToken,
                            email = googleIdTokenCredential.id,
                            displayName = googleIdTokenCredential.displayName
                        )
                        isAuthProcessing = false
                        if (userResult.isSuccess) {
                            val postAction = pendingAuthorizedAction
                            pendingAuthorizedAction = null
                            isAuthGateOpen = false
                            postAction?.invoke()
                        } else {
                            authErrorMessage = userResult.exceptionOrNull()?.message ?: "Google sign-in failed."
                        }
                    } else {
                        isAuthProcessing = false
                        authErrorMessage = "Received unsupported credential type."
                    }
                } catch (e: GetCredentialCancellationException) {
                    isAuthProcessing = false
                    // User dismissed or cancelled Google sign-in
                } catch (e: NoCredentialException) {
                    isAuthProcessing = false
                    authErrorMessage = "Google Sign-In could not access an account on this device. Check that Google Play services is available and try again."
                } catch (e: Exception) {
                    isAuthProcessing = false
                    val msg = e.message.orEmpty()
                    authErrorMessage = when {
                        msg.contains("16", ignoreCase = true) || msg.contains("Cannot find a matching credential", ignoreCase = true) -> {
                            "Google Sign-In authorization failed (Code 16). Verify this build's package name and signing certificate SHA fingerprints in Firebase Console."
                        }
                        msg.contains("10", ignoreCase = true) || msg.contains("DEVELOPER_ERROR", ignoreCase = true) -> {
                            "Google Play Services Developer Error (Code 10). Package name (com.rachit.ojas) or SHA-1 fingerprint mismatch in Firebase Console."
                        }
                        else -> e.message ?: "Google sign-in could not be completed."
                    }
                }
            }
        }

        // Reusable centralized authentication gate preserving return context
        fun requireAuthentication(prompt: String? = null, onAuthorized: () -> Unit) {
            if (authState is AuthState.Authenticated) {
                onAuthorized()
            } else {
                pendingAuthorizedAction = onAuthorized
                authPromptMessage = prompt
                authErrorMessage = null
                currentAuthScreen = AuthScreen.LOGIN
                isAuthGateOpen = true
            }
        }

        when {
            // Priority 1: User signed up / logged in but requires Profile Identity Setup
            authState is AuthState.SetupRequired -> {
                val setupState = authState as AuthState.SetupRequired
                SetupScreen(
                    onCompleteSetup = { displayName, username, avatarUrl ->
                        isAuthProcessing = true
                        authErrorMessage = null
                        coroutineScope.launch {
                            val result = authRepository.completeSetup(displayName, username, avatarUrl)
                            isAuthProcessing = false
                            if (result.isSuccess) {
                                val postAction = pendingAuthorizedAction
                                pendingAuthorizedAction = null
                                isAuthGateOpen = false
                                postAction?.invoke()
                            } else {
                                authErrorMessage = result.exceptionOrNull()?.message
                                    ?: "Failed to save profile setup"
                            }
                        }
                    },
                    onActionNotice = showNotice,
                    initialDisplayName = setupState.user.displayName,
                    initialUsername = setupState.user.username,
                    isLoading = isAuthProcessing,
                    errorMessage = authErrorMessage
                )
            }

            // Priority 2: Authentication Gate triggered (Login / Signup modal flow)
            isAuthGateOpen && authState !is AuthState.Authenticated -> {
                when (currentAuthScreen) {
                    AuthScreen.LOGIN -> {
                        LoginScreen(
                            onLoginClick = { emailOrUsername, password ->
                                isAuthProcessing = true
                                authErrorMessage = null
                                coroutineScope.launch {
                                    val result = authRepository.login(emailOrUsername, password)
                                    isAuthProcessing = false
                                    if (result.isSuccess) {
                                        val postAction = pendingAuthorizedAction
                                        pendingAuthorizedAction = null
                                        isAuthGateOpen = false
                                        postAction?.invoke()
                                    } else {
                                        authErrorMessage = result.exceptionOrNull()?.message
                                            ?: "Authentication failed"
                                    }
                                }
                            },
                            onGoogleSignInClick = {
                                triggerGoogleSignIn()
                            },
                            onPhoneSignInClick = {
                                authErrorMessage = null
                                phoneAuthStep = PhoneAuthStep.ENTER_PHONE
                                currentAuthScreen = AuthScreen.PHONE_OTP
                            },
                            onForgotPasswordClick = { resetEmail ->
                                isForgotPasswordLoading = true
                                forgotPasswordError = null
                                forgotPasswordSuccess = false
                                coroutineScope.launch {
                                    val result = authRepository.sendPasswordResetEmail(resetEmail)
                                    isForgotPasswordLoading = false
                                    if (result.isSuccess) {
                                        forgotPasswordSuccess = true
                                    } else {
                                        forgotPasswordError = result.exceptionOrNull()?.message
                                            ?: "Failed to send password reset email."
                                    }
                                }
                            },
                            onSearchOjasId = { email ->
                                authRepository.recoverOjasId(email)
                            },
                            isForgotPasswordLoading = isForgotPasswordLoading,
                            forgotPasswordError = forgotPasswordError,
                            forgotPasswordSuccess = forgotPasswordSuccess,
                            onDismissForgotPassword = {
                                forgotPasswordError = null
                                forgotPasswordSuccess = false
                            },
                            onNavigateToSignup = {
                                authErrorMessage = null
                                currentAuthScreen = AuthScreen.SIGNUP
                            },
                            onBackClick = {
                                isAuthGateOpen = false
                                pendingAuthorizedAction = null
                                authErrorMessage = null
                            },
                            promptMessage = authPromptMessage,
                            onActionNotice = showNotice,
                            isLoading = isAuthProcessing,
                            errorMessage = authErrorMessage
                        )
                    }

                    AuthScreen.SIGNUP -> {
                        SignupScreen(
                            onSignupClick = { email, password ->
                                isAuthProcessing = true
                                authErrorMessage = null
                                coroutineScope.launch {
                                    val result = authRepository.signup(email, password)
                                    isAuthProcessing = false
                                    if (result.isSuccess) {
                                        val postAction = pendingAuthorizedAction
                                        pendingAuthorizedAction = null
                                        isAuthGateOpen = false
                                        postAction?.invoke()
                                    } else {
                                        authErrorMessage = result.exceptionOrNull()?.message
                                            ?: "Account creation failed"
                                    }
                                }
                            },
                            onGoogleSignInClick = {
                                triggerGoogleSignIn()
                            },
                            onPhoneSignInClick = {
                                authErrorMessage = null
                                phoneAuthStep = PhoneAuthStep.ENTER_PHONE
                                currentAuthScreen = AuthScreen.PHONE_OTP
                            },
                            onNavigateToLogin = {
                                authErrorMessage = null
                                currentAuthScreen = AuthScreen.LOGIN
                            },
                            onActionNotice = showNotice,
                            isLoading = isAuthProcessing,
                            errorMessage = authErrorMessage
                        )
                    }

                    AuthScreen.PHONE_OTP -> {
                        PhoneAuthScreen(
                            step = phoneAuthStep,
                            targetPhoneNumber = phoneTargetNumber,
                            isLoading = isAuthProcessing,
                            errorMessage = authErrorMessage,
                            onActionNotice = showNotice,
                            onSendOtp = { formattedPhone ->
                                isAuthProcessing = true
                                authErrorMessage = null
                                phoneTargetNumber = formattedPhone
                                coroutineScope.launch {
                                    authRepository.sendPhoneOtp(
                                        phoneNumber = formattedPhone,
                                        activity = activity,
                                        onCodeSent = { verificationId, _ ->
                                            isAuthProcessing = false
                                            phoneVerificationId = verificationId
                                            phoneAuthStep = PhoneAuthStep.VERIFY_OTP
                                            showNotice("Verification code sent to $formattedPhone")
                                        },
                                        onVerificationFailed = { exception ->
                                            isAuthProcessing = false
                                            authErrorMessage = exception.message ?: "Failed to send verification code."
                                        },
                                        onAutoVerified = { user ->
                                            isAuthProcessing = false
                                            val postAction = pendingAuthorizedAction
                                            pendingAuthorizedAction = null
                                            isAuthGateOpen = false
                                            postAction?.invoke()
                                        }
                                    )
                                }
                            },
                            onVerifyOtp = { otpCode ->
                                isAuthProcessing = true
                                authErrorMessage = null
                                coroutineScope.launch {
                                    val result = authRepository.verifyPhoneOtp(
                                        verificationId = phoneVerificationId,
                                        otpCode = otpCode,
                                        phoneNumber = phoneTargetNumber
                                    )
                                    isAuthProcessing = false
                                    if (result.isSuccess) {
                                        val postAction = pendingAuthorizedAction
                                        pendingAuthorizedAction = null
                                        isAuthGateOpen = false
                                        postAction?.invoke()
                                    } else {
                                        authErrorMessage = result.exceptionOrNull()?.message
                                            ?: "Invalid verification code. Please try again."
                                    }
                                }
                            },
                            onResendOtp = {
                                if (phoneTargetNumber.isNotBlank()) {
                                    isAuthProcessing = true
                                    authErrorMessage = null
                                    coroutineScope.launch {
                                        authRepository.sendPhoneOtp(
                                            phoneNumber = phoneTargetNumber,
                                            activity = activity,
                                            onCodeSent = { verificationId, _ ->
                                                isAuthProcessing = false
                                                phoneVerificationId = verificationId
                                                showNotice("A new verification code has been sent.")
                                            },
                                            onVerificationFailed = { exception ->
                                                isAuthProcessing = false
                                                authErrorMessage = exception.message ?: "Failed to resend code."
                                            }
                                        )
                                    }
                                }
                            },
                            onBackClick = {
                                authErrorMessage = null
                                if (phoneAuthStep == PhoneAuthStep.VERIFY_OTP) {
                                    phoneAuthStep = PhoneAuthStep.ENTER_PHONE
                                } else {
                                    currentAuthScreen = AuthScreen.LOGIN
                                }
                            }
                        )
                    }
                }
            }

            // Priority 3: Browse First — Main Application Experience (Authenticated or Unauthenticated)
            else -> {
                val authenticatedUser = (authState as? AuthState.Authenticated)?.user

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        OjasBottomBar(
                            currentDestination = currentDestination,
                            onDestinationSelected = { destination ->
                                currentDestination = destination
                            },
                            onCreateClick = {
                                requireAuthentication(prompt = "Sign in to create posts and videos") {
                                    isCreateSheetOpen = true
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Crossfade(
                            targetState = currentDestination,
                            label = "destination_crossfade"
                        ) { destination ->
                            when (destination) {
                                OjasDestination.HOME -> {
                                    HomeScreen(
                                        onActionNotice = showNotice,
                                        onRequireAuth = { action ->
                                            requireAuthentication(
                                                prompt = "Sign in to continue",
                                                onAuthorized = action
                                            )
                                        }
                                    )
                                }

                                OjasDestination.OJ -> {
                                    OjScreen(
                                        selectedCategory = selectedOjCategory,
                                        onFilterClick = { isFilterSheetOpen = true },
                                        onActionNotice = showNotice,
                                        targetOjId = deepLinkTargetOjId,
                                        onTargetOjIdConsumed = { deepLinkTargetOjId = null },
                                        currentUserId = authenticatedUser?.userId,
                                        currentUser = authenticatedUser,
                                        ojRepository = ojRepository,
                                        socialInteractionRepository = socialRepository,
                                        watchAnalyticsRepository = watchAnalyticsRepository,
                                        recommendationRepository = recommendationRepository,
                                        onRequireAuth = { action ->
                                            requireAuthentication(
                                                prompt = "Sign in to interact with videos",
                                                onAuthorized = action
                                            )
                                        }
                                    )
                                }

                                OjasDestination.EXPLORE -> {
                                    ExploreScreen(
                                        onActionNotice = showNotice
                                    )
                                }

                                OjasDestination.YOU -> {
                                    YouScreen(
                                        onActionNotice = showNotice,
                                        currentUser = authenticatedUser,
                                        authRepository = authRepository,
                                        socialInteractionRepository = socialRepository,
                                        postRepository = postRepository,
                                        ojRepository = ojRepository,
                                        onLoginClick = {
                                            requireAuthentication(prompt = "Sign in to your account") {}
                                        },
                                        onLogoutClick = {
                                            coroutineScope.launch {
                                                authRepository.logout()
                                                showNotice("Logged out")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Create Action Sheet (Modal Bottom Sheet showing Post and OJ)
                    if (isCreateSheetOpen) {
                        CreateActionSheet(
                            onDismissRequest = { isCreateSheetOpen = false },
                            onOptionSelected = { option ->
                                isCreateSheetOpen = false
                                if (option.equals("Post", ignoreCase = true)) {
                                    isCreatePostOpen = true
                                } else if (option.equals("OJ", ignoreCase = true)) {
                                    isCreateOjOpen = true
                                }
                            }
                        )
                    }

                    // Create Post Screen
                    if (isCreatePostOpen) {
                        CreatePostScreen(
                            currentUser = authenticatedUser,
                            postRepository = postRepository,
                            mediaStorageService = mediaStorageService,
                            onDismiss = { isCreatePostOpen = false },
                            onPublishSuccess = { _ ->
                                isCreatePostOpen = false
                            },
                            onActionNotice = showNotice
                        )
                    }

                    // Create OJ Screen
                    if (isCreateOjOpen) {
                        CreateOjScreen(
                            currentUser = authenticatedUser,
                            ojRepository = ojRepository,
                            mediaStorageService = mediaStorageService,
                            onDismiss = { isCreateOjOpen = false },
                            onPublishSuccess = { _ ->
                                isCreateOjOpen = false
                            },
                            onActionNotice = showNotice
                        )
                    }

                    // OJ Category Filter Sheet
                    if (isFilterSheetOpen) {
                        CategoryFilterSheet(
                            selectedCategory = selectedOjCategory,
                            onDismissRequest = { isFilterSheetOpen = false },
                            onCategorySelected = { category ->
                                selectedOjCategory = category
                                if (category != null) {
                                    showNotice("Filter applied: $category")
                                } else {
                                    showNotice("Filter cleared: Showing all content")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
