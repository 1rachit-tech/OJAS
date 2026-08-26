package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AccountHelpDialog
import com.example.ui.components.AuthDivider
import com.example.ui.components.ForgotPasswordDialog
import com.example.ui.components.ForgotUsernameDialog
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.PhoneSignInButton
import com.example.ui.theme.OjasRoyalBlue
import com.example.ui.theme.OjasSlate400
import com.example.ui.theme.OjasSlate500
import com.example.ui.theme.OjasSlate800

private enum class ActiveRecoveryDialog {
    NONE,
    FORGOT_PASSWORD,
    FORGOT_OJAS_ID,
    ACCOUNT_HELP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    onNavigateToSignup: () -> Unit,
    onActionNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
    onGoogleSignInClick: (() -> Unit)? = null,
    onPhoneSignInClick: (() -> Unit)? = null,
    onForgotPasswordClick: ((email: String) -> Unit)? = null,
    onSearchOjasId: (suspend (email: String) -> Result<com.example.data.model.OjasUser?>)? = null,
    onBackClick: (() -> Unit)? = null,
    promptMessage: String? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    isForgotPasswordLoading: Boolean = false,
    forgotPasswordError: String? = null,
    forgotPasswordSuccess: Boolean = false,
    onDismissForgotPassword: (() -> Unit)? = null
) {
    var emailOrUsername by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var activeDialog by remember { mutableStateOf(ActiveRecoveryDialog.NONE) }
    var recoveryPrefilledEmail by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Login",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("login_page_title")
                    )
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("login_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Identity & Header
            Text(
                text = "OJAS",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    letterSpacing = 2.sp
                ),
                color = OjasRoyalBlue
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = promptMessage ?: "Sign in to your account",
                style = MaterialTheme.typography.bodyMedium,
                color = OjasSlate500,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Error notice banner if authentication fails
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                        .testTag("login_error_banner")
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 1. Existing User Login Fields
            // Email or Username field
            OutlinedTextField(
                value = emailOrUsername,
                onValueChange = { emailOrUsername = it },
                label = { Text("Email or @username") },
                placeholder = { Text("Enter your email or username") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = null,
                        tint = OjasSlate500,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OjasRoyalBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_username_field")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password field (secure, no plain text exposure)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                placeholder = { Text("Enter your password") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = OjasSlate500,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.testTag("login_toggle_password_visibility")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = OjasSlate500,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (emailOrUsername.isNotBlank() && password.isNotBlank()) {
                            onLoginClick(emailOrUsername, password)
                        } else {
                            onActionNotice("Please enter email/username and password")
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OjasRoyalBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password_field")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Account Recovery Links (Zero SMS Cost: Firebase Reset & ID Recovery)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        recoveryPrefilledEmail = if (emailOrUsername.contains("@")) emailOrUsername else ""
                        activeDialog = ActiveRecoveryDialog.FORGOT_OJAS_ID
                    },
                    modifier = Modifier.testTag("login_forgot_username_button")
                ) {
                    Text(
                        text = "Forgot OJAS ID?",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = OjasSlate500
                    )
                }

                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        recoveryPrefilledEmail = if (emailOrUsername.contains("@")) emailOrUsername else ""
                        activeDialog = ActiveRecoveryDialog.FORGOT_PASSWORD
                    },
                    modifier = Modifier.testTag("login_forgot_password_button")
                ) {
                    Text(
                        text = "Forgot password?",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = OjasRoyalBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Login Action Button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (emailOrUsername.isBlank() || password.isBlank()) {
                        onActionNotice("Please enter both email/username and password")
                    } else {
                        onLoginClick(emailOrUsername, password)
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("login_submit_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OjasRoyalBlue,
                    contentColor = MaterialTheme.colorScheme.surface
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Login",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Social / Google Authentication Option
            AuthDivider()

            Spacer(modifier = Modifier.height(12.dp))

            // Google Sign In Option
            GoogleSignInButton(
                onClick = {
                    focusManager.clearFocus()
                    onGoogleSignInClick?.invoke()
                },
                isLoading = isLoading,
                text = "Continue with Google"
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Help & Troubleshooting Link
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    activeDialog = ActiveRecoveryDialog.ACCOUNT_HELP
                },
                modifier = Modifier.testTag("login_account_help_button")
            ) {
                Text(
                    text = "Can't access your account? Account Help",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = OjasSlate500
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // New User Account Creation Entry
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Don't have an account?",
                    style = MaterialTheme.typography.bodySmall,
                    color = OjasSlate500
                )
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    onClick = onNavigateToSignup,
                    modifier = Modifier.testTag("login_create_account_button")
                ) {
                    Text(
                        text = "Create an account",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = OjasRoyalBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        when (activeDialog) {
            ActiveRecoveryDialog.FORGOT_PASSWORD -> {
                ForgotPasswordDialog(
                    onDismissRequest = {
                        activeDialog = ActiveRecoveryDialog.NONE
                        onDismissForgotPassword?.invoke()
                    },
                    onSubmitEmail = { resetEmail ->
                        onForgotPasswordClick?.invoke(resetEmail)
                    },
                    isLoading = isForgotPasswordLoading,
                    errorMessage = forgotPasswordError,
                    isSuccessSent = forgotPasswordSuccess,
                    initialEmail = recoveryPrefilledEmail.ifBlank {
                        if (emailOrUsername.contains("@")) emailOrUsername else ""
                    }
                )
            }

            ActiveRecoveryDialog.FORGOT_OJAS_ID -> {
                ForgotUsernameDialog(
                    onDismissRequest = {
                        activeDialog = ActiveRecoveryDialog.NONE
                    },
                    onSearchOjasId = onSearchOjasId,
                    onIdSelected = { username ->
                        emailOrUsername = username
                        activeDialog = ActiveRecoveryDialog.NONE
                    },
                    onUseEmailToLogin = { email ->
                        emailOrUsername = email
                        activeDialog = ActiveRecoveryDialog.NONE
                    },
                    onGoogleSignInClick = {
                        activeDialog = ActiveRecoveryDialog.NONE
                        onGoogleSignInClick?.invoke()
                    },
                    onForgotPasswordClick = { email ->
                        recoveryPrefilledEmail = email
                        activeDialog = ActiveRecoveryDialog.FORGOT_PASSWORD
                    },
                    initialEmail = recoveryPrefilledEmail.ifBlank {
                        if (emailOrUsername.contains("@")) emailOrUsername else ""
                    }
                )
            }

            ActiveRecoveryDialog.ACCOUNT_HELP -> {
                AccountHelpDialog(
                    onDismissRequest = { activeDialog = ActiveRecoveryDialog.NONE },
                    onForgotPasswordClick = {
                        recoveryPrefilledEmail = if (emailOrUsername.contains("@")) emailOrUsername else ""
                        activeDialog = ActiveRecoveryDialog.FORGOT_PASSWORD
                    },
                    onForgotUsernameClick = {
                        recoveryPrefilledEmail = if (emailOrUsername.contains("@")) emailOrUsername else ""
                        activeDialog = ActiveRecoveryDialog.FORGOT_OJAS_ID
                    },
                    onGoogleSignInClick = {
                        activeDialog = ActiveRecoveryDialog.NONE
                        onGoogleSignInClick?.invoke()
                    }
                )
            }

            ActiveRecoveryDialog.NONE -> Unit
        }
    }
}
