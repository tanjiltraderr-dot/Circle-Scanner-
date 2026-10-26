package com.example.scanner.engine

import android.graphics.Bitmap
import android.graphics.PointF
import com.example.utils.DocumentQuad
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object OpenCvDocumentProcessor {

    data class Quadrilateral(
        val topLeft: Point,
        val topRight: Point,
        val bottomRight: Point,
        val bottomLeft: Point
    ) {
        fun toArray(): Array<Point> = arrayOf(topLeft, topRight, bottomRight, bottomLeft)

        fun toDocumentQuad(): DocumentQuad = DocumentQuad(
            topLeft = PointF(topLeft.x.toFloat(), topLeft.y.toFloat()),
            topRight = PointF(topRight.x.toFloat(), topRight.y.toFloat()),
            bottomRight = PointF(bottomRight.x.toFloat(), bottomRight.y.toFloat()),
            bottomLeft = PointF(bottomLeft.x.toFloat(), bottomLeft.y.toFloat())
        )
    }

    private val detectionEngine = DocumentDetectionEngine()

    /**
     * Detect 4 corners of a notebook/paper on any textured background (bedsheet, dark table, wood, etc.)
     * Returns null if no genuine document boundary is detected.
     */
    fun detectNotebookCornersOrNull(bitmap: Bitmap): Quadrilateral? {
        val result = detectionEngine.detect(bitmap)
        if (result.isSuccess && result.quad != null) {
            val q = PerspectiveCorrectionEngine.orderQuad(result.quad)
            return Quadrilateral(
                topLeft = Point(q.topLeft.x.toDouble(), q.topLeft.y.toDouble()),
                topRight = Point(q.topRight.x.toDouble(), q.topRight.y.toDouble()),
                bottomRight = Point(q.bottomRight.x.toDouble(), q.bottomRight.y.toDouble()),
                bottomLeft = Point(q.bottomLeft.x.toDouble(), q.bottomLeft.y.toDouble())
            )
        }
        return null
    }

    /**
     * Default guide corners for manual cropping adjustment when automatic detection is not confident.
     */
    fun getGuideCorners(bitmap: Bitmap): Quadrilateral {
        return Quadrilateral(
            topLeft = Point(bitmap.width * 0.05, bitmap.height * 0.05),
            topRight = Point(bitmap.width * 0.95, bitmap.height * 0.05),
            bottomRight = Point(bitmap.width * 0.95, bitmap.height * 0.95),
            bottomLeft = Point(bitmap.width * 0.05, bitmap.height * 0.95)
        )
    }

    /**
     * Detect 4 corners of a notebook/paper on any textured background (bedsheet, dark table, wood, etc.)
     * Returns genuine detected corners if successful, otherwise falls back to neutral guide margins.
     */
    fun detectNotebookCorners(bitmap: Bitmap): Quadrilateral {
        return detectNotebookCornersOrNull(bitmap) ?: getGuideCorners(bitmap)
    }

    /**
     * Perspective Transform (Warp Perspective) using OpenCV
     * Straightens angled paper into a flat, top-down rectangular view preserving document geometry.
     */
    fun warpPerspective(bitmap: Bitmap, quad: Quadrilateral): Bitmap {
        val docQuad = quad.toDocumentQuad()
        return PerspectiveCorrectionEngine.transformPerspective(bitmap, docQuad)
    }

    /**
     * Image Enhancement & Binarization
     * Adaptive Gaussian Thresholding: whitens paper background and makes text sharp, dark black.
     */
    fun applyAdaptiveBinarization(
        bitmap: Bitmap,
        blockSize: Int = 27,
        subtractedConstantC: Double = 10.0
    ): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)

        val grayMat = Mat()
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)

        val binarizedMat = Mat()
        Imgproc.adaptiveThreshold(
            grayMat,
            binarizedMat,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            blockSize,            // Local neighborhood pixel size (must be odd, e.g. 21, 27, 31)
            subtractedConstantC   // Constant subtracted from mean (removes shadowing)
        )

        val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(binarizedMat, resultBitmap)

        srcMat.release()
        grayMat.release()
        binarizedMat.release()

        return resultBitmap
    }
}
