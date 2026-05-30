package com.example.ffmpegterm

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.ffmpegterm.directory.DirectoryPicker
import com.example.ffmpegterm.ui.TerminalFragment

class MainActivity : AppCompatActivity() {

    private lateinit var terminalFragment: TerminalFragment
    private var workingDirectory: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        terminalFragment = TerminalFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, terminalFragment)
            .commit()

        selectWorkingDirectory()
    }

    private fun selectWorkingDirectory() {
        val directoryPicker = DirectoryPicker()
        val getContent = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                workingDirectory = it.toString()
                terminalFragment.setWorkingDirectory(workingDirectory)
            }
        }
        getContent.launch(null)
    }

    fun getWorkingDirectory(): String? {
        return workingDirectory
    }
}