package com.example.scanner.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OCR Service using Google ML Kit Text Recognition.
 * Extracts, formats, and structures text from binarized scanned documents.
 */
object DocumentOcrService {

    data class PageOcr(
        val pageNumber: Int,
        val text: String,
        val blockCount: Int,
        val lineCount: Int
    )

    data class DocumentOcrResult(
        val fullClipboardText: String,
        val pages: List<PageOcr>,
        val totalWords: Int,
        val totalCharacters: Int,
        val estimatedReadingMinutes: Int
    )

    /**
     * Extracts text from a single document page bitmap.
     */
    suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = Tasks.await(recognizer.process(inputImage))
            cleanAndFormatText(visionText.text)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        } finally {
            recognizer.close()
        }
    }

    /**
     * Extracts and aggregates text from multiple document pages into a clipboard-friendly document.
     */
    suspend fun recognizeMultiPageDocument(
        bitmaps: List<Bitmap>,
        documentTitle: String? = null
    ): DocumentOcrResult = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val pages = mutableListOf<PageOcr>()
        val stringBuilder = StringBuilder()

        if (!documentTitle.isNullOrBlank()) {
            stringBuilder.append("# ").append(documentTitle.trim()).append("\n\n")
        }

        try {
            for ((index, bitmap) in bitmaps.withIndex()) {
                val pageNum = index + 1
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = Tasks.await(recognizer.process(inputImage))

                val cleaned = cleanAndFormatText(visionText.text)
                val blockCount = visionText.textBlocks.size
                val lineCount = visionText.textBlocks.sumOf { it.lines.size }

                pages.add(
                    PageOcr(
                        pageNumber = pageNum,
                        text = cleaned,
                        blockCount = blockCount,
                        lineCount = lineCount
                    )
                )

                if (bitmaps.size > 1) {
                    stringBuilder.append("--- Page ").append(pageNum).append(" of ").append(bitmaps.size).append(" ---\n\n")
                }

                if (cleaned.isNotBlank()) {
                    stringBuilder.append(cleaned).append("\n\n")
                } else {
                    stringBuilder.append("[No text detected on this page]\n\n")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            recognizer.close()
        }

        val fullText = stringBuilder.toString().trim()
        val words = if (fullText.isBlank()) 0 else fullText.split(Regex("\\s+")).size
        val chars = fullText.length
        val readingTime = (words / 200).coerceAtLeast(1)

        DocumentOcrResult(
            fullClipboardText = fullText,
            pages = pages,
            totalWords = words,
            totalCharacters = chars,
            estimatedReadingMinutes = readingTime
        )
    }

    /**
     * Cleans OCR text: normalizes linebreaks, trims orphan whitespace, strips control codes.
     */
    private fun cleanAndFormatText(rawText: String): String {
        if (rawText.isBlank()) return ""

        return rawText
            // Normalize carriage returns
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            // Remove excessive blank lines (more than 2)
            .replace(Regex("\n{3,}"), "\n\n")
            // Trim leading/trailing spaces per line
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }

    /**
     * Copies extracted text to the Android system clipboard with a confirmation Toast.
     */
    fun copyToClipboard(context: Context, text: String, label: String = "Scanned Text") {
        if (text.isBlank()) {
            Toast.makeText(context, "No text to copy", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
