package com.example.data.model

/**
 * Audio track metadata associated with an OJ video.
 */
data class OjAudioTrack(
    val audioId: String,
    val title: String,
    val artistName: String,
    val audioUrl: String? = null,
    val isOriginal: Boolean = true
)

/**
 * OJ short-form vertical video model foundation for OJAS.
 * Supports vertical video reference, caption, tags, audio, creator attribution, and engagement metrics.
 */
data class OjVideo(
    val ojId: String,
    val creatorId: String,
    val creatorUsername: String,
    val creatorDisplayName: String,
    val creatorAvatarUrl: String? = null,
    val videoUrl: String,
    val thumbnailUrl: String? = null,
    val caption: String = "",
    val tags: List<String> = emptyList(),
    val category: String = "general",
    val audioTrack: OjAudioTrack? = null,
    val visibility: ContentVisibility = ContentVisibility.PUBLIC,
    val durationSeconds: Int = 15,
    val createdAt: Long = System.currentTimeMillis(),
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val viewCount: Long = 0,
    val isLikedByMe: Boolean = false,
    val isFollowedByMe: Boolean = false
)

/**
 * Draft payload for creating a new OJ video.
 */
data class OjVideoDraft(
    val videoUrl: String,
    val thumbnailUrl: String? = null,
    val caption: String = "",
    val tags: List<String> = emptyList(),
    val category: String = "general",
    val audioTrack: OjAudioTrack? = null,
    val durationSeconds: Int = 15,
    val visibility: ContentVisibility = ContentVisibility.PUBLIC
)
