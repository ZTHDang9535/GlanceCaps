package com.dang.glancecaps.island

import android.media.session.PlaybackState
import android.media.session.MediaSessionManager
import android.media.session.MediaController
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dang.glancecaps.CrashLog
import com.dang.glancecaps.call.CallParser
import com.dang.glancecaps.call.CallTracker
import com.dang.glancecaps.data.ModulePrefs

class IslandListenerService : NotificationListenerService() {
    private var sessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val callbacks = HashMap<MediaController, MediaController.Callback>()

    private fun watchMedia() {
        try {
            val msm = getSystemService(MediaSessionManager::class.java) ?: return
            val component = ComponentName(this, IslandListenerService::class.java)
            val listener = MediaSessionManager.OnActiveSessionsChangedListener { list -> bindControllers(list.orEmpty()) }
            sessionListener = listener
            msm.addOnActiveSessionsChangedListener(listener, component)
            bindControllers(msm.getActiveSessions(component))
        } catch (e: Throwable) {
            CrashLog.append(this, "listener.media", e)
        }
    }

    private fun unwatchMedia() {
        try {
            sessionListener?.let { getSystemService(MediaSessionManager::class.java)?.removeOnActiveSessionsChangedListener(it) }
        } catch (e: Throwable) {
        }
        sessionListener = null
        callbacks.forEach { (c, cb) ->
            try {
                c.unregisterCallback(cb)
            } catch (e: Throwable) {
            }
        }
        callbacks.clear()
    }

    private fun bindControllers(list: List<MediaController>) {
        callbacks.forEach { (c, cb) ->
            try {
                c.unregisterCallback(cb)
            } catch (e: Throwable) {
            }
        }
        callbacks.clear()
        for (c in list) {
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    updateMedia(list)
                }
            }
            c.registerCallback(cb)
            callbacks[c] = cb
        }
        updateMedia(list)
    }

    private fun updateMedia(list: List<MediaController>) {
        val playing = list.any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        if (IslandWatcher.setMedia(playing)) PinnedController.refreshSoon(this, if (playing) 0L else 500L)
    }

    override fun onListenerConnected() {
        try {
            val list = activeNotifications
            IslandWatcher.scan(list, packageName)
            watchMedia()
            list?.forEach { handleCall(it) }
            PinnedController.refresh(this)
        } catch (e: Throwable) {
            CrashLog.append(this, "listener.connect", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val changed = IslandWatcher.posted(sbn, packageName)
        handleCall(sbn)
        if (changed) PinnedController.refreshSoon(this, 0L)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val changed = IslandWatcher.removed(sbn)
        CallTracker.end(this, sbn.key)
        if (changed) PinnedController.refreshSoon(this, 500L)
    }

    override fun onListenerDisconnected() {
        unwatchMedia()
        IslandWatcher.clear()
        CallTracker.end(this)
        PinnedController.refreshSoon(this, 0L)
    }

    private fun handleCall(sbn: StatusBarNotification) {
        try {
            val p = ModulePrefs(this)
            if (!p.callEnabled) return
            if (!p.callAppAllowed(sbn.packageName)) return
            val info = CallParser.parse(this, sbn, p.callPhoneApp) ?: return
            CallTracker.accept(this, info)
        } catch (e: Throwable) {
            CrashLog.append(this, "call.listener", e)
        }
    }
}
