package com.example.ffmpegterm.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TerminalViewModel : ViewModel() {
    private val _logOutput = MutableLiveData<String>()
    val logOutput: LiveData<String> get() = _logOutput

    private var currentCommand: String? = null

    fun updateLog(newLog: String) {
        _logOutput.value = (_logOutput.value ?: "") + newLog + "\n"
    }

    fun setCurrentCommand(command: String) {
        currentCommand = command
    }

    fun getCurrentCommand(): String? {
        return currentCommand
    }

    suspend fun clearLog() {
        withContext(Dispatchers.Main) {
            _logOutput.value = ""
        }
    }
}