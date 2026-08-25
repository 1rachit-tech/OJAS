package com.example.data.repository

import com.example.data.model.MediaUploadMetadata
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Service interface for media uploads (Profile Photos, Post Images/Videos, OJ Videos).
 */
interface MediaStorageService {
    suspend fun uploadMedia(
        userId: String,
        mediaBytes: ByteArray,
        mimeType: String,
        destinationFolder: String,
        fileName: String? = null
    ): Result<MediaUploadMetadata>

    suspend fun deleteMedia(userId: String, mediaId: String): Result<Boolean>
    suspend fun getMediaMetadata(mediaId: String): Result<MediaUploadMetadata>
}

/**
 * Backend-ready implementation of [MediaStorageService].
 * Manages media metadata, validation, and safe thread-safe storage operations.
 */
class OjasMediaStorageService : MediaStorageService {
    private val storedMedia = ConcurrentHashMap<String, MediaUploadMetadata>()
    private val mutex = Mutex()

    override suspend fun uploadMedia(
        userId: String,
        mediaBytes: ByteArray,
        mimeType: String,
        destinationFolder: String,
        fileName: String?
    ): Result<MediaUploadMetadata> {
        if (userId.isBlank()) {
            return Result.failure(IllegalStateException("Authentication required to upload media."))
        }
        if (mediaBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Media content is empty."))
        }
        // Maximum allowed file size: 50MB
        val maxSizeBytes = 50L * 1024 * 1024
        if (mediaBytes.size > maxSizeBytes) {
            return Result.failure(IllegalArgumentException("Media size exceeds maximum allowable limit of 50MB."))
        }

        val mediaId = "media_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"
        val cleanFolder = destinationFolder.trim().trim('/')
        val metadata = MediaUploadMetadata(
            mediaId = mediaId,
            storagePath = "$cleanFolder/$userId/$mediaId",
            downloadUrl = "ojas://storage/$cleanFolder/$userId/$mediaId",
            mimeType = mimeType,
            fileSizeBytes = mediaBytes.size.toLong(),
            uploadTimestamp = System.currentTimeMillis()
        )

        mutex.withLock {
            storedMedia[mediaId] = metadata
        }

        return Result.success(metadata)
    }

    override suspend fun deleteMedia(userId: String, mediaId: String): Result<Boolean> {
        if (userId.isBlank() || mediaId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID and Media ID required."))
        }
        return mutex.withLock {
            val removed = storedMedia.remove(mediaId)
            Result.success(removed != null)
        }
    }

    override suspend fun getMediaMetadata(mediaId: String): Result<MediaUploadMetadata> {
        if (mediaId.isBlank()) {
            return Result.failure(IllegalArgumentException("Media ID cannot be empty."))
        }
        val metadata = storedMedia[mediaId]
            ?: return Result.failure(NoSuchElementException("Media metadata not found for ID: $mediaId"))
        return Result.success(metadata)
    }
}

