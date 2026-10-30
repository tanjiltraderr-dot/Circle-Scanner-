package com.example.scanner.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Official Google ML Kit Document Scanner Integration.
 * Provides on-device machine learning models for automatic document edge detection,
 * bedsheet & complex background suppression, 4-point perspective dewarping,
 * and intelligent CamScanner-style enhancement.
 */
object GoogleDocumentScannerHelper {

    /**
     * Checks whether Google Play Services is installed and updated on the user's device.
     */
    fun isGooglePlayServicesAvailable(context: Context): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val result = availability.isGooglePlayServicesAvailable(context)
        return result == ConnectionResult.SUCCESS
    }

    /**
     * Creates a configured GmsDocumentScanner client with:
     * - FULL scanner mode (ML auto-crop, auto-straighten, auto-filter)
     * - JPEG & PDF output formats
     * - Gallery import support
     * - Multi-page batch support (up to 25 pages)
     */
    fun createScannerClient(): GmsDocumentScanner {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(25)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        return GmsDocumentScanning.getClient(options)
    }

    /**
     * Requests Google ML Kit to launch the document scanning workflow.
     */
    fun launch(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onFailure: (Exception) -> Unit = {}
    ) {
        try {
            if (!isGooglePlayServicesAvailable(activity)) {
                activity.runOnUiThread {
                    onFailure(IllegalStateException("Google Play Services not available"))
                }
                return
            }
            val scanner = createScannerClient()
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener(activity) { intentSender: IntentSender ->
                    try {
                        val request = IntentSenderRequest.Builder(intentSender).build()
                        launcher.launch(request)
                    } catch (e: Exception) {
                        activity.runOnUiThread { onFailure(e) }
                    }
                }
                .addOnFailureListener(activity) { exception ->
                    activity.runOnUiThread { onFailure(exception) }
                }
        } catch (e: Exception) {
            activity.runOnUiThread { onFailure(e) }
        }
    }

    /**
     * Parses the result intent returned from Google ML Kit Document Scanner.
     */
    fun parseResult(data: Intent?): GmsDocumentScanningResult? {
        return GmsDocumentScanningResult.fromActivityResultIntent(data)
    }

    data class SavedScanResult(
        val documentTitle: String,
        val pageFiles: List<File>,
        val pdfFile: File?,
        val primaryImageFile: File,
        val scanId: Long = -1L
    )

    /**
     * Copies returned temporary scanner URIs into app-private persistent storage
     * so they are preserved across app restarts and visible in history.
     */
    suspend fun persistScanResult(
        context: Context,
        result: GmsDocumentScanningResult,
        customTitle: String? = null
    ): SavedScanResult? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.getDefault()).format(Date())
            val title = customTitle ?: "CamScan $timestamp"
            val outDir = File(context.filesDir, "scanned_images").apply { mkdirs() }
            val pdfDir = File(context.filesDir, "scanned_documents").apply { mkdirs() }

            val savedPages = mutableListOf<File>()
            val pages = result.pages ?: emptyList()

            for ((index, page) in pages.withIndex()) {
                val pageUri = page.imageUri
                val targetFile = File(outDir, "scan_${System.currentTimeMillis()}_p${index + 1}.jpg")
                context.contentResolver.openInputStream(pageUri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    savedPages.add(targetFile)
                }
            }

            var savedPdf: File? = null
            result.pdf?.uri?.let { pdfUri ->
                val targetPdf = File(pdfDir, "${title.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")}.pdf")
                context.contentResolver.openInputStream(pdfUri)?.use { input ->
                    FileOutputStream(targetPdf).use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetPdf.exists() && targetPdf.length() > 0) {
                    savedPdf = targetPdf
                }
            }

            val primary = savedPages.firstOrNull() ?: return@withContext null
            var docId = -1L
            try {
                val bmp = android.graphics.BitmapFactory.decodeFile(primary.absolutePath)
                if (bmp != null) {
                    val savedRoom = ScanStorageManager.saveScan(
                        context = context,
                        title = title,
                        processedBitmap = bmp,
                        rawBitmap = bmp,
                        filterUsed = "Google Drive Scanner",
                        pageCount = savedPages.size,
                        pdfFile = savedPdf
                    )
                    docId = savedRoom.scanId
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            SavedScanResult(
                documentTitle = title,
                pageFiles = savedPages,
                pdfFile = savedPdf,
                primaryImageFile = primary,
                scanId = docId
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
