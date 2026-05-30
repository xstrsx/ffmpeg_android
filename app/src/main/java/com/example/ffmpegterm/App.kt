package com.example.ffmpegterm

import android.app.Application

class App : Application() {
    // FFmpeg 由 TerminalViewModel 在首次需要时初始化
    // 不在此处初始化，避免阻塞启动
}
