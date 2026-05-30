package com.example.ffmpegterm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback
import com.arthenica.ffmpegkit.LogCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _workingDir = MutableStateFlow(
        application.getExternalFilesDir(null)?.absolutePath
            ?: application.filesDir.absolutePath
    )
    val workingDir: StateFlow<String> = _workingDir

    private val _processRunning = MutableStateFlow(false)
    val processRunning: StateFlow<Boolean> = _processRunning

    private val _statusText = MutableStateFlow("FFmpeg 就绪")
    val statusText: StateFlow<String> = _statusText

    private var currentSession: FFmpegSession? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            appendLog("[系统] FFmpeg Kit 已就绪")
            appendLog("[系统] 版本: ${FFmpegKitConfig.getFFmpegVersion()}")
            _statusText.value = "FFmpeg 就绪 | 工作目录: ${_workingDir.value}"
        }
    }

    /** 执行命令 — 接受完整 ffmpeg 命令行 */
    fun executeCommand(rawCommand: String) {
        if (_processRunning.value) {
            appendLog("[提示] 有命令正在运行中，请先停止。")
            return
        }

        // 多行处理
        val merged = rawCommand
            .replace("\\\n", " ")
            .replace("\\\r\n", " ")
            .replace("\n", " ")
            .replace("\r", " ")
        val trimmed = merged.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return

        // 智能去除 "ffmpeg" 前缀
        val command = if (trimmed.startsWith("ffmpeg ")) {
            trimmed.removePrefix("ffmpeg ")
        } else if (trimmed == "ffmpeg") {
            "-version"
        } else {
            trimmed
        }

        val displayCmd = if (trimmed.startsWith("ffmpeg")) trimmed else "ffmpeg $trimmed"
        appendLog("> $displayCmd")

        _processRunning.value = true
        _statusText.value = "运行中..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 设置工作目录
                val workDir = _workingDir.value
                // ffmpeg-kit 不支持直接切换工作目录，用 -y 参数时通过输出路径即可

                val session = FFmpegKit.executeAsync(
                    command,
                    { log ->
                        launch(Dispatchers.Main) {
                            appendLog(log.message)
                        }
                    },
                    { session ->
                        launch(Dispatchers.Main) {
                            currentSession = null
                            _processRunning.value = false
                            _statusText.value = "FFmpeg 就绪 | 工作目录: $workDir"

                            val rc = session.returnCode
                            if (rc.isValueSuccess) {
                                appendLog("[完成] 退出码: ${rc.value}")
                            } else {
                                appendLog("[失败] 退出码: ${rc.value}")
                                appendLog("[错误] ${session.failStackTrace ?: "未知错误"}")
                            }
                        }
                    }
                )

                currentSession = session

                // 同步等待执行完成
                session.returnCode
                // 注意：由于使用了 complete callback，这里不需要额外处理

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    _processRunning.value = false
                    _statusText.value = "FFmpeg 就绪 | 工作目录: ${_workingDir.value}"
                    appendLog("[异常] ${e.message}")
                }
            }
        }
    }

    /** 强制停止 */
    fun forceStop() {
        currentSession?.cancel()
        currentSession = null
        _processRunning.value = false
        appendLog("[系统] 进程已强制终止")
    }

    /** 发送 q 键停止（等同 forceStop，ffmpeg-kit 优雅方式） */
    fun stopWithQuit() {
        forceStop()
    }

    /** 停止（等同 forceStop） */
    fun stopWithCtrlC() {
        forceStop()
    }

    /** 清空日志 */
    fun clearLogs() {
        _logs.value = emptyList()
    }

    /** 设置工作目录 */
    fun setWorkingDirectory(path: String) {
        _workingDir.value = path
        _statusText.value = "FFmpeg 就绪 | 工作目录: $path"
        appendLog("[系统] 工作目录切换至: $path")
    }

    private fun appendLog(line: String) {
        _logs.value = _logs.value + line
    }
}

