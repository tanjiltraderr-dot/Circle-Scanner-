package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.scanner.engine.DocumentDetectionEngine
import com.example.scanner.engine.DetectionStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Point
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentDetectionEngineTest {

    private lateinit var engine: DocumentDetectionEngine
    private var isNativeOpenCvAvailable = false

    @Before
    fun setUp() {
        isNativeOpenCvAvailable = try {
            OpenCVLoader.initLocal()
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: Exception) {
            false
        }
        engine = DocumentDetectionEngine()
    }

    @Test
    fun testPointOrderingAlgorithm() {
        // Points provided in random non-ordered arrangement
        val scrambled = arrayOf(
            Point(380.0, 500.0), // Bottom-Right
            Point(40.0, 50.0),   // Top-Left
            Point(370.0, 60.0),  // Top-Right
            Point(50.0, 490.0)   // Bottom-Left
        )

        val ordered = engine.orderPoints(scrambled)
        assertEquals(4, ordered.size)

        // 1. Top-Left (lowest sum x + y)
        assertEquals(Point(40.0, 50.0), ordered[0])
        // 2. Top-Right (lowest diff y - x)
        assertEquals(Point(370.0, 60.0), ordered[1])
        // 3. Bottom-Right (highest sum x + y)
        assertEquals(Point(380.0, 500.0), ordered[2])
        // 4. Bottom-Left (highest diff y - x)
        assertEquals(Point(50.0, 490.0), ordered[3])
    }

    @Test
    fun testEmptyOrRecycledBitmapReturnsGracefully() {
        val emptyBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val result = engine.detect(emptyBitmap)

        assertEquals(DetectionStatus.NO_DOCUMENT_FOUND, result.status)
        assertNull(result.quad)
        assertFalse(result.isSuccess)
        assertNotNull(result.failureReason)
    }

    @Test
    fun testNonDocumentUniformBackgroundReturnsNoDocument() {
        if (!isNativeOpenCvAvailable) return

        // Flat uniform background with no high-contrast quadrilateral
        val bitmap = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.GRAY)

        val result = engine.detect(bitmap)
        assertFalse("Uniform background should not succeed detection", result.isSuccess)
        assertNotEquals("Should not be SUCCESS", DetectionStatus.SUCCESS, result.status)
    }

    @Test
    fun testWhiteDocumentOnDarkBackgroundDetection() {
        if (!isNativeOpenCvAvailable) return

        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Dark table
        canvas.drawColor(Color.rgb(30, 30, 30))

        // White paper in the center (occupying ~60% of frame)
        val paperPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(90f, 120f, 510f, 680f, paperPaint)

        val result = engine.detect(bitmap)

        if (result.isSuccess) {
            val quad = result.quad
            assertNotNull(quad)
            assertTrue("Detected quad should be marked auto-detected", quad!!.isAutoDetected)
            assertTrue("Confidence should be above threshold", result.confidence >= DocumentDetectionEngine.CONFIDENCE_ACCEPT_THRESHOLD)

            // Verify corners are close to drawn rectangle coordinates
            assertEquals(90f, quad.topLeft.x, 30f)
            assertEquals(120f, quad.topLeft.y, 30f)
            assertEquals(510f, quad.topRight.x, 30f)
            assertEquals(120f, quad.topRight.y, 30f)
            assertEquals(510f, quad.bottomRight.x, 30f)
            assertEquals(680f, quad.bottomRight.y, 30f)
            assertEquals(90f, quad.bottomLeft.x, 30f)
            assertEquals(680f, quad.bottomLeft.y, 30f)

            // Verify geometry
            val geom = result.geometry
            assertNotNull(geom)
            assertTrue("Aspect ratio should be plausible", geom!!.aspectRatio in 0.5f..1.5f)
            assertTrue("Area ratio should be ~50-70%", geom.areaRatio in 0.35f..0.80f)
        }
    }

    @Test
    fun testColoredNotebookOnTableDetection() {
        if (!isNativeOpenCvAvailable) return

        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Wooden desk background
        canvas.drawColor(Color.rgb(110, 75, 45))

        // Blue notebook cover
        val notebookPaint = Paint().apply { color = Color.rgb(25, 75, 180) }
        canvas.drawRect(100f, 140f, 500f, 660f, notebookPaint)

        val result = engine.detect(bitmap)

        if (result.isSuccess) {
            val quad = result.quad
            assertNotNull(quad)
            assertTrue(quad!!.isAutoDetected)
            assertTrue(result.confidence >= DocumentDetectionEngine.CONFIDENCE_ACCEPT_THRESHOLD)
            assertEquals(100f, quad.topLeft.x, 35f)
            assertEquals(500f, quad.topRight.x, 35f)
        }
    }

    @Test
    fun testNotebookOnTexturedBedsheetPattern() {
        if (!isNativeOpenCvAvailable) return

        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw patterned bedsheet (alternating stripes)
        canvas.drawColor(Color.rgb(200, 180, 160))
        val stripePaint = Paint().apply {
            color = Color.rgb(170, 140, 120)
            strokeWidth = 6f
        }
        for (i in 0..height step 20) {
            canvas.drawLine(0f, i.toFloat(), width.toFloat(), (i + 40).toFloat(), stripePaint)
        }

        // White notebook resting on bedsheet
        val paperPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(100f, 150f, 500f, 650f, paperPaint)

        val result = engine.detect(bitmap)
        if (result.isSuccess) {
            val quad = result.quad
            assertNotNull(quad)
            assertTrue(quad!!.isAutoDetected)
        }
    }

    @Test
    fun testAngledDocumentPerspectiveDetection() {
        if (!isNativeOpenCvAvailable) return

        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(40, 40, 40))

        // Angled trapezoidal document
        val path = Path().apply {
            moveTo(140f, 180f) // TL (further away, narrower)
            lineTo(460f, 180f) // TR
            lineTo(520f, 640f) // BR (closer, wider)
            lineTo(80f, 640f)  // BL
            close()
        }
        val paint = Paint().apply { color = Color.WHITE }
        canvas.drawPath(path, paint)

        val result = engine.detect(bitmap)
        if (result.isSuccess) {
            val quad = result.quad
            assertNotNull(quad)
            assertEquals(DetectionStatus.SUCCESS, result.status)
            assertTrue(quad!!.isAutoDetected)
        }
    }
}
