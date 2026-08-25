package com.example.data.repository

import com.example.data.model.ContentVisibility
import com.example.data.model.OjRecommendationConfig
import com.example.data.model.OjVideo
import com.example.data.model.TargetContentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository interface for OJ "For You" recommendation and ranking.
 * Supports logged-out discovery, logged-in personalization, diversity interleaving, and stable pagination.
 */
interface OjRecommendationRepository {
    suspend fun getRecommendedForYouFeed(
        userId: String?,
        sessionId: String,
        categoryFilter: String? = null,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<List<OjVideo>>
}

/**
 * Production-ready, transparent rule-based implementation of [OjRecommendationRepository].
 * Uses genuine signals (explicit filters, creator affinity, category interest from likes & watches, freshness, engagement ratio),
 * enforces creator and category diversity, and provides stable cold-start fallback.
 */
class OjasOjRecommendationRepository(
    private val ojRepository: OjRepository,
    private val socialInteractionRepository: SocialInteractionRepository? = null,
    private val watchAnalyticsRepository: OjWatchAnalyticsRepository? = null
) : OjRecommendationRepository {

    private val mutex = Mutex()

    override suspend fun getRecommendedForYouFeed(
        userId: String?,
        sessionId: String,
        categoryFilter: String?,
        page: Int,
        pageSize: Int
    ): Result<List<OjVideo>> {
        val cleanCat = categoryFilter?.trim()?.takeIf { it.isNotBlank() && !it.equals("all", ignoreCase = true) }
        val cleanUserId = userId?.trim()?.takeIf { it.isNotBlank() }
        val cleanSessionId = sessionId.trim()

        // 1. Fetch eligible public videos from core repository
        val publicResult = ojRepository.getPublicOjVideos(category = null, page = 1, pageSize = 500)
        if (publicResult.isFailure) {
            return publicResult
        }

        val allPublicVideos = publicResult.getOrDefault(emptyList()).filter { video ->
            video.visibility == ContentVisibility.PUBLIC
        }

        if (allPublicVideos.isEmpty()) {
            return Result.success(emptyList())
        }

        return mutex.withLock {
            // 2. Collect real viewer signals
            val followedCreatorIds = if (cleanUserId != null && socialInteractionRepository != null) {
                socialInteractionRepository.getFollowedUserIds(cleanUserId).getOrDefault(emptyList()).toSet()
            } else {
                emptySet()
            }

            val likedVideoIds = if (cleanUserId != null && socialInteractionRepository != null) {
                socialInteractionRepository.getUserLikedContentIds(cleanUserId, TargetContentType.OJ).getOrDefault(emptyList()).toSet()
            } else {
                emptySet()
            }

            // Extract preferred categories & creators from liked videos
            val likedCategories = mutableSetOf<String>()
            val likedCreators = mutableSetOf<String>()
            if (likedVideoIds.isNotEmpty()) {
                allPublicVideos.forEach { video ->
                    if (video.ojId in likedVideoIds) {
                        likedCategories.add(video.category.lowercase())
                        video.tags.forEach { likedCategories.add(it.lowercase()) }
                        likedCreators.add(video.creatorId)
                    }
                }
            }

            // Extract preferred categories from valid watch events
            val watchedCategories = mutableSetOf<String>()
            if (watchAnalyticsRepository != null) {
                val watchEvents = watchAnalyticsRepository.getQualifiedWatchEvents(cleanUserId, cleanSessionId)
                val watchedOjIds = watchEvents.map { it.ojId }.toSet()
                allPublicVideos.forEach { video ->
                    if (video.ojId in watchedOjIds) {
                        watchedCategories.add(video.category.lowercase())
                        video.tags.forEach { watchedCategories.add(it.lowercase()) }
                    }
                }
            }

            // 3. Compute base score for each eligible candidate
            val scoredCandidates = allPublicVideos.map { video ->
                var score = 0.0f

                // Explicit filter matching
                val isExplicitFilterMatch = cleanCat != null && (
                    video.category.equals(cleanCat, ignoreCase = true) ||
                        video.tags.any { it.equals(cleanCat, ignoreCase = true) }
                    )
                if (isExplicitFilterMatch) {
                    score += OjRecommendationConfig.EXPLICIT_FILTER_BOOST
                } else if (cleanCat != null) {
                    // When an explicit filter is active, non-matching items are heavily demoted
                    score -= 500.0f
                }

                // Followed creator affinity
                if (video.creatorId in followedCreatorIds) {
                    score += OjRecommendationConfig.FOLLOWED_CREATOR_BOOST
                }

                // Liked creator affinity
                if (video.creatorId in likedCreators) {
                    score += OjRecommendationConfig.LIKED_CREATOR_BOOST
                }

                // Liked category affinity
                if (video.category.lowercase() in likedCategories || video.tags.any { it.lowercase() in likedCategories }) {
                    score += OjRecommendationConfig.LIKED_CATEGORY_BOOST
                }

                // Watched category affinity
                if (video.category.lowercase() in watchedCategories || video.tags.any { it.lowercase() in watchedCategories }) {
                    score += OjRecommendationConfig.WATCHED_CATEGORY_BOOST
                }

                // Freshness signal
                score += OjRecommendationConfig.calculateFreshnessScore(video.createdAt)

                // Real engagement signal
                score += OjRecommendationConfig.calculateEngagementScore(video.likeCount, video.commentCount, video.viewCount)

                VideoScorePair(video, score)
            }

            // 4. Diversification & repetition-controlled sequence generation
            val rankedList = sequenceWithDiversity(scoredCandidates)

            // 5. Stable pagination
            val safePage = page.coerceAtLeast(1)
            val safePageSize = pageSize.coerceIn(1, 50)
            val startIndex = (safePage - 1) * safePageSize

            if (startIndex >= rankedList.size) {
                Result.success(emptyList())
            } else {
                val endIndex = minOf(startIndex + safePageSize, rankedList.size)
                Result.success(rankedList.subList(startIndex, endIndex))
            }
        }
    }

    /**
     * Interleaves candidates to prevent excessive repetition of the same creator or category
     * while preserving ranking order.
     */
    private fun sequenceWithDiversity(candidates: List<VideoScorePair>): List<OjVideo> {
        val remaining = candidates.sortedByDescending { it.score }.toMutableList()
        val result = mutableListOf<OjVideo>()

        var lastCreatorId: String? = null
        var lastCategory: String? = null
        var consecutiveCategoryCount = 0

        while (remaining.isNotEmpty()) {
            // Find the best candidate that doesn't violate consecutive creator rule
            var chosenIndex = -1
            var bestAdjustedScore = Float.NEGATIVE_INFINITY

            for (i in remaining.indices) {
                val item = remaining[i]
                var adjustedScore = item.score

                if (lastCreatorId != null && item.video.creatorId == lastCreatorId) {
                    adjustedScore -= OjRecommendationConfig.CONSECUTIVE_CREATOR_PENALTY
                }

                if (lastCategory != null && item.video.category.equals(lastCategory, ignoreCase = true) && consecutiveCategoryCount >= 2) {
                    adjustedScore -= OjRecommendationConfig.CONSECUTIVE_CATEGORY_PENALTY
                }

                if (adjustedScore > bestAdjustedScore) {
                    bestAdjustedScore = adjustedScore
                    chosenIndex = i
                }
            }

            if (chosenIndex < 0) {
                chosenIndex = 0
            }

            val chosen = remaining.removeAt(chosenIndex).video
            result.add(chosen)

            if (lastCategory != null && chosen.category.equals(lastCategory, ignoreCase = true)) {
                consecutiveCategoryCount++
            } else {
                consecutiveCategoryCount = 1
                lastCategory = chosen.category
            }
            lastCreatorId = chosen.creatorId
        }

        return result
    }

    private data class VideoScorePair(
        val video: OjVideo,
        val score: Float
    )
}
