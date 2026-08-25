package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ContentVisibility
import com.example.data.model.OjAudioTrack
import com.example.data.model.OjVideo
import com.example.data.model.OjVideoDraft
import com.example.data.model.OjasUser
import com.example.data.repository.MediaStorageService
import com.example.data.repository.OjRepository
import com.example.ui.theme.OjasGoldenYellow
import com.example.ui.theme.OjasRoyalBlue
import com.example.ui.theme.OjasSlate100
import com.example.ui.theme.OjasSlate200
import com.example.ui.theme.OjasSlate400
import com.example.ui.theme.OjasSlate500
import com.example.ui.theme.OjasSlate800
import com.example.ui.theme.OjasVibrantOrange
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Validated local OJ video representation.
 */
data class SelectedOjVideo(
    val uri: Uri,
    val mimeType: String,
    val fileName: String,
    val sizeBytes: Long,
    val durationSeconds: Int,
    val width: Int,
    val height: Int,
    val isPreferredVertical: Boolean,
    val thumbnailBitmap: Bitmap? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOjScreen(
    currentUser: OjasUser?,
    ojRepository: OjRepository,
    mediaStorageService: MediaStorageService,
    onDismiss: () -> Unit,
    onPublishSuccess: (OjVideo) -> Unit,
    onActionNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var captionText by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("general") }
    var selectedVideo by remember { mutableStateOf<SelectedOjVideo?>(null) }
    var isPublishing by remember { mutableStateOf(false) }
    var publishingStatusText by remember { mutableStateOf("Publishing...") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val categories = listOf("general", "music", "dance", "comedy", "tech", "lifestyle", "educational")

    // Modern System Video Picker (PickVisualMedia.VideoOnly)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            errorMessage = null
            coroutineScope.launch {
                val validated = withContext(Dispatchers.IO) {
                    validateAndExtractOjVideo(context, uri)
                }
                if (validated != null) {
                    selectedVideo = validated
                } else {
                    errorMessage = "Unsupported video format or unable to read file. Please select a valid video under 50MB."
                }
            }
        }
    }

    val hasContent = selectedVideo != null || captionText.trim().isNotBlank()

    fun handleClose() {
        if (hasContent && !isPublishing) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    fun handlePublish() {
        if (isPublishing) return
        if (currentUser == null || currentUser.userId.isBlank()) {
            errorMessage = "Authentication required to publish."
            return
        }
        val video = selectedVideo
        if (video == null) {
            errorMessage = "Please select a video for your OJ."
            return
        }

        errorMessage = null
        isPublishing = true
        publishingStatusText = "Uploading video..."

        coroutineScope.launch {
            var uploadedMediaId: String? = null
            try {
                // 1. Read video bytes
                val videoBytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(video.uri)?.use { it.readBytes() }
                }

                if (videoBytes == null || videoBytes.isEmpty()) {
                    isPublishing = false
                    errorMessage = "Unable to read selected video file."
                    return@launch
                }

                // 2. Upload video bytes to media storage
                val uploadResult = mediaStorageService.uploadMedia(
                    userId = currentUser.userId,
                    mediaBytes = videoBytes,
                    mimeType = video.mimeType,
                    destinationFolder = "oj_videos",
                    fileName = video.fileName
                )

                if (uploadResult.isFailure) {
                    isPublishing = false
                    errorMessage = uploadResult.exceptionOrNull()?.message ?: "Video upload failed."
                    return@launch
                }

                val mediaMetadata = uploadResult.getOrThrow()
                uploadedMediaId = mediaMetadata.mediaId
                publishingStatusText = "Creating OJ..."

                // 3. Optional thumbnail upload if generated
                var thumbnailStorageUrl: String? = null
                val thumb = video.thumbnailBitmap
                if (thumb != null) {
                    try {
                        val stream = ByteArrayOutputStream()
                        thumb.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                        val thumbBytes = stream.toByteArray()
                        val thumbResult = mediaStorageService.uploadMedia(
                            userId = currentUser.userId,
                            mediaBytes = thumbBytes,
                            mimeType = "image/jpeg",
                            destinationFolder = "oj_thumbnails",
                            fileName = "thumb_${video.fileName}.jpg"
                        )
                        if (thumbResult.isSuccess) {
                            thumbnailStorageUrl = thumbResult.getOrThrow().downloadUrl
                        }
                    } catch (_: Exception) {
                        // Non-fatal if thumbnail upload fails
                    }
                }

                // 4. Parse clean tags
                val parsedTags = tagsText
                    .split(",", " ", "#")
                    .map { it.trim().removePrefix("#") }
                    .filter { it.isNotBlank() }

                // 5. Create OJ Record in repository
                val draft = OjVideoDraft(
                    videoUrl = mediaMetadata.downloadUrl,
                    thumbnailUrl = thumbnailStorageUrl ?: video.uri.toString(),
                    caption = captionText.trim(),
                    tags = parsedTags,
                    category = selectedCategory,
                    audioTrack = OjAudioTrack(
                        audioId = "audio_${mediaMetadata.mediaId}",
                        title = "Original Audio",
                        artistName = currentUser.displayName.takeIf { it.isNotBlank() } ?: "OJAS Creator",
                        isOriginal = true
                    ),
                    durationSeconds = video.durationSeconds,
                    visibility = ContentVisibility.PUBLIC
                )

                val ojResult = ojRepository.createOjVideo(
                    creatorId = currentUser.userId,
                    draft = draft,
                    user = currentUser
                )

                if (ojResult.isSuccess) {
                    val createdOj = ojResult.getOrThrow()
                    isPublishing = false
                    onActionNotice("OJ published successfully")
                    onPublishSuccess(createdOj)
                } else {
                    // Safe cleanup of uploaded media if record creation fails
                    if (uploadedMediaId != null) {
                        mediaStorageService.deleteMedia(currentUser.userId, uploadedMediaId)
                    }
                    isPublishing = false
                    errorMessage = ojResult.exceptionOrNull()?.message ?: "Failed to save OJ video record."
                }
            } catch (e: Exception) {
                if (uploadedMediaId != null) {
                    mediaStorageService.deleteMedia(currentUser.userId, uploadedMediaId)
                }
                isPublishing = false
                errorMessage = e.message ?: "An unexpected error occurred during publishing."
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "New OJ",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { handleClose() },
                        enabled = !isPublishing,
                        modifier = Modifier.testTag("create_oj_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { handlePublish() },
                        enabled = selectedVideo != null && !isPublishing,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OjasVibrantOrange,
                            contentColor = Color.White,
                            disabledContainerColor = OjasVibrantOrange.copy(alpha = 0.3f),
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("publish_oj_button")
                    ) {
                        if (isPublishing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = publishingStatusText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "Publish",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Error banner if any issue occurred
            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { errorMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // User attribution banner
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!currentUser?.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = currentUser?.avatarUrl,
                        contentDescription = "Your profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(OjasSlate200),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "Avatar placeholder",
                            tint = OjasSlate500,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "OJAS Creator",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = null,
                            tint = OjasSlate400,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Public OJ",
                            style = MaterialTheme.typography.labelSmall,
                            color = OjasSlate500
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Video Selection / Preview Section
            val video = selectedVideo
            if (video != null) {
                // Video Preview Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OjasSlate100)
                        .border(1.dp, OjasSlate200, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (video.thumbnailBitmap != null) {
                            Image(
                                bitmap = video.thumbnailBitmap.asImageBitmap(),
                                contentDescription = "Video preview frame",
                                contentScale = if (video.isPreferredVertical) ContentScale.Crop else ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Movie,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = video.fileName,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // Play badge indicator
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Aspect ratio badge
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = if (video.isPreferredVertical) OjasVibrantOrange else Color.Black.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = if (video.isPreferredVertical) "9:16 Vertical" else "${video.width}x${video.height}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Duration badge
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "${video.durationSeconds}s",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Remove video button
                        IconButton(
                            onClick = {
                                if (!isPublishing) selectedVideo = null
                            },
                            enabled = !isPublishing,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .testTag("remove_oj_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Remove video",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = video.fileName,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = OjasSlate800,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${video.sizeBytes / (1024 * 1024)} MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = OjasSlate500
                        )
                    }
                }
            } else {
                // Select Video Trigger Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !isPublishing) {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                        .testTag("select_oj_video_button"),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(OjasVibrantOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VideoLibrary,
                                contentDescription = "Select video",
                                tint = OjasVibrantOrange,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Select a Video",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Choose a 9:16 short video from your device storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Caption / Title Input
            Text(
                text = "Caption",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = captionText,
                onValueChange = {
                    captionText = it
                    errorMessage = null
                },
                placeholder = {
                    Text(
                        text = "Add a title or caption for your OJ video...",
                        color = OjasSlate400,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .testTag("oj_caption_input"),
                enabled = !isPublishing,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OjasRoyalBlue,
                    unfocusedBorderColor = OjasSlate200
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Tags Input
            Text(
                text = "Tags",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                placeholder = {
                    Text(
                        text = "e.g. #dance #comedy #tech (optional)",
                        color = OjasSlate400,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Tag,
                        contentDescription = null,
                        tint = OjasSlate400,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("oj_tags_input"),
                enabled = !isPublishing,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OjasRoyalBlue,
                    unfocusedBorderColor = OjasSlate200
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Category Selection
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = selectedCategory.equals(category, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (!isPublishing) selectedCategory = category
                        },
                        label = {
                            Text(
                                text = category.replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OjasVibrantOrange.copy(alpha = 0.15f),
                            selectedLabelColor = OjasVibrantOrange
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) OjasVibrantOrange else OjasSlate200
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Discard Confirmation Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(text = "Discard OJ Video?") },
            text = { Text(text = "You have selected a video. Are you sure you want to discard and exit?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onDismiss()
                    }
                ) {
                    Text(text = "Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(text = "Keep Editing")
                }
            }
        )
    }
}

/**
 * Validates and extracts video properties safely using Android's MediaMetadataRetriever and ContentResolver.
 */
private fun validateAndExtractOjVideo(context: Context, uri: Uri): SelectedOjVideo? {
    return try {
        val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
        var fileName = "oj_video_${System.currentTimeMillis()}.mp4"
        var sizeBytes = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
                if (sizeIndex != -1) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }

        // Limit to 50MB
        val maxSizeBytes = 50L * 1024 * 1024
        if (sizeBytes > maxSizeBytes) {
            return null
        }

        var durationSec = 15
        var videoWidth = 720
        var videoHeight = 1280
        var thumbBitmap: Bitmap? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!durationStr.isNullOrBlank()) {
                val durationMs = durationStr.toLongOrNull() ?: 15000L
                durationSec = (durationMs / 1000L).toInt().coerceIn(1, 180)
            }

            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val rotation = rotationStr?.toIntOrNull() ?: 0

            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280

            if (rotation == 90 || rotation == 270) {
                videoWidth = rawHeight
                videoHeight = rawWidth
            } else {
                videoWidth = rawWidth
                videoHeight = rawHeight
            }

            // Extract lightweight frame at time 0
            thumbBitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            // Fallback gracefully if retriever metadata is unavailable
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        val isPreferredVertical = videoHeight > videoWidth

        SelectedOjVideo(
            uri = uri,
            mimeType = mimeType,
            fileName = fileName,
            sizeBytes = sizeBytes,
            durationSeconds = durationSec,
            width = videoWidth,
            height = videoHeight,
            isPreferredVertical = isPreferredVertical,
            thumbnailBitmap = thumbBitmap
        )
    } catch (_: Exception) {
        null
    }
}
