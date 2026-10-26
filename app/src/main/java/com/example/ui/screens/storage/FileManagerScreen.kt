package com.example.ui.screens.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.ui.components.AppBackButton
import com.example.ui.components.AppTopBar
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.utils.ManagedFileItem
import com.example.utils.StorageUtils

@Composable
fun FileManagerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var fileList by remember { mutableStateOf(StorageUtils.getAppManagedFiles(context)) }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    var fileToDelete by remember { mutableStateOf<ManagedFileItem?>(null) }
    var fileDetailsToShow by remember { mutableStateOf<ManagedFileItem?>(null) }

    val categories = listOf("ALL" to "All Files", "PDF" to "PDFs", "IMG" to "Images", "OTHER" to "Other")

    val filteredFiles = remember(fileList, selectedCategory, searchQuery) {
        fileList.filter { fileItem ->
            val matchCat = when (selectedCategory) {
                "PDF" -> fileItem.extension == "PDF"
                "IMG" -> fileItem.extension in listOf("PNG", "JPG", "JPEG", "WEBP")
                "OTHER" -> fileItem.extension !in listOf("PDF", "PNG", "JPG", "JPEG", "WEBP")
                else -> true
            }
            val matchQuery = searchQuery.isBlank() || fileItem.name.contains(searchQuery, ignoreCase = true)
            matchCat && matchQuery
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "File Manager",
                navigationIcon = { AppBackButton(onBack = onBack) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search files...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("file_search_input")
            )

            // Category Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { (key, label) ->
                    FilterChip(
                        selected = selectedCategory == key,
                        onClick = { selectedCategory = key },
                        label = { Text(label) },
                        modifier = Modifier.testTag("file_cat_$key")
                    )
                }
            }

            if (filteredFiles.isEmpty()) {
                EmptyState(
                    title = "No files found",
                    description = "PDFs, compressed photos and exports created by Circle Scanner will show here.",
                    icon = Icons.Default.FolderOpen
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("managed_files_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredFiles, key = { it.file.absolutePath }) { item ->
                        FileCard(
                            item = item,
                            onClick = {
                                openFile(context, item)
                            },
                            onShare = {
                                val mime = if (item.extension == "PDF") "application/pdf" else "image/*"
                                StorageUtils.shareFile(context, item.file, mime)
                            },
                            onInfo = { fileDetailsToShow = item },
                            onDelete = { fileToDelete = item }
                        )
                    }
                }
            }
        }
    }

    if (fileToDelete != null) {
        ConfirmDialog(
            title = "Delete File?",
            message = "Are you sure you want to delete ${fileToDelete!!.name}?",
            confirmText = "Delete",
            onConfirm = {
                fileToDelete!!.file.delete()
                fileList = StorageUtils.getAppManagedFiles(context)
                fileToDelete = null
                Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { fileToDelete = null }
        )
    }

    if (fileDetailsToShow != null) {
        AlertDialog(
            onDismissRequest = { fileDetailsToShow = null },
            title = { Text("File Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Name: ${fileDetailsToShow!!.name}", fontWeight = FontWeight.SemiBold)
                    Text("Size: ${fileDetailsToShow!!.formattedSize}")
                    Text("Format: ${fileDetailsToShow!!.extension}")
                    Text("Modified: ${fileDetailsToShow!!.lastModified}")
                    Text("Path: ${fileDetailsToShow!!.file.absolutePath}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { fileDetailsToShow = null }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun FileCard(
    item: ManagedFileItem,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    val icon: ImageVector = when (item.extension) {
        "PDF" -> Icons.Default.PictureAsPdf
        "PNG", "JPG", "JPEG", "WEBP" -> Icons.Default.Image
        else -> Icons.Default.InsertDriveFile
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("file_item_${item.name}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.formattedSize} · ${item.lastModified}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onInfo, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun openFile(context: Context, item: ManagedFileItem) {
    try {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", item.file)
        val mime = if (item.extension == "PDF") "application/pdf" else "image/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
    }
}
