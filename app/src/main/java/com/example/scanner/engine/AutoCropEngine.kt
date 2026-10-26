package com.example.scanner.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.example.utils.DocumentQuad
import kotlin.math.max
import kotlin.math.min

/**
 * Status of the Auto-Crop operation.
 */
enum class AutoCropStatus {
    /** Confidently auto-detected, validated, and dewarped via OpenCV */
    SUCCESS,

    /** Document detected but confidence below threshold -> manual crop suggested */
    LOW_CONFIDENCE,

    /** No 4-corner document boundary found -> manual crop or retake */
    NO_DOCUMENT_FOUND,

    /** Found quadrilateral failed geometric validation (concave, distorted angles, or extreme aspect ratio) */
    INVALID_GEOMETRY,

    /** Blurry or unreadable capture */
    BLURRY_CAPTURE,

    /** Internal error */
    ERROR
}

/**
 * Result returned by [AutoCropEngine].
 * Note: [rawOriginal] is ALWAYS preserved untouched, enabling users to re-crop or adjust manually.
 */
data class AutoCropResult(
    val status: AutoCropStatus,
    val rawOriginal: Bitmap,
    val croppedBitmap: Bitmap?,
    val enhancedBitmap: Bitmap?,
    val quad: DocumentQuad?,
    val confidence: Float,
    val geometry: DocumentGeometry?,
    val rotationAngle: Float = 0f,
    val enhancementMode: EnhancementMode = EnhancementMode.DOCUMENT_COLOR,
    val failureReason: String? = null
) {
    val isSuccess: Boolean get() = status == AutoCropStatus.SUCCESS && croppedBitmap != null
    val needsManualCrop: Boolean get() = !isSuccess
}

/**
 * Backward compatibility scan result alias for existing callers.
 */
data class ProcessedScanResult(
    val rawOriginal: Bitmap,
    val croppedBitmap: Bitmap,
    val enhancedBitmap: Bitmap,
    val quad: DocumentQuad,
    val rotationAngle: Float,
    val enhancementMode: EnhancementMode
)

/**
 * Senior Computer Vision Auto Crop Engine.
 *
 * Implements a complete end-to-end auto-cropping pipeline:
 * 1. Coordinates Mapping: Normalizes and maps coordinates across CameraX analysis buffer and full-resolution captured Bitmap.
 * 2. Multi-Strategy Document Detection: Uses [DocumentDetectionEngine] (Prompt 2) to locate true document boundaries.
 * 3. Validation: Enforces convexity, aspect ratio, orthogonal angles, and real edge gradient contrast.
 * 4. False Crop Prevention: Rejects low-confidence detections instead of performing a fixed 5%/7% crop.
 * 5. Perspective Correction: Uses OpenCV getPerspectiveTransform() and warpPerspective() via [PerspectiveCorrectionEngine].
 * 6. Orientation & Deskew: Automatically detects sideways/inverted orientation and makes upright.
 * 7. Readability Enhancement: High-clarity ink enhancement via [DocumentEnhancementEngine].
 * 8. Original Image Preservation: Guarantees raw capture is preserved for manual adjustments or retakes.
 */
object AutoCropEngine {

    private const val TAG = "AutoCropEngine"

    // Unified detection engine created in Prompt 2
    private val detectionEngine = DocumentDetectionEngine()

    /**
     * Executes the complete automated scanning and perspective cropping pipeline on a captured [Bitmap].
     *
     * @param context Android context
     * @param rawBitmap The full-resolution captured image (with EXIF rotation applied)
     * @param previewQuad Optional quadrilateral detected in real-time CameraX preview
     * @param previewWidth Width of the real-time preview/analysis buffer
     * @param previewHeight Height of the real-time preview/analysis buffer
     * @param previewRotationDegrees Rotation of the preview stream relative to display
     * @param preferredMode Document enhancement mode (Magic Color, Grayscale, etc.)
     * @return Structured [AutoCropResult] containing status, original, cropped bitmap, and metadata.
     */
    suspend fun cropDocument(
        context: Context,
        rawBitmap: Bitmap,
        previewQuad: DocumentQuad? = null,
        previewWidth: Int = 0,
        previewHeight: Int = 0,
        previewRotationDegrees: Int = 0,
        preferredMode: EnhancementMode = EnhancementMode.DOCUMENT_COLOR
    ): AutoCropResult {
        if (rawBitmap.isRecycled || rawBitmap.width <= 16 || rawBitmap.height <= 16) {
            return AutoCropResult(
                status = AutoCropStatus.ERROR,
                rawOriginal = rawBitmap,
                croppedBitmap = null,
                enhancedBitmap = null,
                quad = null,
                confidence = 0f,
                geometry = null,
                failureReason = "Bitmap is null, empty, or recycled"
            )
        }

        val rawW = rawBitmap.width
        val rawH = rawBitmap.height

        // Step 1: Map real-time preview coordinates to full-resolution capture coordinate space if provided
        val mappedPreviewQuad = if (previewQuad != null && previewWidth > 0 && previewHeight > 0) {
            mapCoordinates(
                quad = previewQuad,
                srcWidth = previewWidth,
                srcHeight = previewHeight,
                dstWidth = rawW,
                dstHeight = rawH,
                rotationDegrees = previewRotationDegrees
            )
        } else null

        // Step 2: Run multi-strategy detection directly on the full-resolution captured image
        // (Uses universal Bilateral Canny, Adaptive Threshold, Color Isolation & Sobel Magnitude)
        val highResResult = detectionEngine.detect(rawBitmap)

        // Step 3: Arbitration & Selection of the Most Accurate Document Boundary
        val (candidateQuad, confidence, status, geometry) = arbitrateDetection(
            highResResult = highResResult,
            mappedPreviewQuad = mappedPreviewQuad,
            rawW = rawW,
            rawH = rawH
        )

        // Step 4: Strict Validation
        if (candidateQuad != null) {
            val validation = PerspectiveCorrectionEngine.validateQuad(candidateQuad, rawW, rawH)
            if (!validation.isValid) {
                Log.w(TAG, "Detection candidate failed geometric validation: ${validation.failureReason}")
                return AutoCropResult(
                    status = AutoCropStatus.INVALID_GEOMETRY,
                    rawOriginal = rawBitmap,
                    croppedBitmap = null,
                    enhancedBitmap = null,
                    quad = candidateQuad,
                    confidence = confidence,
                    geometry = geometry,
                    failureReason = validation.failureReason
                )
            }
        }

        // Step 5: Prevent False Crop - Do NOT perform fixed 5% or 7% crop when confidence is low!
        // Instead, require manual crop with the original image preserved.
        if (candidateQuad == null || confidence < DocumentDetectionEngine.CONFIDENCE_ACCEPT_THRESHOLD) {
            val initialGuideQuad = candidateQuad ?: getInitialGuideQuad(rawW, rawH)
            val fallbackStatus = when {
                highResResult.status == DetectionStatus.BLURRY_FRAME -> AutoCropStatus.BLURRY_CAPTURE
                candidateQuad == null -> AutoCropStatus.NO_DOCUMENT_FOUND
                else -> AutoCropStatus.LOW_CONFIDENCE
            }

            Log.d(TAG, "Auto-crop prevented due to low confidence ($confidence < ${DocumentDetectionEngine.CONFIDENCE_ACCEPT_THRESHOLD}) or missing quad. Showing manual crop.")
            return AutoCropResult(
                status = fallbackStatus,
                rawOriginal = rawBitmap,
                croppedBitmap = null,
                enhancedBitmap = null,
                quad = initialGuideQuad,
                confidence = confidence,
                geometry = geometry,
                failureReason = highResResult.failureReason ?: "Confidence below threshold ($confidence). Please adjust corners manually."
            )
        }

        // Step 6: OpenCV Perspective Rectification (Warp Perspective)
        // Transforms 4 corners to flat, front-facing document without squishing
        val rectified = PerspectiveCorrectionEngine.transformPerspective(rawBitmap, candidateQuad)

        // Step 7: Auto-orientation & deskew (ML Kit text-line analysis rotates sideways photos upright)
        val (upright, rotation) = ImageOrientationCorrector.autoCorrectOrientation(rectified)

        // Step 8: Document Enhancement (CamScanner Magic Color, Grayscale, or B&W)
        val enhanced = DocumentEnhancementEngine.enhance(upright, preferredMode)

        return AutoCropResult(
            status = AutoCropStatus.SUCCESS,
            rawOriginal = rawBitmap,
            croppedBitmap = upright,
            enhancedBitmap = enhanced,
            quad = candidateQuad,
            confidence = confidence,
            geometry = geometry,
            rotationAngle = rotation,
            enhancementMode = preferredMode
        )
    }

    /**
     * Executes perspective transformation for a user-confirmed or manually adjusted quadrilateral.
     */
    suspend fun performManualCrop(
        rawBitmap: Bitmap,
        manualQuad: DocumentQuad,
        preferredMode: EnhancementMode = EnhancementMode.DOCUMENT_COLOR
    ): AutoCropResult {
        val oq = PerspectiveCorrectionEngine.orderQuad(manualQuad)
        val rectified = PerspectiveCorrectionEngine.transformPerspective(rawBitmap, oq)
        val (upright, rotation) = ImageOrientationCorrector.autoCorrectOrientation(rectified)
        val enhanced = DocumentEnhancementEngine.enhance(upright, preferredMode)

        return AutoCropResult(
            status = AutoCropStatus.SUCCESS,
            rawOriginal = rawBitmap,
            croppedBitmap = upright,
            enhancedBitmap = enhanced,
            quad = oq,
            confidence = 1.0f,
            geometry = null,
            rotationAngle = rotation,
            enhancementMode = preferredMode
        )
    }

    /**
     * Backward-compatible pipeline call for existing code.
     */
    suspend fun processCapture(
        context: Context,
        rawBitmap: Bitmap,
        preferredMode: EnhancementMode = EnhancementMode.DOCUMENT_COLOR,
        manualQuad: DocumentQuad? = null
    ): ProcessedScanResult {
        if (manualQuad != null) {
            val result = performManualCrop(rawBitmap, manualQuad, preferredMode)
            return ProcessedScanResult(
                rawOriginal = rawBitmap,
                croppedBitmap = result.croppedBitmap ?: rawBitmap,
                enhancedBitmap = result.enhancedBitmap ?: rawBitmap,
                quad = manualQuad,
                rotationAngle = result.rotationAngle,
                enhancementMode = preferredMode
            )
        }

        val result = cropDocument(context, rawBitmap, preferredMode = preferredMode)
        if (result.isSuccess) {
            return ProcessedScanResult(
                rawOriginal = rawBitmap,
                croppedBitmap = result.croppedBitmap!!,
                enhancedBitmap = result.enhancedBitmap!!,
                quad = result.quad!!,
                rotationAngle = result.rotationAngle,
                enhancementMode = preferredMode
            )
        }

        // If auto-detection was uncertain, crop with verified guide quad rather than crashing
        val guideQuad = result.quad ?: getInitialGuideQuad(rawBitmap.width, rawBitmap.height)
        val manual = performManualCrop(rawBitmap, guideQuad, preferredMode)
        return ProcessedScanResult(
            rawOriginal = rawBitmap,
            croppedBitmap = manual.croppedBitmap ?: rawBitmap,
            enhancedBitmap = manual.enhancedBitmap ?: rawBitmap,
            quad = guideQuad,
            rotationAngle = manual.rotationAngle,
            enhancementMode = preferredMode
        )
    }

    /**
     * Accurately maps a quadrilateral from a source resolution (e.g. CameraX preview or analysis frame)
     * to a destination resolution (e.g. captured full-resolution Bitmap).
     *
     * Handles:
     * - Scaling differences along X and Y axes
     * - Aspect ratio differences (e.g., letterbox vs fill center)
     * - Sensor orientation rotations
     */
    fun mapCoordinates(
        quad: DocumentQuad,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        rotationDegrees: Int = 0
    ): DocumentQuad {
        if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) {
            return quad
        }

        // 1. Account for rotation if the src points are in un-rotated sensor coordinate space
        val rotatedQuad = when (rotationDegrees % 360) {
            90 -> DocumentQuad(
                topLeft = PointF(srcHeight - quad.topLeft.y, quad.topLeft.x),
                topRight = PointF(srcHeight - quad.topRight.y, quad.topRight.x),
                bottomRight = PointF(srcHeight - quad.bottomRight.y, quad.bottomRight.x),
                bottomLeft = PointF(srcHeight - quad.bottomLeft.y, quad.bottomLeft.x),
                isAutoDetected = quad.isAutoDetected,
                confidence = quad.confidence
            )
            180 -> DocumentQuad(
                topLeft = PointF(srcWidth - quad.topLeft.x, srcHeight - quad.topLeft.y),
                topRight = PointF(srcWidth - quad.topRight.x, srcHeight - quad.topRight.y),
                bottomRight = PointF(srcWidth - quad.bottomRight.x, srcHeight - quad.bottomRight.y),
                bottomLeft = PointF(srcWidth - quad.bottomLeft.x, srcHeight - quad.bottomLeft.y),
                isAutoDetected = quad.isAutoDetected,
                confidence = quad.confidence
            )
            270 -> DocumentQuad(
                topLeft = PointF(quad.topLeft.y, srcWidth - quad.topLeft.x),
                topRight = PointF(quad.topRight.y, srcWidth - quad.topRight.x),
                bottomRight = PointF(quad.bottomRight.y, srcWidth - quad.bottomRight.x),
                bottomLeft = PointF(quad.bottomLeft.y, srcWidth - quad.bottomLeft.x),
                isAutoDetected = quad.isAutoDetected,
                confidence = quad.confidence
            )
            else -> quad
        }

        val effSrcW = if (rotationDegrees == 90 || rotationDegrees == 270) srcHeight else srcWidth
        val effSrcH = if (rotationDegrees == 90 || rotationDegrees == 270) srcWidth else srcHeight

        val scaleX = dstWidth.toFloat() / effSrcW.toFloat()
        val scaleY = dstHeight.toFloat() / effSrcH.toFloat()

        val mapped = DocumentQuad(
            topLeft = PointF(
                (rotatedQuad.topLeft.x * scaleX).coerceIn(0f, dstWidth.toFloat()),
                (rotatedQuad.topLeft.y * scaleY).coerceIn(0f, dstHeight.toFloat())
            ),
            topRight = PointF(
                (rotatedQuad.topRight.x * scaleX).coerceIn(0f, dstWidth.toFloat()),
                (rotatedQuad.topRight.y * scaleY).coerceIn(0f, dstHeight.toFloat())
            ),
            bottomRight = PointF(
                (rotatedQuad.bottomRight.x * scaleX).coerceIn(0f, dstWidth.toFloat()),
                (rotatedQuad.bottomRight.y * scaleY).coerceIn(0f, dstHeight.toFloat())
            ),
            bottomLeft = PointF(
                (rotatedQuad.bottomLeft.x * scaleX).coerceIn(0f, dstWidth.toFloat()),
                (rotatedQuad.bottomLeft.y * scaleY).coerceIn(0f, dstHeight.toFloat())
            ),
            isAutoDetected = rotatedQuad.isAutoDetected,
            confidence = rotatedQuad.confidence
        )

        return PerspectiveCorrectionEngine.orderQuad(mapped)
    }

    /**
     * Generates a neutral initial guide quad for manual crop adjustment (not presented as auto-cropped).
     */
    fun getInitialGuideQuad(width: Int, height: Int): DocumentQuad {
        val w = width.toFloat()
        val h = height.toFloat()
        val marginX = w * 0.05f
        val marginY = h * 0.05f
        return DocumentQuad(
            topLeft = PointF(marginX, marginY),
            topRight = PointF(w - marginX, marginY),
            bottomRight = PointF(w - marginX, h - marginY),
            bottomLeft = PointF(marginX, h - marginY),
            isAutoDetected = false,
            confidence = 0.20f
        )
    }

    private data class ArbitrationResult(
        val quad: DocumentQuad?,
        val confidence: Float,
        val status: AutoCropStatus,
        val geometry: DocumentGeometry?
    )

    /**
     * Arbitrates between high-resolution direct capture detection and real-time temporal tracking.
     */
    private fun arbitrateDetection(
        highResResult: DetectionResult,
        mappedPreviewQuad: DocumentQuad?,
        rawW: Int,
        rawH: Int
    ): ArbitrationResult {
        // Case 1: High-res detection succeeded with strong confidence
        if (highResResult.isSuccess && highResResult.confidence >= DocumentDetectionEngine.CONFIDENCE_ACCEPT_THRESHOLD) {
            val ordered = PerspectiveCorrectionEngine.orderQuad(highResResult.quad!!)
            return ArbitrationResult(
                quad = ordered,
                confidence = highResResult.confidence,
                status = AutoCropStatus.SUCCESS,
                geometry = highResResult.geometry
            )
        }

        // Case 2: High-res was moderate (e.g. 0.45 - 0.52), and mapped preview quad agrees closely
        if (highResResult.quad != null && mappedPreviewQuad != null && mappedPreviewQuad.confidence >= 0.60f) {
            val agreement = computeQuadAgreement(highResResult.quad, mappedPreviewQuad, rawW, rawH)
            if (agreement > 0.80f) {
                // High agreement boosts confidence
                val boostedConf = min(1.0f, max(highResResult.confidence, mappedPreviewQuad.confidence) + 0.10f)
                if (boostedConf >= DocumentDetectionEngine.CONFIDENCE_ACCEPT_THRESHOLD) {
                    return ArbitrationResult(
                        quad = PerspectiveCorrectionEngine.orderQuad(highResResult.quad),
                        confidence = boostedConf,
                        status = AutoCropStatus.SUCCESS,
                        geometry = highResResult.geometry
                    )
                }
            }
        }

        // Case 3: High-res failed completely but mapped preview was very stable
        if (mappedPreviewQuad != null && mappedPreviewQuad.confidence >= 0.72f) {
            val validation = PerspectiveCorrectionEngine.validateQuad(mappedPreviewQuad, rawW, rawH)
            if (validation.isValid) {
                return ArbitrationResult(
                    quad = PerspectiveCorrectionEngine.orderQuad(mappedPreviewQuad),
                    confidence = mappedPreviewQuad.confidence,
                    status = AutoCropStatus.SUCCESS,
                    geometry = null
                )
            }
        }

        // Case 4: Low confidence or no document found
        val candidate = highResResult.quad ?: mappedPreviewQuad
        val conf = highResResult.confidence.coerceAtLeast(mappedPreviewQuad?.confidence ?: 0f)

        return ArbitrationResult(
            quad = candidate?.let { PerspectiveCorrectionEngine.orderQuad(it) },
            confidence = conf,
            status = if (highResResult.status == DetectionStatus.BLURRY_FRAME) AutoCropStatus.BLURRY_CAPTURE else AutoCropStatus.LOW_CONFIDENCE,
            geometry = highResResult.geometry
        )
    }

    private fun computeQuadAgreement(q1: DocumentQuad, q2: DocumentQuad, w: Int, h: Int): Float {
        val diag = kotlin.math.hypot(w.toFloat(), h.toFloat())
        if (diag <= 0) return 0f

        val dTL = kotlin.math.hypot(q1.topLeft.x - q2.topLeft.x, q1.topLeft.y - q2.topLeft.y) / diag
        val dTR = kotlin.math.hypot(q1.topRight.x - q2.topRight.x, q1.topRight.y - q2.topRight.y) / diag
        val dBR = kotlin.math.hypot(q1.bottomRight.x - q2.bottomRight.x, q1.bottomRight.y - q2.bottomRight.y) / diag
        val dBL = kotlin.math.hypot(q1.bottomLeft.x - q2.bottomLeft.x, q1.bottomLeft.y - q2.bottomLeft.y) / diag

        val avgDistRatio = (dTL + dTR + dBR + dBL) / 4f
        return (1.0f - avgDistRatio * 5f).coerceIn(0f, 1f)
    }
}
