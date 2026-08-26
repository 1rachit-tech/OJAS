package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OjasSlate400
import com.example.ui.theme.OjasSlate500
import com.example.ui.theme.OjasSlate800

/**
 * Explore Discovery Categories
 */
private val EXPLORE_CATEGORIES = listOf(
    "Trending",
    "Music",
    "Tech",
    "Gaming",
    "Comedy",
    "Sports",
    "Education"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onActionNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategoryIndex by remember { mutableStateOf(-1) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // 1. PAGE HEADING: Display simple page name "Explore"
            TopAppBar(
                title = {
                    Text(
                        text = "Explore",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 2. SEARCH: Clear search entry near top
            ExploreSearchEntry(
                onSearchClick = {
                    onActionNotice("Coming Soon")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // 3. COMPACT DISCOVERY CATEGORIES: Limited horizontal discovery area
            ExploreCategoriesRow(
                categories = EXPLORE_CATEGORIES,
                selectedIndex = selectedCategoryIndex,
                onCategoryClick = { index, categoryName ->
                    selectedCategoryIndex = index
                    onActionNotice("Coming Soon")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )

            // 4. DISCOVER CONTENT AREA: Clean grid / adaptable content layout
            DiscoverContentGrid(
                onItemClick = { itemIndex ->
                    onActionNotice("Coming Soon")
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * 2. Search entry field showing placeholder and triggering "Coming Soon" on interaction.
 */
@Composable
private fun ExploreSearchEntry(
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSearchClick)
            .testTag("explore_search_bar")
    ) {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            enabled = false,
            placeholder = {
                Text(
                    text = "Search people, content, topics...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OjasSlate400
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = OjasSlate500,
                    modifier = Modifier.size(20.dp)
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                disabledBorderColor = Color.Transparent,
                disabledTextColor = OjasSlate800,
                disabledPlaceholderColor = OjasSlate400,
                disabledLeadingIconColor = OjasSlate500
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 3. Compact horizontally scrollable discovery categories.
 */
@Composable
private fun ExploreCategoriesRow(
    categories: List<String>,
    selectedIndex: Int,
    onCategoryClick: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(categories.size) { index ->
            val category = categories[index]
            val isSelected = index == selectedIndex

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onCategoryClick(index, category) }
                    .testTag("explore_category_$category"),
                color = if (isSelected) OjasSlate800 else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                border = if (!isSelected) {
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                } else null
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = if (isSelected) Color.White else OjasSlate800,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/**
 * 4. Main content discovery grid layout (lightweight, lazy-loaded structure).
 */
@Composable
private fun DiscoverContentGrid(
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(15) { index ->
            DiscoverGridItem(
                index = index,
                onClick = { onItemClick(index) }
            )
        }
    }
}

/**
 * Lightweight individual grid item placeholder.
 */
@Composable
private fun DiscoverGridItem(
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag("discover_grid_item_$index"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Discover content placeholder",
                tint = OjasSlate400.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
