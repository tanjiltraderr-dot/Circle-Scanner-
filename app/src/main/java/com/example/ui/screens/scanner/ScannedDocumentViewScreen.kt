package com.example.ui.screens.scanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.UtilityHubApp
import com.example.data.local.entity.NoteEntity
import com.example.ui.theme.BrandBlue
import com.example.utils.DocumentFilter
import com.example.utils.DocumentProcessor
import com.example.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.flow.firstOrNull
import com.example.data.local.entity.ScannedDocumentEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannedDocumentViewScreen(
    filePath: String,
    initialTitle: String = "Scanned Document",
    docId: Long = -1L,
    onBack: () -> Unit,
    onNavigateToTool: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { UtilityHubApp.instance.repository }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val decodedPath = remember(filePath) {
        try { URLDecoder.decode(filePath, "UTF-8") } catch (_: Exception) { filePath }
    }
    val decodedTitle = remember(initialTitle) {
        try { URLDecoder.decode(initialTitle, "UTF-8") } catch (_: Exception) { initialTitle }
    }

    var scannedEntity by remember { mutableStateOf<ScannedDocumentEntity?>(null) }
    var documentTitle by remember { mutableStateOf(decodedTitle) }
    var currentFile by remember { mutableStateOf<File?>(null) }
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeFilter by remember { mutableStateOf(DocumentFilter.AUTO_ENHANCE) }
    var isPdfDocument by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingMsg by remember { mutableStateOf("") }

    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var showOcrModal by remember { mutableStateOf(false) }
    var ocrTextResult by remember { mutableStateOf("") }
    var showCropAdjuster by remember { mutableStateOf(false) }
    var cropCandidateBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropQuad by remember { mutableStateOf<com.example.utils.DocumentQuad?>(null) }

    BackHandler {
        if (showCropAdjuster) {
            showCropAdjuster = false
        } else {
            onBack()
        }
    }

    // Load file and bitmap using exact docId / Room database and permanent storage
    LaunchedEffect(docId, decodedPath) {
        withContext(Dispatchers.IO) {
            var entity: ScannedDocumentEntity? = null
            val extractedId = com.example.scanner.engine.ScanStorageManager.extractScanId(decodedPath)
            val effectiveDocId = if (docId > 0) docId else (extractedId ?: -1L)

            if (effectiveDocId > 0) {
                entity = repository.getScannedDocumentById(effectiveDocId)
            }
            if (entity == null && decodedPath.isNotBlank()) {
                val allDocs = repository.getAllScannedDocuments().firstOrNull() ?: emptyList()
                val cleanName = decodedPath.substringAfter("|").trim()
                val candidateName = File(cleanName).name
                entity = allDocs.firstOrNull { doc ->
                    doc.processedImagePath == decodedPath ||
                    doc.processedImagePath == cleanName ||
                    doc.pdfFilePath == decodedPath ||
                    doc.pdfFilePath == cleanName ||
                    doc.originalFileName == candidateName ||
                    doc.originalFileName == cleanName ||
                    (doc.processedImagePath != null && File(doc.processedImagePath).name == candidateName)
                }
            }

            scannedEntity = entity
            if (entity != null) {
                documentTitle = entity.title
            }

            val resolved = if (entity?.processedImagePath != null && File(entity.processedImagePath).exists()) {
                File(entity.processedImagePath)
            } else if (entity?.pdfFilePath != null && File(entity.pdfFilePath).exists()) {
                File(entity.pdfFilePath)
            } else if (entity?.rawImagePath != null && File(entity.rawImagePath).exists()) {
                File(entity.rawImagePath)
            } else {
                val cleanName = decodedPath.substringAfter("|").trim()
                val direct = File(cleanName)
                if (direct.exists() && direct.isFile) {
                    direct
                } else {
                    val candidateDirs = listOf(
                        File(context.filesDir, "scanned_docs"),
                        File(context.filesDir, "raw_docs"),
                        File(context.filesDir, "scanned_images"),
                        File(context.filesDir, "pdfs"),
                        File(context.filesDir, "images"),
                        File(context.filesDir, "exported_media"),
                        context.filesDir
                    )
                    candidateDirs.map { File(it, File(cleanName).name) }.firstOrNull { it.exists() && it.isFile }
                }
            }

            currentFile = resolved
            if (resolved != null && resolved.exists()) {
                if (resolved.name.endsWith(".pdf", ignoreCase = true)) {
                    isPdfDocument = true
                    val uri = Uri.fromFile(resolved)
                    val bmps = PdfUtils.pdfToBitmaps(context, uri, 1)
                    if (bmps.isNotEmpty()) {
                        baseBitmap = bmps.first()
                        displayBitmap = bmps.first()
                    }
                } else {
                    isPdfDocument = false
                    val bmp = BitmapFactory.decodeFile(resolved.absolutePath)
                    if (bmp != null) {
                        baseBitmap = bmp
                        displayBitmap = bmp
                    }
                }
            }

            isLoading = false
        }
    }

    fun shareCurrentFile() {
        val f = currentFile
        if (f != null && f.exists()) {
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                val mime = if (f.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share $documentTitle"))
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "File not available for sharing", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveAsPdf() {
        val bmp = displayBitmap ?: return
        scope.launch {
            isProcessing = true
            processingMsg = "Exporting as PDF..."
            val pdfFile = withContext(Dispatchers.IO) {
                val name = "Doc_${documentTitle.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
                PdfUtils.imagesToPdf(context, listOf(bmp), name)
            }
            isProcessing = false
            if (pdfFile != null) {
                currentFile = pdfFile
                isPdfDocument = true
                repository.addHistory("pdf_tools", "Exported PDF: $documentTitle", pdfFile.name)
                Toast.makeText(context, "Saved as PDF: ${pdfFile.name}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showCompareOriginal by remember { mutableStateOf(false) }
    var rawOriginalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var activeEnhancementMode by remember { mutableStateOf(com.example.scanner.engine.EnhancementMode.DOCUMENT_COLOR) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // Check if raw original is available
    LaunchedEffect(currentFile, scannedEntity) {
        val ent = scannedEntity
        if (ent != null) {
            withContext(Dispatchers.IO) {
                rawOriginalBitmap = com.example.scanner.engine.ScanStorageManager.loadRawOriginal(context, ent)
            }
        } else {
            currentFile?.let { f ->
                withContext(Dispatchers.IO) {
                    rawOriginalBitmap = com.example.scanner.engine.ScanStorageManager.loadRawOriginalByFile(context, f)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                renameInput = documentTitle
                                showRenameDialog = true
                            }
                    ) {
                        Text(
                            text = documentTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (rawOriginalBitmap != null) {
                        IconButton(onClick = { showCompareOriginal = !showCompareOriginal }) {
                            Icon(
                                imageVector = Icons.Default.Compare,
                                contentDescription = "Compare Original",
                                tint = if (showCompareOriginal) Color(0xFF00E676) else Color.White
                            )
                        }
                    }
                    IconButton(onClick = { shareCurrentFile() }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212)
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = BrandBlue
                )
            } else if (displayBitmap == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Document preview unavailable",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The file could not be decoded or loaded from disk.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Status Badge: Auto Cropped & Straightened · Crisp Text
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF00A884).copy(alpha = 0.18f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00A884).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp, bottom = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00A884),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showCompareOriginal) "Viewing Original Raw Photo" else "Auto Cropped · Straightened · Crisp Text",
                                color = if (showCompareOriginal) Color(0xFFFBBF24) else Color(0xFF00E676),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Main Document Preview Frame with Pinch-to-Zoom & Pan
                    val currentBmp = if (showCompareOriginal && rawOriginalBitmap != null) {
                        rawOriginalBitmap!!
                    } else {
                        displayBitmap!!
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E1E))
                            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                    zoomOffset = if (zoomScale > 1f) zoomOffset + pan else Offset.Zero
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = currentBmp.asImageBitmap(),
                            contentDescription = "Scanned Document Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = zoomScale,
                                    scaleY = zoomScale,
                                    translationX = zoomOffset.x,
                                    translationY = zoomOffset.y
                                )
                        )
                    }

                    // Interactive 6-Mode Enhancement Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.example.scanner.engine.EnhancementMode.values().forEach { mode ->
                            val isSelected = mode == activeEnhancementMode
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) Color(0xFF00A884) else Color(0xFF242424),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF00E676) else Color(0xFF333333)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        activeEnhancementMode = mode
                                        val base = baseBitmap ?: return@clickable
                                        scope.launch {
                                            isProcessing = true
                                            processingMsg = "Applying ${mode.label}..."
                                            val filtered = withContext(Dispatchers.IO) {
                                                com.example.scanner.engine.DocumentEnhancementEngine.enhance(base, mode)
                                            }
                                            displayBitmap = filtered
                                            isProcessing = false
                                        }
                                    }
                            ) {
                                Text(
                                    text = mode.label,
                                    color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Bottom Action Toolbar (Rotate, OCR, Crop Adjust, Export PDF)
                    Surface(
                        color = Color(0xFF181818),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262626)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rotate 90°
                            ToolbarIconAction(
                                icon = Icons.Default.RotateRight,
                                label = "Rotate",
                                onClick = {
                                    val curr = displayBitmap ?: return@ToolbarIconAction
                                    scope.launch {
                                        isProcessing = true
                                        processingMsg = "Rotating..."
                                        val rot = withContext(Dispatchers.IO) {
                                            DocumentProcessor.rotateBitmap(curr, 90f)
                                        }
                                        baseBitmap = rot
                                        displayBitmap = rot
                                        isProcessing = false
                                    }
                                }
                            )

                            // Crop & Straighten
                            ToolbarIconAction(
                                icon = Icons.Default.Crop,
                                label = "Crop",
                                onClick = {
                                    val bmp = displayBitmap ?: return@ToolbarIconAction
                                    cropCandidateBitmap = bmp
                                    cropQuad = DocumentProcessor.detectDocumentCorners(bmp)
                                    showCropAdjuster = true
                                }
                            )

                            // Extract Text (OCR)
                            ToolbarIconAction(
                                icon = Icons.Default.TextFields,
                                label = "Extract Text",
                                onClick = {
                                    val bmp = displayBitmap ?: return@ToolbarIconAction
                                    scope.launch {
                                        isProcessing = true
                                        processingMsg = "Reading document text with ML Kit..."
                                        val txt = DocumentProcessor.extractTextReal(bmp)
                                        ocrTextResult = txt
                                        isProcessing = false
                                        showOcrModal = true
                                    }
                                }
                            )

                            // Export PDF
                            ToolbarIconAction(
                                icon = Icons.Default.PictureAsPdf,
                                label = "Save PDF",
                                onClick = { saveAsPdf() }
                            )

                            // Share
                            ToolbarIconAction(
                                icon = Icons.Default.Share,
                                label = "Share",
                                onClick = { shareCurrentFile() }
                            )
                        }
                    }
                }
            }

            // Processing overlay
            if (isProcessing) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF00E676),
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = processingMsg,
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // Interactive Crop Modal Dialog
    if (showCropAdjuster && cropCandidateBitmap != null) {
        val targetBmp = cropCandidateBitmap!!
        val currentQuad = cropQuad ?: remember(targetBmp) { DocumentProcessor.detectDocumentCorners(targetBmp) }
        Dialog(
            onDismissRequest = { showCropAdjuster = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { showCropAdjuster = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                        }
                        Text(
                            text = "Crop & Straighten",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        TextButton(
                            onClick = {
                                val bmp = cropCandidateBitmap ?: return@TextButton
                                val q = cropQuad ?: DocumentProcessor.detectDocumentCorners(bmp)
                                scope.launch {
                                    isProcessing = true
                                    processingMsg = "Cropping document..."
                                    val cropped = withContext(Dispatchers.IO) {
                                        DocumentProcessor.cropPerspective(bmp, q)
                                    }
                                    val filtered = withContext(Dispatchers.IO) {
                                        DocumentProcessor.applyFilter(cropped, activeFilter)
                                    }
                                    baseBitmap = cropped
                                    displayBitmap = filtered
                                    currentFile?.let { f ->
                                        withContext(Dispatchers.IO) {
                                            try {
                                                java.io.FileOutputStream(f).use { out ->
                                                    filtered.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    showCropAdjuster = false
                                    isProcessing = false
                                    Toast.makeText(context, "Document cropped & saved", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Done", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    // Interactive Corner-Dragging Quad Editor
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        InteractiveQuadEditor(
                            bitmap = targetBmp,
                            quad = currentQuad,
                            onQuadChanged = { cropQuad = it }
                        )
                    }

                    // Bottom Quick Options: Auto Detect & Full Page
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = {
                                cropQuad = DocumentProcessor.detectDocumentCorners(targetBmp)
                            }
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto Detect", color = Color.White, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val w = targetBmp.width.toFloat()
                                val h = targetBmp.height.toFloat()
                                cropQuad = com.example.utils.DocumentQuad(
                                    topLeft = PointF(0f, 0f),
                                    topRight = PointF(w, 0f),
                                    bottomRight = PointF(w, h),
                                    bottomLeft = PointF(0f, h),
                                    isAutoDetected = false
                                )
                            }
                        ) {
                            Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Full Page", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            val newTitle = renameInput.trim()
                            documentTitle = newTitle
                            scannedEntity?.let { ent ->
                                scope.launch(Dispatchers.IO) {
                                    val updated = ent.copy(title = newTitle)
                                    repository.updateScannedDocument(updated)
                                    scannedEntity = updated
                                }
                            }
                        }
                        showRenameDialog = false
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

    // OCR Results Modal
    if (showOcrModal) {
        AlertDialog(
            onDismissRequest = { showOcrModal = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Extracted Text",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 260.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF121212))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = ocrTextResult,
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val timeStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
                                repository.insertNote(
                                    NoteEntity(
                                        title = "$documentTitle ($timeStr)",
                                        content = ocrTextResult
                                    )
                                )
                                Toast.makeText(context, "Saved to Notes!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Save to Notes")
                    }

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(ocrTextResult))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        Text("Copy")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showOcrModal = false }) {
                    Text("Close", color = Color.Gray)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(
                    text = "Delete Scan?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will permanently remove '$documentTitle' and its processed document files.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val targetId = scannedEntity?.id ?: (if (docId > 0) docId else -1L)
                            val targetName = currentFile?.name ?: File(decodedPath).name
                            com.example.scanner.engine.ScanStorageManager.deleteScan(context, targetId, targetName)
                            showDeleteDialog = false
                            Toast.makeText(context, "Scan deleted", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun ToolbarIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color(0xFFB0B0B0),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
