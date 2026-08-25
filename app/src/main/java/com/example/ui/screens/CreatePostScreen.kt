package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ContentVisibility
import com.example.data.model.MediaAttachment
import com.example.data.model.MediaType
import com.example.data.model.OjasUser
import com.example.data.model.Post
import com.example.data.model.PostDraft
import com.example.data.repository.MediaStorageService
import com.example.data.repository.PostRepository
import com.example.ui.theme.OjasRoyalBlue
import com.example.ui.theme.OjasSlate100
import com.example.ui.theme.OjasSlate200
import com.example.ui.theme.OjasSlate400
import com.example.ui.theme.OjasSlate500
import com.example.ui.theme.OjasSlate800
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Validated local media item representation.
 */
data class SelectedMediaItem(
    val uri: Uri,
    val mimeType: String,
    val mediaType: MediaType,
    val fileName: String,
    val sizeBytes: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    currentUser: OjasUser?,
    postRepository: PostRepository,
    mediaStorageService: MediaStorageService,
    onDismiss: () -> Unit,
    onPublishSuccess: (Post) -> Unit,
    onActionNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var captionText by remember { mutableStateOf("") }
    var selectedMedia by remember { mutableStateOf<SelectedMediaItem?>(null) }
    var isPublishing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // System Media Picker for Photos and Videos (minimum permission required)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            errorMessage = null
            val validatedItem = validateAndExtractMedia(context, uri)
            if (validatedItem != null) {
                selectedMedia = validatedItem
            } else {
                errorMessage = "Unsupported media file or unable to read selected file."
            }
        }
    }

    val hasContent = captionText.trim().isNotBlank() || selectedMedia != null

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
        if (!hasContent) {
            errorMessage = "Please enter a caption or attach a photo/video."
            return
        }

        errorMessage = null
        isPublishing = true

        coroutineScope.launch {
            try {
                val mediaAttachments = mutableListOf<MediaAttachment>()

                // 1. Upload media if present
                val localMedia = selectedMedia
                if (localMedia != null) {
                    val mediaBytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(localMedia.uri)?.use { stream ->
                            stream.readBytes()
                        }
                    }

                    if (mediaBytes == null || mediaBytes.isEmpty()) {
                        isPublishing = false
                        errorMessage = "Failed to read selected media file."
                        return@launch
                    }

                    val uploadResult = mediaStorageService.uploadMedia(
                        userId = currentUser.userId,
                        mediaBytes = mediaBytes,
                        mimeType = localMedia.mimeType,
                        destinationFolder = "posts",
                        fileName = localMedia.fileName
                    )

                    if (uploadResult.isFailure) {
                        isPublishing = false
                        errorMessage = uploadResult.exceptionOrNull()?.message ?: "Media upload failed."
                        return@launch
                    }

                    val uploadMetadata = uploadResult.getOrThrow()
                    mediaAttachments.add(
                        MediaAttachment(
                            mediaId = uploadMetadata.mediaId,
                            mediaUrl = localMedia.uri.toString(), // Keep accessible local preview URI alongside metadata
                            mediaType = localMedia.mediaType,
                            thumbnailUrl = if (localMedia.mediaType == MediaType.IMAGE) localMedia.uri.toString() else null
                        )
                    )
                }

                // 2. Create and commit Post record
                val draft = PostDraft(
                    textContent = captionText.trim(),
                    mediaAttachments = mediaAttachments,
                    visibility = ContentVisibility.PUBLIC
                )

                val postResult = postRepository.createPost(
                    creatorId = currentUser.userId,
                    draft = draft,
                    user = currentUser
                )

                isPublishing = false

                if (postResult.isSuccess) {
                    val createdPost = postResult.getOrThrow()
                    onActionNotice("Post published successfully")
                    onPublishSuccess(createdPost)
                } else {
                    errorMessage = postResult.exceptionOrNull()?.message ?: "Failed to publish post."
                }
            } catch (e: Exception) {
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
                        text = "New Post",
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
                        modifier = Modifier.testTag("create_post_back_button")
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
                        enabled = hasContent && !isPublishing,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OjasRoyalBlue,
                            contentColor = Color.White,
                            disabledContainerColor = OjasRoyalBlue.copy(alpha = 0.3f),
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("publish_post_button")
                    ) {
                        if (isPublishing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Publishing...",
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
            // Error notice banner if an error occurred
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

            // User attribution header
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
                        text = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "OJAS User",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
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
                            text = "Public",
                            style = MaterialTheme.typography.labelSmall,
                            color = OjasSlate500
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Caption Text Input
            OutlinedTextField(
                value = captionText,
                onValueChange = {
                    captionText = it
                    errorMessage = null
                },
                placeholder = {
                    Text(
                        text = "What's happening? Share a thought or update...",
                        color = OjasSlate400,
                        fontSize = 15.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .testTag("post_caption_input"),
                enabled = !isPublishing,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Selected Media Preview or Media Picker Trigger
            val media = selectedMedia
            if (media != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OjasSlate100)
                        .border(1.dp, OjasSlate200, RoundedCornerShape(16.dp))
                ) {
                    if (media.mediaType == MediaType.IMAGE) {
                        AsyncImage(
                            model = media.uri,
                            contentDescription = "Selected image preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        // Video preview placeholder card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VideoLibrary,
                                contentDescription = "Video attached",
                                tint = OjasRoyalBlue,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = media.fileName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = OjasSlate800
                            )
                            Text(
                                text = "${media.sizeBytes / 1024} KB • Video",
                                style = MaterialTheme.typography.bodySmall,
                                color = OjasSlate500
                            )
                        }
                    }

                    // Remove Media Button
                    IconButton(
                        onClick = {
                            if (!isPublishing) selectedMedia = null
                        },
                        enabled = !isPublishing,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .testTag("remove_media_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Remove media",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                // Add Media Trigger Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !isPublishing) {
                            mediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                        .testTag("add_media_button"),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(OjasRoyalBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = "Add Media",
                                tint = OjasRoyalBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Add Photo or Video",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Select from your device storage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Discard Confirmation Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(text = "Discard Post?") },
            text = { Text(text = "You have unsaved changes. Are you sure you want to discard this post?") },
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
 * Validates selected media URI safely without crashing.
 */
private fun validateAndExtractMedia(context: Context, uri: Uri): SelectedMediaItem? {
    return try {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val mediaType = if (mimeType.startsWith("video/")) MediaType.VIDEO else MediaType.IMAGE

        var fileName = "media_file"
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
        if (sizeBytes > 50L * 1024 * 1024) {
            return null
        }

        SelectedMediaItem(
            uri = uri,
            mimeType = mimeType,
            mediaType = mediaType,
            fileName = fileName,
            sizeBytes = sizeBytes
        )
    } catch (_: Exception) {
        null
    }
}
