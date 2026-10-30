package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.FileProvider
import com.example.UtilityHubApp
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.NoteEntity
import com.example.domain.model.ToolCategory
import com.example.domain.model.ToolRegistry
import com.example.utils.DocumentProcessor
import com.example.utils.ImageUtils
import com.example.utils.PdfUtils
import com.example.utils.StorageUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ui.navigation.Screen
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.DarkBlue
import com.example.ui.theme.ReadingBackground
import com.example.ui.theme.ReadingBorder
import com.example.ui.theme.ReadingSurface
import com.example.ui.theme.ReadingTextPrimary
import com.example.ui.theme.ReadingTextSecondary
import com.example.ui.theme.ReadingBlue
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToScanner: () -> Unit,
    onNavigateToCategory: (String) -> Unit,
    onNavigateToTool: (String) -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToPro: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val repository = remember { UtilityHubApp.instance.repository }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }

    val favoriteIds by repository.getFavoriteToolIds().collectAsState(initial = emptyList())
    val historyItems by repository.getAllHistory().collectAsState(initial = emptyList())
    val recentTools by repository.getRecentTools().collectAsState(initial = emptyList())

    var previewItem by remember { mutableStateOf<HistoryEntity?>(null) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var missingItem by remember { mutableStateOf<HistoryEntity?>(null) }

    // Quick Categories Workflow States
    var selectedImportUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showImportImagesDialog by remember { mutableStateOf(false) }
    var isImportProcessing by remember { mutableStateOf(false) }

    var importedFileUri by remember { mutableStateOf<Uri?>(null) }
    var importedFileName by remember { mutableStateOf("") }
    var importedFileIsPdf by remember { mutableStateOf(false) }
    var showImportFileDialog by remember { mutableStateOf(false) }

    var showExtractTextChoiceDialog by remember { mutableStateOf(false) }
    var isOcrExtracting by remember { mutableStateOf(false) }
    var ocrResultText by remember { mutableStateOf("") }
    var showOcrResultDialog by remember { mutableStateOf(false) }

    val googleScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanResult = com.example.scanner.engine.GoogleDocumentScannerHelper.parseResult(result.data)
            if (scanResult != null) {
                scope.launch {
                    val saved = com.example.scanner.engine.GoogleDocumentScannerHelper.persistScanResult(
                        context, scanResult
                    )
                    if (saved != null) {
                        repository.addHistory("doc_scanner", saved.documentTitle, "scan_id:${saved.scanId}|${saved.primaryImageFile.name}")
                        android.widget.Toast.makeText(context, "Scan saved to history", android.widget.Toast.LENGTH_SHORT).show()
                        onNavigateToTool(Screen.DocumentViewer.createRoute(saved.primaryImageFile.absolutePath, saved.documentTitle, saved.scanId))
                    }
                }
            }
        }
    }

    // Activity Result Launchers
    val imageImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImportUris = uris
            showImportImagesDialog = true
        }
    }

    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val name = resolveFileName(context, uri)
            importedFileUri = uri
            importedFileName = name
            importedFileIsPdf = name.endsWith(".pdf", ignoreCase = true)
            showImportFileDialog = true
        }
    }

    val ocrGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isOcrExtracting = true
                val bmp = withContext(Dispatchers.IO) {
                    ImageUtils.decodeSampledBitmapFromUri(context, uri, 1600, 1600)
                }
                if (bmp != null) {
                    val text = DocumentProcessor.extractTextReal(bmp)
                    ocrResultText = text
                    showOcrResultDialog = true
                } else {
                    Toast.makeText(context, "Could not load selected image for OCR", Toast.LENGTH_SHORT).show()
                }
                isOcrExtracting = false
            }
        }
    }

    fun handleScanItemClick(item: HistoryEntity) {
        val id = item.toolId.lowercase()
        val title = item.title.lowercase()
        if (id.contains("qr") || id.contains("barcode") || title.contains("qr") || title.contains("barcode")) {
            val format = if (id.contains("barcode") || title.contains("barcode")) "CODE_128" else "QR_CODE"
            onNavigateToTool(Screen.ScanResult.createRoute(item.details, format))
        } else {
            val resolved = com.example.utils.ScanThumbnailHelper.resolveFile(context, item)
            val path = resolved?.absolutePath ?: item.details
            val docId = com.example.scanner.engine.ScanStorageManager.extractScanId(item.details) ?: -1L
            onNavigateToTool(Screen.DocumentViewer.createRoute(path, item.title, docId))
        }
    }

    val scanHistoryItems = remember(historyItems) {
        historyItems.filter { item ->
            val id = item.toolId.lowercase()
            val title = item.title.lowercase()
            (id == "doc_scanner" || id == "qr_scanner" || id == "barcode_scanner" ||
             id.contains("pdf") || id.contains("scan") || id.contains("image") || id.contains("img_") ||
             title.contains("scanned") || title.contains("pdf") || title.contains("photo") || title.contains("scan") || title.contains("document")) &&
            !title.contains("clicked") && !title.contains("opened") && !title.contains("viewed") && !title.contains("visited")
        }
    }

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
    val subtleCardBg = when {
        isDark -> Color(0xFF1E293B)
        isReading -> Color(0xFFF3EDE3)
        else -> Color(0xFFF8FAFC)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
            .statusBarsPadding()
            .padding(top = 18.dp)
    ) {
            // 1. Header (Top Bar matching reference)
            HomeHeader(
                isDark = isDark,
                isReading = isReading,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                cardBorder = cardBorder,
                onNavigateToPro = onNavigateToPro,
                onNavigateToSettings = onNavigateToSettings
            )

            // 2. Search Bar
            HomeSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                searchBarBg = searchBarBg,
                searchBarBorder = searchBarBorder,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor
            )

            if (searchQuery.isNotBlank()) {
                // Live Search Results
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_search_results"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults, key = { it.id }) { tool ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    scope.launch { repository.recordToolUsage(tool.id) }
                                    onNavigateToTool(tool.route)
                                }
                                .testTag("home_result_${tool.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = subtleCardBg),
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
                                        modifier = Modifier.size(22.dp)
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
            } else {
                // Exact Reference Home Screen Content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_dashboard_scroll"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // 3. Scan Anything Hero Card
                    item {
                        ScanAnythingHeroCard(
                            isDark = isDark,
                            isReading = isReading,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            onNavigateToQrScan = {
                                onNavigateToTool(Screen.Scanner.createRoute("QR_BARCODE"))
                            },
                            onLaunchGoogleScanner = {
                                val activity = context as? android.app.Activity
                                if (activity != null) {
                                    com.example.scanner.engine.GoogleDocumentScannerHelper.launch(
                                        activity = activity,
                                        launcher = googleScannerLauncher,
                                        onFailure = {
                                            onNavigateToTool(Screen.Scanner.createRoute("DOCUMENT_PDF"))
                                        }
                                    )
                                } else {
                                    onNavigateToTool(Screen.Scanner.createRoute("DOCUMENT_PDF"))
                                }
                            }
                        )
                    }

                    // 4. Quick Categories
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SectionHeaderRow(
                                title = "Quick Categories",
                                primaryTextColor = primaryTextColor,
                                isDark = isDark,
                                onSeeAllClick = onNavigateToTools
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // First row: 1. Smart Scan, 2. PDF Tools, 3. Import Images, 4. Import Files
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                QuickCategoryCircleItem(
                                    title = "Smart Scan",
                                    icon = Icons.Default.DocumentScanner,
                                    iconColor = Color(0xFF2563EB),
                                    isDark = isDark,
                                    isReading = isReading,
                                    primaryTextColor = primaryTextColor,
                                    onClick = { onNavigateToTool(Screen.DocumentScanner.route) },
                                    testTag = "quick_category_smart_scan",
                                    modifier = Modifier.weight(1f)
                                )
                                QuickCategoryCircleItem(
                                    title = "PDF Tools",
                                    icon = Icons.Default.PictureAsPdf,
                                    iconColor = Color(0xFFEF4444),
                                    isDark = isDark,
                                    isReading = isReading,
                                    primaryTextColor = primaryTextColor,
                                    onClick = { onNavigateToTool(Screen.PdfTools.createRoute(0)) },
                                    testTag = "quick_category_pdf_tools",
                                    modifier = Modifier.weight(1f)
                                )
                                QuickCategoryCircleItem(
                                    title = "Import Images",
                                    icon = Icons.Default.AddPhotoAlternate,
                                    iconColor = Color(0xFF10B981),
                                    isDark = isDark,
                                    isReading = isReading,
                                    primaryTextColor = primaryTextColor,
                                    onClick = { imageImportLauncher.launch("image/*") },
                                    testTag = "quick_category_import_images",
                                    modifier = Modifier.weight(1f)
                                )
                                QuickCategoryCircleItem(
                                    title = "Import Files",
                                    icon = Icons.Default.FolderOpen,
                                    iconColor = Color(0xFFF59E0B),
                                    isDark = isDark,
                                    isReading = isReading,
                                    primaryTextColor = primaryTextColor,
                                    onClick = { fileImportLauncher.launch("*/*") },
                                    testTag = "quick_category_import_files",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Second row: 5. ID Cards, 6. Extract Text, 7. All (+ balanced empty 4th column)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                QuickCategoryCircleItem(
                                    title = "ID Cards",
                                    icon = Icons.Default.Badge,
                                    iconColor = Color(0xFF6366F1),
                                    isDark = isDark,
                                    isReading = isReading,
                                    primaryTextColor = primaryTextColor,
                                    onClick = { onNavigateToTool(Screen.Scanner.createRoute("ID_CARD")) },
                                    testTag = "quick_category_id_cards",
                                    modifier = Modifier.weight(1f)
                                )
                                QuickCategoryCircleItem(
                                    title = "Extract Text",
                                    icon = Icons.Default.TextFields,
                                    iconColor = Color(0xFF0EA5E9),
                                    isDark = isDark,
                                    isReading = isReading,
                                    primaryTextColor = primaryTextColor,
                                    onClick = { showExtractTextChoiceDialog = true },
                                    testTag = "quick_category_extract_text",
                                    modifier = Modifier.weight(1f)
                                )
                                QuickCategoryCircleItem(
                                    title = "All",
                                    icon = Icons.Default.GridView,
                                    iconColor = Color(0xFF64748B),
                                    isDark = isDark,
                                    isReading = isReading,
                                    primaryTextColor = primaryTextColor,
                                    onClick = onNavigateToTools,
                                    testTag = "quick_category_all",
                                    modifier = Modifier.weight(1f)
                                )
                                QuickCategoryCircleItem(
                                    title = "Past Scans",
                                    icon = Icons.Default.FolderSpecial,
                                    iconColor = Color(0xFF8B5CF6),
                                    isDark = isDark,
                                    isReading = isReading,
                                    primaryTextColor = primaryTextColor,
                                    onClick = { onNavigateToTool(Screen.PastScans.route) },
                                    testTag = "quick_category_past_scans",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 5. Scan History
                    item {
                        Column {
                            SectionHeaderRow(
                                title = "Scan History",
                                primaryTextColor = primaryTextColor,
                                isDark = isDark,
                                onSeeAllClick = onNavigateToHistory
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            if (scanHistoryItems.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val displayItems = scanHistoryItems.take(4)
                                    displayItems.forEach { item ->
                                        ScanHistoryRow(
                                            item = item,
                                            cardBg = cardBg,
                                            cardBorder = cardBorder,
                                            primaryTextColor = primaryTextColor,
                                            secondaryTextColor = secondaryTextColor,
                                            isDark = isDark,
                                            onClick = { handleScanItemClick(item) }
                                        )
                                    }
                                }
                            } else {
                                EmptyStateCard(
                                    message = "Your scans will appear here. Tap the Camera button to scan your first document.",
                                    subtleCardBg = subtleCardBg,
                                    cardBorder = cardBorder,
                                    secondaryTextColor = secondaryTextColor
                                )
                            }
                        }
                    }

                    // 7. Continue Where You Left Off
                    item {
                        val lastToolId = recentTools.firstOrNull()?.toolId ?: "tool_qr_scanner"
                        val lastTool = ToolRegistry.getById(lastToolId)
                        val lastRoute = lastTool?.route ?: Screen.Scanner.createRoute("QR_BARCODE")

                        val continueBg = when {
                            isDark -> Color(0xFF1E293B)
                            isReading -> ReadingSurface
                            else -> Color(0xFFF0F6FF)
                        }
                        val continueBorder = when {
                            isDark -> Color(0xFF3B82F6).copy(alpha = 0.35f)
                            isReading -> ReadingBorder
                            else -> Color(0xFFDBEAFE)
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = continueBg,
                            border = BorderStroke(1.dp, continueBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    scope.launch { repository.recordToolUsage(lastToolId) }
                                    onNavigateToTool(lastRoute)
                                }
                                .testTag("continue_last_operation_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BrandBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Continue Where You Left Off",
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryTextColor
                                        )
                                    )
                                    Text(
                                        text = "Resume your last operation",
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            color = secondaryTextColor
                                        )
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                    }
                }
            }
        }
    }

    // Scan Detail Preview Dialog
    if (previewItem != null && previewFile != null) {
        ScanDetailPreviewDialog(
            item = previewItem!!,
            file = previewFile!!,
            isDark = isDark,
            onDismiss = {
                previewItem = null
                previewFile = null
            },
            onOpenFile = {
                try {
                    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", previewFile!!)
                    val mime = if (previewFile!!.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/*"
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
                }
            },
            onShareFile = {
                try {
                    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", previewFile!!)
                    val mime = if (previewFile!!.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/*"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Scanned Document"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Missing File Dialog
    if (missingItem != null) {
        AlertDialog(
            onDismissRequest = { missingItem = null },
            title = {
                Text(
                    text = "Scan File Unavailable",
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "The file '${missingItem!!.details}' is no longer available in local storage. It may have been moved or deleted.\n\nWould you like to remove this entry from your scan history?",
                    color = secondaryTextColor,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = missingItem!!.id
                        scope.launch { repository.deleteHistory(toDelete) }
                        missingItem = null
                        Toast.makeText(context, "History entry removed", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove Entry", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { missingItem = null }) {
                    Text("Keep in History")
                }
            }
        )
    }

    // Import Images Workflow Dialog
    if (showImportImagesDialog && selectedImportUris.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                if (!isImportProcessing) showImportImagesDialog = false
            },
            containerColor = cardBg,
            titleContentColor = primaryTextColor,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Import Images",
                            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                        )
                        Text(
                            text = "${selectedImportUris.size} image${if (selectedImportUris.size > 1) "s" else ""} selected",
                            style = TextStyle(fontSize = 12.sp, color = secondaryTextColor)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isImportProcessing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = BrandBlue,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Processing images...",
                                fontSize = 13.5.sp,
                                color = primaryTextColor
                            )
                        }
                    } else {
                        Text(
                            text = "Choose how to process the selected image(s):",
                            fontSize = 13.sp,
                            color = secondaryTextColor
                        )

                        // Option 1: Convert to PDF
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = subtleCardBg,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch {
                                        isImportProcessing = true
                                        val bitmaps = withContext(Dispatchers.IO) {
                                            selectedImportUris.mapNotNull { uri ->
                                                ImageUtils.decodeSampledBitmapFromUri(context, uri, 1400, 1400)
                                            }
                                        }
                                        if (bitmaps.isNotEmpty()) {
                                            val timeStr = SimpleDateFormat("dd-MM-yyyy_HHmm", Locale.getDefault()).format(Date())
                                            val pdfFile = withContext(Dispatchers.IO) {
                                                PdfUtils.imagesToPdf(context, bitmaps, "Document_$timeStr.pdf")
                                            }
                                            if (pdfFile != null) {
                                                repository.addHistory(
                                                    "pdf_tools",
                                                    "Imported PDF (${bitmaps.size} pages)",
                                                    pdfFile.name
                                                )
                                                previewItem = HistoryEntity(
                                                    toolId = "pdf_tools",
                                                    title = "Imported PDF (${bitmaps.size} pages)",
                                                    details = pdfFile.name,
                                                    timestamp = System.currentTimeMillis()
                                                )
                                                previewFile = pdfFile
                                                Toast.makeText(context, "PDF created successfully!", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Could not load images", Toast.LENGTH_SHORT).show()
                                        }
                                        isImportProcessing = false
                                        showImportImagesDialog = false
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Convert to PDF Document", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = primaryTextColor)
                                    Text("Create single PDF from all ${selectedImportUris.size} images", fontSize = 11.5.sp, color = secondaryTextColor)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Option 2: Open in PDF Tools
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = subtleCardBg,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showImportImagesDialog = false
                                    onNavigateToTool(Screen.PdfTools.createRoute(0))
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Open in PDF Tools", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = primaryTextColor)
                                    Text("Images to PDF, compress, and page tools", fontSize = 11.5.sp, color = secondaryTextColor)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Option 3: Extract Text (OCR)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = subtleCardBg,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showImportImagesDialog = false
                                    scope.launch {
                                        isOcrExtracting = true
                                        val firstUri = selectedImportUris.first()
                                        val bmp = withContext(Dispatchers.IO) {
                                            ImageUtils.decodeSampledBitmapFromUri(context, firstUri, 1600, 1600)
                                        }
                                        if (bmp != null) {
                                            val text = DocumentProcessor.extractTextReal(bmp)
                                            ocrResultText = text
                                            showOcrResultDialog = true
                                        } else {
                                            Toast.makeText(context, "Could not load image for OCR", Toast.LENGTH_SHORT).show()
                                        }
                                        isOcrExtracting = false
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.TextFields, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Extract Text (OCR)", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = primaryTextColor)
                                    Text("Run on-device ML Kit OCR on selected image", fontSize = 11.5.sp, color = secondaryTextColor)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                if (!isImportProcessing) {
                    TextButton(onClick = { showImportImagesDialog = false }) {
                        Text("Cancel", color = secondaryTextColor)
                    }
                }
            }
        )
    }

    // Import File Workflow Dialog
    if (showImportFileDialog && importedFileUri != null) {
        AlertDialog(
            onDismissRequest = { showImportFileDialog = false },
            containerColor = cardBg,
            titleContentColor = primaryTextColor,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (importedFileIsPdf) Icons.Default.PictureAsPdf else Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = if (importedFileIsPdf) Color(0xFFEF4444) else Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "File Imported",
                            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                        )
                        Text(
                            text = importedFileName,
                            style = TextStyle(fontSize = 12.sp, color = secondaryTextColor),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (importedFileIsPdf)
                            "Select an action for this PDF document:"
                        else
                            "Select an action for this file:",
                        fontSize = 13.sp,
                        color = secondaryTextColor
                    )

                    if (importedFileIsPdf) {
                        // Option 1: Preview & Share PDF
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = subtleCardBg,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch {
                                        val destDir = File(context.filesDir, "pdfs")
                                        if (!destDir.exists()) destDir.mkdirs()
                                        val destFile = File(destDir, importedFileName)
                                        withContext(Dispatchers.IO) {
                                            try {
                                                context.contentResolver.openInputStream(importedFileUri!!)?.use { input ->
                                                    destFile.outputStream().use { output -> input.copyTo(output) }
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        if (destFile.exists()) {
                                            repository.addHistory("pdf_tools", "Imported PDF: $importedFileName", destFile.name)
                                            previewItem = HistoryEntity(
                                                toolId = "pdf_tools",
                                                title = "Imported PDF: $importedFileName",
                                                details = destFile.name,
                                                timestamp = System.currentTimeMillis()
                                            )
                                            previewFile = destFile
                                        }
                                        showImportFileDialog = false
                                    }
                                }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Preview & Share", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = primaryTextColor)
                                    Text("Inspect PDF pages, open or share file", fontSize = 11.5.sp, color = secondaryTextColor)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Option 2: Open in PDF Tools
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = subtleCardBg,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showImportFileDialog = false
                                    onNavigateToTool(Screen.PdfTools.createRoute(0))
                                }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Open in PDF Tools", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = primaryTextColor)
                                    Text("Merge, split, compress, or convert to images", fontSize = 11.5.sp, color = secondaryTextColor)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Option 3: Open in File Manager
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = subtleCardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showImportFileDialog = false
                                onNavigateToTool(Screen.FileManager.route)
                            }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Open in File Manager", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = primaryTextColor)
                                Text("Browse and manage all app files", fontSize = 11.5.sp, color = secondaryTextColor)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImportFileDialog = false }) {
                    Text("Dismiss", color = secondaryTextColor)
                }
            }
        )
    }

    // Extract Text (OCR) Choice Dialog
    if (showExtractTextChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showExtractTextChoiceDialog = false },
            containerColor = cardBg,
            titleContentColor = primaryTextColor,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0EA5E9).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = null,
                            tint = Color(0xFF0EA5E9),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Extract Text (OCR)",
                            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                        )
                        Text(
                            text = "On-Device Google ML Kit Text Recognition",
                            style = TextStyle(fontSize = 11.5.sp, color = secondaryTextColor)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Choose how you want to extract text:",
                        fontSize = 13.sp,
                        color = secondaryTextColor
                    )

                    // Option 1: Camera
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = subtleCardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showExtractTextChoiceDialog = false
                                onNavigateToTool(Screen.Scanner.createRoute("SCANNER"))
                            }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Scan Document with Camera", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = primaryTextColor)
                                Text("Capture document and extract text instantly", fontSize = 11.5.sp, color = secondaryTextColor)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(16.dp))
                        }
                    }

                    // Option 2: Gallery
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = subtleCardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showExtractTextChoiceDialog = false
                                ocrGalleryLauncher.launch("image/*")
                            }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Select Image from Gallery", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = primaryTextColor)
                                Text("Pick existing photo or screenshot for OCR", fontSize = 11.5.sp, color = secondaryTextColor)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExtractTextChoiceDialog = false }) {
                    Text("Cancel", color = secondaryTextColor)
                }
            }
        )
    }

    // OCR Result Dialog
    if (showOcrResultDialog) {
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showOcrResultDialog = false },
            containerColor = cardBg,
            titleContentColor = primaryTextColor,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0EA5E9).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = null,
                            tint = Color(0xFF0EA5E9),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Extracted Text (OCR)",
                            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                        )
                        Text(
                            text = "Real on-device ML Kit recognition",
                            style = TextStyle(fontSize = 11.5.sp, color = secondaryTextColor)
                        )
                    }
                    IconButton(onClick = { showOcrResultDialog = false }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = secondaryTextColor)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = subtleCardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 240.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = ocrResultText,
                                style = TextStyle(
                                    fontSize = 13.5.sp,
                                    color = primaryTextColor,
                                    lineHeight = 19.sp
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val timeStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
                                repository.insertNote(
                                    NoteEntity(
                                        title = "OCR Extracted ($timeStr)",
                                        content = ocrResultText
                                    )
                                )
                                Toast.makeText(context, "Saved to Notes!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save to Notes")
                    }

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(ocrResultText))
                            Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }
                }
            },
            dismissButton = {
                IconButton(
                    onClick = {
                        try {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, ocrResultText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Extracted Text"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to share text", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = secondaryTextColor)
                }
            }
        )
    }

    // OCR Loading Dialog
    if (isOcrExtracting) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = cardBg,
            shape = RoundedCornerShape(16.dp),
            title = null,
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = BrandBlue,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Extracting Text...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = primaryTextColor
                        )
                        Text(
                            text = "Reading image with Google ML Kit",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}

// ---------------------------------------------------------------------------
// 1. Header (Logo, Circle Scanner, Pro button, Profile button)
// ---------------------------------------------------------------------------
@Composable
private fun HomeHeader(
    isDark: Boolean,
    isReading: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    cardBorder: Color,
    onNavigateToPro: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo (Blue rounded square with 4 white rounded clover lobes)
        UtilityHubLogo()

        Spacer(modifier = Modifier.width(10.dp))

        // Title and Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Circle Scanner",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
            )
            Text(
                text = "Smart Document & Tools",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
            )
        }

        // Pale-blue Pro pill button
        val proBg = when {
            isDark -> Color(0xFF2563EB).copy(alpha = 0.2f)
            isReading -> Color(0xFFE8DFCD)
            else -> Color(0xFFEFF6FF)
        }
        val proBorder = BorderStroke(0.8.dp, when {
            isDark -> Color(0xFF3B82F6).copy(alpha = 0.4f)
            isReading -> ReadingBorder
            else -> Color(0xFFDBEAFE)
        })
        val proTint = when {
            isDark -> Color(0xFF93C5FD)
            isReading -> ReadingBlue
            else -> Color(0xFF2563EB)
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = proBg,
            border = proBorder,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { onNavigateToPro() }
                .testTag("home_pro_pill_action")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = "Pro",
                    tint = proTint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Pro",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = proTint
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Circular Profile Icon (Option A: Header -> Profile | Bottom Nav -> Settings)
        val profileBg = when {
            isDark -> Color(0xFF1E293B)
            isReading -> ReadingSurface
            else -> Color(0xFFF8FAFC)
        }
        val profileIconTint = if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)

        Surface(
            shape = CircleShape,
            color = profileBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable { onNavigateToSettings() }
                .testTag("home_profile_action")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "Profile",
                    tint = profileIconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Modern 4-Lobe Clover Logo
// ---------------------------------------------------------------------------
@Composable
private fun UtilityHubLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BrandBlue),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val petalSize = size.width * 0.42f
            val cornerRadius = CornerRadius(petalSize * 0.42f, petalSize * 0.42f)
            val offset = size.width * 0.05f

            // Top-left lobe
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(offset, offset),
                size = Size(petalSize, petalSize),
                cornerRadius = cornerRadius
            )
            // Top-right lobe
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(size.width - petalSize - offset, offset),
                size = Size(petalSize, petalSize),
                cornerRadius = cornerRadius
            )
            // Bottom-left lobe
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(offset, size.height - petalSize - offset),
                size = Size(petalSize, petalSize),
                cornerRadius = cornerRadius
            )
            // Bottom-right lobe
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(size.width - petalSize - offset, size.height - petalSize - offset),
                size = Size(petalSize, petalSize),
                cornerRadius = cornerRadius
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Search Bar
// ---------------------------------------------------------------------------
@Composable
private fun HomeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    searchBarBg: Color,
    searchBarBorder: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    Surface(
        shape = CircleShape,
        color = searchBarBg,
        border = BorderStroke(1.dp, searchBarBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(42.dp)
            .testTag("home_search_input")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = secondaryTextColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search tools and features...",
                        style = TextStyle(
                            fontSize = 13.5.sp,
                            color = secondaryTextColor
                        )
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 13.5.sp,
                        color = primaryTextColor,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(BrandBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Scan Anything Hero Card
// ---------------------------------------------------------------------------
@Composable
private fun ScanAnythingHeroCard(
    isDark: Boolean,
    isReading: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onNavigateToQrScan: () -> Unit,
    onLaunchGoogleScanner: () -> Unit = {}
) {
    val heroBg = when {
        isDark -> Color(0xFF1E293B)
        isReading -> ReadingSurface
        else -> Color(0xFFEFF6FF)
    }
    val heroBorder = BorderStroke(1.dp, when {
        isDark -> Color(0xFF3B82F6).copy(alpha = 0.35f)
        isReading -> ReadingBorder
        else -> Color(0xFFDBEAFE)
    })

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = heroBg,
        border = heroBorder,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .testTag("scan_anything_hero_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column (Text + Button)
            Column(
                modifier = Modifier.weight(1.25f)
            ) {
                Text(
                    text = "Scan Anything",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "QR codes, barcodes or documents",
                    style = TextStyle(
                        fontSize = 12.5.sp,
                        color = secondaryTextColor
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons Row: QR Scan + Document Scanner
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pill Button "QR Scan"
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = BrandBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { onNavigateToQrScan() }
                            .testTag("start_qr_scan_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "QR Scan",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "QR Scan",
                                style = TextStyle(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            )
                        }
                    }

                    // Pill Button "Document Scanner"
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF0D9488),
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { onLaunchGoogleScanner() }
                            .testTag("start_doc_scanner_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = "Document Scanner",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Document Scanner",
                                style = TextStyle(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Right Illustration (Circular Blob + Scan Frame Illustration)
            Box(
                modifier = Modifier
                    .weight(0.95f)
                    .height(105.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background soft light-blue circular blob
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF2563EB).copy(alpha = 0.25f) else Color(0xFFDBEAFE).copy(alpha = 0.55f))
                )

                // Large scan frame corners + horizontal laser
                Canvas(modifier = Modifier.size(72.dp)) {
                    val strokeW = 3.5.dp.toPx()
                    val bracketLen = 16.dp.toPx()
                    val cornerR = 7.dp.toPx()
                    val blueColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)

                    // Top-Left corner bracket
                    drawLine(blueColor, Offset(0f, bracketLen), Offset(0f, cornerR), strokeW, StrokeCap.Round)
                    drawLine(blueColor, Offset(cornerR, 0f), Offset(bracketLen, 0f), strokeW, StrokeCap.Round)
                    drawArc(
                        color = blueColor,
                        startAngle = 180f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(0f, 0f),
                        size = Size(cornerR * 2, cornerR * 2),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    // Top-Right corner bracket
                    val w = size.width
                    drawLine(blueColor, Offset(w - bracketLen, 0f), Offset(w - cornerR, 0f), strokeW, StrokeCap.Round)
                    drawLine(blueColor, Offset(w, cornerR), Offset(w, bracketLen), strokeW, StrokeCap.Round)
                    drawArc(
                        color = blueColor,
                        startAngle = 270f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(w - cornerR * 2, 0f),
                        size = Size(cornerR * 2, cornerR * 2),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    // Bottom-Left corner bracket
                    val h = size.height
                    drawLine(blueColor, Offset(0f, h - bracketLen), Offset(0f, h - cornerR), strokeW, StrokeCap.Round)
                    drawLine(blueColor, Offset(cornerR, h), Offset(bracketLen, h), strokeW, StrokeCap.Round)
                    drawArc(
                        color = blueColor,
                        startAngle = 90f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(0f, h - cornerR * 2),
                        size = Size(cornerR * 2, cornerR * 2),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    // Bottom-Right corner bracket
                    drawLine(blueColor, Offset(w - bracketLen, h), Offset(w - cornerR, h), strokeW, StrokeCap.Round)
                    drawLine(blueColor, Offset(w, h - bracketLen), Offset(w, h - cornerR), strokeW, StrokeCap.Round)
                    drawArc(
                        color = blueColor,
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(w - cornerR * 2, h - cornerR * 2),
                        size = Size(cornerR * 2, cornerR * 2),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    // Center horizontal scan beam
                    val beamMargin = 10.dp.toPx()
                    drawLine(
                        color = Color(0xFF3B82F6).copy(alpha = 0.55f),
                        start = Offset(beamMargin, h / 2f),
                        end = Offset(w - beamMargin, h / 2f),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 5. Section Header Row ("Title" and "See All >")
// ---------------------------------------------------------------------------
@Composable
private fun SectionHeaderRow(
    title: String,
    primaryTextColor: Color = Color(0xFF0F172A),
    isDark: Boolean = false,
    onSeeAllClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onSeeAllClick() }
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Text(
                text = "See All",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
                )
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "See All",
                tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 6. Quick Category Circle Item (Circular Icon, Light Border, Label Below)
// ---------------------------------------------------------------------------
@Composable
private fun QuickCategoryCircleItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isDark: Boolean,
    isReading: Boolean,
    primaryTextColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val circleBg = when {
        isDark -> Color(0xFF1E293B)
        isReading -> ReadingSurface
        else -> iconColor.copy(alpha = 0.08f)
    }
    val circleBorder = when {
        isDark -> DarkBorder
        isReading -> ReadingBorder
        else -> iconColor.copy(alpha = 0.22f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag(testTag)
    ) {
        Surface(
            shape = CircleShape,
            color = circleBg,
            border = BorderStroke(1.dp, circleBorder),
            modifier = Modifier.size(52.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            style = TextStyle(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// 7. Scan History Row (Real thumbnails, scan type chips, document names & dates)
// ---------------------------------------------------------------------------
@Composable
private fun ScanHistoryRow(
    item: HistoryEntity,
    cardBg: Color = Color.White,
    cardBorder: Color = Color(0xFFF1F5F9),
    primaryTextColor: Color = Color(0xFF1E293B),
    secondaryTextColor: Color = Color(0xFF64748B),
    isDark: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val relativeTime = remember(item.timestamp) {
        formatRelativeTime(item.timestamp)
    }

    val (scanTypeLabel, typeColor, typeIcon) = remember(item.toolId, item.title) {
        val id = item.toolId.lowercase()
        val title = item.title.lowercase()
        when {
            id.contains("doc") || title.contains("document") -> Triple("Document", Color(0xFF0F766E), Icons.Default.Description)
            id.contains("pdf") || title.contains("pdf") -> Triple("PDF", Color(0xFFEF4444), Icons.Default.PictureAsPdf)
            id.contains("qr") || title.contains("qr") -> Triple("QR Code", Color(0xFF2563EB), Icons.Default.QrCode2)
            id.contains("barcode") || title.contains("barcode") -> Triple("Barcode", Color(0xFF7C3AED), Icons.Default.QrCodeScanner)
            id.contains("card") || title.contains("card") -> Triple("ID Card", Color(0xFFD97706), Icons.Default.Badge)
            id.contains("image") || id.contains("photo") || title.contains("photo") -> Triple("Image", Color(0xFF16A34A), Icons.Default.Image)
            else -> Triple("Scan", Color(0xFF0284C7), Icons.Default.DocumentScanner)
        }
    }

    var thumbnailBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.id, item.details) {
        thumbnailBitmap = com.example.utils.ScanThumbnailHelper.loadThumbnail(context, item)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("scan_history_item_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Real Document Thumbnail / Photo Preview
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (thumbnailBitmap != null) Color.Black else typeColor.copy(alpha = 0.08f))
                    .border(1.dp, typeColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = "Scan Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (scanTypeLabel == "PDF") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "PDF",
                            style = TextStyle(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        )
                    }
                } else {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = scanTypeLabel,
                        tint = typeColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title, Type Tag & Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        style = TextStyle(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Type Badge Chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = typeColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = scanTypeLabel,
                            color = typeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.details.ifBlank { "Scan completed" },
                        style = TextStyle(
                            fontSize = 11.5.sp,
                            color = secondaryTextColor
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = relativeTime,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 8. Favorite Compact Card (QR Scanner, PDF Tools, Image Compressor, File Manager)
// ---------------------------------------------------------------------------
@Composable
private fun FavoriteCompactCard(
    title: String,
    badgeColor: Color,
    backgroundColor: Color,
    borderColor: Color,
    primaryTextColor: Color = Color(0xFF1E293B),
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(0.7.dp, borderColor),
        modifier = modifier
            .height(86.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                if (title.contains("PDF", ignoreCase = true)) {
                    Text(
                        text = "PDF",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 9. Empty State Card
// ---------------------------------------------------------------------------
@Composable
private fun EmptyStateCard(
    message: String,
    subtleCardBg: Color = Color(0xFFF8FAFC),
    cardBorder: Color = Color(0xFFE2E8F0),
    secondaryTextColor: Color = Color(0xFF64748B)
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = subtleCardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.HistoryToggleOff,
                contentDescription = null,
                tint = secondaryTextColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = secondaryTextColor
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helper: Relative Time Formatter
// ---------------------------------------------------------------------------
private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

// ---------------------------------------------------------------------------
// 9. Scan Detail Preview Dialog (Full Preview, Metadata, Open & Share)
// ---------------------------------------------------------------------------
@Composable
private fun ScanDetailPreviewDialog(
    item: HistoryEntity,
    file: File,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onOpenFile: () -> Unit,
    onShareFile: () -> Unit
) {
    val context = LocalContext.current
    var previewBitmap by remember(file.absolutePath) { mutableStateOf<Bitmap?>(null) }
    val isPdf = file.name.endsWith(".pdf", ignoreCase = true)

    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            try {
                if (isPdf) {
                    val uri = Uri.fromFile(file)
                    val bmps = PdfUtils.pdfToBitmaps(context, uri, 1)
                    if (bmps.isNotEmpty()) previewBitmap = bmps.first()
                } else {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    previewBitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
                }
            } catch (_: Exception) {
            }
        }
    }

    val dialogBg = if (isDark) DarkSurface else Color.White
    val textColor = if (isDark) DarkTextPrimary else Color(0xFF0F172A)
    val secondaryText = if (isDark) DarkTextSecondary else Color(0xFF64748B)
    val cardBorder = if (isDark) DarkBorder else Color(0xFFE2E8F0)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        titleContentColor = textColor,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPdf) Color(0xFFEF4444).copy(alpha = 0.12f) else BrandBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                        contentDescription = null,
                        tint = if (isPdf) Color(0xFFEF4444) else BrandBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isPdf) "PDF Document" else "Scanned Image",
                        style = TextStyle(fontSize = 11.sp, color = secondaryText)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = secondaryText)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Preview Image or Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF0B132B) else Color(0xFFF8FAFC))
                        .border(1.dp, cardBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Document Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = secondaryText,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Preview ready",
                                color = secondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // File metadata details
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("File Name:", fontSize = 12.sp, color = secondaryText)
                            Text(file.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Size:", fontSize = 12.sp, color = secondaryText)
                            Text(StorageUtils.formatFileSize(file.length()), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Date:", fontSize = 12.sp, color = secondaryText)
                            val dateStr = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault()).format(Date(item.timestamp))
                            Text(dateStr, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenFile,
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
            ) {
                Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open File", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onShareFile) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share")
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Helper: Resolve Display Name from Uri
// ---------------------------------------------------------------------------
private fun resolveFileName(context: android.content.Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        } catch (_: Exception) {}
    }
    if (result == null) {
        result = uri.path?.let { p ->
            val cut = p.lastIndexOf('/')
            if (cut != -1) p.substring(cut + 1) else p
        }
    }
    return result ?: "imported_file_${System.currentTimeMillis()}"
}
