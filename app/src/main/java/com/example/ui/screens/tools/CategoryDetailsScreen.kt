package com.example.ui.screens.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.UtilityHubApp
import com.example.domain.model.ToolCategory
import com.example.domain.model.ToolItem
import com.example.domain.model.ToolRegistry
import com.example.ui.components.AppBackButton
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun CategoryDetailsScreen(
    categoryId: String,
    onBack: () -> Unit,
    onNavigateToRoute: (String) -> Unit
) {
    val repository = remember { UtilityHubApp.instance.repository }
    val scope = rememberCoroutineScope()

    val category = remember(categoryId) {
        ToolCategory.fromId(categoryId)
    }

    var searchQuery by remember { mutableStateOf("") }

    val categoryTools = remember(category) {
        ToolRegistry.getByCategory(category)
    }

    val favoriteIds by repository.getFavoriteToolIds().collectAsState(initial = emptyList())
    val favoriteSet = remember(favoriteIds) { favoriteIds.toSet() }

    val filteredTools = remember(categoryTools, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            categoryTools
        } else {
            categoryTools.filter { tool ->
                tool.name.contains(query, ignoreCase = true) ||
                        tool.description.contains(query, ignoreCase = true) ||
                        tool.keywords.any { it.contains(query, ignoreCase = true) }
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = category.title,
                navigationIcon = { AppBackButton(onBack = onBack) },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(category.accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = category.accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Category Description Header
            Text(
                text = category.shortDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            // Category Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search in ${category.title}...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search category tools",
                        tint = category.accentColor
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = category.accentColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("category_search_input")
            )

            // Responsive 3-Column Tool Grid
            if (filteredTools.isEmpty()) {
                EmptyState(
                    title = "No tools found",
                    description = "No utilities matched \"$searchQuery\" in this category.",
                    icon = Icons.Default.SearchOff
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("category_tools_grid"),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTools, key = { it.id }) { tool ->
                        CategoryToolGridCard(
                            tool = tool,
                            isFavorite = favoriteSet.contains(tool.id),
                            accentColor = category.accentColor,
                            onToolClick = {
                                scope.launch { repository.recordToolUsage(tool.id) }
                                onNavigateToRoute(tool.route)
                            },
                            onFavoriteToggle = {
                                scope.launch {
                                    repository.toggleFavorite(tool.id, favoriteSet.contains(tool.id))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryToolGridCard(
    tool: ToolItem,
    isFavorite: Boolean,
    accentColor: Color,
    onToolClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToolClick() }
            .testTag("tool_card_${tool.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon in styled rounded container
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.name,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Short tool name (max 2 lines, centered, no clipping)
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        lineHeight = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp)
                )
            }

            // Favorite star indicator
            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.TopEnd)
                    .testTag("fav_toggle_${tool.id}")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isFavorite) "Favorited" else "Add to favorites",
                    tint = if (isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
