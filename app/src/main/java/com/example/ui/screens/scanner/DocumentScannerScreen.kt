package com.example.ui.screens.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.UtilityHubApp
import com.example.ui.components.AppBackButton
import com.example.ui.components.AppTopBar
import com.example.ui.components.LoadingState
import com.example.ui.components.PermissionNoticeCard
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.theme.BrandBlue
import com.example.utils.DocumentCornerTracker
import com.example.utils.DocumentFilter
import com.example.utils.DocumentProcessor
import com.example.utils.DocumentQuad
import com.example.utils.PdfUtils
import com.example.utils.ScannedPageItem
import com.example.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

enum class DocScanStep {
    CAMERA,
    CROP_PERSPECTIVE,
    ENHANCE_AND_OCR,
    PAGE_MANAGEMENT,
    SAVED_DOCUMENTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { UtilityHubApp.instance.repository }
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasCameraPermission = isGranted }

    // Multi-page document storage
    val scannedPages = remember { mutableStateListOf<ScannedPageItem>() }
    var selectedPageIndex by remember { mutableIntStateOf(0) }

    // Flow Step State
    var currentStep by remember { mutableStateOf(DocScanStep.CAMERA) }

    // Transient image being cropped
    var pendingRawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentQuad by remember { mutableStateOf<DocumentQuad?>(null) }

    // Real-time Camera Frame Analysis & Tracking
    val cornerTracker = remember { DocumentCornerTracker() }
    var realtimeQuad by remember { mutableStateOf<DocumentQuad?>(null) }
    var realtimeBufferWidth by remember { mutableIntStateOf(0) }
    var realtimeBufferHeight by remember { mutableIntStateOf(0) }
    var isRealtimeReliable by remember { mutableStateOf(false) }
    var isRealtimeStable by remember { mutableStateOf(false) }

    // Auto-capture state
    var isAutoCaptureEnabled by remember { mutableStateOf(false) }
    var isAutoCapturing by remember { mutableStateOf(false) }
    var lastAutoCaptureTime by remember { mutableLongStateOf(0L) }

    // Camera hardware states
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraInfo by remember { mutableStateOf<CameraInfo?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isFlashSupported by remember { mutableStateOf(false) }
    var currentZoomRatio by remember { mutableFloatStateOf(1.0f) }

    // Processing & Output
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("Processing...") }

    // Dialog states
    var showFilenameDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showOcrDialog by remember { mutableStateOf(false) }
    var showCompareOriginal by remember { mutableStateOf(false) }
    var newlyCreatedPdfFile by remember { mutableStateOf<File?>(null) }

    // Saved Documents state
    var savedPdfs by remember { mutableStateOf(listOf<File>()) }
    var savedDocsSearch by remember { mutableStateOf("") }
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var newRenameInput by remember { mutableStateOf("") }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    fun refreshSavedPdfs() {
        val pdfDir = File(context.filesDir, "pdfs")
        savedPdfs = if (pdfDir.exists()) {
            pdfDir.listFiles { f -> f.extension.equals("pdf", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }
    }

    LaunchedEffect(currentStep) {
        if (currentStep == DocScanStep.SAVED_DOCUMENTS) {
            refreshSavedPdfs()
        }
        if (currentStep != DocScanStep.CAMERA) {
            cornerTracker.reset()
            realtimeQuad = null
            isRealtimeReliable = false
            isRealtimeStable = false
            isAutoCapturing = false
        }
    }

    // Sync torch
    LaunchedEffect(isFlashOn, cameraControl) {
        if (isFlashSupported) {
            cameraControl?.enableTorch(isFlashOn)
        }
    }

    // Helper to capture document (used by both manual shutter and auto-capture)
    fun executeCapture(isAuto: Boolean) {
        val capture = imageCapture ?: return
        if (isProcessing) return

        isProcessing = true
        processingMessage = if (isAuto) "Auto-capturing document..." else "Capturing document..."

        val photoFile = File(context.cacheDir, "doc_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    scope.launch(Dispatchers.IO) {
                        var raw = BitmapFactory.decodeFile(photoFile.absolutePath)
                        photoFile.inputStream().use { stream ->
                            if (raw != null) {
                                raw = DocumentProcessor.fixOrientation(raw!!, stream)
                            }
                        }
                        if (raw != null) {
                            val autoResult = DocumentProcessor.autoCropAndEnhance(
                                context = context,
                                rawBitmap = raw!!,
                                preferredFilter = DocumentFilter.AUTO_ENHANCE
                            )
                            val ocr = DocumentProcessor.extractTextReal(autoResult.enhancedBitmap)
                            val pageItem = ScannedPageItem(
                                rawOriginal = raw!!,
                                croppedOriginal = autoResult.croppedBitmap,
                                processed = autoResult.enhancedBitmap,
                                activeFilter = DocumentFilter.AUTO_ENHANCE,
                                rotationAngle = autoResult.rotationAngle,
                                ocrText = ocr,
                                quad = autoResult.quad
                            )

                            if (autoResult.isDetectionSuccess) {
                                val savedImg = DocumentProcessor.saveBitmapToStorage(
                                    context,
                                    autoResult.enhancedBitmap,
                                    "doc_${System.currentTimeMillis()}.jpg"
                                )
                                if (savedImg != null) {
                                    repository.addHistory("doc_scanner", "Scanned Document", savedImg.name)
                                }
                            }

                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                isAutoCapturing = false
                                scannedPages.add(pageItem)
                                selectedPageIndex = scannedPages.lastIndex
                                if (autoResult.isDetectionSuccess) {
                                    currentStep = DocScanStep.ENHANCE_AND_OCR
                                } else {
                                    currentStep = DocScanStep.CROP_PERSPECTIVE
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                isAutoCapturing = false
                                Toast.makeText(context, "Could not decode capture", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isProcessing = false
                    isAutoCapturing = false
                    Toast.makeText(context, "Capture error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Gallery Import for one or multiple images
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                processingMessage = "Importing image(s)..."
                val importedPages = withContext(Dispatchers.IO) {
                    val list = mutableListOf<ScannedPageItem>()
                    for (uri in uris) {
                        try {
                            var bmp: Bitmap? = null
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val raw = BitmapFactory.decodeStream(stream)
                                context.contentResolver.openInputStream(uri)?.use { exifStream ->
                                    bmp = if (raw != null) DocumentProcessor.fixOrientation(raw, exifStream) else null
                                }
                            }
                            if (bmp != null) {
                                val quad = DocumentProcessor.detectDocumentCorners(bmp!!)
                                val cropped = DocumentProcessor.cropPerspective(bmp!!, quad)
                                val enhanced = DocumentProcessor.applyFilter(cropped, DocumentFilter.AUTO_ENHANCE)
                                val ocr = DocumentProcessor.extractTextPreview(enhanced)
                                list.add(
                                    ScannedPageItem(
                                        rawOriginal = bmp!!,
                                        croppedOriginal = cropped,
                                        processed = enhanced,
                                        activeFilter = DocumentFilter.AUTO_ENHANCE,
                                        ocrText = ocr,
                                        quad = quad
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    list
                }
                isProcessing = false
                if (importedPages.isNotEmpty()) {
                    scannedPages.addAll(importedPages)
                    selectedPageIndex = scannedPages.lastIndex
                    currentStep = DocScanStep.PAGE_MANAGEMENT
                    Toast.makeText(context, "${importedPages.size} page(s) imported", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    BackHandler {
        when (currentStep) {
            DocScanStep.CROP_PERSPECTIVE -> currentStep = DocScanStep.CAMERA
            DocScanStep.ENHANCE_AND_OCR -> {
                if (scannedPages.isNotEmpty()) {
                    currentStep = DocScanStep.PAGE_MANAGEMENT
                } else {
                    currentStep = DocScanStep.CAMERA
                }
            }
            DocScanStep.PAGE_MANAGEMENT -> {
                if (scannedPages.isEmpty()) onBack() else currentStep = DocScanStep.CAMERA
            }
            DocScanStep.SAVED_DOCUMENTS -> currentStep = DocScanStep.CAMERA
            DocScanStep.CAMERA -> onBack()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = when (currentStep) {
                    DocScanStep.CAMERA -> "Document Scanner"
                    DocScanStep.CROP_PERSPECTIVE -> "Crop & Perspective"
                    DocScanStep.ENHANCE_AND_OCR -> "Enhance Page (${selectedPageIndex + 1}/${scannedPages.size.coerceAtLeast(1)})"
                    DocScanStep.PAGE_MANAGEMENT -> "Document Pages (${scannedPages.size})"
                    DocScanStep.SAVED_DOCUMENTS -> "My Scanned Documents"
                },
                navigationIcon = {
                    AppBackButton(onBack = {
                        when (currentStep) {
                            DocScanStep.CROP_PERSPECTIVE -> currentStep = DocScanStep.CAMERA
                            DocScanStep.ENHANCE_AND_OCR -> {
                                if (scannedPages.isNotEmpty()) {
                                    currentStep = DocScanStep.PAGE_MANAGEMENT
                                } else {
                                    currentStep = DocScanStep.CAMERA
                                }
                            }
                            DocScanStep.PAGE_MANAGEMENT -> {
                                if (scannedPages.isEmpty()) onBack() else currentStep = DocScanStep.CAMERA
                            }
                            DocScanStep.SAVED_DOCUMENTS -> currentStep = DocScanStep.CAMERA
                            DocScanStep.CAMERA -> onBack()
                        }
                    })
                },
                actions = {
                    if (currentStep == DocScanStep.CAMERA) {
                        // Quick access to saved documents
                        IconButton(
                            onClick = { currentStep = DocScanStep.SAVED_DOCUMENTS },
                            modifier = Modifier.testTag("doc_scanner_saved_docs_btn")
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Saved Documents")
                        }
                        // Badge if active session has scanned pages
                        if (scannedPages.isNotEmpty()) {
                            IconButton(onClick = { currentStep = DocScanStep.PAGE_MANAGEMENT }) {
                                BadgedBox(badge = {
                                    Badge(containerColor = BrandBlue, contentColor = Color.White) {
                                        Text("${scannedPages.size}")
                                    }
                                }) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Review Pages")
                                }
                            }
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentStep) {
                DocScanStep.CAMERA -> {
                    // -------------------------------------------------------------
                    // 1. LIVE CAMERA SCANNER WITH REAL-TIME EDGE DETECTION OVERLAY
                    // -------------------------------------------------------------
                    if (!hasCameraPermission) {
                        PermissionNoticeCard(
                            title = "Camera Permission Required",
                            explanation = "Circle Scanner needs camera access to capture documents, receipts, and book pages.",
                            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Camera preview view & ImageAnalysis pipeline
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx).apply {
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                    }
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                        val capture = ImageCapture.Builder()
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                            .build()
                                        imageCapture = capture

                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .setTargetResolution(android.util.Size(640, 480))
                                            .build()

                                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                            try {
                                                if (currentStep == DocScanStep.CAMERA && !isProcessing && !isAutoCapturing) {
                                                    val yPlane = imageProxy.planes[0]
                                                    val rawResult = DocumentProcessor.detectDocumentInYuv(
                                                        yBuffer = yPlane.buffer,
                                                        width = imageProxy.width,
                                                        height = imageProxy.height,
                                                        rowStride = yPlane.rowStride,
                                                        pixelStride = yPlane.pixelStride,
                                                        rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                                    )
                                                    val trackedQuad = cornerTracker.track(rawResult)
                                                    val stable = cornerTracker.isStable
                                                    val reliable = rawResult != null && rawResult.isReliable

                                                    scope.launch(Dispatchers.Main) {
                                                        realtimeQuad = trackedQuad
                                                        isRealtimeReliable = reliable
                                                        isRealtimeStable = stable
                                                        if (rawResult != null) {
                                                            realtimeBufferWidth = rawResult.bufferWidth
                                                            realtimeBufferHeight = rawResult.bufferHeight
                                                        }

                                                        // Auto Capture Trigger check
                                                        val now = System.currentTimeMillis()
                                                        if (isAutoCaptureEnabled && stable && reliable && !isAutoCapturing && !isProcessing && (now - lastAutoCaptureTime > 3000L)) {
                                                            isAutoCapturing = true
                                                            lastAutoCaptureTime = now
                                                            executeCapture(isAuto = true)
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            } finally {
                                                imageProxy.close()
                                            }
                                        }

                                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                        try {
                                            cameraProvider.unbindAll()
                                            val cam = cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview,
                                                capture,
                                                imageAnalysis
                                            )
                                            cameraControl = cam.cameraControl
                                            cameraInfo = cam.cameraInfo
                                            isFlashSupported = cam.cameraInfo.hasFlashUnit()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("doc_camera_preview")
                            )

                            // Live Document Boundary & Corner Guides Overlay
                            LiveDocumentDetectionOverlay(
                                detectedQuad = realtimeQuad,
                                bufferWidth = realtimeBufferWidth,
                                bufferHeight = realtimeBufferHeight,
                                isReliable = isRealtimeReliable,
                                isStable = isRealtimeStable,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("live_doc_overlay")
                            )

                            // Top Real-Time Status Banner: Instruction / Detection State
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = when {
                                    isAutoCapturing -> Color(0xFF10B981) // Green
                                    isRealtimeStable -> Color(0xFF10B981).copy(alpha = 0.9f)
                                    isRealtimeReliable -> Color(0xFF2563EB).copy(alpha = 0.85f)
                                    else -> Color.Black.copy(alpha = 0.65f)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 74.dp)
                                    .testTag("doc_status_banner")
                            ) {
                                val statusText = when {
                                    isAutoCapturing -> "Auto-Capturing Document..."
                                    isRealtimeStable && isAutoCaptureEnabled -> "Document Steady • Capturing..."
                                    isRealtimeStable -> "Document Detected • Ready"
                                    isRealtimeReliable -> "Document Detected • Hold Steady"
                                    else -> "Align document within the frame"
                                }
                                Text(
                                    text = statusText,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                                )
                            }

                            // Top Camera Controls Bar: Flash, Auto-Capture Toggle & Zoom
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Torch control
                                if (isFlashSupported) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clickable { isFlashOn = !isFlashOn }
                                            .testTag("doc_camera_torch_toggle")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                                contentDescription = "Flashlight",
                                                tint = if (isFlashOn) BrandBlue else Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(44.dp))
                                }

                                // Auto Capture Toggle Pill
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isAutoCaptureEnabled) BrandBlue else Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .clickable {
                                            isAutoCaptureEnabled = !isAutoCaptureEnabled
                                            Toast.makeText(
                                                context,
                                                if (isAutoCaptureEnabled) "Auto Capture enabled" else "Auto Capture disabled",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        .testTag("doc_auto_capture_toggle")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isAutoCaptureEnabled) Icons.Default.AutoMode else Icons.Default.TouchApp,
                                            contentDescription = "Auto Capture",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (isAutoCaptureEnabled) "Auto: ON" else "Auto: OFF",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                // Zoom Pill
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .clickable {
                                            val nextZoom = if (currentZoomRatio >= 1.9f) 1.0f else 2.0f
                                            currentZoomRatio = nextZoom
                                            cameraControl?.setZoomRatio(nextZoom)
                                        }
                                        .testTag("doc_camera_zoom_toggle")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ZoomIn,
                                            contentDescription = "Zoom",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", currentZoomRatio)}x",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }

                            // Bottom Capture Panel
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter),
                                color = Color.Black.copy(alpha = 0.75f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (scannedPages.isNotEmpty())
                                            "${scannedPages.size} page(s) captured • Ready for next page"
                                        else if (isAutoCaptureEnabled)
                                            "Hold steady over document for automatic scan"
                                        else
                                            "Align document and tap shutter",
                                        color = Color.White.copy(alpha = 0.9f),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Gallery Import
                                        IconButton(
                                            onClick = { galleryPicker.launch("image/*") },
                                            modifier = Modifier
                                                .size(50.dp)
                                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                                .testTag("doc_gallery_import_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PhotoLibrary,
                                                contentDescription = "From Gallery",
                                                tint = Color.White
                                            )
                                        }

                                        // Capture Shutter (always available)
                                        Box(
                                            modifier = Modifier
                                                .size(76.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                                .clickable { executeCapture(isAuto = false) }
                                                .padding(6.dp)
                                                .testTag("doc_camera_shutter_btn"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(CircleShape)
                                                    .background(BrandBlue)
                                            )
                                        }

                                        // Done / View pages button
                                        if (scannedPages.isNotEmpty()) {
                                            IconButton(
                                                onClick = { currentStep = DocScanStep.PAGE_MANAGEMENT },
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .background(BrandBlue, CircleShape)
                                                    .testTag("doc_finish_scanning_btn")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Review Pages",
                                                    tint = Color.White
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.size(50.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                DocScanStep.CROP_PERSPECTIVE -> {
                    // -------------------------------------------------------------
                    // 2. DOCUMENT CORNERS REVIEW, PERSPECTIVE CROP & ROTATE
                    // -------------------------------------------------------------
                    val rawBmp = pendingRawBitmap
                    if (rawBmp != null) {
                        var quadState by remember(rawBmp) {
                            mutableStateOf(currentQuad ?: DocumentProcessor.detectDocumentCorners(rawBmp))
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Adjust Document Corners",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "Drag corner handles with live magnifier preview",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (quadState.isAutoDetected) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = BrandBlue.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "Auto Detected",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = BrandBlue,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Interactive Canvas with Loupe Magnifier
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                InteractiveQuadEditor(
                                    bitmap = rawBmp,
                                    quad = quadState,
                                    onQuadChanged = { quadState = it }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick adjustment buttons: Auto-Detect, Full Page, Rotate
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        quadState = DocumentProcessor.detectDocumentCorners(rawBmp)
                                        Toast.makeText(context, "Edges auto-detected", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Auto", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val w = rawBmp.width.toFloat()
                                        val h = rawBmp.height.toFloat()
                                        quadState = DocumentQuad(
                                            PointF(0f, 0f),
                                            PointF(w, 0f),
                                            PointF(w, h),
                                            PointF(0f, h),
                                            isAutoDetected = false
                                        )
                                    },
                                    modifier = Modifier.weight(1.1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Full Page", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val rotated = DocumentProcessor.rotateBitmap(rawBmp, 90f)
                                        pendingRawBitmap = rotated
                                        quadState = DocumentProcessor.detectDocumentCorners(rotated)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rotate", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action Bar: Retake & Next (Crop & Enhance)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { currentStep = DocScanStep.CAMERA },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Retake")
                                }

                                PrimaryButton(
                                    text = "Crop & Next",
                                    icon = Icons.Default.Crop,
                                    onClick = {
                                        scope.launch {
                                            isProcessing = true
                                            processingMessage = "Correcting perspective & flattening..."
                                            val cropped = withContext(Dispatchers.IO) {
                                                DocumentProcessor.cropPerspective(rawBmp, quadState)
                                            }
                                            val enhanced = withContext(Dispatchers.IO) {
                                                DocumentProcessor.applyFilter(cropped, DocumentFilter.AUTO_ENHANCE)
                                            }
                                            val ocr = withContext(Dispatchers.IO) {
                                                DocumentProcessor.extractTextPreview(enhanced)
                                            }

                                            // If we were recropping an existing page in edit mode, replace it
                                            if (selectedPageIndex in scannedPages.indices && scannedPages.isNotEmpty() && pendingRawBitmap == scannedPages[selectedPageIndex].rawOriginal) {
                                                scannedPages[selectedPageIndex] = ScannedPageItem(
                                                    rawOriginal = rawBmp,
                                                    croppedOriginal = cropped,
                                                    processed = enhanced,
                                                    activeFilter = DocumentFilter.AUTO_ENHANCE,
                                                    ocrText = ocr,
                                                    quad = quadState
                                                )
                                            } else {
                                                scannedPages.add(
                                                    ScannedPageItem(
                                                        rawOriginal = rawBmp,
                                                        croppedOriginal = cropped,
                                                        processed = enhanced,
                                                        activeFilter = DocumentFilter.AUTO_ENHANCE,
                                                        ocrText = ocr,
                                                        quad = quadState
                                                    )
                                                )
                                                selectedPageIndex = scannedPages.lastIndex
                                            }

                                            isProcessing = false
                                            currentStep = DocScanStep.ENHANCE_AND_OCR
                                        }
                                    },
                                    modifier = Modifier.weight(1.5f),
                                    testTag = "confirm_crop_btn"
                                )
                            }
                        }
                    }
                }

                DocScanStep.ENHANCE_AND_OCR -> {
                    // -------------------------------------------------------------
                    // 3. DOCUMENT ENHANCEMENT, OCR TEXT PREVIEW & MULTI-FILTER
                    // -------------------------------------------------------------
                    if (scannedPages.isNotEmpty() && selectedPageIndex in scannedPages.indices) {
                        val activePage = scannedPages[selectedPageIndex]
                        var selectedFilter by remember(activePage) { mutableStateOf(activePage.activeFilter) }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Document Enhancement & Filter",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Image Preview with Hold to Compare
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                val displayBmp = if (showCompareOriginal) activePage.croppedOriginal else activePage.processed
                                Image(
                                    bitmap = displayBmp.asImageBitmap(),
                                    contentDescription = "Enhanced Document",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                )

                                if (showCompareOriginal) {
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.75f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 12.dp)
                                    ) {
                                        Text(
                                            text = "Showing Original Page",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Compare Original Button & OCR Text Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { showCompareOriginal = !showCompareOriginal }
                                ) {
                                    Icon(
                                        imageVector = if (showCompareOriginal) Icons.Default.VisibilityOff else Icons.Default.Compare,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (showCompareOriginal) "Enhanced" else "Compare Original")
                                }

                                Button(
                                    onClick = { showOcrDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                                ) {
                                    Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Text (OCR)")
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Filter Chips Row
                            Text(
                                text = "Enhancement Presets",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(DocumentFilter.entries) { filter ->
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = {
                                            selectedFilter = filter
                                            activePage.activeFilter = filter
                                            scope.launch {
                                                isProcessing = true
                                                processingMessage = "Applying ${filter.label}..."
                                                val filtered = withContext(Dispatchers.IO) {
                                                    DocumentProcessor.applyFilter(activePage.croppedOriginal, filter)
                                                }
                                                activePage.processed = filtered
                                                isProcessing = false
                                            }
                                        },
                                        label = { Text(filter.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandBlue,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Bottom Navigation: Recrop, Add Page, Done
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        pendingRawBitmap = activePage.rawOriginal
                                        currentQuad = activePage.quad ?: DocumentProcessor.detectDocumentCorners(activePage.rawOriginal)
                                        currentStep = DocScanStep.CROP_PERSPECTIVE
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Recrop")
                                }

                                OutlinedButton(
                                    onClick = { currentStep = DocScanStep.CAMERA },
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Page")
                                }

                                PrimaryButton(
                                    text = "Done (${scannedPages.size})",
                                    icon = Icons.Default.Check,
                                    onClick = { currentStep = DocScanStep.PAGE_MANAGEMENT },
                                    modifier = Modifier.weight(1.3f)
                                )
                            }
                        }
                    }
                }

                DocScanStep.PAGE_MANAGEMENT -> {
                    // -------------------------------------------------------------
                    // 4. MULTI-PAGE MANAGEMENT, REORDER, DELETE & SAVE AS PDF
                    // -------------------------------------------------------------
                    if (scannedPages.isNotEmpty() && selectedPageIndex in scannedPages.indices) {
                        val currentBmp = scannedPages[selectedPageIndex].processed

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Page Preview
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = currentBmp.asImageBitmap(),
                                    contentDescription = "Page ${selectedPageIndex + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Page Operations Bar: Recrop, Rotate, Move Left, Move Right, Delete
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    pendingRawBitmap = scannedPages[selectedPageIndex].rawOriginal
                                    currentQuad = scannedPages[selectedPageIndex].quad ?: DocumentProcessor.detectDocumentCorners(scannedPages[selectedPageIndex].rawOriginal)
                                    currentStep = DocScanStep.CROP_PERSPECTIVE
                                }) {
                                    Icon(imageVector = Icons.Default.Crop, contentDescription = "Recrop Page")
                                }

                                IconButton(onClick = {
                                    val rotated = DocumentProcessor.rotateBitmap(scannedPages[selectedPageIndex].processed, 90f)
                                    val rotatedCropped = DocumentProcessor.rotateBitmap(scannedPages[selectedPageIndex].croppedOriginal, 90f)
                                    scannedPages[selectedPageIndex].processed = rotated
                                    scannedPages[selectedPageIndex].croppedOriginal = rotatedCropped
                                }) {
                                    Icon(imageVector = Icons.Default.RotateRight, contentDescription = "Rotate 90°")
                                }

                                IconButton(
                                    onClick = {
                                        if (selectedPageIndex > 0) {
                                            val temp = scannedPages[selectedPageIndex]
                                            scannedPages[selectedPageIndex] = scannedPages[selectedPageIndex - 1]
                                            scannedPages[selectedPageIndex - 1] = temp
                                            selectedPageIndex--
                                        }
                                    },
                                    enabled = selectedPageIndex > 0
                                ) {
                                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Move Left")
                                }

                                Text(
                                    text = "Page ${selectedPageIndex + 1} of ${scannedPages.size}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                IconButton(
                                    onClick = {
                                        if (selectedPageIndex < scannedPages.lastIndex) {
                                            val temp = scannedPages[selectedPageIndex]
                                            scannedPages[selectedPageIndex] = scannedPages[selectedPageIndex + 1]
                                            scannedPages[selectedPageIndex + 1] = temp
                                            selectedPageIndex++
                                        }
                                    },
                                    enabled = selectedPageIndex < scannedPages.lastIndex
                                ) {
                                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Move Right")
                                }

                                IconButton(onClick = {
                                    scannedPages.removeAt(selectedPageIndex)
                                    if (scannedPages.isEmpty()) {
                                        currentStep = DocScanStep.CAMERA
                                    } else {
                                        selectedPageIndex = selectedPageIndex.coerceAtMost(scannedPages.lastIndex)
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Page",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Horizontal Thumbnail Strip with Add Button
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(scannedPages) { idx, pageItem ->
                                    val isSelected = idx == selectedPageIndex
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) BrandBlue else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { selectedPageIndex = idx }
                                    ) {
                                        Image(
                                            bitmap = pageItem.processed.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.65f),
                                            shape = RoundedCornerShape(topStart = 4.dp),
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            Text(
                                                text = "${idx + 1}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                item {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                            .clickable { currentStep = DocScanStep.CAMERA },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Page", tint = BrandBlue)
                                            Text("Add", fontSize = 11.sp, color = BrandBlue)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action buttons: Save as PDF & Save as Images
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isProcessing = true
                                            processingMessage = "Saving individual images..."
                                            var savedCount = 0
                                            withContext(Dispatchers.IO) {
                                                scannedPages.forEachIndexed { i, page ->
                                                    val name = "scan_page_${System.currentTimeMillis()}_${i + 1}.jpg"
                                                    val f = DocumentProcessor.saveBitmapToStorage(context, page.processed, name)
                                                    if (f != null) savedCount++
                                                }
                                            }
                                            isProcessing = false
                                            Toast.makeText(context, "Saved $savedCount image(s) to storage", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("As Images")
                                }

                                PrimaryButton(
                                    text = "Save as PDF (${scannedPages.size})",
                                    icon = Icons.Default.PictureAsPdf,
                                    onClick = { showFilenameDialog = true },
                                    modifier = Modifier.weight(1.5f),
                                    testTag = "generate_pdf_btn"
                                )
                            }
                        }
                    }
                }

                DocScanStep.SAVED_DOCUMENTS -> {
                    // -------------------------------------------------------------
                    // 5. SAVED DOCUMENTS & FILE MANAGEMENT SCREEN
                    // -------------------------------------------------------------
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = savedDocsSearch,
                            onValueChange = { savedDocsSearch = it },
                            placeholder = { Text("Search saved documents...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val filteredPdfs = remember(savedPdfs, savedDocsSearch) {
                            if (savedDocsSearch.isBlank()) savedPdfs
                            else savedPdfs.filter { it.name.contains(savedDocsSearch, ignoreCase = true) }
                        }

                        if (filteredPdfs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (savedDocsSearch.isBlank()) "No scanned documents yet" else "No matching documents found",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { currentStep = DocScanStep.CAMERA }) {
                                        Text("Scan New Document")
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredPdfs) { file ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PictureAsPdf,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = file.name,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                val sdf = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
                                                Text(
                                                    text = "${StorageUtils.formatFileSize(file.length())} • ${sdf.format(Date(file.lastModified()))}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            // Actions: Share, Open, Rename, Delete
                                            Row {
                                                IconButton(onClick = {
                                                    StorageUtils.shareFile(context, file, "application/pdf")
                                                }) {
                                                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(20.dp))
                                                }

                                                IconButton(onClick = {
                                                    try {
                                                        val uri = FileProvider.getUriForFile(
                                                            context,
                                                            "${context.packageName}.fileprovider",
                                                            file
                                                        )
                                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                                            setDataAndType(uri, "application/pdf")
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "No PDF viewer installed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }) {
                                                    Icon(Icons.Default.OpenInNew, contentDescription = "Open", modifier = Modifier.size(20.dp))
                                                }

                                                IconButton(onClick = {
                                                    fileToRename = file
                                                    newRenameInput = file.nameWithoutExtension
                                                }) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(20.dp))
                                                }

                                                IconButton(onClick = { fileToDelete = file }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isProcessing) {
                LoadingState(message = processingMessage)
            }
        }
    }

    // -------------------------------------------------------------
    // DIALOG: OCR Text Recognition Sheet
    // -------------------------------------------------------------
    if (showOcrDialog && scannedPages.isNotEmpty() && selectedPageIndex in scannedPages.indices) {
        val page = scannedPages[selectedPageIndex]
        val textContent = page.ocrText ?: DocumentProcessor.extractTextPreview(page.processed)

        AlertDialog(
            onDismissRequest = { showOcrDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TextFields, contentDescription = null, tint = BrandBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recognized Document Text", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = textContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Scanned Text", textContent))
                        Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                        showOcrDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Text")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOcrDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // -------------------------------------------------------------
    // DIALOG: Custom PDF Filename & Save
    // -------------------------------------------------------------
    if (showFilenameDialog) {
        val defaultName = remember {
            "Document_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}"
        }
        var fileNameInput by remember { mutableStateOf(defaultName) }

        AlertDialog(
            onDismissRequest = { showFilenameDialog = false },
            title = { Text("Save Document as PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Specify a name for your ${scannedPages.size}-page compiled PDF:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = fileNameInput,
                        onValueChange = { fileNameInput = it },
                        singleLine = true,
                        suffix = { Text(".pdf") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The file will be compiled and saved to your Circle Scanner document storage.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFilenameDialog = false
                        scope.launch {
                            isProcessing = true
                            processingMessage = "Compiling PDF (${scannedPages.size} pages)..."
                            val cleanName = if (fileNameInput.endsWith(".pdf", ignoreCase = true)) {
                                fileNameInput
                            } else {
                                "$fileNameInput.pdf"
                            }
                            val processedBmps = scannedPages.map { it.processed }
                            val file = withContext(Dispatchers.IO) {
                                PdfUtils.imagesToPdf(context, processedBmps, cleanName)
                            }
                            isProcessing = false
                            if (file != null) {
                                newlyCreatedPdfFile = file
                                repository.addHistory("doc_scanner", "Created Scanned PDF", "${file.name} (${scannedPages.size} pages)")
                                showSuccessDialog = true
                            } else {
                                Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Text("Create PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFilenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // -------------------------------------------------------------
    // DIALOG: PDF Creation Success & Actions (Open / Share / Save to Downloads)
    // -------------------------------------------------------------
    if (showSuccessDialog && newlyCreatedPdfFile != null) {
        val pdf = newlyCreatedPdfFile!!
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            icon = {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(44.dp))
            },
            title = {
                Text("PDF Created Successfully!", fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = pdf.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${scannedPages.size} pages • ${StorageUtils.formatFileSize(pdf.length())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                StorageUtils.shareFile(context, pdf, "application/pdf")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }

                        Button(
                            onClick = {
                                try {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "No PDF viewer installed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val uri = DocumentProcessor.savePdfToPublicDownloads(context, pdf)
                            if (uri != null) {
                                Toast.makeText(context, "Saved to Downloads/UtilityHub", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Could not export to Downloads", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save to Public Downloads")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSuccessDialog = false
                    currentStep = DocScanStep.SAVED_DOCUMENTS
                }) {
                    Text("View My Documents")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // -------------------------------------------------------------
    // DIALOG: Rename File
    // -------------------------------------------------------------
    if (fileToRename != null) {
        val target = fileToRename!!
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text("Rename Document") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newRenameInput,
                        onValueChange = { newRenameInput = it },
                        singleLine = true,
                        suffix = { Text(".pdf") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newRenameInput.isNotBlank()) {
                        val newFile = File(target.parentFile, "$newRenameInput.pdf")
                        target.renameTo(newFile)
                        refreshSavedPdfs()
                    }
                    fileToRename = null
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRename = null }) { Text("Cancel") }
            }
        )
    }

    // -------------------------------------------------------------
    // DIALOG: Delete File Confirmation
    // -------------------------------------------------------------
    if (fileToDelete != null) {
        val target = fileToDelete!!
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete Document?") },
            text = { Text("Are you sure you want to delete '${target.name}'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        target.delete()
                        refreshSavedPdfs()
                        fileToDelete = null
                        Toast.makeText(context, "Document deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
