package com.example.data.repository

import com.example.data.model.ContentVisibility
import com.example.data.model.OjVideo
import com.example.data.model.OjVideoDraft
import com.example.data.model.OjasUser
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository interface for OJ vertical video operations and feed queries.
 * Supports category filtering, paginated feed retrieval, and video creation.
 */
interface OjRepository {
    suspend fun getPublicOjVideos(category: String? = null, page: Int = 1, pageSize: Int = 10): Result<List<OjVideo>>
    suspend fun getFollowingOjVideos(followedUserIds: List<String>, page: Int = 1, pageSize: Int = 10): Result<List<OjVideo>>
    suspend fun getOjVideosByUserId(userId: String): Result<List<OjVideo>>
    suspend fun getOjVideoById(ojId: String): Result<OjVideo>
    suspend fun createOjVideo(creatorId: String, draft: OjVideoDraft, user: OjasUser? = null): Result<OjVideo>
    suspend fun deleteOjVideo(userId: String, ojId: String): Result<Boolean>
    suspend fun syncLikeState(ojId: String, isLikedByMe: Boolean, likeCount: Long): Result<Boolean>
    suspend fun syncCommentCount(ojId: String, commentCount: Long): Result<Boolean>
    suspend fun syncViewCount(ojId: String, viewCount: Long): Result<Boolean>
}

/**
 * Backend-ready implementation of [OjRepository].
 * Manages thread-safe OJ short video lifecycle, author authorization, and real content queries.
 */
class OjasOjRepository(
    private val mediaStorageService: MediaStorageService? = null
) : OjRepository {
    private val publishedOjVideos = CopyOnWriteArrayList<OjVideo>()
    private val mutex = Mutex()

    override suspend fun getPublicOjVideos(category: String?, page: Int, pageSize: Int): Result<List<OjVideo>> {
        val cleanCat = category?.trim()?.takeIf { it.isNotBlank() && !it.equals("all", ignoreCase = true) }
        val filtered = publishedOjVideos.filter { video ->
            video.visibility == ContentVisibility.PUBLIC &&
                (cleanCat == null ||
                    video.category.equals(cleanCat, ignoreCase = true) ||
                    video.tags.any { it.equals(cleanCat, ignoreCase = true) })
        }
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, 50)
        val startIndex = (safePage - 1) * safePageSize
        if (startIndex >= filtered.size) {
            return Result.success(emptyList())
        }
        val endIndex = minOf(startIndex + safePageSize, filtered.size)
        return Result.success(filtered.subList(startIndex, endIndex))
    }

    override suspend fun getFollowingOjVideos(followedUserIds: List<String>, page: Int, pageSize: Int): Result<List<OjVideo>> {
        if (followedUserIds.isEmpty()) {
            return Result.success(emptyList())
        }
        val followedSet = followedUserIds.toSet()
        val filtered = publishedOjVideos.filter { video ->
            video.visibility == ContentVisibility.PUBLIC && video.creatorId in followedSet
        }.sortedByDescending { it.createdAt }
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, 50)
        val startIndex = (safePage - 1) * safePageSize
        if (startIndex >= filtered.size) {
            return Result.success(emptyList())
        }
        val endIndex = minOf(startIndex + safePageSize, filtered.size)
        return Result.success(filtered.subList(startIndex, endIndex))
    }

    override suspend fun getOjVideosByUserId(userId: String): Result<List<OjVideo>> {
        if (userId.isBlank()) {
            return Result.success(emptyList())
        }
        val userVideos = publishedOjVideos.filter { it.creatorId == userId }
        return Result.success(userVideos)
    }

    override suspend fun getOjVideoById(ojId: String): Result<OjVideo> {
        if (ojId.isBlank()) {
            return Result.failure(IllegalArgumentException("OJ ID cannot be empty."))
        }
        val found = publishedOjVideos.find { it.ojId == ojId }
            ?: return Result.failure(NoSuchElementException("OJ video not found with ID: $ojId"))
        return Result.success(found)
    }

    override suspend fun createOjVideo(creatorId: String, draft: OjVideoDraft, user: OjasUser?): Result<OjVideo> {
        if (creatorId.isBlank()) {
            return Result.failure(IllegalStateException("Authentication required to create an OJ video."))
        }
        if (draft.videoUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("Video storage reference or URL cannot be empty."))
        }

        return mutex.withLock {
            val ojId = "oj_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"
            val authorUsername = user?.username?.takeIf { it.isNotBlank() } ?: "user_${creatorId.take(6)}"
            val authorDisplayName = user?.displayName?.takeIf { it.isNotBlank() } ?: "OJAS Creator"

            val newOjVideo = OjVideo(
                ojId = ojId,
                creatorId = creatorId,
                creatorUsername = authorUsername,
                creatorDisplayName = authorDisplayName,
                creatorAvatarUrl = user?.avatarUrl,
                videoUrl = draft.videoUrl,
                thumbnailUrl = draft.thumbnailUrl,
                caption = draft.caption.trim(),
                tags = draft.tags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() },
                category = draft.category.trim().ifBlank { "general" },
                audioTrack = draft.audioTrack,
                visibility = draft.visibility,
                durationSeconds = draft.durationSeconds.coerceIn(1, 180),
                createdAt = System.currentTimeMillis(),
                likeCount = 0,
                commentCount = 0,
                shareCount = 0,
                viewCount = 0,
                isLikedByMe = false,
                isFollowedByMe = false
            )
            // Insert newest video at top of the stream
            publishedOjVideos.add(0, newOjVideo)
            Result.success(newOjVideo)
        }
    }

    override suspend fun deleteOjVideo(userId: String, ojId: String): Result<Boolean> {
        if (userId.isBlank() || ojId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID and OJ ID required for deletion."))
        }
        return mutex.withLock {
            val video = publishedOjVideos.find { it.ojId == ojId }
            if (video == null) {
                Result.failure(NoSuchElementException("OJ video not found with ID: $ojId"))
            } else if (video.creatorId != userId) {
                Result.failure(IllegalAccessException("Unauthorized: Users can only delete their own OJ videos."))
            } else {
                publishedOjVideos.remove(video)
                Result.success(true)
            }
        }
    }

    override suspend fun syncLikeState(ojId: String, isLikedByMe: Boolean, likeCount: Long): Result<Boolean> {
        if (ojId.isBlank()) return Result.failure(IllegalArgumentException("OJ ID cannot be empty."))
        return mutex.withLock {
            val index = publishedOjVideos.indexOfFirst { it.ojId == ojId }
            if (index >= 0) {
                val existing = publishedOjVideos[index]
                publishedOjVideos[index] = existing.copy(
                    isLikedByMe = isLikedByMe,
                    likeCount = likeCount.coerceAtLeast(0L)
                )
                Result.success(true)
            } else {
                Result.success(false)
            }
        }
    }

    override suspend fun syncCommentCount(ojId: String, commentCount: Long): Result<Boolean> {
        if (ojId.isBlank()) return Result.failure(IllegalArgumentException("OJ ID cannot be empty."))
        return mutex.withLock {
            val index = publishedOjVideos.indexOfFirst { it.ojId == ojId }
            if (index >= 0) {
                val existing = publishedOjVideos[index]
                publishedOjVideos[index] = existing.copy(
                    commentCount = commentCount.coerceAtLeast(0L)
                )
                Result.success(true)
            } else {
                Result.success(false)
            }
        }
    }

    override suspend fun syncViewCount(ojId: String, viewCount: Long): Result<Boolean> {
        if (ojId.isBlank()) return Result.failure(IllegalArgumentException("OJ ID cannot be empty."))
        return mutex.withLock {
            val index = publishedOjVideos.indexOfFirst { it.ojId == ojId }
            if (index >= 0) {
                val existing = publishedOjVideos[index]
                publishedOjVideos[index] = existing.copy(
                    viewCount = viewCount.coerceAtLeast(0L)
                )
                Result.success(true)
            } else {
                Result.success(false)
            }
        }
    }
}
