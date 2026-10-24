package com.dang.glancecaps.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class BatterySnapshot(
    val level: Int,
    val charging: Boolean,
    val full: Boolean,
    val plugType: String,
    val tempC: Float,
    val voltageV: Float,
    val currentMa: Float,
    val watts: Float,
    val capacityMah: Float?,
    val health: String,
    val systemEtaMin: Int?,
)

object BatteryReader {
    @Volatile
    private var lastGood: BatterySnapshot? = null

    private val fallback = BatterySnapshot(
        level = 0, charging = false, full = false, plugType = "Not plugged", tempC = 0f, voltageV = 0f,
        currentMa = 0f, watts = 0f, capacityMah = null, health = "Unknown", systemEtaMin = null
    )

    fun read(ctx: Context): BatterySnapshot = try {
        readUnsafe(ctx).also { lastGood = it }
    } catch (e: Throwable) {
        lastGood ?: fallback
    }

    private fun readUnsafe(ctx: Context): BatterySnapshot {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val rawLevel = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val level = if (rawLevel >= 0 && scale > 0) {
            (rawLevel * 100f / scale).roundToInt()
        } else {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        }

        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = plugged != 0 &&
            (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        val full = plugged != 0 && (status == BatteryManager.BATTERY_STATUS_FULL || level >= 100)

        val tempC = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val voltageV = (i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f

        val rawCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (rawCurrent == Int.MIN_VALUE || rawCurrent == 0) {
            0f
        } else {
            val a = abs(rawCurrent)
            if (a >= 20000) a / 1000f else a.toFloat()
        }
        val watts = voltageV * currentMa / 1000f

        val counter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val capacity = if (counter > 0 && counter != Long.MIN_VALUE && level in 5..99) {
            val mah = counter / 1000f / (level / 100f)
            if (mah in 1000f..30000f) mah else null
        } else null

        var systemEta: Int? = null
        if (charging && !full && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val ms = bm.computeChargeTimeRemaining()
            if (ms > 0) systemEta = (ms / 60000L).toInt().coerceAtLeast(1)
        }

        val plugType = when {
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless"
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and 8 != 0 -> "Dock"
            else -> "Not plugged"
        }

        val health = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }

        return BatterySnapshot(
            level = level, charging = charging, full = full, plugType = plugType,
            tempC = tempC, voltageV = voltageV, currentMa = currentMa, watts = watts,
            capacityMah = capacity, health = health, systemEtaMin = systemEta,
        )
    }
}

class ChargeEstimator {
    private var emaMa = 0f

    fun reset() { emaMa = 0f }

    private fun taper(level: Int): Float =
        if (level < 80) 1f else (1f - 0.7f * (level - 80) / 20f)

    fun etaMinutes(s: BatterySnapshot): Int? {
        if (!s.charging || s.full || s.level >= 100) return null
        if (s.currentMa > 0f) {
            emaMa = if (emaMa == 0f) s.currentMa else emaMa * 0.8f + s.currentMa * 0.2f
        }
        s.systemEtaMin?.let { return it }
        val cap = s.capacityMah ?: return null
        if (emaMa <= 0f) return null
        val bulk = emaMa / taper(s.level)
        var minutes = 0f
        var l = s.level
        while (l < 100) {
            val i = bulk * taper(l)
            minutes += (cap / 100f) / i * 60f
            l++
        }
        return minutes.roundToInt().coerceAtLeast(1)
    }
}

enum class ThermalState(val label: String, val hex: String, val blurb: String) {
    COLD("Cold", "#4FC3F7", "Battery is cold, so charging may be a bit slower."),
    NORMAL("Normal", "#34C759", "Comfortable temperature. Nothing to worry about."),
    WARM("Warm", "#FFC107", "A little warm, which is normal during fast charging."),
    HOT("Hot", "#FF9800", "Hot. The phone may throttle charging to protect the battery."),
    CRITICAL("Critical", "#FF3B30", "Too hot. Unplug and let the phone cool down.");

    companion object {
        fun of(c: Float): ThermalState = when {
            c < 15f -> COLD
            c < 35f -> NORMAL
            c < 40f -> WARM
            c < 45f -> HOT
            else -> CRITICAL
        }
    }
}

fun fmt1(v: Float): String = String.format(Locale.US, "%.1f", v)

fun formatTemp(c: Float, useF: Boolean): String =
    if (useF) String.format(Locale.US, "%.1f°F", c * 9f / 5f + 32f)
    else String.format(Locale.US, "%.1f°C", c)

fun formatWatts(w: Float): String = String.format(Locale.US, "%.1f W", w)

fun formatEta(min: Int?): String {
    val m = min ?: return "--"
    return if (m >= 60) "${m / 60}h ${String.format(Locale.US, "%02d", m % 60)}m" else "$m min"
}
