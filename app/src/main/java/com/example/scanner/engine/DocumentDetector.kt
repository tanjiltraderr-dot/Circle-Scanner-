package com.example.scanner.engine

import android.graphics.Bitmap
import com.example.utils.DocumentQuad

interface IDocumentDetector {
    fun detectDocument(bitmap: Bitmap): DocumentQuad?
}

/**
 * Modern Document Detector implementing [IDocumentDetector].
 * Delegates all detection requests to the centralized [DocumentDetectionEngine],
 * ensuring multi-strategy contrast-normalized OpenCV detection and strict geometric validation.
 */
class ContourDocumentDetector(
    private val engine: DocumentDetectionEngine = DocumentDetectionEngine()
) : IDocumentDetector {

    override fun detectDocument(bitmap: Bitmap): DocumentQuad? {
        val result = engine.detect(bitmap)
        return if (result.isSuccess) result.quad else null
    }

    fun detectDetailed(bitmap: Bitmap): DetectionResult {
        return engine.detect(bitmap)
    }
}
