package com.dang.glancecaps.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class ModulePrefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("glancecaps_prefs", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = sp.getBoolean(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            sp.edit().putBoolean(key, value).apply()
        }
    }

    private fun str(key: String, def: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = sp.getString(key, def) ?: def
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            sp.edit().putString(key, value).apply()
        }
    }

    private fun int(key: String, def: Int) = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = sp.getInt(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            sp.edit().putInt(key, value).apply()
        }
    }

    var chargingEnabled: Boolean by bool("chargingEnabled", true)
    var chgShowWatts: Boolean by bool("chgShowWatts", true)
    var chgShowPercent: Boolean by bool("chgShowPercent", true)
    var chgShowClock: Boolean by bool("chgShowClock", true)
    var chgShowRemaining: Boolean by bool("chgShowRemaining", true)
    var chgShowBar: Boolean by bool("chgShowBar", true)
    var chgShowIcon: Boolean by bool("chgShowIcon", true)
    var chgShowRing: Boolean by bool("chgShowRing", true)
    var chgBarColor: String by str("chgBarColor", "#34C759")
    var chgPopOnPlug: Boolean by bool("chgPopOnPlug", true)

    var haptics: Boolean by bool("haptics", true)
    var devMode: Boolean by bool("devMode", false)
    var alertsEnabled: Boolean by bool("alertsEnabled", true)
    var alertRadiusKm: Int by int("alertRadiusKm", 300)
    var alertMinLevel: Int by int("alertMinLevel", 1)

    var chgPadAuto: Boolean by bool("chgPadAuto", true)
    var chgPadLen: Int by int("chgPadLen", 14)
    var tempPadAuto: Boolean by bool("tempPadAuto", true)
    var tempPadLen: Int by int("tempPadLen", 14)
    var stepPadAuto: Boolean by bool("stepPadAuto", true)
    var stepPadLen: Int by int("stepPadLen", 14)
    var connPadAuto: Boolean by bool("connPadAuto", true)
    var connPadLen: Int by int("connPadLen", 14)
    var matchPadAuto: Boolean by bool("matchPadAuto", true)
    var matchPadLen: Int by int("matchPadLen", 14)
    var weatherPadAuto: Boolean by bool("weatherPadAuto", true)
    var weatherPadLen: Int by int("weatherPadLen", 14)

    var tempEnabled: Boolean by bool("tempEnabled", true)
    var tempMerge: Boolean by bool("tempMerge", true)
    var tempUseF: Boolean by bool("tempUseF", false)
    var tempShowLabel: Boolean by bool("tempShowLabel", true)
    var tempColorFollows: Boolean by bool("tempColorFollows", true)
    var tempShowIcon: Boolean by bool("tempShowIcon", true)
    var tempOnlyCharging: Boolean by bool("tempOnlyCharging", true)

    var stepsEnabled: Boolean by bool("stepsEnabled", false)
    var stepGoal: Int by int("stepGoal", 10000)
    var stepShowIcon: Boolean by bool("stepShowIcon", true)
    var stepShowRing: Boolean by bool("stepShowRing", true)
    var stepShowBar: Boolean by bool("stepShowBar", true)
    var stepShowPercent: Boolean by bool("stepShowPercent", true)
    var stepAlways: Boolean by bool("stepAlways", true)
    var stepPopOnGoal: Boolean by bool("stepPopOnGoal", true)
    var stepColor: String by str("stepColor", "#FF9500")
    var stepTaxi: Boolean by bool("stepTaxi", true)
    var stepSource: String by str("stepSource", "auto")
    var themeMode: String by str("themeMode", "system")
    var weatherMode: String by str("weatherMode", "always")
    var weatherHour: Int by int("weatherHour", 7)
    var weatherMinute: Int by int("weatherMinute", 0)
    var weatherDuration: Int by int("weatherDuration", 15)
    var weatherShowCondition: Boolean by bool("weatherShowCondition", true)
    var weatherShowFeels: Boolean by bool("weatherShowFeels", false)
    var weatherShowRange: Boolean by bool("weatherShowRange", true)
    var weatherShowHumidity: Boolean by bool("weatherShowHumidity", false)
    var weatherShowWind: Boolean by bool("weatherShowWind", false)
    var weatherShowCity: Boolean by bool("weatherShowCity", true)

    var weatherEnabled: Boolean by bool("weatherEnabled", false)
    var weatherSource: String by str("weatherSource", "openmeteo")
    var matchSource: String by str("matchSource", "espn")
    var matchApiKey: String by str("matchApiKey", "")
    var weatherKeyAccu: String by str("weatherKeyAccu", "")
    var weatherKeyOwm: String by str("weatherKeyOwm", "")
    var weatherKeyWapi: String by str("weatherKeyWapi", "")
    var weatherCity: String by str("weatherCity", "")
    var weatherLat: String by str("weatherLat", "")
    var weatherLon: String by str("weatherLon", "")

    var matchEnabled: Boolean by bool("matchEnabled", false)
    var matchBrowse: String by str("matchBrowse", "")
    var matchLeagueName: String by str("matchLeagueName", "")
    var matchKickoff: String by str("matchKickoff", "")
    var matchId: String by str("matchId", "")
    var matchLabel: String by str("matchLabel", "")

    var islandMode: String by str("islandMode", "auto")

    var connEnabled: Boolean by bool("connEnabled", false)
    var connWifi: Boolean by bool("connWifi", true)
    var connBluetooth: Boolean by bool("connBluetooth", true)
    var connAirplane: Boolean by bool("connAirplane", true)
    var connShowNames: Boolean by bool("connShowNames", true)
    var connSeconds: Int by int("connSeconds", 4)

    fun weatherKey(source: String): String = when (source) {
        "accuweather" -> weatherKeyAccu
        "openweather" -> weatherKeyOwm
        "weatherapi" -> weatherKeyWapi
        else -> ""
    }

    fun setWeatherKey(source: String, value: String) {
        when (source) {
            "accuweather" -> weatherKeyAccu = value
            "openweather" -> weatherKeyOwm = value
            "weatherapi" -> weatherKeyWapi = value
        }
    }
}
