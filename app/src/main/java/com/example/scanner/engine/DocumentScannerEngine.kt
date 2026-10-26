package com.example.scanner.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ScannedDocumentEntity
import com.example.utils.DocumentFilter
import com.example.utils.DocumentQuad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.*

/**
 * Senior Android Computer Vision Document Scanner Engine.
 *
 * Implements an independent, modular scanning and image processing pipeline:
 * - Real-time CameraX analysis with YUV frame processing
 * - Multi-strategy geometric document detection (HSV Paper Mask, Bilateral Canny, Adaptive Threshold)
 * - Candidate quadrilateral scoring (area, convexity, interior angles, edge alignment, aspect ratio)
 * - Temporal stability tracking and blur detection (Laplacian variance) for automatic capture
 * - Full-resolution coordinate mapping and perspective transformation (warpPerspective)
 * - Document image enhancement suite (Magic Color, Grayscale, B&W Binarization, Sharpening)
 * - Persistent storage management and Room database integration
 */
class DocumentScannerEngine(private val context: Context) {

    companion object {
        private const val TAG = "DocumentScannerEngine"

        // Processing resolution for real-time analysis
        private const val ANALYSIS_TARGET_WIDTH = 480.0
        private const val ANALYSIS_TARGET_HEIGHT = 640.0

        // Geometry & Scoring Constraints
        private const val MIN_AREA_RATIO = 0.12 // Minimum 12% of the frame
        private const val MAX_AREA_RATIO = 0.96 // Maximum 96% of the frame
        private const val MIN_CORNER_ANGLE_DEG = 55.0
        private const val MAX_CORNER_ANGLE_DEG = 125.0

        // Auto-capture criteria default constants
        const val DEFAULT_STABILITY_REQUIRED_FRAMES = 5
        const val DEFAULT_STABILITY_MAX_DRIFT_PX = 16.0
        const val DEFAULT_SHARPNESS_MIN_VARIANCE = 38.0
        const val DEFAULT_MIN_LUMINANCE = 30.0
        const val DEFAULT_MAX_LUMINANCE = 235.0
    }

    /**
     * Fully configurable thresholds for the Live Detection and Automatic Capture pipeline.
     */
    data class AutoCaptureConfig(
        var enabled: Boolean = true,
        var requiredStableFrames: Int = DEFAULT_STABILITY_REQUIRED_FRAMES,
        var maxDriftPixels: Double = DEFAULT_STABILITY_MAX_DRIFT_PX,
        var minConfidence: Float = 0.60f,
        var minSharpnessVariance: Double = DEFAULT_SHARPNESS_MIN_VARIANCE,
        var minLuminance: Double = DEFAULT_MIN_LUMINANCE,
        var maxLuminance: Double = DEFAULT_MAX_LUMINANCE,
        var cooldownMillis: Long = 2500L
    )

    // Modular Document Detection Engine
    val detectionEngine = DocumentDetectionEngine()

    // Configurable Auto-Capture threshold parameters
    var autoCaptureConfig = AutoCaptureConfig()

    // Engine Configuration (backward compatibility property)
    var autoCaptureEnabled: Boolean
        get() = autoCaptureConfig.enabled
        set(value) { autoCaptureConfig.enabled = value }

    var targetEnhancement: DocumentFilter = DocumentFilter.AUTO_ENHANCE

    // State management against duplicate/concurrent captures
    private val isCapturingInProgress = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastAutoCaptureTimestamp: Long = 0L

    // Temporal tracking state
    private var lastQuad: DocumentQuad? = null
    private var stableFrameCount: Int = 0
    private var isAutoCaptureTriggered: Boolean = false
    private var lastAnalysisTimestamp: Long = 0L

    // Callbacks for live UI integration
    interface DetectionListener {
        fun onDocumentDetected(quad: DocumentQuad, confidence: Float, isStable: Boolean)
        fun onStabilityProgress(progress: Float)
        fun onAutoCaptureReady()
        fun onDocumentLost()
        fun onFrameConditionFeedback(
            isTooDark: Boolean = false,
            isTooBright: Boolean = false,
            isBlurry: Boolean = false,
            isMoving: Boolean = false
        ) {}
    }

    var detectionListener: DetectionListener? = null

    /**
     * Marks that a capture operation has commenced.
     * Prevents duplicate auto-captures from firing while photo saving/dewarping is in progress.
     */
    fun notifyCaptureStarted() {
        isCapturingInProgress.set(true)
        lastAutoCaptureTimestamp = System.currentTimeMillis()
    }

    /**
     * Marks that a capture operation has completed.
     */
    fun notifyCaptureFinished() {
        isCapturingInProgress.set(false)
        lastAutoCaptureTimestamp = System.currentTimeMillis()
    }

    /**
     * Resets the cooldown timer for testing or immediate new capture.
     */
    fun resetCooldown() {
        lastAutoCaptureTimestamp = 0L
    }

    /**
     * Checks if auto-capture is allowed to fire based on active state and cooldown debounce.
     */
    fun canTriggerAutoCapture(): Boolean {
        val now = System.currentTimeMillis()
        return autoCaptureConfig.enabled &&
                !isCapturingInProgress.get() &&
                !isAutoCaptureTriggered &&
                (now - lastAutoCaptureTimestamp > autoCaptureConfig.cooldownMillis)
    }

    /**
     * Resets temporal tracking and auto-capture state
     */
    fun resetTracking() {
        lastQuad = null
        stableFrameCount = 0
        isAutoCaptureTriggered = false
        detectionListener?.onDocumentLost()
        detectionListener?.onStabilityProgress(0f)
    }

    // =========================================================================
    // 1. LIVE REAL-TIME FRAME ANALYSIS
    // =========================================================================

    /**
     * Analyzes a single camera frame from CameraX ImageAnalysis.
     * Extracts luminance, rotates to match display orientation, and runs multi-strategy detection.
     */
    fun processAnalysisFrame(
        yBuffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        rotationDegrees: Int
    ): DocumentQuad? {
        val currentTime = System.currentTimeMillis()
        // Throttle to ~15-20 FPS (every 50ms) to conserve CPU/battery and keep preview fluid
        if (currentTime - lastAnalysisTimestamp < 50) {
            return lastQuad
        }
        lastAnalysisTimestamp = currentTime

        var rawMat: Mat? = null
        var rotatedMat: Mat? = null
        var scaledMat: Mat? = null

        try {
            // Step 1: Wrap luminance buffer into OpenCV Mat handling rowStride and pixelStride
            rawMat = Mat(height, width, CvType.CV_8UC1)
            yBuffer.rewind()
            if (rowStride == width && pixelStride == 1) {
                val yBytes = ByteArray(width * height)
                yBuffer.get(yBytes, 0, width * height)
                rawMat.put(0, 0, yBytes)
            } else {
                val rowData = ByteArray(width)
                for (row in 0 until height) {
                    yBuffer.position(row * rowStride)
                    if (pixelStride == 1) {
                        yBuffer.get(rowData, 0, width)
                    } else {
                        for (col in 0 until width) {
                            rowData[col] = yBuffer.get(row * rowStride + col * pixelStride)
                        }
                    }
                    rawMat.put(row, 0, rowData)
                }
            }

            // Step 2: Handle Camera preview rotation (e.g. 90deg on portrait phones)
            rotatedMat = when (rotationDegrees) {
                90 -> {
                    val m = Mat()
                    Core.rotate(rawMat, m, Core.ROTATE_90_CLOCKWISE)
                    m
                }
                180 -> {
                    val m = Mat()
                    Core.rotate(rawMat, m, Core.ROTATE_180)
                    m
                }
                270 -> {
                    val m = Mat()
                    Core.rotate(rawMat, m, Core.ROTATE_90_COUNTERCLOCKWISE)
                    m
                }
                else -> rawMat
            }

            // Step 3: Scale down to normalized analysis resolution
            val rotW = rotatedMat.cols().toDouble()
            val rotH = rotatedMat.rows().toDouble()
            val scale = min(ANALYSIS_TARGET_WIDTH / rotW, ANALYSIS_TARGET_HEIGHT / rotH)
            val targetW = (rotW * scale).toInt().coerceAtLeast(100)
            val targetH = (rotH * scale).toInt().coerceAtLeast(100)

            scaledMat = Mat()
            Imgproc.resize(rotatedMat, scaledMat, Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)

            // Step 4: Multi-Strategy Document Detection
            val candidate = detectDocumentMultiStrategy(scaledMat)

            if (candidate != null) {
                // Scale corners back to rotatedMat dimensions
                val invScale = 1.0 / scale
                val scaledQuad = DocumentQuad(
                    topLeft = PointF((candidate.topLeft.x * invScale).toFloat(), (candidate.topLeft.y * invScale).toFloat()),
                    topRight = PointF((candidate.topRight.x * invScale).toFloat(), (candidate.topRight.y * invScale).toFloat()),
                    bottomRight = PointF((candidate.bottomRight.x * invScale).toFloat(), (candidate.bottomRight.y * invScale).toFloat()),
                    bottomLeft = PointF((candidate.bottomLeft.x * invScale).toFloat(), (candidate.bottomLeft.y * invScale).toFloat()),
                    isAutoDetected = true,
                    confidence = candidate.confidence
                )

                // Step 5: Temporal Stability Evaluation
                val smoothedQuad = updateTemporalTracking(scaledQuad, scaledMat)
                return smoothedQuad
            } else {
                handleDetectionLost()
                return null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Frame analysis exception: ${e.message}")
            return null
        } finally {
            rawMat?.release()
            if (rotatedMat !== rawMat) rotatedMat?.release()
            scaledMat?.release()
        }
    }

    // =========================================================================
    // 2. MULTI-STRATEGY DETECTION PIPELINE
    // =========================================================================

    /**
     * Executes multi-strategy document detection on an OpenCV Mat via [DocumentDetectionEngine].
     */
    fun detectDocumentMultiStrategy(mat: Mat): DocumentQuad? {
        val result = detectionEngine.detectInMat(mat)
        return if (result.isSuccess) result.quad else null
    }

    /**
     * Executes real-time document detection directly on a full-resolution or static Bitmap
     * via the independent [DocumentDetectionEngine].
     */
    fun detectDocument(bitmap: Bitmap): DocumentQuad? {
        val result = detectionEngine.detect(bitmap)
        return if (result.isSuccess) result.quad else null
    }

    /**
     * Executes complete document detection and returns full [DetectionResult] with geometry,
     * status, and confidence score.
     */
    fun detectDocumentDetailed(bitmap: Bitmap): DetectionResult {
        return detectionEngine.detect(bitmap)
    }

    /**
     * Accurately maps a DocumentQuad from one coordinate resolution (e.g. ImageAnalysis buffer)
     * to another resolution (e.g. captured full-resolution Bitmap or display preview).
     */
    fun mapQuadCoordinates(
        quad: DocumentQuad,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): DocumentQuad {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return quad
        val scaleX = dstW.toFloat() / srcW.toFloat()
        val scaleY = dstH.toFloat() / srcH.toFloat()
        return DocumentQuad(
            topLeft = PointF(quad.topLeft.x * scaleX, quad.topLeft.y * scaleY),
            topRight = PointF(quad.topRight.x * scaleX, quad.topRight.y * scaleY),
            bottomRight = PointF(quad.bottomRight.x * scaleX, quad.bottomRight.y * scaleY),
            bottomLeft = PointF(quad.bottomLeft.x * scaleX, quad.bottomLeft.y * scaleY),
            isAutoDetected = quad.isAutoDetected,
            confidence = quad.confidence
        )
    }

    /**
     * Strategy A: Luminance Saliency & Morphological Boundary Detection
     * Notebooks and white paper have significantly elevated luminance compared to backgrounds.
     */
    private fun detectViaLuminanceMorphology(grayMat: Mat): DocumentQuad? {
        val blurred = Mat()
        val thresh = Mat()
        val closed = Mat()
        val edges = Mat()

        try {
            // Gaussian noise reduction
            Imgproc.GaussianBlur(grayMat, blurred, Size(7.0, 7.0), 0.0)

            // Otsu automatic binarization
            Imgproc.threshold(blurred, thresh, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

            // Morphological closing: seals handwriting, ruled notebook lines, and small gaps into solid polygon
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
            Imgproc.morphologyEx(thresh, closed, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            // Canny edge detection
            Imgproc.Canny(closed, edges, 50.0, 150.0)

            return findBestQuadrilateral(edges, grayMat)
        } finally {
            blurred.release()
            thresh.release()
            closed.release()
            edges.release()
        }
    }

    /**
     * Strategy B: Bilateral Filter + Adaptive Canny Edge Detection
     * Bilateral filtering smooths textures (bedsheet patterns, wood grain) while preserving paper edges.
     */
    private fun detectViaBilateralCanny(grayMat: Mat): DocumentQuad? {
        val filtered = Mat()
        val edges = Mat()
        val dilated = Mat()

        try {
            // Bilateral filter preserves sharp edges
            Imgproc.bilateralFilter(grayMat, filtered, 9, 75.0, 75.0)

            // Canny edge detection
            Imgproc.Canny(filtered, edges, 35.0, 120.0)

            // Morphological dilation to bridge broken edges
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(edges, dilated, kernel)
            kernel.release()

            return findBestQuadrilateral(dilated, grayMat)
        } finally {
            filtered.release()
            edges.release()
            dilated.release()
        }
    }

    /**
     * Strategy C: Adaptive Gaussian Thresholding for shadow suppression
     */
    private fun detectViaAdaptiveThreshold(grayMat: Mat): DocumentQuad? {
        val adaptiveMat = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
        val closed = Mat()
        val edges = Mat()

        try {
            Imgproc.adaptiveThreshold(
                grayMat,
                adaptiveMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                21,
                8.0
            )

            Imgproc.morphologyEx(adaptiveMat, closed, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.Canny(closed, edges, 40.0, 140.0)

            return findBestQuadrilateral(edges, grayMat)
        } finally {
            adaptiveMat.release()
            kernel.release()
            closed.release()
            edges.release()
        }
    }

    /**
     * Evaluates contours from edge map and selects the highest scoring quadrilateral
     */
    private fun findBestQuadrilateral(edges: Mat, originalGray: Mat): DocumentQuad? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val totalArea = edges.cols().toDouble() * edges.rows().toDouble()
        var bestQuad: DocumentQuad? = null
        var highestScore = -1.0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            val areaRatio = area / totalArea

            // Discard contours outside plausible size bounds
            if (areaRatio < MIN_AREA_RATIO || areaRatio > MAX_AREA_RATIO) {
                contour.release()
                continue
            }

            val contour2f = MatOfPoint2f(*contour.toArray())
            val perimeter = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()

            // Polygon approximation with epsilon = 2.2% of perimeter
            Imgproc.approxPolyDP(contour2f, approx, 0.022 * perimeter, true)

            // Look for a convex 4-corner polygon
            if (approx.total() == 4L && Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) {
                val pts = approx.toArray()
                val ordered = orderPoints(pts)
                val score = evaluateCandidateGeometry(ordered, areaRatio, edges)

                if (score > highestScore) {
                    highestScore = score
                    bestQuad = DocumentQuad(
                        topLeft = PointF(ordered[0].x.toFloat(), ordered[0].y.toFloat()),
                        topRight = PointF(ordered[1].x.toFloat(), ordered[1].y.toFloat()),
                        bottomRight = PointF(ordered[2].x.toFloat(), ordered[2].y.toFloat()),
                        bottomLeft = PointF(ordered[3].x.toFloat(), ordered[3].y.toFloat()),
                        isAutoDetected = true,
                        confidence = score.toFloat().coerceIn(0f, 1f)
                    )
                }
            }

            contour2f.release()
            approx.release()
            contour.release()
        }

        hierarchy.release()
        return bestQuad
    }

    /**
     * Scores candidate quadrilateral based on geometric plausibility:
     * - Area coverage score
     * - Corner angle orthogonality (should be close to 90 degrees)
     * - Aspect ratio plausible for documents (0.5 to 2.2)
     */
    private fun evaluateCandidateGeometry(pts: Array<Point>, areaRatio: Double, edges: Mat): Double {
        // 1. Calculate side lengths
        val wTop = hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y)
        val wBottom = hypot(pts[2].x - pts[3].x, pts[2].y - pts[3].y)
        val hLeft = hypot(pts[3].x - pts[0].x, pts[3].y - pts[0].y)
        val hRight = hypot(pts[2].x - pts[1].x, pts[2].y - pts[1].y)

        val avgWidth = (wTop + wBottom) / 2.0
        val avgHeight = (hLeft + hRight) / 2.0
        if (avgWidth <= 0 || avgHeight <= 0) return 0.0

        val aspectRatio = avgWidth / avgHeight
        // Typical document aspect ratio ranges from 0.45 to 2.2
        if (aspectRatio < 0.45 || aspectRatio > 2.2) return 0.0

        // 2. Corner angles evaluation (must be near orthogonal 90 deg)
        val angles = DoubleArray(4)
        for (i in 0..3) {
            val pPrev = pts[(i + 3) % 4]
            val pCurr = pts[i]
            val pNext = pts[(i + 1) % 4]

            val v1x = pPrev.x - pCurr.x
            val v1y = pPrev.y - pCurr.y
            val v2x = pNext.x - pCurr.x
            val v2y = pNext.y - pCurr.y

            val dot = v1x * v2x + v1y * v2y
            val mag = hypot(v1x, v1y) * hypot(v2x, v2y)
            if (mag <= 0) return 0.0

            val cosTheta = (dot / mag).coerceIn(-1.0, 1.0)
            angles[i] = acos(cosTheta) * 180.0 / PI
            if (angles[i] < MIN_CORNER_ANGLE_DEG || angles[i] > MAX_CORNER_ANGLE_DEG) {
                return 0.0 // Non-rectangular distorted polygon
            }
        }

        // Orthogonality score (1.0 = perfect 90 deg at all corners)
        val angleDeviations = angles.map { abs(it - 90.0) }.average()
        val orthogonalityScore = (1.0 - (angleDeviations / 35.0)).coerceIn(0.0, 1.0)

        // Area balance score
        val areaScore = when {
            areaRatio in 0.20..0.85 -> 1.0
            areaRatio < 0.20 -> areaRatio / 0.20
            else -> (0.95 - areaRatio) / 0.10
        }.coerceIn(0.0, 1.0)

        // Combined score
        return (orthogonalityScore * 0.6) + (areaScore * 0.4)
    }

    /**
     * Orders 4 vertices consistently: [TopLeft, TopRight, BottomRight, BottomLeft]
     */
    fun orderPoints(pts: Array<Point>): Array<Point> {
        val sumSorted = pts.sortedBy { it.x + it.y }
        val tl = sumSorted.first()
        val br = sumSorted.last()

        val diffSorted = pts.sortedBy { it.y - it.x }
        val tr = diffSorted.first()
        val bl = diffSorted.last()

        return arrayOf(tl, tr, br, bl)
    }

    // =========================================================================
    // 3. TEMPORAL STABILITY & AUTOMATIC CAPTURE
    // =========================================================================

    /**
     * Tracks candidate quadrilateral across successive frames with Exponential Moving Average (EMA).
     * Calculates corner drift and image sharpness (Laplacian variance) for automatic capture.
     */
    private fun updateTemporalTracking(currentQuad: DocumentQuad, scaledMat: Mat): DocumentQuad {
        val previous = lastQuad

        if (previous == null) {
            lastQuad = currentQuad
            stableFrameCount = 1
            detectionListener?.onDocumentDetected(currentQuad, currentQuad.confidence, false)
            detectionListener?.onStabilityProgress(0.2f)
            return currentQuad
        }

        // 1. Calculate average Euclidean drift across all 4 corners
        val driftTL = hypot((currentQuad.topLeft.x - previous.topLeft.x).toDouble(), (currentQuad.topLeft.y - previous.topLeft.y).toDouble())
        val driftTR = hypot((currentQuad.topRight.x - previous.topRight.x).toDouble(), (currentQuad.topRight.y - previous.topRight.y).toDouble())
        val driftBR = hypot((currentQuad.bottomRight.x - previous.bottomRight.x).toDouble(), (currentQuad.bottomRight.y - previous.bottomRight.y).toDouble())
        val driftBL = hypot((currentQuad.bottomLeft.x - previous.bottomLeft.x).toDouble(), (currentQuad.bottomLeft.y - previous.bottomLeft.y).toDouble())
        val avgDrift = (driftTL + driftTR + driftBR + driftBL) / 4.0

        // 2. Camera motion check: calculate centroid shift of the quadrilateral
        val currCentroidX = (currentQuad.topLeft.x + currentQuad.topRight.x + currentQuad.bottomRight.x + currentQuad.bottomLeft.x) / 4.0
        val currCentroidY = (currentQuad.topLeft.y + currentQuad.topRight.y + currentQuad.bottomRight.y + currentQuad.bottomLeft.y) / 4.0
        val prevCentroidX = (previous.topLeft.x + previous.topRight.x + previous.bottomRight.x + previous.bottomLeft.x) / 4.0
        val prevCentroidY = (previous.topLeft.y + previous.topRight.y + previous.bottomRight.y + previous.bottomLeft.y) / 4.0
        val centroidMotion = hypot(currCentroidX - prevCentroidX, currCentroidY - prevCentroidY)

        val isCornerStable = avgDrift < autoCaptureConfig.maxDriftPixels
        val isLowMotion = centroidMotion < (autoCaptureConfig.maxDriftPixels * 0.85)
        val isConfSufficient = currentQuad.confidence >= autoCaptureConfig.minConfidence
        val isFrameStable = isCornerStable && isLowMotion && isConfSufficient

        // Exponential smoothing (alpha = 0.65 for responsiveness, 0.35 to smooth jitter)
        val alpha = 0.65f
        val smoothed = DocumentQuad(
            topLeft = PointF(previous.topLeft.x * (1 - alpha) + currentQuad.topLeft.x * alpha, previous.topLeft.y * (1 - alpha) + currentQuad.topLeft.y * alpha),
            topRight = PointF(previous.topRight.x * (1 - alpha) + currentQuad.topRight.x * alpha, previous.topRight.y * (1 - alpha) + currentQuad.topRight.y * alpha),
            bottomRight = PointF(previous.bottomRight.x * (1 - alpha) + currentQuad.bottomRight.x * alpha, previous.bottomRight.y * (1 - alpha) + currentQuad.bottomRight.y * alpha),
            bottomLeft = PointF(previous.bottomLeft.x * (1 - alpha) + currentQuad.bottomLeft.x * alpha, previous.bottomLeft.y * (1 - alpha) + currentQuad.bottomLeft.y * alpha),
            isAutoDetected = true,
            confidence = currentQuad.confidence
        )

        lastQuad = smoothed

        if (isFrameStable) {
            stableFrameCount++
        } else {
            stableFrameCount = max(0, stableFrameCount - 1)
        }

        val progress = (stableFrameCount.toFloat() / autoCaptureConfig.requiredStableFrames.toFloat()).coerceIn(0f, 1f)
        val isUiMarkedStable = isFrameStable && stableFrameCount >= 3
        detectionListener?.onDocumentDetected(smoothed, smoothed.confidence, isUiMarkedStable)
        detectionListener?.onStabilityProgress(progress)

        // 3. Exposure check: ensure frame is neither pitch dark nor completely blown out
        val meanLuminance = try {
            Core.mean(scaledMat).`val`[0]
        } catch (_: Exception) {
            100.0
        }
        val isTooDark = meanLuminance < autoCaptureConfig.minLuminance
        val isTooBright = meanLuminance > autoCaptureConfig.maxLuminance
        val isExposureValid = !isTooDark && !isTooBright

        // 4. Sharpness check: blur detection via Laplacian variance
        val sharpness = calculateLaplacianVariance(scaledMat)
        val isSharp = sharpness >= autoCaptureConfig.minSharpnessVariance

        // Provide diagnostic condition feedback for UI
        detectionListener?.onFrameConditionFeedback(
            isTooDark = isTooDark,
            isTooBright = isTooBright,
            isBlurry = !isSharp,
            isMoving = !isLowMotion
        )

        // 5. Automatic Capture Evaluation:
        // Requires ALL conditions met:
        // - Auto capture enabled & allowed (not capturing already, past cooldown)
        // - Valid boundary with sufficient confidence
        // - Stable corner positions across consecutive frames
        // - Low camera motion
        // - Acceptable sharpness
        // - Acceptable exposure
        if (canTriggerAutoCapture() &&
            stableFrameCount >= autoCaptureConfig.requiredStableFrames &&
            isExposureValid &&
            isSharp &&
            isConfSufficient
        ) {
            isAutoCaptureTriggered = true
            notifyCaptureStarted()
            detectionListener?.onAutoCaptureReady()
        }

        return smoothed
    }

    private fun handleDetectionLost() {
        stableFrameCount = max(0, stableFrameCount - 1)
        if (stableFrameCount == 0) {
            lastQuad = null
            isAutoCaptureTriggered = false
            detectionListener?.onDocumentLost()
            detectionListener?.onStabilityProgress(0f)
        }
    }

    /**
     * Calculates Laplacian variance to detect blur.
     * Sharp in-focus documents yield high variance (> 50.0). Blurry frames yield low variance.
     */
    fun calculateLaplacianVariance(grayMat: Mat): Double {
        val laplacian = Mat()
        try {
            Imgproc.Laplacian(grayMat, laplacian, CvType.CV_64F)
            val meanStd = MatOfDouble()
            val stdDev = MatOfDouble()
            Core.meanStdDev(laplacian, meanStd, stdDev)
            val std = stdDev.get(0, 0)?.get(0) ?: 0.0
            return std * std
        } catch (e: Exception) {
            return 100.0 // fallback
        } finally {
            laplacian.release()
        }
    }

    // =========================================================================
    // 4. PERSPECTIVE TRANSFORMATION & PIXEL-LEVEL CROPPING
    // =========================================================================

    /**
     * Performs true 4-point perspective dewarping on the full-resolution captured Bitmap.
     * Maps corners, calculates destination dimensions from document geometry,
     * applies OpenCV warpPerspective, and produces a straightened, flat pixel image.
     */
    fun cropAndWarpPerspective(
        bitmap: Bitmap,
        quad: DocumentQuad
    ): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)

        // Calculate destination dimensions
        val wTop = hypot((quad.topRight.x - quad.topLeft.x).toDouble(), (quad.topRight.y - quad.topLeft.y).toDouble())
        val wBottom = hypot((quad.bottomRight.x - quad.bottomLeft.x).toDouble(), (quad.bottomRight.y - quad.bottomLeft.y).toDouble())
        val targetWidth = max(wTop, wBottom).toInt().coerceAtLeast(100)

        val hLeft = hypot((quad.bottomLeft.x - quad.topLeft.x).toDouble(), (quad.bottomLeft.y - quad.topLeft.y).toDouble())
        val hRight = hypot((quad.bottomRight.x - quad.topRight.x).toDouble(), (quad.bottomRight.y - quad.topRight.y).toDouble())
        val targetHeight = max(hLeft, hRight).toInt().coerceAtLeast(100)

        // Source and destination points
        val srcPts = MatOfPoint2f(
            Point(quad.topLeft.x.toDouble(), quad.topLeft.y.toDouble()),
            Point(quad.topRight.x.toDouble(), quad.topRight.y.toDouble()),
            Point(quad.bottomRight.x.toDouble(), quad.bottomRight.y.toDouble()),
            Point(quad.bottomLeft.x.toDouble(), quad.bottomLeft.y.toDouble())
        )

        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((targetWidth - 1).toDouble(), 0.0),
            Point((targetWidth - 1).toDouble(), (targetHeight - 1).toDouble()),
            Point(0.0, (targetHeight - 1).toDouble())
        )

        val transformMatrix = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val warpedMat = Mat()

        Imgproc.warpPerspective(
            srcMat,
            warpedMat,
            transformMatrix,
            Size(targetWidth.toDouble(), targetHeight.toDouble()),
            Imgproc.INTER_CUBIC
        )

        val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(warpedMat, outputBitmap)

        srcMat.release()
        warpedMat.release()
        srcPts.release()
        dstPts.release()
        transformMatrix.release()

        return outputBitmap
    }

    // =========================================================================
    // 5. IMAGE ENHANCEMENT SUITE
    // =========================================================================

    /**
     * Applies document enhancement according to selected mode:
     * - ORIGINAL: untouched cropped bitmap
     * - AUTO_ENHANCE / MAGIC_COLOR: brightness & contrast stretch preserving colors
     * - GRAYSCALE: clean 8-bit luminance
     * - B&W: adaptive Gaussian thresholding (CamScanner-style dark text, white paper)
     * - SHARPEN: high-pass unsharp mask
     */
    fun enhanceDocument(bitmap: Bitmap, filter: DocumentFilter): Bitmap {
        return com.example.utils.DocumentProcessor.applyFilter(bitmap, filter)
    }

    // =========================================================================
    // 6. PERSISTENT STORAGE & ROOM DB INTEGRATION
    // =========================================================================

    data class ScanPersistenceResult(
        val scanId: Long,
        val fullImagePath: String,
        val thumbnailPath: String,
        val fileSizeBytes: Long,
        val rawImagePath: String? = null
    )

    /**
     * Persists the scanned document to app internal storage and inserts record into Room database.
     */
    suspend fun saveScanToDatabase(
        processedBitmap: Bitmap,
        title: String,
        filterUsed: String = "Magic Color",
        pdfFilePath: String? = null,
        rawBitmap: Bitmap? = null,
        pageCount: Int = 1
    ): ScanPersistenceResult = withContext(Dispatchers.IO) {
        val pdfFile = pdfFilePath?.let { File(it) }
        val saved = ScanStorageManager.saveScan(
            context = context,
            title = title,
            processedBitmap = processedBitmap,
            rawBitmap = rawBitmap,
            filterUsed = filterUsed,
            pageCount = pageCount,
            pdfFile = pdfFile
        )

        ScanPersistenceResult(
            scanId = saved.scanId,
            fullImagePath = saved.processedFile.absolutePath,
            thumbnailPath = saved.thumbnailFile.absolutePath,
            fileSizeBytes = saved.fileSizeBytes,
            rawImagePath = saved.rawFile?.absolutePath
        )
    }
}

