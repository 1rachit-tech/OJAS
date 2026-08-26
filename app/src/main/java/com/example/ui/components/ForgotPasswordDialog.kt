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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OjasRoyalBlue
import com.example.ui.theme.OjasSlate500

@Composable
fun ForgotPasswordDialog(
    onDismissRequest: () -> Unit,
    onSubmitEmail: (email: String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    isSuccessSent: Boolean = false,
    initialEmail: String = ""
) {
    var email by remember { mutableStateOf(initialEmail) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isEmailValid = email.isNotBlank() && email.contains("@") && email.contains(".")

    AlertDialog(
        onDismissRequest = {
            keyboardController?.hide()
            if (!isLoading) onDismissRequest()
        },
        modifier = Modifier.testTag("forgot_password_dialog"),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = if (isSuccessSent) Icons.Outlined.CheckCircle else Icons.Outlined.LockReset,
                contentDescription = null,
                tint = if (isSuccessSent) MaterialTheme.colorScheme.primary else OjasRoyalBlue,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = if (isSuccessSent) "Reset Link Sent" else "Reset Password",
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isSuccessSent) {
                    Text(
                        text = "If an OJAS account exists with this email address, we have sent instructions to reset your password. Please check your inbox and spam folder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OjasSlate500,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Text(
                        text = "Enter the email address registered with your OJAS account. We'll send a secure password reset link to your inbox.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OjasSlate500,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("forgot_password_error")
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Account Email") },
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
                                if (isEmailValid && !isLoading) {
                                    keyboardController?.hide()
                                    onSubmitEmail(email.trim())
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
                            .testTag("forgot_password_email_field")
                    )
                }
            }
        },
        confirmButton = {
            if (isSuccessSent) {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        onDismissRequest()
                    },
                    modifier = Modifier.testTag("forgot_password_done_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OjasRoyalBlue)
                ) {
                    Text("Got It")
                }
            } else {
                Button(
                    onClick = {
                        if (isEmailValid && !isLoading) {
                            keyboardController?.hide()
                            onSubmitEmail(email.trim())
                        }
                    },
                    enabled = isEmailValid && !isLoading,
                    modifier = Modifier.testTag("forgot_password_submit_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OjasRoyalBlue)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Send Link")
                    }
                }
            }
        },
        dismissButton = {
            if (!isSuccessSent) {
                TextButton(
                    onClick = {
                        keyboardController?.hide()
                        onDismissRequest()
                    },
                    enabled = !isLoading,
                    modifier = Modifier.testTag("forgot_password_cancel_button")
                ) {
                    Text("Cancel", color = OjasSlate500)
                }
            }
        }
    )
}
