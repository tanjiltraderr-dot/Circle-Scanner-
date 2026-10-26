package com.example.ui.screens.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.UtilityHubApp
import com.example.ui.components.PermissionNoticeCard
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.screens.home.ScannerMode
import com.example.ui.theme.BrandBlue
import com.example.scanner.engine.DocumentScannerEngine
import com.example.utils.DocumentCornerTracker
import com.example.utils.DocumentFilter
import com.example.utils.DocumentProcessor
import com.example.utils.DocumentQuad
import com.example.utils.ImageUtils
import com.example.utils.PdfUtils
import com.example.utils.QrCodeHelper
import com.example.utils.ScannedPageItem
import com.example.utils.StorageUtils
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import com.example.data.local.entity.ScannedDocumentEntity
import com.example.scanner.pdf.PdfConverterUtility
import com.example.scanner.ocr.DocumentOcrService

enum class CamScannerStep {
    CAMERA,
    CROP_EDITOR,
    ENHANCE_PREVIEW,
    PDF_SESSION_REVIEW
}

enum class CameraAppMode(val label: String, val isDocument: Boolean) {
    SCANNER("Scanner", true),
    PDF("PDF", true),
    QR_BARCODE("QR / Barcode", false),
    ID_CARD("ID Card", false),
    PHOTO("Photo", false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScannerScreen(
    initialMode: String = "DOCUMENT_PDF",
    onBack: () -> Unit,
    onNavigateToResult: (content: String, format: String) -> Unit,
    onNavigateToDocumentViewer: (filePath: String, title: String) -> Unit = { _, _ -> },
    onNavigateToDocumentViewerWithId: (filePath: String, title: String, docId: Long) -> Unit = { p, t, _ -> onNavigateToDocumentViewer(p, t) },
    onNavigateToPastScans: () -> Unit = {},
    onNavigateToCategory: ((String) -> Unit)? = null,
    onNavigateToTool: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { UtilityHubApp.instance.repository }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var lastSavedScanFile by remember { mutableStateOf<File?>(null) }
    var lastSavedScanId by remember { mutableLongStateOf(-1L) }
    val scanSettingsPrefs = remember {
        context.getSharedPreferences("doc_scan_settings_prefs", android.content.Context.MODE_PRIVATE)
    }
    var isAutoCropEnabled by remember {
        mutableStateOf(scanSettingsPrefs.getBoolean("pref_auto_edge_detection", true))
    }

    // Current Workflow Step (Camera -> Crop -> Enhance -> Session)
    var currentStep by remember { mutableStateOf(CamScannerStep.CAMERA) }

    // Active Camera Mode (Scanner, PDF, QR / Barcode, ID Card, Photo)
    var activeCameraMode by remember {
        mutableStateOf(
            when (initialMode) {
                "PDF", "PDF_BATCH" -> CameraAppMode.PDF
                "QR_BARCODE" -> CameraAppMode.QR_BARCODE
                "ID_CARD" -> CameraAppMode.ID_CARD
                "IMAGE_SCAN", "PHOTO" -> CameraAppMode.PHOTO
                else -> CameraAppMode.SCANNER
            }
        )
    }

    // QR Code vs Barcode Sub-mode
    var qrBarcodeSubMode by remember { mutableStateOf(QrBarcodeSubMode.QR_CODE) }

    // NID / ID Card Side Sub-mode (Front / Back)
    var idCardSide by remember { mutableStateOf("Front Side") }

    // Glassmorphic All Features Bottom Sheet State
    var showAllFeaturesSheet by remember { mutableStateOf(false) }

    var isFlashOn by remember { mutableStateOf(false) }
    var isFlashSupported by remember { mutableStateOf(false) }
    var isHdEnabled by remember { mutableStateOf(true) }
    var currentZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var minZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var maxZoomRatio by remember { mutableFloatStateOf(5.0f) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }

    // Real-time document boundary detection
    val cornerTracker = remember { DocumentCornerTracker() }
    val scannerEngine = remember { DocumentScannerEngine(context) }
    var autoCaptureCountdownProgress by remember { mutableFloatStateOf(0f) }
    var realtimeQuad by remember { mutableStateOf<DocumentQuad?>(null) }
    var realtimeBufferWidth by remember { mutableIntStateOf(0) }
    var realtimeBufferHeight by remember { mutableIntStateOf(0) }
    var isRealtimeReliable by remember { mutableStateOf(false) }
    var isRealtimeStable by remember { mutableStateOf(false) }
    var isFrameTooDark by remember { mutableStateOf(false) }
    var isFrameTooBright by remember { mutableStateOf(false) }
    var isFrameBlurry by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "Camera permission needed to scan", Toast.LENGTH_SHORT).show()
        }
    }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("Processing...") }

    // Document Scanning Pipeline Data
    var pendingRawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCropQuad by remember { mutableStateOf<DocumentQuad?>(null) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var enhancedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeFilter by remember { mutableStateOf(DocumentFilter.CAMSCANNER) }
    var showCompareOriginal by remember { mutableStateOf(false) }

    // Multi-page batch session storage
    val documentPages = remember { mutableStateListOf<ScannedPageItem>() }
    var selectedPageIndex by remember { mutableIntStateOf(0) }

    // Document Naming
    var documentName by remember {
        mutableStateOf(
            "CamScanner " + SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.getDefault()).format(Date())
        )
    }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(documentName) }

    // Dedicated Background Executor for CameraX Frame Analysis (preventing UI thread block)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                cameraExecutor.shutdown()
            } catch (_: Exception) {}
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
            } catch (_: Exception) {}
            scannerEngine.resetTracking()
        }
    }

    // Modals
    var showOcrModal by remember { mutableStateOf(false) }
    var ocrExtractedText by remember { mutableStateOf("") }
    var showSaveSuccessDialog by remember { mutableStateOf(false) }
    var savedPdfFile by remember { mutableStateOf<File?>(null) }

    // Card capture state
    var capturedCardBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showCardResultDialog by remember { mutableStateOf(false) }

    // Photo capture state
    var capturedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showImageResultDialog by remember { mutableStateOf(false) }

    // Synchronize flash
    LaunchedEffect(isFlashOn, cameraControl) {
        if (isFlashSupported) {
            cameraControl?.enableTorch(isFlashOn)
        }
    }

    // Helper vibration
    fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(30)
            }
        } catch (_: Exception) {}
    }

    // Gallery Picker for single or multi-images
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                processingMessage = "Importing image(s)..."
                val firstUri = uris.first()
                val importedBmp = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(firstUri)?.use { stream ->
                            val raw = BitmapFactory.decodeStream(stream)
                            context.contentResolver.openInputStream(firstUri)?.use { exifStream ->
                                if (raw != null) DocumentProcessor.fixOrientation(raw, exifStream) else null
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                isProcessing = false

                if (importedBmp != null) {
                    if (activeCameraMode == CameraAppMode.QR_BARCODE) {
                        val decoded = QrCodeHelper.decodeBitmap(importedBmp)
                        if (decoded != null) {
                            vibrate()
                            repository.addHistory("qr_scanner", "QR from Gallery", decoded)
                            onNavigateToResult(decoded, "QR_CODE")
                        } else {
                            Toast.makeText(context, "No QR or barcode found in photo", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Automatically auto-crop, auto-straighten, and enhance imported image
                        scope.launch {
                            isProcessing = true
                            processingMessage = "Auto cropping & straightening..."
                            val autoCropResult = withContext(Dispatchers.IO) {
                                com.example.scanner.engine.AutoCropEngine.cropDocument(
                                    context = context,
                                    rawBitmap = importedBmp,
                                    preferredMode = com.example.scanner.engine.EnhancementMode.CAMSCANNER_MAGIC
                                )
                            }

                            if (autoCropResult.isSuccess && autoCropResult.croppedBitmap != null && autoCropResult.enhancedBitmap != null) {
                                pendingRawBitmap = importedBmp
                                pendingCropQuad = autoCropResult.quad
                                croppedBitmap = autoCropResult.croppedBitmap
                                enhancedBitmap = autoCropResult.enhancedBitmap
                                activeFilter = DocumentFilter.CAMSCANNER

                                val persistResult = withContext(Dispatchers.IO) {
                                    scannerEngine.saveScanToDatabase(
                                        processedBitmap = autoCropResult.enhancedBitmap!!,
                                        rawBitmap = importedBmp,
                                        title = documentName,
                                        filterUsed = "CamScanner Magic"
                                    )
                                }
                                lastSavedScanId = persistResult.scanId
                                lastSavedScanFile = File(persistResult.fullImagePath)
                                repository.addHistory("doc_scanner", documentName, "scan_id:${persistResult.scanId}|${lastSavedScanFile!!.name}")

                                isProcessing = false
                                currentStep = CamScannerStep.ENHANCE_PREVIEW
                            } else {
                                // False crop prevented or manual boundary required: open crop editor without saving
                                pendingRawBitmap = importedBmp
                                pendingCropQuad = autoCropResult.quad ?: com.example.scanner.engine.AutoCropEngine.getInitialGuideQuad(importedBmp.width, importedBmp.height)
                                Toast.makeText(context, "Adjust corners manually to crop document", Toast.LENGTH_SHORT).show()
                                isProcessing = false
                                currentStep = CamScannerStep.CROP_EDITOR
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, "Could not load selected image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Trigger photo capture from CameraX
    fun executeCapture() {
        val capture = imageCapture ?: return
        if (isProcessing) return
        if (!scannerEngine.canTriggerAutoCapture()) return

        scannerEngine.notifyCaptureStarted()
        isProcessing = true
        processingMessage = "Capturing..."
        vibrate()

        val photoFile = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
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

                        withContext(Dispatchers.Main) {
                            if (raw != null) {
                                when (activeCameraMode) {
                                    CameraAppMode.SCANNER, CameraAppMode.PDF -> {
                                        scope.launch {
                                            isProcessing = true
                                            processingMessage = "Analyzing document boundaries..."

                                            val capturedRaw = raw!!

                                            try {
                                                // Execute AutoCropEngine pipeline with multi-strategy CV, validation & false-crop prevention
                                                val autoCropResult = withContext(Dispatchers.IO) {
                                                    com.example.scanner.engine.AutoCropEngine.cropDocument(
                                                        context = context,
                                                        rawBitmap = capturedRaw,
                                                        previewQuad = realtimeQuad,
                                                        previewWidth = realtimeBufferWidth,
                                                        previewHeight = realtimeBufferHeight,
                                                        preferredMode = com.example.scanner.engine.EnhancementMode.CAMSCANNER_MAGIC
                                                    )
                                                }

                                                if (isAutoCropEnabled && autoCropResult.isSuccess) {
                                                    pendingRawBitmap = capturedRaw
                                                    pendingCropQuad = autoCropResult.quad
                                                    croppedBitmap = autoCropResult.croppedBitmap
                                                    enhancedBitmap = autoCropResult.enhancedBitmap
                                                    activeFilter = DocumentFilter.CAMSCANNER

                                                    val persistResult = withContext(Dispatchers.IO) {
                                                        scannerEngine.saveScanToDatabase(
                                                            processedBitmap = autoCropResult.enhancedBitmap!!,
                                                            rawBitmap = capturedRaw,
                                                            title = documentName,
                                                            filterUsed = "Magic Color"
                                                        )
                                                    }
                                                    lastSavedScanId = persistResult.scanId
                                                    lastSavedScanFile = File(persistResult.fullImagePath)
                                                    repository.addHistory("doc_scanner", documentName, "scan_id:${persistResult.scanId}|${lastSavedScanFile!!.name}")

                                                    isProcessing = false
                                                    currentStep = CamScannerStep.ENHANCE_PREVIEW
                                                } else {
                                                    // False crop prevented or manual adjustment required:
                                                    // Keep original raw image and open Crop Editor
                                                    if (isAutoCropEnabled) {
                                                        val reasonMsg = when (autoCropResult.status) {
                                                            com.example.scanner.engine.AutoCropStatus.LOW_CONFIDENCE ->
                                                                "Boundary uncertain. Please adjust corners manually or retake."
                                                            com.example.scanner.engine.AutoCropStatus.BLURRY_CAPTURE ->
                                                                "Image appears blurry. Adjust corners or retake."
                                                            com.example.scanner.engine.AutoCropStatus.INVALID_GEOMETRY ->
                                                                "Unusual shape detected. Adjust corners manually."
                                                            else -> "Adjust corners manually or retake."
                                                        }
                                                        Toast.makeText(context, reasonMsg, Toast.LENGTH_SHORT).show()
                                                    }
                                                    pendingRawBitmap = capturedRaw
                                                    pendingCropQuad = autoCropResult.quad ?: com.example.scanner.engine.AutoCropEngine.getInitialGuideQuad(capturedRaw.width, capturedRaw.height)
                                                    isProcessing = false
                                                    currentStep = CamScannerStep.CROP_EDITOR
                                                }
                                            } finally {
                                                scannerEngine.notifyCaptureFinished()
                                            }
                                        }
                                    }
                                    CameraAppMode.ID_CARD -> {
                                        isProcessing = false
                                        scannerEngine.notifyCaptureFinished()
                                        capturedCardBitmap = raw
                                        showCardResultDialog = true
                                    }
                                    CameraAppMode.PHOTO -> {
                                        isProcessing = false
                                        scannerEngine.notifyCaptureFinished()
                                        capturedImageBitmap = raw
                                        showImageResultDialog = true
                                    }
                                    CameraAppMode.QR_BARCODE -> {
                                        isProcessing = false
                                        scannerEngine.notifyCaptureFinished()
                                    }
                                }
                            } else {
                                isProcessing = false
                                scannerEngine.notifyCaptureFinished()
                                Toast.makeText(context, "Could not decode captured photo", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isProcessing = false
                    scannerEngine.notifyCaptureFinished()
                    Toast.makeText(context, "Capture error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    LaunchedEffect(scannerEngine, isAutoCropEnabled, isProcessing, currentStep) {
        scannerEngine.autoCaptureEnabled = isAutoCropEnabled
        scannerEngine.detectionListener = object : DocumentScannerEngine.DetectionListener {
            override fun onDocumentDetected(quad: DocumentQuad, confidence: Float, isStable: Boolean) {
                realtimeQuad = quad
                isRealtimeReliable = confidence >= 0.65f
                isRealtimeStable = isStable
            }

            override fun onStabilityProgress(progress: Float) {
                autoCaptureCountdownProgress = progress
            }

            override fun onFrameConditionFeedback(
                isTooDark: Boolean,
                isTooBright: Boolean,
                isBlurry: Boolean,
                isMoving: Boolean
            ) {
                isFrameTooDark = isTooDark
                isFrameTooBright = isTooBright
                isFrameBlurry = isBlurry
            }

            override fun onAutoCaptureReady() {
                if (currentStep == CamScannerStep.CAMERA && !isProcessing && isAutoCropEnabled) {
                    executeCapture()
                }
            }

            override fun onDocumentLost() {
                realtimeQuad = null
                isRealtimeReliable = false
                isRealtimeStable = false
                autoCaptureCountdownProgress = 0f
                isFrameTooDark = false
                isFrameTooBright = false
                isFrameBlurry = false
            }
        }
    }

    // Save final document (PDF compilation + ML Kit OCR + Room persistence)
    fun finalizeAndSaveDocument() {
        scope.launch {
            isProcessing = true
            processingMessage = "Compiling PDF..."

            val pagesToSave = if (documentPages.isNotEmpty()) {
                documentPages.map { it.processed }
            } else if (enhancedBitmap != null) {
                listOf(enhancedBitmap!!)
            } else {
                emptyList()
            }

            if (pagesToSave.isEmpty()) {
                isProcessing = false
                Toast.makeText(context, "No pages to save", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 1. PDF Conversion Utility
            val pdfResult = withContext(Dispatchers.IO) {
                PdfConverterUtility.convertBitmapsToPdf(
                    context = context,
                    bitmaps = pagesToSave,
                    fileNamePrefix = documentName
                )
            }

            // 2. OCR Text Extraction Service
            processingMessage = "Extracting text with OCR..."
            val ocrResult = withContext(Dispatchers.IO) {
                DocumentOcrService.recognizeMultiPageDocument(
                    bitmaps = pagesToSave,
                    documentTitle = documentName
                )
            }

            isProcessing = false
            if (pdfResult != null) {
                savedPdfFile = pdfResult.file

                // 3. Persist metadata into Room database schema
                val docEntity = ScannedDocumentEntity(
                    title = documentName,
                    originalFileName = pdfResult.file.name,
                    processedImagePath = pdfResult.file.absolutePath,
                    rawImagePath = null,
                    pdfFilePath = pdfResult.file.absolutePath,
                    thumbnailPath = pdfResult.thumbnailFile?.absolutePath,
                    pageCount = pdfResult.pageCount,
                    fileSizeBytes = pdfResult.fileSizeBytes,
                    ocrExtractedText = ocrResult.fullClipboardText.ifBlank { null },
                    filterUsed = activeFilter.label,
                    createdAtTimestamp = System.currentTimeMillis()
                )
                val insertedDocId = repository.insertScannedDocument(docEntity)
                lastSavedScanId = insertedDocId

                repository.addHistory(
                    "doc_scanner",
                    "$documentName (${pagesToSave.size} pages)",
                    "scan_id:$insertedDocId|${pdfResult.file.name}"
                )
                showSaveSuccessDialog = true
            } else {
                Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Handle Back Press across the CamScanner workflow
    BackHandler {
        when (currentStep) {
            CamScannerStep.PDF_SESSION_REVIEW -> {
                currentStep = CamScannerStep.CAMERA
            }
            CamScannerStep.ENHANCE_PREVIEW -> {
                currentStep = CamScannerStep.CAMERA
            }
            CamScannerStep.CROP_EDITOR -> {
                currentStep = CamScannerStep.CAMERA
            }
            CamScannerStep.CAMERA -> {
                onBack()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CamScannerDarkBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentStep) {
                // ==========================================
                // 1. CAMERA SCANNER SCREEN (Frame 00:00 - 00:04)
                // ==========================================
                CamScannerStep.CAMERA -> {
                    if (!hasCameraPermission) {
                        PermissionNoticeCard(
                            title = "Camera Access Required",
                            explanation = "Circle Scanner needs camera permission for CamScanner document detection and QR scanning.",
                            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        // Real CameraX Stream Engine
                        key(lensFacing) {
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
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                            .build()
                                        imageCapture = capture

                                        val analysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .setTargetResolution(android.util.Size(640, 480))
                                            .build()

                                        val multiReader = MultiFormatReader().apply {
                                            val formats = listOf(
                                                BarcodeFormat.QR_CODE,
                                                BarcodeFormat.EAN_13,
                                                BarcodeFormat.EAN_8,
                                                BarcodeFormat.UPC_A,
                                                BarcodeFormat.UPC_E,
                                                BarcodeFormat.CODE_128,
                                                BarcodeFormat.CODE_39,
                                                BarcodeFormat.ITF,
                                                BarcodeFormat.DATA_MATRIX,
                                                BarcodeFormat.PDF_417,
                                                BarcodeFormat.AZTEC
                                            )
                                            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to formats))
                                        }

                                        var hasFoundCode = false
                                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                            try {
                                                if (activeCameraMode == CameraAppMode.QR_BARCODE && !hasFoundCode) {
                                                    val buffer = imageProxy.planes[0].buffer
                                                    val data = ByteArray(buffer.remaining())
                                                    buffer.get(data)
                                                    val width = imageProxy.width
                                                    val height = imageProxy.height

                                                    try {
                                                        val source = PlanarYUVLuminanceSource(
                                                            data, width, height, 0, 0, width, height, false
                                                        )
                                                        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                                                        val result = multiReader.decodeWithState(binaryBitmap)
                                                        val text = result.text
                                                        if (!text.isNullOrBlank()) {
                                                            hasFoundCode = true
                                                            scope.launch(Dispatchers.Main) {
                                                                vibrate()
                                                                repository.addHistory(
                                                                    "qr_scanner",
                                                                    "Scanned ${result.barcodeFormat.name}",
                                                                    text
                                                                )
                                                                onNavigateToResult(text, result.barcodeFormat.name)
                                                            }
                                                        }
                                                    } catch (_: NotFoundException) {
                                                    } catch (_: Exception) {
                                                    } finally {
                                                        multiReader.reset()
                                                    }
                                                } else if (activeCameraMode.isDocument && !isProcessing) {
                                                    val yPlane = imageProxy.planes[0]
                                                    val trackedQuad = scannerEngine.processAnalysisFrame(
                                                        yBuffer = yPlane.buffer,
                                                        width = imageProxy.width,
                                                        height = imageProxy.height,
                                                        rowStride = yPlane.rowStride,
                                                        pixelStride = yPlane.pixelStride,
                                                        rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                                    )
                                                    val rot = imageProxy.imageInfo.rotationDegrees
                                                    val bufW = if (rot == 90 || rot == 270) imageProxy.height else imageProxy.width
                                                    val bufH = if (rot == 90 || rot == 270) imageProxy.width else imageProxy.height
                                                    scope.launch(Dispatchers.Main) {
                                                        realtimeBufferWidth = bufW
                                                        realtimeBufferHeight = bufH
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            } finally {
                                                imageProxy.close()
                                            }
                                        }

                                        try {
                                            cameraProvider.unbindAll()
                                            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                                            val camera = cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview,
                                                capture,
                                                analysis
                                            )
                                            cameraControl = camera.cameraControl
                                            isFlashSupported = camera.cameraInfo.hasFlashUnit()
                                            camera.cameraInfo.zoomState.observe(lifecycleOwner) { zState ->
                                                if (zState != null) {
                                                    minZoomRatio = zState.minZoomRatio
                                                    maxZoomRatio = zState.maxZoomRatio.coerceAtMost(8f)
                                                    currentZoomRatio = zState.zoomRatio
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(cameraControl) {
                                        detectTransformGestures { _, _, zoom, _ ->
                                            cameraControl?.let { ctrl ->
                                                val newZoom = (currentZoomRatio * zoom).coerceIn(minZoomRatio, maxZoomRatio)
                                                currentZoomRatio = newZoom
                                                ctrl.setZoomRatio(newZoom)
                                            }
                                        }
                                    }
                                    .testTag("camscanner_preview")
                            )
                        }

                        // 1. Permanent, Rock-Solid Category Viewfinder (replaces jittering live polygon)
                        PermanentCategoryViewfinder(
                            mode = activeCameraMode,
                            qrBarcodeSubMode = qrBarcodeSubMode,
                            idCardSide = idCardSide,
                            pageCount = documentPages.size,
                            modifier = Modifier.fillMaxSize()
                        )

                        // 2. Sub-mode Selector for QR Code vs Barcode (when in QR_BARCODE mode)
                        if (activeCameraMode == CameraAppMode.QR_BARCODE) {
                            QrBarcodeSelectorSwitch(
                                activeSubMode = qrBarcodeSubMode,
                                onSubModeSelected = { qrBarcodeSubMode = it },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(top = 58.dp)
                                    .testTag("qr_barcode_toggle_switch")
                            )
                        } else if (activeCameraMode == CameraAppMode.ID_CARD) {
                            // Front / Back Side Switcher for ID Card
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF18181B).copy(alpha = 0.9f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(top = 58.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf("Front Side", "Back Side").forEach { side ->
                                        val isSel = (idCardSide == side)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (isSel) Color.White else Color.Transparent)
                                                .clickable { idCardSide = side }
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = side,
                                                color = if (isSel) Color.Black else Color.White.copy(alpha = 0.8f),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Top Camera Toolbar (Back, Flash, HD, Flip)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Back arrow button
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .testTag("camera_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Right controls: Flash, HD, Camera Flip
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Flash Toggle
                                if (isFlashSupported) {
                                    IconButton(
                                        onClick = { isFlashOn = !isFlashOn },
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .testTag("camera_flash_toggle")
                                    ) {
                                        Icon(
                                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                            contentDescription = "Flash",
                                            tint = if (isFlashOn) Color(0xFFFBBF24) else Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                // HD Quality Toggle Badge
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isHdEnabled) CamScannerTeal.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { isHdEnabled = !isHdEnabled }
                                ) {
                                    Text(
                                        text = "HD",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }

                                // Auto-Crop vs Manual Crop Toggle Badge
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isAutoCropEnabled) Color(0xFF10B981).copy(alpha = 0.85f) else Color(0xFFF59E0B).copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            isAutoCropEnabled = !isAutoCropEnabled
                                            scanSettingsPrefs.edit().putBoolean("pref_auto_edge_detection", isAutoCropEnabled).apply()
                                            Toast.makeText(
                                                context,
                                                if (isAutoCropEnabled) "Auto-Crop: Enabled (auto edge detection)" else "Manual Crop: Enabled (adjust areas manually)",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        .testTag("camera_auto_crop_toggle")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isAutoCropEnabled) Icons.Default.CropFree else Icons.Default.Crop,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (isAutoCropEnabled) "Auto" else "Manual",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                // Flip Camera
                                IconButton(
                                    onClick = {
                                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                                            CameraSelector.LENS_FACING_FRONT
                                        else
                                            CameraSelector.LENS_FACING_BACK
                                    },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FlipCameraAndroid,
                                        contentDescription = "Flip Camera",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // Bottom Area: Mode Selector & Shutter Bar (Frames 00:00 - 00:04)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Horizontally arranged mode selector directly above shutter
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                                    .testTag("camscanner_mode_selector"),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(CameraAppMode.entries.toTypedArray()) { mode ->
                                    val isSelected = activeCameraMode == mode
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable {
                                                if (activeCameraMode != mode) {
                                                    activeCameraMode = mode
                                                    vibrate()
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = mode.label,
                                            color = if (isSelected) CamScannerTealBright else Color.White.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .width(18.dp)
                                                    .height(3.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(CamScannerTealBright)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(7.dp))
                                        }
                                    }
                                }
                            }

                            // Shutter Controls Row (Matching CamScanner UX)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: All Features Glass Sheet Launcher
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { showAllFeaturesSheet = true }
                                        .testTag("btn_all_features")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "All features",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "All features",
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 10.sp
                                    )
                                }

                                // Center: Signature Shutter Button with Auto-Capture Stability Ring
                                Box(
                                    modifier = Modifier.size(86.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (autoCaptureCountdownProgress > 0f && isAutoCropEnabled) {
                                        CircularProgressIndicator(
                                            progress = { autoCaptureCountdownProgress },
                                            modifier = Modifier.size(86.dp),
                                            color = CamScannerTealBright,
                                            strokeWidth = 3.5.dp,
                                            trackColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .clickable { executeCapture() }
                                            .testTag("camscanner_shutter_btn"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(CamScannerTeal),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Inner white center circle
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                            )
                                        }
                                    }
                                }

                                // Right: Gallery / Import with live badge
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { galleryPicker.launch("image/*") }
                                        .testTag("gallery_import_btn")
                                ) {
                                    Box {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = "Gallery",
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(26.dp)
                                        )
                                        if (documentPages.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 6.dp, y = (-4).dp)
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(CamScannerTealBright),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${documentPages.size}",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Gallery",
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 2. CROP EDITOR SCREEN (Frame 00:05 - 00:09)
                // ==========================================
                CamScannerStep.CROP_EDITOR -> {
                    val rawBmp = pendingRawBitmap
                    val currentCropQuad = pendingCropQuad ?: (rawBmp?.let { DocumentProcessor.detectDocumentCorners(it) })

                    if (rawBmp != null && currentCropQuad != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CamScannerDarkBg)
                        ) {
                            // Top Bar: Back arrow and "Crop" title
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { currentStep = CamScannerStep.CAMERA },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to Camera",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Crop",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }

                            // Center: Full-Screen Interactive Quad Editor
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                InteractiveQuadEditor(
                                    bitmap = rawBmp,
                                    quad = currentCropQuad,
                                    onQuadChanged = { updatedQuad ->
                                        pendingCropQuad = updatedQuad
                                    }
                                )
                            }

                            // Bottom Bar: Left, Right, All, Reset, Next FAB (Matching Frame 00:05)
                            CamScannerCropBottomBar(
                                onRotateLeft = {
                                    val rot = DocumentProcessor.rotateBitmap(rawBmp, 270f)
                                    pendingRawBitmap = rot
                                    pendingCropQuad = DocumentProcessor.detectDocumentCorners(rot)
                                },
                                onRotateRight = {
                                    val rot = DocumentProcessor.rotateBitmap(rawBmp, 90f)
                                    pendingRawBitmap = rot
                                    pendingCropQuad = DocumentProcessor.detectDocumentCorners(rot)
                                },
                                onRetake = {
                                    pendingRawBitmap = null
                                    pendingCropQuad = null
                                    currentStep = CamScannerStep.CAMERA
                                },
                                onSelectAll = {
                                    val w = rawBmp.width.toFloat()
                                    val h = rawBmp.height.toFloat()
                                    val fullQuad = DocumentQuad(
                                        PointF(0f, 0f),
                                        PointF(w, 0f),
                                        PointF(w, h),
                                        PointF(0f, h),
                                        isAutoDetected = false
                                    )
                                    pendingCropQuad = fullQuad
                                },
                                onReset = {
                                    val autoQuad = DocumentProcessor.detectDocumentCorners(rawBmp)
                                    pendingCropQuad = autoQuad
                                },
                                onNext = {
                                    // Execute perspective correction and enhance
                                    scope.launch {
                                        isProcessing = true
                                        processingMessage = "Correcting perspective..."
                                        val quad = pendingCropQuad ?: DocumentProcessor.detectDocumentCorners(rawBmp)
                                        val cropped = withContext(Dispatchers.IO) {
                                            com.example.scanner.engine.PerspectiveCorrectionEngine.transformPerspective(rawBmp, quad)
                                        }
                                        val enhanced = withContext(Dispatchers.IO) {
                                            scannerEngine.enhanceDocument(cropped, activeFilter)
                                        }
                                        croppedBitmap = cropped
                                        enhancedBitmap = enhanced

                                        val persistResult = withContext(Dispatchers.IO) {
                                            scannerEngine.saveScanToDatabase(
                                                processedBitmap = enhanced,
                                                rawBitmap = rawBmp,
                                                title = documentName,
                                                filterUsed = activeFilter.name
                                            )
                                        }
                                        lastSavedScanId = persistResult.scanId
                                        lastSavedScanFile = File(persistResult.fullImagePath)
                                        repository.addHistory("doc_scanner", documentName, "scan_id:${persistResult.scanId}|${lastSavedScanFile!!.name}")

                                        isProcessing = false
                                        currentStep = CamScannerStep.ENHANCE_PREVIEW
                                    }
                                }
                            )
                        }
                    }
                }

                // ==========================================
                // 3. ENHANCE PREVIEW SCREEN (Frame 00:10 - 00:17)
                // ==========================================
                CamScannerStep.ENHANCE_PREVIEW -> {
                    val crBmp = croppedBitmap
                    val enhBmp = enhancedBitmap

                    if (crBmp != null && enhBmp != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CamScannerDarkBg)
                        ) {
                            // Top Bar: Back, Document Title + Edit, Compare Button, View Page Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    IconButton(
                                        onClick = { currentStep = CamScannerStep.CAMERA },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back to Camera",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable {
                                                renameInput = documentName
                                                showRenameDialog = true
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = documentName,
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
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
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Compare Button (Press & Hold or Click)
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (showCompareOriginal) CamScannerTeal else Color(0xFF262626),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { showCompareOriginal = !showCompareOriginal }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Compare,
                                                contentDescription = "Compare",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Compare",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // View Page Button (opens full document viewer)
                                    if (lastSavedScanFile != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = CamScannerTeal,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable {
                                                    onNavigateToDocumentViewerWithId(lastSavedScanFile!!.absolutePath, documentName, lastSavedScanId)
                                                }
                                                .testTag("open_view_page_btn")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Visibility,
                                                    contentDescription = "View Page",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "View",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Center: Display Corrected Document
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F0F0F)),
                                contentAlignment = Alignment.Center
                            ) {
                                val displayBmp = if (showCompareOriginal && pendingRawBitmap != null) {
                                    pendingRawBitmap!!
                                } else {
                                    enhBmp
                                }
                                Image(
                                    bitmap = displayBmp.asImageBitmap(),
                                    contentDescription = "Corrected Document Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (showCompareOriginal) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.75f),
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 12.dp)
                                    ) {
                                        Text(
                                            text = "Original Photo",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Multi-page thumbnail strip with '+' Add Page button directly below the photo!
                            MultiPageThumbnailStrip(
                                pages = documentPages,
                                currentBitmap = enhBmp,
                                selectedIndex = selectedPageIndex,
                                onSelectPage = { idx ->
                                    if (idx in documentPages.indices) {
                                        selectedPageIndex = idx
                                        val page = documentPages[idx]
                                        croppedBitmap = page.croppedOriginal
                                        enhancedBitmap = page.processed
                                        activeFilter = page.activeFilter
                                        pendingRawBitmap = page.rawOriginal
                                        pendingCropQuad = page.quad
                                    }
                                },
                                onAddPage = {
                                    // Commit current page to documentPages
                                    val pageItem = ScannedPageItem(
                                        rawOriginal = pendingRawBitmap ?: crBmp,
                                        croppedOriginal = crBmp,
                                        processed = enhBmp,
                                        activeFilter = activeFilter,
                                        quad = pendingCropQuad
                                    )
                                    if (selectedPageIndex < documentPages.size) {
                                        documentPages[selectedPageIndex] = pageItem
                                    } else {
                                        documentPages.add(pageItem)
                                    }
                                    selectedPageIndex = documentPages.size
                                    // Return directly to camera scanner page with permanent border
                                    currentStep = CamScannerStep.CAMERA
                                    Toast.makeText(context, "Page ${documentPages.size} saved. Frame next page inside border.", Toast.LENGTH_SHORT).show()
                                },
                                onDeletePage = { idx ->
                                    if (documentPages.size > 1 && idx in documentPages.indices) {
                                        documentPages.removeAt(idx)
                                        val newIdx = (idx - 1).coerceAtLeast(0)
                                        selectedPageIndex = newIdx
                                        val page = documentPages[newIdx]
                                        croppedBitmap = page.croppedOriginal
                                        enhancedBitmap = page.processed
                                        activeFilter = page.activeFilter
                                    }
                                }
                            )

                            // Bottom Bar: Filter Carousel Strip + Tools (Left, Markup, Extract Text, Recrop, Done)
                            CamScannerEnhanceBottomBar(
                                currentFilter = activeFilter,
                                onFilterSelected = { filter ->
                                    activeFilter = filter
                                    scope.launch {
                                        val updated = withContext(Dispatchers.IO) {
                                            DocumentProcessor.applyFilter(crBmp, filter)
                                        }
                                        enhancedBitmap = updated
                                    }
                                },
                                onRotateLeft = {
                                    val rot = DocumentProcessor.rotateBitmap(crBmp, 270f)
                                    croppedBitmap = rot
                                    scope.launch {
                                        val updated = withContext(Dispatchers.IO) {
                                            DocumentProcessor.applyFilter(rot, activeFilter)
                                        }
                                        enhancedBitmap = updated
                                    }
                                },
                                onMarkup = {
                                    Toast.makeText(context, "Document signature/markup enabled", Toast.LENGTH_SHORT).show()
                                },
                                onExtractText = {
                                    scope.launch {
                                        isProcessing = true
                                        val text = DocumentOcrService.recognizeText(enhBmp)
                                        isProcessing = false
                                        ocrExtractedText = text
                                        showOcrModal = true
                                    }
                                },
                                onRecrop = {
                                    currentStep = CamScannerStep.CROP_EDITOR
                                },
                                onDone = {
                                    // Save page item
                                    val pageItem = ScannedPageItem(
                                        rawOriginal = pendingRawBitmap ?: crBmp,
                                        croppedOriginal = crBmp,
                                        processed = enhBmp,
                                        activeFilter = activeFilter,
                                        quad = pendingCropQuad
                                    )
                                    if (selectedPageIndex < documentPages.size) {
                                        documentPages[selectedPageIndex] = pageItem
                                    } else {
                                        documentPages.add(pageItem)
                                    }
                                    selectedPageIndex = documentPages.size - 1
                                    Toast.makeText(context, "Page ${documentPages.size} ready. Reviewing session...", Toast.LENGTH_SHORT).show()
                                    currentStep = CamScannerStep.PDF_SESSION_REVIEW
                                }
                            )
                        }
                    }
                }

                // ==========================================
                // 4. PDF SESSION REVIEW (Multi-Page Workflow)
                // Camera -> Capture -> Crop -> Correct Perspective -> Enhance -> Add Page or Finish -> PDF Preview/Save
                // ==========================================
                CamScannerStep.PDF_SESSION_REVIEW -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CamScannerDarkBg)
                    ) {
                        // Top Bar: Back, Title + Rename, Save PDF Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = { currentStep = CamScannerStep.CAMERA },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable {
                                            renameInput = documentName
                                            showRenameDialog = true
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$documentName (${documentPages.size} p.)",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
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
                            }

                            Button(
                                onClick = { finalizeAndSaveDocument() },
                                colors = ButtonDefaults.buttonColors(containerColor = CamScannerTeal),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save PDF", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Pages Horizontal List with Reordering and Controls
                        LazyRow(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(documentPages) { idx, page ->
                                Box(
                                    modifier = Modifier
                                        .width(230.dp)
                                        .fillMaxHeight(0.88f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black)
                                        .border(
                                            2.dp,
                                            if (selectedPageIndex == idx) CamScannerTealBright else Color.White.copy(alpha = 0.2f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedPageIndex = idx }
                                ) {
                                    Image(
                                        bitmap = page.processed.asImageBitmap(),
                                        contentDescription = "Page ${idx + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(bottom = 36.dp, top = 36.dp, start = 8.dp, end = 8.dp)
                                    )

                                    // Top Controls on Card: Move Left, Move Right, Delete
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.TopCenter)
                                            .padding(6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left arrow to reorder page left
                                        if (idx > 0) {
                                            IconButton(
                                                onClick = {
                                                    val temp = documentPages[idx]
                                                    documentPages[idx] = documentPages[idx - 1]
                                                    documentPages[idx - 1] = temp
                                                    selectedPageIndex = idx - 1
                                                },
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Move Left",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.size(28.dp))
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            // Right arrow to reorder page right
                                            if (idx < documentPages.size - 1) {
                                                IconButton(
                                                    onClick = {
                                                        val temp = documentPages[idx]
                                                        documentPages[idx] = documentPages[idx + 1]
                                                        documentPages[idx + 1] = temp
                                                        selectedPageIndex = idx + 1
                                                    },
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.ArrowForward,
                                                        contentDescription = "Move Right",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            // Delete Page Button
                                            IconButton(
                                                onClick = {
                                                    if (documentPages.size > 1) {
                                                        documentPages.removeAt(idx)
                                                        selectedPageIndex = (selectedPageIndex - 1).coerceAtLeast(0)
                                                    } else {
                                                        Toast.makeText(context, "Cannot delete only page", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(Color.Red.copy(alpha = 0.75f), CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Delete Page",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Bottom Row: Page Number Badge + Re-crop Action
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.Black.copy(alpha = 0.75f)
                                        ) {
                                            Text(
                                                text = "${idx + 1} / ${documentPages.size}",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = CamScannerTeal.copy(alpha = 0.85f),
                                            modifier = Modifier.clickable {
                                                pendingRawBitmap = page.rawOriginal
                                                pendingCropQuad = page.quad ?: DocumentProcessor.detectDocumentCorners(page.rawOriginal)
                                                currentStep = CamScannerStep.CROP_EDITOR
                                            }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Crop, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Recrop", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Actions: Add Page (Camera), Add Page (Gallery), Finish & Save PDF
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { currentStep = CamScannerStep.CAMERA },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Camera", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { galleryPicker.launch("image/*") },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Gallery", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { finalizeAndSaveDocument() },
                                modifier = Modifier.weight(1.3f),
                                colors = ButtonDefaults.buttonColors(containerColor = CamScannerTeal)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Finish PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Universal Busy Processing Overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CamScannerTealBright)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(processingMessage, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    // Rename Document Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Document Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            documentName = renameInput.trim()
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Save", color = CamScannerTeal)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // OCR Modal
    if (showOcrModal) {
        AlertDialog(
            onDismissRequest = { showOcrModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TextFields, contentDescription = null, tint = CamScannerTeal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Extracted Text", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = ocrExtractedText.ifBlank { "No clear text recognized in document." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(ocrExtractedText))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        showOcrModal = false
                    }
                ) {
                    Text("Copy All", color = CamScannerTeal)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOcrModal = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Save PDF Success Dialog
    if (showSaveSuccessDialog && savedPdfFile != null) {
        AlertDialog(
            onDismissRequest = {
                showSaveSuccessDialog = false
                currentStep = CamScannerStep.CAMERA
                documentPages.clear()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CamScannerTeal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Document Saved!", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("PDF created successfully:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        savedPdfFile!!.name,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = CamScannerTeal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Location: App Storage / pdfs/", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveSuccessDialog = false
                        val path = savedPdfFile?.absolutePath ?: ""
                        if (path.isNotBlank()) {
                            onNavigateToDocumentViewerWithId(path, documentName, lastSavedScanId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScannerTeal)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View Document")
                }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            if (savedPdfFile != null) {
                                StorageUtils.shareFile(context, savedPdfFile!!, "application/pdf")
                            }
                        }
                    ) {
                        Text("Share", color = Color.White.copy(alpha = 0.9f))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = {
                            showSaveSuccessDialog = false
                            currentStep = CamScannerStep.CAMERA
                            documentPages.clear()
                        }
                    ) {
                        Text("New Scan", color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
        )
    }

    // Card Result Dialog
    if (showCardResultDialog && capturedCardBitmap != null) {
        AlertDialog(
            onDismissRequest = { showCardResultDialog = false },
            title = { Text("ID Card Captured", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        Image(
                            bitmap = capturedCardBitmap!!.asImageBitmap(),
                            contentDescription = "Card",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("ID Card recorded in storage", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bmp = capturedCardBitmap
                        if (bmp != null) {
                            scope.launch {
                                val saved = withContext(Dispatchers.IO) {
                                    ImageUtils.saveBitmapToGalleryOrApp(context, bmp, "ID_Card_${System.currentTimeMillis()}")
                                }
                                if (saved != null) {
                                    repository.addHistory("card_scanner", "ID Card Scan", saved.name)
                                    Toast.makeText(context, "Saved to gallery", Toast.LENGTH_SHORT).show()
                                }
                                showCardResultDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScannerTeal)
                ) {
                    Text("Save to Gallery")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCardResultDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Photo Result Dialog
    if (showImageResultDialog && capturedImageBitmap != null) {
        AlertDialog(
            onDismissRequest = { showImageResultDialog = false },
            title = { Text("Photo Captured", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        Image(
                            bitmap = capturedImageBitmap!!.asImageBitmap(),
                            contentDescription = "Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bmp = capturedImageBitmap
                        if (bmp != null) {
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    ImageUtils.saveBitmapToGalleryOrApp(context, bmp, "Photo_${System.currentTimeMillis()}")
                                }
                                if (file != null) {
                                    repository.addHistory("image_scanner", "Scanned Photo", file.name)
                                    Toast.makeText(context, "Saved to gallery", Toast.LENGTH_SHORT).show()
                                }
                                showImageResultDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScannerTeal)
                ) {
                    Text("Save to Gallery")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImageResultDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // All Features Glassmorphic Bottom Sheet
    if (showAllFeaturesSheet) {
        AllFeaturesGlassBottomSheet(
            onDismiss = { showAllFeaturesSheet = false },
            onSelectCameraMode = { mode ->
                activeCameraMode = mode
                showAllFeaturesSheet = false
            },
            onSelectCategory = { cat ->
                showAllFeaturesSheet = false
                onNavigateToCategory?.invoke(cat.categoryId)
            }
        )
    }
}
