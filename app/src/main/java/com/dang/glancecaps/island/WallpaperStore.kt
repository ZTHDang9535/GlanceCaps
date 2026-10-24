package com.dang.glancecaps.island

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.dang.glancecaps.CrashLog

object WallpaperStore {
    private var cached: Bitmap? = null
    private const val TARGET_WIDTH = 720

    fun hasAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    @Synchronized
    fun load(ctx: Context): Bitmap? {
        if (!hasAccess()) return null
        cached?.let { return it }

        return try {
            val wm = WallpaperManager.getInstance(ctx.applicationContext)
            val bmp = decodeFromFile(wm)
            cached = bmp
            bmp
        } catch (e: Throwable) {
            CrashLog.append(ctx, "wallpaper", e)
            null
        }
    }

    private fun decodeFromFile(wm: WallpaperManager): Bitmap? {
        val pfd = wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM) ?: return null
        pfd.use {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            return BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, opts)
        }
    }

    fun openAccessSettings(ctx: Context) {
        val tries = listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
        )
        for (t in tries) {
            try {
                ctx.startActivity(t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) { }
        }
    }
}
