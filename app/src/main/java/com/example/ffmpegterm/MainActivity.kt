package com.example.ffmpegterm

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    // 存储权限请求
    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val allGranted = grants.values.all { it }
            if (!allGranted) {
                Toast.makeText(this, "需要存储权限才能访问媒体文件", Toast.LENGTH_LONG).show()
            }
        }

    // 全文件访问权限（Android 11+ 跳转设置）
    private val manageStorageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "已获得全文件访问权限", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val dirPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                // 尝试将 content URI 转换为实际路径
                val path = resolveContentUri(it)
                viewModel.setWorkingDirectory(path)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[TerminalViewModel::class.java]

        requestNeededPermissions()
        setupLogList()
        setupButtons()
        observeViewModel()
    }

    /** 请求必要的运行时权限 */
    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()

        // Android 13+ 细分媒体权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.READ_MEDIA_VIDEO)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            // Android 12 及以下
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // Android 13+ 通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (needed.isNotEmpty()) {
            storagePermissionLauncher.launch(needed.toTypedArray())
        }

        // Android 11+ 全文件访问：引导用户去设置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(
                    this,
                    "提示：前往设置 → 特殊应用权限 → 所有文件访问 开启后可访问全部文件",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** 将 SAF content URI 转为可读路径 */
    private fun resolveContentUri(uri: Uri): String {
        val docId = uri.lastPathSegment ?: return uri.toString()
        // content://com.android.externalstorage.documents/tree/primary%3Affmpeg
        // → /storage/emulated/0/ffmpeg
        return when {
            docId.startsWith("primary:") -> {
                "/storage/emulated/0/" + docId.removePrefix("primary:")
            }
            docId.startsWith("home:") -> {
                "/storage/emulated/0/" + docId.removePrefix("home:")
            }
            else -> uri.toString()
        }
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
