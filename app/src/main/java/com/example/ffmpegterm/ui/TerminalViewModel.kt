package com.example.ffmpegterm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

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

    private val _statusText = MutableStateFlow("正在初始化...")
    val statusText: StateFlow<String> = _statusText

    private var currentProcess: Process? = null
    private var ffmpegPath: String? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val libDir = ctx.applicationInfo.nativeLibraryDir
            val candidate = File(libDir, "libffmpeg_exec.so")
            if (candidate.exists() && candidate.canExecute()) {
                ffmpegPath = candidate.absolutePath
                _statusText.value = "FFmpeg 就绪 | ${_workingDir.value}"
            } else {
                _statusText.value = "FFmpeg 未找到"
                appendLog("[错误] libffmpeg_exec.so 未在原生库目录找到")
                appendLog("[提示] 目录: $libDir")
                appendLog("[提示] 内容: ${File(libDir).listFiles()?.joinToString { it.name } ?: "空"}")
            }
        }
    }

    fun executeCommand(rawCommand: String) {
        if (ffmpegPath == null) { appendLog("[错误] FFmpeg 未就绪"); return }
        if (_processRunning.value) { appendLog("[提示] 正在运行中"); return }

        val merged = rawCommand
            .replace("\\\n", " ").replace("\\\r\n", " ")
            .replace("\n", " ").replace("\r", " ")
        val trimmed = merged.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return

        val command = if (trimmed.startsWith("ffmpeg ")) trimmed.removePrefix("ffmpeg ")
        else if (trimmed == "ffmpeg") "-version" else trimmed
        val displayCmd = if (trimmed.startsWith("ffmpeg")) trimmed else "ffmpeg $trimmed"
        appendLog("> $displayCmd")

        _processRunning.value = true
        _statusText.value = "运行中..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val args = parseArgs(command)
                val cmd = mutableListOf(ffmpegPath!!); cmd.addAll(args)
                val pb = ProcessBuilder(cmd)
                    .directory(File(_workingDir.value))
                    .redirectErrorStream(false)
                currentProcess = pb.start()

                launch(Dispatchers.IO) {
                    currentProcess!!.inputStream.bufferedReader().use { r ->
                        var l = r.readLine()
                        while (l != null) { launch(Dispatchers.Main) { appendLog(l) }; l = r.readLine() }
                    }
                }
                launch(Dispatchers.IO) {
                    currentProcess!!.errorStream.bufferedReader().use { r ->
                        var l = r.readLine()
                        while (l != null) { launch(Dispatchers.Main) { appendLog(l) }; l = r.readLine() }
                    }
                }

                val exit = currentProcess!!.waitFor()
                currentProcess = null
                launch(Dispatchers.Main) {
                    _processRunning.value = false
                    _statusText.value = "FFmpeg 就绪 | ${_workingDir.value}"
                    appendLog("[完成] 退出码: $exit")
                }
            } catch (e: Exception) {
                currentProcess = null
                launch(Dispatchers.Main) {
                    _processRunning.value = false
                    appendLog("[错误] ${e.message}")
                }
            }
        }
    }

    fun forceStop() {
        try { currentProcess?.destroy() } catch (_: Exception) {}
        currentProcess = null; _processRunning.value = false
    }
    fun stopWithQuit() {
        try {
            currentProcess?.outputStream?.write("q\n".toByteArray())
            currentProcess?.outputStream?.flush()
        } catch (_: Exception) { forceStop() }
    }
    fun stopWithCtrlC() = stopWithQuit()
    fun clearLogs() { _logs.value = emptyList() }

    fun setWorkingDirectory(path: String) {
        _workingDir.value = path
        _statusText.value = "FFmpeg 就绪 | $path"
    }

    private fun appendLog(line: String) { _logs.value = _logs.value + line }

    private fun parseArgs(input: String): List<String> {
        val args = mutableListOf<String>()
        val buf = StringBuilder()
        var inQuote = false; var q = ' '
        for (ch in input) {
            when {
                (ch == '"' || ch == '\'') && !inQuote -> { inQuote = true; q = ch }
                inQuote && ch == q -> { inQuote = false; args.add(buf.toString()); buf.clear() }
                ch == ' ' && !inQuote -> { if (buf.isNotEmpty()) { args.add(buf.toString()); buf.clear() } }
                else -> buf.append(ch)
            }
        }
        if (buf.isNotEmpty()) args.add(buf.toString())
        return args
    }
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

