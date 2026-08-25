package com.example.data.model

/**
 * Visibility state for posts and social content.
 */
enum class ContentVisibility {
    PUBLIC,
    FRIENDS_ONLY,
    PRIVATE
}

/**
 * Media attachment types for posts.
 */
enum class MediaType {
    IMAGE,
    VIDEO,
    AUDIO,
    NONE
}

/**
 * Media attachment reference stored with content.
 */
data class MediaAttachment(
    val mediaId: String,
    val mediaUrl: String,
    val mediaType: MediaType,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null
)

/**
 * Post model foundation for OJAS.
 * Supports text, media, creator attribution, visibility, and engagement references.
 */
data class Post(
    val postId: String,
    val creatorId: String,
    val creatorUsername: String,
    val creatorDisplayName: String,
    val creatorAvatarUrl: String? = null,
    val textContent: String = "",
    val mediaAttachments: List<MediaAttachment> = emptyList(),
    val visibility: ContentVisibility = ContentVisibility.PUBLIC,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val isLikedByMe: Boolean = false
)

/**
 * Draft payload for creating a new Post.
 */
data class PostDraft(
    val textContent: String,
    val mediaAttachments: List<MediaAttachment> = emptyList(),
    val visibility: ContentVisibility = ContentVisibility.PUBLIC
)
