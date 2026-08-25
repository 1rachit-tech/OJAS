package com.example.data.repository

import com.example.data.model.OjViewEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Interface for OJ view counting, playback analytics aggregation, and session-aware deduplication.
 */
interface OjWatchAnalyticsRepository {
    suspend fun recordQualifiedView(event: OjViewEvent): Result<Boolean>
    suspend fun getViewCount(ojId: String): Result<Long>
    suspend fun hasSessionViewed(sessionId: String, ojId: String): Boolean
    suspend fun getQualifiedWatchEvents(userId: String?, sessionId: String?): List<OjViewEvent>
    fun getSessionId(): String
}

/**
 * Production-ready, thread-safe implementation of [OjWatchAnalyticsRepository].
 * Enforces session-scoped view deduplication, idempotency against retries and video loops,
 * and maintains accurate backend-synchronized view tallies without collecting device-identifying data.
 */
class OjasOjWatchAnalyticsRepository(
    private val ojRepository: OjRepository? = null
) : OjWatchAnalyticsRepository {

    // Privacy-conscious, rotating ephemeral session ID per app run
    private val currentSessionId = "sess_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    // Deduplication collections
    private val processedEventIds = ConcurrentHashMap.newKeySet<String>()
    private val sessionViewedPairs = ConcurrentHashMap.newKeySet<String>()
    private val ojViewCounts = ConcurrentHashMap<String, Long>()
    private val eventHistory = CopyOnWriteArrayList<OjViewEvent>()
    private val mutex = Mutex()

    override fun getSessionId(): String = currentSessionId

    override suspend fun recordQualifiedView(event: OjViewEvent): Result<Boolean> {
        if (event.ojId.isBlank()) {
            return Result.failure(IllegalArgumentException("Target OJ ID cannot be blank."))
        }

        // 1. Idempotency Check: eventId already processed
        if (processedEventIds.contains(event.eventId)) {
            return Result.success(true)
        }

        val sessionKey = "${event.sessionId}:${event.ojId}"

        // 2. Session deduplication: One view per valid watch session
        if (sessionViewedPairs.contains(sessionKey)) {
            processedEventIds.add(event.eventId)
            return Result.success(true)
        }

        return mutex.withLock {
            // Re-check after lock
            if (sessionViewedPairs.contains(sessionKey)) {
                processedEventIds.add(event.eventId)
                return@withLock Result.success(true)
            }

            processedEventIds.add(event.eventId)
            sessionViewedPairs.add(sessionKey)
            eventHistory.add(event)

            val newCount = (ojViewCounts[event.ojId] ?: 0L) + 1L
            ojViewCounts[event.ojId] = newCount

            // Synchronize with core OJ repository
            ojRepository?.syncViewCount(event.ojId, newCount)

            Result.success(true)
        }
    }

    override suspend fun getViewCount(ojId: String): Result<Long> {
        if (ojId.isBlank()) {
            return Result.failure(IllegalArgumentException("Target OJ ID cannot be blank."))
        }
        val count = ojViewCounts[ojId] ?: 0L
        return Result.success(count)
    }

    override suspend fun hasSessionViewed(sessionId: String, ojId: String): Boolean {
        if (sessionId.isBlank() || ojId.isBlank()) return false
        return sessionViewedPairs.contains("$sessionId:$ojId")
    }

    override suspend fun getQualifiedWatchEvents(userId: String?, sessionId: String?): List<OjViewEvent> {
        val cleanUserId = userId?.trim()?.takeIf { it.isNotBlank() }
        val cleanSessionId = sessionId?.trim()?.takeIf { it.isNotBlank() }
        if (cleanUserId == null && cleanSessionId == null) {
            return emptyList()
        }
        return eventHistory.filter { event ->
            (cleanUserId != null && event.viewerId == cleanUserId) ||
                (cleanSessionId != null && event.sessionId == cleanSessionId)
        }
    }
}
