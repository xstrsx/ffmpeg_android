# FFmpeg Terminal Tool

## 项目概述
FFmpeg Terminal Tool 是一款基于 Android 原生开发的图形化 FFmpeg 命令行工具，旨在为用户提供一个友好的界面来执行 FFmpeg 命令。该应用遵循 Material You 设计规范，支持硬件加速，并提供丰富的功能以满足用户的需求。

## 功能特性
- **命令行交互**：用户可以在应用中输入完整的 FFmpeg 命令，并实时查看输出日志。
- **工作目录选择**：用户首次启动应用时可选择一个本地文件夹作为全局工作目录，后续所有命令将在该目录下执行。
- **进程管理**：支持优雅停止和强制停止 FFmpeg 进程，确保用户能够灵活控制任务执行。
- **剪贴板支持**：用户可以方便地从剪贴板粘贴命令，提升操作效率。
- **硬件加速**：集成了支持 MediaCodec 的 FFmpeg 二进制文件，用户可以利用硬件加速进行视频处理。

## 技术栈
- **开发语言**：Kotlin
- **最低适配版本**：Android 7.0 (API 24)
- **核心依赖**：仅使用 Android 原生 API
- **FFmpeg**：集成了支持硬件加速的 FFmpeg 二进制文件，适配 arm64-v8a 和 armeabi-v7a 架构。

## 文件结构
```
ffmpeg-terminal-tool
├── app
│   ├── src
│   │   └── main
│   │       ├── assets
│   │       │   └── ffmpeg
│   │       │       ├── arm64-v8a
│   │       │       │   └── ffmpeg
│   │       │       └── armeabi-v7a
│   │       │           └── ffmpeg
│   │       ├── java
│   │       │   └── com
│   │       │       └── example
│   │       │           └── ffmpegterm
│   │       │               ├── App.kt
│   │       │               ├── MainActivity.kt
│   │       │               ├── directory
│   │       │               │   └── DirectoryPicker.kt
│   │       │               ├── ffmpeg
│   │       │               │   ├── FFmpegBinaryInstaller.kt
│   │       │               │   └── FFmpegProcessManager.kt
│   │       │               ├── ui
│   │       │               │   ├── TerminalAdapter.kt
│   │       │               │   └── TerminalViewModel.kt
│   │       │               └── clipboard
│   │       │                   └── ClipboardHelper.kt
│   │       └── res
│   │           ├── drawable
│   │           ├── layout
│   │           │   └── activity_main.xml
│   │           ├── mipmap-hdpi
│   │           │   └── ic_launcher.xml
│   │           ├── values
│   │           │   ├── colors.xml
│   │           │   ├── strings.xml
│   │           │   └── themes.xml
│   │           └── values-zh
│   │               └── strings.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── gradle
│   └── wrapper
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradle.properties
├── gradlew
├── gradlew.bat
├── local.properties
├── settings.gradle.kts
└── README.md
```

## 使用说明
1. **启动应用**：首次启动时选择工作目录。
2. **输入命令**：在命令输入框中输入 FFmpeg 命令，点击回车执行。
3. **查看日志**：实时查看终端窗口中的输出日志。
4. **停止进程**：使用 Ctrl+C 或 Q 按钮停止正在运行的 FFmpeg 进程。

## 贡献
欢迎任何形式的贡献！请提交问题或拉取请求以帮助我们改进这个项目。

## 许可证
本项目遵循 MIT 许可证。