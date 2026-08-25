package com.example.data.repository

import com.example.data.auth.AuthRepository
import com.example.data.model.OjasUser

/**
 * Update request payload for user profile modification.
 */
data class ProfileUpdateRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null
)

/**
 * Repository interface for user profile data and username uniqueness validation.
 */
interface UserProfileRepository {
    suspend fun getProfile(userId: String): Result<OjasUser>
    suspend fun updateProfile(userId: String, updates: ProfileUpdateRequest): Result<OjasUser>
    suspend fun checkUsernameAvailability(username: String): Result<Boolean>
}

/**
 * Backend-ready implementation of [UserProfileRepository].
 */
class OjasUserProfileRepository(
    private val authRepository: AuthRepository? = null
) : UserProfileRepository {

    override suspend fun getProfile(userId: String): Result<OjasUser> {
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID cannot be empty."))
        }
        return authRepository?.getUserProfile(userId)
            ?: Result.failure(IllegalStateException("Backend database provider is not configured."))
    }

    override suspend fun updateProfile(
        userId: String,
        updates: ProfileUpdateRequest
    ): Result<OjasUser> {
        if (userId.isBlank()) {
            return Result.failure(IllegalStateException("Authentication required to update profile."))
        }
        return authRepository?.updateUserProfile(
            userId = userId,
            displayName = updates.displayName,
            bio = updates.bio,
            avatarUrl = updates.avatarUrl
        ) ?: Result.failure(IllegalStateException("Backend database provider is not configured."))
    }

    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> {
        val cleaned = username.trim().removePrefix("@")
        if (cleaned.length < 3 || cleaned.length > 30 || !cleaned.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return Result.failure(IllegalArgumentException("Invalid username format. Must be 3-30 alphanumeric characters or underscores."))
        }
        return authRepository?.checkUsernameAvailability(cleaned)
            ?: Result.failure(IllegalStateException("Backend database provider is not configured."))
    }
}
