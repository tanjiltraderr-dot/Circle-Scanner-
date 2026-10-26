package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

object PdfUtils {

    // Standard A4 dimensions at 72 DPI (595 x 842 points)
    const val PAGE_WIDTH_A4 = 595
    const val PAGE_HEIGHT_A4 = 842

    fun imagesToPdf(
        context: Context,
        bitmaps: List<Bitmap>,
        outputFileName: String = "doc_${System.currentTimeMillis()}.pdf"
    ): File? {
        if (bitmaps.isEmpty()) return null
        val pdfDocument = PdfDocument()

        return try {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            for ((index, bitmap) in bitmaps.withIndex()) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_A4, PAGE_HEIGHT_A4, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                // Fill background white
                canvas.drawColor(Color.WHITE)

                // Scale bitmap to fit within A4 preserving aspect ratio with margin
                val margin = 24
                val availW = PAGE_WIDTH_A4 - (margin * 2)
                val availH = PAGE_HEIGHT_A4 - (margin * 2)

                val ratio = minOf(availW.toFloat() / bitmap.width, availH.toFloat() / bitmap.height)
                val destW = (bitmap.width * ratio).toInt()
                val destH = (bitmap.height * ratio).toInt()
                val left = margin + (availW - destW) / 2
                val top = margin + (availH - destH) / 2

                val destRect = Rect(left, top, left + destW, top + destH)
                canvas.drawBitmap(bitmap, null, destRect, paint)

                pdfDocument.finishPage(page)
            }

            val outDir = File(context.filesDir, "pdfs").apply { mkdirs() }
            val outFile = File(outDir, outputFileName)
            FileOutputStream(outFile).use { out ->
                pdfDocument.writeTo(out)
            }
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            pdfDocument.close()
        }
    }

    fun pdfToBitmaps(context: Context, pdfUri: Uri, maxPages: Int = 10): List<Bitmap> {
        val result = mutableListOf<Bitmap>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return emptyList()
            renderer = PdfRenderer(pfd)
            val pageCount = minOf(renderer.pageCount, maxPages)

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val width = page.width * 2
                val height = page.height * 2
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                result.add(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            renderer?.close()
            pfd?.close()
        }
        return result
    }

    fun getPdfPageCount(context: Context, pdfUri: Uri): Int {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                renderer.pageCount
            } else 0
        } catch (e: Exception) {
            0
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    fun mergePdfUris(context: Context, uris: List<Uri>, outputFileName: String): File? {
        if (uris.isEmpty()) return null
        val pdfDocument = PdfDocument()
        var totalPageIndex = 1

        try {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            for (uri in uris) {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: continue
                val renderer = PdfRenderer(pfd)
                for (p in 0 until renderer.pageCount) {
                    val page = renderer.openPage(p)
                    val w = page.width
                    val h = page.height
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvasBmp = Canvas(bitmap)
                    canvasBmp.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageInfo = PdfDocument.PageInfo.Builder(w, h, totalPageIndex++).create()
                    val docPage = pdfDocument.startPage(pageInfo)
                    docPage.canvas.drawBitmap(bitmap, 0f, 0f, paint)
                    pdfDocument.finishPage(docPage)
                }
                renderer.close()
                pfd.close()
            }

            val outDir = File(context.filesDir, "pdfs").apply { mkdirs() }
            val outFile = File(outDir, outputFileName)
            FileOutputStream(outFile).use { out ->
                pdfDocument.writeTo(out)
            }
            return outFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            pdfDocument.close()
        }
    }

    fun splitPdfPages(context: Context, pdfUri: Uri, startPage: Int, endPage: Int, outputFileName: String): File? {
        val pdfDocument = PdfDocument()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        return try {
            pfd = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return null
            renderer = PdfRenderer(pfd)
            val total = renderer.pageCount
            val from = (startPage - 1).coerceIn(0, total - 1)
            val to = (endPage - 1).coerceIn(from, total - 1)

            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            var outIndex = 1

            for (p in from..to) {
                val page = renderer.openPage(p)
                val w = page.width
                val h = page.height
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvasBmp = Canvas(bitmap)
                canvasBmp.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(w, h, outIndex++).create()
                val docPage = pdfDocument.startPage(pageInfo)
                docPage.canvas.drawBitmap(bitmap, 0f, 0f, paint)
                pdfDocument.finishPage(docPage)
            }

            val outDir = File(context.filesDir, "pdfs").apply { mkdirs() }
            val outFile = File(outDir, outputFileName)
            FileOutputStream(outFile).use { out ->
                pdfDocument.writeTo(out)
            }
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            renderer?.close()
            pfd?.close()
            pdfDocument.close()
        }
    }
}
