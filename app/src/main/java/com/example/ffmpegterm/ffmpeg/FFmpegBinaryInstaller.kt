package com.example.ffmpegterm.ffmpeg

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * 管理 FFmpeg 二进制文件的定位与安装。
 *
 * 策略（按优先级）：
 * 1. jniLibs 原生库目录 — 系统自动解压到此，保证可执行（Android 16+ 推荐）
 * 2. assets 复制到 getDir() — 兼容旧版，需 chmod
 */
object FFmpegBinaryInstaller {

    private const val BINARY_NAME = "ffmpeg"
    private const val JNI_BINARY_NAME = "libffmpeg_exec.so"
    private const val ASSET_PATH = "ffmpeg"

    @Volatile
    var installedPath: String? = null
        private set

    enum class Status { NOT_INSTALLED, INSTALLING, READY, FAILED }

    @Volatile
    var status: Status = Status.NOT_INSTALLED
        private set

    fun install(context: Context): Boolean {
        if (status == Status.READY && installedPath != null) {
            val f = File(installedPath!!)
            if (f.exists() && f.canExecute()) return true
        }
        status = Status.INSTALLING

        // 方案1: 从 jniLibs 原生库目录加载（Android 系统保证可执行）
        val nativePath = findInNativeLibDir(context)
        if (nativePath != null) {
            installedPath = nativePath
            status = Status.READY
            return true
        }

        // 方案2: 从 assets 复制到 getDir()
        return installFromAssets(context)
    }

    /** 在原生库目录中查找 ffmpeg 二进制 */
    private fun findInNativeLibDir(context: Context): String? {
        return try {
            val libDir = context.applicationInfo.nativeLibraryDir
            val file = File(libDir, JNI_BINARY_NAME)
            if (file.exists() && file.canExecute() && isElfFile(file)) {
                file.absolutePath
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /** 从 assets 复制二进制文件 */
    private fun installFromAssets(context: Context): Boolean {
        return try {
            val targetDir = context.getDir("ffmpeg_bin", Context.MODE_PRIVATE)
            targetDir.setReadable(true, false)
            targetDir.setExecutable(true, false)

            val targetFile = File(targetDir, BINARY_NAME)
            if (targetFile.exists() && targetFile.canExecute()) {
                installedPath = targetFile.absolutePath
                status = Status.READY
                return true
            }

            targetFile.delete()

            val arch = detectArchitecture()
            val assetName = "$ASSET_PATH/$arch/$BINARY_NAME"
            context.assets.open(assetName).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.length() < 1024 || !isElfFile(targetFile)) {
                targetFile.delete()
                status = Status.FAILED
                return false
            }

            // 权限：Java API + shell chmod 双保险
            targetFile.setReadable(true, false)
            targetFile.setExecutable(true, false)

            try {
                val p = Runtime.getRuntime().exec(arrayOf("chmod", "755", targetFile.absolutePath))
                p.waitFor()
            } catch (_: Exception) {}

            if (!targetFile.canExecute()) {
                try {
                    val p = Runtime.getRuntime().exec(arrayOf("chmod", "-R", "755", targetDir.absolutePath))
                    p.waitFor()
                } catch (_: Exception) {}
            }

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
        for (abi in android.os.Build.SUPPORTED_ABIS) {
            when {
                abi.startsWith("arm64") -> return "arm64-v8a"
                abi.startsWith("armeabi") -> return "armeabi-v7a"
                abi.startsWith("x86_64") -> return "x86_64"
                abi.startsWith("x86") -> return "x86"
            }
        }
        throw UnsupportedOperationException(
            "Unsupported CPU architecture: ${android.os.Build.SUPPORTED_ABIS.joinToString()}"
        )
    }

    /** 检查文件是否为有效的 ELF 二进制（魔数 0x7F 'E' 'L' 'F'） */
    private fun isElfFile(file: File): Boolean {
        return try {
            file.inputStream().use {
                val magic = ByteArray(4)
                if (it.read(magic) == 4) {
                    magic[0] == 0x7f.toByte() &&
                        magic[1] == 'E'.code.toByte() &&
                        magic[2] == 'L'.code.toByte() &&
                        magic[3] == 'F'.code.toByte()
                } else false
            }
        } catch (_: Exception) {
            false
        }
    }
}


