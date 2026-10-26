package com.example.ui.screens.scanner

import android.content.Context
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.example.UtilityHubApp
import com.example.data.local.entity.ScannedDocumentEntity
import com.example.scanner.ocr.DocumentOcrService
import com.example.ui.theme.BrandBlue
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastScansScreen(
    onBack: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onOpenDocumentViewer: (filePath: String, title: String, docId: Long) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { UtilityHubApp.instance.repository }
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PDF, OCR
    var documentToDelete by remember { mutableStateOf<ScannedDocumentEntity?>(null) }
    var ocrPreviewDoc by remember { mutableStateOf<ScannedDocumentEntity?>(null) }

    val allDocuments by if (searchQuery.isBlank()) {
        repository.getAllScannedDocuments().collectAsState(initial = emptyList())
    } else {
        repository.searchScannedDocuments(searchQuery).collectAsState(initial = emptyList())
    }

    val filteredDocuments = remember(allDocuments, selectedFilter) {
        when (selectedFilter) {
            "PDF" -> allDocuments.filter { !it.pdfFilePath.isNullOrBlank() }
            "OCR" -> allDocuments.filter { !it.ocrExtractedText.isNullOrBlank() }
            else -> allDocuments
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Scanned Documents",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${allDocuments.size} saved documents",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_past_scans")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToScanner,
                        modifier = Modifier.testTag("btn_top_open_scanner")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "New Scan",
                            tint = BrandBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToScanner,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Scan", fontWeight = FontWeight.SemiBold) },
                containerColor = BrandBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_new_scan")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("input_search_past_scans"),
                placeholder = { Text("Search by title, file or OCR text...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${allDocuments.size})") },
                    leadingIcon = if (selectedFilter == "ALL") {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = selectedFilter == "PDF",
                    onClick = { selectedFilter = "PDF" },
                    label = { Text("PDFs") },
                    leadingIcon = {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = selectedFilter == "OCR",
                    onClick = { selectedFilter = "OCR" },
                    label = { Text("With OCR Text") },
                    leadingIcon = {
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Document List or Empty State
            if (filteredDocuments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrandBlue.copy(alpha = 0.1f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching scans found" else "No scanned documents yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try searching with a different keyword" else "Use the camera scanner to capture notebooks, receipts, or PDFs.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onNavigateToScanner,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Scanning")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = filteredDocuments,
                        key = { it.id }
                    ) { doc ->
                        ScannedDocumentItemCard(
                            doc = doc,
                            onOpen = {
                                val targetPath = doc.processedImagePath ?: doc.originalFileName
                                onOpenDocumentViewer(targetPath, doc.title, doc.id)
                            },
                            onShare = {
                                shareScannedDocument(context, doc)
                            },
                            onCopyOcr = {
                                if (!doc.ocrExtractedText.isNullOrBlank()) {
                                    DocumentOcrService.copyToClipboard(context, doc.ocrExtractedText, doc.title)
                                }
                            },
                            onViewOcr = {
                                ocrPreviewDoc = doc
                            },
                            onDelete = {
                                documentToDelete = doc
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (documentToDelete != null) {
        val target = documentToDelete!!
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("Delete Scan?") },
            text = { Text("Are you sure you want to delete \"${target.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            com.example.scanner.engine.ScanStorageManager.deleteScan(context, target.id)
                            documentToDelete = null
                            Toast.makeText(context, "Document deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full OCR Text Dialog
    if (ocrPreviewDoc != null) {
        val target = ocrPreviewDoc!!
        AlertDialog(
            onDismissRequest = { ocrPreviewDoc = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TextFields, contentDescription = null, tint = BrandBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Extracted Text", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    Text(
                        text = target.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        LazyColumn(modifier = Modifier.padding(12.dp)) {
                            item {
                                Text(
                                    text = target.ocrExtractedText ?: "No text detected",
                                    fontSize = 13.5.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        target.ocrExtractedText?.let {
                            DocumentOcrService.copyToClipboard(context, it, target.title)
                        }
                        ocrPreviewDoc = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy All Text")
                }
            },
            dismissButton = {
                TextButton(onClick = { ocrPreviewDoc = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun ScannedDocumentItemCard(
    doc: ScannedDocumentEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopyOcr: () -> Unit,
    onViewOcr: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }
    val formattedDate = remember(doc.createdAtTimestamp) {
        dateFormat.format(Date(doc.createdAtTimestamp))
    }

    val fileSizeText = remember(doc.fileSizeBytes) {
        when {
            doc.fileSizeBytes <= 0L -> ""
            doc.fileSizeBytes < 1024 -> "${doc.fileSizeBytes} B"
            doc.fileSizeBytes < 1024 * 1024 -> "${doc.fileSizeBytes / 1024} KB"
            else -> String.format(Locale.US, "%.1f MB", doc.fileSizeBytes / (1024.0 * 1024.0))
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("card_scanned_doc_${doc.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Thumbnail or Document Icon
                Box(
                    modifier = Modifier
                        .size(72.dp, 92.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val thumbFile = doc.thumbnailPath?.let { File(it) }
                    val displayFile = if (thumbFile != null && thumbFile.exists() && thumbFile.length() > 0L) {
                        thumbFile
                    } else {
                        val full = doc.processedImagePath?.let { File(it) }
                            ?: File(LocalContext.current.filesDir, "scanned_docs/${doc.originalFileName}")
                        if (full.exists() && full.length() > 0L) full else null
                    }

                    if (displayFile != null) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(displayFile)
                                    .size(160, 210)
                                    .crossfade(true)
                                    .build()
                            ),
                            contentDescription = doc.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (!doc.pdfFilePath.isNullOrBlank()) Icons.Default.PictureAsPdf else Icons.Default.Description,
                            contentDescription = null,
                            tint = BrandBlue,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Page count pill on thumbnail
                    if (doc.pageCount > 1) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = "${doc.pageCount}p",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Title and Meta Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedDate,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Badges Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // PDF Badge
                        if (!doc.pdfFilePath.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "PDF",
                                        color = Color(0xFFDC2626),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Size badge
                        if (fileSizeText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = fileSizeText,
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Filter badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BrandBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = doc.filterUsed,
                                fontSize = 10.5.sp,
                                color = BrandBlue,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // OCR snippet preview if available
                    if (!doc.ocrExtractedText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onViewOcr() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TextFields,
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = doc.ocrExtractedText.trim().replace("\n", " "),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "View",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandBlue
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Open / View Button
                TextButton(
                    onClick = onOpen,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View", fontSize = 12.5.sp)
                }

                // Copy OCR Text
                if (!doc.ocrExtractedText.isNullOrBlank()) {
                    TextButton(
                        onClick = onCopyOcr,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Text", fontSize = 12.5.sp)
                    }
                }

                // Share Button
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Delete Button
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun shareScannedDocument(context: Context, doc: ScannedDocumentEntity) {
    val file = doc.pdfFilePath?.let { File(it) }?.takeIf { it.exists() }
        ?: doc.processedImagePath?.let { File(it) }?.takeIf { it.exists() }
        ?: File(context.filesDir, "scanned_docs/${doc.originalFileName}").takeIf { it.exists() }
        ?: doc.thumbnailPath?.let { File(it) }?.takeIf { it.exists() }

    if (file == null) {
        Toast.makeText(context, "No saved document file to share", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = if (file.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/*"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, doc.title)
            if (!doc.ocrExtractedText.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, doc.ocrExtractedText)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Scanned Document"))
    } catch (e: Exception) {
        Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

