package com.example.scanner.engine

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object ImageEnhancer {

    enum class FilterType { ORIGINAL, MAGIC_COLOR, B_AND_W }

    fun applyFilter(bitmap: Bitmap, type: FilterType): Bitmap {
        if (type == FilterType.ORIGINAL) return bitmap

        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        val dest = Mat()

        when (type) {
            FilterType.MAGIC_COLOR -> {
                // Increase contrast and brightness mildly, keep colors
                src.convertTo(dest, -1, 1.2, 15.0)
            }
            FilterType.B_AND_W -> {
                // Adaptive Threshold for clean Black and White text
                Imgproc.cvtColor(src, dest, Imgproc.COLOR_BGR2GRAY)
                Imgproc.adaptiveThreshold(
                    dest,
                    dest,
                    255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    15,
                    10.0
                )
            }
            else -> {}
        }

        val resultBitmap = Bitmap.createBitmap(dest.cols(), dest.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dest, resultBitmap)

        src.release()
        dest.release()
        return resultBitmap
    }
}
