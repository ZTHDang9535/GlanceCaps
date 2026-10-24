package com.dang.glancecaps.island

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.match.MatchTracker

class MatchCloseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ModulePrefs(context).matchEnabled = false
        MatchTracker.reset()
        context.getSystemService(NotificationManager::class.java).cancel(GlanceCapsNotifications.ID_MATCH)
        GlanceCapsService.sync(context)
    }

    companion object {
        const val ACTION = "com.dang.glancecaps.CLOSE_MATCH"
    }
}
