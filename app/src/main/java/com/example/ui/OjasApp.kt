package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.SignupScreen
import com.example.ui.screens.YouScreen
import com.example.ui.theme.OjasTheme
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private enum class AuthScreen { LOGIN, SIGNUP }

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
        var destination by remember { mutableStateOf(OjasDestination.HOME) }
        var deepLink by remember { mutableStateOf<String?>(null) }
        var gateOpen by remember { mutableStateOf(false) }
        var authScreen by remember { mutableStateOf(AuthScreen.LOGIN) }
        var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        var prompt by remember { mutableStateOf<String?>(null) }
        var authError by remember { mutableStateOf<String?>(null) }
        var loading by remember { mutableStateOf(false) }
        var forgotLoading by remember { mutableStateOf(false) }
        var forgotError by remember { mutableStateOf<String?>(null) }
        var forgotSuccess by remember { mutableStateOf(false) }
        var createSheet by remember { mutableStateOf(false) }
        var createPost by remember { mutableStateOf(false) }
        var createOj by remember { mutableStateOf(false) }
        var filterOpen by remember { mutableStateOf(false) }
        var selectedCategory by remember { mutableStateOf<String?>(null) }

        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val snackbars = remember { SnackbarHostState() }
        val notice: (String) -> Unit = { message ->
            scope.launch {
                snackbars.currentSnackbarData?.dismiss()
                snackbars.showSnackbar(message)
            }
        }

        LaunchedEffect(initialDeepLinkOjId) {
            if (!initialDeepLinkOjId.isNullOrBlank()) {
                deepLink = initialDeepLinkOjId
                destination = OjasDestination.OJ
                onDeepLinkConsumed?.invoke()
            }
        }

        LaunchedEffect(authState) {
            when (val state = authState) {
                is AuthState.Authenticated,
                is AuthState.SetupRequired -> {
                    loading = false
                    gateOpen = false
                    authError = null
                }
                is AuthState.ConfigMissing -> {
                    loading = false
                    authError = state.message
                }
                is AuthState.InitializationFailed -> {
                    loading = false
                    authError = state.reason
                }
                else -> Unit
            }
        }

        fun finishAuth() {
            authRepository.checkSession()
            loading = false
            authError = null
            gateOpen = false
            pendingAction?.let { action ->
                pendingAction = null
                action()
            }
        }

        fun requireAuth(text: String? = null, action: () -> Unit) {
            if (authState is AuthState.Authenticated) {
                action()
            } else {
                pendingAction = action
                prompt = text
                authError = null
                authScreen = AuthScreen.LOGIN
                gateOpen = true
            }
        }

        fun google() {
            if (loading) return
            loading = true
            authError = null
            scope.launch {
                try {
                    val webClientId = run {
                        val resourceId = context.resources.getIdentifier(
                            "default_web_client_id",
                            "string",
                            context.packageName
                        )
                        if (resourceId != 0) {
                            context.getString(resourceId).trim()
                        } else {
                            runCatching {
                                context.getString(com.example.R.string.default_web_client_id).trim()
                            }.getOrDefault("")
                        }
                    }

                    if (!webClientId.matches(Regex("^[0-9]+-[a-zA-Z0-9_.-]+\\.apps\\.googleusercontent\\.com$"))) {
                        authError = "Google Sign-In is not configured correctly. Verify the Firebase Google provider and google-services.json."
                        loading = false
                        return@launch
                    }

                    val option = GetSignInWithGoogleOption.Builder(webClientId).build()
                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(option)
                        .build()
                    val credential = CredentialManager.create(context)
                        .getCredential(context, request)
                        .credential

                    if (credential !is CustomCredential ||
                        credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        authError = "Google returned an unsupported credential."
                        loading = false
                        return@launch
                    }

                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val result = authRepository.signInWithGoogle(
                        googleCredential.idToken,
                        googleCredential.id,
                        googleCredential.displayName
                    )

                    if (result.isSuccess) {
                        finishAuth()
                    } else {
                        loading = false
                        authError = result.exceptionOrNull()?.message ?: "Google sign-in failed."
                    }
                } catch (_: GetCredentialCancellationException) {
                    loading = false
                } catch (e: NoCredentialException) {
                    loading = false
                    authError = "No Google account could be accessed. Check Google Play services and try again."
                } catch (e: Exception) {
                    loading = false
                    authError = e.message ?: "Google sign-in could not be completed."
                }
            }
        }

        when {
            authState is AuthState.SetupRequired -> {
                val state = authState as AuthState.SetupRequired
                SetupScreen(
                    onCompleteSetup = { displayName, username, avatarUrl ->
                        loading = true
                        authError = null
                        scope.launch {
                            val result = authRepository.completeSetup(displayName, username, avatarUrl)
                            if (result.isSuccess) finishAuth()
                            else {
                                loading = false
                                authError = result.exceptionOrNull()?.message ?: "Failed to save profile setup."
                            }
                        }
                    },
                    onActionNotice = notice,
                    initialDisplayName = state.user.displayName,
                    initialUsername = state.user.username,
                    isLoading = loading,
                    errorMessage = authError
                )
            }

            gateOpen && authState !is AuthState.Authenticated -> {
                when (authScreen) {
                    AuthScreen.LOGIN -> LoginScreen(
                        onLoginClick = { email, password ->
                            if (loading) return@LoginScreen
                            loading = true
                            authError = null
                            scope.launch {
                                val result = authRepository.login(email, password)
                                if (result.isSuccess) finishAuth()
                                else {
                                    loading = false
                                    authError = result.exceptionOrNull()?.message ?: "Authentication failed."
                                }
                            }
                        },
                        onNavigateToSignup = {
                            authError = null
                            authScreen = AuthScreen.SIGNUP
                        },
                        onActionNotice = notice,
                        onGoogleSignInClick = ::google,
                        onForgotPasswordClick = { email ->
                            forgotLoading = true
                            forgotError = null
                            forgotSuccess = false
                            scope.launch {
                                val result = authRepository.sendPasswordResetEmail(email)
                                forgotLoading = false
                                if (result.isSuccess) forgotSuccess = true
                                else forgotError = result.exceptionOrNull()?.message ?: "Failed to send password reset email."
                            }
                        },
                        onSearchOjasId = { email -> authRepository.recoverOjasId(email) },
                        onBackClick = {
                            gateOpen = false
                            pendingAction = null
                            authError = null
                        },
                        promptMessage = prompt,
                        isLoading = loading,
                        errorMessage = authError,
                        isForgotPasswordLoading = forgotLoading,
                        forgotPasswordError = forgotError,
                        forgotPasswordSuccess = forgotSuccess,
                        onDismissForgotPassword = {
                            forgotError = null
                            forgotSuccess = false
                        }
                    )

                    AuthScreen.SIGNUP -> SignupScreen(
                        onSignupClick = { email, password ->
                            if (loading) return@SignupScreen
                            loading = true
                            authError = null
                            scope.launch {
                                val result = authRepository.signup(email, password)
                                if (result.isSuccess) finishAuth()
                                else {
                                    loading = false
                                    authError = result.exceptionOrNull()?.message ?: "Account creation failed."
                                }
                            }
                        },
                        onGoogleSignInClick = ::google,
                        onNavigateToLogin = {
                            authError = null
                            authScreen = AuthScreen.LOGIN
                        },
                        onActionNotice = notice,
                        isLoading = loading,
                        errorMessage = authError
                    )
                }
            }

            else -> {
                val user = (authState as? AuthState.Authenticated)?.user
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbars) },
                    bottomBar = {
                        OjasBottomBar(
                            destination,
                            onDestinationSelected = { destination = it },
                            onCreateClick = {
                                requireAuth("Sign in to create posts and videos") {
                                    createSheet = true
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        Crossfade(destination, label = "destination_crossfade") { selectedDestination ->
                            when (selectedDestination) {
                                OjasDestination.HOME -> HomeScreen(
                                    onActionNotice = notice,
                                    onRequireAuth = { action -> requireAuth("Sign in to continue", action) }
                                )
                                OjasDestination.OJ -> OjScreen(
                                    selectedCategory = selectedCategory,
                                    onFilterClick = { filterOpen = true },
                                    onActionNotice = notice,
                                    targetOjId = deepLink,
                                    onTargetOjIdConsumed = { deepLink = null },
                                    currentUserId = user?.userId,
                                    currentUser = user,
                                    ojRepository = ojRepository,
                                    socialInteractionRepository = socialRepository,
                                    watchAnalyticsRepository = watchAnalyticsRepository,
                                    recommendationRepository = recommendationRepository,
                                    onRequireAuth = { action -> requireAuth("Sign in to interact with videos", action) }
                                )
                                OjasDestination.EXPLORE -> ExploreScreen(onActionNotice = notice)
                                OjasDestination.YOU -> YouScreen(
                                    onActionNotice = notice,
                                    currentUser = user,
                                    authRepository = authRepository,
                                    socialInteractionRepository = socialRepository,
                                    postRepository = postRepository,
                                    ojRepository = ojRepository,
                                    onLoginClick = { requireAuth("Sign in to your account") {} },
                                    onLogoutClick = {
                                        scope.launch {
                                            authRepository.logout()
                                            notice("Logged out")
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (createSheet) {
                        CreateActionSheet(
                            onDismissRequest = { createSheet = false },
                            onOptionSelected = { option ->
                                createSheet = false
                                if (option.equals("Post", true)) createPost = true
                                else if (option.equals("OJ", true)) createOj = true
                            }
                        )
                    }
                    if (createPost) CreatePostScreen(
                        currentUser = user,
                        postRepository = postRepository,
                        mediaStorageService = mediaStorageService,
                        onDismiss = { createPost = false },
                        onPublishSuccess = { createPost = false },
                        onActionNotice = notice
                    )
                    if (createOj) CreateOjScreen(
                        currentUser = user,
                        ojRepository = ojRepository,
                        mediaStorageService = mediaStorageService,
                        onDismiss = { createOj = false },
                        onPublishSuccess = { createOj = false },
                        onActionNotice = notice
                    )
                    if (filterOpen) CategoryFilterSheet(
                        selectedCategory = selectedCategory,
                        onDismissRequest = { filterOpen = false },
                        onCategorySelected = { category ->
                            selectedCategory = category
                            notice(if (category != null) "Filter applied: $category" else "Filter cleared: Showing all content")
                        }
                    )
                }
            }
        }
    }
}
