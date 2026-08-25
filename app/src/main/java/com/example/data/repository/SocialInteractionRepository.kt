package com.example.data.repository

import com.example.data.model.CommentRecord
import com.example.data.model.FollowRecord
import com.example.data.model.OjasUser
import com.example.data.model.TargetContentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Repository interface for social interactions (Likes, Comments, Follows).
 * Manages relationships, duplicate prevention, and paginated comment threads.
 */
interface SocialInteractionRepository {
    // Like / Unlike operations
    suspend fun setLike(userId: String, contentId: String, contentType: TargetContentType, isLiked: Boolean): Result<Boolean>
    suspend fun checkLikeStatus(userId: String, contentId: String): Result<Boolean>
    suspend fun getLikesCount(contentId: String, contentType: TargetContentType): Result<Long>
    suspend fun getUserLikedContentIds(userId: String, contentType: TargetContentType): Result<List<String>>

    // Comment operations
    suspend fun getComments(contentId: String, page: Int = 1, pageSize: Int = 20): Result<List<CommentRecord>>
    suspend fun postComment(authorId: String, contentId: String, contentType: TargetContentType, text: String, user: OjasUser? = null): Result<CommentRecord>
    suspend fun deleteComment(userId: String, commentId: String): Result<Boolean>
    suspend fun getCommentsCount(contentId: String, contentType: TargetContentType): Result<Long>

    // Follow / Unfollow operations
    suspend fun setFollow(followerId: String, followedId: String, isFollowing: Boolean): Result<Boolean>
    suspend fun checkFollowStatus(followerId: String, followedId: String): Result<Boolean>
    suspend fun getFollowedUserIds(followerId: String): Result<List<String>>
    suspend fun getFollowingCount(userId: String): Result<Int>
    suspend fun getFollowersCount(userId: String): Result<Int>
}

/**
 * Backend-ready implementation of [SocialInteractionRepository].
 * Enforces authenticated user authority, self-follow prevention, rapid-tap concurrency protection,
 * duplicate relationship prevention, and thread-safe real comment threads.
 */
class OjasSocialInteractionRepository : SocialInteractionRepository {

    // Thread-safe in-memory relationship store for active session consistency
    // Key format: "$userId:${contentType.name}:$contentId" -> LikeRecord
    private val activeLikes = ConcurrentHashMap<String, com.example.data.model.LikeRecord>()
    private val likeMutex = Mutex()

    // Key format: "$followerId:$followedId" -> FollowRecord
    private val activeFollows = ConcurrentHashMap<String, FollowRecord>()
    private val followMutex = Mutex()

    // Real thread-safe comment storage by contentId
    private val activeComments = ConcurrentHashMap<String, CopyOnWriteArrayList<CommentRecord>>()
    private val commentMutex = Mutex()

    override suspend fun setLike(
        userId: String,
        contentId: String,
        contentType: TargetContentType,
        isLiked: Boolean
    ): Result<Boolean> = likeMutex.withLock {
        if (userId.isBlank()) {
            return Result.failure(IllegalStateException("Authentication required to like content."))
        }
        if (contentId.isBlank()) {
            return Result.failure(IllegalArgumentException("Content ID cannot be empty."))
        }

        val relationshipKey = "$userId:${contentType.name}:$contentId"

        return if (isLiked) {
            if (!activeLikes.containsKey(relationshipKey)) {
                val record = com.example.data.model.LikeRecord(
                    likeId = "like_${System.currentTimeMillis()}_${userId.take(6)}",
                    userId = userId,
                    targetContentId = contentId,
                    targetContentType = contentType,
                    createdAt = System.currentTimeMillis()
                )
                activeLikes[relationshipKey] = record
            }
            Result.success(true)
        } else {
            activeLikes.remove(relationshipKey)
            Result.success(false)
        }
    }

    override suspend fun checkLikeStatus(userId: String, contentId: String): Result<Boolean> {
        if (userId.isBlank() || contentId.isBlank()) {
            return Result.success(false)
        }
        val isLiked = activeLikes.values.any { it.userId == userId && it.targetContentId == contentId }
        return Result.success(isLiked)
    }

    override suspend fun getLikesCount(contentId: String, contentType: TargetContentType): Result<Long> {
        if (contentId.isBlank()) return Result.success(0L)
        val count = activeLikes.values.count { it.targetContentId == contentId && it.targetContentType == contentType }.toLong()
        return Result.success(count)
    }

    override suspend fun getUserLikedContentIds(userId: String, contentType: TargetContentType): Result<List<String>> {
        if (userId.isBlank()) return Result.success(emptyList())
        val ids = activeLikes.values
            .filter { it.userId == userId && it.targetContentType == contentType }
            .map { it.targetContentId }
            .distinct()
        return Result.success(ids)
    }

    override suspend fun getComments(contentId: String, page: Int, pageSize: Int): Result<List<CommentRecord>> {
        if (contentId.isBlank()) {
            return Result.failure(IllegalArgumentException("Content ID cannot be empty."))
        }
        val allComments = activeComments[contentId] ?: return Result.success(emptyList())
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, 50)
        val startIndex = (safePage - 1) * safePageSize
        if (startIndex >= allComments.size) {
            return Result.success(emptyList())
        }
        val endIndex = minOf(startIndex + safePageSize, allComments.size)
        return Result.success(allComments.subList(startIndex, endIndex).toList())
    }

    override suspend fun postComment(
        authorId: String,
        contentId: String,
        contentType: TargetContentType,
        text: String,
        user: OjasUser?
    ): Result<CommentRecord> = commentMutex.withLock {
        if (authorId.isBlank()) {
            return Result.failure(IllegalStateException("Authentication required to post a comment."))
        }
        if (contentId.isBlank()) {
            return Result.failure(IllegalArgumentException("Target content ID cannot be empty."))
        }
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) {
            return Result.failure(IllegalArgumentException("Comment text cannot be empty or whitespace only."))
        }

        val commentId = "cmt_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val authorUsername = user?.username?.takeIf { it.isNotBlank() } ?: "user_${authorId.take(6)}"
        val authorDisplayName = user?.displayName?.takeIf { it.isNotBlank() } ?: "OJAS User"

        val newComment = CommentRecord(
            commentId = commentId,
            targetContentId = contentId,
            targetContentType = contentType,
            authorId = authorId,
            authorUsername = authorUsername,
            authorDisplayName = authorDisplayName,
            authorAvatarUrl = user?.avatarUrl,
            text = trimmedText,
            createdAt = System.currentTimeMillis(),
            likeCount = 0L,
            isLikedByMe = false
        )

        val list = activeComments.getOrPut(contentId) { CopyOnWriteArrayList() }
        // Insert newest comment at the top for real-time responsiveness
        list.add(0, newComment)
        Result.success(newComment)
    }

    override suspend fun deleteComment(userId: String, commentId: String): Result<Boolean> = commentMutex.withLock {
        if (userId.isBlank() || commentId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID and Comment ID required for deletion."))
        }

        for ((_, list) in activeComments) {
            val found = list.find { it.commentId == commentId }
            if (found != null) {
                if (found.authorId != userId) {
                    return Result.failure(IllegalAccessException("Unauthorized: Users can only delete their own comments."))
                }
                list.remove(found)
                return Result.success(true)
            }
        }
        Result.failure(NoSuchElementException("Comment not found with ID: $commentId"))
    }

    override suspend fun getCommentsCount(contentId: String, contentType: TargetContentType): Result<Long> {
        if (contentId.isBlank()) return Result.success(0L)
        val count = activeComments[contentId]?.size?.toLong() ?: 0L
        return Result.success(count)
    }

    override suspend fun setFollow(
        followerId: String,
        followedId: String,
        isFollowing: Boolean
    ): Result<Boolean> = followMutex.withLock {
        if (followerId.isBlank()) {
            return Result.failure(IllegalStateException("Authentication required to follow users."))
        }
        if (followedId.isBlank()) {
            return Result.failure(IllegalArgumentException("Target user ID cannot be empty."))
        }
        if (followerId == followedId) {
            return Result.failure(IllegalArgumentException("Users cannot follow themselves."))
        }

        val relationshipKey = "$followerId:$followedId"

        return if (isFollowing) {
            // Check if duplicate relationship already exists
            if (!activeFollows.containsKey(relationshipKey)) {
                val record = FollowRecord(
                    followId = "follow_${System.currentTimeMillis()}_${followerId.take(4)}",
                    followerId = followerId,
                    followedId = followedId,
                    createdAt = System.currentTimeMillis()
                )
                activeFollows[relationshipKey] = record
            }
            Result.success(true)
        } else {
            // Remove relationship
            activeFollows.remove(relationshipKey)
            Result.success(false)
        }
    }

    override suspend fun checkFollowStatus(followerId: String, followedId: String): Result<Boolean> {
        if (followerId.isBlank() || followedId.isBlank() || followerId == followedId) {
            return Result.success(false)
        }
        val relationshipKey = "$followerId:$followedId"
        return Result.success(activeFollows.containsKey(relationshipKey))
    }

    override suspend fun getFollowedUserIds(followerId: String): Result<List<String>> {
        if (followerId.isBlank()) return Result.success(emptyList())
        val ids = activeFollows.values
            .filter { it.followerId == followerId }
            .map { it.followedId }
            .distinct()
        return Result.success(ids)
    }

    override suspend fun getFollowingCount(userId: String): Result<Int> {
        if (userId.isBlank()) return Result.success(0)
        val count = activeFollows.values.count { it.followerId == userId }
        return Result.success(count)
    }

    override suspend fun getFollowersCount(userId: String): Result<Int> {
        if (userId.isBlank()) return Result.success(0)
        val count = activeFollows.values.count { it.followedId == userId }
        return Result.success(count)
    }
}
