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
     * Android 上通过 sh -c 运行可执行文件更可靠（避免 SELinux 阻止直接 exec）。
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
            // 通过 shell 执行：sh -c '/path/to/ffmpeg arg1 "arg 2" ...'
            // 这样可以避免部分 Android 设备直接 exec 时的 Permission denied
            val fullCommand = buildString {
                append("'$ffmpegPath'")
                for (arg in args) {
                    append(" '${arg.replace("'", "'\\''")}'")
                }
            }

            val pb = ProcessBuilder("sh", "-c", fullCommand)
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

    /** 发送 q 键（FFmpeg 优雅退出） */
    fun sendQuit() {
        try {
            process?.outputStream?.write("q\n".toByteArray())
            process?.outputStream?.flush()
        } catch (_: Exception) {}
    }

    /** 发送 q 键停止（等同 Ctrl+C 效果，FFmpeg 会优雅退出并保留输出文件） */
    fun sendCtrlC() {
        sendQuit()
    }

    /** 强制终止 */
    fun forceStop() {
        try {
            process?.destroy()
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
