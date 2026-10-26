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
fun QrScannerScreen(
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
        if (!isGranted) {
            Toast.makeText(context, "Camera permission is required for QR scanning", Toast.LENGTH_SHORT).show()
        }
    }

    var scannedResult by remember { mutableStateOf<String?>(null) }
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
                            scannedResult = decoded
                            scope.launch {
                                repository.addHistory("qr_scanner", "QR Scanned from Photo", decoded)
                            }
                        } else {
                            Toast.makeText(context, "No QR code found in selected image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error decoding image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "QR Scanner",
                navigationIcon = { AppBackButton(onBack = onBack) },
                actions = {
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.testTag("scan_gallery_btn")
                    ) {
                        Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = "Scan from Photo")
                    }
                    if (hasCameraPermission && scannedResult == null) {
                        IconButton(
                            onClick = {
                                isTorchEnabled = !isTorchEnabled
                                cameraControl?.enableTorch(isTorchEnabled)
                            },
                            modifier = Modifier.testTag("toggle_torch_btn")
                        ) {
                            Icon(
                                imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Torch",
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
                    explanation = "Circle Scanner needs camera access to scan QR codes in real-time.",
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                if (scannedResult == null) {
                    // Real Camera Preview
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
                                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
                                )
                                multiReader.setHints(hints)

                                val cameraExecutor = Executors.newSingleThreadExecutor()
                                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    if (scannedResult == null) {
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
                                                scannedResult = text
                                                scope.launch {
                                                    repository.addHistory("qr_scanner", "Scanned QR Code", text)
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
                        modifier = Modifier.fillMaxSize().testTag("qr_camera_preview")
                    )

                    // Target Reticle Box Overlay
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Transparent)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                        ) {}
                    }

                    Text(
                        text = "Point camera at a QR code",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 40.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    // Result Display Card
                    QrResultCard(
                        result = scannedResult!!,
                        onScanAgain = { scannedResult = null },
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
fun QrResultCard(
    result: String,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isUrl = result.startsWith("http://", ignoreCase = true) || result.startsWith("https://", ignoreCase = true)
    val isPhone = result.startsWith("tel:", ignoreCase = true) || result.matches(Regex("^[+]?[0-9\\-\\s()]{7,}$"))
    val isEmail = result.startsWith("mailto:", ignoreCase = true) || result.contains("@") && result.contains(".")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("qr_result_card"),
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
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Scan Successful",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Contextual Actions
            if (isUrl) {
                PrimaryButton(
                    text = "Open Website",
                    icon = Icons.Default.OpenInBrowser,
                    onClick = {
                        val url = if (!result.startsWith("http")) "https://$result" else result
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    testTag = "open_website_btn"
                )
                Spacer(modifier = Modifier.height(10.dp))
            } else if (isPhone) {
                PrimaryButton(
                    text = "Dial Number",
                    icon = Icons.Default.Phone,
                    onClick = {
                        val num = result.removePrefix("tel:")
                        try {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open dialer", Toast.LENGTH_SHORT).show()
                        }
                    },
                    testTag = "dial_number_btn"
                )
                Spacer(modifier = Modifier.height(10.dp))
            } else if (isEmail) {
                PrimaryButton(
                    text = "Send Email",
                    icon = Icons.Default.Email,
                    onClick = {
                        val email = result.removePrefix("mailto:")
                        try {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open email client", Toast.LENGTH_SHORT).show()
                        }
                    },
                    testTag = "send_email_btn"
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryButton(
                    text = "Copy",
                    icon = Icons.Default.ContentCopy,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Scanned QR", result))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "copy_result_btn"
                )

                SecondaryButton(
                    text = "Share",
                    icon = Icons.Default.Share,
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, result)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Scanned QR"))
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "share_result_btn"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            TextButton(
                onClick = onScanAgain,
                modifier = Modifier.testTag("scan_again_btn")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan Again")
            }
        }
    }
}
