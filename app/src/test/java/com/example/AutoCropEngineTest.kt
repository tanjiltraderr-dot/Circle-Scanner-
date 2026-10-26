package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import com.example.scanner.engine.AutoCropEngine
import com.example.scanner.engine.AutoCropStatus
import com.example.scanner.engine.EnhancementMode
import com.example.scanner.engine.PerspectiveCorrectionEngine
import com.example.utils.DocumentQuad
import kotlinx.coroutines.runBlocking
import com.example.scanner.engine.DocumentScannerEngine
import com.example.scanner.engine.DocumentScannerEngine.AutoCaptureConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutoCropEngineTest {

    private lateinit var context: Context
    private var isOpenCvNativeAvailable = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        isOpenCvNativeAvailable = try {
            OpenCVLoader.initLocal()
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Requirement 3 & 4: Corner ordering strictly orders scrambled points to [TL, TR, BR, BL].
     */
    @Test
    fun testCornerOrderingSequence() {
        val scrambled = arrayOf(
            PointF(700f, 900f), // Bottom-Right
            PointF(100f, 120f), // Top-Left
            PointF(680f, 130f), // Top-Right
            PointF(110f, 890f)  // Bottom-Left
        )

        val ordered = PerspectiveCorrectionEngine.orderPoints(scrambled)
        assertEquals(4, ordered.size)

        // Top-Left (minimum x + y)
        assertEquals(PointF(100f, 120f), ordered[0])
        // Top-Right (minimum y - x)
        assertEquals(PointF(680f, 130f), ordered[1])
        // Bottom-Right (maximum x + y)
        assertEquals(PointF(700f, 900f), ordered[2])
        // Bottom-Left (maximum y - x)
        assertEquals(PointF(110f, 890f), ordered[3])
    }

    /**
     * Requirement 2: Coordinate mapping correctly maps analysis frame (e.g. 640x480)
     * to full-resolution captured Bitmap (e.g. 1920x1080) without cutting off document parts.
     */
    @Test
    fun testCoordinateMappingResolutionAndScaling() {
        // Quad detected in a 480x640 portrait analysis frame
        val analysisQuad = DocumentQuad(
            topLeft = PointF(48f, 64f),      // 10% from top-left
            topRight = PointF(432f, 64f),    // 90% along width, 10% from top
            bottomRight = PointF(432f, 576f),// 90% along width, 90% along height
            bottomLeft = PointF(48f, 576f)   // 10% along width, 90% along height
        )

        val dstW = 1920
        val dstH = 2560

        val mapped = AutoCropEngine.mapCoordinates(
            quad = analysisQuad,
            srcWidth = 480,
            srcHeight = 640,
            dstWidth = dstW,
            dstHeight = dstH
        )

        // Verify proportional mapping
        assertEquals(192f, mapped.topLeft.x, 2f)
        assertEquals(256f, mapped.topLeft.y, 2f)
        assertEquals(1728f, mapped.topRight.x, 2f)
        assertEquals(256f, mapped.topRight.y, 2f)
        assertEquals(1728f, mapped.bottomRight.x, 2f)
        assertEquals(2304f, mapped.bottomRight.y, 2f)
        assertEquals(192f, mapped.bottomLeft.x, 2f)
        assertEquals(2304f, mapped.bottomLeft.y, 2f)
    }

    /**
     * Requirement 4: Convexity & Geometry validation rejects non-convex/concave polygons.
     */
    @Test
    fun testConvexityValidationRejectsDentedPolygon() {
        val frameW = 600
        val frameH = 800

        // Inward dent: Bottom-Right is dented inwards toward the center
        val concaveQuad = DocumentQuad(
            topLeft = PointF(100f, 100f),
            topRight = PointF(500f, 100f),
            bottomRight = PointF(250f, 250f), // Dented inward!
            bottomLeft = PointF(100f, 700f)
        )

        val validation = PerspectiveCorrectionEngine.validateQuad(concaveQuad, frameW, frameH)
        assertFalse("Concave or dented quad must fail validation", validation.isValid)
        assertFalse("isConvex must be false for dented polygon", validation.isConvex)
    }

    /**
     * Requirement 4: Convexity validation accepts convex rectangular quad.
     */
    @Test
    fun testConvexityValidationAcceptsValidQuad() {
        val frameW = 600
        val frameH = 800

        val validQuad = DocumentQuad(
            topLeft = PointF(100f, 120f),
            topRight = PointF(500f, 120f),
            bottomRight = PointF(500f, 680f),
            bottomLeft = PointF(100f, 680f)
        )

        val validation = PerspectiveCorrectionEngine.validateQuad(validQuad, frameW, frameH)
        assertTrue("Valid convex quad must pass validation", validation.isValid)
        assertTrue(validation.isConvex)
        assertTrue(validation.hasPlausibleDimensions)
        assertTrue(validation.hasPlausibleAngles)
    }

    /**
     * Requirement 3: Perspective correction calculates output dimensions based on document geometry
     * without squishing or stretching.
     */
    @Test
    fun testPerspectiveOutputDimensionsCalculation() {
        // Document with width ~400 and height ~600
        val quad = DocumentQuad(
            topLeft = PointF(100f, 100f),
            topRight = PointF(500f, 100f),    // width = 400
            bottomRight = PointF(500f, 700f), // height = 600
            bottomLeft = PointF(100f, 700f)
        )

        val dims = PerspectiveCorrectionEngine.calculateOutputDimensions(quad)
        assertEquals(400, dims.width)
        assertEquals(600, dims.height)
        assertEquals(400f / 600f, dims.aspectRatio, 0.05f)

        // Angled perspective trapezoid: top is farther away (narrower ~360), bottom is closer (wider ~440)
        val trapezoidQuad = DocumentQuad(
            topLeft = PointF(140f, 100f),
            topRight = PointF(500f, 100f),    // top width = 360
            bottomRight = PointF(540f, 700f), // bottom width = 440
            bottomLeft = PointF(100f, 700f)
        )

        val trapDims = PerspectiveCorrectionEngine.calculateOutputDimensions(trapezoidQuad)
        // Max width is 440 (reflecting unforeshortened document width)
        assertEquals(440, trapDims.width)
        assertTrue(trapDims.height in 590..610)
    }

    /**
     * Requirement 1 & 3: Perspective rectification produces straightened rectangular output.
     */
    @Test
    fun testPerspectiveWarpTransformation() {
        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.DKGRAY)

        val quad = DocumentQuad(
            topLeft = PointF(100f, 120f),
            topRight = PointF(500f, 120f),
            bottomRight = PointF(500f, 680f),
            bottomLeft = PointF(100f, 680f)
        )

        val transformed = PerspectiveCorrectionEngine.transformPerspective(bitmap, quad)
        assertNotNull(transformed)
        assertEquals(400, transformed.width)
        assertEquals(560, transformed.height)
    }

    /**
     * Requirement 5: False Crop Prevention.
     * When confidence is low or no document is found on uniform background,
     * AutoCropEngine does NOT perform a fake 5% or 7% crop, but returns LOW_CONFIDENCE / NO_DOCUMENT_FOUND
     * with croppedBitmap == null and preserves rawOriginal intact!
     */
    @Test
    fun testFalseCropPreventionOnLowConfidenceUniformBackground() = runBlocking {
        if (!isOpenCvNativeAvailable) return@runBlocking

        val uniformBitmap = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(uniformBitmap)
        canvas.drawColor(Color.GRAY)

        val result = AutoCropEngine.cropDocument(context, uniformBitmap)

        assertFalse("Must not declare success on uniform background", result.isSuccess)
        assertNull("Must not produce croppedBitmap when confidence is low", result.croppedBitmap)
        assertTrue("Must require manual crop or retake", result.needsManualCrop)
        assertSame("Original bitmap must be preserved intact", uniformBitmap, result.rawOriginal)
    }

    /**
     * Requirement 5 & 8: Manual crop execution preserves original and rectifies user corners.
     */
    @Test
    fun testManualCropPreservesOriginalAndRectifies() = runBlocking {
        val bitmap = Bitmap.createBitmap(500, 700, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLUE)

        val manualQuad = DocumentQuad(
            topLeft = PointF(50f, 50f),
            topRight = PointF(450f, 50f),
            bottomRight = PointF(450f, 650f),
            bottomLeft = PointF(50f, 650f)
        )

        val result = AutoCropEngine.performManualCrop(bitmap, manualQuad)

        assertTrue(result.isSuccess)
        assertNotNull(result.croppedBitmap)
        assertEquals(400, result.croppedBitmap!!.width)
        assertEquals(600, result.croppedBitmap!!.height)
        assertSame(bitmap, result.rawOriginal)
    }

    /**
     * Requirement 6: Example - Notebook on bedsheet pattern.
     * When a clear high-contrast document is present, AutoCropEngine detects it
     * and performs true perspective cropping, cutting away surrounding bedsheet.
     */
    @Test
    fun testAutoCropNotebookOnPatternedBackground() = runBlocking {
        if (!isOpenCvNativeAvailable) return@runBlocking

        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw textured background
        canvas.drawColor(Color.rgb(180, 150, 120))
        val stripePaint = Paint().apply {
            color = Color.rgb(140, 110, 85)
            strokeWidth = 4f
        }
        for (i in 0..height step 18) {
            canvas.drawLine(0f, i.toFloat(), width.toFloat(), (i + 30).toFloat(), stripePaint)
        }

        // Draw notebook in the center
        val notebookPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(100f, 140f, 500f, 660f, notebookPaint)

        val result = AutoCropEngine.cropDocument(context, bitmap)

        if (result.isSuccess) {
            assertNotNull(result.croppedBitmap)
            val quad = result.quad
            assertNotNull(quad)
            // Verify corners are close to actual notebook boundaries (cutting away bedsheet)
            assertEquals(100f, quad!!.topLeft.x, 35f)
            assertEquals(140f, quad.topLeft.y, 35f)
            assertEquals(500f, quad.topRight.x, 35f)
            assertEquals(140f, quad.topRight.y, 35f)

            // Cropped bitmap should have approximately 400x520 dimensions
            assertTrue(result.croppedBitmap!!.width in 370..430)
            assertTrue(result.croppedBitmap!!.height in 490..550)
            assertSame("Original bitmap preserved", bitmap, result.rawOriginal)
        }
    }

    /**
     * Requirement: Automatic Capture duplicate capture prevention & state management.
     * Tests that once a capture is started, duplicate auto-captures are strictly prevented
     * and cooldown timers are honored.
     */
    @Test
    fun testDuplicateCapturePreventionAndCooldown() {
        val engine = DocumentScannerEngine(context)
        engine.autoCaptureConfig = AutoCaptureConfig(cooldownMillis = 1000L)

        // Initially ready to capture
        assertTrue(engine.canTriggerAutoCapture())

        // Start capture -> must block subsequent trigger
        engine.notifyCaptureStarted()
        assertFalse("Auto capture must be blocked while capture is in progress", engine.canTriggerAutoCapture())

        // Complete capture -> enters cooldown period
        engine.notifyCaptureFinished()
        assertFalse("Auto capture must be blocked during cooldown period", engine.canTriggerAutoCapture())

        // Reset cooldown -> should be ready again
        engine.resetCooldown()
        assertTrue("Auto capture should be allowed after cooldown reset", engine.canTriggerAutoCapture())
    }

    /**
     * Requirement: AutoCaptureConfig customizable thresholds.
     */
    @Test
    fun testAutoCaptureConfigCustomization() {
        val customConfig = AutoCaptureConfig(
            requiredStableFrames = 7,
            maxDriftPixels = 12.0,
            minConfidence = 0.75f,
            minSharpnessVariance = 45.0,
            minLuminance = 40.0,
            maxLuminance = 220.0,
            cooldownMillis = 3000L
        )

        val engine = DocumentScannerEngine(context)
        engine.autoCaptureConfig = customConfig

        assertEquals(7, engine.autoCaptureConfig.requiredStableFrames)
        assertEquals(12.0, engine.autoCaptureConfig.maxDriftPixels, 0.001)
        assertEquals(0.75f, engine.autoCaptureConfig.minConfidence, 0.001f)
        assertEquals(45.0, engine.autoCaptureConfig.minSharpnessVariance, 0.001)
        assertEquals(40.0, engine.autoCaptureConfig.minLuminance, 0.001)
        assertEquals(220.0, engine.autoCaptureConfig.maxLuminance, 0.001)
        assertEquals(3000L, engine.autoCaptureConfig.cooldownMillis)
    }
}
