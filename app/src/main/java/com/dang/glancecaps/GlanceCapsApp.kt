package com.dang.glancecaps

import android.app.Application

class GlanceCapsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
