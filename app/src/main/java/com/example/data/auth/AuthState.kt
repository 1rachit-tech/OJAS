package com.example.data.auth

import com.example.data.model.OjasUser

/**
 * Authentication state definition for OJAS session handling.
 */
sealed interface AuthState {
    /**
     * No active authenticated session.
     */
    data object Unauthenticated : AuthState

    /**
     * Resolving or restoring an existing session or verifying credentials.
     */
    data object Loading : AuthState

    /**
     * Authenticated session where initial user setup (Display Name & Username) is required.
     */
    data class SetupRequired(val user: OjasUser) : AuthState

    /**
     * Active authenticated session with a verified user identity and completed setup.
     */
    data class Authenticated(val user: OjasUser) : AuthState

    /**
     * Configuration status indicating required backend credentials/services are missing.
     */
    data class ConfigMissing(val message: String) : AuthState

    /**
     * Initialization error indicating the provider failed during startup or initialization.
     */
    data class InitializationFailed(val reason: String) : AuthState
}
