package com.dang.glancecaps.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.dang.glancecaps.CrashLog
import com.dang.glancecaps.data.ModulePrefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class UpdateInfo(val version: String, val url: String, val notes: String)

enum class CheckResult { NEW, LATEST, FAILED }

object UpdateChecker {
    private const val API = "https://api.github.com/repos/ZTHDang9535/GlanceCaps/releases/latest"
    private const val FALLBACK_URL = "https://github.com/ZTHDang9535/GlanceCaps/releases/latest"
    private const val INTERVAL_MS = 6 * 60 * 60 * 1000L

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false

    fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Throwable) {
        ""
    }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").split('.', '-', '+')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    fun isNewer(candidate: String, current: String): Boolean {
        if (candidate.isBlank() || current.isBlank()) return false
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun available(ctx: Context, p: ModulePrefs): UpdateInfo? {
        if (!p.updateCheck || p.updateVersion.isBlank()) return null
        if (!isNewer(p.updateVersion, currentVersion(ctx))) return null
        return UpdateInfo(p.updateVersion, p.updateUrl.ifBlank { FALLBACK_URL }, p.updateNotes)
    }

    fun refresh(ctx: Context, p: ModulePrefs, force: Boolean = false, onDone: ((CheckResult) -> Unit)? = null) {
        val now = System.currentTimeMillis()
        if (!force && (!p.updateCheck || now - p.updateCheckedAt < INTERVAL_MS)) return
        if (running) {
            if (onDone != null) main.post { onDone(CheckResult.FAILED) }
            return
        }
        running = true
        val app = ctx.applicationContext
        executor.execute {
            val result = try {
                fetch(app, ModulePrefs(app))
            } catch (e: java.io.IOException) {
                CheckResult.FAILED
            } catch (e: Throwable) {
                CrashLog.append(app, "update.check", e)
                CheckResult.FAILED
            } finally {
                running = false
            }
            if (onDone != null) main.post { onDone(result) }
        }
    }

    private fun fetch(ctx: Context, p: ModulePrefs): CheckResult {
        val c = URL(API).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "GlanceCaps/" + currentVersion(ctx) + " github.com/ZTHDang9535/GlanceCaps")
            val code = c.responseCode
            if (code == 404) {
                p.updateCheckedAt = System.currentTimeMillis()
                return CheckResult.LATEST
            }
            if (code != 200) return CheckResult.FAILED
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").trim().removePrefix("v").removePrefix("V")
            if (tag.isBlank()) return CheckResult.FAILED
            p.updateVersion = tag
            p.updateUrl = json.optString("html_url").ifBlank { FALLBACK_URL }
            p.updateNotes = json.optString("body").trim().take(600)
            p.updateCheckedAt = System.currentTimeMillis()
            return if (isNewer(tag, currentVersion(ctx))) CheckResult.NEW else CheckResult.LATEST
        } finally {
            c.disconnect()
        }
    }
}
