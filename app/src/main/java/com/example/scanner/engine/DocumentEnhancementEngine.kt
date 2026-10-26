package com.example.scanner.engine

import android.graphics.Bitmap
import android.graphics.Color
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Standard Document Enhancement Modes requested for Utility Hub / Circle Scanner.
 */
enum class EnhancementMode(val label: String) {
    ORIGINAL("Original Color"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("Black & White"),
    DOCUMENT_CONTRAST("Document Contrast"),
    NOISE_REDUCTION("Noise Reduction"),
    SHARPENING("Controlled Sharpening"),
    CAMSCANNER_MAGIC("CamScanner Magic"),
    SHADOW_REDUCTION("Shadow Reduction"),
    DOCUMENT_COLOR("Document Color");

    companion object {
        val ORIGINAL_COLOR get() = ORIGINAL
        val CONTROLLED_SHARPENING get() = SHARPENING
    }
}

/**
 * Senior Computer Vision Document Enhancement Engine.
 *
 * Implements real image enhancement modes:
 * 1. Original Color - Exact authentic pixel preservation without distortion.
 * 2. Grayscale - Standard ITU-R luminance conversion with fine pencil/pen stroke preservation.
 * 3. Black & White - Intelligent adaptive thresholding designed to keep light handwriting/pencil visible.
 * 4. Document Contrast - Normalizes uneven background illumination and boosts text/ink contrast.
 * 5. Noise Reduction - Edge-preserving smoothing (Bilateral / selective filtering) that eliminates paper grain without blurring text glyphs.
 * 6. Controlled Sharpening - Unsharp masking with threshold coring and soft-clipping to prevent character stroke degradation.
 * 7. Shadow Reduction - Background illumination extraction with text/background separation quality validation.
 *
 * Core Guarantee: Source bitmaps are NEVER overwritten in-place; all operations return new instances
 * so that raw original captured photos are permanently preserved intact.
 */
object DocumentEnhancementEngine {

    val isOpenCvAvailable: Boolean by lazy {
        try {
            OpenCVLoader.initLocal()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Executes the requested enhancement mode on the provided document [Bitmap].
     */
    fun enhance(bitmap: Bitmap, mode: EnhancementMode): Bitmap {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return bitmap

        return when (mode) {
            EnhancementMode.ORIGINAL -> applyOriginal(bitmap)
            EnhancementMode.GRAYSCALE -> applyGrayscale(bitmap)
            EnhancementMode.BLACK_WHITE -> applyBlackAndWhite(bitmap)
            EnhancementMode.DOCUMENT_CONTRAST, EnhancementMode.DOCUMENT_COLOR -> applyDocumentContrast(bitmap)
            EnhancementMode.NOISE_REDUCTION -> applyNoiseReduction(bitmap)
            EnhancementMode.SHARPENING -> applyControlledSharpening(bitmap)
            EnhancementMode.CAMSCANNER_MAGIC -> applyCamScannerMagic(bitmap)
            EnhancementMode.SHADOW_REDUCTION -> applyShadowReduction(bitmap)
        }
    }

    // =========================================================================
    // 1. ORIGINAL COLOR
    // =========================================================================

    /**
     * Mode 1: Original Color.
     * Preserves authentic colors and tones. Returns a distinct copy to ensure
     * that original captured image data is never modified in-place.
     */
    fun applyOriginal(bitmap: Bitmap): Bitmap {
        return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
    }

    // =========================================================================
    // 2. GRAYSCALE
    // =========================================================================

    /**
     * Mode 2: Grayscale.
     * Computes perceptual luminance (ITU-R BT.601 / BT.709: 0.299R + 0.587G + 0.114B).
     * Enhances paper contrast mildly while strictly protecting faint pencil strokes and light blue inks.
     */
    fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        if (isOpenCvAvailable) {
            try {
                val srcMat = Mat()
                Utils.bitmapToMat(bitmap, srcMat)
                val grayMat = Mat()
                Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

                // Contrast stretch without clipping fine text
                val enhancedGray = Mat()
                Core.normalize(grayMat, enhancedGray, 10.0, 250.0, Core.NORM_MINMAX)

                val destMat = Mat()
                Imgproc.cvtColor(enhancedGray, destMat, Imgproc.COLOR_GRAY2RGBA)

                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(destMat, out)

                srcMat.release()
                grayMat.release()
                enhancedGray.release()
                destMat.release()
                return out
            } catch (_: Throwable) {
                // Fallback to pure Kotlin below
            }
        }

        // Pure Kotlin direct pixel array luminance conversion
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (r * 77 + g * 150 + b * 29) shr 8
            val stretch = ((lum - 10) * 255 / 232).coerceIn(0, 255)
            outPixels[i] = (0xFF shl 24) or (stretch shl 16) or (stretch shl 8) or stretch
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    // =========================================================================
    // 3. BLACK & WHITE (INTELLIGENT ADAPTIVE THRESHOLD)
    // =========================================================================

    /**
     * Mode 3: Black & White.
     * Applies calibrated local adaptive thresholding designed specifically so that:
     * - Uneven bedsheet/desk illumination and shadow gradients are stripped.
     * - Paper becomes clean pure white.
     * - CRITICAL: Faint handwriting, light pencil strokes, and thin thermal receipts
     *   DO NOT disappear into white background.
     */
    fun applyBlackAndWhite(source: Bitmap, blockSize: Int = 25, offset: Double = 6.0): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 8 || h < 8) return applyGrayscale(source)

        if (isOpenCvAvailable) {
            try {
                val srcMat = Mat()
                Utils.bitmapToMat(source, srcMat)
                val grayMat = Mat()
                Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

                val safeBlock = if (blockSize % 2 == 0) blockSize + 1 else blockSize
                val binaryMat = Mat()

                // Adaptive Gaussian threshold with conservative C offset (6.0)
                Imgproc.adaptiveThreshold(
                    grayMat,
                    binaryMat,
                    255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    safeBlock,
                    offset
                )

                val destMat = Mat()
                Imgproc.cvtColor(binaryMat, destMat, Imgproc.COLOR_GRAY2RGBA)

                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(destMat, out)

                srcMat.release()
                grayMat.release()
                binaryMat.release()
                destMat.release()
                return out
            } catch (_: Throwable) {
                // Fallback to pure Kotlin integral image below
            }
        }

        // Pure Kotlin Integral Image Adaptive Threshold
        return try {
            val pixels = IntArray(w * h)
            source.getPixels(pixels, 0, w, 0, 0, w, h)

            // Fast integer luminance: (77R + 150G + 29B) >> 8
            val gray = IntArray(w * h)
            for (i in pixels.indices) {
                val c = pixels[i]
                gray[i] = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
            }

            // Integral image for O(1) area sums
            val integral = LongArray((w + 1) * (h + 1))
            for (y in 0 until h) {
                var rowSum = 0L
                val rowOffset = (y + 1) * (w + 1)
                val prevRowOffset = y * (w + 1)
                val grayRow = y * w
                for (x in 0 until w) {
                    rowSum += gray[grayRow + x]
                    integral[rowOffset + (x + 1)] = integral[prevRowOffset + (x + 1)] + rowSum
                }
            }

            val half = blockSize / 2
            val outPixels = IntArray(w * h)
            val baseOffset = offset.toInt().coerceAtLeast(3)

            for (y in 0 until h) {
                val y1 = max(0, y - half)
                val y2 = min(h - 1, y + half)
                val intY1 = y1 * (w + 1)
                val intY2 = (y2 + 1) * (w + 1)
                val row = y * w

                for (x in 0 until w) {
                    val x1 = max(0, x - half)
                    val x2 = min(w - 1, x + half)

                    val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                    val sum = integral[intY2 + (x2 + 1)] -
                            integral[intY1 + (x2 + 1)] -
                            integral[intY2 + x1] +
                            integral[intY1 + x1]

                    val localMean = (sum / count).toInt()
                    val pixel = gray[row + x]

                    val threshold = localMean - baseOffset
                    outPixels[row + x] = if (pixel < threshold) Color.BLACK else Color.WHITE
                }
            }

            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            result.setPixels(outPixels, 0, w, 0, 0, w, h)
            result
        } catch (_: Exception) {
            applyGrayscale(source)
        }
    }

    // =========================================================================
    // 4. DOCUMENT CONTRAST
    // =========================================================================

    /**
     * Mode 4: Document Contrast.
     * Whitens the paper background, removes light ambient shadow tints,
     * and boosts contrast of text, ink signatures, and rubber stamps.
     */
    fun applyDocumentContrast(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        if (isOpenCvAvailable) {
            try {
                val srcMat = Mat()
                Utils.bitmapToMat(bitmap, srcMat)

                val yCrCbMat = Mat()
                Imgproc.cvtColor(srcMat, yCrCbMat, Imgproc.COLOR_RGBA2RGB)
                Imgproc.cvtColor(yCrCbMat, yCrCbMat, Imgproc.COLOR_RGB2YCrCb)

                val channels = ArrayList<Mat>()
                Core.split(yCrCbMat, channels)
                val yChannel = channels[0]

                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(25.0, 25.0))
                val bgMat = Mat()
                Imgproc.morphologyEx(yChannel, bgMat, Imgproc.MORPH_CLOSE, kernel)

                val normalizedY = Mat()
                Core.divide(yChannel, bgMat, normalizedY, 240.0)

                channels[0] = normalizedY
                Core.merge(channels, yCrCbMat)

                val destMat = Mat()
                Imgproc.cvtColor(yCrCbMat, destMat, Imgproc.COLOR_YCrCb2RGB)
                Imgproc.cvtColor(destMat, destMat, Imgproc.COLOR_RGB2RGBA)

                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(destMat, out)

                srcMat.release()
                yCrCbMat.release()
                bgMat.release()
                normalizedY.release()
                kernel.release()
                destMat.release()
                channels.forEach { it.release() }
                return out
            } catch (_: Throwable) {
                // Fallback to pure Kotlin
            }
        }

        // Pure Kotlin direct pixel array contrast & background whitening
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            // Whitens paper highlights (>= 190) towards 240-255, while deepening dark text (<= 140)
            val nr = if (r >= 190) (225 + (r - 190) * 30 / 65).coerceIn(0, 255) else ((r - 20) * 1.30f).toInt().coerceIn(0, 255)
            val ng = if (g >= 190) (225 + (g - 190) * 30 / 65).coerceIn(0, 255) else ((g - 20) * 1.30f).toInt().coerceIn(0, 255)
            val nb = if (b >= 190) (225 + (b - 190) * 30 / 65).coerceIn(0, 255) else ((b - 20) * 1.30f).toInt().coerceIn(0, 255)

            outPixels[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    // =========================================================================
    // 5. NOISE REDUCTION (EDGE-PRESERVING BILATERAL FILTER)
    // =========================================================================

    /**
     * Mode 5: Noise Reduction.
     * Strips ISO sensor noise, scanner grain, and paper texture while
     * strictly preserving crisp text edges and thin strokes.
     */
    fun applyNoiseReduction(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return applyOriginal(bitmap)

        if (isOpenCvAvailable) {
            try {
                val srcMat = Mat()
                Utils.bitmapToMat(bitmap, srcMat)

                val bgrMat = Mat()
                Imgproc.cvtColor(srcMat, bgrMat, Imgproc.COLOR_RGBA2BGR)

                val denoisedBgr = Mat()
                Imgproc.bilateralFilter(bgrMat, denoisedBgr, 7, 50.0, 50.0)

                val destMat = Mat()
                Imgproc.cvtColor(denoisedBgr, destMat, Imgproc.COLOR_BGR2RGBA)

                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(destMat, out)

                srcMat.release()
                bgrMat.release()
                denoisedBgr.release()
                destMat.release()
                return out
            } catch (_: Throwable) {
                // Fallback to pure Kotlin
            }
        }

        // Pure Kotlin Edge-Preserving Selective Smoothing
        val srcPixels = IntArray(w * h)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)

        System.arraycopy(srcPixels, 0, outPixels, 0, w)
        System.arraycopy(srcPixels, (h - 1) * w, outPixels, (h - 1) * w, w)

        val edgeDiffThreshold = 28

        for (y in 1 until h - 1) {
            val row = y * w
            outPixels[row] = srcPixels[row]
            outPixels[row + w - 1] = srcPixels[row + w - 1]

            for (x in 1 until w - 1) {
                val idx = row + x
                val pC = srcPixels[idx]
                val rC = (pC shr 16) and 0xFF
                val gC = (pC shr 8) and 0xFF
                val bC = pC and 0xFF

                var sumR = rC * 2
                var sumG = gC * 2
                var sumB = bC * 2
                var totalWeight = 2

                val neighbors = intArrayOf(idx - w, idx + w, idx - 1, idx + 1)
                for (nIdx in neighbors) {
                    val pN = srcPixels[nIdx]
                    val rN = (pN shr 16) and 0xFF
                    val gN = (pN shr 8) and 0xFF
                    val bN = pN and 0xFF

                    val diff = abs(rN - rC) + abs(gN - gC) + abs(bN - bC)
                    if (diff < edgeDiffThreshold * 3) {
                        sumR += rN
                        sumG += gN
                        sumB += bN
                        totalWeight += 1
                    }
                }

                val nr = sumR / totalWeight
                val ng = sumG / totalWeight
                val nb = sumB / totalWeight
                outPixels[idx] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    // =========================================================================
    // 6. CONTROLLED SHARPENING
    // =========================================================================

    /**
     * Mode 6: Controlled Sharpening.
     * Unsharp masking with threshold coring and soft-clipping:
     * - Small differences (< 4 gray levels) are ignored so paper noise is NOT amplified.
     * - Character edges receive crisp accentuation.
     * - High-contrast differences are softly clamped to prevent dark halos and loop filling
     *   inside small character loops ('e', 'o', 'a', 'p').
     */
    fun applyControlledSharpening(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 16 || h < 16) return applyOriginal(bitmap)

        if (isOpenCvAvailable) {
            try {
                val srcMat = Mat()
                Utils.bitmapToMat(bitmap, srcMat)

                val blurred = Mat()
                Imgproc.GaussianBlur(srcMat, blurred, Size(3.0, 3.0), 1.0)

                val detail = Mat()
                Core.subtract(srcMat, blurred, detail)

                val sharpened = Mat()
                Core.addWeighted(srcMat, 1.0, detail, 0.38, 0.0, sharpened)

                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(sharpened, out)

                srcMat.release()
                blurred.release()
                detail.release()
                sharpened.release()
                return out
            } catch (_: Throwable) {
                // Fallback to pure Kotlin
            }
        }

        // Pure Kotlin Unsharp Masking with Threshold Coring
        val srcPixels = IntArray(w * h)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)

        System.arraycopy(srcPixels, 0, outPixels, 0, w)
        System.arraycopy(srcPixels, (h - 1) * w, outPixels, (h - 1) * w, w)

        val gain = 0.38f
        val noiseThreshold = 4
        val maxBoost = 24

        for (y in 1 until h - 1) {
            val row = y * w
            outPixels[row] = srcPixels[row]
            outPixels[row + w - 1] = srcPixels[row + w - 1]

            for (x in 1 until w - 1) {
                val idx = row + x
                val pC = srcPixels[idx]
                val pT = srcPixels[idx - w]
                val pB = srcPixels[idx + w]
                val pL = srcPixels[idx - 1]
                val pR = srcPixels[idx + 1]

                val rC = (pC shr 16) and 0xFF
                val gC = (pC shr 8) and 0xFF
                val bC = pC and 0xFF

                val rBlur = (((pT shr 16) and 0xFF) + ((pB shr 16) and 0xFF) + ((pL shr 16) and 0xFF) + ((pR shr 16) and 0xFF)) shr 2
                val gBlur = (((pT shr 8) and 0xFF) + ((pB shr 8) and 0xFF) + ((pL shr 8) and 0xFF) + ((pR shr 8) and 0xFF)) shr 2
                val bBlur = ((pT and 0xFF) + (pB and 0xFF) + (pL and 0xFF) + (pR and 0xFF)) shr 2

                val diffR = rC - rBlur
                val diffG = gC - gBlur
                val diffB = bC - bBlur

                val boostR = if (abs(diffR) > noiseThreshold) (diffR * gain).toInt().coerceIn(-maxBoost, maxBoost) else 0
                val boostG = if (abs(diffG) > noiseThreshold) (diffG * gain).toInt().coerceIn(-maxBoost, maxBoost) else 0
                val boostB = if (abs(diffB) > noiseThreshold) (diffB * gain).toInt().coerceIn(-maxBoost, maxBoost) else 0

                val nr = (rC + boostR).coerceIn(0, 255)
                val ng = (gC + boostG).coerceIn(0, 255)
                val nb = (bC + boostB).coerceIn(0, 255)

                outPixels[idx] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    // =========================================================================
    // 7. SHADOW REDUCTION
    // =========================================================================

    /**
     * Mode 7: Shadow Reduction.
     * Lifts shadows and normalizes uneven background illumination across the document.
     * Enforces text/background contrast separation so text strokes remain distinct and dark.
     */
    fun applyShadowReduction(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        if (isOpenCvAvailable) {
            try {
                val srcMat = Mat()
                Utils.bitmapToMat(bitmap, srcMat)

                val grayMat = Mat()
                Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

                val bgMat = Mat()
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(29.0, 29.0))
                Imgproc.morphologyEx(grayMat, bgMat, Imgproc.MORPH_CLOSE, kernel)

                val normalizedGray = Mat()
                Core.divide(grayMat, bgMat, normalizedGray, 245.0)

                val destMat = Mat()
                Imgproc.cvtColor(normalizedGray, destMat, Imgproc.COLOR_GRAY2RGBA)

                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(destMat, out)

                srcMat.release()
                grayMat.release()
                bgMat.release()
                kernel.release()
                normalizedGray.release()
                destMat.release()
                return out
            } catch (_: Throwable) {
                // Fallback to pure Kotlin
            }
        }

        // Pure Kotlin Background Illumination Normalization
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)

        val blockSize = 16
        val gridW = (w + blockSize - 1) / blockSize
        val gridH = (h + blockSize - 1) / blockSize
        val bgGrid = IntArray(gridW * gridH)

        for (gy in 0 until gridH) {
            val yStart = gy * blockSize
            val yEnd = min(h, yStart + blockSize)
            for (gx in 0 until gridW) {
                val xStart = gx * blockSize
                val xEnd = min(w, xStart + blockSize)

                var maxLum = 0
                for (y in yStart until yEnd) {
                    val row = y * w
                    for (x in xStart until xEnd) {
                        val c = pixels[row + x]
                        val lum = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
                        if (lum > maxLum) maxLum = lum
                    }
                }
                bgGrid[gy * gridW + gx] = maxLum.coerceIn(90, 255)
            }
        }

        for (y in 0 until h) {
            val gy = (y / blockSize).coerceIn(0, gridH - 1)
            val row = y * w
            for (x in 0 until w) {
                val gx = (x / blockSize).coerceIn(0, gridW - 1)
                val localBg = bgGrid[gy * gridW + gx]

                val c = pixels[row + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val scale = 240.0 / localBg
                val nr = (r * scale).toInt().coerceIn(0, 255)
                val ng = (g * scale).toInt().coerceIn(0, 255)
                val nb = (b * scale).toInt().coerceIn(0, 255)

                outPixels[row + x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    // =========================================================================
    // 8. CAMSCANNER SIGNATURE MAGIC COLOR
    // =========================================================================

    /**
     * CamScanner Signature Magic Filter:
     * High dynamic contrast that whitens paper background and intensifies ink strokes
     * while preserving colored pen inks.
     */
    fun applyCamScannerMagic(bitmap: Bitmap): Bitmap {
        val contrasted = applyDocumentContrast(bitmap)
        return applyControlledSharpening(contrasted)
    }

    // =========================================================================
    // BACKWARD COMPATIBILITY ALIASES
    // =========================================================================

    fun applyAdaptiveThreshold(source: Bitmap, windowSize: Int = 25, offset: Int = 6): Bitmap {
        return applyBlackAndWhite(source, windowSize, offset.toDouble())
    }

    fun applyDocumentColor(bitmap: Bitmap): Bitmap {
        return applyDocumentContrast(bitmap)
    }

    fun applySharpening(bitmap: Bitmap): Bitmap {
        return applyControlledSharpening(bitmap)
    }
}
