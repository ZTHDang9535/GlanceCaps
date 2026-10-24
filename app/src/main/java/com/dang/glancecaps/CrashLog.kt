package com.dang.glancecaps

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {
    private const val FILE = "diagnostics.txt"
    private const val MAX_CHARS = 24_000

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, FILE)

    @Synchronized
    fun append(ctx: Context, tag: String, t: Throwable?) {
        try {
            val sw = StringWriter()
            t?.let { it.printStackTrace(PrintWriter(sw)) }
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val entry = buildString {
                append("=== ").append(tag).append("  ").append(stamp).append("\n")
                append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                append("  Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                append(sw.toString().take(6000)).append("\n")
            }
            val f = file(ctx)
            val old = if (f.exists()) f.readText() else ""
            f.writeText((old + entry).takeLast(MAX_CHARS))
        } catch (e: Throwable) {

        }
    }

    fun read(ctx: Context): String? = try {
        val f = file(ctx)
        if (f.exists() && f.length() > 0) f.readText() else null
    } catch (e: Throwable) { null }

    fun clear(ctx: Context) {
        try { file(ctx).delete() } catch (e: Throwable) { }
    }

    fun install(app: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            append(app, "CRASH on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
