package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotInterested
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ContentVisibility
import com.example.data.model.OjVideo
import com.example.data.model.OjViewEvent
import com.example.data.model.OjWatchAnalyticsConfig
import com.example.data.model.OjasUser
import com.example.data.model.TargetContentType
import com.example.data.repository.OjRecommendationRepository
import com.example.data.repository.OjRepository
import com.example.data.repository.OjWatchAnalyticsRepository
import com.example.data.repository.OjasOjRecommendationRepository
import com.example.data.repository.OjasOjRepository
import com.example.data.repository.OjasOjWatchAnalyticsRepository
import com.example.data.repository.SocialInteractionRepository
import com.example.data.util.OjDeepLinkUtil
import com.example.ui.components.OjCommentBottomSheet
import com.example.ui.components.OjVideoPlayer
import com.example.ui.theme.OjasDarkBackground
import com.example.ui.theme.OjasGoldenYellow
import com.example.ui.theme.OjasRoyalBlue
import kotlinx.coroutines.launch

/**
 * Primary Feed Modes for OJ short-form video experience.
 */
enum class OjFeedMode(val title: String) {
    FOR_YOU("For You"),
    FOLLOWING("Following")
}

@Composable
fun OjScreen(
    selectedCategory: String?,
    onFilterClick: () -> Unit,
    onActionNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
    targetOjId: String? = null,
    onTargetOjIdConsumed: (() -> Unit)? = null,
    onRequireAuth: (((() -> Unit) -> Unit))? = null,
    currentUserId: String? = null,
    currentUser: OjasUser? = null,
    ojRepository: OjRepository = remember { OjasOjRepository() },
    socialInteractionRepository: SocialInteractionRepository? = null,
    watchAnalyticsRepository: OjWatchAnalyticsRepository = remember(ojRepository) { OjasOjWatchAnalyticsRepository(ojRepository) },
    recommendationRepository: OjRecommendationRepository = remember(ojRepository, socialInteractionRepository, watchAnalyticsRepository) {
        OjasOjRecommendationRepository(ojRepository, socialInteractionRepository, watchAnalyticsRepository)
    }
) {
    val context = LocalContext.current
    var activeFeedMode by remember { mutableStateOf(OjFeedMode.FOR_YOU) }
    var isUserPaused by remember { mutableStateOf(false) }
    var selectedVideoForMore by remember { mutableStateOf<OjVideo?>(null) }
    var activeCommentVideo by remember { mutableStateOf<OjVideo?>(null) }
    var isCommentsSheetOpen by remember { mutableStateOf(false) }

    // Session-scoped watch tracking state (prevents duplicate counting for loops, re-renders, and rapid swipes)
    val sessionViewedSet = remember { mutableSetOf<String>() }
    val activeWatchAccumulator = remember { mutableMapOf<String, Long>() }

    // Video Feed Data State
    var forYouVideos by remember { mutableStateOf<List<OjVideo>>(emptyList()) }
    var followingVideos by remember { mutableStateOf<List<OjVideo>>(emptyList()) }
    var isLoadingFeed by remember { mutableStateOf(false) }
    var hasFeedError by remember { mutableStateOf(false) }
    var followedCreatorCount by remember { mutableIntStateOf(0) }
    var isFollowingPaginating by remember { mutableStateOf(false) }

    // Pagination State
    var forYouPage by remember { mutableIntStateOf(1) }
    var followingPage by remember { mutableIntStateOf(1) }
    var hasMoreForYou by remember { mutableStateOf(true) }
    var hasMoreFollowing by remember { mutableStateOf(true) }

    // User Interaction Local State (Likes, Comments, and Follows per video)
    val likedVideoMap = remember { mutableStateMapOf<String, Boolean>() }
    val likeCountMap = remember { mutableStateMapOf<String, Long>() }
    val likeInFlightMap = remember { mutableStateMapOf<String, Boolean>() }
    val commentCountMap = remember { mutableStateMapOf<String, Long>() }
    val followStatusMap = remember { mutableStateMapOf<String, Boolean>() }
    val followLoadingMap = remember { mutableStateMapOf<String, Boolean>() }

    val coroutineScope = rememberCoroutineScope()

    // Clear and isolate account-specific Like/Follow states on user switch or logout
    LaunchedEffect(currentUserId) {
        likedVideoMap.clear()
        followStatusMap.clear()
        likeInFlightMap.clear()
        followedCreatorCount = 0
    }

    fun executeWithAuth(prompt: String = "Sign in to continue", action: () -> Unit) {
        if (onRequireAuth != null) {
            onRequireAuth(action)
        } else {
            action()
        }
    }

    // Function to reload the current feed from page 1
    fun loadInitialFeed(mode: OjFeedMode) {
        coroutineScope.launch {
            isLoadingFeed = true
            hasFeedError = false
            try {
                if (mode == OjFeedMode.FOR_YOU) {
                    forYouPage = 1
                    val result = recommendationRepository.getRecommendedForYouFeed(
                        userId = currentUserId,
                        sessionId = watchAnalyticsRepository.getSessionId(),
                        categoryFilter = selectedCategory,
                        page = 1,
                        pageSize = 10
                    )
                    if (result.isSuccess) {
                        val items = result.getOrDefault(emptyList())
                        forYouVideos = items
                        hasMoreForYou = items.size >= 10
                    } else {
                        hasFeedError = true
                    }
                } else {
                    // Following Feed
                    followingPage = 1
                    if (currentUserId.isNullOrBlank()) {
                        followingVideos = emptyList()
                        hasMoreFollowing = false
                        followedCreatorCount = 0
                    } else {
                        val followedIdsResult = socialInteractionRepository?.getFollowedUserIds(currentUserId)
                        val followedIds = followedIdsResult?.getOrDefault(emptyList()) ?: emptyList()
                        followedCreatorCount = followedIds.size
                        if (followedIds.isEmpty()) {
                            followingVideos = emptyList()
                            hasMoreFollowing = false
                        } else {
                            val result = ojRepository.getFollowingOjVideos(
                                followedUserIds = followedIds,
                                page = 1,
                                pageSize = 10
                            )
                            if (result.isSuccess) {
                                val items = result.getOrDefault(emptyList())
                                followingVideos = items
                                hasMoreFollowing = items.size >= 10
                            } else {
                                hasFeedError = true
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                hasFeedError = true
            } finally {
                isLoadingFeed = false
            }
        }
    }

    // Function to load the next page of videos with deduplication
    fun loadNextPage(mode: OjFeedMode) {
        if (isLoadingFeed || isFollowingPaginating) return
        coroutineScope.launch {
            if (mode == OjFeedMode.FOR_YOU && hasMoreForYou) {
                val nextPage = forYouPage + 1
                val result = recommendationRepository.getRecommendedForYouFeed(
                    userId = currentUserId,
                    sessionId = watchAnalyticsRepository.getSessionId(),
                    categoryFilter = selectedCategory,
                    page = nextPage,
                    pageSize = 10
                )
                if (result.isSuccess) {
                    val newItems = result.getOrDefault(emptyList())
                    if (newItems.isEmpty()) {
                        hasMoreForYou = false
                    } else {
                        forYouPage = nextPage
                        val existingIds = forYouVideos.map { it.ojId }.toSet()
                        val deduplicated = newItems.filter { it.ojId !in existingIds }
                        forYouVideos = forYouVideos + deduplicated
                        hasMoreForYou = newItems.size >= 10
                    }
                }
            } else if (mode == OjFeedMode.FOLLOWING && hasMoreFollowing && !currentUserId.isNullOrBlank()) {
                isFollowingPaginating = true
                try {
                    val nextPage = followingPage + 1
                    val followedIdsResult = socialInteractionRepository?.getFollowedUserIds(currentUserId)
                    val followedIds = followedIdsResult?.getOrDefault(emptyList()) ?: emptyList()
                    if (followedIds.isNotEmpty()) {
                        val result = ojRepository.getFollowingOjVideos(
                            followedUserIds = followedIds,
                            page = nextPage,
                            pageSize = 10
                        )
                        if (result.isSuccess) {
                            val newItems = result.getOrDefault(emptyList())
                            if (newItems.isEmpty()) {
                                hasMoreFollowing = false
                            } else {
                                followingPage = nextPage
                                val existingIds = followingVideos.map { it.ojId }.toSet()
                                val deduplicated = newItems.filter { it.ojId !in existingIds }
                                followingVideos = followingVideos + deduplicated
                                hasMoreFollowing = newItems.size >= 10
                            }
                        }
                    } else {
                        hasMoreFollowing = false
                    }
                } finally {
                    isFollowingPaginating = false
                }
            }
        }
    }

    // Trigger initial feed load or category filter change
    LaunchedEffect(activeFeedMode, selectedCategory, currentUserId) {
        isUserPaused = false
        loadInitialFeed(activeFeedMode)
    }

    val activeVideos = if (activeFeedMode == OjFeedMode.FOR_YOU) forYouVideos else followingVideos

    // Vertical Pager State
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { activeVideos.size }
    )

    // Deep Link Navigation and Target OJ Verification
    LaunchedEffect(targetOjId) {
        if (!targetOjId.isNullOrBlank()) {
            val resolvedId = targetOjId.trim()
            val existingIndex = forYouVideos.indexOfFirst { it.ojId == resolvedId }
            if (existingIndex >= 0) {
                activeFeedMode = OjFeedMode.FOR_YOU
                pagerState.scrollToPage(existingIndex)
            } else {
                val fetchResult = ojRepository.getOjVideoById(resolvedId)
                if (fetchResult.isSuccess) {
                    val fetchedVideo = fetchResult.getOrThrow()
                    if (fetchedVideo.visibility != ContentVisibility.PUBLIC && fetchedVideo.creatorId != currentUserId) {
                        onActionNotice("Linked OJ video is private")
                    } else {
                        // Prepend into the For You list cleanly without duplicate key issues
                        forYouVideos = listOf(fetchedVideo) + forYouVideos.filter { it.ojId != fetchedVideo.ojId }
                        activeFeedMode = OjFeedMode.FOR_YOU
                        pagerState.scrollToPage(0)
                    }
                } else {
                    onActionNotice("Linked OJ video is unavailable or has been removed")
                }
            }
            onTargetOjIdConsumed?.invoke()
        }
    }

    // Trigger next page loading when user scrolls near the end
    LaunchedEffect(pagerState.currentPage, activeVideos.size) {
        if (activeVideos.isNotEmpty() && pagerState.currentPage >= activeVideos.size - 2) {
            loadNextPage(activeFeedMode)
        }
    }

    // Reset user pause state on swipe to next video
    LaunchedEffect(pagerState.currentPage) {
        isUserPaused = false
    }

    // Check follow status and sync real Like status & counts for active/neighboring videos
    LaunchedEffect(pagerState.currentPage, activeVideos, currentUserId) {
        val currentVideo = activeVideos.getOrNull(pagerState.currentPage)
        if (currentVideo != null) {
            if (socialInteractionRepository != null) {
                // Fetch real like count
                val countResult = socialInteractionRepository.getLikesCount(currentVideo.ojId, TargetContentType.OJ)
                if (countResult.isSuccess) {
                    likeCountMap[currentVideo.ojId] = countResult.getOrDefault(currentVideo.likeCount)
                } else if (!likeCountMap.containsKey(currentVideo.ojId)) {
                    likeCountMap[currentVideo.ojId] = currentVideo.likeCount
                }

                // Fetch real comment count
                val commentCountResult = socialInteractionRepository.getCommentsCount(currentVideo.ojId, TargetContentType.OJ)
                if (commentCountResult.isSuccess) {
                    commentCountMap[currentVideo.ojId] = commentCountResult.getOrDefault(currentVideo.commentCount)
                } else if (!commentCountMap.containsKey(currentVideo.ojId)) {
                    commentCountMap[currentVideo.ojId] = currentVideo.commentCount
                }

                // Check like status if authenticated
                if (!currentUserId.isNullOrBlank() && !likedVideoMap.containsKey(currentVideo.ojId)) {
                    val statusResult = socialInteractionRepository.checkLikeStatus(currentUserId, currentVideo.ojId)
                    if (statusResult.isSuccess) {
                        likedVideoMap[currentVideo.ojId] = statusResult.getOrDefault(false)
                    }
                }

                // Check follow status if authenticated and not self
                if (!currentUserId.isNullOrBlank() && currentUserId != currentVideo.creatorId && !followStatusMap.containsKey(currentVideo.creatorId)) {
                    val statusResult = socialInteractionRepository.checkFollowStatus(currentUserId, currentVideo.creatorId)
                    if (statusResult.isSuccess) {
                        followStatusMap[currentVideo.creatorId] = statusResult.getOrDefault(false)
                    }
                }
            } else {
                if (!likeCountMap.containsKey(currentVideo.ojId)) {
                    likeCountMap[currentVideo.ojId] = currentVideo.likeCount
                }
                if (!commentCountMap.containsKey(currentVideo.ojId)) {
                    commentCountMap[currentVideo.ojId] = currentVideo.commentCount
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OjasDarkBackground)
    ) {
        // ==========================================
        // 1. VERTICAL VIDEO FEED
        // ==========================================
        if (activeFeedMode == OjFeedMode.FOLLOWING && currentUserId.isNullOrBlank()) {
            // Clean Unauthenticated Following State
            OjFollowingUnauthenticatedCard(
                onSignInClick = {
                    executeWithAuth(prompt = "Sign in to see creators you follow") {
                        loadInitialFeed(OjFeedMode.FOLLOWING)
                    }
                }
            )
        } else if (isLoadingFeed && activeVideos.isEmpty()) {
            // Minimal Loading Spinner
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = OjasRoyalBlue,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else if (activeFeedMode == OjFeedMode.FOLLOWING && activeVideos.isEmpty()) {
            if (followedCreatorCount == 0) {
                // Signed-in user follows 0 creators
                OjFollowingZeroFollowsState(
                    onExploreClick = {
                        activeFeedMode = OjFeedMode.FOR_YOU
                    }
                )
            } else {
                // Signed-in user follows creators, but none currently have eligible OJs
                OjFollowingNoContentState(
                    onExploreClick = {
                        activeFeedMode = OjFeedMode.FOR_YOU
                    }
                )
            }
        } else if (activeVideos.isEmpty()) {
            // Honest Clean Empty State for For You feed
            OjEmptyFeedState(
                feedMode = activeFeedMode,
                selectedCategory = selectedCategory,
                onClearFilter = {
                    onActionNotice("Showing all content")
                    onFilterClick()
                }
            )
        } else {
            // Full Screen Vertical Video Pager
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> activeVideos.getOrNull(index)?.ojId ?: index.toString() }
            ) { pageIndex ->
                val video = activeVideos.getOrNull(pageIndex)
                if (video != null) {
                    val isActivePage = pagerState.currentPage == pageIndex
                    val isLiked = likedVideoMap[video.ojId] ?: video.isLikedByMe
                    val likeCount = likeCountMap[video.ojId] ?: video.likeCount
                    val isLikeInFlight = likeInFlightMap[video.ojId] ?: false
                    val isFollowing = followStatusMap[video.creatorId] ?: video.isFollowedByMe
                    val isFollowLoading = followLoadingMap[video.creatorId] ?: false
                    val isSelf = currentUserId != null && currentUserId == video.creatorId

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    isUserPaused = !isUserPaused
                                }
                            )
                    ) {
                        // 1. Native Video Player
                        OjVideoPlayer(
                            videoUrl = video.videoUrl,
                            thumbnailUrl = video.thumbnailUrl,
                            isActive = isActivePage,
                            isUserPaused = isUserPaused,
                            modifier = Modifier.fillMaxSize(),
                            onPlaybackProgress = { _, durationMs, isPlaying ->
                                if (isActivePage && !isUserPaused && isPlaying) {
                                    val prevAccum = activeWatchAccumulator[video.ojId] ?: 0L
                                    val updatedAccum = prevAccum + OjWatchAnalyticsConfig.PROGRESS_POLL_INTERVAL_MS
                                    activeWatchAccumulator[video.ojId] = updatedAccum

                                    if (!sessionViewedSet.contains(video.ojId) &&
                                        OjWatchAnalyticsConfig.isQualifiedView(updatedAccum, durationMs)
                                    ) {
                                        sessionViewedSet.add(video.ojId)
                                        coroutineScope.launch {
                                            val event = OjViewEvent(
                                                ojId = video.ojId,
                                                viewerId = currentUserId,
                                                sessionId = watchAnalyticsRepository.getSessionId(),
                                                watchedDurationMs = updatedAccum,
                                                totalDurationMs = durationMs
                                            )
                                            watchAnalyticsRepository.recordQualifiedView(event)
                                        }
                                    }
                                }
                            }
                        )

                        // 2. Minimal Top Contrast Gradient
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.55f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // 3. Minimal Bottom Contrast Gradient
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.75f)
                                        )
                                    )
                                )
                        )

                        // 4. Compact Right-Side Action Controls
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 24.dp, end = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 4a. Like Action
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconButton(
                                    onClick = {
                                        executeWithAuth("Sign in to like videos") {
                                            val userId = currentUserId ?: return@executeWithAuth
                                            if (likeInFlightMap[video.ojId] == true) return@executeWithAuth
                                            likeInFlightMap[video.ojId] = true

                                            coroutineScope.launch {
                                                val currentLiked = likedVideoMap[video.ojId] ?: video.isLikedByMe
                                                val targetLiked = !currentLiked
                                                val result = socialInteractionRepository?.setLike(
                                                    userId = userId,
                                                    contentId = video.ojId,
                                                    contentType = TargetContentType.OJ,
                                                    isLiked = targetLiked
                                                )
                                                likeInFlightMap[video.ojId] = false

                                                if (result?.isSuccess == true) {
                                                    val confirmedState = result.getOrDefault(targetLiked)
                                                    likedVideoMap[video.ojId] = confirmedState

                                                    // Reconcile with verified backend like count
                                                    val updatedCountRes = socialInteractionRepository?.getLikesCount(video.ojId, TargetContentType.OJ)
                                                    val updatedCount = updatedCountRes?.getOrDefault(0L)
                                                        ?: (if (confirmedState) (likeCountMap[video.ojId] ?: 0L) + 1L else ((likeCountMap[video.ojId] ?: 1L) - 1L).coerceAtLeast(0L))
                                                    likeCountMap[video.ojId] = updatedCount

                                                    // Synchronize repository cache
                                                    ojRepository.syncLikeState(video.ojId, confirmedState, updatedCount)
                                                    onActionNotice(if (confirmedState) "Liked video" else "Removed like")
                                                } else {
                                                    val err = result?.exceptionOrNull()?.message ?: "Failed to update like"
                                                    onActionNotice(err)
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isLikeInFlight,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("oj_like_button")
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Like",
                                        tint = if (isLiked) Color(0xFFEF4444) else Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                if (likeCount > 0) {
                                    Text(
                                        text = formatOjCount(likeCount),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.testTag("oj_like_count")
                                    )
                                }
                            }

                            // 4b. Comment Action
                            val commentCount = commentCountMap[video.ojId] ?: video.commentCount
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconButton(
                                    onClick = {
                                        activeCommentVideo = video
                                        isCommentsSheetOpen = true
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("oj_comment_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = "Comment",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                if (commentCount > 0) {
                                    Text(
                                        text = formatOjCount(commentCount),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.testTag("oj_comment_count")
                                    )
                                }
                            }

                            // 4c. Real Native Share Action
                            IconButton(
                                onClick = {
                                    OjDeepLinkUtil.openSystemShareSheet(
                                        context = context,
                                        caption = video.caption,
                                        ojId = video.ojId
                                    )
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("oj_share_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Send,
                                    contentDescription = "Share",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // 4d. More Options Action
                            IconButton(
                                onClick = { selectedVideoForMore = video },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("oj_more_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "More Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // 5. Compact Bottom Information Area
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(0.80f)
                                .padding(start = 16.dp, bottom = 20.dp)
                        ) {
                            // Row 1: Profile Avatar + @creatorUsername + Compact Follow Action
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!video.creatorAvatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = video.creatorAvatarUrl,
                                        contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(OjasRoyalBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = video.creatorDisplayName.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "@${video.creatorUsername}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!isSelf) {
                                    Spacer(modifier = Modifier.width(10.dp))

                                    Button(
                                        onClick = {
                                            executeWithAuth("Sign in to follow creators") {
                                                if (isFollowLoading) return@executeWithAuth
                                                val userId = currentUserId ?: return@executeWithAuth
                                                followLoadingMap[video.creatorId] = true

                                                coroutineScope.launch {
                                                    val targetState = !isFollowing
                                                    val result = socialInteractionRepository?.setFollow(
                                                        followerId = userId,
                                                        followedId = video.creatorId,
                                                        isFollowing = targetState
                                                    )
                                                    followLoadingMap[video.creatorId] = false
                                                    if (result?.isSuccess == true) {
                                                        followStatusMap[video.creatorId] = targetState
                                                        onActionNotice(if (targetState) "Following @${video.creatorUsername}" else "Unfollowed @${video.creatorUsername}")
                                                    } else {
                                                        onActionNotice(result?.exceptionOrNull()?.message ?: "Failed to update follow")
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isFollowLoading,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFollowing) Color.White.copy(alpha = 0.2f) else OjasRoyalBlue,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(26.dp)
                                            .testTag("oj_follow_button")
                                    ) {
                                        Text(
                                            text = if (isFollowing) "Following" else "Follow",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Row 2: Caption (Max 2 lines)
                            if (video.caption.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = video.caption,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 17.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.95f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Row 3: Compact Tags
                            if (video.tags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = video.tags.joinToString(" ") { "#$it" },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    color = OjasGoldenYellow,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Row 4: Single-line Audio Track Info
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = video.audioTrack?.let { "${it.title} — ${it.artistName}" } ?: "Original Audio — ${video.creatorDisplayName}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. MINIMAL TOP FEED SWITCHER & FILTER ICON
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter)
        ) {
            // Centered Feed Switcher: For You | Following
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OjTopTabItem(
                    title = OjFeedMode.FOR_YOU.title,
                    isSelected = activeFeedMode == OjFeedMode.FOR_YOU,
                    onClick = { activeFeedMode = OjFeedMode.FOR_YOU },
                    testTag = "oj_for_you_tab"
                )

                OjTopTabItem(
                    title = OjFeedMode.FOLLOWING.title,
                    isSelected = activeFeedMode == OjFeedMode.FOLLOWING,
                    onClick = {
                        activeFeedMode = OjFeedMode.FOLLOWING
                    },
                    testTag = "oj_following_tab"
                )
            }

            // Compact filter icon at top right (only applies to For You)
            IconButton(
                onClick = onFilterClick,
                modifier = Modifier
                    .size(34.dp)
                    .align(Alignment.CenterEnd)
                    .testTag("oj_filter_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "Preferences",
                    tint = if (selectedCategory != null && activeFeedMode == OjFeedMode.FOR_YOU) OjasGoldenYellow else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ==========================================
        // 3. MORE OPTIONS MODAL BOTTOM SHEET
        // ==========================================
        if (selectedVideoForMore != null) {
            val targetVideo = selectedVideoForMore!!
            OjMoreBottomSheet(
                video = targetVideo,
                onDismissRequest = { selectedVideoForMore = null },
                onActionSelected = { action ->
                    if (action == "Video saved" || action == "Account blocked") {
                        executeWithAuth {
                            onActionNotice(action)
                        }
                    } else {
                        onActionNotice(action)
                    }
                    selectedVideoForMore = null
                }
            )
        }

        // ==========================================
        // 4. REAL OJ COMMENT MODAL BOTTOM SHEET
        // ==========================================
        if (isCommentsSheetOpen && activeCommentVideo != null) {
            val commentTargetVideo = activeCommentVideo!!
            OjCommentBottomSheet(
                ojVideo = commentTargetVideo,
                currentUserId = currentUserId,
                currentUser = currentUser,
                socialInteractionRepository = socialInteractionRepository,
                onDismissRequest = {
                    isCommentsSheetOpen = false
                    activeCommentVideo = null
                },
                onRequireAuth = { action ->
                    executeWithAuth(prompt = "Sign in to comment", action = action)
                },
                onCommentCountChanged = { newCount ->
                    commentCountMap[commentTargetVideo.ojId] = newCount
                    coroutineScope.launch {
                        ojRepository.syncCommentCount(commentTargetVideo.ojId, newCount)
                    }
                },
                onActionNotice = onActionNotice
            )
        }
    }
}

/**
 * Minimal Top Feed Switcher Tab Item (For You | Following).
 */
@Composable
private fun OjTopTabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
        label = "tab_text_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag(testTag)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp
            ),
            color = textColor
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Subtle indicator bar
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (isSelected) Color.White else Color.Transparent)
        )
    }
}

/**
 * Clean Unauthenticated State Card for Following feed.
 */
@Composable
private fun OjFollowingUnauthenticatedCard(
    onSignInClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Following Feed",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Sign in to watch short videos from creators you follow.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onSignInClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OjasRoyalBlue,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                modifier = Modifier.testTag("oj_following_signin_button")
            ) {
                Text(
                    text = "Sign In",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * Clean Minimal Empty State when signed-in user follows zero creators.
 */
@Composable
private fun OjFollowingZeroFollowsState(
    onExploreClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("oj_following_zero_follows_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Creators Followed Yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Follow creators to see their newest public OJs here.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onExploreClick,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OjasRoyalBlue,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                modifier = Modifier.testTag("oj_explore_creators_button")
            ) {
                Text(
                    text = "Explore For You",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Clean Truthful Empty State when user follows creators, but none have posted eligible OJs.
 */
@Composable
private fun OjFollowingNoContentState(
    onExploreClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("oj_following_no_content_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No New OJs",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "The creators you follow haven't posted any new public OJs yet.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onExploreClick,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                modifier = Modifier.testTag("oj_explore_for_you_button")
            ) {
                Text(
                    text = "Explore For You",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Honest Empty State for OJ Feed.
 */
@Composable
private fun OjEmptyFeedState(
    feedMode: OjFeedMode,
    selectedCategory: String?,
    onClearFilter: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (feedMode == OjFeedMode.FOLLOWING) "No Following Videos" else "No Videos Found",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (feedMode == OjFeedMode.FOLLOWING) {
                    "Videos from creators you follow will appear here."
                } else if (selectedCategory != null) {
                    "No published videos match \"$selectedCategory\" yet."
                } else {
                    "No OJ short videos published yet."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            if (selectedCategory != null && feedMode == OjFeedMode.FOR_YOU) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onClearFilter,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Show All Videos",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Lightweight More Bottom Sheet for secondary actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OjMoreBottomSheet(
    video: OjVideo,
    onDismissRequest: () -> Unit,
    onActionSelected: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Options",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OjMoreOptionItem(
                icon = Icons.Outlined.BookmarkBorder,
                label = "Save video",
                testTag = "oj_more_save",
                onClick = { onActionSelected("Video saved") }
            )

            OjMoreOptionItem(
                icon = Icons.Outlined.NotInterested,
                label = "Not interested",
                testTag = "oj_more_not_interested",
                onClick = { onActionSelected("Will show fewer videos like this") }
            )

            OjMoreOptionItem(
                icon = Icons.Outlined.ReportProblem,
                label = "Report",
                testTag = "oj_more_report",
                onClick = { onActionSelected("Report submitted") }
            )

            OjMoreOptionItem(
                icon = Icons.Outlined.Block,
                label = "Block @${video.creatorUsername}",
                testTag = "oj_more_block",
                onClick = { onActionSelected("Account blocked") }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun OjMoreOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatOjCount(count: Long): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }
}
