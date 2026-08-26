package com.example.data.model

import java.util.UUID

/**
 * Centrally configurable threshold definitions for OJ video watch analytics and view qualification.
 */
object OjWatchAnalyticsConfig {

    /**
     * Absolute minimum continuous playback duration required to qualify as a valid view (in milliseconds).
     */
    const val MIN_QUALIFYING_WATCH_MS: Long = 3000L // 3.0 seconds

    /**
     * Minimum percentage of total video duration required for shorter videos (e.g. 25% of duration).
     */
    const val MIN_QUALIFYING_PERCENTAGE: Float = 0.25f

    /**
     * Absolute minimum floor threshold for any video, regardless of percentage calculation (in milliseconds).
     */
    const val MIN_ABSOLUTE_FLOOR_MS: Long = 2000L // 2.0 seconds

    /**
     * Polling cadence for active player progress verification (in milliseconds).
     */
    const val PROGRESS_POLL_INTERVAL_MS: Long = 250L

    /**
     * Evaluates if accumulated active playback time satisfies the view qualification rule.
     * Hybrid rule: Qualified if active playback >= 3 seconds OR 25% of video duration, subject to a 2.0s floor.
     */
    fun isQualifiedView(accumulatedActivePlaybackMs: Long, totalDurationMs: Long): Boolean {
        if (accumulatedActivePlaybackMs < MIN_ABSOLUTE_FLOOR_MS) return false
        val percentageThreshold = if (totalDurationMs > 0) {
            (totalDurationMs * MIN_QUALIFYING_PERCENTAGE).toLong()
        } else {
            MIN_QUALIFYING_WATCH_MS
        }
        val targetThreshold = minOf(MIN_QUALIFYING_WATCH_MS, percentageThreshold).coerceAtLeast(MIN_ABSOLUTE_FLOOR_MS)
        return accumulatedActivePlaybackMs >= targetThreshold
    }
}

/**
 * Data model for a qualified OJ view event.
 * Contains idempotency key, target OJ ID, privacy-safe session ID, and playback metrics.
 */
data class OjViewEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val ojId: String,
    val viewerId: String? = null,
    val sessionId: String,
    val watchedDurationMs: Long,
    val totalDurationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
