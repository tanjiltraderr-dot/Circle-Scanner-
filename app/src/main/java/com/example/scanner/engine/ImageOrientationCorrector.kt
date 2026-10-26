package com.example.scanner.engine

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InputStream
import kotlin.math.abs

object ImageOrientationCorrector {

    /**
     * Fixes image orientation based on EXIF metadata stream.
     */
    fun fixExifOrientation(bitmap: Bitmap, inputStream: InputStream): Bitmap {
        return try {
            val exif = ExifInterface(inputStream)
            applyExifOrientation(bitmap, exif)
        } catch (_: Exception) {
            bitmap
        }
    }

    /**
     * Fixes image orientation directly from a file path.
     */
    fun fixExifOrientation(bitmap: Bitmap, filePath: String): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            applyExifOrientation(bitmap, exif)
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, exif: ExifInterface): Bitmap {
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return bitmap
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Rotates bitmap by specified degrees.
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Automatically detects whether the document text is photographed sideways or inverted,
     * and rotates it upright using Google ML Kit text orientation line analysis.
     * Note: Respects document geometry — does NOT arbitrarily rotate documents merely because width > height.
     */
    suspend fun autoCorrectOrientation(bitmap: Bitmap): Pair<Bitmap, Float> = suspendCancellableCoroutine { continuation ->
        try {
            val maxDim = 800
            val scale = minOf(1.0f, maxDim.toFloat() / maxOf(bitmap.width, bitmap.height))
            val sampleBmp = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else bitmap

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(sampleBmp, 0)

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
                            norm in 65f..115f -> 270f   // Text is sideways 90 deg -> rotate 270 deg
                            norm in -115f..-65f -> 90f  // Text is sideways -90 deg -> rotate 90 deg
                            norm > 155f || norm < -155f -> 180f // Text is upside down -> rotate 180 deg
                            abs(norm) in 1.5f..25f -> -norm     // Minor tilt -> fine deskew
                            else -> 0f
                        }
                    } else {
                        // Crucial: When no text is detected (e.g. certificates, drawings, wide blank forms),
                        // do NOT arbitrarily rotate merely because width > height. Preserve true document geometry.
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
                    // On recognition failure, preserve orientation as-is without blind aspect-ratio guessing
                    if (sampleBmp != bitmap) {
                        sampleBmp.recycle()
                    }
                    continuation.resume(Pair(bitmap, 0f), null)
                }
        } catch (_: Exception) {
            continuation.resume(Pair(bitmap, 0f), null)
        }
    }
}
