package com.example.ui.screens.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class ScannerMode(
    val title: String,
    val subtitle: String,
    val instruction: String,
    val icon: ImageVector
) {
    QR_BARCODE(
        title = "QR & Barcode",
        subtitle = "Scan QR codes and barcodes automatically",
        instruction = "Point camera at a QR code or barcode",
        icon = Icons.Default.QrCodeScanner
    ),
    DOCUMENT_PDF(
        title = "Document / PDF",
        subtitle = "Scan multi-page documents and create PDFs",
        instruction = "Position document inside the frame and capture",
        icon = Icons.Default.DocumentScanner
    ),
    ID_CARD(
        title = "ID / Card",
        subtitle = "Capture IDs, business cards & badges",
        instruction = "Align card edges within the rectangular guide",
        icon = Icons.Default.Badge
    ),
    IMAGE_SCAN(
        title = "Image Scan",
        subtitle = "Process and enhance photos from camera or gallery",
        instruction = "Frame the subject to capture or import photo",
        icon = Icons.Default.CropFree
    )
}
