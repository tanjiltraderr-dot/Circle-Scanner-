package com.example.scanner.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-performance PDF conversion utility for scanned & binarized images.
 * Uses Android's built-in [PdfDocument] API to assemble single or multi-page documents.
 */
object PdfConverterUtility {

    enum class PageFormat(val widthPoints: Int, val heightPoints: Int, val label: String) {
        A4(595, 842, "A4 (Standard)"),
        LETTER(612, 792, "US Letter"),
        FIT_IMAGE(0, 0, "Fit to Image")
    }

    data class PdfConversionOptions(
        val pageFormat: PageFormat = PageFormat.A4,
        val marginPoints: Int = 20,
        val addPageNumbers: Boolean = true,
        val documentTitle: String? = null
    )

    data class PdfResult(
        val file: File,
        val pageCount: Int,
        val fileSizeBytes: Long,
        val thumbnailFile: File?
    )

    /**
     * Compiles a collection of processed/binarized document bitmaps into a PDF.
     */
    suspend fun convertBitmapsToPdf(
        context: Context,
        bitmaps: List<Bitmap>,
        fileNamePrefix: String = "Scan",
        options: PdfConversionOptions = PdfConversionOptions()
    ): PdfResult? = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) return@withContext null

        val pdfDocument = PdfDocument()
        val pdfDir = File(context.filesDir, "scanned_pdfs").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val sanitizedPrefix = fileNamePrefix.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val pdfFile = File(pdfDir, "${sanitizedPrefix}_$timestamp.pdf")

        // First page thumbnail
        val thumbsDir = File(context.filesDir, "scan_thumbnails").apply { mkdirs() }
        val thumbFile = File(thumbsDir, "thumb_${sanitizedPrefix}_$timestamp.jpg")

        try {
            val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 10f
                textAlign = Paint.Align.CENTER
            }

            for ((index, originalBmp) in bitmaps.withIndex()) {
                val pageNumber = index + 1
                val targetW = if (options.pageFormat == PageFormat.FIT_IMAGE) {
                    originalBmp.width
                } else {
                    options.pageFormat.widthPoints
                }
                val targetH = if (options.pageFormat == PageFormat.FIT_IMAGE) {
                    originalBmp.height
                } else {
                    options.pageFormat.heightPoints
                }

                val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                // Solid white background for clear contrast
                canvas.drawColor(Color.WHITE)

                val margin = if (options.pageFormat == PageFormat.FIT_IMAGE) 0 else options.marginPoints
                val footerHeight = if (options.addPageNumbers && options.pageFormat != PageFormat.FIT_IMAGE) 24 else 0

                val availW = (targetW - (margin * 2)).coerceAtLeast(1)
                val availH = (targetH - (margin * 2) - footerHeight).coerceAtLeast(1)

                val scale = minOf(
                    availW.toFloat() / originalBmp.width.toFloat(),
                    availH.toFloat() / originalBmp.height.toFloat()
                )

                val scaledW = (originalBmp.width * scale).toInt()
                val scaledH = (originalBmp.height * scale).toInt()

                val left = margin + (availW - scaledW) / 2
                val top = margin + (availH - scaledH) / 2

                val destRect = Rect(left, top, left + scaledW, top + scaledH)
                canvas.drawBitmap(originalBmp, null, destRect, bitmapPaint)

                // Optional page number
                if (options.addPageNumbers && options.pageFormat != PageFormat.FIT_IMAGE) {
                    val footerText = "Page $pageNumber of ${bitmaps.size}"
                    canvas.drawText(
                        footerText,
                        (targetW / 2).toFloat(),
                        (targetH - 8).toFloat(),
                        textPaint
                    )
                }

                pdfDocument.finishPage(page)
            }

            // Write PDF to disk
            FileOutputStream(pdfFile).use { fos ->
                pdfDocument.writeTo(fos)
            }

            // Create compressed JPEG thumbnail of first page for Room persistence & rapid UI loading
            try {
                val firstBmp = bitmaps.first()
                val thumbScale = 320f / maxOf(firstBmp.width, firstBmp.height).toFloat()
                val thumbW = (firstBmp.width * thumbScale).toInt().coerceAtLeast(64)
                val thumbH = (firstBmp.height * thumbScale).toInt().coerceAtLeast(64)
                val scaledThumb = Bitmap.createScaledBitmap(firstBmp, thumbW, thumbH, true)

                FileOutputStream(thumbFile).use { fos ->
                    scaledThumb.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                }
                if (scaledThumb != firstBmp) {
                    scaledThumb.recycle()
                }
            } catch (_: Exception) {}

            PdfResult(
                file = pdfFile,
                pageCount = bitmaps.size,
                fileSizeBytes = pdfFile.length(),
                thumbnailFile = if (thumbFile.exists()) thumbFile else null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            pdfDocument.close()
        }
    }
}
