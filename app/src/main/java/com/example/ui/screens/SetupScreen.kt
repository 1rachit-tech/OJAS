package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OjasRoyalBlue
import com.example.ui.theme.OjasSlate100
import com.example.ui.theme.OjasSlate400
import com.example.ui.theme.OjasSlate500
import com.example.ui.theme.OjasSlate800

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onCompleteSetup: (displayName: String, username: String, avatarUrl: String?) -> Unit,
    onActionNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialDisplayName: String = "",
    initialUsername: String = "",
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var displayName by remember { mutableStateOf(initialDisplayName) }
    var username by remember { mutableStateOf(initialUsername) }
    var selectedAvatarUrl by remember { mutableStateOf<String?>(null) }
    var localValidationError by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    fun validateAndSubmit() {
        localValidationError = null
        val trimmedDisplayName = displayName.trim()
        val cleanedUsername = username.trim().removePrefix("@")

        if (trimmedDisplayName.isBlank()) {
            localValidationError = "Please enter your Display Name."
            return
        }

        if (cleanedUsername.isBlank()) {
            localValidationError = "Please choose a Username."
            return
        }

        if (!cleanedUsername.matches(Regex("^[a-zA-Z0-9_]{3,30}$"))) {
            localValidationError = "Username must be 3-30 characters (letters, numbers, underscores)."
            return
        }

        onCompleteSetup(trimmedDisplayName, cleanedUsername, selectedAvatarUrl)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Setup",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("setup_page_title")
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            // Skip photo or finish setup with entered data
                            validateAndSubmit()
                        },
                        enabled = !isLoading,
                        modifier = Modifier.testTag("setup_skip_button")
                    ) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = OjasSlate500
                        )
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
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle
            Text(
                text = "Complete your profile",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Choose your display name and unique @username to get started on OJAS.",
                style = MaterialTheme.typography.bodySmall,
                color = OjasSlate500,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Optional Profile Photo Picker
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(OjasSlate100)
                    .border(2.dp, OjasRoyalBlue.copy(alpha = 0.4f), CircleShape)
                    .clickable { onActionNotice("Coming Soon") }
                    .testTag("setup_avatar_picker"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddAPhoto,
                        contentDescription = "Add Profile Photo",
                        tint = OjasRoyalBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Photo",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = OjasSlate800
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Profile photo is optional",
                style = MaterialTheme.typography.labelSmall,
                color = OjasSlate400
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Error notice banner
            val displayError = localValidationError ?: errorMessage
            if (displayError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                        .testTag("setup_error_banner")
                ) {
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 1. Display Name Field
            OutlinedTextField(
                value = displayName,
                onValueChange = { 
                    displayName = it
                    localValidationError = null
                },
                label = { Text("Display Name") },
                placeholder = { Text("e.g. Alex Morgan") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Badge,
                        contentDescription = null,
                        tint = OjasSlate500,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
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
                    .testTag("setup_display_name_field")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Username Field (@username)
            OutlinedTextField(
                value = username,
                onValueChange = { 
                    username = it
                    localValidationError = null
                },
                label = { Text("Username") },
                placeholder = { Text("alexmorgan") },
                prefix = {
                    Text(
                        text = "@",
                        color = OjasRoyalBlue,
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.AlternateEmail,
                        contentDescription = null,
                        tint = OjasSlate500,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        validateAndSubmit()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OjasRoyalBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_username_field")
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 3. Primary Completion Action Button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    validateAndSubmit()
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("setup_submit_button"),
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
                        text = "Complete Setup",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
