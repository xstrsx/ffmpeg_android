package com.example.ffmpegterm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ffmpegterm.ffmpeg.FFmpegBinaryInstaller
import com.example.ffmpegterm.ffmpeg.FFmpegProcessManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val processManager = FFmpegProcessManager()

    /** 日志输出列表 */
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    /** 工作目录 */
    private val _workingDir = MutableStateFlow(
        application.getExternalFilesDir(null)?.absolutePath
            ?: application.filesDir.absolutePath
    )
    val workingDir: StateFlow<String> = _workingDir

    /** 进程是否运行中 */
    private val _processRunning = MutableStateFlow(false)
    val processRunning: StateFlow<Boolean> = _processRunning

    /** FFmpeg 是否就绪 */
    private val _ffmpegReady = MutableStateFlow(false)
    val ffmpegReady: StateFlow<Boolean> = _ffmpegReady

    /** 安装状态文本 */
    private val _statusText = MutableStateFlow("正在初始化...")
    val statusText: StateFlow<String> = _statusText

    init {
        initializeFFmpeg()
    }

    private fun initializeFFmpeg() {
        viewModelScope.launch(Dispatchers.IO) {
            _statusText.value = "正在准备 FFmpeg..."
            val ok = FFmpegBinaryInstaller.install(getApplication())
            if (ok) {
                _ffmpegReady.value = true
                _statusText.value = "FFmpeg 就绪 | 工作目录: ${_workingDir.value}"
                appendLog("[系统] FFmpeg 已就绪，文件路径: ${FFmpegBinaryInstaller.installedPath}")
            } else {
                _ffmpegReady.value = false
                _statusText.value = "FFmpeg 安装失败！"
                appendLog("[错误] FFmpeg 二进制文件安装失败，请检查 assets 目录。")
            }
        }
    }

    /** 执行 FFmpeg 命令 */
    fun executeCommand(command: String) {
        if (!_ffmpegReady.value) {
            appendLog("[错误] FFmpeg 尚未就绪，无法执行命令。")
            return
        }
        if (_processRunning.value) {
            appendLog("[提示] 有命令正在运行中，请先停止。")
            return
        }
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        val ffmpegPath = FFmpegBinaryInstaller.installedPath ?: return

        // 解析参数（简单按空格分割，支持引号）
        val args = parseArgs(trimmed)
        appendLog("> $trimmed")

        _processRunning.value = true
        _statusText.value = "运行中..."

        viewModelScope.launch {
            processManager.execute(
                ffmpegPath = ffmpegPath,
                args = args,
                workingDir = File(_workingDir.value),
                onOutput = { line -> appendLog(line) },
                onError = { line -> appendLog(line) },
                onComplete = { exitCode ->
                    _processRunning.value = false
                    _statusText.value = "FFmpeg 就绪 | 工作目录: ${_workingDir.value}"
                    appendLog("[完成] 退出码: $exitCode")
                }
            )
        }
    }

    /** 发送 Ctrl+C */
    fun stopWithCtrlC() {
        processManager.sendCtrlC()
    }

    /** 发送 q 键优雅退出 */
    fun stopWithQuit() {
        processManager.sendQuit()
    }

    /** 强制终止 */
    fun forceStop() {
        processManager.forceStop()
        _processRunning.value = false
        appendLog("[系统] 进程已强制终止")
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

    override fun onCleared() {
        super.onCleared()
        processManager.cleanup()
    }

    private fun appendLog(line: String) {
        _logs.value = _logs.value + line
    }

    /** 简单参数解析：按空格分割，保留引号内内容 */
    private fun parseArgs(command: String): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        var quoteChar = ' '

        for (ch in command) {
            when {
                ch == '"' || ch == '\'' -> {
                    if (inQuote && ch == quoteChar) {
                        inQuote = false
                        if (current.isNotEmpty()) {
                            args.add(current.toString())
                            current.clear()
                        }
                    } else if (!inQuote) {
                        inQuote = true
                        quoteChar = ch
                    } else {
                        current.append(ch)
                    }
                }
                ch == ' ' && !inQuote -> {
                    if (current.isNotEmpty()) {
                        args.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) args.add(current.toString())
        return args
    }
}
