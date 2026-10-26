package com.example.scanner

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

class ScannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (OpenCVLoader.initLocal()) {
            Log.d("OpenCV", "OpenCV native library initialized successfully")
        } else {
            Log.e("OpenCV", "Failed to initialize OpenCV")
        }
    }
}
