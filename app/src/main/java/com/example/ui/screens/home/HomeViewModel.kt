package com.example.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.UtilityHubApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as UtilityHubApp).repository

    private val _scannerMode = MutableStateFlow(ScannerMode.QR_BARCODE)
    val scannerMode: StateFlow<ScannerMode> = _scannerMode.asStateFlow()

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    val isProUser: StateFlow<Boolean> = repository.isProUnlocked

    fun setScannerMode(mode: ScannerMode) {
        _scannerMode.value = mode
    }

    fun toggleFlash() {
        _isFlashOn.value = !_isFlashOn.value
    }

    fun setFlash(on: Boolean) {
        _isFlashOn.value = on
    }

    fun logScanHistory(toolId: String, title: String, details: String) {
        viewModelScope.launch {
            repository.addHistory(toolId, title, details)
        }
    }
}
