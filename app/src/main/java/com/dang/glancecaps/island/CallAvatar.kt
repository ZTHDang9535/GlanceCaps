package com.dang.glancecaps.island

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.dang.glancecaps.R
import com.dang.glancecaps.call.CallInfo
import com.dang.glancecaps.data.ModulePrefs

object CallAvatar {
    private const val SIZE = 192

    private fun parse(hex: String, fallback: String): Int =
        try {
            Color.parseColor(hex.trim())
        } catch (e: Throwable) {
            Color.parseColor(fallback)
        }

    fun fromDrawable(d: Drawable): Bitmap {
        val w = d.intrinsicWidth.coerceIn(1, 256)
        val h = d.intrinsicHeight.coerceIn(1, 256)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        return bmp
    }

    fun circle(src: Bitmap, size: Int = SIZE): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val side = minOf(src.width, src.height)
        val left = (src.width - side) / 2
        val top = (src.height - side) / 2
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, Rect(left, top, left + side, top + side), Rect(0, 0, size, size), paint)
        return out
    }

    fun placeholder(ctx: Context, name: String, p: ModulePrefs): Bitmap {
        if (p.callPlaceholderMode == 2) {
            CallPlaceholderStore.bitmap(ctx)?.let { return circle(it) }
        }
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = parse(p.callPlaceholderColor, "#4C5C7A")
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, paint)
        if (p.callPlaceholderMode == 1) {
            val d = ContextCompat.getDrawable(ctx, R.drawable.ic_person)?.mutate()
            if (d != null) {
                val inset = (SIZE * 0.2f).toInt()
                d.setBounds(inset, inset, SIZE - inset, SIZE - inset)
                d.setTint(Color.WHITE)
                d.draw(canvas)
            }
        } else {
            val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
            paint.color = Color.WHITE
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = SIZE * 0.46f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(letter, SIZE / 2f, SIZE / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
        }
        return out
    }

    fun bitmap(ctx: Context, info: CallInfo, p: ModulePrefs): Bitmap =
        info.avatar?.let { circle(it) } ?: placeholder(ctx, info.name, p)
}
