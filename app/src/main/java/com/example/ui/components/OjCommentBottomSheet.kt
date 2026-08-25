package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CommentRecord
import com.example.data.model.OjVideo
import com.example.data.model.OjasUser
import com.example.data.model.TargetContentType
import com.example.data.repository.SocialInteractionRepository
import com.example.ui.theme.OjasRoyalBlue
import kotlinx.coroutines.launch

/**
 * Compact, video-focused bottom sheet for reading, writing, and deleting real comments on an OJ.
 * Preserves the clean OJ aesthetic while providing responsive, thread-safe interaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OjCommentBottomSheet(
    ojVideo: OjVideo,
    currentUserId: String?,
    currentUser: OjasUser?,
    socialInteractionRepository: SocialInteractionRepository?,
    onDismissRequest: () -> Unit,
    onRequireAuth: (((() -> Unit) -> Unit))? = null,
    onCommentCountChanged: (Long) -> Unit,
    onActionNotice: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    val commentsList = remember(ojVideo.ojId) { mutableStateListOf<CommentRecord>() }
    var isLoadingComments by remember(ojVideo.ojId) { mutableStateOf(true) }
    var commentsErrorMessage by remember(ojVideo.ojId) { mutableStateOf<String?>(null) }
    var currentPage by remember(ojVideo.ojId) { mutableIntStateOf(1) }
    var hasMoreComments by remember(ojVideo.ojId) { mutableStateOf(false) }
    var isLoadingMore by remember(ojVideo.ojId) { mutableStateOf(false) }

    var inputText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var deletingCommentId by remember { mutableStateOf<String?>(null) }

    // Load initial comments page and verified count for the active OJ
    fun loadInitialComments() {
        if (socialInteractionRepository == null) {
            isLoadingComments = false
            return
        }
        coroutineScope.launch {
            isLoadingComments = true
            commentsErrorMessage = null
            currentPage = 1

            val result = socialInteractionRepository.getComments(ojVideo.ojId, page = 1, pageSize = 20)
            isLoadingComments = false
            if (result.isSuccess) {
                val list = result.getOrDefault(emptyList())
                commentsList.clear()
                commentsList.addAll(list)
                hasMoreComments = list.size >= 20

                val countRes = socialInteractionRepository.getCommentsCount(ojVideo.ojId, TargetContentType.OJ)
                val totalCount = countRes.getOrDefault(list.size.toLong())
                onCommentCountChanged(totalCount)
            } else {
                commentsErrorMessage = result.exceptionOrNull()?.message ?: "Unable to load comments"
            }
        }
    }

    // Load next page of comments
    fun loadNextPage() {
        if (socialInteractionRepository == null || isLoadingMore || !hasMoreComments) return
        coroutineScope.launch {
            isLoadingMore = true
            val nextPage = currentPage + 1
            val result = socialInteractionRepository.getComments(ojVideo.ojId, page = nextPage, pageSize = 20)
            isLoadingMore = false
            if (result.isSuccess) {
                val newItems = result.getOrDefault(emptyList())
                if (newItems.isNotEmpty()) {
                    // Prevent duplicate comments during pagination
                    val existingIds = commentsList.map { it.commentId }.toSet()
                    val distinctNewItems = newItems.filter { it.commentId !in existingIds }
                    commentsList.addAll(distinctNewItems)
                    currentPage = nextPage
                    hasMoreComments = newItems.size >= 20
                } else {
                    hasMoreComments = false
                }
            }
        }
    }

    LaunchedEffect(ojVideo.ojId) {
        loadInitialComments()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.testTag("comments_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 340.dp, max = 560.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. Header Bar: Title + Total Count + Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Comments",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (commentsList.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${commentsList.size})",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("close_comments_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close comments",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )

            // 2. Comments List / Loading / Empty / Error State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isLoadingComments && commentsList.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = OjasRoyalBlue,
                                strokeWidth = 2.5.dp
                            )
                        }
                    }

                    commentsErrorMessage != null && commentsList.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = commentsErrorMessage ?: "Failed to load comments",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { loadInitialComments() },
                                modifier = Modifier.testTag("retry_comments_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = OjasRoyalBlue),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Retry")
                            }
                        }
                    }

                    !isLoadingComments && commentsList.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No comments yet",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Be the first to share your thoughts!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("comments_list"),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(
                                items = commentsList,
                                key = { it.commentId }
                            ) { comment ->
                                OjCommentItem(
                                    comment = comment,
                                    isOwnComment = currentUserId != null && currentUserId == comment.authorId,
                                    isDeleting = deletingCommentId == comment.commentId,
                                    onDeleteClick = {
                                        if (currentUserId == null) return@OjCommentItem
                                        if (deletingCommentId != null) return@OjCommentItem

                                        deletingCommentId = comment.commentId
                                        coroutineScope.launch {
                                            val delRes = socialInteractionRepository?.deleteComment(currentUserId, comment.commentId)
                                            deletingCommentId = null
                                            if (delRes?.isSuccess == true) {
                                                commentsList.remove(comment)
                                                val countRes = socialInteractionRepository.getCommentsCount(ojVideo.ojId, TargetContentType.OJ)
                                                val newCount = countRes.getOrDefault(commentsList.size.toLong())
                                                onCommentCountChanged(newCount)
                                                onActionNotice("Comment deleted")
                                            } else {
                                                val err = delRes?.exceptionOrNull()?.message ?: "Failed to delete comment"
                                                onActionNotice(err)
                                            }
                                        }
                                    }
                                )
                            }

                            if (hasMoreComments) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isLoadingMore) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = OjasRoyalBlue
                                            )
                                        } else {
                                            TextButton(
                                                onClick = { loadNextPage() },
                                                modifier = Modifier.testTag("load_more_comments_button")
                                            ) {
                                                Text(
                                                    text = "Load older comments",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = OjasRoyalBlue
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )

            // 3. Compact Input Bar (Type -> Send with duplicate tap protection)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Real User Avatar or initial placeholder
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(OjasRoyalBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentUser?.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentUser?.avatarUrl,
                            contentDescription = "My avatar",
                            modifier = Modifier.size(34.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val initial = currentUser?.displayName?.firstOrNull()?.uppercase()
                            ?: currentUser?.username?.firstOrNull()?.uppercase()
                            ?: "U"
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = OjasRoyalBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = if (currentUserId.isNullOrBlank()) "Sign in to add a comment..." else "Add a comment...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("comment_input_field"),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OjasRoyalBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    maxLines = 3,
                    singleLine = false
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send Button with duplicate tap guard & Auth Gate integration
                IconButton(
                    onClick = {
                        val textToSubmit = inputText.trim()
                        if (currentUserId.isNullOrBlank()) {
                            // Unauthenticated user attempts to comment -> trigger auth gate
                            if (onRequireAuth != null) {
                                onRequireAuth {
                                    // User completed login; can now submit safely
                                }
                            } else {
                                onActionNotice("Sign in to comment")
                            }
                            return@IconButton
                        }

                        if (textToSubmit.isBlank()) {
                            return@IconButton
                        }

                        if (isSubmitting) return@IconButton
                        isSubmitting = true

                        coroutineScope.launch {
                            val result = socialInteractionRepository?.postComment(
                                authorId = currentUserId,
                                contentId = ojVideo.ojId,
                                contentType = TargetContentType.OJ,
                                text = textToSubmit,
                                user = currentUser
                            )
                            isSubmitting = false

                            if (result?.isSuccess == true) {
                                val created = result.getOrThrow()
                                // Clear input only upon backend confirmation
                                inputText = ""
                                commentsList.add(0, created)
                                val countRes = socialInteractionRepository?.getCommentsCount(ojVideo.ojId, TargetContentType.OJ)
                                val newTotal = countRes?.getOrDefault(commentsList.size.toLong()) ?: commentsList.size.toLong()
                                onCommentCountChanged(newTotal)
                                onActionNotice("Comment posted")
                            } else {
                                // Preserve user text for easy retry
                                val err = result?.exceptionOrNull()?.message ?: "Failed to post comment"
                                onActionNotice(err)
                            }
                        }
                    },
                    enabled = !isSubmitting && (inputText.trim().isNotEmpty() || currentUserId.isNullOrBlank()),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (inputText.trim().isNotEmpty()) OjasRoyalBlue else MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("send_comment_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send comment",
                            tint = if (inputText.trim().isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Comment item row rendering real author details, timestamp, and delete action for owner.
 */
@Composable
private fun OjCommentItem(
    comment: CommentRecord,
    isOwnComment: Boolean,
    isDeleting: Boolean,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("comment_item_${comment.commentId}"),
        verticalAlignment = Alignment.Top
    ) {
        // Author Avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!comment.authorAvatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = comment.authorAvatarUrl,
                    contentDescription = comment.authorDisplayName,
                    modifier = Modifier.size(32.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val letter = comment.authorDisplayName.firstOrNull()?.uppercase()
                    ?: comment.authorUsername.firstOrNull()?.uppercase()
                    ?: "U"
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Author Name, Timestamp, Body text
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.authorDisplayName.ifBlank { "@${comment.authorUsername}" },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "• ${formatRelativeTime(comment.createdAt)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Delete action button exclusively visible for comment author
                if (isOwnComment) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("delete_comment_${comment.commentId}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete comment",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Formats timestamps into scannable relative strings.
 */
private fun formatRelativeTime(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}
