package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_documents")
data class ScannedDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val originalFileName: String,
    val processedImagePath: String? = null,
    val rawImagePath: String? = null,
    val pdfFilePath: String? = null,
    val thumbnailPath: String? = null,
    val pageCount: Int = 1,
    val fileSizeBytes: Long = 0L,
    val ocrExtractedText: String? = null,
    val filterUsed: String = "CamScanner Magic",
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
) {
    val fullProcessedPath: String
        get() = processedImagePath ?: originalFileName
}
