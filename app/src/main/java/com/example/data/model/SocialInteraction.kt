package com.example.data.model

/**
 * Supported content types for social interactions (Likes, Comments, Bookmarks).
 */
enum class TargetContentType {
    POST,
    OJ
}

/**
 * Like relationship model foundation.
 * Maps a single user to a single content entity with uniqueness prevention.
 */
data class LikeRecord(
    val likeId: String,
    val userId: String,
    val targetContentId: String,
    val targetContentType: TargetContentType,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Comment model foundation.
 * Represents a user comment on a post or OJ video.
 */
data class CommentRecord(
    val commentId: String,
    val targetContentId: String,
    val targetContentType: TargetContentType,
    val authorId: String,
    val authorUsername: String,
    val authorDisplayName: String,
    val authorAvatarUrl: String? = null,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val likeCount: Long = 0,
    val isLikedByMe: Boolean = false
)

/**
 * Follow relationship model foundation.
 * Maps a follower user to a followed user.
 */
data class FollowRecord(
    val followId: String,
    val followerId: String,
    val followedId: String,
    val createdAt: Long = System.currentTimeMillis()
)
