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
            _statusText.value = "正在定位 FFmpeg 二进制..."
            val ok = FFmpegBinaryInstaller.install(getApplication())
            if (ok) {
                _ffmpegReady.value = true
                _statusText.value = "FFmpeg 就绪 | 工作目录: ${_workingDir.value}"
                appendLog("[系统] FFmpeg 已就绪")
                appendLog("[系统] 路径: ${FFmpegBinaryInstaller.installedPath}")
            } else {
                _ffmpegReady.value = false
                _statusText.value = "FFmpeg 未找到！请放置二进制文件。"
                appendLog("========================================")
                appendLog("[错误] 未找到有效的 FFmpeg 二进制文件！")
                appendLog("")
                appendLog("请将 Android 版 ffmpeg 二进制放入以下位置之一：")
                appendLog("  1. jniLibs/<架构>/libffmpeg_exec.so（推荐）")
                appendLog("     arm64-v8a: app/src/main/jniLibs/arm64-v8a/")
                appendLog("     armeabi-v7a: app/src/main/jniLibs/armeabi-v7a/")
                appendLog("  2. assets/ffmpeg/<架构>/ffmpeg（备用）")
                appendLog("     arm64-v8a: app/src/main/assets/ffmpeg/arm64-v8a/")
                appendLog("     armeabi-v7a: app/src/main/assets/ffmpeg/armeabi-v7a/")
                appendLog("")
                appendLog("可从以下渠道获取预编译二进制：")
                appendLog("  https://github.com/nicknisi/ffmpeg-android")
                appendLog("  https://johnvansickle.com/ffmpeg/ (选 arm64/armv7)")
                appendLog("========================================")
            }
        }
    }

    /** 执行命令 — 接受完整 ffmpeg 命令行，支持 \ 续行 */
    fun executeCommand(rawCommand: String) {
        if (!_ffmpegReady.value) {
            appendLog("[错误] FFmpeg 尚未就绪，无法执行命令。")
            return
        }
        if (_processRunning.value) {
            appendLog("[提示] 有命令正在运行中，请先停止。")
            return
        }

        // 多行处理：\ 续行符 → 拼接；普通换行 → 空格
        val merged = rawCommand
            .replace("\\\n", " ")
            .replace("\\\r\n", " ")
            .replace("\n", " ")
            .replace("\r", " ")
        val trimmed = merged.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return

        val ffmpegPath = FFmpegBinaryInstaller.installedPath ?: return

        // 智能解析：去除可选的 "ffmpeg" 前缀
        val args = if (trimmed.startsWith("ffmpeg ")) {
            parseArgs(trimmed.removePrefix("ffmpeg ").trim())
        } else if (trimmed == "ffmpeg") {
            listOf("-version")
        } else {
            parseArgs(trimmed)
        }

        val displayCmd = if (trimmed.startsWith("ffmpeg")) trimmed else "ffmpeg $trimmed"
        appendLog("> $displayCmd")

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
