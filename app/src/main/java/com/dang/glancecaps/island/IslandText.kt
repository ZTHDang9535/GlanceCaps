package com.dang.glancecaps.island

import android.content.Context
import com.dang.glancecaps.battery.BatterySnapshot
import com.dang.glancecaps.battery.ThermalState
import com.dang.glancecaps.battery.formatEta
import com.dang.glancecaps.battery.formatTemp
import com.dang.glancecaps.battery.formatWatts
import com.dang.glancecaps.data.ModulePrefs
import java.util.Date
import java.util.Locale

object IslandText {

    data class Texts(
        val title: String,
        val subtitle: String,
        val cardTitle: String = title,
        val content: String = subtitle,
        val sub: String? = null,
    )

    private const val FIG = '\u2007'

    fun fig(text: String, width: Int): String =
        if (text.length >= width) text else FIG.toString().repeat(width - text.length) + text

    fun clock(ctx: Context, etaMin: Int): String {
        val t = android.text.format.DateFormat.getTimeFormat(ctx)
            .format(Date(System.currentTimeMillis() + etaMin * 60_000L))
        return if (t.indexOf(':') == 1) FIG + t else t
    }

    private fun etaText(min: Int): String =
        if (min >= 60) formatEta(min) else fig("$min min", 6)

    fun count(n: Int): String = String.format(Locale.getDefault(), "%,d", n)

    fun charging(ctx: Context, s: BatterySnapshot, eta: Int?, p: ModulePrefs): Texts {
        val title = when {
            s.full -> "Fully charged"
            !s.charging -> "${s.level}%"
            p.chgShowWatts -> fig(formatWatts(s.watts), 6)
            else -> "Charging"
        }
        if (!s.charging) return Texts(title, "Not charging")

        val parts = ArrayList<String>()
        if (p.chgShowPercent) parts.add(fig("${s.level}%", 4))
        val remaining = if (!s.full && p.chgShowRemaining) {
            if (eta != null) etaText(eta) else fig("-- min", 6)
        } else null
        if (!s.full && p.chgShowClock) parts.add(if (eta != null) clock(ctx, eta) else "--:--")

        val all = parts + listOfNotNull(remaining)
        var content = parts.joinToString(" - ")
        var sub = remaining
        if (content.isEmpty()) {
            content = sub ?: "Charging"
            sub = null
        }
        return Texts(title, all.joinToString(" - "), title, content, sub)
    }

    fun temperature(s: BatterySnapshot, p: ModulePrefs): Texts {
        val title = fig(formatTemp(s.tempC, p.tempUseF), 6)
        val label = if (p.tempShowLabel) ThermalState.of(s.tempC).label else ""
        return Texts(title, label, title, "Battery temperature", label.ifEmpty { null })
    }

    fun steps(steps: Int, goal: Int, p: ModulePrefs): Texts {
        val g = goal.coerceAtLeast(1)
        val pct = (steps * 100L / g).toInt().coerceIn(0, 100)
        val goalText = "Goal ${count(g)}"
        return Texts(
            title = "${count(steps)} steps",
            subtitle = "",
            cardTitle = "${count(steps)} steps",
            content = goalText,
            sub = if (p.stepShowPercent) "$pct%" else null
        )
    }
}
