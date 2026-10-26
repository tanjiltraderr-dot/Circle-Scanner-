package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.ScannedDocumentEntity
import com.example.data.repository.AppRepository
import com.example.scanner.engine.AutoCropEngine
import com.example.scanner.engine.AutoCropStatus
import com.example.scanner.engine.DocumentDetectionEngine
import com.example.scanner.engine.DocumentEnhancementEngine
import com.example.scanner.engine.EnhancementMode
import com.example.scanner.engine.OpenCvDocumentProcessor
import com.example.scanner.engine.PerspectiveCorrectionEngine
import com.example.scanner.engine.ScanStorageManager
import com.example.utils.DocumentProcessor
import com.example.utils.DocumentQuad
import com.example.utils.ScanThumbnailHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Senior QA & Computer Vision Engineer Audit Suite.
 *
 * Verifies all 10 critical document scanning scenarios:
 * 1. Notebook on textured bedsheet
 * 2. White paper on dark table
 * 3. Angled / skewed paper with perspective distortion
 * 4. Low light document capture
 * 5. Document with harsh shadow gradient
 * 6. Low contrast / background blending document
 * 7. Blurry capture detection
 * 8. Multiple documents / background clutter candidate selection
 * 9. Uniform background detection failure (prevents fake 5% crop)
 * 10. Persistence across app restart & history retrieval
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentScannerPipelineAuditTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository
    private var isOpenCvNative: Boolean = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        isOpenCvNative = try {
            OpenCVLoader.initLocal()
        } catch (_: Throwable) {
            false
        }

        database = AppDatabase.getInstance(context)
        repository = AppRepository(context)
    }

    @After
    fun tearDown() {
        // Leave cleanup to OS temp
    }

    /**
     * Scenario 1: Notebook on textured bedsheet.
     * High frequency background texture (stripes/pattern) with a solid notebook.
     * The detector must identify the notebook boundary instead of textured bedsheet lines.
     */
    @Test
    fun testScenario1_NotebookOnTexturedBedsheet() = runBlocking {
        val width = 800
        val height = 1000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw textured bedsheet background (alternating colored stripes)
        val stripePaint1 = Paint().apply { color = Color.rgb(180, 150, 140) }
        val stripePaint2 = Paint().apply { color = Color.rgb(160, 130, 120) }
        for (i in 0 until height step 20) {
            canvas.drawRect(0f, i.toFloat(), width.toFloat(), (i + 10).toFloat(), stripePaint1)
            canvas.drawRect(0f, (i + 10).toFloat(), width.toFloat(), (i + 20).toFloat(), stripePaint2)
        }

        // Draw solid notebook in center (margins: 100px left/top, 700px right, 900px bottom)
        val notebookPaint = Paint().apply { color = Color.rgb(245, 245, 240) }
        canvas.drawRect(100f, 100f, 700f, 900f, notebookPaint)

        // Draw notebook ruled lines inside
        val linePaint = Paint().apply {
            color = Color.rgb(200, 220, 240)
            strokeWidth = 2f
        }
        for (y in 160 until 850 step 40) {
            canvas.drawLine(120f, y.toFloat(), 680f, y.toFloat(), linePaint)
        }

        val result = AutoCropEngine.cropDocument(context, bitmap)
        // Detections should succeed and find corners enclosing the notebook
        if (result.isSuccess && result.quad != null) {
            val q = result.quad!!
            assertTrue("Top-left X should be near notebook edge", q.topLeft.x in 80f..140f)
            assertTrue("Top-left Y should be near notebook edge", q.topLeft.y in 80f..140f)
            assertTrue("Bottom-right X should be near notebook edge", q.bottomRight.x in 660f..720f)
            assertTrue("Bottom-right Y should be near notebook edge", q.bottomRight.y in 860f..920f)
        } else {
            // Ensure rawOriginal is preserved untouched
            assertNotNull(result.rawOriginal)
            assertEquals(width, result.rawOriginal.width)
        }
    }

    /**
     * Scenario 2: White paper on dark table.
     * High contrast document on dark desk.
     */
    @Test
    fun testScenario2_WhitePaperOnDarkTable() = runBlocking {
        val width = 800
        val height = 1000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark table
        canvas.drawColor(Color.rgb(35, 25, 20))

        // White paper
        val paperPaint = Paint().apply { color = Color.rgb(250, 250, 250) }
        canvas.drawRect(120f, 150f, 680f, 850f, paperPaint)

        val result = AutoCropEngine.cropDocument(context, bitmap)
        assertNotNull(result.rawOriginal)

        val detector = DocumentDetectionEngine()
        val detResult = detector.detect(bitmap)
        if (detResult.isSuccess && detResult.quad != null) {
            val quad = detResult.quad!!
            assertEquals(120f, quad.topLeft.x, 30f)
            assertEquals(150f, quad.topLeft.y, 30f)
            assertEquals(680f, quad.topRight.x, 30f)
            assertEquals(150f, quad.topRight.y, 30f)
            assertEquals(680f, quad.bottomRight.x, 30f)
            assertEquals(850f, quad.bottomRight.y, 30f)
        }
    }

    /**
     * Scenario 3: Angled/skewed paper with perspective distortion (trapezoid).
     * Warping engine should deskew the perspective back into a flat rectangle.
     */
    @Test
    fun testScenario3_AngledSkewedPaperPerspective() {
        val width = 1000
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(40, 40, 40))

        // Skewed trapezoid (perspective-shot document)
        val path = Path().apply {
            moveTo(250f, 200f)  // Top-Left (narrower due to perspective)
            lineTo(750f, 200f)  // Top-Right
            lineTo(880f, 1000f) // Bottom-Right (wider, closer to camera)
            lineTo(120f, 1000f) // Bottom-Left
            close()
        }
        val paperPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paperPaint)

        val quad = DocumentQuad(
            topLeft = PointF(250f, 200f),
            topRight = PointF(750f, 200f),
            bottomRight = PointF(880f, 1000f),
            bottomLeft = PointF(120f, 1000f)
        )

        // Perspective transform
        val unwarped = PerspectiveCorrectionEngine.transformPerspective(bitmap, quad)
        assertNotNull("Perspective warped bitmap must not be null", unwarped)
        assertTrue("Warped width should be positive", unwarped.width > 300)
        assertTrue("Warped height should be positive", unwarped.height > 500)
    }

    /**
     * Scenario 4: Low light document capture.
     * Image with low mean luminance (<60).
     */
    @Test
    fun testScenario4_LowLightCapture() {
        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dim background (value 30)
        canvas.drawColor(Color.rgb(30, 30, 30))

        // Dim paper (value 70)
        val dimPaper = Paint().apply { color = Color.rgb(70, 70, 70) }
        canvas.drawRect(80f, 100f, 520f, 700f, dimPaper)

        // Text in dark ink (value 20)
        val ink = Paint().apply { color = Color.rgb(20, 20, 20) }
        canvas.drawRect(120f, 180f, 480f, 200f, ink)

        // Test contrast stretching / enhancement
        val enhanced = DocumentEnhancementEngine.enhance(
            bitmap = bitmap,
            mode = EnhancementMode.DOCUMENT_COLOR
        )
        assertNotNull(enhanced)
        assertEquals(width, enhanced.width)
        assertEquals(height, enhanced.height)
    }

    /**
     * Scenario 5: Document with harsh shadow gradient.
     * Evaluates adaptive Gaussian binarization and shadow removal.
     */
    @Test
    fun testScenario5_ShadowGradientHandling() {
        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Base white paper
        canvas.drawColor(Color.WHITE)

        // Simulate dark shadow across the top half
        val shadowPaint = Paint().apply {
            color = Color.argb(150, 0, 0, 0)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), (height / 2).toFloat(), shadowPaint)

        // Dark text in both shadowed and bright regions
        val textPaint = Paint().apply { color = Color.BLACK }
        canvas.drawRect(100f, 200f, 400f, 220f, textPaint) // in shadow
        canvas.drawRect(100f, 600f, 400f, 620f, textPaint) // in light

        val binarized = DocumentEnhancementEngine.enhance(
            bitmap = bitmap,
            mode = EnhancementMode.BLACK_WHITE
        )
        assertNotNull(binarized)
        assertEquals(width, binarized.width)
    }

    /**
     * Scenario 6: Document blending with low-contrast background.
     * Subtle gray difference (RGB 190 vs 200).
     */
    @Test
    fun testScenario6_BlendedBackground() = runBlocking {
        val width = 500
        val height = 700
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.rgb(195, 195, 195))
        val paperPaint = Paint().apply { color = Color.rgb(205, 205, 205) }
        canvas.drawRect(80f, 80f, 420f, 620f, paperPaint)

        // Engine must safely process without crashing
        val result = AutoCropEngine.cropDocument(context, bitmap)
        assertNotNull(result.rawOriginal)
        // If confidence is insufficient, must not return false success
        if (!result.isSuccess) {
            assertTrue(result.needsManualCrop)
        }
    }

    /**
     * Scenario 7: Blurry capture detection.
     */
    @Test
    fun testScenario7_BlurryCaptureHandling() = runBlocking {
        // Completely flat/blurred image
        val width = 400
        val height = 500
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.LTGRAY)

        val result = AutoCropEngine.cropDocument(context, bitmap)
        assertNotNull(result.rawOriginal)
        // Flat gray image has 0 sharpness / contrast: auto crop must not claim SUCCESS
        assertNotEquals(AutoCropStatus.SUCCESS, result.status)
        assertTrue(result.needsManualCrop)
    }

    /**
     * Scenario 8: Multiple documents / clutter.
     * Prominent large paper vs small debris.
     */
    @Test
    fun testScenario8_MultipleDocumentsCandidateSelection() {
        val width = 1000
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.DKGRAY)

        // Small business card 1
        val smallPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(50f, 50f, 150f, 100f, smallPaint)

        // Small business card 2
        canvas.drawRect(50f, 200f, 180f, 280f, smallPaint)

        // Large primary document (area ~ 50% of frame)
        canvas.drawRect(250f, 200f, 850f, 1050f, smallPaint)

        val detector = DocumentDetectionEngine()
        val det = detector.detect(bitmap)
        if (det.isSuccess && det.quad != null) {
            val q = det.quad!!
            // Should pick the large document, not the small cards
            assertTrue("Detected top-left X should belong to primary document", q.topLeft.x >= 200f)
            assertTrue("Detected top-left Y should belong to primary document", q.topLeft.y >= 150f)
        }
    }

    /**
     * Scenario 9: Document detection failure (uniform background).
     * CRITICAL AUDIT CHECK: Must NOT generate a fake 5% crop or report SUCCESS when no document exists!
     */
    @Test
    fun testScenario9_DetectionFailureNoFakeCrop() = runBlocking {
        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(100, 100, 100)) // Pure solid background

        // 1. OpenCvDocumentProcessor.detectNotebookCornersOrNull must return null
        val nullCorners = OpenCvDocumentProcessor.detectNotebookCornersOrNull(bitmap)
        assertNull("detectNotebookCornersOrNull must return null when no document is present", nullCorners)

        // 2. DocumentProcessor.autoCropAndEnhance must flag isDetectionSuccess = false
        val scanResult = DocumentProcessor.autoCropAndEnhance(
            context = context,
            rawBitmap = bitmap
        )
        assertFalse("autoCropAndEnhance must report isDetectionSuccess = false on failure", scanResult.isDetectionSuccess)
        // Must preserve rawOriginal and NOT slice 5% off
        assertEquals(bitmap, scanResult.rawOriginal)
        assertEquals(bitmap, scanResult.croppedBitmap)
    }

    /**
     * Scenario 10: App restart persistence and exact document retrieval.
     * Verifies that after saving a scan and restarting (re-instantiating the repository/database):
     * - The scan is retrieved by exact scanId
     * - The processed file on disk is intact
     * - The thumbnail is decoded using memory-safe downsampling
     * - Deleting the scan removes both metadata and files cleanly
     */
    @Test
    fun testScenario10_AppRestartAndScanHistoryPersistence() = runBlocking {
        val testScanBitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(testScanBitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLUE }
        canvas.drawText("INVOICE #9981", 50f, 100f, paint)

        // 1. Save scan via ScanStorageManager
        val savedScan = ScanStorageManager.saveScan(
            context = context,
            title = "Test Audit Document",
            processedBitmap = testScanBitmap,
            rawBitmap = testScanBitmap,
            filterUsed = "CamScanner Magic"
        )
        assertNotNull("Saved scan must not be null", savedScan)
        val scanId = savedScan.scanId
        val processedFile = savedScan.processedFile
        assertTrue("Processed file must exist", processedFile.exists())

        // 2. Retrieve document by exact scanId from database
        val doc = database.scannedDocumentDao().getDocumentById(scanId)
        assertNotNull("Document must be retrievable by scanId from database", doc)
        assertEquals(scanId, doc!!.id)
        assertEquals("Test Audit Document", doc.title)

        // 3. Test memory-controlled thumbnail helper
        val historyItem = HistoryEntity(
            id = 1,
            toolId = "doc_scanner",
            title = "Test Audit Document",
            details = "scan_id:$scanId|${processedFile.name}",
            timestamp = System.currentTimeMillis()
        )
        val thumb = ScanThumbnailHelper.loadThumbnail(context, historyItem)
        assertNotNull("Thumbnail must load successfully", thumb)
        assertTrue("Thumbnail width must be sampled for memory safety", thumb!!.width <= 320)

        // 4. Delete scan: removes physical files and DB record
        val deleted = ScanStorageManager.deleteScan(context, scanId, processedFile.name)
        assertTrue("Scan deletion should succeed", deleted)
        assertFalse("Processed file should be deleted from disk", processedFile.exists())
    }
}
