package com.example.ui.screens.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import com.example.UtilityHubApp
import com.example.domain.model.ToolCategory
import com.example.domain.model.ToolItem
import com.example.domain.model.ToolRegistry
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.ReadingBackground
import com.example.ui.theme.ReadingBorder
import com.example.ui.theme.ReadingSurface
import com.example.ui.theme.ReadingTextPrimary
import com.example.ui.theme.ReadingTextSecondary
import kotlinx.coroutines.launch

// Data class representing each of the 10 category cards matching the reference image
data class CategoryDisplayItem(
    val categoryId: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val toolCount: Int
)

@Composable
fun ToolsScreen(
    onNavigateToCategory: (String) -> Unit,
    onNavigateToTool: (String) -> Unit
) {
    val repository = remember { UtilityHubApp.instance.repository }
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }

    // Search results filtering across all registered tools
    val searchResults = remember(searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            ToolRegistry.allTools.filter { tool ->
                tool.name.contains(query, ignoreCase = true) ||
                        tool.description.contains(query, ignoreCase = true) ||
                        tool.category.title.contains(query, ignoreCase = true) ||
                        tool.keywords.any { it.contains(query, ignoreCase = true) }
            }
        }
    }

    // 10 Categories in the exact order and specifications from prompt and reference screenshot
    val categories = remember {
        listOf(
            CategoryDisplayItem(
                categoryId = "scanner",
                title = "Scanner",
                description = "Scan QR, barcode and documents",
                icon = Icons.Default.QrCodeScanner,
                iconColor = Color(0xFF2563EB), // Blue
                toolCount = ToolRegistry.getCategoryCount(ToolCategory.SCANNER).let { if (it > 0) it else 4 }
            ),
            CategoryDisplayItem(
                categoryId = "pdf",
                title = "PDF Tools",
                description = "Merge, split, compress and more",
                icon = Icons.Default.PictureAsPdf,
                iconColor = Color(0xFFEF4444), // Red
                toolCount = ToolRegistry.getCategoryCount(ToolCategory.PDF).let { if (it > 0) it else 12 }
            ),
            CategoryDisplayItem(
                categoryId = "qr_barcode",
                title = "QR & Barcode",
                description = "Scan, generate and create QR codes",
                icon = Icons.Default.QrCode,
                iconColor = Color(0xFF10B981), // Green
                toolCount = ToolRegistry.getCategoryCount(ToolCategory.QR_BARCODE).let { if (it > 0) it else 11 }
            ),
            CategoryDisplayItem(
                categoryId = "image",
                title = "Image Tools",
                description = "Compress, resize, crop and convert",
                icon = Icons.Default.Image,
                iconColor = Color(0xFF8B5CF6), // Purple
                toolCount = ToolRegistry.getCategoryCount(ToolCategory.IMAGE).let { if (it > 0) it else 7 }
            ),
            CategoryDisplayItem(
                categoryId = "files",
                title = "File Tools",
                description = "Manage, organize and share files",
                icon = Icons.Default.Folder,
                iconColor = Color(0xFFF97316), // Orange
                toolCount = ToolRegistry.getCategoryCount(ToolCategory.FILES).let { if (it > 0) it else 10 }
            ),
            CategoryDisplayItem(
                categoryId = "finance",
                title = "Finance",
                description = "Expense, income and tracking",
                icon = Icons.Default.AccountBalanceWallet,
                iconColor = Color(0xFF0D9488), // Teal
                toolCount = ToolRegistry.getCategoryCount(ToolCategory.FINANCE).let { if (it > 0) it else 4 }
            ),
            CategoryDisplayItem(
                categoryId = "calculator",
                title = "Calculator & Converter",
                description = "Math, date, unit and more",
                icon = Icons.Default.Calculate,
                iconColor = Color(0xFF2563EB), // Blue
                toolCount = ToolRegistry.getCategoryCount(ToolCategory.CALCULATOR).let { if (it > 0) it else 10 }
            ),
            CategoryDisplayItem(
                categoryId = "productivity",
                title = "Productivity",
                description = "Notes, timer, stopwatch and more",
                icon = Icons.Default.AssignmentTurnedIn,
                iconColor = Color(0xFF6366F1), // Purple
                toolCount = ToolRegistry.getCategoryCount(ToolCategory.PRODUCTIVITY).let { if (it > 0) it else 4 }
            ),
            CategoryDisplayItem(
                categoryId = "security",
                title = "Security",
                description = "Password generator and privacy tools",
                icon = Icons.Default.Shield,
                iconColor = Color(0xFFEF4444), // Red
                toolCount = ToolRegistry.getCategoryCount(ToolCategory.SECURITY).let { if (it > 0) it else 3 }
            ),
            CategoryDisplayItem(
                categoryId = "other",
                title = "Other Utilities",
                description = "Useful tools for everyday life",
                icon = Icons.Default.Apps,
                iconColor = Color(0xFF64748B), // Gray-blue
                toolCount = ToolRegistry.getCategoryCount(ToolCategory.OTHER).let { if (it > 0) it else 6 }
            )
        )
    }

    val currentThemeMode by repository.themeMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (currentThemeMode.uppercase()) {
        "DARK" -> true
        "SYSTEM", "SYSTEM DEFAULT" -> isSystemDark
        else -> false
    }
    val isReading = currentThemeMode.uppercase() == "READING"

    val screenBg = when {
        isDark -> DarkBackground
        isReading -> ReadingBackground
        else -> Color.White
    }
    val cardBg = when {
        isDark -> DarkSurface
        isReading -> ReadingSurface
        else -> Color.White
    }
    val primaryTextColor = when {
        isDark -> DarkTextPrimary
        isReading -> ReadingTextPrimary
        else -> Color(0xFF0F172A)
    }
    val secondaryTextColor = when {
        isDark -> DarkTextSecondary
        isReading -> ReadingTextSecondary
        else -> Color(0xFF64748B)
    }
    val cardBorder = when {
        isDark -> DarkBorder
        isReading -> ReadingBorder
        else -> Color(0xFFE2E8F0)
    }
    val searchBarBg = when {
        isDark -> Color(0xFF1E293B)
        isReading -> Color(0xFFF1EAE0)
        else -> Color(0xFFF1F5F9)
    }
    val searchBarBorder = when {
        isDark -> DarkBorder
        isReading -> ReadingBorder
        else -> Color(0xFFE2E8F0).copy(alpha = 0.8f)
    }
    val toolCountPillBg = when {
        isDark -> Color(0xFF2563EB).copy(alpha = 0.2f)
        isReading -> Color(0xFFE8DFCD)
        else -> Color(0xFFEFF6FF)
    }
    val toolCountPillText = when {
        isDark -> Color(0xFF93C5FD)
        isReading -> Color(0xFF2B5CC4)
        else -> Color(0xFF3B82F6)
    }
    val searchCardBg = when {
        isDark -> Color(0xFF1E293B)
        isReading -> ReadingSurface
        else -> Color(0xFFF8FAFC)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        // 1. Header matching reference screenshot (Title and Subtitle only)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
        ) {
            Text(
                text = "Tools",
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Everything you need, in one place",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
            )
        }

        // 2. Search Bar matching reference
        Surface(
            shape = CircleShape,
            color = searchBarBg,
            border = BorderStroke(1.dp, searchBarBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .height(48.dp)
                .testTag("tools_global_search_input")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = secondaryTextColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search tools...",
                            style = TextStyle(
                                fontSize = 14.sp,
                                color = secondaryTextColor
                            )
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = primaryTextColor,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(BrandBlue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Content: Search Results or 2-Column Category Grid
        if (searchQuery.isNotBlank()) {
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = secondaryTextColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No tools matched \"$searchQuery\"",
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryTextColor
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try another search keyword",
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = secondaryTextColor
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("tools_search_results_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults, key = { it.id }) { tool ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    scope.launch { repository.recordToolUsage(tool.id) }
                                    onNavigateToTool(tool.route)
                                }
                                .testTag("search_result_${tool.id}"),
                            shape = RoundedCornerShape(14.dp),
                            color = searchCardBg,
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(tool.category.accentColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tool.icon,
                                        contentDescription = null,
                                        tint = tool.category.accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tool.name,
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryTextColor
                                        )
                                    )
                                    Text(
                                        text = "${tool.category.title} · ${tool.description}",
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            color = secondaryTextColor
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFF64748B) else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // 4. Two-Column Category Grid matching reference screenshot
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("tools_categories_grid"),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 90.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(categories, key = { it.categoryId }) { item ->
                    ToolsCategoryGridCard(
                        item = item,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        toolCountPillBg = toolCountPillBg,
                        toolCountPillText = toolCountPillText,
                        isDark = isDark,
                        onClick = { onNavigateToCategory(item.categoryId) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Category Grid Card matching reference screenshot
// ---------------------------------------------------------------------------
@Composable
private fun ToolsCategoryGridCard(
    item: CategoryDisplayItem,
    cardBg: Color,
    cardBorder: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    toolCountPillBg: Color,
    toolCountPillText: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = if (isDark) 0.dp else 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag("category_card_${item.categoryId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored Rounded-Square Icon on the left
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(item.iconColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(9.dp))

            // Center details: Title, Description, Tool Count Pill
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = TextStyle(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = TextStyle(
                        fontSize = 10.5.sp,
                        color = secondaryTextColor,
                        lineHeight = 13.5.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Pill showing available tools count
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = toolCountPillBg,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = "${item.toolCount} tools",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = toolCountPillText
                        ),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Right-facing chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isDark) Color(0xFF64748B) else Color(0xFFCBD5E1),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
