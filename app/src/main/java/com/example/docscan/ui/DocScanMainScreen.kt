package com.example.docscan.ui

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.docscan.data.DocScanRepository
import com.example.docscan.model.DocScanDocument
import com.example.docscan.model.DocScanSettings
import com.example.docscan.model.DocScannerMode
import com.example.ui.theme.BrandBlue
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocScanMainScreen(
    onBack: () -> Unit,
    onStartScan: () -> Unit,
    onOpenDocumentDetail: (documentId: String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DocScanRepository(context) }
    val scope = rememberCoroutineScope()

    // Seed sample documents on first entry
    LaunchedEffect(Unit) {
        repository.seedSampleDocumentsIfEmpty()
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Scan, 1: Library, 2: Settings
    val settings by repository.settings.collectAsState()
    val allDocuments by repository.allDocuments.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BrandBlue,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "DocScan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("docscan_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Scan") },
                    label = { Text("Scan") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBlue,
                        selectedTextColor = BrandBlue,
                        indicatorColor = BrandBlue.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (allDocuments.isNotEmpty()) {
                                    Badge { Text("${allDocuments.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.FolderCopy, contentDescription = "Library")
                        }
                    },
                    label = { Text("Library") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBlue,
                        selectedTextColor = BrandBlue,
                        indicatorColor = BrandBlue.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBlue,
                        selectedTextColor = BrandBlue,
                        indicatorColor = BrandBlue.copy(alpha = 0.15f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DocScanTabScan(
                    settings = settings,
                    recentDocuments = allDocuments.take(5),
                    onStartScan = onStartScan,
                    onOpenDocument = onOpenDocumentDetail,
                    onGoToSettings = { selectedTab = 2 }
                )
                1 -> DocScanTabLibrary(
                    documents = allDocuments,
                    onOpenDocument = onOpenDocumentDetail,
                    onStartScan = onStartScan,
                    onDeleteDocument = { id ->
                        scope.launch {
                            repository.deleteDocument(id)
                            Toast.makeText(context, "Document deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShareDocument = { doc ->
                        repository.shareDocument(doc)
                    }
                )
                2 -> DocScanTabSettings(
                    settings = settings,
                    onUpdateSettings = { newSettings ->
                        scope.launch { repository.updateSettings(newSettings) }
                    },
                    onResetSettings = {
                        scope.launch {
                            repository.resetSettings()
                            Toast.makeText(context, "Settings reset to default", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteAll = {
                        scope.launch {
                            repository.deleteAllDocuments()
                            Toast.makeText(context, "All scans cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 1. Scan Tab (Ported from scan.page.html / scan.page.ts)
// ---------------------------------------------------------------------------
@Composable
private fun DocScanTabScan(
    settings: DocScanSettings,
    recentDocuments: List<DocScanDocument>,
    onStartScan: () -> Unit,
    onOpenDocument: (id: String) -> Unit,
    onGoToSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Scan Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = BrandBlue.copy(alpha = 0.12f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Scan Documents",
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                            Text(
                                text = "Point your camera to auto-detect edges & dewarp",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Settings Summary Chips (Matching DocScan scan.page.ts)
                    Text(
                        text = "Scan Configuration",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DocScanPill(text = "Up to ${settings.pageLimit}p")
                        DocScanPill(text = "${settings.imageQuality}% quality")
                        DocScanPill(text = if (settings.generatePdf) "PDF included" else "Images only")
                        DocScanPill(text = if (settings.autoEdgeDetection) "Auto-Crop" else "Manual Crop")
                        if (settings.saveToFiles) DocScanPill(text = "Auto-Save")
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary Start Scan Button
                    Button(
                        onClick = onStartScan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("docscan_start_scan_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Scan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // Recent Scans Header
        if (recentDocuments.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Scans",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    TextButton(onClick = onGoToSettings) {
                        Text("Configure", color = BrandBlue, fontSize = 13.sp)
                    }
                }
            }

            items(recentDocuments, key = { it.id }) { doc ->
                DocScanItemCard(
                    doc = doc,
                    onClick = { onOpenDocument(doc.id) }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Library Tab (Ported from library.page.html / library.page.ts)
// ---------------------------------------------------------------------------
@Composable
private fun DocScanTabLibrary(
    documents: List<DocScanDocument>,
    onOpenDocument: (id: String) -> Unit,
    onStartScan: () -> Unit,
    onDeleteDocument: (id: String) -> Unit,
    onShareDocument: (doc: DocScanDocument) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var documentToDelete by remember { mutableStateOf<DocScanDocument?>(null) }

    val filtered = remember(documents, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) documents else documents.filter { it.title.lowercase().contains(q) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by document title...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("docscan_library_search")
        )

        // List Header / Count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filtered.size} documents",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (filtered.isEmpty()) {
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
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = BrandBlue,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No documents match '$searchQuery'" else "No documents scanned yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scanned documents and sample PDFs will appear here.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onStartScan, colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan First Document")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { doc ->
                    DocScanItemCard(
                        doc = doc,
                        onClick = { onOpenDocument(doc.id) },
                        onShare = { onShareDocument(doc) },
                        onDelete = { documentToDelete = doc }
                    )
                }
            }
        }
    }

    // Delete Confirmation
    if (documentToDelete != null) {
        val target = documentToDelete!!
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("Delete document?") },
            text = { Text("\"${target.title}\" and all its pages will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDocument(target.id)
                        documentToDelete = null
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
}

// ---------------------------------------------------------------------------
// 3. Settings Tab (Ported from settings.page.html / settings.page.ts)
// ---------------------------------------------------------------------------
@Composable
private fun DocScanTabSettings(
    settings: DocScanSettings,
    onUpdateSettings: (DocScanSettings) -> Unit,
    onResetSettings: () -> Unit,
    onDeleteAll: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Scanner Settings
        item {
            Text(
                text = "Scanner Defaults",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = BrandBlue
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Page Limit Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Page Limit", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                            Text("Max pages per document", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = "${settings.pageLimit} pages",
                            fontWeight = FontWeight.Bold,
                            color = BrandBlue,
                            fontSize = 14.sp
                        )
                    }
                    Slider(
                        value = settings.pageLimit.toFloat(),
                        onValueChange = { onUpdateSettings(settings.copy(pageLimit = it.toInt())) },
                        valueRange = 1f..100f,
                        steps = 98,
                        colors = SliderDefaults.colors(thumbColor = BrandBlue, activeTrackColor = BrandBlue)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Image Quality Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Image Quality", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                            Text("Higher quality produces larger file sizes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = "${settings.imageQuality}%",
                            fontWeight = FontWeight.Bold,
                            color = BrandBlue,
                            fontSize = 14.sp
                        )
                    }
                    Slider(
                        value = settings.imageQuality.toFloat(),
                        onValueChange = { onUpdateSettings(settings.copy(imageQuality = it.toInt())) },
                        valueRange = 20f..100f,
                        steps = 15,
                        colors = SliderDefaults.colors(thumbColor = BrandBlue, activeTrackColor = BrandBlue)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Generate PDF Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Generate PDF", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                            Text("Combine scanned pages into a single PDF document", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.generatePdf,
                            onCheckedChange = { onUpdateSettings(settings.copy(generatePdf = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandBlue)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Automatic Edge Detection & Cropping Setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Edge Detection & Crop", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                            Text(
                                text = if (settings.autoEdgeDetection)
                                    "Automatically detect paper edges and crop upon capture. (Enabled)"
                                else
                                    "Opens manual crop editor with adjustable corner handles for each scan. (Manual Crop)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoEdgeDetection,
                            onCheckedChange = { onUpdateSettings(settings.copy(autoEdgeDetection = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandBlue)
                        )
                    }
                }
            }
        }

        // Section: Auto-Save Destinations
        item {
            Text(
                text = "Auto-Save Destinations",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = BrandBlue
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Save to Documents
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Save to Documents", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                            Text("Automatically copy scans to Android Documents/DocScan", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.saveToFiles,
                            onCheckedChange = { onUpdateSettings(settings.copy(saveToFiles = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandBlue)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Save to Photos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Save to Photos / Gallery", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                            Text("Automatically save individual page images to Gallery", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.saveToPhotos,
                            onCheckedChange = { onUpdateSettings(settings.copy(saveToPhotos = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandBlue)
                        )
                    }
                }
            }
        }

        // Section: Danger Zone / Actions
        item {
            Text(
                text = "Management",
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
                    // Reset Settings Button
                    TextButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = BrandBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Settings to Default", color = BrandBlue, fontWeight = FontWeight.SemiBold)
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

                    // Delete All Documents Button
                    TextButton(
                        onClick = { showDeleteAllDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete All Scanned Documents", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset scanner settings?") },
            text = { Text("All scanner options will be restored to their factory defaults.") },
            confirmButton = {
                TextButton(onClick = {
                    onResetSettings()
                    showResetDialog = false
                }) {
                    Text("Reset", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete all documents?") },
            text = { Text("Every scanned page and PDF will be permanently removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAll()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Reusable Document Card Component (Matching DocScan document-item.component)
// ---------------------------------------------------------------------------
@Composable
private fun DocScanItemCard(
    doc: DocScanDocument,
    onClick: () -> Unit,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }
    val formattedDate = remember(doc.createdAt) { dateFormat.format(Date(doc.createdAt)) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("docscan_item_${doc.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(54.dp, 68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val thumbPath = doc.firstPageThumbnail
                if (thumbPath != null && File(thumbPath).exists()) {
                    Image(
                        painter = rememberAsyncImagePainter(File(thumbPath)),
                        contentDescription = doc.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = BrandBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
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

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Page Count Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = BrandBlue.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${doc.pageCount} ${if (doc.pageCount == 1) "page" else "pages"}",
                            fontSize = 10.sp,
                            color = BrandBlue,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // PDF Badge
                    if (!doc.pdfPath.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "PDF",
                                fontSize = 10.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Quick Actions
            if (onShare != null) {
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(17.dp))
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DocScanPill(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
