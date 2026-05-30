package com.example.ffmpegterm

import android.app.Application
import com.example.ffmpegterm.ffmpeg.FFmpegBinaryInstaller

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeFFmpeg()
    }

    private fun initializeFFmpeg() {
        FFmpegBinaryInstaller.installFFmpegBinary(applicationContext)
    }
}