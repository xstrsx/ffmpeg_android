package com.example.ffmpegterm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ffmpegterm.databinding.ActivityMainBinding
import com.example.ffmpegterm.ui.LogAdapter
import com.example.ffmpegterm.ui.TerminalViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: TerminalViewModel
    private lateinit var logAdapter: LogAdapter

    private val dirPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                val path = uri.path?.replace("/tree/primary:", "/storage/emulated/0/")
                    ?: uri.toString()
                viewModel.setWorkingDirectory(path)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[TerminalViewModel::class.java]

        setupLogList()
        setupButtons()
        observeViewModel()
    }

    private fun setupLogList() {
        logAdapter = LogAdapter()
        binding.logRecycler.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = logAdapter
        }
    }

    private fun setupButtons() {
        binding.btnRun.setOnClickListener {
            val cmd = binding.commandInput.text.toString().trim()
            if (cmd.isNotEmpty()) {
                viewModel.executeCommand(cmd)
                binding.commandInput.text.clear()
            }
        }

        binding.btnStopCtrlC.setOnClickListener {
            viewModel.stopWithCtrlC()
            Toast.makeText(this, "已发送 Ctrl+C", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopQ.setOnClickListener {
            viewModel.stopWithQuit()
            Toast.makeText(this, "已发送 q (优雅退出)", Toast.LENGTH_SHORT).show()
        }

        binding.btnForceStop.setOnClickListener {
            viewModel.forceStop()
        }

        binding.btnClear.setOnClickListener {
            viewModel.clearLogs()
        }

        binding.btnDir.setOnClickListener {
            dirPicker.launch(null)
        }

        binding.btnPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                binding.commandInput.setText(text)
            }
        }

        // 回车键执行命令
        binding.commandInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                binding.btnRun.performClick()
                true
            } else false
        }
    }

    private fun observeViewModel() {
        // 日志更新
        lifecycleScope.launch {
            viewModel.logs.collect { logList ->
                logAdapter.submitList(logList)
                // 自动滚动到底部
                if (logList.isNotEmpty()) {
                    binding.logRecycler.post {
                        binding.logRecycler.smoothScrollToPosition(logList.size - 1)
                    }
                }
            }
        }

        // 状态更新
        lifecycleScope.launch {
            viewModel.statusText.collect { status ->
                binding.statusText.text = status
            }
        }

        // 进程运行状态
        lifecycleScope.launch {
            viewModel.processRunning.collect { running ->
                binding.btnRun.isEnabled = !running && viewModel.ffmpegReady.value
                binding.btnStopCtrlC.isEnabled = running
                binding.btnStopQ.isEnabled = running
                binding.btnForceStop.isEnabled = running
            }
        }

        // FFmpeg 就绪状态
        lifecycleScope.launch {
            viewModel.ffmpegReady.collect { ready ->
                binding.btnRun.isEnabled = ready && !viewModel.processRunning.value
            }
        }
    }
}
