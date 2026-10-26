package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.entity.HistoryEntity
import com.example.scanner.engine.ScanStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

object ScanThumbnailHelper {

    /**
     * Resolves and loads a memory-controlled thumbnail bitmap for any scan history item.
     * - For QR/Barcode scans: generates real QR code bitmap preview
     * - For Documents/Photos: resolves exact scan record by unique scan ID or exact file,
     *   loads pre-cached compact thumbnail, or generates a downsampled thumbnail without
     *   allocating large full-size bitmaps in memory.
     * - Never falls back to showing another scan's photo or the latest file!
     */
    suspend fun loadThumbnail(context: Context, item: HistoryEntity): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val id = item.toolId.lowercase()
            val details = item.details.trim()
            val title = item.title.lowercase()

            // 1. QR or Barcode: generate real QR preview bitmap
            if (id.contains("qr") || id.contains("barcode") || title.contains("qr") || title.contains("barcode")) {
                val qrContent = if (details.contains("|")) details.substringAfter("|").trim() else details
                if (qrContent.isNotBlank() && !qrContent.endsWith(".jpg") && !qrContent.endsWith(".png") && !qrContent.endsWith(".pdf")) {
                    return@withContext QrCodeHelper.generateQrBitmap(qrContent, 120, 120)
                }
            }

            val db = AppDatabase.getInstance(context)

            // 2. Resolve document via exact Scan ID from metadata
            val scanId = ScanStorageManager.extractScanId(details)
            if (scanId != null && scanId > 0) {
                val doc = db.scannedDocumentDao().getDocumentById(scanId)
                if (doc != null) {
                    val thumbFile = doc.thumbnailPath?.let { File(it) }
                    if (thumbFile != null && thumbFile.exists() && thumbFile.length() > 0L) {
                        val bmp = decodeSampledBitmap(thumbFile, 160, 160)
                        if (bmp != null) return@withContext bmp
                    }

                    // Thumbnail missing on disk -> generate memory-safely from processed image
                    val generated = ScanStorageManager.ensureThumbnail(context, doc)
                    if (generated != null && generated.exists()) {
                        val bmp = decodeSampledBitmap(generated, 160, 160)
                        if (bmp != null) return@withContext bmp
                    }
                }
            }

            // 3. Fallback: match by title or original file name in ScannedDocumentDao
            val cleanName = details.substringAfter("|").substringBefore(" (").trim()
            val allDocs = db.scannedDocumentDao().getAllDocuments().firstOrNull() ?: emptyList()
            val matchedDoc = allDocs.firstOrNull {
                it.originalFileName.equals(cleanName, ignoreCase = true) ||
                it.title.equals(item.title, ignoreCase = true) ||
                (it.processedImagePath != null && File(it.processedImagePath).name.equals(cleanName, ignoreCase = true))
            }
            if (matchedDoc != null) {
                val thumbFile = matchedDoc.thumbnailPath?.let { File(it) }
                if (thumbFile != null && thumbFile.exists() && thumbFile.length() > 0L) {
                    val bmp = decodeSampledBitmap(thumbFile, 160, 160)
                    if (bmp != null) return@withContext bmp
                }
                val generated = ScanStorageManager.ensureThumbnail(context, matchedDoc)
                if (generated != null && generated.exists()) {
                    val bmp = decodeSampledBitmap(generated, 160, 160)
                    if (bmp != null) return@withContext bmp
                }
            }

            // 4. Exact file resolution (only if exact file exists, no guesswork)
            val resolvedFile = resolveFile(context, item)
            if (resolvedFile != null && resolvedFile.exists()) {
                if (resolvedFile.name.endsWith(".pdf", ignoreCase = true)) {
                    val uri = Uri.fromFile(resolvedFile)
                    val bmps = PdfUtils.pdfToBitmaps(context, uri, 1)
                    if (bmps.isNotEmpty()) return@withContext bmps.first()
                } else {
                    val bmp = decodeSampledBitmap(resolvedFile, 160, 160)
                    if (bmp != null) return@withContext bmp
                }
            }
        } catch (_: Exception) {}
        null
    }

    /**
     * Resolves the actual File on disk strictly corresponding to this specific scan history item.
     * Uses unique scan ID or exact matching filename; never returns the latest unrelated file.
     */
    fun resolveFile(context: Context, item: HistoryEntity): File? {
        val details = item.details.trim()

        // 1. Try resolving through Scan ID
        val scanId = ScanStorageManager.extractScanId(details)
        if (scanId != null && scanId > 0) {
            try {
                val db = AppDatabase.getInstance(context)
                val doc = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    db.scannedDocumentDao().getDocumentById(scanId)
                }
                if (doc != null) {
                    doc.processedImagePath?.let {
                        val f = File(it)
                        if (f.exists() && f.isFile) return f
                    }
                    doc.pdfFilePath?.let {
                        val f = File(it)
                        if (f.exists() && f.isFile) return f
                    }
                    doc.rawImagePath?.let {
                        val f = File(it)
                        if (f.exists() && f.isFile) return f
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Exact file name lookup across known document directories
        val candidateName = details.substringAfter("|").substringBefore(" (").trim()
        if (candidateName.isBlank()) return null

        val direct = File(candidateName)
        if (direct.exists() && direct.isFile) return direct

        val candidateDirs = listOf(
            File(context.filesDir, "scanned_docs"),
            File(context.filesDir, "raw_docs"),
            File(context.filesDir, "scanned_images"),
            File(context.filesDir, "pdfs"),
            File(context.filesDir, "images"),
            File(context.filesDir, "exported_media"),
            context.filesDir
        )

        val namesToTry = listOf(
            candidateName,
            "$candidateName.jpg",
            "$candidateName.png",
            "$candidateName.pdf"
        )

        for (dir in candidateDirs) {
            if (!dir.exists()) continue
            for (name in namesToTry) {
                val f = File(dir, name)
                if (f.exists() && f.isFile) return f
            }
        }

        return null
    }

    /**
     * Memory-safe downsampled Bitmap decoder using inSampleSize calculation.
     * Prevents loading full 12+ megapixel bitmaps into memory just for small thumbnails.
     */
    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (!file.exists() || file.length() <= 0L) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)

            val height = options.outHeight
            val width = options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = max(1, inSampleSize)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (_: Throwable) {
            null
        }
    }
}
