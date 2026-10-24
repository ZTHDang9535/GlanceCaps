package com.dang.glancecaps.match

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.dang.glancecaps.CrashLog
import com.dang.glancecaps.data.ModulePrefs
import java.util.concurrent.Executors

object MatchTracker {
    @Volatile var current: MatchInfo? = null
    @Volatile var home: Bitmap? = null
    @Volatile var away: Bitmap? = null
    @Volatile var pair: Bitmap? = null
    @Volatile private var busy = false
    @Volatile private var lastFetch = 0L
    @Volatile private var trackedId = ""
    private val pool = Executors.newSingleThreadExecutor()

    fun reset() {
        current = null
        home = null
        away = null
        pair = null
        lastFetch = 0L
        trackedId = ""
    }

    fun refresh(ctx: Context, p: ModulePrefs) {
        val id = p.matchId
        if (id != trackedId) {
            reset()
            trackedId = id
        }
        val now = SystemClock.elapsedRealtime()
        val metered = id.startsWith("a:")
        val interval = when (current?.state) {
            "in" -> if (metered) 120_000L else 20_000L
            "pre" -> if (metered) 600_000L else 60_000L
            else -> if (metered) 900_000L else 120_000L
        }
        if (busy || now - lastFetch < interval) return
        busy = true
        val apiKey = p.matchApiKey
        val seedLeague = p.matchLeagueName
        val seedKickoff = p.matchKickoff.toLongOrNull() ?: 0L
        pool.execute {
            try {
                val found = MatchApi.fetchOne(id, apiKey)?.withMeta(seedLeague, seedKickoff)
                if (found != null) {
                    val previous = current
                    current = found
                    val needLogos = home == null || away == null ||
                        previous == null || previous.home.logo != found.home.logo || previous.away.logo != found.away.logo
                    if (needLogos) {
                        home = MatchLogos.load(found.home.logo)
                        away = MatchLogos.load(found.away.logo)
                        pair = MatchLogos.pair(home, away)
                    }
                }
            } catch (e: Throwable) {
                CrashLog.append(ctx, "match.refresh", e)
            } finally {
                lastFetch = SystemClock.elapsedRealtime()
                busy = false
            }
        }
    }
}
