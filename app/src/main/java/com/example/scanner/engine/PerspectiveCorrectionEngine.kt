package com.example.scanner.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.util.Log
import com.example.utils.DocumentQuad
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.PI

/**
 * Senior Computer Vision Perspective Rectification Engine.
 *
 * Transforms any 4-corner quadrilateral (e.g., a notebook lying at an angle on a bed)
 * into a straightened, flat rectangular document image using OpenCV's:
 * - Imgproc.getPerspectiveTransform()
 * - Imgproc.warpPerspective()
 *
 * Strict corner ordering: [Top-Left, Top-Right, Bottom-Right, Bottom-Left] (Clockwise).
 * Output dimensions are computed from the document's true geometry so the paper is never
 * unnaturally squished or stretched.
 */
object PerspectiveCorrectionEngine {

    private const val TAG = "PerspectiveCorrectionEngine"

    private var isOpenCvAvailable: Boolean = true

    init {
        try {
            System.loadLibrary("opencv_java4")
            isOpenCvAvailable = true
        } catch (e: UnsatisfiedLinkError) {
            isOpenCvAvailable = false
            Log.w(TAG, "OpenCV native library load notice in PerspectiveCorrectionEngine: ${e.message}")
        } catch (_: Exception) {
            isOpenCvAvailable = false
        }
    }

    data class OutputDimensions(
        val width: Int,
        val height: Int,
        val aspectRatio: Float
    )

    data class QuadValidation(
        val isValid: Boolean,
        val isConvex: Boolean,
        val hasPlausibleDimensions: Boolean,
        val hasPlausibleAngles: Boolean,
        val areaRatio: Float,
        val failureReason: String? = null
    )

    /**
     * Orders 4 points consistently: [Top-Left, Top-Right, Bottom-Right, Bottom-Left]
     * Uses sum (x + y) and difference (y - x).
     */
    fun orderPoints(pts: Array<PointF>): Array<PointF> {
        require(pts.size == 4) { "Exactly 4 points required for ordering" }

        // Top-Left has minimum (x + y), Bottom-Right has maximum (x + y)
        val sumSorted = pts.sortedBy { it.x + it.y }
        val tl = sumSorted.first()
        val br = sumSorted.last()

        // Top-Right has minimum (y - x), Bottom-Left has maximum (y - x)
        val diffSorted = pts.sortedBy { it.y - it.x }
        val tr = diffSorted.first()
        val bl = diffSorted.last()

        return arrayOf(tl, tr, br, bl)
    }

    /**
     * Orders a DocumentQuad into strict [TL, TR, BR, BL] sequence.
     */
    fun orderQuad(quad: DocumentQuad): DocumentQuad {
        val ordered = orderPoints(arrayOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft))
        return DocumentQuad(
            topLeft = ordered[0],
            topRight = ordered[1],
            bottomRight = ordered[2],
            bottomLeft = ordered[3],
            isAutoDetected = quad.isAutoDetected,
            confidence = quad.confidence
        )
    }

    /**
     * Calculates the output dimensions according to the true document geometry.
     * Preserves real proportions and avoids unnaturally squishing or stretching.
     */
    fun calculateOutputDimensions(quad: DocumentQuad): OutputDimensions {
        val oq = orderQuad(quad)
        val pTL = oq.topLeft
        val pTR = oq.topRight
        val pBR = oq.bottomRight
        val pBL = oq.bottomLeft

        // 1. Calculate side lengths using Euclidean distance
        val topWidth = hypot((pTR.x - pTL.x).toDouble(), (pTR.y - pTL.y).toDouble())
        val bottomWidth = hypot((pBR.x - pBL.x).toDouble(), (pBR.y - pBL.y).toDouble())
        val leftHeight = hypot((pBL.x - pTL.x).toDouble(), (pBL.y - pTL.y).toDouble())
        val rightHeight = hypot((pBR.x - pTR.x).toDouble(), (pBR.y - pTR.y).toDouble())

        // 2. Compute target dimensions based on maximum perspective foreshortening
        val targetWidth = max(topWidth, bottomWidth).roundToInt().coerceIn(120, 4800)
        val targetHeight = max(leftHeight, rightHeight).roundToInt().coerceIn(120, 4800)

        val aspect = if (targetHeight > 0) targetWidth.toFloat() / targetHeight.toFloat() else 1.0f

        return OutputDimensions(
            width = targetWidth,
            height = targetHeight,
            aspectRatio = aspect
        )
    }

    /**
     * Validates whether a quadrilateral is geometrically plausible for document cropping:
     * - Check points are inside or near image boundaries
     * - Check strict convexity (no inward dents or self-intersecting hourglass)
     * - Check interior corner angles (must be reasonable, 45° to 135°)
     * - Check area ratio against total frame
     */
    fun validateQuad(quad: DocumentQuad, frameWidth: Int, frameHeight: Int): QuadValidation {
        val oq = orderQuad(quad)
        val pTL = oq.topLeft
        val pTR = oq.topRight
        val pBR = oq.bottomRight
        val pBL = oq.bottomLeft

        val fw = frameWidth.toFloat()
        val fh = frameHeight.toFloat()
        val tolerance = 16f

        // 1. Bounds check
        val points = listOf(pTL, pTR, pBR, pBL)
        for (p in points) {
            if (p.x < -tolerance || p.x > fw + tolerance || p.y < -tolerance || p.y > fh + tolerance) {
                return QuadValidation(
                    isValid = false,
                    isConvex = false,
                    hasPlausibleDimensions = false,
                    hasPlausibleAngles = false,
                    areaRatio = 0f,
                    failureReason = "Corner point lies outside image bounds"
                )
            }
        }

        // 2. Strict convexity check via cross-product orientation consistency
        val cp0 = crossProduct(pTL, pTR, pBR)
        val cp1 = crossProduct(pTR, pBR, pBL)
        val cp2 = crossProduct(pBR, pBL, pTL)
        val cp3 = crossProduct(pBL, pTL, pTR)

        val allPositive = cp0 > 0 && cp1 > 0 && cp2 > 0 && cp3 > 0
        val allNegative = cp0 < 0 && cp1 < 0 && cp2 < 0 && cp3 < 0
        val isConvex = allPositive || allNegative

        if (!isConvex) {
            return QuadValidation(
                isValid = false,
                isConvex = false,
                hasPlausibleDimensions = false,
                hasPlausibleAngles = false,
                areaRatio = 0f,
                failureReason = "Polygon is concave or self-intersecting"
            )
        }

        // 3. Area coverage check
        val area = calculatePolygonArea(listOf(pTL, pTR, pBR, pBL))
        val totalArea = fw * fh
        val areaRatio = if (totalArea > 0) area / totalArea else 0f
        if (areaRatio < 0.08f || areaRatio > 0.98f) {
            return QuadValidation(
                isValid = false,
                isConvex = true,
                hasPlausibleDimensions = false,
                hasPlausibleAngles = false,
                areaRatio = areaRatio,
                failureReason = "Polygon area is outside plausible document bounds (${(areaRatio * 100).roundToInt()}%)"
            )
        }

        // 4. Interior angles check (must be roughly rectangular: 45° to 135°)
        val angles = FloatArray(4)
        for (i in 0..3) {
            val pPrev = points[(i + 3) % 4]
            val pCurr = points[i]
            val pNext = points[(i + 1) % 4]

            val v1x = pPrev.x - pCurr.x
            val v1y = pPrev.y - pCurr.y
            val v2x = pNext.x - pCurr.x
            val v2y = pNext.y - pCurr.y

            val dot = v1x * v2x + v1y * v2y
            val mag = hypot(v1x.toDouble(), v1y.toDouble()) * hypot(v2x.toDouble(), v2y.toDouble())
            if (mag <= 0) {
                return QuadValidation(
                    isValid = false,
                    isConvex = true,
                    hasPlausibleDimensions = false,
                    hasPlausibleAngles = false,
                    areaRatio = areaRatio,
                    failureReason = "Degenerate zero-length side detected"
                )
            }
            val cosTheta = (dot / mag).coerceIn(-1.0, 1.0)
            angles[i] = (acos(cosTheta) * 180.0 / PI).toFloat()
            if (angles[i] < 45f || angles[i] > 135f) {
                return QuadValidation(
                    isValid = false,
                    isConvex = true,
                    hasPlausibleDimensions = false,
                    hasPlausibleAngles = false,
                    areaRatio = areaRatio,
                    failureReason = "Corner angle too skewed (${angles[i].roundToInt()}°)"
                )
            }
        }

        // 5. Output dimensions check
        val dims = calculateOutputDimensions(oq)
        if (dims.width < 100 || dims.height < 100 || dims.aspectRatio < 0.35f || dims.aspectRatio > 2.85f) {
            return QuadValidation(
                isValid = false,
                isConvex = true,
                hasPlausibleDimensions = false,
                hasPlausibleAngles = true,
                areaRatio = areaRatio,
                failureReason = "Aspect ratio (${String.format("%.2f", dims.aspectRatio)}) not suitable for document"
            )
        }

        return QuadValidation(
            isValid = true,
            isConvex = true,
            hasPlausibleDimensions = true,
            hasPlausibleAngles = true,
            areaRatio = areaRatio
        )
    }

    /**
     * Executes perspective transformation mapping the 4 detected document corners
     * to a flat, front-facing rectangular document image using OpenCV:
     * - Imgproc.getPerspectiveTransform()
     * - Imgproc.warpPerspective()
     *
     * @param bitmap The full-resolution original captured image.
     * @param quad The quadrilateral defining the 4 document corners.
     * @return The straightened, dewarped rectangular document Bitmap.
     */
    fun transformPerspective(bitmap: Bitmap, quad: DocumentQuad): Bitmap {
        val oq = orderQuad(quad)
        val dims = calculateOutputDimensions(oq)
        val targetWidth = dims.width
        val targetHeight = dims.height

        // Attempt OpenCV Warp Perspective
        try {
            return warpPerspectiveWithOpenCv(bitmap, oq, targetWidth, targetHeight)
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "OpenCV native library not available, falling back to Android Matrix warp: ${e.message}")
            return warpPerspectiveWithMatrixFallback(bitmap, oq, targetWidth, targetHeight)
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV warpPerspective exception, falling back: ${e.message}", e)
            return warpPerspectiveWithMatrixFallback(bitmap, oq, targetWidth, targetHeight)
        }
    }

    /**
     * OpenCV implementation using getPerspectiveTransform() and warpPerspective().
     */
    private fun warpPerspectiveWithOpenCv(
        bitmap: Bitmap,
        quad: DocumentQuad,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)

        val srcPoints = MatOfPoint2f(
            Point(quad.topLeft.x.toDouble(), quad.topLeft.y.toDouble()),
            Point(quad.topRight.x.toDouble(), quad.topRight.y.toDouble()),
            Point(quad.bottomRight.x.toDouble(), quad.bottomRight.y.toDouble()),
            Point(quad.bottomLeft.x.toDouble(), quad.bottomLeft.y.toDouble())
        )

        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((targetWidth - 1).toDouble(), 0.0),
            Point((targetWidth - 1).toDouble(), (targetHeight - 1).toDouble()),
            Point(0.0, (targetHeight - 1).toDouble())
        )

        // Generate 3x3 perspective transformation matrix
        val transformMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        val warpedMat = Mat()

        // Perform warp perspective with high-quality bicubic interpolation
        Imgproc.warpPerspective(
            srcMat,
            warpedMat,
            transformMatrix,
            Size(targetWidth.toDouble(), targetHeight.toDouble()),
            Imgproc.INTER_CUBIC,
            org.opencv.core.Core.BORDER_REPLICATE,
            Scalar(255.0, 255.0, 255.0)
        )

        val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(warpedMat, outputBitmap)

        // Clean up native memory
        srcMat.release()
        warpedMat.release()
        srcPoints.release()
        dstPoints.release()
        transformMatrix.release()

        return outputBitmap
    }

    /**
     * Android Matrix poly-to-poly fallback when OpenCV native binary is unavailable (e.g. JVM tests).
     */
    private fun warpPerspectiveWithMatrixFallback(
        bitmap: Bitmap,
        quad: DocumentQuad,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val src = floatArrayOf(
            quad.topLeft.x, quad.topLeft.y,
            quad.topRight.x, quad.topRight.y,
            quad.bottomRight.x, quad.bottomRight.y,
            quad.bottomLeft.x, quad.bottomLeft.y
        )

        val dst = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)

        return output
    }

    private fun crossProduct(a: PointF, b: PointF, c: PointF): Float {
        return (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
    }

    private fun calculatePolygonArea(pts: List<PointF>): Float {
        var area = 0f
        val n = pts.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += pts[i].x * pts[j].y
            area -= pts[j].x * pts[i].y
        }
        return abs(area) / 2f
    }
}
