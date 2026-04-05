package com.kenshin.vreader2

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VReaderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Các extension sẽ được load trực tiếp từ file thay vì copy từ assets
    }
}
