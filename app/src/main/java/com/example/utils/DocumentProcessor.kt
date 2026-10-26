package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.*

enum class DocumentFilter(val label: String) {
    ORIGINAL("Original Color"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("Black & White"),
    DOCUMENT_CONTRAST("Document Contrast"),
    NOISE_REDUCTION("Noise Reduction"),
    SHARPEN("Controlled Sharpen"),
    MAGIC_COLOR("Color"),
    CAMSCANNER("CamScanner Magic"),
    NO_SHADOW("No Shadow");

    companion object {
        // Compatibility aliases
        val LIGHTEN get() = DOCUMENT_CONTRAST
        val AUTO_ENHANCE get() = CAMSCANNER
        val ENHANCE get() = DOCUMENT_CONTRAST
    }
}

data class DocumentQuad(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF,
    val isAutoDetected: Boolean = true,
    val confidence: Float = 0.8f
)

data class RealtimeDetectionResult(
    val quad: DocumentQuad,
    val bufferWidth: Int,
    val bufferHeight: Int,
    val confidence: Float,
    val isReliable: Boolean
)

data class ScannedPageItem(
    val rawOriginal: Bitmap,
    var croppedOriginal: Bitmap,
    var processed: Bitmap,
    var activeFilter: DocumentFilter = DocumentFilter.AUTO_ENHANCE,
    var rotationAngle: Float = 0f,
    var ocrText: String? = null,
    var quad: DocumentQuad? = null
) {
    // Secondary constructor for backward compatibility
    constructor(
        original: Bitmap,
        processed: Bitmap,
        activeFilter: DocumentFilter = DocumentFilter.AUTO_ENHANCE,
        rotationAngle: Float = 0f,
        ocrText: String? = null
    ) : this(
        rawOriginal = original,
        croppedOriginal = processed,
        processed = processed,
        activeFilter = activeFilter,
        rotationAngle = rotationAngle,
        ocrText = ocrText,
        quad = null
    )

    // Property alias for backward compatibility
    val original: Bitmap get() = rawOriginal
}

/**
 * Temporal smoother and stability analyzer across consecutive camera preview frames.
 * Prevents jitter, flickering, and provides stable trigger for automatic capture.
 */
class DocumentCornerTracker {
    private var smoothedQuad: DocumentQuad? = null
    private var consecutiveStableFrames = 0
    private var lastQuad: DocumentQuad? = null
    var isStable: Boolean = false
        private set

    fun track(newResult: RealtimeDetectionResult?): DocumentQuad? {
        if (newResult == null || !newResult.isReliable) {
            consecutiveStableFrames = 0
            isStable = false
            smoothedQuad = null
            lastQuad = null
            return null
        }

        val nq = newResult.quad
        val lq = lastQuad
        if (lq == null) {
            smoothedQuad = nq
            lastQuad = nq
            consecutiveStableFrames = 1
            isStable = false
            return nq
        }

        // Distance moved by corners between frames
        val dTL = hypot((nq.topLeft.x - lq.topLeft.x).toDouble(), (nq.topLeft.y - lq.topLeft.y).toDouble())
        val dTR = hypot((nq.topRight.x - lq.topRight.x).toDouble(), (nq.topRight.y - lq.topRight.y).toDouble())
        val dBR = hypot((nq.bottomRight.x - lq.bottomRight.x).toDouble(), (nq.bottomRight.y - lq.bottomRight.y).toDouble())
        val dBL = hypot((nq.bottomLeft.x - lq.bottomLeft.x).toDouble(), (nq.bottomLeft.y - lq.bottomLeft.y).toDouble())
        val maxDist = maxOf(dTL, dTR, dBR, dBL)

        val moveThreshold = newResult.bufferWidth * 0.035f

        if (maxDist > moveThreshold * 4.5f) {
            // Sudden large camera shift or document moved - reset smoother
            smoothedQuad = nq
            consecutiveStableFrames = 1
            isStable = false
        } else {
            // Exponential moving average filter for smooth coordinates
            val alpha = 0.35f
            val prev = smoothedQuad ?: lq
            val sTL = PointF(prev.topLeft.x * (1 - alpha) + nq.topLeft.x * alpha, prev.topLeft.y * (1 - alpha) + nq.topLeft.y * alpha)
            val sTR = PointF(prev.topRight.x * (1 - alpha) + nq.topRight.x * alpha, prev.topRight.y * (1 - alpha) + nq.topRight.y * alpha)
            val sBR = PointF(prev.bottomRight.x * (1 - alpha) + nq.bottomRight.x * alpha, prev.bottomRight.y * (1 - alpha) + nq.bottomRight.y * alpha)
            val sBL = PointF(prev.bottomLeft.x * (1 - alpha) + nq.bottomLeft.x * alpha, prev.bottomLeft.y * (1 - alpha) + nq.bottomLeft.y * alpha)

            smoothedQuad = DocumentQuad(sTL, sTR, sBR, sBL, isAutoDetected = true, confidence = newResult.confidence)

            if (maxDist <= moveThreshold) {
                consecutiveStableFrames++
            } else {
                consecutiveStableFrames = 1
            }
        }

        lastQuad = nq
        isStable = consecutiveStableFrames >= 6
        return smoothedQuad
    }

    fun getStableCount(): Int = consecutiveStableFrames

    fun reset() {
        smoothedQuad = null
        lastQuad = null
        consecutiveStableFrames = 0
        isStable = false
    }
}

object DocumentProcessor {

    /**
     * Corrects image orientation based on EXIF metadata.
     */
    fun fixOrientation(bitmap: Bitmap, inputStream: InputStream?): Bitmap {
        if (inputStream == null) return bitmap
        return try {
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    /**
     * Real-time document boundary detection on CameraX YUV_420_888 frame buffer.
     * Extracts luminance plane, downsamples, handles sensor rotation, calculates gradient,
     * fits quadrilateral boundaries, and validates convexity, area, and angles.
     */
    fun detectDocumentInYuv(
        yBuffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        rotationDegrees: Int
    ): RealtimeDetectionResult? {
        try {
            // Downsample to ~160px width for <4ms processing time per frame
            val step = max(1, width / 160)
            val sampleW = width / step
            val sampleH = height / step

            // Extract Y channel
            val lum = IntArray(sampleW * sampleH)
            yBuffer.rewind()
            for (y in 0 until sampleH) {
                val rowOffset = (y * step) * rowStride
                val targetRow = y * sampleW
                for (x in 0 until sampleW) {
                    val colOffset = (x * step) * pixelStride
                    val pos = rowOffset + colOffset
                    if (pos < yBuffer.limit()) {
                        lum[targetRow + x] = yBuffer.get(pos).toInt() and 0xFF
                    }
                }
            }

            // Apply sensor rotation (typically 90 degrees for portrait)
            val rotW: Int
            val rotH: Int
            val rotLum: IntArray
            val fullRotW: Int
            val fullRotH: Int

            when (rotationDegrees) {
                90 -> {
                    rotW = sampleH
                    rotH = sampleW
                    fullRotW = height
                    fullRotH = width
                    rotLum = IntArray(rotW * rotH)
                    for (y in 0 until sampleH) {
                        for (x in 0 until sampleW) {
                            val rx = sampleH - 1 - y
                            val ry = x
                            rotLum[ry * rotW + rx] = lum[y * sampleW + x]
                        }
                    }
                }
                270 -> {
                    rotW = sampleH
                    rotH = sampleW
                    fullRotW = height
                    fullRotH = width
                    rotLum = IntArray(rotW * rotH)
                    for (y in 0 until sampleH) {
                        for (x in 0 until sampleW) {
                            val rx = y
                            val ry = sampleW - 1 - x
                            rotLum[ry * rotW + rx] = lum[y * sampleW + x]
                        }
                    }
                }
                180 -> {
                    rotW = sampleW
                    rotH = sampleH
                    fullRotW = width
                    fullRotH = height
                    rotLum = IntArray(rotW * rotH)
                    for (y in 0 until sampleH) {
                        for (x in 0 until sampleW) {
                            val rx = sampleW - 1 - x
                            val ry = sampleH - 1 - y
                            rotLum[ry * rotW + rx] = lum[y * sampleW + x]
                        }
                    }
                }
                else -> {
                    rotW = sampleW
                    rotH = sampleH
                    fullRotW = width
                    fullRotH = height
                    rotLum = lum
                }
            }

            // Compute gradient magnitude (Sobel approximation)
            val grad = FloatArray(rotW * rotH)
            var maxGrad = 0f
            for (y in 1 until rotH - 1) {
                val rowIdx = y * rotW
                for (x in 1 until rotW - 1) {
                    val idx = rowIdx + x
                    val gx = (rotLum[idx + 1] - rotLum[idx - 1]).toFloat()
                    val gy = (rotLum[idx + rotW] - rotLum[idx - rotW]).toFloat()
                    val g = abs(gx) + abs(gy)
                    grad[idx] = g
                    if (g > maxGrad) maxGrad = g
                }
            }

            if (maxGrad < 30f) return null

            val threshold = max(24f, maxGrad * 0.28f)

            // Scan outward rays / corner quadrant detection
            val searchMarginX = (rotW * 0.38f).toInt()
            val searchMarginY = (rotH * 0.38f).toInt()

            var bestTL: PointF? = null
            var minTLDist = Float.MAX_VALUE
            for (y in 4..searchMarginY) {
                val rowIdx = y * rotW
                for (x in 4..searchMarginX) {
                    val idx = rowIdx + x
                    if (grad[idx] >= threshold) {
                        val dist = (x * x + y * y).toFloat()
                        if (dist < minTLDist) {
                            minTLDist = dist
                            bestTL = PointF(x.toFloat(), y.toFloat())
                        }
                    }
                }
            }

            var bestTR: PointF? = null
            var minTRDist = Float.MAX_VALUE
            for (y in 4..searchMarginY) {
                val rowIdx = y * rotW
                for (x in (rotW - searchMarginX) until (rotW - 4)) {
                    val idx = rowIdx + x
                    if (grad[idx] >= threshold) {
                        val dx = rotW - 1 - x
                        val dist = (dx * dx + y * y).toFloat()
                        if (dist < minTRDist) {
                            minTRDist = dist
                            bestTR = PointF(x.toFloat(), y.toFloat())
                        }
                    }
                }
            }

            var bestBR: PointF? = null
            var minBRDist = Float.MAX_VALUE
            for (y in (rotH - searchMarginY) until (rotH - 4)) {
                val rowIdx = y * rotW
                for (x in (rotW - searchMarginX) until (rotW - 4)) {
                    val idx = rowIdx + x
                    if (grad[idx] >= threshold) {
                        val dx = rotW - 1 - x
                        val dy = rotH - 1 - y
                        val dist = (dx * dx + dy * dy).toFloat()
                        if (dist < minBRDist) {
                            minBRDist = dist
                            bestBR = PointF(x.toFloat(), y.toFloat())
                        }
                    }
                }
            }

            var bestBL: PointF? = null
            var minBLDist = Float.MAX_VALUE
            for (y in (rotH - searchMarginY) until (rotH - 4)) {
                val rowIdx = y * rotW
                for (x in 4..searchMarginX) {
                    val idx = rowIdx + x
                    if (grad[idx] >= threshold) {
                        val dy = rotH - 1 - y
                        val dist = (x * x + dy * dy).toFloat()
                        if (dist < minBLDist) {
                            minBLDist = dist
                            bestBL = PointF(x.toFloat(), y.toFloat())
                        }
                    }
                }
            }

            if (bestTL != null && bestTR != null && bestBR != null && bestBL != null) {
                // Scale back to full rotated buffer coordinate space
                val sx = fullRotW / rotW.toFloat()
                val sy = fullRotH / rotH.toFloat()

                val pTL = PointF(bestTL.x * sx, bestTL.y * sy)
                val pTR = PointF(bestTR.x * sx, bestTR.y * sy)
                val pBR = PointF(bestBR.x * sx, bestBR.y * sy)
                val pBL = PointF(bestBL.x * sx, bestBL.y * sy)

                // Validate geometric quadrilateral properties
                if (validateQuadrilateral(pTL, pTR, pBR, pBL, fullRotW.toFloat(), fullRotH.toFloat())) {
                    val area = calculateQuadArea(pTL, pTR, pBR, pBL)
                    val totalArea = fullRotW.toFloat() * fullRotH.toFloat()
                    val areaRatio = area / totalArea

                    val confidence = (0.55f + areaRatio * 0.35f).coerceIn(0.60f, 0.95f)
                    val isReliable = confidence >= 0.70f

                    return RealtimeDetectionResult(
                        quad = DocumentQuad(pTL, pTR, pBR, pBL, isAutoDetected = true, confidence = confidence),
                        bufferWidth = fullRotW,
                        bufferHeight = fullRotH,
                        confidence = confidence,
                        isReliable = isReliable
                    )
                }
            }
        } catch (_: Exception) {
            // Graceful error handling
        }

        return null
    }

    /**
     * Automatic document boundary detection on high-resolution captured Bitmap.
     * Computes candidate corners, validates convexity, area, and aspect ratio.
     */
    /**
     * Automatic document boundary detection on high-resolution captured Bitmap.
     * Accurately detects notebook / document edges placed on beds, tables, or textured backgrounds,
     * cutting away the surrounding background.
     */
    fun detectDocumentCorners(bitmap: Bitmap): DocumentQuad {
        val origW = bitmap.width.toFloat()
        val origH = bitmap.height.toFloat()

        try {
            val result = com.example.scanner.engine.DocumentDetectionEngine().detect(bitmap)
            if (result.isSuccess && result.quad != null) {
                return result.quad
            }
        } catch (_: Exception) {}

        val defaultQuad = DocumentQuad(
            topLeft = PointF(origW * 0.05f, origH * 0.05f),
            topRight = PointF(origW * 0.95f, origH * 0.05f),
            bottomRight = PointF(origW * 0.95f, origH * 0.95f),
            bottomLeft = PointF(origW * 0.05f, origH * 0.95f),
            isAutoDetected = false,
            confidence = 0.25f
        )
        return defaultQuad
    }

    private fun unusedLegacyDetect(bitmap: Bitmap): DocumentQuad {
        val origW = bitmap.width.toFloat()
        val origH = bitmap.height.toFloat()

        val defaultQuad = DocumentQuad(
            topLeft = PointF(origW * 0.05f, origH * 0.05f),
            topRight = PointF(origW * 0.95f, origH * 0.05f),
            bottomRight = PointF(origW * 0.95f, origH * 0.95f),
            bottomLeft = PointF(origW * 0.05f, origH * 0.95f),
            isAutoDetected = false,
            confidence = 0.3f
        )

        try {
            // Downscale for fast, accurate edge analysis (max dimension 360)
            val scale = min(360f / bitmap.width, 360f / bitmap.height).coerceAtMost(1f)
            val scaledW = (bitmap.width * scale).toInt().coerceAtLeast(32)
            val scaledH = (bitmap.height * scale).toInt().coerceAtLeast(32)

            val scaledBmp = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
            val pixels = IntArray(scaledW * scaledH)
            scaledBmp.getPixels(pixels, 0, scaledW, 0, 0, scaledW, scaledH)

            val lum = FloatArray(scaledW * scaledH)
            val isPaper = BooleanArray(scaledW * scaledH)
            var totalLum = 0f
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val maxC = maxOf(r, maxOf(g, b))
                val minC = minOf(r, minOf(g, b))
                val sat = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
                val l = 0.299f * r + 0.587f * g + 0.114f * b
                lum[i] = l
                // White, cream, or light notebook paper has low saturation and moderate-to-high lightness
                isPaper[i] = (sat < 0.28f && l >= 75f)
                totalLum += l
            }
            val avgLum = totalLum / (scaledW * scaledH)

            // Compute edge gradient reinforced by paper-to-bedsheet boundary transitions
            val grad = FloatArray(scaledW * scaledH)
            var maxGrad = 0f
            for (y in 1 until scaledH - 1) {
                val rowIdx = y * scaledW
                for (x in 1 until scaledW - 1) {
                    val idx = rowIdx + x
                    val gx = (lum[idx + 1] - lum[idx - 1]) * 0.5f
                    val gy = (lum[idx + scaledW] - lum[idx - scaledW]) * 0.5f
                    var g = abs(gx) + abs(gy)

                    // If pixel lies on a boundary between paper and bedsheet/background, boost edge score
                    val paperNeighbors = (if (isPaper[idx - 1]) 1 else 0) +
                            (if (isPaper[idx + 1]) 1 else 0) +
                            (if (isPaper[idx - scaledW]) 1 else 0) +
                            (if (isPaper[idx + scaledW]) 1 else 0)
                    if (paperNeighbors in 1..3) {
                        g += 25f
                    }

                    grad[idx] = g
                    if (g > maxGrad) maxGrad = g
                }
            }

            val threshold = max(16f, maxGrad * 0.20f)
            // Allow searching up to 48% inwards to catch notebooks taking up middle of bed
            val searchMarginX = (scaledW * 0.48f).toInt()
            val searchMarginY = (scaledH * 0.48f).toInt()

            var bestTL: PointF? = null
            var minTLDist = Float.MAX_VALUE
            for (y in 3..searchMarginY) {
                val rowIdx = y * scaledW
                for (x in 3..searchMarginX) {
                    val idx = rowIdx + x
                    if (grad[idx] >= threshold) {
                        val dist = (x * x + y * y).toFloat()
                        if (dist < minTLDist) {
                            minTLDist = dist
                            bestTL = PointF(x.toFloat(), y.toFloat())
                        }
                    }
                }
            }

            var bestTR: PointF? = null
            var minTRDist = Float.MAX_VALUE
            for (y in 3..searchMarginY) {
                val rowIdx = y * scaledW
                for (x in (scaledW - searchMarginX) until (scaledW - 3)) {
                    val idx = rowIdx + x
                    if (grad[idx] >= threshold) {
                        val dx = scaledW - 1 - x
                        val dist = (dx * dx + y * y).toFloat()
                        if (dist < minTRDist) {
                            minTRDist = dist
                            bestTR = PointF(x.toFloat(), y.toFloat())
                        }
                    }
                }
            }

            var bestBR: PointF? = null
            var minBRDist = Float.MAX_VALUE
            for (y in (scaledH - searchMarginY) until (scaledH - 3)) {
                val rowIdx = y * scaledW
                for (x in (scaledW - searchMarginX) until (scaledW - 3)) {
                    val idx = rowIdx + x
                    if (grad[idx] >= threshold) {
                        val dx = scaledW - 1 - x
                        val dy = scaledH - 1 - y
                        val dist = (dx * dx + dy * dy).toFloat()
                        if (dist < minBRDist) {
                            minBRDist = dist
                            bestBR = PointF(x.toFloat(), y.toFloat())
                        }
                    }
                }
            }

            var bestBL: PointF? = null
            var minBLDist = Float.MAX_VALUE
            for (y in (scaledH - searchMarginY) until (scaledH - 3)) {
                val rowIdx = y * scaledW
                for (x in 3..searchMarginX) {
                    val idx = rowIdx + x
                    if (grad[idx] >= threshold) {
                        val dy = scaledH - 1 - y
                        val dist = (x * x + dy * dy).toFloat()
                        if (dist < minBLDist) {
                            minBLDist = dist
                            bestBL = PointF(x.toFloat(), y.toFloat())
                        }
                    }
                }
            }

            if (scaledBmp != bitmap) {
                scaledBmp.recycle()
            }

            val invScale = 1f / scale
            if (bestTL != null && bestTR != null && bestBR != null && bestBL != null) {
                val tl = PointF(bestTL.x * invScale, bestTL.y * invScale)
                val tr = PointF(bestTR.x * invScale, bestTR.y * invScale)
                val br = PointF(bestBR.x * invScale, bestBR.y * invScale)
                val bl = PointF(bestBL.x * invScale, bestBL.y * invScale)

                if (validateQuadrilateral(tl, tr, br, bl, origW, origH)) {
                    val area = calculateQuadArea(tl, tr, br, bl)
                    val areaRatio = area / (origW * origH)
                    val conf = (0.65f + areaRatio * 0.30f).coerceIn(0.70f, 0.98f)

                    return DocumentQuad(
                        topLeft = tl,
                        topRight = tr,
                        bottomRight = br,
                        bottomLeft = bl,
                        isAutoDetected = true,
                        confidence = conf
                    )
                }
            }

            // First: Attempt OpenCV geometric notebook/document detection
            try {
                val cvQuad = com.example.scanner.engine.OpenCvDocumentProcessor.detectNotebookCornersOrNull(bitmap)
                if (cvQuad != null) {
                    val pTL = PointF(cvQuad.topLeft.x.toFloat(), cvQuad.topLeft.y.toFloat())
                    val pTR = PointF(cvQuad.topRight.x.toFloat(), cvQuad.topRight.y.toFloat())
                    val pBR = PointF(cvQuad.bottomRight.x.toFloat(), cvQuad.bottomRight.y.toFloat())
                    val pBL = PointF(cvQuad.bottomLeft.x.toFloat(), cvQuad.bottomLeft.y.toFloat())
                    if (validateQuadrilateral(pTL, pTR, pBR, pBL, origW, origH)) {
                        return DocumentQuad(
                            topLeft = pTL,
                            topRight = pTR,
                            bottomRight = pBR,
                            bottomLeft = pBL,
                            isAutoDetected = true,
                            confidence = 0.88f
                        )
                    }
                }
            } catch (_: Exception) {
            }

            // Fallback: Honest non-detected quadrilateral for manual adjustment
            val insetX = origW * 0.05f
            val insetY = origH * 0.05f
            return DocumentQuad(
                topLeft = PointF(insetX, insetY),
                topRight = PointF(origW - insetX, insetY),
                bottomRight = PointF(origW - insetX, origH - insetY),
                bottomLeft = PointF(insetX, origH - insetY),
                isAutoDetected = false,
                confidence = 0.25f
            )
        } catch (_: Exception) {
            // Fall back cleanly
        }

        return defaultQuad
    }

    /**
     * Verifies that 4 points form a valid, non-self-intersecting, convex quadrilateral
     * with reasonable area and edge lengths.
     */
    private fun validateQuadrilateral(
        tl: PointF,
        tr: PointF,
        br: PointF,
        bl: PointF,
        frameWidth: Float,
        frameHeight: Float
    ): Boolean {
        // Points must be inside frame bounds (with small tolerance)
        val tolerance = 8f
        val pts = listOf(tl, tr, br, bl)
        for (p in pts) {
            if (p.x < -tolerance || p.x > frameWidth + tolerance ||
                p.y < -tolerance || p.y > frameHeight + tolerance
            ) {
                return false
            }
        }

        // Convexity check: all cross products along the polygon loop must have the same sign
        val cp1 = crossProduct(tl, tr, br)
        val cp2 = crossProduct(tr, br, bl)
        val cp3 = crossProduct(br, bl, tl)
        val cp4 = crossProduct(bl, tl, tr)

        val allPositive = cp1 > 0 && cp2 > 0 && cp3 > 0 && cp4 > 0
        val allNegative = cp1 < 0 && cp2 < 0 && cp3 < 0 && cp4 < 0

        if (!allPositive && !allNegative) {
            return false // Self-intersecting or concave polygon
        }

        // Area check: must occupy between 12% and 96% of frame area
        val area = calculateQuadArea(tl, tr, br, bl)
        val totalArea = frameWidth * frameHeight
        val areaRatio = area / totalArea
        if (areaRatio < 0.12f || areaRatio > 0.96f) {
            return false
        }

        // Edge lengths check: minimum length
        val topW = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
        val botW = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
        val leftH = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
        val rightH = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())

        if (topW < frameWidth * 0.22f || botW < frameWidth * 0.22f ||
            leftH < frameHeight * 0.22f || rightH < frameHeight * 0.22f
        ) {
            return false
        }

        return true
    }

    private fun crossProduct(a: PointF, b: PointF, c: PointF): Float {
        return (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
    }

    fun calculateQuadArea(tl: PointF, tr: PointF, br: PointF, bl: PointF): Float {
        return 0.5f * abs(
            (tl.x * tr.y - tr.x * tl.y) +
            (tr.x * br.y - br.x * tr.y) +
            (br.x * bl.y - bl.x * br.y) +
            (bl.x * tl.y - tl.x * bl.y)
        )
    }

    /**
     * Performs OpenCV perspective transformation & rectangular crop based on 4 corners.
     */
    fun cropPerspective(bitmap: Bitmap, quad: DocumentQuad): Bitmap {
        return com.example.scanner.engine.PerspectiveCorrectionEngine.transformPerspective(bitmap, quad)
    }

    /**
     * Applies document enhancement filters:
     * - ORIGINAL: Unaltered cropped page
     * - AUTO_ENHANCE: Balanced contrast, clear text, subtle background whitening
     * - MAGIC_COLOR: Saturated color inks + clean bright paper background
     * - GRAYSCALE: Clean 8-bit perceptual luminance gradient
     * - BLACK_WHITE: High-contrast binarization threshold for crisp text
     */
    fun applyFilter(bitmap: Bitmap, filter: DocumentFilter): Bitmap {
        return when (filter) {
            DocumentFilter.ORIGINAL -> {
                com.example.scanner.engine.DocumentEnhancementEngine.applyOriginal(bitmap)
            }
            DocumentFilter.GRAYSCALE -> {
                com.example.scanner.engine.DocumentEnhancementEngine.applyGrayscale(bitmap)
            }
            DocumentFilter.BLACK_WHITE -> {
                com.example.scanner.engine.DocumentEnhancementEngine.applyBlackAndWhite(bitmap)
            }
            DocumentFilter.DOCUMENT_CONTRAST -> {
                com.example.scanner.engine.DocumentEnhancementEngine.applyDocumentContrast(bitmap)
            }
            DocumentFilter.NOISE_REDUCTION -> {
                com.example.scanner.engine.DocumentEnhancementEngine.applyNoiseReduction(bitmap)
            }
            DocumentFilter.SHARPEN -> {
                com.example.scanner.engine.DocumentEnhancementEngine.applyControlledSharpening(bitmap)
            }
            DocumentFilter.MAGIC_COLOR -> {
                val satMatrix = ColorMatrix().apply { setSaturation(1.35f) }
                val contrastMatrix = ColorMatrix(floatArrayOf(
                    1.30f, 0f, 0f, 0f, -18f,
                    0f, 1.30f, 0f, 0f, -18f,
                    0f, 0f, 1.30f, 0f, -18f,
                    0f, 0f, 0f, 1f, 0f
                ))
                val combined = ColorMatrix().apply {
                    setConcat(contrastMatrix, satMatrix)
                }
                applyColorMatrix(bitmap, combined)
            }
            DocumentFilter.NO_SHADOW -> {
                com.example.scanner.engine.DocumentEnhancementEngine.applyShadowReduction(bitmap)
            }
            DocumentFilter.CAMSCANNER -> {
                com.example.scanner.engine.DocumentEnhancementEngine.applyCamScannerMagic(bitmap)
            }
        }
    }

    private fun applyColorMatrix(bitmap: Bitmap, cm: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    /**
     * Rotates bitmap by specified degrees (e.g. 90, 180, 270).
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Fast On-Device OCR text extraction engine powered by Google ML Kit.
     * Extracts actual text from the provided image.
     */
    suspend fun extractTextReal(bitmap: Bitmap): String = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        try {
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val recognized = visionText.text.trim()
                    val result = if (recognized.isNotBlank()) {
                        recognized
                    } else {
                        "No readable text was detected in this image.\n\nPlease ensure the document is clear, sharp, and well illuminated."
                    }
                    if (continuation.isActive) continuation.resume(result, null)
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) {
                        continuation.resume("Text extraction encountered an issue: ${e.localizedMessage ?: "Unknown error"}", null)
                    }
                }
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume("Failed to process OCR: ${e.localizedMessage ?: "Unknown error"}", null)
            }
        }
    }

    /**
     * Synchronous fallback or preview.
     */
    fun extractTextPreview(bitmap: Bitmap): String {
        return "Extracting text with on-device OCR engine..."
    }

    /**
     * Saves a PDF file to the device's public Downloads / UtilityHub folder using Scoped Storage.
     */
    fun savePdfToPublicDownloads(context: Context, sourceFile: File): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/UtilityHub")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    uri
                } else null
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "UtilityHub").apply { mkdirs() }
                val targetFile = File(downloadsDir, sourceFile.name)
                sourceFile.copyTo(targetFile, overwrite = true)
                Uri.fromFile(targetFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a bitmap as a JPEG image in app's internal or external storage.
     */
    fun saveBitmapToStorage(context: Context, bitmap: Bitmap, fileName: String): File? {
        return try {
            val outDir = File(context.filesDir, "scanned_images").apply { mkdirs() }
            val file = File(outDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    data class AutoScanResult(
        val rawOriginal: Bitmap,
        val croppedBitmap: Bitmap,
        val enhancedBitmap: Bitmap,
        val quad: DocumentQuad,
        val rotationAngle: Float,
        val isDetectionSuccess: Boolean = true
    )

    /**
     * Automatically detects if a document / page is rotated (e.g. 90 deg, 180 deg, 270 deg sideways)
     * or tilted, and rotates it upright ("সোজা করে দিবে").
     * Analyzes text lines with Google ML Kit.
     */
    suspend fun autoCorrectOrientationAndDeskew(bitmap: Bitmap): Pair<Bitmap, Float> = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        try {
            // Downscale for fast orientation check (<35ms)
            val maxDim = 800
            val scale = minOf(1.0f, maxDim.toFloat() / maxOf(bitmap.width, bitmap.height))
            val sampleBmp = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else bitmap

            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
            )
            val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(sampleBmp, 0)

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val lines = visionText.textBlocks.flatMap { it.lines }
                    var rotationNeeded = 0f

                    if (lines.isNotEmpty()) {
                        val angles = lines.map { it.angle }
                        val avgAngle = angles.average().toFloat()

                        var norm = avgAngle
                        while (norm > 180f) norm -= 360f
                        while (norm < -180f) norm += 360f

                        rotationNeeded = when {
                            norm in 65f..115f -> 270f   // Rotated 90 deg clockwise -> rotate 270 deg to make upright
                            norm in -115f..-65f -> 90f  // Rotated 90 deg counter-clockwise -> rotate 90 deg
                            norm > 155f || norm < -155f -> 180f // Inverted / upside down -> rotate 180 deg
                            abs(norm) in 1.5f..25f -> -norm     // Deskew slight tilt
                            else -> 0f
                        }
                    } else {
                        // Preserves landscape geometry (e.g. certificates, checks, widescreen notes)
                        rotationNeeded = 0f
                    }

                    if (sampleBmp != bitmap) {
                        sampleBmp.recycle()
                    }

                    if (abs(rotationNeeded) >= 1.0f) {
                        val rotated = rotateBitmap(bitmap, rotationNeeded)
                        continuation.resume(Pair(rotated, rotationNeeded), null)
                    } else {
                        continuation.resume(Pair(bitmap, 0f), null)
                    }
                }
                .addOnFailureListener {
                    if (sampleBmp != bitmap) {
                        sampleBmp.recycle()
                    }
                    continuation.resume(Pair(bitmap, 0f), null)
                }
        } catch (_: Exception) {
            continuation.resume(Pair(bitmap, 0f), null)
        }
    }

    /**
     * Complete automatic document processing pipeline:
     * 1. Auto-detect document boundaries using multi-strategy CV (crops away bed sheet / table background)
     * 2. OpenCV perspective warp & crop to flat rectangular document
     * 3. Auto-orientation & deskew (rotates sideways photo upright)
     * 4. Text enhancement for readability
     */
    suspend fun autoCropAndEnhance(
        context: Context,
        rawBitmap: Bitmap,
        preferredFilter: DocumentFilter = DocumentFilter.AUTO_ENHANCE,
        customQuad: DocumentQuad? = null
    ): AutoScanResult {
        if (customQuad != null) {
            val manualResult = com.example.scanner.engine.AutoCropEngine.performManualCrop(
                rawBitmap = rawBitmap,
                manualQuad = customQuad
            )
            val filtered = applyFilter(manualResult.croppedBitmap ?: rawBitmap, preferredFilter)
            return AutoScanResult(
                rawOriginal = rawBitmap,
                croppedBitmap = manualResult.croppedBitmap ?: rawBitmap,
                enhancedBitmap = filtered,
                quad = customQuad,
                rotationAngle = manualResult.rotationAngle
            )
        }

        val autoResult = com.example.scanner.engine.AutoCropEngine.cropDocument(
            context = context,
            rawBitmap = rawBitmap
        )

        if (autoResult.isSuccess && autoResult.croppedBitmap != null && autoResult.quad != null) {
            val (uprightCropped, rotationAngle) = Pair(autoResult.croppedBitmap, autoResult.rotationAngle)
            val enhanced = applyFilter(uprightCropped, preferredFilter)
            return AutoScanResult(
                rawOriginal = rawBitmap,
                croppedBitmap = uprightCropped,
                enhancedBitmap = enhanced,
                quad = autoResult.quad,
                rotationAngle = rotationAngle,
                isDetectionSuccess = true
            )
        } else {
            // Detection failed or low confidence: preserve rawOriginal, provide neutral manual crop guide
            val manualQuad = autoResult.quad ?: detectDocumentCorners(rawBitmap)
            val (uprightOriginal, rotationAngle) = autoCorrectOrientationAndDeskew(rawBitmap)
            val filtered = applyFilter(uprightOriginal, preferredFilter)
            return AutoScanResult(
                rawOriginal = rawBitmap,
                croppedBitmap = rawBitmap,
                enhancedBitmap = filtered,
                quad = manualQuad.copy(isAutoDetected = false, confidence = 0.25f),
                rotationAngle = rotationAngle,
                isDetectionSuccess = false
            )
        }
    }
}

