package com.dang.glancecaps.island

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings

data class IslandStatus(val protocol: Int, val islandFlag: Boolean, val focusPermission: Boolean)

object IslandSupport {
    fun status(ctx: Context) = IslandStatus(protocolVersion(ctx), islandFlag(), hasFocusPermission(ctx))

    private fun protocolVersion(ctx: Context): Int = try {
        Settings.System.getInt(ctx.contentResolver, "notification_focus_protocol", 0)
    } catch (e: Exception) { 0 }

    private fun islandFlag(): Boolean = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val m = clazz.getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
        m.invoke(null, "persist.sys.feature.island", false) as? Boolean ?: false
    } catch (e: Exception) { false }

    private fun hasFocusPermission(ctx: Context): Boolean = try {
        val uri = Uri.parse("content://miui.statusbar.notification.public")
        val extras = Bundle().apply { putString("package", ctx.packageName) }
        val b = ctx.contentResolver.call(uri, "canShowFocus", null, extras)
        b?.getBoolean("canShowFocus", false) ?: false
    } catch (e: Exception) { false }

    fun notificationsEnabled(ctx: Context, live: Boolean = false): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return false
        val ch = nm.getNotificationChannel(if (live) GlanceCapsNotifications.CH_LIVEUPDATE else GlanceCapsNotifications.CH_LIVE)
        return ch == null || ch.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun autostartEnabled(ctx: Context): Boolean? {
        val maker = Build.MANUFACTURER.lowercase()
        if (!(maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco"))) return null
        return try {
            val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val m = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
            )
            val mode = m.invoke(ops, 10008, android.os.Process.myUid(), ctx.packageName) as Int
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            null
        }
    }

    fun openNotificationSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (e: Exception) { openAppInfo(ctx) }
    }

    fun openAppInfo(ctx: Context) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (e: Exception) { }
    }

    fun openAutostartSettings(ctx: Context) {
        val tries = listOf(
            Intent().setComponent(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            ),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
        )
        for (t in tries) {
            try {
                ctx.startActivity(t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) { }
        }
        openAppInfo(ctx)
    }
}
