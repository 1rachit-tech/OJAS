package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OjasRoyalBlue
import com.example.ui.theme.OjasSlate100
import com.example.ui.theme.OjasSlate200
import com.example.ui.theme.OjasSlate400
import com.example.ui.theme.OjasSlate500
import com.example.ui.theme.OjasSlate800
import com.example.ui.theme.OjasVibrantOrange

/**
 * Feed modes supported in the OJAS Home stream.
 */
enum class HomeFeedMode(val title: String) {
    FOR_YOU("For You"),
    FOLLOWING("Following")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onActionNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRequireAuth: (((() -> Unit) -> Unit))? = null
) {
    var selectedFeedMode by remember { mutableStateOf(HomeFeedMode.FOR_YOU) }

    fun executeWithAuth(action: () -> Unit) {
        if (onRequireAuth != null) {
            onRequireAuth(action)
        } else {
            action()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // 1. Compact Top Area (OJAS branding + single notification placeholder)
            TopAppBar(
                title = {
                    Text(
                        text = "OJAS",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = OjasRoyalBlue,
                        modifier = Modifier.testTag("home_brand_title")
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            executeWithAuth {
                                onActionNotice("Notifications coming soon")
                            }
                        },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .testTag("home_notifications_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsNone,
                            contentDescription = "Notifications",
                            tint = OjasSlate800,
                            modifier = Modifier.size(22.dp)
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
            // 2. Feed Switcher Area (FOR YOU | FOLLOWING)
            item {
                HomeFeedSwitcher(
                    selectedMode = selectedFeedMode,
                    onModeSelected = { selectedFeedMode = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            // 3. Compact Story Area (Original rounded-square squircle form)
            item {
                HomeStoryRow(
                    onAddStoryClick = {
                        executeWithAuth {
                            onActionNotice("Story creation coming soon")
                        }
                    },
                    onStoryPlaceholderClick = { label -> onActionNotice("$label space ready for creator stories") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }

            // Subtle divider separator
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                )
            }

            // 4. Main Content Feed (Polished empty state foundation for For You / Following)
            item {
                AnimatedContent(
                    targetState = selectedFeedMode,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                    },
                    label = "feed_mode_transition"
                ) { mode ->
                    when (mode) {
                        HomeFeedMode.FOR_YOU -> {
                            HomeFeedEmptyState(
                                icon = Icons.Outlined.DynamicFeed,
                                title = "Your Feed is Ready",
                                description = "Recommended community photos, videos, and updates will appear here as creators publish.",
                                testTag = "home_feed_for_you_empty"
                            )
                        }
                        HomeFeedMode.FOLLOWING -> {
                            HomeFeedEmptyState(
                                icon = Icons.Outlined.People,
                                title = "Following Stream",
                                description = "Posts from creators and friends you follow will appear here in chronological stream.",
                                testTag = "home_feed_following_empty"
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Minimal and lightweight Home Feed Switcher (For You | Following).
 */
@Composable
private fun HomeFeedSwitcher(
    selectedMode: HomeFeedMode,
    onModeSelected: (HomeFeedMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HomeFeedSwitcherTab(
            title = HomeFeedMode.FOR_YOU.title,
            isSelected = selectedMode == HomeFeedMode.FOR_YOU,
            onClick = { onModeSelected(HomeFeedMode.FOR_YOU) },
            testTag = "feed_switcher_for_you"
        )

        Spacer(modifier = Modifier.width(20.dp))

        HomeFeedSwitcherTab(
            title = HomeFeedMode.FOLLOWING.title,
            isSelected = selectedMode == HomeFeedMode.FOLLOWING,
            onClick = { onModeSelected(HomeFeedMode.FOLLOWING) },
            testTag = "feed_switcher_following"
        )
    }
}

@Composable
private fun HomeFeedSwitcherTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val textColor by animateColorAsState(
        targetValue = if (isSelected) OjasSlate800 else OjasSlate400,
        label = "tab_text_color"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp
            ),
            color = textColor
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Subtle active indicator bar
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSelected) OjasRoyalBlue else Color.Transparent)
        )
    }
}

/**
 * Compact horizontal Story area featuring original OJAS rounded-square squircle items.
 */
@Composable
private fun HomeStoryRow(
    onAddStoryClick: () -> Unit,
    onStoryPlaceholderClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        // First item: Add Story (Original rounded-square with vibrant orange accent)
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onAddStoryClick)
                    .testTag("add_story_item")
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .border(1.5.dp, OjasVibrantOrange, RoundedCornerShape(16.dp))
                        .padding(2.5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(OjasSlate100),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add Story",
                        tint = OjasVibrantOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = "Add Story",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = OjasSlate800
                )
            }
        }

        // Story placeholders prepared for future social feed
        val placeholders = listOf("Friends", "Creators", "Channels", "Updates")
        items(placeholders.size) { index ->
            val label = placeholders[index]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onStoryPlaceholderClick(label) }
                    .testTag("story_placeholder_$index")
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .border(1.dp, OjasSlate200, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(OjasSlate100.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label.take(2).uppercase(),
                        fontWeight = FontWeight.SemiBold,
                        color = OjasSlate400,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = OjasSlate500
                )
            }
        }
    }
}

/**
 * Clean, lightweight and intentional empty feed state.
 */
@Composable
private fun HomeFeedEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(OjasRoyalBlue.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OjasRoyalBlue,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            ),
            color = OjasSlate800
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                lineHeight = 19.sp
            ),
            color = OjasSlate500,
            textAlign = TextAlign.Center
        )
    }
}
