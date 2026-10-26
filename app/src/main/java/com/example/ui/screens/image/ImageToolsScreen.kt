package com.example.ui.screens.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.UtilityHubApp
import com.example.ui.components.*
import com.example.utils.ImageUtils
import com.example.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ImageToolsScreen(
    initialTab: Int = 0,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { UtilityHubApp.instance.repository }
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableStateOf(initialTab.coerceIn(0, 3)) }
    val tabs = listOf("Compress", "Resize", "Crop", "Convert")

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Compress Settings
    var compressQuality by remember { mutableStateOf(75f) }

    // Resize Settings
    var targetWidthStr by remember { mutableStateOf("") }
    var targetHeightStr by remember { mutableStateOf("") }
    var maintainAspect by remember { mutableStateOf(true) }

    // Convert Settings
    var selectedFormatIndex by remember { mutableStateOf(0) }
    val formats = listOf("JPEG", "PNG", "WEBP")

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
        processedBitmap = null
        outputFile = null
        if (uri != null) {
            scope.launch {
                isProcessing = true
                val bmp = withContext(Dispatchers.IO) {
                    ImageUtils.decodeSampledBitmapFromUri(context, uri, 1600, 1600)
                }
                loadedBitmap = bmp
                if (bmp != null) {
                    targetWidthStr = bmp.width.toString()
                    targetHeightStr = bmp.height.toString()
                }
                isProcessing = false
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Image Tools",
                navigationIcon = { AppBackButton(onBack = onBack) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth().testTag("image_tools_tabs")
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = {
                            selectedTabIndex = index
                            outputFile = null
                            processedBitmap = null
                        },
                        text = { Text(title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Image Selector Button
                item {
                    SecondaryButton(
                        text = if (loadedBitmap != null) "Change Photo (${loadedBitmap!!.width} x ${loadedBitmap!!.height} px)" else "Select Photo from Gallery",
                        icon = Icons.Default.PhotoLibrary,
                        onClick = { imagePicker.launch("image/*") },
                        testTag = "select_photo_btn"
                    )
                }

                // Image Preview Card
                if (loadedBitmap != null) {
                    item {
                        val displayBmp = processedBitmap ?: loadedBitmap!!
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Image(
                                    bitmap = displayBmp.asImageBitmap(),
                                    contentDescription = "Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(8.dp)
                                )
                            }
                        }
                    }

                    // Tab Specific Controls
                    when (selectedTabIndex) {
                        0 -> { // Compress
                            item {
                                Text(
                                    text = "Target Quality: ${compressQuality.toInt()}%",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Slider(
                                    value = compressQuality,
                                    onValueChange = { compressQuality = it },
                                    valueRange = 10f..100f,
                                    modifier = Modifier.testTag("compress_slider")
                                )
                                PrimaryButton(
                                    text = "Compress & Save",
                                    icon = Icons.Default.Compress,
                                    onClick = {
                                        scope.launch {
                                            isProcessing = true
                                            val file = withContext(Dispatchers.IO) {
                                                ImageUtils.compressBitmapToFile(
                                                    context,
                                                    loadedBitmap!!,
                                                    compressQuality.toInt(),
                                                    Bitmap.CompressFormat.JPEG,
                                                    "compressed_${System.currentTimeMillis()}.jpg"
                                                )
                                            }
                                            isProcessing = false
                                            if (file != null) {
                                                outputFile = file
                                                repository.addHistory("img_compress", "Compressed Photo", "${file.name} · ${StorageUtils.formatFileSize(file.length())}")
                                            }
                                        }
                                    },
                                    testTag = "execute_compress_btn"
                                )
                            }
                        }

                        1 -> { // Resize
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = targetWidthStr,
                                        onValueChange = {
                                            targetWidthStr = it
                                            if (maintainAspect && loadedBitmap != null) {
                                                val w = it.toIntOrNull() ?: 1
                                                val ratio = loadedBitmap!!.height.toFloat() / loadedBitmap!!.width
                                                targetHeightStr = (w * ratio).toInt().toString()
                                            }
                                        },
                                        label = { Text("Width (px)") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = targetHeightStr,
                                        onValueChange = {
                                            targetHeightStr = it
                                            if (maintainAspect && loadedBitmap != null) {
                                                val h = it.toIntOrNull() ?: 1
                                                val ratio = loadedBitmap!!.width.toFloat() / loadedBitmap!!.height
                                                targetWidthStr = (h * ratio).toInt().toString()
                                            }
                                        },
                                        label = { Text("Height (px)") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = maintainAspect, onCheckedChange = { maintainAspect = it })
                                    Text("Maintain aspect ratio")
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                PrimaryButton(
                                    text = "Resize Photo",
                                    icon = Icons.Default.AspectRatio,
                                    onClick = {
                                        val w = targetWidthStr.toIntOrNull()?.coerceIn(10, 8000) ?: loadedBitmap!!.width
                                        val h = targetHeightStr.toIntOrNull()?.coerceIn(10, 8000) ?: loadedBitmap!!.height
                                        val resized = ImageUtils.resizeBitmap(loadedBitmap!!, w, h)
                                        processedBitmap = resized
                                        val file = ImageUtils.saveBitmapToGalleryOrApp(context, resized, "resized_${System.currentTimeMillis()}")
                                        outputFile = file
                                        if (file != null) {
                                            scope.launch {
                                                repository.addHistory("img_resize", "Resized Photo", "${w}x${h} px · ${file.name}")
                                            }
                                        }
                                    },
                                    testTag = "execute_resize_btn"
                                )
                            }
                        }

                        2 -> { // Crop & Rotate
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    IconButton(onClick = {
                                        val rotated = ImageUtils.rotateBitmap(loadedBitmap!!, 90f)
                                        loadedBitmap = rotated
                                        processedBitmap = rotated
                                    }) {
                                        Icon(imageVector = Icons.Default.RotateRight, contentDescription = "Rotate 90°")
                                    }
                                    IconButton(onClick = {
                                        val flipped = ImageUtils.flipBitmap(loadedBitmap!!, horizontal = true)
                                        loadedBitmap = flipped
                                        processedBitmap = flipped
                                    }) {
                                        Icon(imageVector = Icons.Default.Flip, contentDescription = "Flip Horizontal")
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Preset Crop Ratios:", style = MaterialTheme.typography.titleSmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("1:1" to 1f, "4:3" to 4f / 3f, "16:9" to 16f / 9f).forEach { (label, ratio) ->
                                        FilledTonalButton(
                                            onClick = {
                                                val bmp = loadedBitmap!!
                                                val targetH = (bmp.width / ratio).toInt().coerceAtMost(bmp.height)
                                                val targetW = (targetH * ratio).toInt().coerceAtMost(bmp.width)
                                                val left = (bmp.width - targetW) / 2
                                                val top = (bmp.height - targetH) / 2
                                                val cropped = ImageUtils.cropBitmap(bmp, Rect(left, top, left + targetW, top + targetH))
                                                processedBitmap = cropped
                                                val file = ImageUtils.saveBitmapToGalleryOrApp(context, cropped, "cropped_${System.currentTimeMillis()}")
                                                outputFile = file
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(label)
                                        }
                                    }
                                }
                            }
                        }

                        3 -> { // Convert
                            item {
                                Text("Convert to Format:", style = MaterialTheme.typography.titleSmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    formats.forEachIndexed { idx, fmt ->
                                        FilterChip(
                                            selected = selectedFormatIndex == idx,
                                            onClick = { selectedFormatIndex = idx },
                                            label = { Text(fmt) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                PrimaryButton(
                                    text = "Convert & Export as ${formats[selectedFormatIndex]}",
                                    icon = Icons.Default.Transform,
                                    onClick = {
                                        val compressFormat = when (selectedFormatIndex) {
                                            1 -> Bitmap.CompressFormat.PNG
                                            2 -> Bitmap.CompressFormat.WEBP
                                            else -> Bitmap.CompressFormat.JPEG
                                        }
                                        val file = ImageUtils.saveBitmapToGalleryOrApp(
                                            context,
                                            processedBitmap ?: loadedBitmap!!,
                                            "converted_${System.currentTimeMillis()}",
                                            compressFormat
                                        )
                                        outputFile = file
                                        if (file != null) {
                                            scope.launch {
                                                repository.addHistory("img_convert", "Converted Image", "${file.name} (${formats[selectedFormatIndex]})")
                                            }
                                        }
                                    },
                                    testTag = "execute_convert_btn"
                                )
                            }
                        }
                    }

                    // Result File Actions
                    if (outputFile != null) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth().testTag("image_success_card")
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Processed Successfully!",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${outputFile!!.name} · ${StorageUtils.formatFileSize(outputFile!!.length())}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SecondaryButton(
                                        text = "Share Photo",
                                        icon = Icons.Default.Share,
                                        onClick = {
                                            StorageUtils.shareFile(context, outputFile!!, "image/*")
                                        },
                                        testTag = "share_processed_photo_btn"
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        EmptyState(
                            title = "No photo selected",
                            description = "Pick any image from your device gallery to begin editing, compressing, or converting.",
                            icon = Icons.Default.AddPhotoAlternate
                        )
                    }
                }
            }
        }

        if (isProcessing) {
            LoadingState("Processing image...")
        }
    }
}
