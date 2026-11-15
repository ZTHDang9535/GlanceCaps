package com.dang.glancecaps.island

import android.service.notification.StatusBarNotification

object IslandWatcher {
    private val keys = HashSet<String>()

    @Volatile
    var active = false
        private set

    @Volatile
    private var media = false

    private fun isIsland(sbn: StatusBarNotification, own: String): Boolean {
        if (sbn.packageName == own && sbn.id == GlanceCapsNotifications.ID_PINNED) return false
        val raw = try {
            sbn.notification.extras?.getString("miui.focus.param")
        } catch (e: Throwable) {
            null
        } ?: return false
        return raw.contains("param_island")
    }

    @Synchronized
    fun scan(list: Array<StatusBarNotification>?, own: String) {
        keys.clear()
        list?.forEach { if (isIsland(it, own)) keys.add(it.key) }
        active = media || keys.isNotEmpty()
    }

    @Synchronized
    fun posted(sbn: StatusBarNotification, own: String): Boolean {
        val before = active
        if (isIsland(sbn, own)) keys.add(sbn.key) else keys.remove(sbn.key)
        active = media || keys.isNotEmpty()
        return before != active
    }

    @Synchronized
    fun removed(sbn: StatusBarNotification): Boolean {
        val before = active
        keys.remove(sbn.key)
        active = media || keys.isNotEmpty()
        return before != active
    }

    @Synchronized
    fun setMedia(on: Boolean): Boolean {
        val before = active
        media = on
        active = media || keys.isNotEmpty()
        return before != active
    }

    @Synchronized
    fun clear() {
        keys.clear()
        media = false
        active = false
    }
}
