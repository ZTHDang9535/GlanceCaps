package com.dang.glancecaps.island

import org.json.JSONObject
import android.os.Looper
import android.os.Handler
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import com.dang.glancecaps.CrashLog
import com.dang.glancecaps.data.ModulePrefs
import java.util.concurrent.Executors

object IslandPoster {
    private const val MIN_GAP_MS = 20_000L
    private const val SETTLE_MS = 700L
    private const val NETWORK_SETTLE_MS = 1500L

    private val pending = LinkedHashMap<Int, Notification>()
    private val lastBypass = HashMap<Int, Long>()
    private val deferred = HashMap<Int, Notification>()
    private val worker = Executors.newSingleThreadExecutor()
    private const val REASSERT_MS = 30 * 60 * 1000L
    private const val WATCH_MS = 60_000L
    private const val GAP_MS = 120_000L

    private class Entry(var n: Notification, var assertedAt: Long, var lastPost: Long)

    private val tracked = HashMap<Int, Entry>()
    private val handler = Handler(Looper.getMainLooper())
    private var watching = false
    private var scheduled = false

    fun post(ctx: Context, id: Int, n: Notification) {
        val app = ctx.applicationContext
        val plan = if (bypassOn(app)) null else IslandMarquee.prepare(n)
        track(id, n)
        send(app, id, n)
        IslandMarquee.begin(app, id, n, plan)
        startWatch(app)
    }

    fun lastPostAt(id: Int): Long = synchronized(tracked) { tracked[id]?.lastPost ?: 0L }

    private fun track(id: Int, n: Notification) {
        if (!n.extras.containsKey("miui.focus.param")) return
        val now = SystemClock.elapsedRealtime()
        synchronized(tracked) {
            val e = tracked[id]
            if (e == null || now - e.lastPost > GAP_MS) {
                tracked[id] = Entry(n, now, now)
            } else {
                e.n = n
                e.lastPost = now
            }
        }
    }

    private fun startWatch(ctx: Context) {
        if (watching) return
        watching = true
        handler.postDelayed(object : Runnable {
            override fun run() {
                reassertDue(ctx)
                handler.postDelayed(this, WATCH_MS)
            }
        }, WATCH_MS)
    }

    private fun reassertDue(ctx: Context) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val active = nm.activeNotifications.map { it.id }.toSet()
            val now = SystemClock.elapsedRealtime()
            val due = ArrayList<Pair<Int, Notification>>()
            synchronized(tracked) {
                val it = tracked.entries.iterator()
                while (it.hasNext()) {
                    val (id, e) = it.next()
                    if (id !in active) {
                        it.remove()
                    } else if (now - e.assertedAt >= REASSERT_MS) {
                        e.assertedAt = now
                        due.add(id to e.n)
                    }
                }
            }
            for ((id, n) in due) reassert(ctx, id, n)
        } catch (e: Throwable) {
            CrashLog.append(ctx, "poster.reassert", e)
        }
    }

    private fun reassert(ctx: Context, id: Int, n: Notification) {
        val copy = n.clone()
        val raw = copy.extras.getString("miui.focus.param")
        if (raw != null) {
            val root = JSONObject(raw)
            root.optJSONObject("param_v2")?.put("islandFirstFloat", false)?.put("enableFloat", false)
            copy.extras.putString("miui.focus.param", root.toString())
        }
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
        handler.postDelayed({ send(ctx, id, copy) }, 400L)
    }

    private fun bypassOn(ctx: Context): Boolean {
        val p = ModulePrefs(ctx)
        return p.shizukuWorkaround && !p.xmsfUnsupported && GlanceCapsNotifications.useHyper(p) &&
            ShizukuWorkaround.state() == ShizukuWorkaround.READY
    }

    private fun isActive(nm: NotificationManager, id: Int): Boolean = try {
        nm.activeNotifications.any { it.id == id }
    } catch (e: Throwable) {
        false
    }

    private fun send(ctx: Context, id: Int, n: Notification) {
        val app = ctx.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        if (!bypassOn(app)) {
            nm.notify(id, n)
            return
        }
        val update = isActive(nm, id)
        val now = SystemClock.elapsedRealtime()
        var wait = 0L
        synchronized(pending) {
            val remaining = (lastBypass[id] ?: 0L) + MIN_GAP_MS - now
            if (update && remaining > 0L && !pending.containsKey(id)) {
                val queued = deferred.containsKey(id)
                deferred[id] = n
                if (queued) return
                wait = remaining
            } else {
                deferred.remove(id)
                lastBypass[id] = now
                pending[id] = n
                if (scheduled) return
                scheduled = true
            }
        }
        if (wait > 0L) {
            handler.postDelayed({ flushDeferred(app, id) }, wait)
            return
        }
        worker.execute { runBatch(app) }
    }

    private fun flushDeferred(ctx: Context, id: Int) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val alive = isActive(nm, id)
        val start = synchronized(pending) {
            val n = deferred.remove(id) ?: return
            if (!alive) return
            lastBypass[id] = SystemClock.elapsedRealtime()
            pending[id] = n
            if (scheduled) {
                false
            } else {
                scheduled = true
                true
            }
        }
        if (start) worker.execute { runBatch(ctx) }
    }

    private fun runBatch(ctx: Context) {
        try {
            Thread.sleep(150)
            val batch = synchronized(pending) {
                val copy = LinkedHashMap(pending)
                pending.clear()
                scheduled = false
                copy
            }
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val p = ModulePrefs(ctx)
            p.xmsfPending = true
            val method = ShizukuWorkaround.block(ctx)
            try {
                batch.forEach { (id, n) -> nm.notify(id, n) }
                if (method == ShizukuWorkaround.METHOD_NETWORK) Thread.sleep(NETWORK_SETTLE_MS)
                else if (method != ShizukuWorkaround.METHOD_NONE) Thread.sleep(SETTLE_MS)
            } finally {
                if (method != ShizukuWorkaround.METHOD_NONE) {
                    if (ShizukuWorkaround.unblock(ctx, method)) p.xmsfPending = false
                } else {
                    p.xmsfPending = false
                }
            }
        } catch (e: Throwable) {
            CrashLog.append(ctx, "poster.batch", e)
            synchronized(pending) { scheduled = false }
        }
    }
}
