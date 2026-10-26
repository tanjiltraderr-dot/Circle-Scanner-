package com.example.scanner.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.UtilityHubApp
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ScannedDocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.min

data class SavedScanResult(
    val scanId: Long,
    val title: String,
    val processedFile: File,
    val rawFile: File?,
    val thumbnailFile: File,
    val pdfFile: File?,
    val timestamp: Long,
    val pageCount: Int = 1,
    val filterUsed: String = "CamScanner Magic",
    val fileSizeBytes: Long = 0L
)

// Backward-compatible meta alias
typealias SavedScanMeta = SavedScanResult

object ScanStorageManager {

    private const val THUMBNAIL_MAX_DIMENSION = 320f

    /**
     * Persists a document scan to permanent internal storage and creates a Room database record.
     * Guarantees:
     * - Processed image is permanently written to internal storage (context.filesDir/scanned_docs)
     * - Original raw capture is preserved (context.filesDir/raw_docs) without overwriting
     * - Compact thumbnail (< 320px) is generated for high-performance memory-safe history list rendering
     * - Record is stored in Room database with unique scan ID, paths, timestamp, title, page count, filter
     * - Activity is logged to HistoryEntity
     * - If image saving fails, no partial or corrupt database record is created.
     */
    suspend fun saveScan(
        context: Context,
        title: String,
        processedBitmap: Bitmap,
        rawBitmap: Bitmap? = null,
        filterUsed: String = "CamScanner Magic",
        pageCount: Int = 1,
        pdfFile: File? = null,
        ocrText: String? = null
    ): SavedScanResult = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val randomSuffix = UUID.randomUUID().toString().take(6)
        val safeTitle = title.trim().ifBlank {
            "Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))}"
        }

        // Permanent directories in internal storage (never cleared by OS cache eviction)
        val docsDir = File(context.filesDir, "scanned_docs").apply { mkdirs() }
        val rawDir = File(context.filesDir, "raw_docs").apply { mkdirs() }
        val thumbDir = File(context.filesDir, "scan_thumbnails").apply { mkdirs() }

        val processedFile = File(docsDir, "doc_${timestamp}_${randomSuffix}.jpg")
        val thumbFile = File(thumbDir, "thumb_${timestamp}_${randomSuffix}.jpg")
        var rawFile: File? = null

        // 1. Save Enhanced Final Image to permanent storage
        try {
            FileOutputStream(processedFile).use { out ->
                val ok = processedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                if (!ok) throw IOException("Failed to compress processed document bitmap")
            }
        } catch (e: Exception) {
            if (processedFile.exists()) processedFile.delete()
            throw IOException("Error saving processed document: ${e.message}", e)
        }

        if (!processedFile.exists() || processedFile.length() <= 0L) {
            throw IOException("Processed file verification failed: file empty or missing")
        }

        // 2. Save Original Raw Image to permanent storage (if available)
        if (rawBitmap != null) {
            try {
                val f = File(rawDir, "raw_${timestamp}_${randomSuffix}.jpg")
                FileOutputStream(f).use { out ->
                    rawBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                if (f.exists() && f.length() > 0L) {
                    rawFile = f
                }
            } catch (_: Exception) {
                // Raw original save error should not block saving if processed succeeded
            }
        }

        // 3. Generate compact memory-controlled thumbnail
        try {
            val scale = min(
                THUMBNAIL_MAX_DIMENSION / processedBitmap.width.toFloat(),
                THUMBNAIL_MAX_DIMENSION / processedBitmap.height.toFloat()
            ).coerceAtMost(1f)
            val thumbW = (processedBitmap.width * scale).toInt().coerceAtLeast(64)
            val thumbH = (processedBitmap.height * scale).toInt().coerceAtLeast(64)
            val thumbBmp = Bitmap.createScaledBitmap(processedBitmap, thumbW, thumbH, true)

            FileOutputStream(thumbFile).use { out ->
                thumbBmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            if (thumbBmp != processedBitmap) {
                thumbBmp.recycle()
            }
        } catch (_: Exception) {
            // Fallback: write standard small file
        }

        val totalSizeBytes = processedFile.length() + (rawFile?.length() ?: 0L) + (pdfFile?.length() ?: 0L)

        // 4. Insert persistent record into Room Database schema
        val entity = ScannedDocumentEntity(
            title = safeTitle,
            originalFileName = processedFile.name,
            processedImagePath = processedFile.absolutePath,
            rawImagePath = rawFile?.absolutePath,
            pdfFilePath = pdfFile?.absolutePath,
            thumbnailPath = thumbFile.absolutePath,
            pageCount = pageCount,
            fileSizeBytes = totalSizeBytes,
            ocrExtractedText = ocrText,
            filterUsed = filterUsed,
            createdAtTimestamp = timestamp
        )

        val db = AppDatabase.getInstance(context)
        val newScanId = db.scannedDocumentDao().insertDocument(entity)

        // 5. Add activity history record for unified dashboard timeline
        try {
            UtilityHubApp.instance.repository.addHistory(
                toolId = "doc_scanner",
                title = safeTitle,
                details = "scan_id:$newScanId|${processedFile.name}"
            )
        } catch (_: Exception) {}

        SavedScanResult(
            scanId = newScanId,
            title = safeTitle,
            processedFile = processedFile,
            rawFile = rawFile,
            thumbnailFile = thumbFile,
            pdfFile = pdfFile,
            timestamp = timestamp,
            pageCount = pageCount,
            filterUsed = filterUsed,
            fileSizeBytes = totalSizeBytes
        )
    }

    /**
     * Overload for ProcessedScanResult from AutoCropEngine.
     */
    suspend fun saveScan(
        context: Context,
        title: String,
        result: ProcessedScanResult,
        pageCount: Int = 1
    ): SavedScanResult {
        return saveScan(
            context = context,
            title = title,
            processedBitmap = result.enhancedBitmap,
            rawBitmap = result.rawOriginal,
            filterUsed = "CamScanner Magic",
            pageCount = pageCount
        )
    }

    /**
     * Extracts unique scan ID from history details string formatted as "scan_id:123|doc_...".
     */
    fun extractScanId(details: String): Long? {
        val trimmed = details.trim()
        if (trimmed.startsWith("scan_id:")) {
            val numPart = trimmed.removePrefix("scan_id:").substringBefore("|").substringBefore(" ").trim()
            return numPart.toLongOrNull()
        }
        val match = Regex("""scan_id:(\d+)""").find(trimmed)
        if (match != null) {
            return match.groupValues[1].toLongOrNull()
        }
        return null
    }

    /**
     * Completely removes a scanned document, associated physical files, and related history entries.
     */
    suspend fun deleteScan(
        context: Context,
        docId: Long,
        fallbackFileName: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            var doc = if (docId > 0) db.scannedDocumentDao().getDocumentById(docId) else null
            if (doc == null && !fallbackFileName.isNullOrBlank()) {
                val allDocs = db.scannedDocumentDao().getAllDocuments().firstOrNull() ?: emptyList()
                doc = allDocs.firstOrNull {
                    it.originalFileName == fallbackFileName ||
                    it.processedImagePath == fallbackFileName ||
                    it.processedImagePath?.endsWith(fallbackFileName) == true
                }
            }
            val targetId = doc?.id ?: docId

            if (doc != null) {
                // Delete physical files
                doc.processedImagePath?.let { File(it).delete() }
                doc.rawImagePath?.let { File(it).delete() }
                doc.thumbnailPath?.let { File(it).delete() }
                doc.pdfFilePath?.let { File(it).delete() }

                // Check candidate paths by filename in case path was relative
                File(context.filesDir, "scanned_docs/${doc.originalFileName}").delete()
                File(context.filesDir, "scanned_images/${doc.originalFileName}").delete()
                File(context.filesDir, "raw_docs/${doc.originalFileName}").delete()
                File(context.filesDir, "raw_scans/${doc.originalFileName}").delete()
                File(context.filesDir, "scan_thumbnails/thumb_${doc.id}.jpg").delete()
                File(context.filesDir, "thumbnails/thumb_${doc.id}.jpg").delete()

                val pagesDir = File(context.filesDir, "doc_${doc.id}_pages")
                if (pagesDir.exists()) pagesDir.deleteRecursively()

                db.scannedDocumentDao().deleteDocument(doc)
            } else if (targetId > 0) {
                db.scannedDocumentDao().deleteDocumentById(targetId)
            }

            if (!fallbackFileName.isNullOrBlank()) {
                File(context.filesDir, "scanned_docs/$fallbackFileName").delete()
                File(context.filesDir, "scanned_images/$fallbackFileName").delete()
                File(context.filesDir, fallbackFileName).delete()
            }

            // Clean up corresponding history records to maintain synchronization
            try {
                val allHistory = db.historyDao().getAllHistory().firstOrNull() ?: emptyList()
                allHistory.filter { history ->
                    (targetId > 0 && history.details.contains("scan_id:$targetId")) ||
                    (!fallbackFileName.isNullOrBlank() && history.details.contains(fallbackFileName))
                }.forEach { matchingHistory ->
                    db.historyDao().deleteById(matchingHistory.id)
                }
            } catch (_: Exception) {}

            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deletes a scan record matching a specific file on disk.
     */
    suspend fun deleteScanByFile(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val allDocs = db.scannedDocumentDao().getAllDocuments().firstOrNull() ?: emptyList()
            val match = allDocs.firstOrNull {
                it.processedImagePath == file.absolutePath ||
                it.originalFileName == file.name ||
                it.pdfFilePath == file.absolutePath
            }

            if (match != null) {
                deleteScan(context, match.id, file.name)
            } else {
                file.delete()
                deleteScan(context, -1L, file.name)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resolves and decodes the original raw camera photo for perspective/enhancement comparison.
     */
    fun loadRawOriginal(context: Context, doc: ScannedDocumentEntity): Bitmap? {
        // 1. Direct path from entity
        doc.rawImagePath?.let { path ->
            val f = File(path)
            if (f.exists() && f.isFile) {
                return BitmapFactory.decodeFile(f.absolutePath)
            }
        }

        // 2. Lookup in raw_docs and raw_scans directory
        val rawDirs = listOf(
            File(context.filesDir, "raw_docs"),
            File(context.filesDir, "raw_scans")
        )

        for (dir in rawDirs) {
            if (!dir.exists()) continue
            val direct = File(dir, "raw_${doc.originalFileName.removePrefix("doc_")}")
            if (direct.exists()) return BitmapFactory.decodeFile(direct.absolutePath)

            val match = dir.listFiles()?.firstOrNull { it.name.contains("${doc.id}") || it.name.contains(doc.originalFileName) }
            if (match != null) return BitmapFactory.decodeFile(match.absolutePath)
        }

        return null
    }

    /**
     * Fallback for loading raw photo by file name.
     */
    fun loadRawOriginal(context: Context, fileName: String): Bitmap? {
        val clean = fileName.removePrefix("doc_").removePrefix("raw_").removeSuffix(".jpg")
        val candidateDirs = listOf(
            File(context.filesDir, "raw_docs"),
            File(context.filesDir, "raw_scans")
        )
        for (dir in candidateDirs) {
            if (!dir.exists()) continue
            val f = File(dir, "raw_$clean.jpg")
            if (f.exists()) return BitmapFactory.decodeFile(f.absolutePath)
            val match = dir.listFiles()?.firstOrNull { it.name.contains(clean) }
            if (match != null) return BitmapFactory.decodeFile(match.absolutePath)
        }
        return null
    }

    fun loadRawOriginalByFile(context: Context, file: File): Bitmap? {
        return loadRawOriginal(context, file.name)
    }

    /**
     * Generates a memory-safe thumbnail if not yet present for an entity.
     */
    suspend fun ensureThumbnail(context: Context, doc: ScannedDocumentEntity): File? = withContext(Dispatchers.IO) {
        val existing = doc.thumbnailPath?.let { File(it) }
        if (existing != null && existing.exists() && existing.length() > 0L) {
            return@withContext existing
        }

        val sourcePath = doc.processedImagePath ?: doc.originalFileName
        val sourceFile = File(sourcePath).takeIf { it.exists() }
            ?: File(context.filesDir, "scanned_docs/${doc.originalFileName}").takeIf { it.exists() }
            ?: File(context.filesDir, "scanned_images/${doc.originalFileName}").takeIf { it.exists() }

        if (sourceFile == null || !sourceFile.exists()) return@withContext null

        try {
            val thumbDir = File(context.filesDir, "scan_thumbnails").apply { mkdirs() }
            val thumbFile = File(thumbDir, "thumb_${doc.id}_${doc.createdAtTimestamp}.jpg")

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOpts)

            val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            var inSample = 1
            while (maxDim / inSample > 400) {
                inSample *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = inSample }
            val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOpts) ?: return@withContext null

            FileOutputStream(thumbFile).use { out ->
                decoded.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            decoded.recycle()

            // Update entity with thumbnail path
            val db = AppDatabase.getInstance(context)
            db.scannedDocumentDao().updateDocument(doc.copy(thumbnailPath = thumbFile.absolutePath))

            return@withContext thumbFile
        } catch (_: Exception) {
            return@withContext null
        }
    }
}
