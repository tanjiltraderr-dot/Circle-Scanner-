package com.example.docscan.model

import java.io.Serializable

enum class DocScannerMode(val label: String, val description: String) {
    BASIC("Basic", "Crop & rotate only"),
    ENHANCE("Enhance", "Filters (+ magic enhance)"),
    FULL("Full", "Full (+ auto clean-up & dewarp)")
}

data class DocScanSettings(
    val pageLimit: Int = 24,
    val imageQuality: Int = 85,
    val generatePdf: Boolean = true,
    val scannerMode: DocScannerMode = DocScannerMode.FULL,
    val saveToFiles: Boolean = true,
    val saveToPhotos: Boolean = false,
    val autoEdgeDetection: Boolean = true
) : Serializable

data class DocScanDocument(
    val id: String,
    val title: String,
    val createdAt: Long,
    val pages: List<String>,
    val pdfPath: String? = null,
    val savedToFilesPath: String? = null,
    val savedToPhotos: Boolean = false,
    val fileSizeBytes: Long = 0L
) : Serializable {
    val pageCount: Int get() = pages.size
    val firstPageThumbnail: String? get() = pages.firstOrNull()
}
