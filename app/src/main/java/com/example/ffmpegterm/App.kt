package com.example.ffmpegterm

import android.app.Application
import android.content.Context
import com.example.ffmpegterm.ffmpeg.FFmpegBinaryInstaller

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeFFmpeg()
    }

    private fun initializeFFmpeg() {
        val installer = FFmpegBinaryInstaller(applicationContext)
        installer.installFFmpegBinary()
    }
}