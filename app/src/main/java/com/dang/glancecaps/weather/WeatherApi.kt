package com.dang.glancecaps.weather

import android.content.Context
import android.os.SystemClock
import com.dang.glancecaps.CrashLog
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.island.IslandIcons
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

class City(val name: String, val region: String, val country: String, val lat: Double, val lon: Double) {
    val label: String
        get() = listOf(name, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

class Weather(
    val temp: Double,
    val feels: Double,
    val code: Int,
    val day: Boolean,
    val high: Double,
    val low: Double,
    val humidity: Int,
    val wind: Double,
)

object WeatherCodes {
    fun label(code: Int, day: Boolean): String = when (code) {
        0 -> if (day) "Sunny" else "Clear"
        1 -> if (day) "Mostly sunny" else "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 66, 67 -> "Rain"
        65 -> "Heavy rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        80, 81 -> "Showers"
        82 -> "Heavy showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Cloudy"
    }

    fun kind(code: Int, day: Boolean): IslandIcons.Kind = when (code) {
        0, 1 -> if (day) IslandIcons.Kind.WX_SUN else IslandIcons.Kind.WX_NIGHT
        2 -> IslandIcons.Kind.WX_PARTLY
        3 -> IslandIcons.Kind.WX_CLOUD
        45, 48 -> IslandIcons.Kind.WX_FOG
        51, 53, 55, 56, 57, 61, 63, 66, 67, 80, 81 -> IslandIcons.Kind.WX_RAIN
        65, 82 -> IslandIcons.Kind.WX_POUR
        71, 73, 75, 77, 85, 86 -> IslandIcons.Kind.WX_SNOW
        95, 96, 99 -> IslandIcons.Kind.WX_STORM
        else -> IslandIcons.Kind.WX_CLOUD
    }

    fun accent(code: Int, day: Boolean): String = when (kind(code, day)) {
        IslandIcons.Kind.WX_SUN -> "#FFB300"
        IslandIcons.Kind.WX_NIGHT -> "#8E9BFF"
        IslandIcons.Kind.WX_PARTLY -> "#7FB8FF"
        IslandIcons.Kind.WX_RAIN, IslandIcons.Kind.WX_POUR -> "#4FA3FF"
        IslandIcons.Kind.WX_SNOW -> "#9ADCF5"
        IslandIcons.Kind.WX_STORM -> "#B388FF"
        else -> "#B0BEC5"
    }
}

object WeatherSchedule {
    fun active(p: ModulePrefs): Boolean {
        if (p.weatherMode != "scheduled") return true
        val now = java.time.LocalTime.now()
        val nowMin = now.hour * 60 + now.minute
        val start = p.weatherHour * 60 + p.weatherMinute
        val end = start + p.weatherDuration
        return if (end <= 1440) nowMin in start until end else nowMin >= start || nowMin < end - 1440
    }
}

object WeatherSources {
    const val OPEN_METEO = "openmeteo"
    const val SYSTEM = "system"
    const val OM_ECMWF = "om_ecmwf"
    const val OM_ICON = "om_icon"
    const val METNO = "metno"
    const val WTTR = "wttr"
    const val ACCU = "accuweather"
    const val OWM = "openweather"
    const val WAPI = "weatherapi"

    private val labels = mapOf(
        OPEN_METEO to "Open-Meteo",
        SYSTEM to "System weather app",
        OM_ECMWF to "Open-Meteo ECMWF",
        OM_ICON to "Open-Meteo DWD ICON",
        METNO to "MET Norway",
        WTTR to "wttr.in",
        ACCU to "AccuWeather (API key)",
        OWM to "OpenWeatherMap (API key)",
        WAPI to "WeatherAPI.com (API key)"
    )

    fun options(system: Boolean): List<Pair<String, String>> {
        val order = if (system) {
            listOf(OPEN_METEO, SYSTEM, OM_ECMWF, OM_ICON, METNO, WTTR, ACCU, OWM, WAPI)
        } else {
            listOf(OPEN_METEO, OM_ECMWF, OM_ICON, METNO, WTTR, ACCU, OWM, WAPI)
        }
        return order.map { it to labels.getValue(it) }
    }

    fun label(key: String): String = labels[key] ?: "Open-Meteo"

    fun needsKey(key: String): Boolean = key == ACCU || key == OWM || key == WAPI
}

object WeatherApi {
    private const val ACCU_BASE = "https://dataservice.accuweather.com"

    @Volatile private var accuLocSig = ""
    @Volatile private var accuLocKey = ""
    @Volatile private var accuRangeSig = ""
    @Volatile private var accuRangeAt = 0L
    @Volatile private var accuHigh = Double.NaN
    @Volatile private var accuLow = Double.NaN

    private fun get(url: String): String? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "GlanceCaps/1.0 github.com/ZTHDang9535/GlanceCaps")
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Throwable) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun num(o: JSONObject?, key: String, def: Double): Double {
        val v = o?.optDouble(key) ?: return def
        return if (v.isNaN()) def else v
    }

    fun search(query: String): List<City>? {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val body = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=en&format=json") ?: return null
        return try {
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            val out = ArrayList<City>()
            for (i in 0 until results.length()) {
                val r = results.optJSONObject(i) ?: continue
                out.add(
                    City(
                        name = r.optString("name"),
                        region = r.optString("admin1"),
                        country = r.optString("country"),
                        lat = r.optDouble("latitude"),
                        lon = r.optDouble("longitude")
                    )
                )
            }
            out
        } catch (e: Throwable) {
            null
        }
    }

    fun fetch(ctx: Context, p: ModulePrefs, lat: Double?, lon: Double?): Weather? {
        val source = p.weatherSource
        if (source == WeatherSources.SYSTEM) return SystemWeather.fetch(ctx)
        if (lat == null || lon == null) return null
        val key = p.weatherKey(source).trim()
        if (WeatherSources.needsKey(source) && key.isEmpty()) return null
        return try {
            when (source) {
                WeatherSources.ACCU -> fetchAccu(lat, lon, key)
                WeatherSources.OWM -> fetchOwm(lat, lon, key)
                WeatherSources.WAPI -> fetchWeatherApi(lat, lon, key)
                WeatherSources.METNO -> fetchMet(lat, lon)
                WeatherSources.WTTR -> fetchWttr(lat, lon)
                WeatherSources.OM_ECMWF -> fetchOpenMeteo(lat, lon, "ecmwf_ifs025") ?: fetchOpenMeteo(lat, lon, null)
                WeatherSources.OM_ICON -> fetchOpenMeteo(lat, lon, "icon_seamless") ?: fetchOpenMeteo(lat, lon, null)
                else -> fetchOpenMeteo(lat, lon, null)
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun fetchOpenMeteo(lat: Double, lon: Double, model: String?): Weather? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1" +
            (if (model != null) "&models=$model" else "")
        val body = get(url) ?: return null
        val root = JSONObject(body)
        val cur = root.optJSONObject("current") ?: return null
        if (cur.isNull("temperature_2m")) return null
        val daily = root.optJSONObject("daily")
        return Weather(
            temp = cur.optDouble("temperature_2m"),
            feels = cur.optDouble("apparent_temperature"),
            code = cur.optInt("weather_code"),
            day = cur.optInt("is_day", 1) == 1,
            high = daily?.optJSONArray("temperature_2m_max")?.optDouble(0) ?: cur.optDouble("temperature_2m"),
            low = daily?.optJSONArray("temperature_2m_min")?.optDouble(0) ?: cur.optDouble("temperature_2m"),
            humidity = cur.optInt("relative_humidity_2m"),
            wind = cur.optDouble("wind_speed_10m")
        )
    }

    private fun fetchAccu(lat: Double, lon: Double, key: String): Weather? {
        val k = URLEncoder.encode(key, "UTF-8")
        val sig = "$lat|$lon"
        if (accuLocSig != sig || accuLocKey.isBlank()) {
            val loc = get("$ACCU_BASE/locations/v1/cities/geoposition/search?apikey=$k&q=$lat%2C$lon") ?: return null
            val id = JSONObject(loc).optString("Key")
            if (id.isBlank()) return null
            accuLocKey = id
            accuLocSig = sig
        }
        val id = accuLocKey
        val body = get("$ACCU_BASE/currentconditions/v1/$id?apikey=$k&details=true") ?: return null
        val c = JSONArray(body).optJSONObject(0) ?: return null
        val temp = num(c.optJSONObject("Temperature")?.optJSONObject("Metric"), "Value", Double.NaN)
        if (temp.isNaN()) return null
        val feels = num(c.optJSONObject("RealFeelTemperature")?.optJSONObject("Metric"), "Value", temp)
        val wind = num(c.optJSONObject("Wind")?.optJSONObject("Speed")?.optJSONObject("Metric"), "Value", 0.0)
        val nowMs = System.currentTimeMillis()
        if (accuRangeSig != sig || nowMs - accuRangeAt > 6 * 3_600_000L) {
            val fc = get("$ACCU_BASE/forecasts/v1/daily/1day/$id?apikey=$k&metric=true")
            val day = fc?.let { JSONObject(it).optJSONArray("DailyForecasts")?.optJSONObject(0) }
            if (day != null) {
                val t = day.optJSONObject("Temperature")
                accuHigh = num(t?.optJSONObject("Maximum"), "Value", temp)
                accuLow = num(t?.optJSONObject("Minimum"), "Value", temp)
                accuRangeSig = sig
                accuRangeAt = nowMs
            }
        }
        return Weather(
            temp = temp,
            feels = feels,
            code = accuCode(c.optInt("WeatherIcon")),
            day = c.optBoolean("IsDayTime", true),
            high = if (accuRangeSig == sig && !accuHigh.isNaN()) accuHigh else temp,
            low = if (accuRangeSig == sig && !accuLow.isNaN()) accuLow else temp,
            humidity = c.optInt("RelativeHumidity"),
            wind = wind
        )
    }

    private fun accuCode(icon: Int): Int = when (icon) {
        1, 2, 30, 31, 33 -> 0
        5, 34, 37 -> 1
        3, 4, 35, 36 -> 2
        6, 7, 8, 32, 38 -> 3
        11 -> 45
        12, 13, 14, 39, 40 -> 80
        15, 16, 17, 41, 42 -> 95
        18 -> 63
        19, 20, 21, 43 -> 71
        22, 23, 44 -> 73
        24, 25, 26, 29 -> 67
        else -> 3
    }

    private fun fetchOwm(lat: Double, lon: Double, key: String): Weather? {
        val k = URLEncoder.encode(key, "UTF-8")
        val body = get("https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=metric&appid=$k") ?: return null
        val root = JSONObject(body)
        val main = root.optJSONObject("main") ?: return null
        val temp = num(main, "temp", Double.NaN)
        if (temp.isNaN()) return null
        val w0 = root.optJSONArray("weather")?.optJSONObject(0)
        return Weather(
            temp = temp,
            feels = num(main, "feels_like", temp),
            code = owmCode(w0?.optInt("id") ?: 800),
            day = w0?.optString("icon").orEmpty().endsWith("d") || w0?.optString("icon").isNullOrEmpty(),
            high = num(main, "temp_max", temp),
            low = num(main, "temp_min", temp),
            humidity = main.optInt("humidity"),
            wind = num(root.optJSONObject("wind"), "speed", 0.0) * 3.6
        )
    }

    private fun owmCode(id: Int): Int = when {
        id in 200..232 -> 95
        id in 300..321 -> 53
        id == 500 || id == 501 -> 61
        id in 502..504 -> 65
        id == 511 -> 67
        id == 520 || id == 521 -> 80
        id == 522 || id == 531 -> 82
        id in 600..622 -> 73
        id in 700..781 -> 45
        id == 800 -> 0
        id == 801 -> 1
        id == 802 -> 2
        id == 803 || id == 804 -> 3
        else -> 3
    }

    private fun fetchWeatherApi(lat: Double, lon: Double, key: String): Weather? {
        val k = URLEncoder.encode(key, "UTF-8")
        val body = get("https://api.weatherapi.com/v1/forecast.json?key=$k&q=$lat,$lon&days=1&aqi=no&alerts=no") ?: return null
        val root = JSONObject(body)
        val cur = root.optJSONObject("current") ?: return null
        val temp = num(cur, "temp_c", Double.NaN)
        if (temp.isNaN()) return null
        val day = root.optJSONObject("forecast")?.optJSONArray("forecastday")?.optJSONObject(0)?.optJSONObject("day")
        return Weather(
            temp = temp,
            feels = num(cur, "feelslike_c", temp),
            code = wapiCode(cur.optJSONObject("condition")?.optInt("code") ?: 1000),
            day = cur.optInt("is_day", 1) == 1,
            high = num(day, "maxtemp_c", temp),
            low = num(day, "mintemp_c", temp),
            humidity = cur.optInt("humidity"),
            wind = num(cur, "wind_kph", 0.0)
        )
    }

    private fun wapiCode(c: Int): Int = when (c) {
        1000 -> 0
        1003 -> 2
        1006, 1009 -> 3
        1030, 1135, 1147 -> 45
        1150, 1153, 1168, 1171 -> 51
        1063, 1180, 1183, 1186, 1189 -> 61
        1192, 1195 -> 65
        1240 -> 80
        1243, 1246 -> 82
        1087, 1273, 1276, 1279, 1282 -> 95
        1066, 1114, 1117, 1210, 1213, 1216, 1219, 1222, 1225, 1255, 1258 -> 73
        1069, 1072, 1198, 1201, 1204, 1207, 1237, 1249, 1252, 1261, 1264 -> 67
        else -> 3
    }

    private fun fetchWttr(lat: Double, lon: Double): Weather? {
        val body = get("https://wttr.in/$lat,$lon?format=j1") ?: return null
        val root = JSONObject(body)
        val cur = root.optJSONArray("current_condition")?.optJSONObject(0) ?: return null
        val temp = cur.optString("temp_C").toDoubleOrNull() ?: return null
        val today = root.optJSONArray("weather")?.optJSONObject(0)
        val astro = today?.optJSONArray("astronomy")?.optJSONObject(0)
        return Weather(
            temp = temp,
            feels = cur.optString("FeelsLikeC").toDoubleOrNull() ?: temp,
            code = wwoCode(cur.optString("weatherCode").toIntOrNull() ?: 119),
            day = wttrDay(cur, astro),
            high = today?.optString("maxtempC")?.toDoubleOrNull() ?: temp,
            low = today?.optString("mintempC")?.toDoubleOrNull() ?: temp,
            humidity = cur.optString("humidity").toIntOrNull() ?: 0,
            wind = cur.optString("windspeedKmph").toDoubleOrNull() ?: 0.0
        )
    }

    private fun wttrDay(cur: JSONObject, astro: JSONObject?): Boolean {
        return try {
            val fmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
            val obs = LocalTime.parse(cur.optString("localObsDateTime").substringAfter(" ").trim(), fmt)
            val rise = LocalTime.parse(astro?.optString("sunrise").orEmpty().trim(), fmt)
            val set = LocalTime.parse(astro?.optString("sunset").orEmpty().trim(), fmt)
            obs.isAfter(rise) && obs.isBefore(set)
        } catch (e: Throwable) {
            true
        }
    }

    private fun wwoCode(c: Int): Int = when (c) {
        113 -> 0
        116 -> 2
        119, 122 -> 3
        143, 248 -> 45
        260 -> 48
        263, 266 -> 51
        281, 284 -> 56
        176, 353 -> 80
        356 -> 81
        359 -> 82
        293, 296 -> 61
        299, 302 -> 63
        305, 308 -> 65
        311, 314, 182, 185, 317, 320, 362, 365 -> 67
        179, 323, 326 -> 71
        227, 329, 332 -> 73
        230, 335, 338 -> 75
        350, 374, 377 -> 77
        368 -> 85
        371 -> 86
        200, 386, 389, 392, 395 -> 95
        else -> 3
    }

    private fun fetchMet(lat: Double, lon: Double): Weather? {
        val url = String.format(Locale.US, "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f", lat, lon)
        val body = get(url) ?: return null
        val series = JSONObject(body).optJSONObject("properties")?.optJSONArray("timeseries") ?: return null
        val first = series.optJSONObject(0)?.optJSONObject("data") ?: return null
        val details = first.optJSONObject("instant")?.optJSONObject("details") ?: return null
        val temp = num(details, "air_temperature", Double.NaN)
        if (temp.isNaN()) return null
        val humidity = num(details, "relative_humidity", 50.0)
        val windMs = num(details, "wind_speed", 0.0)
        var hi = temp
        var lo = temp
        for (i in 0 until minOf(series.length(), 24)) {
            val t = num(series.optJSONObject(i)?.optJSONObject("data")?.optJSONObject("instant")?.optJSONObject("details"), "air_temperature", temp)
            if (t > hi) hi = t
            if (t < lo) lo = t
        }
        val symbol = first.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")
            ?: first.optJSONObject("next_6_hours")?.optJSONObject("summary")?.optString("symbol_code")
            ?: "cloudy"
        val vapour = humidity / 100.0 * 6.105 * Math.exp(17.27 * temp / (237.7 + temp))
        return Weather(
            temp = temp,
            feels = temp + 0.33 * vapour - 0.70 * windMs - 4.0,
            code = metCode(symbol),
            day = !symbol.endsWith("_night"),
            high = hi,
            low = lo,
            humidity = humidity.toInt(),
            wind = windMs * 3.6
        )
    }

    private fun metCode(sym: String): Int {
        val s = sym.substringBefore("_")
        return when {
            s.contains("thunder") -> 95
            s.contains("sleet") -> 67
            s.contains("snow") -> 73
            s.startsWith("heavyrainshowers") -> 82
            s.contains("showers") -> 80
            s.startsWith("heavyrain") -> 65
            s.startsWith("lightrain") -> 61
            s.contains("rain") -> 63
            s == "clearsky" -> 0
            s == "fair" -> 1
            s == "partlycloudy" -> 2
            s == "cloudy" -> 3
            s == "fog" -> 45
            else -> 3
        }
    }
}

object WeatherTracker {
    @Volatile var current: Weather? = null
    @Volatile private var busy = false
    @Volatile private var lastFetch = 0L
    @Volatile private var trackedKey = ""
    private val pool = Executors.newSingleThreadExecutor()

    fun reset() {
        current = null
        lastFetch = 0L
        trackedKey = ""
    }

    fun refresh(ctx: Context, p: ModulePrefs) {
        val source = p.weatherSource
        val system = source == WeatherSources.SYSTEM
        val lat = p.weatherLat.toDoubleOrNull()
        val lon = p.weatherLon.toDoubleOrNull()
        if (!system && (lat == null || lon == null)) return
        val key = if (system) "system" else "$lat|$lon|$source|" + p.weatherKey(source)
        if (key != trackedKey) {
            reset()
            trackedKey = key
        }
        val now = SystemClock.elapsedRealtime()
        val steady = if (source == WeatherSources.ACCU) 60 * 60_000L else 15 * 60_000L
        val interval = if (current == null) 30_000L else steady
        if (busy || now - lastFetch < interval) return
        busy = true
        pool.execute {
            try {
                WeatherApi.fetch(ctx, p, lat, lon)?.let { current = it }
            } catch (e: Throwable) {
                CrashLog.append(ctx, "weather.refresh", e)
            } finally {
                lastFetch = SystemClock.elapsedRealtime()
                busy = false
            }
        }
    }
}
