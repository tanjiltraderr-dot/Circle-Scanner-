package com.example.scanner.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanner.engine.BitmapUtils
import com.example.scanner.engine.DocumentAnalyzer
import com.example.scanner.engine.ImageEnhancer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScannerViewModel : ViewModel() {

    sealed class ScannerUiState {
        object Idle : ScannerUiState()
        data class Loading(val message: String) : ScannerUiState()
        data class AutoCropSuccess(val finalImage: Bitmap, val corners: List<org.opencv.core.Point>) : ScannerUiState()
        data class RequireManualCrop(val originalFixedBitmap: Bitmap, val reason: String) : ScannerUiState()
        data class Error(val message: String) : ScannerUiState()
    }

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun onImageCaptured(capturedBitmap: Bitmap, exifRotation: Int) {
        _uiState.value = ScannerUiState.Loading("Analyzing document...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Fix EXIF Rotation first (Crucial for Android cameras)
                val fixedBitmap = BitmapUtils.rotateBitmapByExif(capturedBitmap, exifRotation)

                // 2. Run the Engine
                val result = DocumentAnalyzer.processImage(fixedBitmap)

                withContext(Dispatchers.Main) {
                    when (result) {
                        is DocumentAnalyzer.ScanResult.Success -> {
                            // AUTO CROP SUCCESSFUL! Enhance it with Magic Color filter
                            val finalImage = ImageEnhancer.applyFilter(
                                result.croppedBitmap,
                                ImageEnhancer.FilterType.MAGIC_COLOR
                            )
                            _uiState.value = ScannerUiState.AutoCropSuccess(finalImage, result.corners)
                        }
                        is DocumentAnalyzer.ScanResult.Fallback -> {
                            // AUTO CROP FAILED (Low confidence / Bad lighting)
                            // Do NOT silently crop. Show manual crop UI with the original image.
                            _uiState.value = ScannerUiState.RequireManualCrop(
                                result.originalFixedBitmap,
                                result.reason
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = ScannerUiState.Error(e.localizedMessage ?: "Processing error occurred")
                }
            }
        }
    }

    fun applyManualFilter(bitmap: Bitmap, filterType: ImageEnhancer.FilterType): Bitmap {
        return ImageEnhancer.applyFilter(bitmap, filterType)
    }

    fun resetState() {
        _uiState.value = ScannerUiState.Idle
    }
}
