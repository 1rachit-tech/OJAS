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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OjasRoyalBlue
import com.example.ui.theme.OjasSlate400
import com.example.ui.theme.OjasSlate500
import com.example.ui.theme.OjasSlate800
import kotlinx.coroutines.delay

enum class PhoneAuthStep {
    ENTER_PHONE,
    VERIFY_OTP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneAuthScreen(
    onSendOtp: (phoneNumber: String) -> Unit,
    onVerifyOtp: (otpCode: String) -> Unit,
    onResendOtp: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    step: PhoneAuthStep = PhoneAuthStep.ENTER_PHONE,
    targetPhoneNumber: String = "",
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onActionNotice: (String) -> Unit = {}
) {
    var countryCode by remember { mutableStateOf("+91") }
    var rawPhoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var resendCooldown by remember { mutableIntStateOf(60) }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // Countdown timer for OTP resend
    LaunchedEffect(step) {
        if (step == PhoneAuthStep.VERIFY_OTP) {
            resendCooldown = 60
            while (resendCooldown > 0) {
                delay(1000L)
                resendCooldown--
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (step == PhoneAuthStep.ENTER_PHONE) "Phone Sign In" else "Verify OTP",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("phone_auth_screen_title")
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("phone_auth_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
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
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Branding Header
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
                text = if (step == PhoneAuthStep.ENTER_PHONE) {
                    "Enter your phone number to sign in or register"
                } else {
                    "Enter the 6-digit code sent via SMS"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = OjasSlate500,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Error banner if any
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp)
                        .testTag("phone_auth_error_banner")
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (step == PhoneAuthStep.ENTER_PHONE) {
                // STEP 1: Enter Phone Number
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Country code selector / text field
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { 
                            if (it.startsWith("+") || it.isEmpty()) {
                                countryCode = it
                            } else {
                                countryCode = "+$it"
                            }
                        },
                        label = { Text("Code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .width(80.dp)
                            .testTag("phone_country_code_field"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OjasRoyalBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Phone number digits
                    OutlinedTextField(
                        value = rawPhoneNumber,
                        onValueChange = { input ->
                            rawPhoneNumber = input.filter { it.isDigit() }
                        },
                        label = { Text("Phone Number") },
                        placeholder = { Text("e.g. 9876543210") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Phone,
                                contentDescription = null,
                                tint = OjasSlate500,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (rawPhoneNumber.length >= 7) {
                                    val formatted = "$countryCode$rawPhoneNumber"
                                    onSendOtp(formatted)
                                } else {
                                    onActionNotice("Please enter a valid phone number.")
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("phone_number_field"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OjasRoyalBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "We will send a 6-digit verification code to this phone number.",
                    style = MaterialTheme.typography.labelSmall,
                    color = OjasSlate400,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Send OTP Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (rawPhoneNumber.length >= 7) {
                            val formatted = "$countryCode$rawPhoneNumber"
                            onSendOtp(formatted)
                        } else {
                            onActionNotice("Please enter a valid phone number.")
                        }
                    },
                    enabled = !isLoading && rawPhoneNumber.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("phone_send_otp_button"),
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
                            text = "Send Verification Code",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

            } else {
                // STEP 2: Verify OTP
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Verification code sent to:",
                            style = MaterialTheme.typography.labelSmall,
                            color = OjasSlate500
                        )
                        Text(
                            text = targetPhoneNumber.ifBlank { "$countryCode$rawPhoneNumber" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("phone_change_number_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Number",
                            modifier = Modifier.size(16.dp),
                            tint = OjasRoyalBlue
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = OjasRoyalBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 6-digit OTP code entry
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all { it.isDigit() }) {
                            otpCode = input
                            if (input.length == 6) {
                                focusManager.clearFocus()
                                onVerifyOtp(input)
                            }
                        }
                    },
                    label = { Text("6-Digit OTP Code") },
                    placeholder = { Text("123456") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = OjasSlate500,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 6.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (otpCode.length == 6) {
                                onVerifyOtp(otpCode)
                            } else {
                                onActionNotice("Please enter all 6 digits of the verification code.")
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phone_otp_field"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OjasRoyalBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Verify Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (otpCode.length == 6) {
                            onVerifyOtp(otpCode)
                        } else {
                            onActionNotice("Please enter all 6 digits of the verification code.")
                        }
                    },
                    enabled = !isLoading && otpCode.length == 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("phone_verify_otp_button"),
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
                            text = "Verify & Sign In",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Resend OTP Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (resendCooldown > 0) {
                        Text(
                            text = "Resend code in ${resendCooldown}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = OjasSlate400
                        )
                    } else {
                        TextButton(
                            onClick = {
                                resendCooldown = 60
                                onResendOtp()
                            },
                            enabled = !isLoading,
                            modifier = Modifier.testTag("phone_resend_otp_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = OjasRoyalBlue
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Resend Code",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = OjasRoyalBlue
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
