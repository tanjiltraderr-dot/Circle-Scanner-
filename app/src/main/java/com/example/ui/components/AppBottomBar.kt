package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.navigation.Screen
import com.example.ui.theme.BrandBlue

@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onScannerClick: () -> Unit = { onNavigate(Screen.Scanner.createRoute("QR_BARCODE")) },
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_bottom_bar"),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home
            val isHomeSelected = currentRoute == Screen.Home.route
            BottomNavItemCell(
                title = "Home",
                icon = if (isHomeSelected) Icons.Filled.Home else Icons.Outlined.Home,
                isSelected = isHomeSelected,
                onClick = { onNavigate(Screen.Home.route) },
                modifier = Modifier.weight(1f),
                testTag = "nav_item_home"
            )

            // 2. Tools
            val isToolsSelected = currentRoute == Screen.Tools.route || currentRoute?.startsWith("category/") == true
            BottomNavItemCell(
                title = "Tools",
                icon = if (isToolsSelected) Icons.Filled.GridView else Icons.Outlined.GridView,
                isSelected = isToolsSelected,
                onClick = { onNavigate(Screen.Tools.route) },
                modifier = Modifier.weight(1f),
                testTag = "nav_item_tools"
            )

            // 3. Center Scanner Button (Prominent circular action button)
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = CircleShape,
                            ambientColor = BrandBlue.copy(alpha = 0.35f),
                            spotColor = BrandBlue.copy(alpha = 0.55f)
                        )
                        .clip(CircleShape)
                        .background(BrandBlue)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = Color.White),
                            role = Role.Button,
                            onClick = onScannerClick
                        )
                        .testTag("floating_scanner_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scanner",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 4. All Documents (replacing removed bottom Pro item)
            val isDocsSelected = currentRoute == Screen.FileManager.route
            BottomNavItemCell(
                title = "Documents",
                icon = if (isDocsSelected) Icons.Filled.Description else Icons.Outlined.Description,
                isSelected = isDocsSelected,
                onClick = { onNavigate(Screen.FileManager.route) },
                modifier = Modifier.weight(1f),
                testTag = "nav_item_documents"
            )

            // 5. Settings
            val isSettingsSelected = currentRoute == Screen.Settings.route
            BottomNavItemCell(
                title = "Settings",
                icon = if (isSettingsSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                isSelected = isSettingsSelected,
                onClick = { onNavigate(Screen.Settings.route) },
                modifier = Modifier.weight(1f),
                testTag = "nav_item_settings"
            )
        }
    }
}

@Composable
private fun BottomNavItemCell(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 150),
        label = "icon_color"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 150),
        label = "text_color"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = BrandBlue.copy(alpha = 0.15f)),
                role = Role.Tab,
                onClick = onClick
            )
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                letterSpacing = 0.2.sp
            ),
            color = textColor
        )
    }
}
