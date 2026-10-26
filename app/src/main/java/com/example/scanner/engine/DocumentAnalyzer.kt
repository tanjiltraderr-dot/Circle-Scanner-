package com.example.scanner.engine

import android.graphics.Bitmap
import org.opencv.core.Point

object DocumentAnalyzer {

    sealed class ScanResult {
        data class Success(val croppedBitmap: Bitmap, val corners: List<Point>) : ScanResult()
        data class Fallback(val originalFixedBitmap: Bitmap, val reason: String) : ScanResult()
    }

    private val detectionEngine = DocumentDetectionEngine()

    /**
     * Executes the Document Detection Engine & Perspective Rectification pipeline.
     * Prevents false cropping by returning [ScanResult.Fallback] when confidence is low,
     * preserving the original image for manual cropping.
     */
    fun processImage(bitmap: Bitmap): ScanResult {
        val result = detectionEngine.detect(bitmap)

        if (result.isSuccess && result.quad != null && result.confidence >= DocumentDetectionEngine.CONFIDENCE_ACCEPT_THRESHOLD) {
            val orderedQuad = PerspectiveCorrectionEngine.orderQuad(result.quad)
            val validation = PerspectiveCorrectionEngine.validateQuad(orderedQuad, bitmap.width, bitmap.height)

            if (validation.isValid) {
                val cropped = PerspectiveCorrectionEngine.transformPerspective(bitmap, orderedQuad)
                val cornerPoints = listOf(
                    Point(orderedQuad.topLeft.x.toDouble(), orderedQuad.topLeft.y.toDouble()),
                    Point(orderedQuad.topRight.x.toDouble(), orderedQuad.topRight.y.toDouble()),
                    Point(orderedQuad.bottomRight.x.toDouble(), orderedQuad.bottomRight.y.toDouble()),
                    Point(orderedQuad.bottomLeft.x.toDouble(), orderedQuad.bottomLeft.y.toDouble())
                )
                return ScanResult.Success(cropped, cornerPoints)
            }
        }

        // FALLBACK: Could not confidently find document. Return original image for Manual Crop.
        return ScanResult.Fallback(
            originalFixedBitmap = bitmap,
            reason = result.failureReason ?: "Confidence below acceptance threshold (${result.confidence})."
        )
    }

    fun orderPoints(points: List<Point>): List<Point> {
        val pts = points.map { android.graphics.PointF(it.x.toFloat(), it.y.toFloat()) }.toTypedArray()
        val ordered = PerspectiveCorrectionEngine.orderPoints(pts)
        return ordered.map { Point(it.x.toDouble(), it.y.toDouble()) }
    }
}
