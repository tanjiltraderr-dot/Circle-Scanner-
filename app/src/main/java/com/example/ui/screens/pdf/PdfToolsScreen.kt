package com.example.ui.screens.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.ui.components.AppBackButton
import com.example.ui.components.AppTopBar
import com.example.ui.components.LoadingState
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.utils.PdfUtils
import com.example.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PdfToolsScreen(
    initialTab: Int = 0,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { UtilityHubApp.instance.repository }
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableStateOf(initialTab.coerceIn(0, 4)) }
    val tabs = listOf("Images to PDF", "PDF to Image", "Merge PDF", "Split PDF", "Compress PDF")

    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var outputPdfFile by remember { mutableStateOf<File?>(null) }

    // State for Images to PDF
    val selectedImages = remember { mutableStateListOf<Bitmap>() }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        try {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)?.let { selectedImages.add(it) }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                isProcessing = false
            }
        }
    }

    // State for PDF to Image
    var selectedPdfForExtract by remember { mutableStateOf<Uri?>(null) }
    val extractedBitmaps = remember { mutableStateListOf<Bitmap>() }
    val pdfPickerForExtract = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedPdfForExtract = uri
        if (uri != null) {
            scope.launch {
                isProcessing = true
                withContext(Dispatchers.IO) {
                    val bitmaps = PdfUtils.pdfToBitmaps(context, uri, maxPages = 20)
                    extractedBitmaps.clear()
                    extractedBitmaps.addAll(bitmaps)
                }
                isProcessing = false
            }
        }
    }

    // State for Merge PDF
    val selectedPdfsToMerge = remember { mutableStateListOf<Uri>() }
    val pdfsPickerForMerge = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        selectedPdfsToMerge.addAll(uris)
    }

    // State for Split PDF
    var selectedPdfToSplit by remember { mutableStateOf<Uri?>(null) }
    var totalPagesInSplitPdf by remember { mutableStateOf(0) }
    var splitStartPage by remember { mutableStateOf("1") }
    var splitEndPage by remember { mutableStateOf("1") }
    val pdfPickerForSplit = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedPdfToSplit = uri
        if (uri != null) {
            val count = PdfUtils.getPdfPageCount(context, uri)
            totalPagesInSplitPdf = count
            splitStartPage = "1"
            splitEndPage = count.coerceAtLeast(1).toString()
        }
    }

    // State for Compress PDF
    var selectedPdfToCompress by remember { mutableStateOf<Uri?>(null) }
    val pdfPickerForCompress = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> selectedPdfToCompress = uri }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "PDF Tools",
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
                modifier = Modifier.fillMaxWidth().testTag("pdf_tools_tabs")
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = {
                            selectedTabIndex = index
                            resultMessage = null
                            outputPdfFile = null
                        },
                        text = { Text(title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> { // Images to PDF
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            item {
                                Text(
                                    text = "Convert Multiple Photos to PDF",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Select images from gallery and export them as an A4 formatted PDF file.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            item {
                                SecondaryButton(
                                    text = "Select Images (${selectedImages.size} selected)",
                                    icon = Icons.Default.AddPhotoAlternate,
                                    onClick = { imagePicker.launch("image/*") },
                                    testTag = "select_images_for_pdf_btn"
                                )
                            }
                            if (selectedImages.isNotEmpty()) {
                                item {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(selectedImages) { bmp ->
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                    }
                                }
                                item {
                                    PrimaryButton(
                                        text = "Generate PDF (${selectedImages.size} Photos)",
                                        icon = Icons.Default.PictureAsPdf,
                                        onClick = {
                                            scope.launch {
                                                isProcessing = true
                                                val file = withContext(Dispatchers.IO) {
                                                    PdfUtils.imagesToPdf(context, selectedImages.toList())
                                                }
                                                isProcessing = false
                                                if (file != null) {
                                                    outputPdfFile = file
                                                    resultMessage = "PDF created: ${file.name} (${StorageUtils.formatFileSize(file.length())})"
                                                    repository.addHistory("img_to_pdf", "Image to PDF", file.name)
                                                }
                                            }
                                        },
                                        testTag = "create_pdf_btn"
                                    )
                                }
                            }
                            if (outputPdfFile != null) {
                                item {
                                    SuccessPdfCard(file = outputPdfFile!!)
                                }
                            }
                        }
                    }

                    1 -> { // PDF to Image
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            item {
                                Text(
                                    text = "Extract Pages as Images",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Convert pages of any PDF into high resolution image files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            item {
                                PrimaryButton(
                                    text = "Choose PDF Document",
                                    icon = Icons.Default.PictureAsPdf,
                                    onClick = { pdfPickerForExtract.launch("application/pdf") },
                                    testTag = "choose_pdf_to_img_btn"
                                )
                            }
                            if (extractedBitmaps.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Extracted ${extractedBitmaps.size} Page(s)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                items(extractedBitmaps) { bmp ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.fillMaxWidth().height(240.dp)
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> { // Merge PDF
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            item {
                                Text(
                                    text = "Merge Multiple PDF Files",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Combine two or more PDF documents into one single file.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            item {
                                SecondaryButton(
                                    text = "Add PDFs (${selectedPdfsToMerge.size} selected)",
                                    icon = Icons.Default.Add,
                                    onClick = { pdfsPickerForMerge.launch("application/pdf") },
                                    testTag = "select_pdfs_merge_btn"
                                )
                            }
                            if (selectedPdfsToMerge.size >= 2) {
                                item {
                                    PrimaryButton(
                                        text = "Merge ${selectedPdfsToMerge.size} PDF Files",
                                        icon = Icons.Default.CallMerge,
                                        onClick = {
                                            scope.launch {
                                                isProcessing = true
                                                val file = withContext(Dispatchers.IO) {
                                                    PdfUtils.mergePdfUris(
                                                        context,
                                                        selectedPdfsToMerge.toList(),
                                                        "merged_${System.currentTimeMillis()}.pdf"
                                                    )
                                                }
                                                isProcessing = false
                                                if (file != null) {
                                                    outputPdfFile = file
                                                    repository.addHistory("pdf_merge", "Merged PDFs", file.name)
                                                }
                                            }
                                        },
                                        testTag = "merge_pdfs_execute_btn"
                                    )
                                }
                            }
                            if (outputPdfFile != null) {
                                item {
                                    SuccessPdfCard(file = outputPdfFile!!)
                                }
                            }
                        }
                    }

                    3 -> { // Split PDF
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            item {
                                Text(
                                    text = "Extract / Split Pages",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Select a PDF and choose the start and end page to extract.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            item {
                                SecondaryButton(
                                    text = if (selectedPdfToSplit != null) "PDF Selected ($totalPagesInSplitPdf Pages)" else "Choose PDF File",
                                    icon = Icons.Default.PictureAsPdf,
                                    onClick = { pdfPickerForSplit.launch("application/pdf") },
                                    testTag = "choose_pdf_to_split_btn"
                                )
                            }
                            if (selectedPdfToSplit != null && totalPagesInSplitPdf > 0) {
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedTextField(
                                            value = splitStartPage,
                                            onValueChange = { splitStartPage = it },
                                            label = { Text("Start Page") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = splitEndPage,
                                            onValueChange = { splitEndPage = it },
                                            label = { Text("End Page") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                item {
                                    PrimaryButton(
                                        text = "Split & Save Pages",
                                        icon = Icons.Default.CallSplit,
                                        onClick = {
                                            scope.launch {
                                                isProcessing = true
                                                val start = splitStartPage.toIntOrNull() ?: 1
                                                val end = splitEndPage.toIntOrNull() ?: totalPagesInSplitPdf
                                                val file = withContext(Dispatchers.IO) {
                                                    PdfUtils.splitPdfPages(
                                                        context,
                                                        selectedPdfToSplit!!,
                                                        start,
                                                        end,
                                                        "split_${System.currentTimeMillis()}.pdf"
                                                    )
                                                }
                                                isProcessing = false
                                                if (file != null) {
                                                    outputPdfFile = file
                                                    repository.addHistory("pdf_split", "Split PDF", "${file.name} (pp. $start-$end)")
                                                }
                                            }
                                        },
                                        testTag = "split_pdf_execute_btn"
                                    )
                                }
                            }
                            if (outputPdfFile != null) {
                                item {
                                    SuccessPdfCard(file = outputPdfFile!!)
                                }
                            }
                        }
                    }

                    4 -> { // Compress PDF
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            item {
                                Text(
                                    text = "Compress PDF Document",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Optimize PDF size to make sharing via email or chat easier.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            item {
                                SecondaryButton(
                                    text = if (selectedPdfToCompress != null) "PDF Selected" else "Choose PDF Document",
                                    icon = Icons.Default.PictureAsPdf,
                                    onClick = { pdfPickerForCompress.launch("application/pdf") },
                                    testTag = "choose_pdf_compress_btn"
                                )
                            }
                            if (selectedPdfToCompress != null) {
                                item {
                                    PrimaryButton(
                                        text = "Compress PDF",
                                        icon = Icons.Default.Compress,
                                        onClick = {
                                            val pdfUri = selectedPdfToCompress
                                            if (pdfUri != null) {
                                                scope.launch {
                                                isProcessing = true
                                                val count = withContext(Dispatchers.IO) {
                                                    PdfUtils.getPdfPageCount(context, pdfUri)
                                                }
                                                val file = withContext(Dispatchers.IO) {
                                                    PdfUtils.splitPdfPages(
                                                        context,
                                                        pdfUri,
                                                        1,
                                                        count,
                                                        "compressed_${System.currentTimeMillis()}.pdf"
                                                    )
                                                }
                                                isProcessing = false
                                                if (file != null) {
                                                    outputPdfFile = file
                                                    repository.addHistory("pdf_compress", "Compressed PDF", file.name)
                                                }
                                            }
                                        }
                                        },
                                        testTag = "compress_pdf_execute_btn"
                                    )
                                }
                            }
                            if (outputPdfFile != null) {
                                item {
                                    SuccessPdfCard(file = outputPdfFile!!)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isProcessing) {
            LoadingState(message = "Processing PDF...")
        }
    }
}

@Composable
private fun SuccessPdfCard(file: File) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().testTag("pdf_success_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "File Generated Successfully!",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${file.name} · ${StorageUtils.formatFileSize(file.length())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { StorageUtils.shareFile(context, file, "application/pdf") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).testTag("share_created_pdf_btn")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share PDF")
                }
            }
        }
    }
}
