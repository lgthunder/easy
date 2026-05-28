package com.lei.save_box

import android.app.Application
import com.lei.save_box.manager.AppLockManager
import com.lei.save_box.manager.BackgroundTaskManager

class SaveBoxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        BackgroundTaskManager.init(this)
        AppLockManager.init(this)
    }
}
