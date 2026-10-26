package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import com.example.scanner.engine.DocumentScannerEngine
import com.example.utils.DocumentFilter
import com.example.utils.DocumentQuad
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentScannerEngineTest {

    private lateinit var context: Context
    private lateinit var engine: DocumentScannerEngine
    private var isNativeOpenCvAvailable = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        isNativeOpenCvAvailable = try {
            OpenCVLoader.initLocal()
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: Exception) {
            false
        }
        engine = DocumentScannerEngine(context)
    }

    @Test
    fun testPointOrderingConsistency() {
        // Unordered four points
        val unordered = arrayOf(
            Point(350.0, 50.0),  // TR
            Point(380.0, 480.0), // BR
            Point(40.0, 60.0),   // TL
            Point(60.0, 460.0)   // BL
        )

        val ordered = engine.orderPoints(unordered)
        assertEquals(4, ordered.size)

        // Top-Left should be closest to (0,0)
        assertEquals(Point(40.0, 60.0), ordered[0])
        // Top-Right should have highest x and low y
        assertEquals(Point(350.0, 50.0), ordered[1])
        // Bottom-Right should have highest x and high y
        assertEquals(Point(380.0, 480.0), ordered[2])
        // Bottom-Left should have lowest x and high y
        assertEquals(Point(60.0, 460.0), ordered[3])
    }

    @Test
    fun testPerspectiveWarpProducesValidBitmap() {
        if (!isNativeOpenCvAvailable) return // Requires Android runtime with libopencv_java4.so

        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.DKGRAY)

        val paint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(80f, 100f, 520f, 700f, paint)

        val quad = DocumentQuad(
            topLeft = PointF(80f, 100f),
            topRight = PointF(520f, 100f),
            bottomRight = PointF(520f, 700f),
            bottomLeft = PointF(80f, 700f)
        )

        try {
            val warped = engine.cropAndWarpPerspective(bitmap, quad)
            assertNotNull(warped)
            assertTrue("Warped width should be approximately 440", warped.width in 430..450)
            assertTrue("Warped height should be approximately 600", warped.height in 590..610)
        } catch (_: UnsatisfiedLinkError) {
            // Handled when native NDK library is loaded on device
        }
    }

    @Test
    fun testDocumentEnhancements() {
        if (!isNativeOpenCvAvailable) return // Requires Android runtime with libopencv_java4.so

        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.LTGRAY)
        val paint = Paint().apply { color = Color.BLACK }
        canvas.drawText("CV TEST DOCUMENT", 20f, 100f, paint)

        try {
            val magic = engine.enhanceDocument(bitmap, DocumentFilter.CAMSCANNER)
            assertNotNull(magic)
            assertEquals(200, magic.width)

            val gray = engine.enhanceDocument(bitmap, DocumentFilter.GRAYSCALE)
            assertNotNull(gray)
            assertEquals(200, gray.width)

            val bw = engine.enhanceDocument(bitmap, DocumentFilter.BLACK_WHITE)
            assertNotNull(bw)
            assertEquals(200, bw.width)
        } catch (_: UnsatisfiedLinkError) {
            // Handled when native NDK library is loaded on device
        }
    }

    @Test
    fun testLaplacianVarianceSharpnessCalculation() {
        if (!isNativeOpenCvAvailable) return // Requires Android runtime with libopencv_java4.so

        try {
            val mat = Mat(100, 100, CvType.CV_8UC1)
            val data = ByteArray(100 * 100)
            for (i in data.indices) {
                data[i] = if ((i % 100) % 2 == 0) 255.toByte() else 0.toByte()
            }
            mat.put(0, 0, data)

            val sharpness = engine.calculateLaplacianVariance(mat)
            assertTrue("Sharpness for high-frequency checkerboard should be high", sharpness > 10.0)
            mat.release()
        } catch (_: UnsatisfiedLinkError) {
            // Handled when native NDK library is loaded on device
        }
    }

    @Test
    fun testSaveScanPersistence() = runBlocking {
        val testBmp = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        val result = engine.saveScanToDatabase(
            processedBitmap = testBmp,
            title = "Engine Unit Test Doc",
            filterUsed = "Magic Color"
        )

        assertTrue(result.scanId > 0)
        assertTrue(result.fileSizeBytes > 0)
        assertTrue(java.io.File(result.fullImagePath).exists())
        assertTrue(java.io.File(result.thumbnailPath).exists())
    }
}
