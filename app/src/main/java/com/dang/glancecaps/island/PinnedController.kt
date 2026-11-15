package com.dang.glancecaps.island

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.dang.glancecaps.CrashLog
import com.dang.glancecaps.data.ModulePrefs

object PinnedController {
    private val handler = Handler(Looper.getMainLooper())
    private var shown = false
    private var lastSig = ""
    private var pending: Runnable? = null

    @Synchronized
    fun forget() {
        shown = false
        lastSig = ""
    }

    @Synchronized
    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        try {
            val p = ModulePrefs(app)
            val nm = app.getSystemService(NotificationManager::class.java)
            val want = p.pinnedEnabled &&
                GlanceCapsNotifications.useHyper(p) &&
                !ConnectivityMonitor.showing() &&
                !IslandWatcher.active
            if (!want) {
                if (shown) nm.cancel(GlanceCapsNotifications.ID_PINNED)
                forget()
                return
            }
            val sig = GlanceCapsNotifications.pinnedSignature(app, p)
            if (shown && sig == lastSig) return
            IslandPoster.post(app, GlanceCapsNotifications.ID_PINNED, GlanceCapsNotifications.pinned(app, p))
            shown = true
            lastSig = sig
        } catch (e: Throwable) {
            CrashLog.append(app, "pinned.refresh", e)
        }
    }

    fun refreshSoon(ctx: Context, delayMs: Long) {
        val app = ctx.applicationContext
        pending?.let { handler.removeCallbacks(it) }
        val task = Runnable { refresh(app) }
        pending = task
        handler.postDelayed(task, delayMs)
    }
}
