package com.palmlink.app

import android.app.Application

class PalmApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            CrashLog.write(filesDir, "未捕获异常 @ ${thread.name}", e)
            prev?.uncaughtException(thread, e)
        }
    }
}
