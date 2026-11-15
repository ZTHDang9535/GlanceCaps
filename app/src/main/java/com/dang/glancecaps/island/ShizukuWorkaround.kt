package com.dang.glancecaps.island

import android.content.Context
import android.content.pm.PackageManager
import com.dang.glancecaps.CrashLog
import com.dang.glancecaps.data.ModulePrefs
import rikka.shizuku.Shizuku

object ShizukuWorkaround {
    const val NOT_RUNNING = 0
    const val NEEDS_PERMISSION = 1
    const val READY = 2

    const val METHOD_NONE = 0
    const val METHOD_DISABLE = 1
    const val METHOD_SUSPEND = 2
    const val METHOD_NETWORK = 3

    private const val REQUEST_CODE = 4201
    private const val XMSF = "com.xiaomi.xmsf"

    @Volatile private var logged = false

    fun state(): Int = try {
        when {
            !Shizuku.pingBinder() -> NOT_RUNNING
            Shizuku.isPreV11() -> NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> NEEDS_PERMISSION
            else -> READY
        }
    } catch (e: Throwable) {
        NOT_RUNNING
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Throwable) { }
    }

    private fun exec(command: String): Pair<Int, String> {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        val out = process.inputStream.bufferedReader().readText() + process.errorStream.bufferedReader().readText()
        return process.waitFor() to out.trim()
    }

    private fun blockCommand(method: Int): String = when (method) {
        METHOD_NETWORK -> "cmd connectivity set-chain3-enabled true && cmd connectivity set-package-networking-enabled false $XMSF"
        METHOD_SUSPEND -> "pm suspend --user 0 $XMSF"
        else -> "pm disable-user --user 0 $XMSF"
    }

    private fun restoreCommand(method: Int): String = when (method) {
        METHOD_NETWORK -> "cmd connectivity set-package-networking-enabled true $XMSF"
        METHOD_SUSPEND -> "pm unsuspend --user 0 $XMSF"
        else -> "pm enable --user 0 $XMSF"
    }

    private fun networkBlocked(): Boolean {
        val (code, out) = exec("cmd connectivity get-package-networking-enabled $XMSF")
        return code != 0 || out.contains("false", ignoreCase = true) || !out.contains("true", ignoreCase = true)
    }

    private fun note(ctx: Context, e: Throwable) {
        if (logged) return
        logged = true
        CrashLog.append(ctx, "shizuku.xmsf", e)
    }

    fun block(ctx: Context): Int {
        val p = ModulePrefs(ctx)
        if (p.xmsfUnsupported) return METHOD_NONE
        val all = listOf(METHOD_NETWORK, METHOD_DISABLE, METHOD_SUSPEND)
        val order = if (p.xmsfMethod in all) listOf(p.xmsfMethod) + all.filter { it != p.xmsfMethod } else all
        val failures = ArrayList<String>()
        for (method in order) {
            try {
                val (code, out) = exec(blockCommand(method))
                if (code == 0 && (method != METHOD_NETWORK || networkBlocked())) {
                    p.xmsfMethod = method
                    return method
                }
                if (code == 0) exec(restoreCommand(method))
                failures.add("method $method exit $code: $out")
            } catch (e: Throwable) {
                failures.add("method $method " + e.javaClass.simpleName + ": " + e.message)
            }
        }
        p.xmsfUnsupported = true
        note(ctx, IllegalStateException("xmsf cannot be blocked on this ROM\n" + failures.joinToString("\n")))
        return METHOD_NONE
    }

    fun unblock(ctx: Context, method: Int): Boolean {
        return try {
            val (code, out) = exec(restoreCommand(method))
            if (code != 0) note(ctx, IllegalStateException("restore exit $code: $out"))
            code == 0
        } catch (e: Throwable) {
            note(ctx, e)
            false
        }
    }

    fun restoreAll(ctx: Context): Boolean {
        val network = unblock(ctx, METHOD_NETWORK)
        val enabled = unblock(ctx, METHOD_DISABLE)
        val resumed = unblock(ctx, METHOD_SUSPEND)
        return network || enabled || resumed
    }

    fun recover(ctx: Context) {
        val p = ModulePrefs(ctx)
        if (!p.xmsfPending) return
        if (state() != READY) return
        if (restoreAll(ctx)) p.xmsfPending = false
    }
}
