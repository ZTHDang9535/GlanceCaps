package com.dang.glancecaps.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.dang.glancecaps.weather.WeatherCodes
import com.dang.glancecaps.weather.Weather
import kotlin.math.roundToInt
import org.json.JSONArray
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import com.dang.glancecaps.MainActivity
import com.dang.glancecaps.R
import com.dang.glancecaps.battery.BatterySnapshot
import com.dang.glancecaps.match.MatchInfo
import com.dang.glancecaps.match.MatchLogos
import com.dang.glancecaps.battery.ThermalState
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.weather.WeatherAlert
import org.json.JSONObject

object GlanceCapsNotifications {
    const val CH_SERVICE = "glancecaps_service"
    const val CH_LIVE = "glancecaps_live"
    const val CH_LIVEUPDATE = "glancecaps_liveupdate"
    const val ID_SERVICE = 1
    const val ID_CHARGING = 1001
    const val ID_TEMP = 1002
    const val ID_STEPS = 1003
    const val ID_CONN = 1004
    const val ID_MATCH = 1005

    private const val PIC_BOLT = "miui.focus.pic_bolt"
    private const val PIC_THERMO = "miui.focus.pic_thermo"
    private const val PIC_WALK = "miui.focus.pic_walk"
    private const val PIC_BLANK = "miui.focus.pic_blank"
    private const val PIC_FLAG = "miui.focus.pic_flag"
    private const val PIC_CONN = "miui.focus.pic_conn"
    private const val PIC_HOME = "miui.focus.pic_home"
    private const val PIC_AWAY = "miui.focus.pic_away"
    private const val PIC_PAIR = "miui.focus.pic_pair"
    private const val WHITE = "#FFFFFF"

    private val blank: Bitmap by lazy { Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888) }

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_SERVICE) == null) {
            val ch = NotificationChannel(CH_SERVICE, "Background service", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        if (nm.getNotificationChannel(CH_LIVEUPDATE) == null) {
            val ch = NotificationChannel(CH_LIVEUPDATE, "Live Update", NotificationManager.IMPORTANCE_DEFAULT)
            ch.setSound(null, null)
            ch.enableVibration(false)
            ch.setShowBadge(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
        if (nm.getNotificationChannel(CH_LIVE) == null) {
            val ch = NotificationChannel(CH_LIVE, "Live island", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)
            ch.enableVibration(false)
            ch.setShowBadge(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
    }

    internal fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun pic(key: String) = JSONObject().put("type", 1).put("pic", key)

    private fun ring(progress: Int, color: String) = JSONObject()
        .put("progress", progress.coerceIn(0, 100))
        .put("colorReach", color)
        .put("colorUnReach", "#444444")

    private fun baseInfo(title: String, content: String, sub: String?, subColor: String): JSONObject {
        val b = JSONObject()
            .put("type", 2)
            .put("title", title.ifBlank { " " })
            .put("content", content.ifBlank { " " })
        if (!sub.isNullOrEmpty()) {
            b.put("subContent", sub)
            b.put("colorSubContent", subColor)
        }
        return b
    }

    private fun paramV2(
        business: String, ticker: String, tickerPic: String, aodTitle: String,
        island: JSONObject, base: JSONObject, cardIcon: String?, progress: JSONObject?, expand: Boolean,
        hint: JSONObject? = null,
    ): JSONObject {
        val v = JSONObject()
            .put("protocol", 3)
            .put("business", business)
            .put("islandFirstFloat", expand)
            .put("enableFloat", expand)
            .put("updatable", true)
            .put("isShowNotification", true)
            .put("ticker", ticker)
            .put("tickerPic", tickerPic)
            .put("aodTitle", aodTitle)
            .put("aodPic", tickerPic)
            .put("param_island", island)
            .put("baseInfo", base)
        if (cardIcon != null) v.put("picInfo", JSONObject().put("type", 2).put("pic", cardIcon))
        if (progress != null) v.put("progressInfo", progress)
        if (hint != null) v.put("hintInfo", hint)
        return v
    }

    private fun smallIsland(icon: String?, ring: JSONObject?): JSONObject = when {
        icon != null && ring != null -> JSONObject().put(
            "combinePicInfo", JSONObject().put("picInfo", pic(icon)).put("progressInfo", ring)
        )
        icon != null -> JSONObject().put("picInfo", pic(icon))
        ring != null -> JSONObject().put(
            "combinePicInfo", JSONObject().put("picInfo", pic(PIC_BLANK)).put("progressInfo", ring)
        )
        else -> JSONObject().put("picInfo", pic(PIC_BLANK))
    }

    private fun hexOf(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    private fun baseBuilder(ctx: Context, smallIcon: Int, title: String, text: String): Notification.Builder =
        Notification.Builder(ctx, CH_LIVE)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STATUS)

    fun serviceNotification(ctx: Context, p: ModulePrefs): Notification {
        ensureChannels(ctx)
        val on = ArrayList<String>()
        if (p.chargingEnabled) on.add("Charging")
        if (p.tempEnabled) on.add("Temperature")
        if (p.stepsEnabled) on.add("Steps")
        if (p.connEnabled) on.add("Connectivity")
        if (p.matchEnabled) on.add("Matchday")
        if (p.weatherEnabled) on.add("Weather")
        val text = if (on.isEmpty()) "No modules on" else on.joinToString(" + ")
        return Notification.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    fun chargingJson(
        ctx: Context, s: BatterySnapshot, eta: Int?, p: ModulePrefs, merged: Boolean, expand: Boolean
    ): String {
        val t = IslandText.charging(ctx, s, eta, p)
        val level = s.level.coerceIn(0, 100)
        val bar = p.chgBarColor
        val outline = if (merged && p.tempColorFollows) ThermalState.of(s.tempC).hex else bar

        val textInfo = JSONObject().put("title", t.title)
        if (t.subtitle.isNotEmpty()) textInfo.put("content", t.subtitle)

        val left = JSONObject().put("type", 1).put("textInfo", textInfo)
        if (p.chgShowIcon) {
            left.put("picInfo", pic(PIC_BOLT))
            if (p.chgShowRing) left.put("progressInfo", ring(level, bar))
        }
        val big = JSONObject().put("imageTextInfoLeft", left)
        if (merged) {
            val tt = IslandText.temperature(s, p)
            val right = JSONObject().put("type", 2).put("textInfo", JSONObject().put("title", tt.title))
            if (p.tempShowIcon) right.put("picInfo", pic(PIC_THERMO))
            big.put("imageTextInfoRight", right)
        }
        IslandPadding.apply(big, p.chgPadAuto, p.chgPadLen)

        val small = smallIsland(
            if (p.chgShowIcon) PIC_BOLT else null,
            if (p.chgShowRing || !p.chgShowIcon) ring(level, bar) else null
        )
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("islandPriority", 1)
            .put("highlightColor", outline)
            .put("bigIslandArea", big)
            .put("smallIslandArea", small)

        val pop = expand && p.chgPopOnPlug
        val v2 = paramV2(
            business = "glancecaps_charging", ticker = "Charging", tickerPic = PIC_BOLT, aodTitle = t.title,
            island = island,
            base = baseInfo(t.cardTitle, t.content, t.sub, bar),
            cardIcon = PIC_BOLT,
            progress = if (p.chgShowBar) JSONObject().put("progress", level).put("colorProgress", bar) else null,
            expand = pop
        )
        return JSONObject().put("param_v2", v2).toString()
    }

    fun charging(
        ctx: Context, s: BatterySnapshot, eta: Int?, p: ModulePrefs, merged: Boolean, expand: Boolean
    ): Notification {
        ensureChannels(ctx)
        if (!useHyper(p)) return chargingLive(ctx, s, eta, p, merged)
        val t = IslandText.charging(ctx, s, eta, p)
        val bolt = IconStore.bolt(ctx)

        val pics = Bundle()
        pics.putParcelable(PIC_BOLT, Icon.createWithBitmap(bolt))
        pics.putParcelable(PIC_BLANK, Icon.createWithBitmap(blank))
        if (merged) pics.putParcelable(PIC_THERMO, Icon.createWithBitmap(thermoBitmap(ctx, s)))

        val n = baseBuilder(ctx, R.drawable.ic_bolt, t.title, t.subtitle)
            .setProgress(100, s.level.coerceIn(0, 100), false)
            .setLargeIcon(Icon.createWithBitmap(bolt))
            .addExtras(Bundle().apply { putBundle("miui.focus.pics", pics) })
            .build()
        n.extras.putString("miui.focus.param", chargingJson(ctx, s, eta, p, merged, expand))
        return n
    }

    fun chargingSignature(ctx: Context, s: BatterySnapshot, eta: Int?, p: ModulePrefs, merged: Boolean): String =
        chargingJson(ctx, s, eta, p, merged, false) + "|" + IconStore.stamp(ctx) + "|" + ThermalState.of(s.tempC).ordinal + "|" + p.islandMode

    fun temperatureJson(s: BatterySnapshot, p: ModulePrefs, expand: Boolean): String {
        val t = IslandText.temperature(s, p)
        val state = ThermalState.of(s.tempC)
        val outline = if (p.tempColorFollows) state.hex else WHITE

        val textInfo = JSONObject().put("title", t.title)
        if (t.subtitle.isNotEmpty()) textInfo.put("content", t.subtitle)
        val left = JSONObject().put("type", 1).put("textInfo", textInfo)
        if (p.tempShowIcon) left.put("picInfo", pic(PIC_THERMO))

        val big = JSONObject().put("imageTextInfoLeft", left)
        IslandPadding.apply(big, p.tempPadAuto, p.tempPadLen)
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("islandPriority", 1)
            .put("highlightColor", outline)
            .put("bigIslandArea", big)
            .put("smallIslandArea", smallIsland(if (p.tempShowIcon) PIC_THERMO else null, null))

        val v2 = paramV2(
            business = "glancecaps_temperature", ticker = "Temperature", tickerPic = PIC_THERMO, aodTitle = t.title,
            island = island,
            base = baseInfo(t.cardTitle, t.content, t.sub, state.hex),
            cardIcon = PIC_THERMO,
            progress = null,
            expand = expand
        )
        return JSONObject().put("param_v2", v2).toString()
    }

    fun temperature(ctx: Context, s: BatterySnapshot, p: ModulePrefs, expand: Boolean): Notification {
        ensureChannels(ctx)
        if (!useHyper(p)) return temperatureLive(ctx, s, p)
        val t = IslandText.temperature(s, p)
        val thermo = thermoBitmap(ctx, s)
        val pics = Bundle()
        pics.putParcelable(PIC_THERMO, Icon.createWithBitmap(thermo))
        pics.putParcelable(PIC_BLANK, Icon.createWithBitmap(blank))

        val n = baseBuilder(ctx, R.drawable.ic_thermostat, t.title, t.content)
            .setLargeIcon(Icon.createWithBitmap(thermo))
            .addExtras(Bundle().apply { putBundle("miui.focus.pics", pics) })
            .build()
        n.extras.putString("miui.focus.param", temperatureJson(s, p, expand))
        return n
    }

    fun temperatureSignature(s: BatterySnapshot, p: ModulePrefs): String =
        temperatureJson(s, p, false) + "|" + ThermalState.of(s.tempC).ordinal + "|" + p.islandMode

    fun thermoBitmap(ctx: Context, s: BatterySnapshot): Bitmap {
        val base = Color.parseColor(ThermalState.of(s.tempC).hex)
        return IslandIcons.glyph(ctx, IslandIcons.Kind.THERMO, IslandIcons.lighten(base, 0.25f), base)
    }

    fun walkBitmap(ctx: Context, p: ModulePrefs): Bitmap {
        val base = Color.parseColor(p.stepColor)
        return IslandIcons.glyph(ctx, IslandIcons.Kind.WALK, IslandIcons.lighten(base, 0.25f), base)
    }

    private fun taxiBar(pct: Int, color: String) = JSONObject()
        .put("progress", pct.coerceIn(0, 100))
        .put("colorProgress", color)
        .put("colorProgressEnd", hexOf(IslandIcons.lighten(Color.parseColor(color), 0.35f)))
        .put("picForward", PIC_WALK)
        .put("picEnd", PIC_FLAG)

    fun flagBitmap(ctx: Context): Bitmap =
        IslandIcons.glyph(ctx, IslandIcons.Kind.FLAG, Color.parseColor("#D5D8DC"), Color.parseColor("#8E949B"))

    fun stepsJson(steps: Int, p: ModulePrefs, expand: Boolean): String {
        val goal = p.stepGoal.coerceAtLeast(1)
        val pct = (steps * 100L / goal).toInt().coerceIn(0, 100)
        val t = IslandText.steps(steps, goal, p)
        val color = p.stepColor

        val textInfo = JSONObject().put("title", t.title)
        if (t.subtitle.isNotEmpty()) textInfo.put("content", t.subtitle)
        val left = JSONObject().put("type", 1).put("textInfo", textInfo)
        if (p.stepShowIcon) {
            left.put("picInfo", pic(PIC_WALK))
            if (p.stepShowRing) left.put("progressInfo", ring(pct, color))
        }
        val big = JSONObject().put("imageTextInfoLeft", left)
        if (p.stepShowPercent) {
            big.put("imageTextInfoRight", JSONObject().put("type", 2).put("textInfo", JSONObject().put("title", "$pct%")))
        }
        IslandPadding.apply(big, p.stepPadAuto, p.stepPadLen)

        val island = JSONObject()
            .put("islandProperty", 1)
            .put("islandPriority", 1)
            .put("highlightColor", color)
            .put("bigIslandArea", big)
            .put(
                "smallIslandArea",
                smallIsland(
                    if (p.stepShowIcon) PIC_WALK else null,
                    if (p.stepShowRing || !p.stepShowIcon) ring(pct, color) else null
                )
            )

        val v2 = paramV2(
            business = "glancecaps_steps", ticker = "Steps", tickerPic = PIC_WALK, aodTitle = t.title,
            island = island,
            base = baseInfo(t.cardTitle, t.content, t.sub, color),
            cardIcon = if (p.stepTaxi) null else PIC_WALK,
            progress = when {
                !p.stepShowBar -> null
                p.stepTaxi -> taxiBar(pct, color)
                else -> JSONObject().put("progress", pct).put("colorProgress", color)
            },
            expand = expand
        )
        return JSONObject().put("param_v2", v2).toString()
    }

    fun steps(ctx: Context, steps: Int, p: ModulePrefs, expand: Boolean): Notification {
        ensureChannels(ctx)
        if (!useHyper(p)) return stepsLive(ctx, steps, p)
        val t = IslandText.steps(steps, p.stepGoal, p)
        val walk = walkBitmap(ctx, p)
        val pics = Bundle()
        pics.putParcelable(PIC_WALK, Icon.createWithBitmap(walk))
        pics.putParcelable(PIC_BLANK, Icon.createWithBitmap(blank))
        pics.putParcelable(PIC_FLAG, Icon.createWithBitmap(flagBitmap(ctx)))

        val n = baseBuilder(ctx, R.drawable.ic_directions_walk, t.cardTitle, t.content)
            .setLargeIcon(Icon.createWithBitmap(walk))
            .addExtras(Bundle().apply { putBundle("miui.focus.pics", pics) })
            .build()
        n.extras.putString("miui.focus.param", stepsJson(steps, p, expand))
        return n
    }

    fun stepsSignature(steps: Int, p: ModulePrefs): String = stepsJson(steps, p, false) + "|" + p.islandMode

    enum class ConnKind { WIFI, BLUETOOTH, AIRPLANE }

    fun connLabel(kind: ConnKind): String = when (kind) {
        ConnKind.WIFI -> "Wi-Fi"
        ConnKind.BLUETOOTH -> "Bluetooth"
        ConnKind.AIRPLANE -> "Airplane mode"
    }

    fun connText(kind: ConnKind, on: Boolean, detail: String?): String {
        val state = if (on) "On" else "Off"
        return when (kind) {
            ConnKind.WIFI -> "Wi-Fi: " + (if (on && !detail.isNullOrBlank()) detail else state)
            ConnKind.BLUETOOTH -> "Bluetooth: " + (if (on && !detail.isNullOrBlank()) detail else state)
            ConnKind.AIRPLANE -> state
        }
    }

    fun connAccent(kind: ConnKind, on: Boolean): String = when {
        !on -> "#9AA0A6"
        else -> "#3D8BFF"
    }

    private fun connKindIcon(kind: ConnKind, on: Boolean): IslandIcons.Kind = when (kind) {
        ConnKind.WIFI -> if (on) IslandIcons.Kind.WIFI_ON else IslandIcons.Kind.WIFI_OFF
        ConnKind.BLUETOOTH -> if (on) IslandIcons.Kind.BT_ON else IslandIcons.Kind.BT_OFF
        ConnKind.AIRPLANE -> if (on) IslandIcons.Kind.AIR_ON else IslandIcons.Kind.AIR_OFF
    }

    private fun connRes(kind: ConnKind, on: Boolean): Int = when (kind) {
        ConnKind.WIFI -> if (on) R.drawable.ic_wifi_on else R.drawable.ic_wifi_off
        ConnKind.BLUETOOTH -> if (on) R.drawable.ic_bluetooth_on else R.drawable.ic_bluetooth_off
        ConnKind.AIRPLANE -> if (on) R.drawable.ic_airplane_on else R.drawable.ic_airplane_off
    }

    fun connBitmap(ctx: Context, kind: ConnKind, on: Boolean): Bitmap {
        val base = Color.parseColor(connAccent(kind, on))
        return IslandIcons.glyph(ctx, connKindIcon(kind, on), IslandIcons.lighten(base, 0.3f), base)
    }

    fun connectivityJson(kind: ConnKind, on: Boolean, detail: String?, p: ModulePrefs): String {
        val text = connText(kind, on, detail)
        val accent = connAccent(kind, on)
        val left = JSONObject().put("type", 1).put("picInfo", pic(PIC_CONN))
        val right = JSONObject().put("type", 2)
            .put("textInfo", JSONObject().put("title", text).put("showHighlightColor", true))
        val big = JSONObject().put("imageTextInfoLeft", left).put("imageTextInfoRight", right)
        IslandPadding.apply(big, p.connPadAuto, p.connPadLen)
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("islandPriority", 2)
            .put("islandOrder", true)
            .put("highlightColor", accent)
            .put("bigIslandArea", big)
            .put("smallIslandArea", smallIsland(PIC_CONN, null))
        val cardTitle = if (kind == ConnKind.AIRPLANE) connLabel(kind) else text
        val cardContent = if (kind == ConnKind.AIRPLANE) text else connLabel(kind)
        val v2 = paramV2(
            business = "glancecaps_connectivity", ticker = text, tickerPic = PIC_CONN, aodTitle = text,
            island = island,
            base = baseInfo(cardTitle, cardContent, null, accent),
            cardIcon = PIC_CONN,
            progress = null,
            expand = false
        )
        return JSONObject().put("param_v2", v2).toString()
    }

    fun connectivity(ctx: Context, kind: ConnKind, on: Boolean, detail: String?, p: ModulePrefs): Notification {
        ensureChannels(ctx)
        if (!useHyper(p)) return connectivityLive(ctx, kind, on, detail, p)
        val bmp = connBitmap(ctx, kind, on)
        val pics = Bundle()
        pics.putParcelable(PIC_CONN, Icon.createWithBitmap(bmp))
        pics.putParcelable(PIC_BLANK, Icon.createWithBitmap(blank))
        val text = connText(kind, on, detail)
        val n = baseBuilder(ctx, connRes(kind, on), text, connLabel(kind))
            .setLargeIcon(Icon.createWithBitmap(bmp))
            .setTimeoutAfter(p.connSeconds.coerceIn(2, 10) * 1000L)
            .addExtras(Bundle().apply { putBundle("miui.focus.pics", pics) })
            .build()
        n.extras.putString("miui.focus.param", connectivityJson(kind, on, detail, p))
        return n
    }

    fun isXiaomiFamily(): Boolean {
        val maker = Build.MANUFACTURER.lowercase()
        return maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco")
    }

    fun useHyper(p: ModulePrefs): Boolean = when (p.islandMode) {
        "hyper" -> isXiaomiFamily()
        "live" -> false
        else -> isXiaomiFamily()
    }

    private fun clean(s: String): String = s.replace("\u2007", "").trim()

    private fun joinLine(a: String, b: String?): String =
        listOfNotNull(a.takeIf { it.isNotBlank() }, b?.takeIf { it.isNotBlank() }).joinToString("  -  ")

    private fun chargingLive(ctx: Context, s: BatterySnapshot, eta: Int?, p: ModulePrefs, merged: Boolean): Notification {
        val t = IslandText.charging(ctx, s, eta, p)
        val level = s.level.coerceIn(0, 100)
        val base = clean(joinLine(t.content, t.sub))
        val temp = if (merged) IslandText.temperature(s, p) else null
        val tempLine = temp?.let { clean(joinLine(it.title, it.sub)) }
        val body = if (tempLine.isNullOrBlank()) base else "$base  -  $tempLine"
        return LiveUpdates.build(
            ctx, openApp(ctx), R.drawable.ic_bolt,
            title = clean(t.cardTitle), text = body,
            shortText = clean(com.dang.glancecaps.battery.formatWatts(s.watts)), progress = if (p.chgShowBar) level else null,
            accent = Color.parseColor(p.chgBarColor),
            subText = "Charging"
        )
    }

    private fun temperatureLive(ctx: Context, s: BatterySnapshot, p: ModulePrefs): Notification {
        val t = IslandText.temperature(s, p)
        return LiveUpdates.build(
            ctx, openApp(ctx), R.drawable.ic_thermostat,
            title = clean(t.cardTitle), text = clean(joinLine(t.content, t.sub)),
            shortText = clean(t.title), progress = null,
            accent = Color.parseColor(ThermalState.of(s.tempC).hex),
            subText = "Temperature"
        )
    }

    private fun stepsLive(ctx: Context, steps: Int, p: ModulePrefs): Notification {
        val t = IslandText.steps(steps, p.stepGoal, p)
        val pct = if (p.stepGoal > 0) (steps * 100L / p.stepGoal).toInt().coerceIn(0, 100) else 0
        return LiveUpdates.build(
            ctx, openApp(ctx), R.drawable.ic_directions_walk,
            title = clean(t.cardTitle), text = clean(joinLine(t.content, t.sub)),
            shortText = IslandText.count(steps), progress = if (p.stepShowBar) pct else null,
            accent = Color.parseColor(p.stepColor),
            subText = "Steps"
        )
    }

    private fun connectivityLive(ctx: Context, kind: ConnKind, on: Boolean, detail: String?, p: ModulePrefs): Notification {
        val text = connText(kind, on, detail)
        return LiveUpdates.build(
            ctx, openApp(ctx), connRes(kind, on),
            title = if (kind == ConnKind.AIRPLANE) connLabel(kind) else text,
            text = if (kind == ConnKind.AIRPLANE) text else connLabel(kind),
            shortText = if (on) "On" else "Off", progress = null,
            accent = Color.parseColor(connAccent(kind, on)),
            timeoutMs = p.connSeconds.coerceIn(2, 10) * 1000L,
            subText = "Connectivity"
        )
    }

    const val ID_WEATHER = 1006
    const val ID_ALERT = 1007
    private const val PIC_ALERT = "miui.focus.pic_alert"
    private const val PIC_SCORE = "miui.focus.pic_score"
    private const val PIC_WX = "miui.focus.pic_wx"
    private const val ACTION_CLOSE_KEY = "miui.focus.action_close"

    fun kickoffLabel(ctx: Context, m: MatchInfo): String {
        val zone = java.time.ZoneId.systemDefault()
        val day = java.time.Instant.ofEpochMilli(m.kickoff).atZone(zone).toLocalDate()
        val today = java.time.LocalDate.now(zone)
        val time = android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(m.kickoff))
        return when (day) {
            today -> "Today $time"
            today.plusDays(1) -> "Tomorrow $time"
            else -> java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(java.util.Date(m.kickoff)) + " " + time
        }
    }

    fun matchStatusLine(ctx: Context, m: MatchInfo): String {
        val status = when (m.state) {
            "in" -> m.detail.ifBlank { "Live" }
            "post" -> "Full time"
            else -> "Kick-off " + kickoffLabel(ctx, m)
        }
        return if (m.leagueName.isBlank()) status else status + "  -  " + m.leagueName
    }

    fun matchTitle(m: MatchInfo): String =
        if (m.state == "pre") "${m.home.name} vs ${m.away.name}" else "${m.home.name} ${m.homeScore} - ${m.awayScore} ${m.away.name}"

    private fun matchScoreLabel(ctx: Context, m: MatchInfo): String =
        if (m.state == "pre") android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(m.kickoff)) else "${m.homeScore}-${m.awayScore}"

    private fun closeIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 4242,
            Intent(ctx, MatchCloseReceiver::class.java).setAction(MatchCloseReceiver.ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun matchJson(ctx: Context, m: MatchInfo, p: ModulePrefs, expand: Boolean): String {
        val accent = if (m.live) "#34C759" else "#8E8E93"
        val left = JSONObject().put("type", 1)
            .put("picInfo", pic(PIC_HOME))
            .put("textInfo", JSONObject().put("title", m.homeScore))
        val right = JSONObject().put("type", 2)
            .put("picInfo", pic(PIC_AWAY))
            .put("textInfo", JSONObject().put("title", m.awayScore))
        val big = JSONObject().put("imageTextInfoLeft", left).put("imageTextInfoRight", right)
        IslandPadding.apply(big, p.matchPadAuto, p.matchPadLen)
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("islandPriority", 1)
            .put("highlightColor", accent)
            .put("bigIslandArea", big)
            .put("smallIslandArea", smallIsland(PIC_SCORE, null))
        val progress = if (m.live) {
            JSONObject().put("progress", (m.minute * 100 / 90).coerceIn(0, 100)).put("colorProgress", accent)
        } else null
        val hint = JSONObject()
            .put("type", 1)
            .put("title", "Tracking this match")
            .put("actionInfo", JSONObject().put("action", ACTION_CLOSE_KEY))
        val v2 = paramV2(
            business = "glancecaps_match", ticker = matchTitle(m), tickerPic = PIC_PAIR, aodTitle = matchTitle(m),
            island = island,
            base = baseInfo(matchTitle(m), matchStatusLine(ctx, m), null, accent),
            cardIcon = PIC_PAIR,
            progress = progress,
            expand = expand,
            hint = hint
        )
        return JSONObject().put("param_v2", v2).toString()
    }

    fun matchSignature(ctx: Context, m: MatchInfo, p: ModulePrefs, logos: Boolean): String =
        matchJson(ctx, m, p, false) + "|" + p.islandMode + "|" + logos

    fun match(
        ctx: Context,
        m: MatchInfo,
        home: Bitmap?,
        away: Bitmap?,
        pair: Bitmap?,
        p: ModulePrefs,
        expand: Boolean,
    ): Notification {
        ensureChannels(ctx)
        if (!useHyper(p)) return matchLive(ctx, m, pair)
        val pics = Bundle()
        pics.putParcelable(PIC_HOME, Icon.createWithBitmap(home ?: MatchLogos.placeholder()))
        pics.putParcelable(PIC_AWAY, Icon.createWithBitmap(away ?: MatchLogos.placeholder()))
        pics.putParcelable(PIC_PAIR, Icon.createWithBitmap(pair ?: MatchLogos.pair(home, away)))
        pics.putParcelable(PIC_SCORE, Icon.createWithBitmap(MatchLogos.scoreBitmap(matchScoreLabel(ctx, m))))
        pics.putParcelable(PIC_BLANK, Icon.createWithBitmap(blank))
        val close = Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_close), "Close", closeIntent(ctx)).build()
        val actionBundle = Bundle()
        actionBundle.putParcelable(ACTION_CLOSE_KEY, close)
        val extras = Bundle()
        extras.putBundle("miui.focus.pics", pics)
        extras.putBundle("miui.focus.actions", actionBundle)
        val n = baseBuilder(ctx, R.drawable.ic_soccer, matchTitle(m), matchStatusLine(ctx, m))
            .setLargeIcon(Icon.createWithBitmap(pair ?: MatchLogos.pair(home, away)))
            .addAction(close)
            .addExtras(extras)
            .build()
        n.extras.putString("miui.focus.param", matchJson(ctx, m, p, expand))
        return n
    }

    private fun matchLive(ctx: Context, m: MatchInfo, pair: Bitmap?): Notification =
        LiveUpdates.build(
            ctx, openApp(ctx), R.drawable.ic_soccer,
            title = matchTitle(m), text = matchStatusLine(ctx, m),
            shortText = matchScoreLabel(ctx, m),
            progress = if (m.live) (m.minute * 100 / 90).coerceIn(0, 100) else null,
            accent = Color.parseColor("#34C759"),
            largeIcon = pair ?: MatchLogos.pair(null, null),
            subText = "Matchday",
            closeIntent = closeIntent(ctx)
        )

    fun weatherTemp(c: Double, p: ModulePrefs): String {
        val t = if (p.tempUseF) c * 9.0 / 5.0 + 32.0 else c
        return "${t.roundToInt()}°" + (if (p.tempUseF) "F" else "C")
    }

    fun weatherRange(w: Weather, p: ModulePrefs): String {
        fun conv(c: Double): Int = (if (p.tempUseF) c * 9.0 / 5.0 + 32.0 else c).roundToInt()
        val parts = ArrayList<String>()
        if (p.weatherShowFeels) parts.add("Feels ${conv(w.feels)}°")
        if (p.weatherShowRange) parts.add("H ${conv(w.high)}°  L ${conv(w.low)}°")
        if (p.weatherShowHumidity) parts.add("${w.humidity}%")
        if (p.weatherShowWind) parts.add("${w.wind.roundToInt()} km/h")
        val cityName = if (p.weatherSource == "system") com.dang.glancecaps.weather.SystemWeather.city else p.weatherCity
        if (p.weatherShowCity && cityName.isNotBlank()) parts.add(cityName.substringBefore(","))
        return if (parts.isEmpty()) WeatherCodes.label(w.code, w.day) else parts.joinToString("  -  ")
    }

    fun weatherHeadline(w: Weather, p: ModulePrefs): String =
        if (p.weatherShowCondition) weatherTemp(w.temp, p) + "  " + WeatherCodes.label(w.code, w.day) else weatherTemp(w.temp, p)

    fun weatherBitmap(ctx: Context, w: Weather): Bitmap {
        val base = Color.parseColor(WeatherCodes.accent(w.code, w.day))
        return IslandIcons.glyph(ctx, WeatherCodes.kind(w.code, w.day), IslandIcons.lighten(base, 0.3f), base)
    }

    private fun weatherRes(w: Weather): Int = when (WeatherCodes.kind(w.code, w.day)) {
        IslandIcons.Kind.WX_SUN -> R.drawable.ic_wx_sun
        IslandIcons.Kind.WX_NIGHT -> R.drawable.ic_wx_night
        IslandIcons.Kind.WX_PARTLY -> R.drawable.ic_wx_partly
        IslandIcons.Kind.WX_RAIN -> R.drawable.ic_wx_rain
        IslandIcons.Kind.WX_POUR -> R.drawable.ic_wx_pour
        IslandIcons.Kind.WX_SNOW -> R.drawable.ic_wx_snow
        IslandIcons.Kind.WX_STORM -> R.drawable.ic_wx_storm
        IslandIcons.Kind.WX_FOG -> R.drawable.ic_wx_fog
        else -> R.drawable.ic_wx_cloud
    }

    fun weatherJson(ctx: Context, w: Weather, p: ModulePrefs, expand: Boolean): String {
        val temp = weatherTemp(w.temp, p)
        val label = if (p.weatherShowCondition) WeatherCodes.label(w.code, w.day) else weatherRange(w, p)
        val accent = WeatherCodes.accent(w.code, w.day)
        val left = JSONObject().put("type", 1)
            .put("picInfo", pic(PIC_WX))
            .put("textInfo", JSONObject().put("title", temp))
        val right = JSONObject().put("type", 2)
            .put("textInfo", JSONObject().put("title", label))
        val big = JSONObject().put("imageTextInfoLeft", left).put("imageTextInfoRight", right)
        IslandPadding.apply(big, p.weatherPadAuto, p.weatherPadLen)
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("islandPriority", 0)
            .put("highlightColor", accent)
            .put("bigIslandArea", big)
            .put("smallIslandArea", smallIsland(PIC_WX, null))
        val v2 = paramV2(
            business = "glancecaps_weather", ticker = weatherHeadline(w, p), tickerPic = PIC_WX, aodTitle = weatherHeadline(w, p),
            island = island,
            base = baseInfo(weatherHeadline(w, p), weatherRange(w, p), null, accent),
            cardIcon = PIC_WX,
            progress = null,
            expand = expand
        )
        return JSONObject().put("param_v2", v2).toString()
    }

    fun weatherSignature(ctx: Context, w: Weather, p: ModulePrefs): String =
        weatherJson(ctx, w, p, false) + "|" + p.islandMode

    fun weather(ctx: Context, w: Weather, p: ModulePrefs, expand: Boolean): Notification {
        ensureChannels(ctx)
        if (!useHyper(p)) return weatherLive(ctx, w, p)
        val bmp = weatherBitmap(ctx, w)
        val pics = Bundle()
        pics.putParcelable(PIC_WX, Icon.createWithBitmap(bmp))
        pics.putParcelable(PIC_BLANK, Icon.createWithBitmap(blank))
        val n = baseBuilder(ctx, weatherRes(w), weatherHeadline(w, p), weatherRange(w, p))
            .setLargeIcon(Icon.createWithBitmap(bmp))
            .addExtras(Bundle().apply { putBundle("miui.focus.pics", pics) })
            .build()
        n.extras.putString("miui.focus.param", weatherJson(ctx, w, p, expand))
        return n
    }

    private fun weatherLive(ctx: Context, w: Weather, p: ModulePrefs): Notification {
        val temp = weatherTemp(w.temp, p)
        return LiveUpdates.build(
            ctx, openApp(ctx), weatherRes(w),
            title = weatherHeadline(w, p),
            text = weatherRange(w, p),
            shortText = "${(if (p.tempUseF) w.temp * 9.0 / 5.0 + 32.0 else w.temp).roundToInt()}°",
            progress = null,
            accent = Color.parseColor(WeatherCodes.accent(w.code, w.day)),
            subText = "Weather"
        )
    }

    private fun alertAccent(level: Int): String = when (level) {
        2 -> "#FF3B30"
        1 -> "#FF9500"
        else -> "#FFCC00"
    }

    private fun alertDistance(a: WeatherAlert): String = if (a.distanceKm > 0) "${a.distanceKm} km" else "Local"

    private fun alertBitmap(ctx: Context, level: Int): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.parseColor(alertAccent(level))
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        val d = ctx.getDrawable(R.drawable.ic_error)?.mutate()
        if (d != null) {
            d.setTint(Color.WHITE)
            val inset = size / 5
            d.setBounds(inset, inset, size - inset, size - inset)
            d.draw(canvas)
        }
        return bmp
    }

    fun alertJson(a: WeatherAlert, expand: Boolean): String {
        val accent = alertAccent(a.level)
        val left = JSONObject().put("type", 1)
            .put("picInfo", pic(PIC_ALERT))
            .put("textInfo", JSONObject().put("title", a.kind))
        val right = JSONObject().put("type", 2)
            .put("textInfo", JSONObject().put("title", alertDistance(a)))
        val big = JSONObject().put("imageTextInfoLeft", left).put("imageTextInfoRight", right)
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("islandPriority", 3)
            .put("highlightColor", accent)
            .put("bigIslandArea", big)
            .put("smallIslandArea", smallIsland(PIC_ALERT, null))
        val v2 = paramV2(
            business = "glancecaps_alert", ticker = a.title, tickerPic = PIC_ALERT, aodTitle = a.title,
            island = island,
            base = baseInfo(a.title, a.detail, a.source + "  -  " + alertDistance(a), accent),
            cardIcon = PIC_ALERT,
            progress = null,
            expand = expand
        )
        return JSONObject().put("param_v2", v2).toString()
    }

    fun alertSignature(a: WeatherAlert, p: ModulePrefs): String = alertJson(a, false) + "|" + p.islandMode

    fun alert(ctx: Context, a: WeatherAlert, p: ModulePrefs, expand: Boolean): Notification {
        ensureChannels(ctx)
        if (!useHyper(p)) {
            return LiveUpdates.build(
                ctx, openApp(ctx), R.drawable.ic_error,
                title = a.title,
                text = a.detail,
                shortText = a.kind,
                progress = null,
                accent = Color.parseColor(alertAccent(a.level)),
                subText = "Weather alert"
            )
        }
        val bmp = alertBitmap(ctx, a.level)
        val pics = Bundle()
        pics.putParcelable(PIC_ALERT, Icon.createWithBitmap(bmp))
        pics.putParcelable(PIC_BLANK, Icon.createWithBitmap(blank))
        val n = baseBuilder(ctx, R.drawable.ic_error, a.title, a.detail)
            .setLargeIcon(Icon.createWithBitmap(bmp))
            .addExtras(Bundle().apply { putBundle("miui.focus.pics", pics) })
            .build()
        n.extras.putString("miui.focus.param", alertJson(a, expand))
        return n
    }
}
