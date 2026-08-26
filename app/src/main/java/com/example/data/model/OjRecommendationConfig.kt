package com.example.data.model

/**
 * Centrally configurable ranking weights and diversity parameters for OJ "For You" recommendation feed.
 * Allows rule adjustments and future ML signal calibration without refactoring UI layers.
 */
object OjRecommendationConfig {

    /**
     * Boost applied when a video matches an explicitly selected user category/tag filter.
     * High magnitude ensures explicit user intent immediately shapes the feed.
     */
    const val EXPLICIT_FILTER_BOOST: Float = 1000.0f

    /**
     * Boost applied when the video creator is followed by the viewer.
     */
    const val FOLLOWED_CREATOR_BOOST: Float = 25.0f

    /**
     * Boost applied when the video category matches categories of videos previously liked by the viewer.
     */
    const val LIKED_CATEGORY_BOOST: Float = 20.0f

    /**
     * Boost applied when the video creator matches creators of videos previously liked by the viewer.
     */
    const val LIKED_CREATOR_BOOST: Float = 15.0f

    /**
     * Boost applied when the video category matches categories of videos with qualified watch history.
     */
    const val WATCHED_CATEGORY_BOOST: Float = 15.0f

    /**
     * Maximum boost granted for content freshness (decays smoothly over 7 days).
     */
    const val FRESHNESS_MAX_BOOST: Float = 10.0f

    /**
     * Decay half-life for freshness in hours (48 hours).
     */
    const val FRESHNESS_HALF_LIFE_HOURS: Float = 48.0f

    /**
     * Maximum boost granted for genuine aggregate engagement (likes, views, comments).
     */
    const val ENGAGEMENT_MAX_BOOST: Float = 8.0f

    /**
     * Penalty applied to consecutive candidate items from the same creator during feed interleaving.
     */
    const val CONSECUTIVE_CREATOR_PENALTY: Float = 30.0f

    /**
     * Penalty applied when 2 or more consecutive items share the same category.
     */
    const val CONSECUTIVE_CATEGORY_PENALTY: Float = 12.0f

    /**
     * Calculate freshness score from published timestamp.
     */
    fun calculateFreshnessScore(createdAt: Long, currentTimestamp: Long = System.currentTimeMillis()): Float {
        if (createdAt <= 0) return 0f
        val ageHours = ((currentTimestamp - createdAt).coerceAtLeast(0L) / (1000.0f * 3600.0f))
        val decayFactor = Math.pow(0.5, (ageHours / FRESHNESS_HALF_LIFE_HOURS).toDouble()).toFloat()
        return FRESHNESS_MAX_BOOST * decayFactor
    }

    /**
     * Calculate real engagement baseline score from genuine likes, views, comments.
     */
    fun calculateEngagementScore(likeCount: Long, commentCount: Long, viewCount: Long): Float {
        val totalInteractions = (likeCount * 2.0f) + (commentCount * 3.0f)
        val safeViews = viewCount.coerceAtLeast(1L).toFloat()
        val engagementRatio = (totalInteractions / safeViews).coerceIn(0.0f, 1.0f)
        return ENGAGEMENT_MAX_BOOST * engagementRatio
    }
}
