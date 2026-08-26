package com.example.ui.screens

import android.util.Patterns
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
import com.example.ui.components.AuthDivider
import com.example.ui.components.GoogleSignInButton
import com.example.ui.theme.OjasRoyalBlue
import com.example.ui.theme.OjasSlate500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onSignupClick: (String, String) -> Unit,
    onNavigateToLogin: () -> Unit,
    onActionNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
    onGoogleSignInClick: (() -> Unit)? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var localValidationError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    fun validateAndSubmit() {
        localValidationError = null
        val trimmedEmail = email.trim()
        when {
            trimmedEmail.isBlank() || password.isBlank() || confirmPassword.isBlank() -> localValidationError = "Please fill in all required fields."
            !Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches() -> localValidationError = "Please enter a valid email address."
            password.length < 6 -> localValidationError = "Password must be at least 6 characters."
            password != confirmPassword -> localValidationError = "Passwords do not match."
            else -> onSignupClick(trimmedEmail, password)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Signup", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("signup_page_title")) },
                navigationIcon = { IconButton(onClick = onNavigateToLogin, modifier = Modifier.testTag("signup_back_button")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Login", tint = MaterialTheme.colorScheme.onSurface) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background).verticalScroll(scrollState).imePadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(8.dp))
            Text("OJAS", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = 2.sp), color = OjasRoyalBlue)
            Spacer(Modifier.height(6.dp))
            Text("Create your OJAS account", style = MaterialTheme.typography.bodyMedium, color = OjasSlate500, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            val displayError = localValidationError ?: errorMessage
            if (displayError != null) {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f), RoundedCornerShape(8.dp)).padding(12.dp).testTag("signup_error_banner")) {
                    Text(displayError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
                Spacer(Modifier.height(16.dp))
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; localValidationError = null },
                label = { Text("Email") }, placeholder = { Text("Enter your email address") },
                leadingIcon = { Icon(Icons.Outlined.Email, null, tint = OjasSlate500, modifier = Modifier.size(20.dp)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OjasRoyalBlue, unfocusedBorderColor = MaterialTheme.colorScheme.outline), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("signup_email_field")
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it; localValidationError = null }, label = { Text("Password") }, placeholder = { Text("Create a secure password") },
                leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = OjasSlate500, modifier = Modifier.size(20.dp)) },
                trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.testTag("signup_toggle_password_visibility")) { Icon(if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (passwordVisible) "Hide password" else "Show password", tint = OjasSlate500, modifier = Modifier.size(20.dp)) } },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OjasRoyalBlue, unfocusedBorderColor = MaterialTheme.colorScheme.outline), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("signup_password_field")
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = confirmPassword, onValueChange = { confirmPassword = it; localValidationError = null }, label = { Text("Confirm Password") }, placeholder = { Text("Re-enter your password") },
                leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = OjasSlate500, modifier = Modifier.size(20.dp)) },
                trailingIcon = { IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }, modifier = Modifier.testTag("signup_toggle_confirm_password_visibility")) { Icon(if (confirmPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (confirmPasswordVisible) "Hide password" else "Show password", tint = OjasSlate500, modifier = Modifier.size(20.dp)) } },
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); validateAndSubmit() }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OjasRoyalBlue, unfocusedBorderColor = MaterialTheme.colorScheme.outline), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("signup_confirm_password_field")
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { focusManager.clearFocus(); validateAndSubmit() }, enabled = !isLoading, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("signup_submit_button"), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = OjasRoyalBlue, contentColor = MaterialTheme.colorScheme.surface)) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.surface, strokeWidth = 2.dp) else Text("Signup", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            AuthDivider()
            Spacer(Modifier.height(14.dp))
            GoogleSignInButton(onClick = { focusManager.clearFocus(); onGoogleSignInClick?.invoke() }, isLoading = isLoading, text = "Continue with Google")
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("Already have an account?", style = MaterialTheme.typography.bodySmall, color = OjasSlate500)
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onNavigateToLogin, modifier = Modifier.testTag("signup_to_login_button")) { Text("Login", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = OjasRoyalBlue) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
