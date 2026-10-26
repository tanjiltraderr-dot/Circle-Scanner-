package com.example.scanner.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object BitmapUtils {

    /**
     * Fixes EXIF rotation for camera captures
     */
    fun rotateBitmapByExif(bitmap: Bitmap, exifRotation: Int): Bitmap {
        val degrees = when (exifRotation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            90 -> 90f
            180 -> 180f
            270 -> 270f
            else -> 0f
        }

        if (degrees == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    /**
     * Saves document to internal storage and returns the absolute file path
     */
    fun saveDocumentToInternalStorage(context: Context, bitmap: Bitmap, fileName: String): String {
        val directory = File(context.filesDir, "scanned_docs")
        if (!directory.exists()) directory.mkdirs()

        val file = File(directory, "$fileName.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        return file.absolutePath
    }
}
