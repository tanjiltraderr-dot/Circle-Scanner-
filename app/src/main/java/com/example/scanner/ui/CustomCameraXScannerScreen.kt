package com.example.scanner.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.scanner.engine.OpenCvDocumentProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

@Composable
fun CustomCameraXScannerScreen(
    onScanFinished: (flatBinarizedBitmap: Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Position notebook inside frame") }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Custom CameraX Preview Viewfinder
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        Log.e("CameraX", "Binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // 2. Custom Viewfinder Framing Guides
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f) // Standard A4 / Notebook aspect ratio
                    .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            )
        }

        // 3. Status Bar
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.6f)
        ) {
            Text(
                text = statusText,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // 4. Custom Shutter Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(bottom = 40.dp, top = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = {
                    val capture = imageCapture ?: return@IconButton
                    if (isProcessing) return@IconButton

                    isProcessing = true
                    statusText = "Capturing..."

                    val tempFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

                    capture.takePicture(
                        outputOptions,
                        Executors.newSingleThreadExecutor(),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                coroutineScope.launch {
                                    statusText = "Detecting paper corners & dewarping..."

                                    val processedResult = withContext(Dispatchers.Default) {
                                        // A. Load and fix EXIF camera orientation
                                        val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                                        tempFile.delete()

                                        if (bitmap != null) {
                                            // Execute AutoCropEngine pipeline
                                            val autoResult = com.example.scanner.engine.AutoCropEngine.cropDocument(
                                                context = context,
                                                rawBitmap = bitmap,
                                                preferredMode = com.example.scanner.engine.EnhancementMode.BLACK_WHITE
                                            )

                                            val finalBitmap = if (autoResult.isSuccess) {
                                                autoResult.enhancedBitmap ?: autoResult.croppedBitmap ?: bitmap
                                            } else {
                                                // Fallback to verified perspective transform
                                                val guideQuad = autoResult.quad ?: com.example.scanner.engine.AutoCropEngine.getInitialGuideQuad(bitmap.width, bitmap.height)
                                                val dewarped = com.example.scanner.engine.PerspectiveCorrectionEngine.transformPerspective(bitmap, guideQuad)
                                                OpenCvDocumentProcessor.applyAdaptiveBinarization(dewarped)
                                            }

                                            finalBitmap
                                        } else {
                                            Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                                        }
                                    }

                                    isProcessing = false
                                    onScanFinished(processedResult)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isProcessing = false
                                statusText = "Capture failed: ${exception.message}"
                            }
                        }
                    )
                },
                enabled = !isProcessing,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Capture Document",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // 5. Full-Screen Processing Spinner
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = statusText, color = Color.White)
                }
            }
        }
    }
}
