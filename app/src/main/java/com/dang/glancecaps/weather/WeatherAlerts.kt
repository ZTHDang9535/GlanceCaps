package com.dang.glancecaps.weather

import android.content.Context
import android.os.SystemClock
import com.dang.glancecaps.CrashLog
import com.dang.glancecaps.data.ModulePrefs
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class WeatherAlert(
    val id: String,
    val kind: String,
    val title: String,
    val detail: String,
    val level: Int,
    val distanceKm: Int,
    val source: String,
)

object AlertApi {
    private fun get(url: String): String? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "GlanceCaps/1.0 (github.com/ZTHDang9535/GlanceCaps)")
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Throwable) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }

    private fun firstPoint(a: JSONArray?): Pair<Double, Double>? {
        if (a == null || a.length() == 0) return null
        if (a.length() >= 2 && a.opt(0) is Number) return a.optDouble(0) to a.optDouble(1)
        return firstPoint(a.optJSONArray(0))
    }

    fun fetch(lat: Double, lon: Double, radiusKm: Int, minLevel: Int): List<WeatherAlert>? {
        val parts = listOf(gdacs(lat, lon, radiusKm), usgs(lat, lon, radiusKm), nws(lat, lon))
        if (parts.all { it == null }) return null
        return parts.filterNotNull().flatten()
            .filter { it.level >= minLevel }
            .sortedWith(compareByDescending<WeatherAlert> { it.level }.thenBy { it.distanceKm })
    }

    private fun gdacsKind(type: String): String = when (type) {
        "TC" -> "Cyclone"
        "FL" -> "Flood"
        "EQ" -> "Earthquake"
        "VO" -> "Volcano"
        "WF" -> "Wildfire"
        else -> "Alert"
    }

    private fun gdacs(lat: Double, lon: Double, radius: Int): List<WeatherAlert>? {
        val to = LocalDate.now(ZoneOffset.UTC)
        val from = to.minusDays(4)
        val url = "https://www.gdacs.org/gdacsapi/api/events/geteventlist/SEARCH" +
            "?eventlist=TC;FL;EQ;VO;WF&alertlevel=Orange;Red&fromdate=$from&todate=$to"
        val body = get(url) ?: return null
        return try {
            val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
            val out = ArrayList<WeatherAlert>()
            for (i in 0 until features.length()) {
                val f = features.optJSONObject(i) ?: continue
                val props = f.optJSONObject("properties") ?: continue
                if (props.optString("iscurrent") == "false") continue
                val point = firstPoint(f.optJSONObject("geometry")?.optJSONArray("coordinates")) ?: continue
                val dist = distanceKm(lat, lon, point.second, point.first)
                val type = props.optString("eventtype")
                val reach = if (type == "TC") radius * 2.0 else radius.toDouble()
                if (dist > reach) continue
                val red = props.optString("alertlevel").equals("Red", ignoreCase = true)
                val kind = gdacsKind(type)
                val name = props.optString("name")
                val severity = props.optJSONObject("severitydata")?.optString("severitytext").orEmpty()
                out.add(
                    WeatherAlert(
                        id = "gdacs:$type:" + props.optString("eventid"),
                        kind = kind,
                        title = name.ifBlank { kind },
                        detail = severity.ifBlank { props.optString("description") },
                        level = if (red) 2 else 1,
                        distanceKm = dist.roundToInt(),
                        source = "GDACS"
                    )
                )
            }
            out
        } catch (e: Throwable) {
            null
        }
    }

    private fun usgs(lat: Double, lon: Double, radius: Int): List<WeatherAlert>? {
        val body = get("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_day.geojson") ?: return null
        return try {
            val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
            val out = ArrayList<WeatherAlert>()
            for (i in 0 until features.length()) {
                val f = features.optJSONObject(i) ?: continue
                val props = f.optJSONObject("properties") ?: continue
                val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                if (coords.length() < 2) continue
                val mag = props.optDouble("mag")
                if (mag.isNaN()) continue
                val dist = distanceKm(lat, lon, coords.optDouble(1), coords.optDouble(0))
                val near = mag >= 5.0 && dist <= radius
                val far = mag >= 6.5 && dist <= radius * 2.0
                if (!near && !far) continue
                val level = when {
                    mag >= 6.5 -> 2
                    mag >= 5.5 -> 1
                    else -> 0
                }
                val tsunami = props.optInt("tsunami") == 1
                out.add(
                    WeatherAlert(
                        id = "usgs:" + f.optString("id"),
                        kind = "Earthquake",
                        title = String.format(Locale.US, "M%.1f earthquake", mag),
                        detail = props.optString("place") + if (tsunami) "  -  Tsunami possible" else "",
                        level = level,
                        distanceKm = dist.roundToInt(),
                        source = "USGS"
                    )
                )
            }
            out
        } catch (e: Throwable) {
            null
        }
    }

    private fun nws(lat: Double, lon: Double): List<WeatherAlert>? {
        if (lat < 17.0 || lat > 72.0 || lon < -180.0 || lon > -64.0) return null
        val url = String.format(Locale.US, "https://api.weather.gov/alerts/active?point=%.4f%%2C%.4f", lat, lon)
        val body = get(url) ?: return null
        return try {
            val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
            val out = ArrayList<WeatherAlert>()
            for (i in 0 until features.length()) {
                val props = features.optJSONObject(i)?.optJSONObject("properties") ?: continue
                val level = when (props.optString("severity")) {
                    "Extreme" -> 2
                    "Severe" -> 1
                    "Moderate" -> 0
                    else -> continue
                }
                val event = props.optString("event")
                out.add(
                    WeatherAlert(
                        id = "nws:" + props.optString("id"),
                        kind = event,
                        title = event,
                        detail = props.optString("headline").ifBlank { props.optString("areaDesc") },
                        level = level,
                        distanceKm = 0,
                        source = "NWS"
                    )
                )
            }
            out
        } catch (e: Throwable) {
            null
        }
    }
}

object AlertTracker {
    @Volatile var current: WeatherAlert? = null
    @Volatile private var busy = false
    @Volatile private var nextFetch = 0L
    @Volatile private var trackedKey = ""
    private val pool = Executors.newSingleThreadExecutor()

    fun reset() {
        current = null
        nextFetch = 0L
        trackedKey = ""
    }

    fun refresh(ctx: Context, p: ModulePrefs) {
        val lat = p.weatherLat.toDoubleOrNull() ?: return
        val lon = p.weatherLon.toDoubleOrNull() ?: return
        val radius = p.alertRadiusKm
        val minLevel = p.alertMinLevel
        val key = "$lat|$lon|$radius|$minLevel"
        if (key != trackedKey) {
            reset()
            trackedKey = key
        }
        val now = SystemClock.elapsedRealtime()
        if (busy || now < nextFetch) return
        busy = true
        pool.execute {
            var wait = 5 * 60_000L
            try {
                val list = AlertApi.fetch(lat, lon, radius, minLevel)
                if (list != null) {
                    current = list.firstOrNull()
                    wait = 20 * 60_000L
                }
            } catch (e: Throwable) {
                CrashLog.append(ctx, "alerts.refresh", e)
            } finally {
                nextFetch = SystemClock.elapsedRealtime() + wait
                busy = false
            }
        }
    }
}
