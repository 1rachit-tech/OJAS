package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.data.model.OjasUser
import com.example.ui.theme.OjasRoyalBlue
import com.example.ui.theme.OjasSlate500
import com.example.ui.theme.OjasSlate800

/**
 * Dialog to help users who forgot their OJAS username / ID.
 * Provides interactive search using their registered email and options to log in or reset password.
 */
@Composable
fun ForgotUsernameDialog(
    onDismissRequest: () -> Unit,
    onSearchOjasId: (suspend (email: String) -> Result<OjasUser?>)? = null,
    onIdSelected: ((username: String) -> Unit)? = null,
    onUseEmailToLogin: ((email: String) -> Unit)? = null,
    onGoogleSignInClick: (() -> Unit)? = null,
    onForgotPasswordClick: ((email: String) -> Unit)? = null,
    initialEmail: String = ""
) {
    var email by remember { mutableStateOf(initialEmail) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchedUser by remember { mutableStateOf<OjasUser?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val isEmailValid = email.isNotBlank() && email.contains("@") && email.contains(".")

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("forgot_username_dialog"),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Outlined.AlternateEmail,
                contentDescription = null,
                tint = OjasRoyalBlue,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Find Your OJAS ID",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!hasSearched) {
                    Text(
                        text = "Enter your registered email address to find your @username or restore access to your account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OjasSlate500,
                        textAlign = TextAlign.Center
                    )

                    if (searchError != null) {
                        Text(
                            text = searchError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().testTag("forgot_username_error")
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            searchError = null
                        },
                        label = { Text("Registered Email") },
                        placeholder = { Text("name@example.com") },
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
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (isEmailValid && !isSearching && onSearchOjasId != null) {
                                    isSearching = true
                                    searchError = null
                                    coroutineScope.launch {
                                        val res = onSearchOjasId(email.trim())
                                        isSearching = false
                                        if (res.isSuccess) {
                                            searchedUser = res.getOrNull()
                                            hasSearched = true
                                        } else {
                                            searchError = res.exceptionOrNull()?.message ?: "Lookup failed"
                                        }
                                    }
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
                            .testTag("forgot_username_email_field")
                    )

                    Button(
                        onClick = {
                            if (isEmailValid && !isSearching && onSearchOjasId != null) {
                                isSearching = true
                                searchError = null
                                coroutineScope.launch {
                                    val res = onSearchOjasId(email.trim())
                                    isSearching = false
                                    if (res.isSuccess) {
                                        searchedUser = res.getOrNull()
                                        hasSearched = true
                                    } else {
                                        searchError = res.exceptionOrNull()?.message ?: "Lookup failed"
                                    }
                                }
                            }
                        },
                        enabled = isEmailValid && !isSearching,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OjasRoyalBlue),
                        modifier = Modifier.fillMaxWidth().testTag("forgot_username_search_button")
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Find OJAS ID")
                        }
                    }
                } else {
                    // Searched results state
                    if (searchedUser != null) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "OJAS Account Found:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "@${searchedUser?.username}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = OjasRoyalBlue
                                    )
                                )
                                if (!searchedUser?.displayName.isNullOrBlank()) {
                                    Text(
                                        text = "${searchedUser?.displayName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OjasSlate500
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val u = searchedUser?.username
                                onDismissRequest()
                                if (!u.isNullOrBlank()) {
                                    onIdSelected?.invoke(u)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OjasRoyalBlue),
                            modifier = Modifier.fillMaxWidth().testTag("forgot_username_fill_button")
                        ) {
                            Text("Log in as @${searchedUser?.username}")
                        }
                    } else {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Account Access:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "You can sign in directly using your email address ($email) or Google account. Your @username will be visible on your profile page once you log in.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = OjasSlate500
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onDismissRequest()
                                onUseEmailToLogin?.invoke(email.trim())
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OjasRoyalBlue),
                            modifier = Modifier.fillMaxWidth().testTag("forgot_username_use_email_button")
                        ) {
                            Text("Use Email to Log In")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onDismissRequest()
                            onForgotPasswordClick?.invoke(email.trim())
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LockReset,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Password for this account", fontSize = 13.sp)
                    }
                }

                if (onGoogleSignInClick != null) {
                    GoogleSignInButton(
                        onClick = {
                            onDismissRequest()
                            onGoogleSignInClick()
                        },
                        text = "Sign in with Google"
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OjasRoyalBlue),
                modifier = Modifier.testTag("forgot_username_done_button")
            ) {
                Text("Close")
            }
        }
    )
}

/**
 * Comprehensive Account Help & Recovery Guidance Dialog.
 */
@Composable
fun AccountHelpDialog(
    onDismissRequest: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onForgotUsernameClick: () -> Unit,
    onGoogleSignInClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("account_help_dialog"),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Outlined.HelpOutline,
                contentDescription = null,
                tint = OjasRoyalBlue,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Account Access & Recovery",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "OJAS uses secure, instant authentication via Google and Email. Choose an option below to regain access:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OjasSlate500
                )

                // Option 1: Google Sign-In Recovery
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "1-Tap Google Sign-In",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "If you created your account with Google, simply sign in to instantly restore your OJAS ID and all your content.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = OjasSlate500
                        )
                    }
                }

                // Action Buttons
                OutlinedButton(
                    onClick = {
                        onDismissRequest()
                        onForgotPasswordClick()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LockReset,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Forgotten Password", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        onDismissRequest()
                        onForgotUsernameClick()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AlternateEmail,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Look up OJAS ID", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OjasRoyalBlue),
                modifier = Modifier.testTag("account_help_close_button")
            ) {
                Text("Close")
            }
        }
    )
}
