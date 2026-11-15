package com.dang.glancecaps.island

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

object IslandMarquee {
    private const val CAPACITY = 16
    private const val START_HOLD_MS = 1200L
    private const val STEP_MS = 320L
    private const val END_HOLD_MS = 2600L
    private const val FIG = "\u2007"
    private const val PARAM = "miui.focus.param"
    private val KEYS = listOf("imageTextInfoLeft", "imageTextInfoRight")

    private val handler = Handler(Looper.getMainLooper())
    private val running = HashMap<Int, Runnable>()

    class Plan(val root: JSONObject, val sides: List<Pair<JSONObject, String>>) {
        val steps: Int = sides.maxOf { it.second.length } - CAPACITY
    }

    private fun bigArea(root: JSONObject): JSONObject? = root.optJSONObject("param_v2")
        ?.optJSONObject("param_island")
        ?.optJSONObject("bigIslandArea")

    private fun longSides(root: JSONObject): List<Pair<JSONObject, String>> {
        val big = bigArea(root) ?: return emptyList()
        val out = ArrayList<Pair<JSONObject, String>>()
        for (key in KEYS) {
            val info = big.optJSONObject(key)?.optJSONObject("textInfo") ?: continue
            val full = info.optString("title").replace(FIG, "").trim()
            if (full.length > CAPACITY) out.add(info to full)
        }
        return out
    }

    fun totalMs(json: String): Long {
        return try {
            val sides = longSides(JSONObject(json))
            if (sides.isEmpty()) 0L else {
                val steps = sides.maxOf { it.second.length } - CAPACITY
                START_HOLD_MS + steps * STEP_MS + END_HOLD_MS
            }
        } catch (e: Throwable) {
            0L
        }
    }

    private fun frame(n: Notification, plan: Plan, offset: Int) {
        for ((info, full) in plan.sides) {
            val o = offset.coerceIn(0, full.length - CAPACITY)
            info.put("title", full.substring(o, o + CAPACITY))
        }
        n.extras.putString(PARAM, plan.root.toString())
    }

    fun prepare(n: Notification): Plan? {
        return try {
            val raw = n.extras.getString(PARAM) ?: return null
            val root = JSONObject(raw)
            val sides = longSides(root)
            if (sides.isEmpty()) return null
            val plan = Plan(root, sides)
            frame(n, plan, 0)
            plan
        } catch (e: Throwable) {
            null
        }
    }

    fun begin(ctx: Context, id: Int, n: Notification, plan: Plan?) {
        handler.post {
            stop(id)
            if (plan != null) schedule(ctx, id, n, plan)
        }
    }

    fun stop(id: Int) {
        running.remove(id)?.let { handler.removeCallbacks(it) }
    }

    private fun schedule(ctx: Context, id: Int, n: Notification, plan: Plan) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val task = object : Runnable {
            var offset = 0
            override fun run() {
                try {
                    val alive = nm.activeNotifications.any { it.id == id }
                    if (!alive) {
                        running.remove(id)
                        return
                    }
                    if (offset >= plan.steps) {
                        running.remove(id)
                        if (n.timeoutAfter <= 0L) {
                            frame(n, plan, 0)
                            nm.notify(id, n)
                        }
                        return
                    }
                    offset++
                    frame(n, plan, offset)
                    nm.notify(id, n)
                    handler.postDelayed(this, if (offset >= plan.steps) END_HOLD_MS else STEP_MS)
                } catch (e: Throwable) {
                    running.remove(id)
                }
            }
        }
        running[id] = task
        handler.postDelayed(task, START_HOLD_MS)
    }
}
