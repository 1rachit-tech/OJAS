package com.example.data.model

/**
 * Core user identity model foundation for OJAS.
 * Represents an authenticated user with a unique stable identity.
 */
data class OjasUser(
    val userId: String,
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSetupComplete: Boolean = false,
    val email: String? = null,
    val phoneNumber: String? = null,
    val isEmailVerified: Boolean = false
)
