package com.example.ui.screens.scanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.UtilityHubApp
import com.example.ui.components.AppBackButton
import com.example.ui.components.AppTopBar
import com.example.ui.components.PermissionNoticeCard
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.utils.QrCodeHelper
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun BarcodeScannerScreen(
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
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var scannedBarcode by remember { mutableStateOf<String?>(null) }
    var barcodeFormatName by remember { mutableStateOf("Barcode") }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val decoded = QrCodeHelper.decodeBitmap(bitmap)
                        if (decoded != null) {
                            scannedBarcode = decoded
                            barcodeFormatName = "Scanned Code"
                            scope.launch {
                                repository.addHistory("barcode_scanner", "Barcode from Photo", decoded)
                            }
                        } else {
                            Toast.makeText(context, "No barcode found in image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Barcode Scanner",
                navigationIcon = { AppBackButton(onBack = onBack) },
                actions = {
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = "Scan Image")
                    }
                    if (hasCameraPermission && scannedBarcode == null) {
                        IconButton(onClick = {
                            isTorchEnabled = !isTorchEnabled
                            cameraControl?.enableTorch(isTorchEnabled)
                        }) {
                            Icon(
                                imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flashlight",
                                tint = if (isTorchEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
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
            if (!hasCameraPermission) {
                PermissionNoticeCard(
                    title = "Camera Permission Required",
                    explanation = "Circle Scanner needs camera access to scan barcodes.",
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                if (scannedBarcode == null) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                val multiReader = MultiFormatReader()
                                val hints = mapOf(
                                    DecodeHintType.POSSIBLE_FORMATS to listOf(
                                        BarcodeFormat.EAN_13,
                                        BarcodeFormat.EAN_8,
                                        BarcodeFormat.UPC_A,
                                        BarcodeFormat.UPC_E,
                                        BarcodeFormat.CODE_128,
                                        BarcodeFormat.CODE_39,
                                        BarcodeFormat.QR_CODE
                                    )
                                )
                                multiReader.setHints(hints)

                                val cameraExecutor = Executors.newSingleThreadExecutor()
                                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    if (scannedBarcode == null) {
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
                                                scannedBarcode = text
                                                barcodeFormatName = result.barcodeFormat.name
                                                scope.launch {
                                                    repository.addHistory("barcode_scanner", "Scanned ${result.barcodeFormat.name}", text)
                                                }
                                            }
                                        } catch (_: NotFoundException) {
                                        } catch (e: Exception) {
                                        } finally {
                                            multiReader.reset()
                                        }
                                    }
                                    imageProxy.close()
                                }

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                try {
                                    cameraProvider.unbindAll()
                                    val camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )
                                    cameraControl = camera.cameraControl
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize().testTag("barcode_camera_preview")
                    )

                    // Wide Reticle Box for Barcodes
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(160.dp)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                        ) {}
                    }

                    Text(
                        text = "Align barcode within the frame",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 40.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    BarcodeResultCard(
                        barcode = scannedBarcode!!,
                        formatName = barcodeFormatName,
                        onScanAgain = { scannedBarcode = null },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BarcodeResultCard(
    barcode: String,
    formatName: String,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth().testTag("barcode_result_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CropFree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = formatName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = barcode,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            PrimaryButton(
                text = "Search Product Online",
                icon = Icons.Default.Search,
                onClick = {
                    val searchUrl = "https://www.google.com/search?q=$barcode"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)))
                },
                testTag = "search_product_btn"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryButton(
                    text = "Copy Code",
                    icon = Icons.Default.ContentCopy,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Barcode", barcode))
                        Toast.makeText(context, "Barcode copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "copy_barcode_btn"
                )

                SecondaryButton(
                    text = "Share",
                    icon = Icons.Default.Share,
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, barcode)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Barcode"))
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "share_barcode_btn"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            TextButton(onClick = onScanAgain) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan Another Barcode")
            }
        }
    }
}
