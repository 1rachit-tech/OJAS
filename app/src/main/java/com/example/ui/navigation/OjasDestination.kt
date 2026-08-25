package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

enum class OjasDestination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home, "home_tab"),
    OJ("oj", "OJ", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle, "oj_tab"),
    EXPLORE("explore", "Explore", Icons.Filled.Search, Icons.Outlined.Search, "explore_tab"),
    YOU("you", "You", Icons.Filled.Person, Icons.Outlined.Person, "you_tab")
}
