package com.example.ffmpegterm.ffmpeg

import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream

/**
 * 管理 FFmpeg 进程的生命周期、输入输出流。
 */
class FFmpegProcessManager {

    private var process: Process? = null
    private var outputJob: Job? = null
    private var errorJob: Job? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    /**
     * 执行 FFmpeg 命令。
     * @param ffmpegPath ffmpeg 二进制路径
     * @param args FFmpeg 参数（不含"ffmpeg"自身）
     * @param workingDir 工作目录
     * @param onOutput 标准输出回调
     * @param onError 标准错误回调（FFmpeg 的日志信息在 stderr）
     * @param onComplete 进程结束回调 (exitCode)
     */
    suspend fun execute(
        ffmpegPath: String,
        args: List<String>,
        workingDir: File,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit,
        onComplete: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val cmd = mutableListOf(ffmpegPath)
            cmd.addAll(args)

            val pb = ProcessBuilder(cmd)
                .directory(workingDir)
                .redirectErrorStream(false)

            process = pb.start()
            isRunning = true

            val scope = CoroutineScope(Dispatchers.IO)

            // 读取 stdout
            outputJob = scope.launch {
                streamReader(process!!.inputStream, onOutput)
            }

            // 读取 stderr（FFmpeg 的进度/日志信息）
            errorJob = scope.launch {
                streamReader(process!!.errorStream, onError)
            }

            // 等待进程结束
            val exitCode = withContext(Dispatchers.IO) {
                process!!.waitFor()
            }

            outputJob?.join()
            errorJob?.join()
            scope.cancel()

            isRunning = false
            process = null

            withContext(Dispatchers.Main) {
                onComplete(exitCode)
            }
        } catch (e: Exception) {
            isRunning = false
            process = null
            withContext(Dispatchers.Main) {
                onError("[ERROR] ${e.message}")
                onComplete(-1)
            }
        }
    }

    /** 发送 q 键（优雅退出） */
    fun sendQuit() {
        try {
            process?.outputStream?.write("q\n".toByteArray())
            process?.outputStream?.flush()
        } catch (_: Exception) {}
    }

    /** 发送 SIGINT（Ctrl+C） */
    fun sendCtrlC() {
        try {
            process?.outputStream?.write("\u0003".toByteArray())
            process?.outputStream?.flush()
        } catch (_: Exception) {}
    }

    /** 强制终止 */
    fun forceStop() {
        try {
            process?.destroyForcibly()
        } catch (_: Exception) {}
        isRunning = false
        process = null
    }

    /** 清理资源 */
    fun cleanup() {
        outputJob?.cancel()
        errorJob?.cancel()
        forceStop()
    }

    private suspend fun streamReader(input: InputStream, callback: (String) -> Unit) {
        try {
            input.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    withContext(Dispatchers.Main) {
                        callback(line)
                    }
                    line = reader.readLine()
                }
            }
        } catch (_: Exception) {
            // 流被关闭时忽略
        }
    }
}
