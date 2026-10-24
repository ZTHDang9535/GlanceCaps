package com.dang.glancecaps.weather

import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.dang.glancecaps.CrashLog
import java.util.Calendar

object SystemWeather {
    private val uri: Uri = Uri.parse("content://weather/actualWeatherData/1")
    private val numberPattern = Regex("-?\\d+(\\.\\d+)?")

    @Volatile var city: String = ""
    @Volatile private var logged = false

    fun fetch(ctx: Context): Weather? {
        return try {
            val cursor = ctx.contentResolver.query(uri, null, null, null, null)
            if (cursor == null) {
                note(ctx, IllegalStateException("System weather provider returned no data"))
                null
            } else {
                cursor.use { read(ctx, it) }
            }
        } catch (e: Throwable) {
            note(ctx, e)
            null
        }
    }

    private fun note(ctx: Context, e: Throwable) {
        if (logged) return
        logged = true
        CrashLog.append(ctx, "weather.system", e)
    }

    private fun read(ctx: Context, c: Cursor): Weather? {
        if (!c.moveToFirst()) {
            note(ctx, IllegalStateException("System weather provider is empty"))
            return null
        }
        val names = c.columnNames.map { it.lowercase() }
        fun str(vararg keys: String): String? {
            for (k in keys) {
                val i = names.indexOf(k)
                if (i >= 0) {
                    val v = try {
                        c.getString(i)
                    } catch (e: Throwable) {
                        null
                    }
                    if (!v.isNullOrBlank()) return v
                }
            }
            return null
        }
        fun num(vararg keys: String): Double? = str(*keys)?.let { numberPattern.find(it)?.value?.toDoubleOrNull() }

        val temp = num("temperature", "temp", "current_temperature", "currenttemp")
        if (temp == null) {
            note(ctx, IllegalStateException("Unrecognised weather columns: " + names.joinToString()))
            return null
        }
        val description = str("description", "weather_description", "weather_name", "weather_text").orEmpty()
        val type = num("weather_type", "weathertype", "type", "weather_code")?.toInt()
        val code = if (type != null) miuiCode(type) else textCode(description)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        city = str("city_name", "cityname", "city", "name").orEmpty()
        return Weather(
            temp = temp,
            feels = num("feels_like", "apparent_temperature", "sensible_temp", "body_temperature", "bodytemp") ?: temp,
            code = code,
            day = hour in 6..17,
            high = num("tmphighs", "temp_high", "tmphigh", "high_temp", "max_temp") ?: temp,
            low = num("tmplows", "temp_low", "tmplow", "low_temp", "min_temp") ?: temp,
            humidity = num("humidity")?.toInt() ?: 0,
            wind = 0.0
        )
    }

    private fun miuiCode(t: Int): Int = when (t) {
        0 -> 0
        1 -> 2
        2 -> 3
        3 -> 80
        4 -> 95
        5 -> 96
        6 -> 67
        7, 21 -> 61
        8, 22 -> 63
        9, 10, 11, 12, 23, 24, 25 -> 65
        13 -> 85
        14, 26 -> 71
        15, 27 -> 73
        16, 17, 28 -> 75
        18, 53 -> 45
        19 -> 67
        else -> 3
    }

    private fun textCode(text: String): Int {
        val t = text.lowercase()
        return when {
            t.contains("thunder") -> 95
            t.contains("snow") -> 73
            t.contains("sleet") -> 67
            t.contains("shower") -> 80
            t.contains("rain") -> 63
            t.contains("fog") || t.contains("haze") || t.contains("mist") -> 45
            t.contains("overcast") -> 3
            t.contains("cloud") -> 2
            t.contains("sun") || t.contains("clear") -> 0
            else -> 3
        }
    }
}
