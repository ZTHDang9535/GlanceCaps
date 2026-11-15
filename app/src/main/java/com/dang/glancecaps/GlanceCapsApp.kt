package com.dang.glancecaps

import android.app.Application

class GlanceCapsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        com.dang.glancecaps.i18n.Lang.load(com.dang.glancecaps.data.ModulePrefs(this))
    }
}
