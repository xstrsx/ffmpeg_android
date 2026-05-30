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
     * 优先尝试直接 exec（避免 sh 的 seccomp 限制导致 "Bad system call"），
     * 失败则回退到 sh -c（兼容某些 SELinux 严格设备）。
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
            // 构建完整命令行：[ffmpeg路径, arg1, arg2, ...]
            val cmd = mutableListOf(ffmpegPath)
            cmd.addAll(args)

            // 环境变量（帮助静态二进制绕过 Android seccomp 限制）
            val env = ProcessBuilder(cmd).environment().apply {
                put("LD_PRELOAD", "")
                put("PROOT_NO_SECCOMP", "1")
            }

            // 方案1: 直接 ProcessBuilder exec
            var pb = ProcessBuilder(cmd)
                .directory(workingDir)
                .redirectErrorStream(false)
            pb.environment().putAll(env)

            try {
                process = pb.start()
            } catch (e: Exception) {
                // 方案2: 通过 sh -c 重试
                val fullCommand = buildString {
                    append("'$ffmpegPath'")
                    for (arg in args) {
                        append(" '${arg.replace("'", "'\\''")}'")
                    }
                }
                pb = ProcessBuilder("sh", "-c", fullCommand)
                    .directory(workingDir)
                    .redirectErrorStream(false)
                pb.environment().putAll(env)
                process = pb.start()
            }

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
