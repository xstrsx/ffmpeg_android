package com.example.ffmpegterm.ffmpeg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class FFmpegProcessManager(private val workingDirectory: File) {

    private var process: Process? = null
    private var outputStream: OutputStream? = null

    suspend fun startFFmpeg(command: String) {
        withContext(Dispatchers.IO) {
            try {
                val processBuilder = ProcessBuilder(command.split(" "))
                processBuilder.directory(workingDirectory)
                process = processBuilder.start()
                outputStream = process?.outputStream

                readProcessOutput(process?.inputStream)
                readProcessOutput(process?.errorStream)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun readProcessOutput(inputStream: InputStream?) {
        inputStream?.let {
            withContext(Dispatchers.IO) {
                it.bufferedReader().forEachLine { line ->
                    // Handle the output line (e.g., send to UI)
                    println(line) // Replace with actual logging mechanism
                }
            }
        }
    }

    fun stopFFmpegGracefully() {
        outputStream?.write("q".toByteArray())
        outputStream?.flush()
    }

    fun stopFFmpegForcefully() {
        process?.destroy()
    }

    fun isRunning(): Boolean {
        return try {
            process?.exitValue()
            false // exitValue() succeeded → process has terminated
        } catch (e: IllegalThreadStateException) {
            true // process is still running
        }
    }

    fun cleanup() {
        process?.destroy()
        outputStream?.close()
    }
}