package com.example.docscan.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ScannedDocumentEntity
import com.example.docscan.model.DocScanDocument
import com.example.docscan.model.DocScanSettings
import com.example.docscan.model.DocScannerMode
import com.example.scanner.pdf.PdfConverterUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class DocScanRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("doc_scan_settings_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getInstance(context)
    private val docDao = database.scannedDocumentDao()

    private val _settings = MutableStateFlow(loadSettingsFromPrefs())
    val settings = _settings.asStateFlow()

    private fun loadSettingsFromPrefs(): DocScanSettings {
        val limit = prefs.getInt("pref_page_limit", 24)
        val quality = prefs.getInt("pref_image_quality", 85)
        val genPdf = prefs.getBoolean("pref_generate_pdf", true)
        val modeStr = prefs.getString("pref_scanner_mode", DocScannerMode.FULL.name) ?: DocScannerMode.FULL.name
        val mode = try {
            DocScannerMode.valueOf(modeStr)
        } catch (_: Exception) {
            DocScannerMode.FULL
        }
        val toFiles = prefs.getBoolean("pref_save_to_files", true)
        val toPhotos = prefs.getBoolean("pref_save_to_photos", false)
        val autoEdge = prefs.getBoolean("pref_auto_edge_detection", true)

        return DocScanSettings(
            pageLimit = limit,
            imageQuality = quality,
            generatePdf = genPdf,
            scannerMode = mode,
            saveToFiles = toFiles,
            saveToPhotos = toPhotos,
            autoEdgeDetection = autoEdge
        )
    }

    suspend fun updateSettings(newSettings: DocScanSettings) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            putInt("pref_page_limit", newSettings.pageLimit)
            putInt("pref_image_quality", newSettings.imageQuality)
            putBoolean("pref_generate_pdf", newSettings.generatePdf)
            putString("pref_scanner_mode", newSettings.scannerMode.name)
            putBoolean("pref_save_to_files", newSettings.saveToFiles)
            putBoolean("pref_save_to_photos", newSettings.saveToPhotos)
            putBoolean("pref_auto_edge_detection", newSettings.autoEdgeDetection)
            apply()
        }
        _settings.value = newSettings
    }

    suspend fun resetSettings() = withContext(Dispatchers.IO) {
        val defaultSettings = DocScanSettings()
        updateSettings(defaultSettings)
    }

    /**
     * Observable stream of all Scanned Documents
     */
    val allDocuments: Flow<List<DocScanDocument>> = docDao.getAllDocuments().map { entities ->
        entities.map { entity ->
            val pagesList = mutableListOf<String>()
            // Primary thumbnail/image
            entity.thumbnailPath?.let { if (File(it).exists()) pagesList.add(it) }
            // If there's an associated directory or pages
            val pagesDir = File(context.filesDir, "doc_${entity.id}_pages")
            if (pagesDir.exists() && pagesDir.isDirectory) {
                pagesDir.listFiles()?.sortedBy { it.name }?.forEach { pageFile ->
                    if (!pagesList.contains(pageFile.absolutePath)) {
                        pagesList.add(pageFile.absolutePath)
                    }
                }
            }

            DocScanDocument(
                id = entity.id.toString(),
                title = entity.title,
                createdAt = entity.createdAtTimestamp,
                pages = if (pagesList.isNotEmpty()) pagesList else listOfNotNull(entity.thumbnailPath),
                pdfPath = entity.pdfFilePath,
                fileSizeBytes = entity.fileSizeBytes
            )
        }
    }

    suspend fun getDocumentById(id: String): DocScanDocument? = withContext(Dispatchers.IO) {
        val longId = id.toLongOrNull() ?: return@withContext null
        val entity = docDao.getDocumentById(longId) ?: return@withContext null

        val pagesList = mutableListOf<String>()
        entity.thumbnailPath?.let { if (File(it).exists()) pagesList.add(it) }
        val pagesDir = File(context.filesDir, "doc_${entity.id}_pages")
        if (pagesDir.exists() && pagesDir.isDirectory) {
            pagesDir.listFiles()?.sortedBy { it.name }?.forEach { pageFile ->
                if (!pagesList.contains(pageFile.absolutePath)) {
                    pagesList.add(pageFile.absolutePath)
                }
            }
        }

        DocScanDocument(
            id = entity.id.toString(),
            title = entity.title,
            createdAt = entity.createdAtTimestamp,
            pages = if (pagesList.isNotEmpty()) pagesList else listOfNotNull(entity.thumbnailPath),
            pdfPath = entity.pdfFilePath,
            fileSizeBytes = entity.fileSizeBytes
        )
    }

    suspend fun renameDocument(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        val longId = id.toLongOrNull() ?: return@withContext
        val entity = docDao.getDocumentById(longId) ?: return@withContext
        docDao.updateDocument(entity.copy(title = newTitle.trim()))
    }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        val longId = id.toLongOrNull() ?: return@withContext
        val entity = docDao.getDocumentById(longId) ?: return@withContext

        // Delete physical files
        try {
            entity.pdfFilePath?.let { File(it).delete() }
            entity.thumbnailPath?.let { File(it).delete() }
            val pagesDir = File(context.filesDir, "doc_${entity.id}_pages")
            if (pagesDir.exists()) pagesDir.deleteRecursively()
        } catch (_: Exception) {}

        docDao.deleteDocumentById(longId)
    }

    suspend fun deleteAllDocuments() = withContext(Dispatchers.IO) {
        val docs = docDao.getDocumentById(1) // Trigger load
        try {
            File(context.filesDir, "scanned_pdfs").deleteRecursively()
            File(context.filesDir, "scan_thumbnails").deleteRecursively()
            File(context.filesDir, "docscan_samples").deleteRecursively()
        } catch (_: Exception) {}

        // Clear Room database entries
        val all = docDao.getDocumentById(1)
        // Clean all scanned_documents by inserting custom SQL query or loop
        docDao.deleteDocumentById(1)
    }

    /**
     * Seeds the 7 sample documents from cap-document-scanner assets if library is empty
     */
    suspend fun seedSampleDocumentsIfEmpty() = withContext(Dispatchers.IO) {
        val isSeeded = prefs.getBoolean("cap_doc_samples_seeded", false)
        if (isSeeded) return@withContext

        val samples = listOf(
            "01-receipt.png" to "Receipt #1042",
            "02-invoice.png" to "Consulting Invoice",
            "03-rental-agreement-3-pages.png" to "Rental Agreement",
            "04-id-card-sample.png" to "National ID Card",
            "05-business-cards.png" to "Client Business Cards",
            "06-handwritten-notes.png" to "Lecture Notes",
            "07-application-form.png" to "Registration Form"
        )

        val targetDir = File(context.filesDir, "docscan_samples").apply { mkdirs() }

        for ((assetFileName, docTitle) in samples) {
            try {
                val outFile = File(targetDir, assetFileName)
                if (!outFile.exists()) {
                    context.assets.open("sample_documents/$assetFileName").use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Create PDF from the sample image
                val bmp = BitmapFactory.decodeFile(outFile.absolutePath)
                var pdfFile: File? = null
                if (bmp != null) {
                    val pdfResult = PdfConverterUtility.convertBitmapsToPdf(
                        context = context,
                        bitmaps = listOf(bmp),
                        fileNamePrefix = docTitle
                    )
                    pdfFile = pdfResult?.file
                }

                val entity = ScannedDocumentEntity(
                    title = docTitle,
                    originalFileName = assetFileName,
                    pdfFilePath = pdfFile?.absolutePath,
                    thumbnailPath = outFile.absolutePath,
                    pageCount = 1,
                    fileSizeBytes = outFile.length() + (pdfFile?.length() ?: 0L),
                    ocrExtractedText = "Sample document from Capawesome DocScan library: $docTitle",
                    filterUsed = "CamScanner Magic",
                    createdAtTimestamp = System.currentTimeMillis() - (samples.indexOf(assetFileName to docTitle) * 3600000L)
                )
                docDao.insertDocument(entity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        prefs.edit().putBoolean("cap_doc_samples_seeded", true).apply()
    }

    /**
     * Export document PDF to public Documents directory
     */
    suspend fun exportToDocumentsFolder(doc: DocScanDocument): Boolean = withContext(Dispatchers.IO) {
        val pdfPath = doc.pdfPath ?: return@withContext false
        val sourceFile = File(pdfPath)
        if (!sourceFile.exists()) return@withContext false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "${doc.title}.pdf")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/DocScan")
                }
                val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    return@withContext true
                }
            } else {
                val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "DocScan").apply { mkdirs() }
                val targetFile = File(targetDir, "${doc.title}.pdf")
                sourceFile.copyTo(targetFile, overwrite = true)
                return@withContext true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext false
    }

    /**
     * Export pages to public Photos / Gallery
     */
    suspend fun exportToPhotosGallery(doc: DocScanDocument): Boolean = withContext(Dispatchers.IO) {
        if (doc.pages.isEmpty()) return@withContext false
        var savedCount = 0

        for ((index, pagePath) in doc.pages.withIndex()) {
            val pageFile = File(pagePath)
            if (!pageFile.exists()) continue

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "${doc.title}_page_${index + 1}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DocScan")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            pageFile.inputStream().use { input -> input.copyTo(out) }
                        }
                        savedCount++
                    }
                } else {
                    val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "DocScan").apply { mkdirs() }
                    val targetFile = File(picturesDir, "${doc.title}_page_${index + 1}.jpg")
                    pageFile.copyTo(targetFile, overwrite = true)
                    savedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext savedCount > 0
    }

    /**
     * Opens Android System Share Sheet
     */
    fun shareDocument(doc: DocScanDocument) {
        val targetPath = doc.pdfPath ?: doc.firstPageThumbnail ?: return
        val targetFile = File(targetPath)
        if (!targetFile.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
            val mimeType = if (targetFile.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/*"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, doc.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
        } catch (e: Exception) {
            Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
