package com.dang.glancecaps.island

import android.app.NotificationManager
import android.content.Context
import com.dang.glancecaps.battery.BatterySnapshot
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.match.MatchInfo
import com.dang.glancecaps.match.MatchLogos
import com.dang.glancecaps.match.MatchTeam
import com.dang.glancecaps.weather.Weather
import com.dang.glancecaps.weather.WeatherAlert

class DevCase(val label: String, val run: (Context, ModulePrefs) -> Unit)

class DevModule(val name: String, val cases: List<DevCase>)

object DevTests {
    private fun nm(ctx: Context): NotificationManager = ctx.getSystemService(NotificationManager::class.java)

    private fun battery(level: Int, full: Boolean, watts: Float, tempC: Float) = BatterySnapshot(
        level = level,
        charging = true,
        full = full,
        plugType = "USB-C",
        tempC = tempC,
        voltageV = 9f,
        currentMa = if (watts > 0f) watts / 9f * 1000f else 0f,
        watts = watts,
        capacityMah = 5000f,
        health = "Good",
        systemEtaMin = null
    )

    private fun charge(label: String, level: Int, full: Boolean, watts: Float, eta: Int?) = DevCase(label) { ctx, p ->
        nm(ctx).notify(
            GlanceCapsNotifications.ID_CHARGING,
            GlanceCapsNotifications.charging(ctx, battery(level, full, watts, 33f), eta, p, false, true)
        )
    }

    private fun temp(label: String, tempC: Float) = DevCase(label) { ctx, p ->
        nm(ctx).notify(
            GlanceCapsNotifications.ID_TEMP,
            GlanceCapsNotifications.temperature(ctx, battery(60, false, 20f, tempC), p, true)
        )
    }

    private fun steps(label: String, count: (ModulePrefs) -> Int) = DevCase(label) { ctx, p ->
        nm(ctx).notify(GlanceCapsNotifications.ID_STEPS, GlanceCapsNotifications.steps(ctx, count(p), p, true))
    }

    private fun conn(label: String, kind: GlanceCapsNotifications.ConnKind, on: Boolean, detail: String?) =
        DevCase(label) { ctx, p ->
            nm(ctx).notify(
                GlanceCapsNotifications.ID_CONN,
                GlanceCapsNotifications.connectivity(ctx, kind, on, detail, p)
            )
        }

    private fun match(label: String, state: String, detail: String, minute: Int, home: Int?, away: Int?, kickoffOffsetMin: Long) =
        DevCase(label) { ctx, p ->
            val m = MatchInfo(
                id = "dev", leagueKey = "dev", leagueName = "V.League 1", country = "Vietnam",
                kickoff = System.currentTimeMillis() + kickoffOffsetMin * 60_000L,
                state = state, detail = detail, minute = minute,
                home = MatchTeam("Hanoi FC", "HAN", "", home),
                away = MatchTeam("Saigon FC", "SGN", "", away)
            )
            val logo = MatchLogos.placeholder()
            nm(ctx).notify(
                GlanceCapsNotifications.ID_MATCH,
                GlanceCapsNotifications.match(ctx, m, logo, logo, MatchLogos.pair(logo, logo), p, true)
            )
        }

    private fun weather(label: String, code: Int, day: Boolean, tempC: Double) = DevCase(label) { ctx, p ->
        val w = Weather(tempC, tempC + 2.0, code, day, tempC + 4.0, tempC - 5.0, 72, 14.0)
        nm(ctx).notify(GlanceCapsNotifications.ID_WEATHER, GlanceCapsNotifications.weather(ctx, w, p, true))
    }

    private fun alert(label: String, a: WeatherAlert) = DevCase(label) { ctx, p ->
        nm(ctx).notify(GlanceCapsNotifications.ID_ALERT, GlanceCapsNotifications.alert(ctx, a, p, true))
    }

    val modules: List<DevModule> = listOf(
        DevModule(
            "Charging",
            listOf(
                charge("Fast charging 65 W, 42%", 42, false, 65f, 38),
                charge("Slow charging 5 W, 80%", 80, false, 5f, 70),
                charge("Fully charged", 100, true, 0f, null)
            )
        ),
        DevModule(
            "Temperature",
            listOf(temp("Normal 33 C", 33f), temp("Warm 39 C", 39f), temp("Hot 45 C", 45f))
        ),
        DevModule(
            "Steps",
            listOf(
                steps("4,200 steps") { 4200 },
                steps("Goal reached") { it.stepGoal },
                steps("12,500 steps") { 12500 }
            )
        ),
        DevModule(
            "Connectivity",
            listOf(
                conn("Wi-Fi connected", GlanceCapsNotifications.ConnKind.WIFI, true, "Home Wi-Fi"),
                conn("Wi-Fi off", GlanceCapsNotifications.ConnKind.WIFI, false, null),
                conn("Bluetooth connected", GlanceCapsNotifications.ConnKind.BLUETOOTH, true, "Headphones"),
                conn("Bluetooth off", GlanceCapsNotifications.ConnKind.BLUETOOTH, false, null),
                conn("Airplane mode on", GlanceCapsNotifications.ConnKind.AIRPLANE, true, null),
                conn("Airplane mode off", GlanceCapsNotifications.ConnKind.AIRPLANE, false, null)
            )
        ),
        DevModule(
            "Matchday",
            listOf(
                match("Live match 2-1, 67'", "in", "67'", 67, 2, 1, -67),
                match("Upcoming match", "pre", "", 0, null, null, 90),
                match("Full time 3-0", "post", "FT", 90, 3, 0, -110)
            )
        ),
        DevModule(
            "Weather",
            listOf(
                weather("Sunny", 0, true, 34.0),
                weather("Rain", 63, true, 27.0),
                weather("Thunderstorm", 95, true, 29.0),
                weather("Snow", 73, true, -2.0),
                weather("Clear night", 0, false, 24.0),
                weather("Fog", 45, true, 12.0)
            )
        ),
        DevModule(
            "Weather alerts",
            listOf(
                alert(
                    "Cyclone, red",
                    WeatherAlert("dev1", "Cyclone", "Typhoon approaching", "Winds up to 165 km/h, landfall expected within 28 hours", 2, 180, "GDACS")
                ),
                alert(
                    "Earthquake, orange",
                    WeatherAlert("dev2", "Earthquake", "M6.1 earthquake", "52 km SE of the coast  -  Tsunami possible", 1, 240, "USGS")
                ),
                alert(
                    "Flood, orange",
                    WeatherAlert("dev3", "Flood", "River flooding", "Water levels rising across the delta region", 1, 95, "GDACS")
                ),
                alert(
                    "Severe thunderstorm, local",
                    WeatherAlert("dev4", "Thunderstorm", "Severe Thunderstorm Warning", "Damaging winds and large hail expected until 9:00 PM", 1, 0, "NWS")
                )
            )
        )
    )

    fun clear(ctx: Context) {
        val n = nm(ctx)
        listOf(
            GlanceCapsNotifications.ID_CHARGING, GlanceCapsNotifications.ID_TEMP, GlanceCapsNotifications.ID_STEPS,
            GlanceCapsNotifications.ID_CONN, GlanceCapsNotifications.ID_MATCH, GlanceCapsNotifications.ID_WEATHER,
            GlanceCapsNotifications.ID_ALERT
        ).forEach { n.cancel(it) }
    }
}
