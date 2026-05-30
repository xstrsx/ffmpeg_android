package com.example.ffmpegterm.directory

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class DirectoryPicker(private val activity: AppCompatActivity) {

    private lateinit var directoryResultLauncher: ActivityResultLauncher<Intent>
    var selectedDirectory: String? = null

    init {
        registerDirectoryPicker()
    }

    private fun registerDirectoryPicker() {
        directoryResultLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = result.data?.data
                uri?.let {
                    selectedDirectory = getPathFromUri(it)
                    Toast.makeText(activity, "Selected Directory: $selectedDirectory", Toast.LENGTH_SHORT).show()
                } ?: run {
                    Toast.makeText(activity, "Directory selection failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun pickDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        directoryResultLauncher.launch(intent)
    }

    private fun getPathFromUri(uri: Uri): String? {
        val documentId = DocumentsContract.getDocumentId(uri)
        val split = documentId.split(":")
        val type = split[0]

        return if ("primary" == type) {
            "${activity.getExternalFilesDir(null)?.absolutePath}/${split[1]}"
        } else {
            null // Handle non-primary volumes if needed
        }
    }
}