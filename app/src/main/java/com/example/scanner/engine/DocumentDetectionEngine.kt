package com.example.scanner.engine

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.example.utils.DocumentQuad
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.CLAHE
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import kotlin.math.*

/**
 * Detection status indicating the result of the document analysis pipeline.
 */
enum class DetectionStatus {
    SUCCESS,
    LOW_CONFIDENCE,
    NO_DOCUMENT_FOUND,
    BLURRY_FRAME,
    INVALID_GEOMETRY,
    OUT_OF_BOUNDS
}

/**
 * Geometric properties of a detected document boundary.
 */
data class DocumentGeometry(
    val width: Float,
    val height: Float,
    val aspectRatio: Float,
    val areaRatio: Float,
    val tiltDegrees: Float,
    val cornerAngles: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DocumentGeometry
        return width == other.width &&
                height == other.height &&
                aspectRatio == other.aspectRatio &&
                areaRatio == other.areaRatio &&
                tiltDegrees == other.tiltDegrees &&
                cornerAngles.contentEquals(other.cornerAngles)
    }

    override fun hashCode(): Int {
        var result = width.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + aspectRatio.hashCode()
        result = 31 * result + areaRatio.hashCode()
        result = 31 * result + tiltDegrees.hashCode()
        result = 31 * result + cornerAngles.contentHashCode()
        return result
    }
}

/**
 * Structured result returned by [DocumentDetectionEngine].
 */
data class DetectionResult(
    val status: DetectionStatus,
    val quad: DocumentQuad?,
    val confidence: Float,
    val geometry: DocumentGeometry?,
    val failureReason: String? = null
) {
    val isSuccess: Boolean get() = status == DetectionStatus.SUCCESS && quad != null
}

/**
 * Independent, Modular Document Detection Engine.
 *
 * Implements a complete computer vision pipeline without relying on external scanning apps
 * or Google Drive / ML Kit APIs:
 *
 * 1. Input Image / Frame Ingestion (Bitmap, OpenCV Mat, or CameraX YUV buffer)
 * 2. Sensor Orientation and Rotation Normalization
 * 3. Grayscale Luminance Conversion
 * 4. Texture-Preserving Noise Reduction (Bilateral / Gaussian Filtering)
 * 5. Contrast Limited Adaptive Histogram Equalization (CLAHE) for shadows, low light & uneven lighting
 * 6. Multi-Strategy Edge Detection (Universal Dynamic Canny, Adaptive Gaussian Threshold, Color Contrast)
 * 7. Morphological Edge Closing (connects broken lines, suppresses handwriting/ruled gaps)
 * 8. Contour Boundary Extraction (Imgproc.findContours)
 * 9. Multi-Epsilon Polygon Fitting (approxPolyDP) to locate candidate 4-corner polygons
 * 10. Strict Convexity and Area Coverage Verification
 * 11. Geometric Validity (Aspect ratio, internal corner angles, perspective trapezoid divergence)
 * 12. Consistent Corner Ordering: [Top-Left, Top-Right, Bottom-Right, Bottom-Left]
 * 13. Candidate Scoring based on edge gradient support, orthogonality, perspective coherence, and area
 */
class DocumentDetectionEngine {

    companion object {
        private const val TAG = "DocumentDetectionEngine"

        // Normalized dimension for speed & high-frequency texture noise suppression
        private const val NORMALIZED_TARGET_DIM = 640.0

        // Geometric boundary constraints
        private const val MIN_AREA_RATIO = 0.10 // Min 10% of total frame area
        private const val MAX_AREA_RATIO = 0.98 // Max 98% of total frame area
        private const val MIN_ASPECT_RATIO = 0.38f
        private const val MAX_ASPECT_RATIO = 2.60f
        private const val MIN_CORNER_ANGLE_DEG = 50.0
        private const val MAX_CORNER_ANGLE_DEG = 130.0
        private const val MAX_OPPOSITE_SIDE_DIVERGENCE = 0.48 // Perspective trapezoid check

        // Confidence threshold for auto-detection acceptance
        const val CONFIDENCE_ACCEPT_THRESHOLD = 0.52f
        const val BLUR_LAPLACIAN_MIN_VARIANCE = 22.0

        init {
            try {
                System.loadLibrary("opencv_java4")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "OpenCV native library load notice: ${e.message}")
            }
        }
    }

    /**
     * Executes the complete document detection pipeline on a [Bitmap].
     *
     * @param bitmap The source image to detect documents in.
     * @param rotationDegrees Optional camera orientation angle (0, 90, 180, 270).
     * @return A structured [DetectionResult] containing status, corners, confidence, and geometry.
     */
    fun detect(bitmap: Bitmap, rotationDegrees: Int = 0): DetectionResult {
        if (bitmap.isRecycled || bitmap.width <= 16 || bitmap.height <= 16) {
            return DetectionResult(
                status = DetectionStatus.NO_DOCUMENT_FOUND,
                quad = null,
                confidence = 0f,
                geometry = null,
                failureReason = "Bitmap is null, empty or too small (${bitmap.width}x${bitmap.height})"
            )
        }

        var srcMat: Mat? = null
        var rotatedMat: Mat? = null

        try {
            srcMat = Mat()
            Utils.bitmapToMat(bitmap, srcMat)

            // Step 2: Orientation & rotation correction
            rotatedMat = when (rotationDegrees % 360) {
                90 -> {
                    val m = Mat()
                    Core.rotate(srcMat, m, Core.ROTATE_90_CLOCKWISE)
                    m
                }
                180 -> {
                    val m = Mat()
                    Core.rotate(srcMat, m, Core.ROTATE_180)
                    m
                }
                270 -> {
                    val m = Mat()
                    Core.rotate(srcMat, m, Core.ROTATE_90_COUNTERCLOCKWISE)
                    m
                }
                else -> srcMat
            }

            return detectInMat(rotatedMat)
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "OpenCV native library not available in runtime: ${e.message}")
            return DetectionResult(
                status = DetectionStatus.NO_DOCUMENT_FOUND,
                quad = null,
                confidence = 0f,
                geometry = null,
                failureReason = "OpenCV native library unavailable: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Detection failure: ${e.message}", e)
            return DetectionResult(
                status = DetectionStatus.NO_DOCUMENT_FOUND,
                quad = null,
                confidence = 0f,
                geometry = null,
                failureReason = "Exception during detection: ${e.message}"
            )
        } finally {
            srcMat?.release()
            if (rotatedMat !== srcMat) rotatedMat?.release()
        }
    }

    /**
     * Executes the detection pipeline directly on an OpenCV [Mat] (BGR or Gray).
     */
    fun detectInMat(inputMat: Mat): DetectionResult {
        val origW = inputMat.cols().toDouble()
        val origH = inputMat.rows().toDouble()
        if (origW < 32 || origH < 32) {
            return DetectionResult(
                status = DetectionStatus.NO_DOCUMENT_FOUND,
                quad = null,
                confidence = 0f,
                geometry = null,
                failureReason = "Input frame too small (${origW.toInt()}x${origH.toInt()})"
            )
        }

        // Downscale to normalized dimension for speed and background pattern suppression
        val scale = min(NORMALIZED_TARGET_DIM / origW, NORMALIZED_TARGET_DIM / origH).coerceAtMost(1.0)
        val scaledW = (origW * scale).toInt().coerceAtLeast(100)
        val scaledH = (origH * scale).toInt().coerceAtLeast(100)

        val scaledMat = Mat()
        val grayMat = Mat()
        val claheMat = Mat()
        var clahe: CLAHE? = null

        try {
            Imgproc.resize(inputMat, scaledMat, Size(scaledW.toDouble(), scaledH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)

            // Step 3: Grayscale conversion
            if (scaledMat.channels() == 1) {
                scaledMat.copyTo(grayMat)
            } else if (scaledMat.channels() == 3) {
                Imgproc.cvtColor(scaledMat, grayMat, Imgproc.COLOR_BGR2GRAY)
            } else if (scaledMat.channels() == 4) {
                Imgproc.cvtColor(scaledMat, grayMat, Imgproc.COLOR_BGRA2GRAY)
            } else {
                return DetectionResult(
                    status = DetectionStatus.NO_DOCUMENT_FOUND,
                    quad = null,
                    confidence = 0f,
                    geometry = null,
                    failureReason = "Unsupported Mat channels: ${scaledMat.channels()}"
                )
            }

            // Blur evaluation using Laplacian variance
            val laplacianVariance = computeLaplacianVariance(grayMat)
            if (laplacianVariance < BLUR_LAPLACIAN_MIN_VARIANCE) {
                // If extremely blurry, flag blurry frame status but still attempt detection if possible
                Log.d(TAG, "Frame blurry: variance = $laplacianVariance")
            }

            // Step 5: Contrast normalization via CLAHE (handles uneven lighting, low light, light shadows)
            clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(grayMat, claheMat)

            // Step 6-13: Multi-Strategy Document Detection
            val candidate = executeMultiStrategyDetection(claheMat, scaledMat)

            if (candidate == null) {
                return DetectionResult(
                    status = if (laplacianVariance < BLUR_LAPLACIAN_MIN_VARIANCE) DetectionStatus.BLURRY_FRAME else DetectionStatus.NO_DOCUMENT_FOUND,
                    quad = null,
                    confidence = 0f,
                    geometry = null,
                    failureReason = "No plausible 4-corner document boundary found"
                )
            }

            // Scale detected candidate back to original unscaled coordinates
            val invScale = 1.0 / scale
            val unscaledPoints = candidate.orderedPoints.map {
                Point(it.x * invScale, it.y * invScale)
            }.toTypedArray()

            val unscaledQuad = DocumentQuad(
                topLeft = PointF(unscaledPoints[0].x.toFloat(), unscaledPoints[0].y.toFloat()),
                topRight = PointF(unscaledPoints[1].x.toFloat(), unscaledPoints[1].y.toFloat()),
                bottomRight = PointF(unscaledPoints[2].x.toFloat(), unscaledPoints[2].y.toFloat()),
                bottomLeft = PointF(unscaledPoints[3].x.toFloat(), unscaledPoints[3].y.toFloat()),
                isAutoDetected = candidate.confidence >= CONFIDENCE_ACCEPT_THRESHOLD,
                confidence = candidate.confidence
            )

            // Calculate unscaled geometry
            val geometry = computeGeometry(unscaledPoints, origW, origH)

            val status = when {
                candidate.confidence >= CONFIDENCE_ACCEPT_THRESHOLD -> DetectionStatus.SUCCESS
                laplacianVariance < BLUR_LAPLACIAN_MIN_VARIANCE -> DetectionStatus.BLURRY_FRAME
                else -> DetectionStatus.LOW_CONFIDENCE
            }

            return DetectionResult(
                status = status,
                quad = unscaledQuad,
                confidence = candidate.confidence,
                geometry = geometry,
                failureReason = if (status != DetectionStatus.SUCCESS) "Confidence below threshold (${candidate.confidence})" else null
            )

        } finally {
            scaledMat.release()
            grayMat.release()
            claheMat.release()
            clahe?.collectGarbage()
        }
    }

    /**
     * Executes the detection pipeline on a CameraX YUV_420_888 luminance plane buffer.
     */
    fun detectYuvBuffer(
        yBuffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        rotationDegrees: Int
    ): DetectionResult {
        var rawMat: Mat? = null
        var rotatedMat: Mat? = null
        try {
            rawMat = Mat(height, width, CvType.CV_8UC1)
            val yBytes = ByteArray(yBuffer.remaining())
            yBuffer.get(yBytes)
            rawMat.put(0, 0, yBytes)

            rotatedMat = when (rotationDegrees % 360) {
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

            return detectInMat(rotatedMat)
        } catch (e: Exception) {
            Log.e(TAG, "YUV detection error: ${e.message}")
            return DetectionResult(
                status = DetectionStatus.NO_DOCUMENT_FOUND,
                quad = null,
                confidence = 0f,
                geometry = null,
                failureReason = e.message
            )
        } finally {
            rawMat?.release()
            if (rotatedMat !== rawMat) rotatedMat?.release()
        }
    }

    // =========================================================================
    // MULTI-STRATEGY PIPELINE IMPLEMENTATION
    // =========================================================================

    private data class CandidateQuad(
        val orderedPoints: Array<Point>,
        val confidence: Float,
        val areaRatio: Double,
        val strategy: String
    )

    /**
     * Evaluates multiple computer vision strategies in parallel:
     * - Strategy 1: Universal Bilateral Filter + Otsu Dynamic Canny (Colored notebooks, books, white paper on dark/wood tables)
     * - Strategy 2: Adaptive Gaussian Thresholding + Morphological Close (Light shadows, low light, uneven illumination)
     * - Strategy 3: Multi-Band Color / Saturation Edge Isolation (Suppresses patterned bedsheets, floral fabrics, tablecloths)
     * - Strategy 4: Gradient Magnitude Edge Extraction (Low contrast paper matching background)
     *
     * Candidates are evaluated, scored, and the best candidate with real edge gradient support is returned.
     */
    private fun executeMultiStrategyDetection(claheMat: Mat, colorMat: Mat): CandidateQuad? {
        val candidates = mutableListOf<CandidateQuad>()

        // Compute reference gradient magnitude map for verifying edge support across candidates
        val referenceGradMap = computeGradientMap(claheMat)

        try {
            // Strategy 1: Universal Bilateral Filter + Otsu Dynamic Canny
            val cand1 = detectStrategyUniversalBilateralCanny(claheMat, referenceGradMap)
            if (cand1 != null) {
                candidates.add(cand1)
                // If high confidence, we have a clear, reliable document
                if (cand1.confidence >= 0.72f) return cand1
            }

            // Strategy 2: Adaptive Gaussian Thresholding
            val cand2 = detectStrategyAdaptiveThreshold(claheMat, referenceGradMap)
            if (cand2 != null) {
                candidates.add(cand2)
                if (cand2.confidence >= 0.70f) return cand2
            }

            // Strategy 3: Multi-band Color & Saturation Isolation (when color information is present)
            if (colorMat.channels() >= 3) {
                val cand3 = detectStrategyColorIsolation(colorMat, referenceGradMap)
                if (cand3 != null) {
                    candidates.add(cand3)
                    if (cand3.confidence >= 0.70f) return cand3
                }
            }

            // Strategy 4: Sobel Gradient Magnitude Thresholding
            val cand4 = detectStrategySobelMagnitude(referenceGradMap)
            if (cand4 != null) {
                candidates.add(cand4)
            }

            // Select candidate with highest confidence score
            return candidates.maxByOrNull { it.confidence }
        } finally {
            referenceGradMap.release()
        }
    }

    /**
     * Strategy 1: Bilateral Filter + Dynamic Otsu Canny
     * Works for ANY colored notebook (blue, yellow, red, dark, white) on wood, desk, or floor.
     * Preserves sharp document edges while smoothing high-frequency noise and textures.
     */
    private fun detectStrategyUniversalBilateralCanny(claheMat: Mat, gradMap: Mat): CandidateQuad? {
        val filtered = Mat()
        val edges = Mat()
        val closed = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))

        try {
            // Bilateral filter preserves edge discontinuity
            Imgproc.bilateralFilter(claheMat, filtered, 7, 60.0, 60.0)

            // Dynamic Canny thresholds using Otsu binarization
            val dummy = Mat()
            val otsuThreshold = Imgproc.threshold(filtered, dummy, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            dummy.release()

            val high = otsuThreshold.coerceIn(50.0, 160.0)
            val low = (high * 0.5).coerceAtLeast(25.0)

            Imgproc.Canny(filtered, edges, low, high)

            // Morphological closing connects broken lines caused by text, ruled margins, or reflection
            Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)

            return evaluateContoursForQuads(closed, gradMap, "BilateralCanny")
        } finally {
            filtered.release()
            edges.release()
            closed.release()
            kernel.release()
        }
    }

    /**
     * Strategy 2: Adaptive Gaussian Thresholding
     * Robust against heavy shadows (e.g. phone shadow, room overhead light) and uneven lighting.
     */
    private fun detectStrategyAdaptiveThreshold(claheMat: Mat, gradMap: Mat): CandidateQuad? {
        val adaptiveMat = Mat()
        val closed = Mat()
        val edges = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))

        try {
            Imgproc.adaptiveThreshold(
                claheMat,
                adaptiveMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                25,
                8.0
            )

            Imgproc.morphologyEx(adaptiveMat, closed, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.Canny(closed, edges, 40.0, 130.0)

            return evaluateContoursForQuads(edges, gradMap, "AdaptiveThreshold")
        } finally {
            adaptiveMat.release()
            closed.release()
            edges.release()
            kernel.release()
        }
    }

    /**
     * Strategy 3: Multi-Band Color Contrast Isolation
     * Separates paper/notebook from colored, patterned bedsheets or tablecloths.
     * Evaluates contrast between saturation and value channels rather than assuming white only.
     */
    private fun detectStrategyColorIsolation(colorMat: Mat, gradMap: Mat): CandidateQuad? {
        val hsv = Mat()
        val vChannel = Mat()
        val sChannel = Mat()
        val contrastMap = Mat()
        val edges = Mat()
        val channels = ArrayList<Mat>()

        try {
            Imgproc.cvtColor(colorMat, hsv, Imgproc.COLOR_BGR2HSV)
            Core.split(hsv, channels)
            channels[1].copyTo(sChannel) // Saturation
            channels[2].copyTo(vChannel) // Value/Brightness

            // Compute edge contrast by taking absolute difference of gradient between V and S
            // White/colored notebooks produce clean steps across both brightness and saturation boundaries
            val gradV = Mat()
            val gradS = Mat()
            Imgproc.Sobel(vChannel, gradV, CvType.CV_8U, 1, 1)
            Imgproc.Sobel(sChannel, gradS, CvType.CV_8U, 1, 1)

            Core.addWeighted(gradV, 0.6, gradS, 0.4, 0.0, contrastMap)
            gradV.release()
            gradS.release()

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.morphologyEx(contrastMap, contrastMap, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            Imgproc.Canny(contrastMap, edges, 35.0, 110.0)

            return evaluateContoursForQuads(edges, gradMap, "ColorIsolation")
        } finally {
            hsv.release()
            vChannel.release()
            sChannel.release()
            contrastMap.release()
            edges.release()
            for (c in channels) c.release()
        }
    }

    /**
     * Strategy 4: Direct Sobel Gradient Magnitude
     * Catches soft paper boundaries where background color matches paper closely.
     */
    private fun detectStrategySobelMagnitude(gradMap: Mat): CandidateQuad? {
        val threshEdges = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        try {
            // Threshold top 28% gradient magnitude
            Imgproc.threshold(gradMap, threshEdges, 35.0, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.morphologyEx(threshEdges, threshEdges, Imgproc.MORPH_CLOSE, kernel)

            return evaluateContoursForQuads(threshEdges, gradMap, "SobelMagnitude")
        } finally {
            threshEdges.release()
            kernel.release()
        }
    }

    // =========================================================================
    // CONTOUR EXTRACTION, POLYGON APPROXIMATION & SCORING
    // =========================================================================

    /**
     * Extracts contours, performs multi-epsilon polygon approximation (approxPolyDP),
     * enforces convexity and area, and scores candidate quadrilaterals.
     *
     * DO NOT blindly choose the largest contour. Every candidate is validated geometrically.
     */
    private fun evaluateContoursForQuads(edges: Mat, gradMap: Mat, strategyName: String): CandidateQuad? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val totalArea = edges.cols().toDouble() * edges.rows().toDouble()
        var bestCandidate: CandidateQuad? = null
        var highestScore = -1.0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            val areaRatio = area / totalArea

            // Discard contours outside reasonable document area bounds (10% to 98% of frame)
            if (areaRatio < MIN_AREA_RATIO || areaRatio > MAX_AREA_RATIO) {
                contour.release()
                continue
            }

            val contour2f = MatOfPoint2f(*contour.toArray())
            val perimeter = Imgproc.arcLength(contour2f, true)

            // Try multi-epsilon polygon approximations to handle rounded corners, slight folds, and perspective
            val epsilons = doubleArrayOf(0.020, 0.028, 0.015, 0.038)
            var foundQuadPoints: Array<Point>? = null

            for (eps in epsilons) {
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(contour2f, approx, eps * perimeter, true)

                if (approx.total() == 4L) {
                    val mop = MatOfPoint(*approx.toArray())
                    val isConvex = Imgproc.isContourConvex(mop)
                    mop.release()

                    if (isConvex) {
                        foundQuadPoints = approx.toArray()
                        approx.release()
                        break
                    }
                }
                approx.release()
            }

            if (foundQuadPoints != null && foundQuadPoints.size == 4) {
                val ordered = orderPoints(foundQuadPoints)
                val score = evaluateCandidateGeometryAndEdgeSupport(ordered, areaRatio, gradMap)

                if (score > highestScore) {
                    highestScore = score
                    bestCandidate = CandidateQuad(
                        orderedPoints = ordered,
                        confidence = score.toFloat().coerceIn(0f, 1f),
                        areaRatio = areaRatio,
                        strategy = strategyName
                    )
                }
            }

            contour2f.release()
            contour.release()
        }

        hierarchy.release()
        return bestCandidate
    }

    /**
     * Scores candidate quadrilateral using:
     * 1. Corner angle orthogonality (should be close to 90 degrees)
     * 2. Perspective trapezoid plausibility (opposite sides must not diverge wildly)
     * 3. Aspect ratio check (0.38 to 2.6)
     * 4. REAL EDGE GRADIENT SUPPORT: Samples points along the 4 line segments of the candidate
     *    in the gradient map to confirm that the candidate represents a true visual boundary!
     */
    private fun evaluateCandidateGeometryAndEdgeSupport(
        pts: Array<Point>,
        areaRatio: Double,
        gradMap: Mat
    ): Double {
        val pTL = pts[0]
        val pTR = pts[1]
        val pBR = pts[2]
        val pBL = pts[3]

        // 1. Calculate side lengths
        val wTop = hypot(pTR.x - pTL.x, pTR.y - pTL.y)
        val wBottom = hypot(pBR.x - pBL.x, pBR.y - pBL.y)
        val hLeft = hypot(pBL.x - pTL.x, pBL.y - pTL.y)
        val hRight = hypot(pBR.x - pTR.x, pBR.y - pTR.y)

        val avgWidth = (wTop + wBottom) / 2.0
        val avgHeight = (hLeft + hRight) / 2.0
        if (avgWidth <= 0 || avgHeight <= 0) return 0.0

        val aspectRatio = avgWidth / avgHeight
        if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) {
            return 0.0 // Non-document aspect ratio
        }

        // Perspective trapezoid divergence check: opposite sides shouldn't diverge excessively
        val maxW = max(wTop, wBottom)
        val minW = min(wTop, wBottom)
        val maxH = max(hLeft, hRight)
        val minH = min(hLeft, hRight)

        val wDivergence = if (maxW > 0) (maxW - minW) / maxW else 1.0
        val hDivergence = if (maxH > 0) (maxH - minH) / maxH else 1.0
        if (wDivergence > MAX_OPPOSITE_SIDE_DIVERGENCE || hDivergence > MAX_OPPOSITE_SIDE_DIVERGENCE) {
            return 0.0
        }

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
                return 0.0 // Severely skewed or non-rectangular polygon
            }
        }

        val angleDeviations = angles.map { abs(it - 90.0) }.average()
        val orthogonalityScore = (1.0 - (angleDeviations / 38.0)).coerceIn(0.0, 1.0)

        // 3. Area coverage score
        val areaScore = when {
            areaRatio in 0.20..0.85 -> 1.0
            areaRatio < 0.20 -> areaRatio / 0.20
            else -> (0.98 - areaRatio) / 0.13
        }.coerceIn(0.0, 1.0)

        // 4. Real edge gradient support along the 4 line segments
        val edgeSupportScore = calculateEdgeSupport(pts, gradMap)

        // Combined composite confidence score
        // Candidates with high edge gradient support and good orthogonality score highest!
        return (edgeSupportScore * 0.40) + (orthogonalityScore * 0.35) + (areaScore * 0.15) + (1.0 - max(wDivergence, hDivergence)) * 0.10
    }

    /**
     * Samples pixels along the 4 edges of the candidate quadrilateral to confirm
     * the presence of real visual contrast/gradients.
     */
    private fun calculateEdgeSupport(pts: Array<Point>, gradMap: Mat): Double {
        var supportedSamples = 0
        var totalSamples = 0
        val samplesPerEdge = 12

        val w = gradMap.cols()
        val h = gradMap.rows()

        for (i in 0..3) {
            val p1 = pts[i]
            val p2 = pts[(i + 1) % 4]

            for (s in 1..samplesPerEdge) {
                val t = s.toDouble() / (samplesPerEdge + 1)
                val x = (p1.x + (p2.x - p1.x) * t).toInt().coerceIn(0, w - 1)
                val y = (p1.y + (p2.y - p1.y) * t).toInt().coerceIn(0, h - 1)

                val gradVal = gradMap.get(y, x)?.firstOrNull() ?: 0.0
                if (gradVal > 28.0) {
                    supportedSamples++
                }
                totalSamples++
            }
        }

        return if (totalSamples > 0) supportedSamples.toDouble() / totalSamples.toDouble() else 0.0
    }

    /**
     * Computes a normalized gradient magnitude map (Sobel operator).
     */
    private fun computeGradientMap(grayMat: Mat): Mat {
        val gradX = Mat()
        val gradY = Mat()
        val absGradX = Mat()
        val absGradY = Mat()
        val grad = Mat()

        Imgproc.Sobel(grayMat, gradX, CvType.CV_16S, 1, 0)
        Imgproc.Sobel(grayMat, gradY, CvType.CV_16S, 0, 1)

        Core.convertScaleAbs(gradX, absGradX)
        Core.convertScaleAbs(gradY, absGradY)

        Core.addWeighted(absGradX, 0.5, absGradY, 0.5, 0.0, grad)

        gradX.release()
        gradY.release()
        absGradX.release()
        absGradY.release()

        return grad
    }

    /**
     * Computes Laplacian variance for blur detection.
     */
    fun computeLaplacianVariance(grayMat: Mat): Double {
        val laplacian = Mat()
        try {
            Imgproc.Laplacian(grayMat, laplacian, CvType.CV_64F)
            val mean = MatOfDouble()
            val stddev = MatOfDouble()
            Core.meanStdDev(laplacian, mean, stddev)
            val variance = stddev.toArray()[0].pow(2.0)
            mean.release()
            stddev.release()
            return variance
        } catch (_: Exception) {
            return 50.0
        } finally {
            laplacian.release()
        }
    }

    /**
     * Orders 4 vertices consistently: [TopLeft, TopRight, BottomRight, BottomLeft].
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

    /**
     * Computes geometric metadata for the detected quadrilateral.
     */
    private fun computeGeometry(pts: Array<Point>, totalW: Double, totalH: Double): DocumentGeometry {
        val pTL = pts[0]
        val pTR = pts[1]
        val pBR = pts[2]
        val pBL = pts[3]

        val wTop = hypot(pTR.x - pTL.x, pTR.y - pTL.y)
        val wBottom = hypot(pBR.x - pBL.x, pBR.y - pBL.y)
        val hLeft = hypot(pBL.x - pTL.x, pBL.y - pTL.y)
        val hRight = hypot(pBR.x - pTR.x, pBR.y - pTR.y)

        val avgW = (wTop + wBottom) / 2.0
        val avgH = (hLeft + hRight) / 2.0
        val aspect = if (avgH > 0) (avgW / avgH).toFloat() else 1.0f

        val area = calculatePolygonArea(pts)
        val totalArea = totalW * totalH
        val areaRatio = if (totalArea > 0) (area / totalArea).toFloat() else 0f

        // Tilt angle based on top edge slope
        val dx = pTR.x - pTL.x
        val dy = pTR.y - pTL.y
        val tilt = (atan2(dy, dx) * 180.0 / PI).toFloat()

        // 4 interior angles
        val angles = FloatArray(4)
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
            if (mag > 0) {
                val cosTheta = (dot / mag).coerceIn(-1.0, 1.0)
                angles[i] = (acos(cosTheta) * 180.0 / PI).toFloat()
            } else {
                angles[i] = 90f
            }
        }

        return DocumentGeometry(
            width = avgW.toFloat(),
            height = avgH.toFloat(),
            aspectRatio = aspect,
            areaRatio = areaRatio,
            tiltDegrees = tilt,
            cornerAngles = angles
        )
    }

    private fun calculatePolygonArea(pts: Array<Point>): Double {
        var area = 0.0
        val n = pts.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += pts[i].x * pts[j].y
            area -= pts[j].x * pts[i].y
        }
        return abs(area) / 2.0
    }
}
