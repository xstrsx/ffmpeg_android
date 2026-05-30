package com.example.ffmpegterm

import android.app.Application
import androidx.multidex.MultiDex

class App : Application() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}
