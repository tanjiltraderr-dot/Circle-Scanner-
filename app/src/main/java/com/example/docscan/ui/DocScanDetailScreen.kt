package com.example.docscan.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.example.docscan.data.DocScanRepository
import com.example.docscan.model.DocScanDocument
import com.example.ui.theme.BrandBlue
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocScanDetailScreen(
    documentId: String,
    onBack: () -> Unit,
    onOpenPdfViewer: (filePath: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DocScanRepository(context) }
    val scope = rememberCoroutineScope()

    var document by remember { mutableStateOf<DocScanDocument?>(null) }
    var isExportingToFiles by remember { mutableStateOf(false) }
    var isExportingToPhotos by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(documentId) {
        document = repository.getDocumentById(documentId)
    }

    val currentDoc = document

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentDoc?.title ?: "Document Detail",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("doc_detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentDoc != null) {
                        IconButton(onClick = {
                            renameInput = currentDoc.title
                            showRenameDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = BrandBlue)
                        }
                        IconButton(onClick = { repository.shareDocument(currentDoc) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (currentDoc == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandBlue)
            }
        } else {
            val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy • h:mm a", Locale.getDefault()) }
            val formattedDate = remember(currentDoc.createdAt) { dateFormat.format(Date(currentDoc.createdAt)) }

            val fileSizeText = remember(currentDoc.fileSizeBytes) {
                when {
                    currentDoc.fileSizeBytes <= 0L -> ""
                    currentDoc.fileSizeBytes < 1024 -> "${currentDoc.fileSizeBytes} B"
                    currentDoc.fileSizeBytes < 1024 * 1024 -> "${currentDoc.fileSizeBytes / 1024} KB"
                    else -> String.format(Locale.US, "%.1f MB", currentDoc.fileSizeBytes / (1024.0 * 1024.0))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Document Info Summary Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = currentDoc.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formattedDate,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BrandBlue.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "${currentDoc.pageCount} ${if (currentDoc.pageCount == 1) "page" else "pages"}",
                                        color = BrandBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                if (!currentDoc.pdfPath.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFEF4444).copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "PDF Generated",
                                            color = Color(0xFFDC2626),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                if (fileSizeText.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = fileSizeText,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.5.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Primary Actions Row (Open PDF / Share)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!currentDoc.pdfPath.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    onOpenPdfViewer(currentDoc.pdfPath, currentDoc.title)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open PDF", fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = { repository.shareDocument(currentDoc) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Export Options (Save to Files / Save to Photos)
                item {
                    Text(
                        text = "Device Export Options",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            // Save to Documents / Files
                            ListItem(
                                headlineContent = { Text("Save to Documents", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("Exports PDF to Android Documents/DocScan", fontSize = 12.sp) },
                                leadingContent = {
                                    Surface(shape = CircleShape, color = BrandBlue.copy(alpha = 0.1f), modifier = Modifier.size(38.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                },
                                trailingContent = {
                                    if (isExportingToFiles) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        isExportingToFiles = true
                                        val success = repository.exportToDocumentsFolder(currentDoc)
                                        isExportingToFiles = false
                                        Toast.makeText(
                                            context,
                                            if (success) "Saved to Documents/DocScan" else "Failed to save file",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

                            // Save to Photos / Gallery
                            ListItem(
                                headlineContent = { Text("Save to Photos / Gallery", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("Exports page images to Pictures/DocScan", fontSize = 12.sp) },
                                leadingContent = {
                                    Surface(shape = CircleShape, color = Color(0xFF10B981).copy(alpha = 0.1f), modifier = Modifier.size(38.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                },
                                trailingContent = {
                                    if (isExportingToPhotos) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        isExportingToPhotos = true
                                        val success = repository.exportToPhotosGallery(currentDoc)
                                        isExportingToPhotos = false
                                        Toast.makeText(
                                            context,
                                            if (success) "Saved to Gallery / Photos" else "Failed to save photos",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }

                // Scanned Pages Gallery View
                item {
                    Text(
                        text = "Document Pages (${currentDoc.pages.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                items(currentDoc.pages.size) { pageIndex ->
                    val pagePath = currentDoc.pages[pageIndex]
                    val pageFile = File(pagePath)

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp)
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (pageFile.exists()) {
                                    Image(
                                        painter = rememberAsyncImagePainter(pageFile),
                                        contentDescription = "Page ${pageIndex + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.BrokenImage,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Page ${pageIndex + 1} of ${currentDoc.pages.size}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                TextButton(
                                    onClick = {
                                        try {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                pageFile
                                            )
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "image/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No app to open image", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("Full View", color = BrandBlue, fontSize = 12.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog && currentDoc != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename document") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    placeholder = { Text("Document name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            scope.launch {
                                repository.renameDocument(currentDoc.id, renameInput)
                                document = repository.getDocumentById(currentDoc.id)
                                showRenameDialog = false
                                Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Dialog
    if (showDeleteDialog && currentDoc != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete document?") },
            text = { Text("\"${currentDoc.title}\" and all its pages will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteDocument(currentDoc.id)
                            showDeleteDialog = false
                            Toast.makeText(context, "Document deleted", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
