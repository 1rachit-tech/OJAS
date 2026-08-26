package com.example.data.model

/**
 * Media metadata container for media storage uploads and references.
 */
data class MediaUploadMetadata(
    val mediaId: String,
    val storagePath: String,
    val downloadUrl: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val uploadTimestamp: Long = System.currentTimeMillis()
)
