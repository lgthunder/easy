package com.lei.save_box

import android.app.Application
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.lei.save_box.manager.AppLockManager
import com.lei.save_box.manager.BackgroundTaskManager

class SaveBoxApp : Application() {
    companion object{
        var APP : Application? = null
    }

    override fun onCreate() {
        super.onCreate()
        BackgroundTaskManager.init(this)
        AppLockManager.init(this)
        
        // 初始化 FFmpegKit
        val session = FFmpegKit.execute("-version")
        Log.d("FFmpeg", "FFmpeg initialized: ${session.returnCode}")
        APP = this;
    }
}
