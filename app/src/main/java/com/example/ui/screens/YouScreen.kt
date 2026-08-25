package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.auth.AuthRepository
import com.example.data.model.OjVideo
import com.example.data.model.Post
import com.example.data.repository.OjRepository
import com.example.data.repository.PostRepository
import com.example.data.repository.SocialInteractionRepository
import com.example.ui.components.AccountSecurityDialog
import com.example.ui.theme.OjasGoldenYellow
import com.example.ui.theme.OjasRoyalBlue
import com.example.ui.theme.OjasSlate100
import com.example.ui.theme.OjasSlate200
import com.example.ui.theme.OjasSlate400
import com.example.ui.theme.OjasSlate500
import com.example.ui.theme.OjasSlate800
import com.example.ui.theme.OjasVibrantOrange

/**
 * You Content Switcher Options: strictly Posts and OJ.
 */
enum class YouContentType(val title: String) {
    POSTS("Posts"),
    OJ("OJ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouScreen(
    onActionNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
    currentUser: com.example.data.model.OjasUser? = null,
    onLoginClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    authRepository: AuthRepository? = null,
    socialInteractionRepository: SocialInteractionRepository? = null,
    postRepository: PostRepository? = null,
    ojRepository: OjRepository? = null
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isSettingsSheetOpen by remember { mutableStateOf(false) }
    var isAccountSecurityOpen by remember { mutableStateOf(false) }
    var isEmailVerifiedState by remember(currentUser) {
        mutableStateOf(currentUser?.isEmailVerified ?: (authRepository?.isEmailVerified() ?: false))
    }
    var linkedProviders by remember { mutableStateOf<List<String>>(emptyList()) }
    var followersCount by remember { mutableStateOf<Int?>(null) }
    var followingCount by remember { mutableStateOf<Int?>(null) }
    var userPosts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var userOjVideos by remember { mutableStateOf<List<OjVideo>>(emptyList()) }
    val isAuthenticated = currentUser != null
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(currentUser?.userId) {
        val uid = currentUser?.userId
        if (!uid.isNullOrBlank() && socialInteractionRepository != null) {
            followersCount = socialInteractionRepository.getFollowersCount(uid).getOrNull()
            followingCount = socialInteractionRepository.getFollowingCount(uid).getOrNull()
        } else {
            followersCount = null
            followingCount = null
        }

        if (!uid.isNullOrBlank() && postRepository != null) {
            val postsResult = postRepository.getPostsByUserId(uid)
            userPosts = postsResult.getOrDefault(emptyList())
        } else {
            userPosts = emptyList()
        }

        if (!uid.isNullOrBlank() && ojRepository != null) {
            val ojResult = ojRepository.getOjVideosByUserId(uid)
            userOjVideos = ojResult.getOrDefault(emptyList())
        } else {
            userOjVideos = emptyList()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // Page Heading & 5. Settings Access
            TopAppBar(
                title = {
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    // 5. SETTINGS ACCESS: One compact, accessible settings entry
                    IconButton(
                        onClick = {
                            if (isAuthenticated) {
                                isSettingsSheetOpen = true
                            } else {
                                onLoginClick()
                            }
                        },
                        modifier = Modifier.testTag("you_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ==========================================
            // 1. COMPACT PROFILE INFORMATION & 2. PRIMARY ACTION
            // ==========================================
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile picture placeholder or real avatar image
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(OjasRoyalBlue.copy(alpha = 0.12f))
                                .border(1.5.dp, OjasRoyalBlue.copy(alpha = 0.5f), CircleShape)
                                .clickable {
                                    if (isAuthenticated) {
                                        onActionNotice("Coming Soon")
                                    } else {
                                        onLoginClick()
                                    }
                                }
                                .testTag("you_profile_avatar"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isAuthenticated && !currentUser?.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = currentUser?.avatarUrl,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = "Profile Picture",
                                    tint = OjasRoyalBlue,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Compact honest profile statistics structure
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ProfileStatItem(
                                count = if (isAuthenticated) followersCount?.toString() ?: "—" else "—",
                                label = "Followers",
                                onClick = {
                                    if (isAuthenticated) {
                                        onActionNotice("Coming Soon")
                                    } else {
                                        onLoginClick()
                                    }
                                }
                            )
                            ProfileStatItem(
                                count = if (isAuthenticated) followingCount?.toString() ?: "—" else "—",
                                label = "Following",
                                onClick = {
                                    if (isAuthenticated) {
                                        onActionNotice("Coming Soon")
                                    } else {
                                        onLoginClick()
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Display name & @username (Honest setup-ready state)
                    val displayName = if (isAuthenticated) {
                        currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Your Profile"
                    } else {
                        "Your Profile"
                    }
                    val usernameText = if (isAuthenticated) {
                        currentUser?.username?.takeIf { it.isNotBlank() }?.let { "@${it.removePrefix("@")}" } ?: "@username"
                    } else {
                        "@username"
                    }

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = usernameText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp
                        ),
                        color = OjasSlate500
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Short bio
                    val bioText = if (isAuthenticated) {
                        currentUser?.bio?.takeIf { it.isNotBlank() } ?: "Your personal space on OJAS."
                    } else {
                        "Sign in to view your profile and personal activity."
                    }
                    Text(
                        text = bioText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. PRIMARY PROFILE ACTION: Edit Profile (Authenticated) or Login (Unauthenticated)
                    if (isAuthenticated) {
                        Button(
                            onClick = { onActionNotice("Coming Soon") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("edit_profile_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(
                                text = "Edit Profile",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Button(
                            onClick = onLoginClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("you_login_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OjasRoyalBlue,
                                contentColor = androidx.compose.ui.graphics.Color.White
                            )
                        ) {
                            Text(
                                text = "Login",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 3. CONTENT SWITCHER: Strictly Posts and OJ
            // ==========================================
            item {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = OjasRoyalBlue,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = OjasRoyalBlue
                        )
                    }
                ) {
                    // Posts Tab
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        modifier = Modifier.testTag("you_posts_tab")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GridOn,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = YouContentType.POSTS.title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    // OJ Tab
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        modifier = Modifier.testTag("you_oj_tab")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = YouContentType.OJ.title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 4. USER CONTENT AREA: Real Published Posts / OJ Videos or Honest Empty State
            // ==========================================
            if (selectedTabIndex == 0 && userPosts.isNotEmpty()) {
                items(userPosts.size) { index ->
                    val post = userPosts[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("user_post_item_${post.postId}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            if (post.textContent.isNotBlank()) {
                                Text(
                                    text = post.textContent,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            if (post.mediaAttachments.isNotEmpty()) {
                                val attachment = post.mediaAttachments.first()
                                if (attachment.thumbnailUrl != null || attachment.mediaUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = attachment.thumbnailUrl ?: attachment.mediaUrl,
                                        contentDescription = "Post media preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Public,
                                        contentDescription = null,
                                        tint = OjasSlate400,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Public",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OjasSlate500
                                    )
                                }

                                Text(
                                    text = "Published",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OjasSlate400
                                )
                            }
                        }
                    }
                }
            } else if (selectedTabIndex == 1 && userOjVideos.isNotEmpty()) {
                items(userOjVideos.size) { index ->
                    val oj = userOjVideos[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("user_oj_item_${oj.ojId}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = OjasVibrantOrange.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "OJ • ${oj.durationSeconds}s",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = OjasVibrantOrange
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (oj.category.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "#${oj.category}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OjasGoldenYellow
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Public,
                                        contentDescription = null,
                                        tint = OjasSlate400,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Public",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OjasSlate500
                                    )
                                }
                            }

                            if (oj.caption.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = oj.caption,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (oj.tags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = oj.tags.joinToString(" ") { "#$it" },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    color = OjasRoyalBlue
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 44.dp)
                            .testTag("you_content_area"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(OjasRoyalBlue.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selectedTabIndex == 0) {
                                    Icons.Outlined.GridOn
                                } else {
                                    Icons.Outlined.PlayCircle
                                },
                                contentDescription = null,
                                tint = OjasRoyalBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (selectedTabIndex == 0) "No posts yet" else "No OJ videos yet",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (selectedTabIndex == 0) {
                                "Your photos, updates, and thoughts will appear here."
                            } else {
                                "Short video creations will be stored in this section."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                lineHeight = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Settings Bottom Sheet (Compact M3 layout for account options & logout)
    if (isSettingsSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isSettingsSheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Account settings item
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            isSettingsSheetOpen = false
                            if (authRepository != null) {
                                coroutineScope.launch {
                                    linkedProviders = authRepository.getLinkedProviders()
                                }
                            }
                            isAccountSecurityOpen = true
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                        .testTag("you_account_settings_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Account & Security",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Privacy settings item
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            isSettingsSheetOpen = false
                            if (authRepository != null) {
                                coroutineScope.launch {
                                    linkedProviders = authRepository.getLinkedProviders()
                                }
                            }
                            isAccountSecurityOpen = true
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                        .testTag("you_privacy_safety_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Privacy & Safety",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Log out action item
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            isSettingsSheetOpen = false
                            onLogoutClick()
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                        .testTag("you_logout_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Log Out",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (isAccountSecurityOpen) {
            AccountSecurityDialog(
                currentUser = currentUser,
                linkedProviders = linkedProviders,
                isEmailVerified = isEmailVerifiedState,
                onDismissRequest = { isAccountSecurityOpen = false },
                onSendPasswordReset = {
                    val email = currentUser?.email
                    if (!email.isNullOrBlank() && authRepository != null) {
                        coroutineScope.launch {
                            val res = authRepository.sendPasswordResetEmail(email)
                            if (res.isSuccess) {
                                onActionNotice("Password reset email sent to $email")
                            } else {
                                onActionNotice(res.exceptionOrNull()?.message ?: "Failed to send reset email")
                            }
                        }
                    } else {
                        onActionNotice("No registered email address found for this account.")
                    }
                },
                onSendEmailVerification = {
                    if (authRepository != null) {
                        coroutineScope.launch {
                            val res = authRepository.sendEmailVerification()
                            if (res.isSuccess) {
                                onActionNotice("Verification email sent to ${currentUser?.email}")
                            } else {
                                onActionNotice(res.exceptionOrNull()?.message ?: "Failed to send verification email")
                            }
                        }
                    }
                },
                onRefreshVerificationStatus = {
                    if (authRepository != null) {
                        coroutineScope.launch {
                            val res = authRepository.reloadUser()
                            if (res.isSuccess) {
                                val isVerified = res.getOrDefault(false)
                                isEmailVerifiedState = isVerified
                                if (isVerified) {
                                    onActionNotice("Email verified successfully!")
                                } else {
                                    onActionNotice("Email is not verified yet. Please check your inbox and click the verification link.")
                                }
                            } else {
                                onActionNotice(res.exceptionOrNull()?.message ?: "Failed to refresh verification status.")
                            }
                        }
                    }
                },
                onLinkGoogleClick = {
                    onActionNotice("Please sign out and sign in with Google to link your accounts.")
                }
            )
        }
    }
}

/**
 * Compact profile statistic item.
 */
@Composable
private fun ProfileStatItem(
    count: String,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
