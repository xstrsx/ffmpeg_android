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
     * 如果已安装且可执行则跳过。
     * 使用 getDir() 确保目标目录支持执行权限。
     */
    fun install(context: Context): Boolean {
        // 已就绪则直接返回
        if (status == Status.READY && installedPath != null) {
            val f = File(installedPath!!)
            if (f.exists() && f.canExecute()) return true
        }

        status = Status.INSTALLING

        return try {
            // 使用 getDir() 创建 app 私有可执行目录
            val targetDir = context.getDir("ffmpeg_bin", Context.MODE_PRIVATE)
            val targetFile = File(targetDir, BINARY_NAME)

            // 如果已有且可执行，无需重新安装
            if (targetFile.exists() && targetFile.canExecute()) {
                installedPath = targetFile.absolutePath
                status = Status.READY
                return true
            }

            // 清理旧文件
            targetFile.delete()

            // 从 assets 复制二进制
            val arch = detectArchitecture()
            val assetName = "$ASSET_PATH/$arch/$BINARY_NAME"
            context.assets.open(assetName).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 设置可执行权限 — 两步确保:
            // 1. Java API
            targetFile.setReadable(true, false)
            targetFile.setExecutable(true, false)

            // 2. Shell chmod 兜底（解决部分设备 setExecutable 无效的问题）
            if (!targetFile.canExecute()) {
                try {
                    val chmod = Runtime.getRuntime().exec(
                        arrayOf("chmod", "755", targetFile.absolutePath)
                    )
                    chmod.waitFor()
                } catch (_: Exception) {
                    // chmod 失败 — 继续尝试，有些设备 Java API 就已足够
                }
            }

            // 最终验证
            if (!targetFile.canExecute()) {
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

