package com.dang.glancecaps.island

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object IconStore {
    private const val FILE = "custom_icon.png"
    private var cached: Bitmap? = null
    private var cachedStamp = -1L

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, FILE)

    fun hasCustom(ctx: Context): Boolean = file(ctx).exists()

    @Synchronized
    fun saveFrom(ctx: Context, uri: Uri): Boolean {
        val sq = loadSquare(ctx, uri, 256) ?: return false

        val icon = if (sq.hasAlpha()) sq else maskSquircle(sq)
        return try {
            FileOutputStream(file(ctx)).use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cached = null
            true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun reset(ctx: Context) {
        file(ctx).delete()
        cached = null
    }

    @Synchronized
    fun custom(ctx: Context): Bitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        if (cached == null || cachedStamp != f.lastModified()) {
            cached = try { BitmapFactory.decodeFile(f.path) } catch (e: Throwable) { null }
            cachedStamp = f.lastModified()
        }
        return cached
    }

    fun bolt(ctx: Context): Bitmap =
        custom(ctx) ?: IslandIcons.glyph(
            ctx, IslandIcons.Kind.BOLT, Color.parseColor("#FFE65C"), Color.parseColor("#FFB300")
        )

    fun stamp(ctx: Context): Long = file(ctx).let { if (it.exists()) it.lastModified() else 0L }

    private fun loadSquare(ctx: Context, uri: Uri, size: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            val side = minOf(src.width, src.height)
            val cropped = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            Bitmap.createScaledBitmap(cropped, size, size, true)
        } catch (e: Throwable) {
            null
        }
    }

    private fun maskSquircle(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawPath(IslandIcons.squircle(src.width.toFloat()), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(src, 0f, 0f, paint)
        return out
    }
}
