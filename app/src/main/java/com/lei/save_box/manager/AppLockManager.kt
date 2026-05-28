package com.lei.save_box.manager

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.lei.save_box.FakeHomeActivity
import com.lei.save_box.LockableActivity

class AppLockManager private constructor(private val app: Application) {

    companion object {
        @Volatile
        private var instance: AppLockManager? = null

        fun init(app: Application): AppLockManager {
            return instance ?: synchronized(this) {
                instance ?: AppLockManager(app).also { instance = it }
            }
        }

        fun getInstance(): AppLockManager {
            return instance ?: throw IllegalStateException("AppLockManager not initialized")
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val lockRunnable = Runnable { lock() }
    private val activeActivities = mutableSetOf<Activity>()

    init {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                activeActivities.add(activity)
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                activeActivities.remove(activity)
            }
        })

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                handler.removeCallbacks(lockRunnable)
            }
            override fun onStop(owner: LifecycleOwner) {
                handler.postDelayed(lockRunnable, 30_000L)
            }
        })
    }

    private fun lock() {
        handler.removeCallbacks(lockRunnable)

        val activities = activeActivities.filter { it !is FakeHomeActivity && !it.isDestroyed }

        for (activity in activities) {
            if (activity is LockableActivity) {
                try { activity.onAppLockCleanup() } catch (_: Exception) {}
            }
        }

        BackgroundTaskManager.getInstance().cancelAllProcessing()

        for (activity in activities) {
            activity.finish()
        }
    }
}
