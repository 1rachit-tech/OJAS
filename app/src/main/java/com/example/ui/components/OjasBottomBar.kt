package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.navigation.OjasDestination
import com.example.ui.theme.OjasRoyalBlue
import com.example.ui.theme.OjasSlate400
import com.example.ui.theme.OjasVibrantGold
import com.example.ui.theme.OjasVibrantOrange

@Composable
fun OjasBottomBar(
    currentDestination: OjasDestination,
    onDestinationSelected: (OjasDestination) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Home
                NavItem(
                    destination = OjasDestination.HOME,
                    isSelected = currentDestination == OjasDestination.HOME,
                    onClick = { onDestinationSelected(OjasDestination.HOME) }
                )

                // 2. OJ
                NavItem(
                    destination = OjasDestination.OJ,
                    isSelected = currentDestination == OjasDestination.OJ,
                    onClick = { onDestinationSelected(OjasDestination.OJ) }
                )

                // 3. Central Create Action (Vibrant gradient rounded-2xl squircle)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = OjasVibrantOrange.copy(alpha = 0.4f),
                            spotColor = OjasVibrantOrange.copy(alpha = 0.5f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(OjasVibrantOrange, OjasVibrantGold)
                            )
                        )
                        .clickable(onClick = onCreateClick)
                        .testTag("create_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Create",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // 4. Explore
                NavItem(
                    destination = OjasDestination.EXPLORE,
                    isSelected = currentDestination == OjasDestination.EXPLORE,
                    onClick = { onDestinationSelected(OjasDestination.EXPLORE) }
                )

                // 5. You
                NavItem(
                    destination = OjasDestination.YOU,
                    isSelected = currentDestination == OjasDestination.YOU,
                    onClick = { onDestinationSelected(OjasDestination.YOU) }
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: OjasDestination,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) OjasRoyalBlue else OjasSlate400,
        label = "nav_icon_color"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) OjasRoyalBlue else OjasSlate400,
        label = "nav_text_color"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(destination.testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = destination.title,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = destination.title,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
