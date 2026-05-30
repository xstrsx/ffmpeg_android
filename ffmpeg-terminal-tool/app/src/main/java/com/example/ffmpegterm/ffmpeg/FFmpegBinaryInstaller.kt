package com.example.ffmpegterm.ffmpeg

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FFmpegBinaryInstaller {

    private const val ARM64_V8A = "arm64-v8a"
    private const val ARMEABI_V7A = "armeabi-v7a"
    private const val FFmpeg_BINARY_NAME = "ffmpeg"

    fun installFFmpegBinary(context: Context) {
        val targetDir = context.getFilesDir()
        val architecture = getArchitecture()
        val ffmpegBinaryPath = File(targetDir, FFmpeg_BINARY_NAME)

        if (!ffmpegBinaryPath.exists()) {
            copyBinaryFromAssets(context, architecture, ffmpegBinaryPath)
            setExecutablePermission(ffmpegBinaryPath)
        }
    }

    private fun getArchitecture(): String {
        return when {
            android.os.Build.SUPPORTED_ABIS.contains(ARM64_V8A) -> ARM64_V8A
            android.os.Build.SUPPORTED_ABIS.contains(ARMEABI_V7A) -> ARMEABI_V7A
            else -> throw UnsupportedOperationException("Unsupported architecture")
        }
    }

    private fun copyBinaryFromAssets(context: Context, architecture: String, targetFile: File) {
        val assetManager = context.assets
        val inputStream: InputStream = assetManager.open("ffmpeg/$architecture/$FFmpeg_BINARY_NAME")
        val outputStream = FileOutputStream(targetFile)

        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun setExecutablePermission(file: File) {
        file.setExecutable(true)
    }
}