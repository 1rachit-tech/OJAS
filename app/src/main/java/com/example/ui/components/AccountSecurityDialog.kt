package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OjasUser
import com.example.data.security.OjasSecurityManager
import com.example.ui.theme.OjasGoldenYellow
import com.example.ui.theme.OjasRoyalBlue
import com.example.ui.theme.OjasSlate400
import com.example.ui.theme.OjasSlate500
import com.example.ui.theme.OjasSlate800

@Composable
fun AccountSecurityDialog(
    currentUser: OjasUser?,
    linkedProviders: List<String>,
    onDismissRequest: () -> Unit,
    onSendPasswordReset: () -> Unit,
    onSendEmailVerification: (() -> Unit)? = null,
    onRefreshVerificationStatus: (() -> Unit)? = null,
    onLinkGoogleClick: (() -> Unit)? = null,
    isEmailVerified: Boolean = false
) {
    val isGoogleLinked = linkedProviders.contains("google.com")
    val isPasswordLinked = linkedProviders.contains("password") || (currentUser?.email?.isNotBlank() == true)
    val hasTwoWayRecovery = isGoogleLinked && isPasswordLinked

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("account_security_dialog"),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = OjasRoyalBlue,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Account Security Center",
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
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Real-User & Integrity Status Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Device & Bot Protection Active",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (OjasSecurityManager.isIntegrityProtected()) {
                                    "Firebase App Check & Play Integrity active"
                                } else {
                                    "Rate-limiting & session integrity active"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = OjasSlate500
                            )
                        }
                    }
                }

                // Security Recommendation Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasTwoWayRecovery) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (hasTwoWayRecovery) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                            contentDescription = null,
                            tint = if (hasTwoWayRecovery) MaterialTheme.colorScheme.primary else OjasGoldenYellow,
                            modifier = Modifier.size(18.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (hasTwoWayRecovery) "Maximum Account Safety" else "Security Recommendation",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (hasTwoWayRecovery) {
                                    "Both Google Sign-In and Email/Password are linked. You have independent 2-way account recovery."
                                } else if (!isGoogleLinked) {
                                    "Link your Google account so you can sign in with 1-tap even if you forget your password."
                                } else {
                                    "Set an Email/Password so you have a secondary way to access your account."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = OjasSlate500
                            )
                        }
                    }
                }

                // Linked Sign-In Methods Section
                Text(
                    text = "Sign-In & Recovery Providers",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = OjasSlate800
                )

                // Email Provider Row
                ProviderDetailRow(
                    name = "Email / Password",
                    icon = Icons.Outlined.Email,
                    isLinked = isPasswordLinked,
                    detail = currentUser?.email.orEmpty().ifBlank { "Primary Login" },
                    badgeText = if (isEmailVerified) "Verified" else "Active",
                    isVerified = isEmailVerified
                )

                // Google Provider Row
                ProviderDetailRow(
                    name = "Google Account",
                    icon = Icons.Outlined.Security,
                    isLinked = isGoogleLinked,
                    detail = if (isGoogleLinked) "Linked & Verified (1-Tap Sign-In)" else "Not Linked",
                    badgeText = if (isGoogleLinked) "Linked" else null,
                    isVerified = isGoogleLinked
                )

                // OJAS Identity & Username
                ProviderDetailRow(
                    name = "OJAS Public Handle",
                    icon = Icons.Outlined.MarkEmailRead,
                    isLinked = true,
                    detail = "@${currentUser?.username.orEmpty()}",
                    badgeText = "Public ID",
                    isVerified = true
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Account Recovery Section
                Text(
                    text = "Account Recovery Actions",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = OjasSlate800
                )

                if (!isEmailVerified && currentUser?.email?.isNotBlank() == true && onSendEmailVerification != null) {
                    OutlinedButton(
                        onClick = onSendEmailVerification,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_security_verify_email_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MarkEmailRead,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send Email Verification Link", fontSize = 13.sp)
                    }

                    if (onRefreshVerificationStatus != null) {
                        OutlinedButton(
                            onClick = onRefreshVerificationStatus,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("account_security_refresh_verification_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh Verification Status", fontSize = 13.sp)
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        onDismissRequest()
                        onSendPasswordReset()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_security_reset_password_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Password Reset Link", fontSize = 13.sp)
                }

                if (!isGoogleLinked && onLinkGoogleClick != null) {
                    OutlinedButton(
                        onClick = {
                            onDismissRequest()
                            onLinkGoogleClick()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_security_link_google_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Link Google Account", fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OjasRoyalBlue),
                modifier = Modifier.testTag("account_security_close_button")
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun ProviderDetailRow(
    name: String,
    icon: ImageVector,
    isLinked: Boolean,
    detail: String,
    badgeText: String? = null,
    isVerified: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isLinked) OjasRoyalBlue else OjasSlate400,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = OjasSlate500
            )
        }
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isVerified) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = if (isVerified) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

