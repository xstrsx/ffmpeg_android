package com.example.ffmpegterm.ffmpeg

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * 管理 FFmpeg 二进制文件的安装。
 * 首次启动时从 APK 内置 assets 中提取到内部存储，无需联网。
 */
object FFmpegBinaryInstaller {

    private const val BINARY_NAME = "ffmpeg"
    private const val ASSET_PATH = "ffmpeg"

    /** 已安装的 ffmpeg 二进制路径，null 表示尚未安装 */
    @Volatile
    var installedPath: String? = null
        private set

    /** 安装状态 */
    enum class Status { NOT_INSTALLED, INSTALLING, READY, FAILED }

    @Volatile
    var status: Status = Status.NOT_INSTALLED
        private set

    /**
     * 将 ffmpeg 二进制从 assets 安装到内部存储。
     * 如果已安装则跳过。
     */
    fun install(context: Context): Boolean {
        if (status == Status.READY && installedPath != null) {
            val f = File(installedPath!!)
            if (f.exists() && f.canExecute()) return true
        }

        status = Status.INSTALLING

        return try {
            val arch = detectArchitecture()
            val targetDir = File(context.filesDir, "ffmpeg_bin")
            if (!targetDir.exists()) targetDir.mkdirs()

            val targetFile = File(targetDir, BINARY_NAME)

            // 如果已存在且可执行，跳过复制
            if (targetFile.exists() && targetFile.canExecute()) {
                installedPath = targetFile.absolutePath
                status = Status.READY
                return true
            }

            // 从 assets 复制
            val assetName = "$ASSET_PATH/$arch/$BINARY_NAME"
            context.assets.open(assetName).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 设置可执行权限
            if (!targetFile.setExecutable(true, false)) {
                status = Status.FAILED
                return false
            }

            installedPath = targetFile.absolutePath
            status = Status.READY
            true
        } catch (e: Exception) {
            e.printStackTrace()
            status = Status.FAILED
            false
        }
    }

    private fun detectArchitecture(): String {
        val abis = android.os.Build.SUPPORTED_ABIS
        for (abi in abis) {
            when {
                abi.startsWith("arm64") -> return "arm64-v8a"
                abi.startsWith("armeabi") -> return "armeabi-v7a"
                abi.startsWith("x86_64") -> return "x86_64"
                abi.startsWith("x86") -> return "x86"
            }
        }
        throw UnsupportedOperationException(
            "Unsupported CPU architecture: ${abis.joinToString()}"
        )
    }
}
