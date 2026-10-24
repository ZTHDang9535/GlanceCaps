package com.dang.glancecaps.island

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.core.content.ContextCompat
import com.dang.glancecaps.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

object IslandIcons {
    enum class Kind { BOLT, THERMO, WALK, FLAG, WX_SUN, WX_NIGHT, WX_PARTLY, WX_CLOUD, WX_RAIN, WX_POUR, WX_SNOW, WX_STORM, WX_FOG, WIFI_ON, WIFI_OFF, BT_ON, BT_OFF, AIR_ON, AIR_OFF }

    private val cache = HashMap<String, Bitmap>()

    @Synchronized
    private fun cached(key: String, make: () -> Bitmap): Bitmap {
        cache[key]?.let { return it }
        if (cache.size > 40) cache.clear()
        val b = make()
        cache[key] = b
        return b
    }

    fun darken(color: Int, f: Float = 0.35f): Int {
        val r = (Color.red(color) * (1f - f)).toInt()
        val g = (Color.green(color) * (1f - f)).toInt()
        val b = (Color.blue(color) * (1f - f)).toInt()
        return Color.rgb(r, g, b)
    }

    fun lighten(color: Int, f: Float = 0.35f): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * f).toInt()
        val g = (Color.green(color) + (255 - Color.green(color)) * f).toInt()
        val b = (Color.blue(color) + (255 - Color.blue(color)) * f).toInt()
        return Color.rgb(r, g, b)
    }

    fun glyph(ctx: Context, kind: Kind, top: Int, bottom: Int, size: Int = 192): Bitmap =
        cached("g-$kind-$top-$bottom-$size") {
            val res = when (kind) {
                Kind.BOLT -> R.drawable.ic_bolt
                Kind.THERMO -> R.drawable.ic_thermostat
                Kind.WALK -> R.drawable.ic_directions_walk
                Kind.FLAG -> R.drawable.ic_flag_goal
                Kind.WX_SUN -> R.drawable.ic_wx_sun
                Kind.WX_NIGHT -> R.drawable.ic_wx_night
                Kind.WX_PARTLY -> R.drawable.ic_wx_partly
                Kind.WX_CLOUD -> R.drawable.ic_wx_cloud
                Kind.WX_RAIN -> R.drawable.ic_wx_rain
                Kind.WX_POUR -> R.drawable.ic_wx_pour
                Kind.WX_SNOW -> R.drawable.ic_wx_snow
                Kind.WX_STORM -> R.drawable.ic_wx_storm
                Kind.WX_FOG -> R.drawable.ic_wx_fog
                Kind.WIFI_ON -> R.drawable.ic_wifi_on
                Kind.WIFI_OFF -> R.drawable.ic_wifi_off
                Kind.BT_ON -> R.drawable.ic_bluetooth_on
                Kind.BT_OFF -> R.drawable.ic_bluetooth_off
                Kind.AIR_ON -> R.drawable.ic_airplane_on
                Kind.AIR_OFF -> R.drawable.ic_airplane_off
            }
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val d = ContextCompat.getDrawable(ctx, res)?.mutate()
            if (d != null) {

                val ext = when (kind) {
                    Kind.BOLT, Kind.THERMO -> (size * 0.14f).toInt()
                    Kind.WALK -> -(size * 0.12f).toInt()
                    else -> -(size * 0.04f).toInt()
                }
                d.setBounds(-ext, -ext, size + ext, size + ext)
                d.setTint(Color.WHITE)
                d.draw(c)
            }
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), top, bottom, Shader.TileMode.CLAMP)
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
            bmp
        }

    fun squircle(s: Float, n: Float = 5f): Path {
        val p = Path()
        val r = s / 2f
        val steps = 96
        for (k in 0..steps) {
            val t = k.toDouble() / steps * 2.0 * Math.PI
            val ct = cos(t)
            val st = sin(t)
            val x = r + r * sign(ct) * abs(ct).pow(2.0 / n)
            val y = r + r * sign(st) * abs(st).pow(2.0 / n)
            if (k == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
        }
        p.close()
        return p
    }
}
