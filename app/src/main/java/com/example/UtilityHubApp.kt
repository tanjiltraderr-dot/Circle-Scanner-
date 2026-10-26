package com.example

import android.app.Application
import com.example.data.repository.AppRepository

class UtilityHubApp : Application() {
    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = AppRepository(this)
        if (org.opencv.android.OpenCVLoader.initLocal()) {
            android.util.Log.d("OpenCV", "OpenCV native library initialized successfully")
        } else {
            android.util.Log.e("OpenCV", "Failed to initialize OpenCV")
        }
    }

    companion object {
        lateinit var instance: UtilityHubApp
            private set
    }
}
