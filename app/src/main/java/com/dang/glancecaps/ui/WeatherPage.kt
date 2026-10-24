package com.dang.glancecaps.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.island.GlanceCapsNotifications
import com.dang.glancecaps.weather.City
import com.dang.glancecaps.weather.SystemWeather
import com.dang.glancecaps.weather.Weather
import kotlin.math.roundToInt
import com.dang.glancecaps.weather.AlertTracker
import com.dang.glancecaps.weather.WeatherApi
import com.dang.glancecaps.weather.WeatherCodes
import com.dang.glancecaps.weather.WeatherSources
import com.dang.glancecaps.weather.WeatherTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private val sampleWeather = Weather(24.0, 26.0, 2, true, 28.0, 21.0, 64, 12.0)

@Composable
fun WeatherPage(prefs: ModulePrefs, onBack: () -> Unit, onChanged: () -> Unit) {
    val view = LocalView.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var searchTick by remember { mutableIntStateOf(0) }
    var cityKey by remember { mutableStateOf(prefs.weatherLat + prefs.weatherLon) }
    var current by remember { mutableStateOf<Weather?>(null) }
    var mode by remember { mutableStateOf(prefs.weatherMode) }
    var hour by remember { mutableIntStateOf(prefs.weatherHour) }
    var minute by remember { mutableIntStateOf(prefs.weatherMinute) }
    var duration by remember { mutableIntStateOf(prefs.weatherDuration) }
    var source by remember { mutableStateOf(prefs.weatherSource) }
    var apiKey by remember { mutableStateOf(prefs.weatherKey(prefs.weatherSource)) }
    var failed by remember { mutableStateOf(false) }
    var alertsOn by remember { mutableStateOf(prefs.alertsEnabled) }
    var alertLevel by remember { mutableStateOf(prefs.alertMinLevel) }
    var alertRadius by remember { mutableStateOf(prefs.alertRadiusKm) }
    val wallpaper = rememberWallpaper()
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    val bump: () -> Unit = { rev++; onChanged() }

    LaunchedEffect(searchTick) {
        if (searchTick == 0) return@LaunchedEffect
        status = "Searching"
        val found = withContext(Dispatchers.IO) { WeatherApi.search(query) }
        results = found ?: emptyList()
        status = when {
            found == null -> "Could not reach the weather service"
            found.isEmpty() -> "No city found"
            else -> ""
        }
    }

    LaunchedEffect(cityKey, source, apiKey) {
        val lat = prefs.weatherLat.toDoubleOrNull()
        val lon = prefs.weatherLon.toDoubleOrNull()
        if (source != WeatherSources.SYSTEM && (lat == null || lon == null)) {
            current = null
            failed = false
            return@LaunchedEffect
        }
        if (WeatherSources.needsKey(source) && apiKey.isBlank()) {
            current = null
            failed = false
            return@LaunchedEffect
        }
        delay(600)
        val fetched = withContext(Dispatchers.IO) { WeatherApi.fetch(ctx, prefs, lat, lon) }
        current = fetched
        failed = fetched == null
    }

    ScreenFrame(title = "Weather", spacing = 14.dp, onBack = onBack) {
        PreviewStage(wallpaper) { WeatherPreview(current ?: sampleWeather, prefs, rev) }

        GroupLabel("Tracking")
        Group {
            ToggleRow(
                "Show weather",
                (if (source == WeatherSources.SYSTEM) SystemWeather.city else prefs.weatherCity).ifBlank { null },
                { prefs.weatherEnabled }, { prefs.weatherEnabled = it }, bump
            )
        }

        GroupLabel("Alerts")
        Group {
            ToggleRow(
                "Weather alerts", null,
                { prefs.alertsEnabled },
                { prefs.alertsEnabled = it },
                { alertsOn = prefs.alertsEnabled; AlertTracker.reset(); bump() }
            )
            if (alertsOn) {
                RowDivider()
                ChoiceRow(
                    title = "Alert level",
                    subtitle = null,
                    options = listOf("0" to "All", "1" to "Orange and red", "2" to "Red only"),
                    selected = alertLevel.toString()
                ) { key ->
                    alertLevel = key.toInt()
                    prefs.alertMinLevel = alertLevel
                    AlertTracker.reset()
                }
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Radius", fontSize = 17.sp)
                        Text("$alertRadius km", color = MaterialTheme.colorScheme.primary)
                    }
                    GlanceSlider(
                        value = alertRadius.toFloat(),
                        onValueChange = {
                            val n = (it / 50f).roundToInt() * 50
                            if (n != alertRadius) {
                                alertRadius = n
                                prefs.alertRadiusKm = n
                                AlertTracker.reset()
                            }
                        },
                        valueRange = 50f..1000f,
                        steps = 18
                    )
                }
            }
        }

        GroupLabel("Island")
        Group {
            IslandPaddingRows({ prefs.weatherPadAuto }, { prefs.weatherPadAuto = it }, { prefs.weatherPadLen }, { prefs.weatherPadLen = it }, bump)
        }

        GroupLabel("Source")
        Group {
            ChoiceRow(
                title = "Weather source",
                subtitle = null,
                options = WeatherSources.options(GlanceCapsNotifications.isXiaomiFamily()),
                selected = source
            ) { key ->
                source = key
                prefs.weatherSource = key
                apiKey = prefs.weatherKey(key)
                WeatherTracker.reset()
                bump()
            }
            if (WeatherSources.needsKey(source)) {
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            prefs.setWeatherKey(source, it.trim())
                            WeatherTracker.reset()
                            onChanged()
                        },
                        singleLine = true,
                        placeholder = { Text("API key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (failed) {
                    RowDivider()
                    RowItem(icon = null, title = "No data from this source", subtitle = "Check the API key")
                }
            } else if (failed) {
                RowDivider()
                if (source == WeatherSources.SYSTEM) {
                    RowItem(icon = null, title = "Cannot read the system weather app", subtitle = "Open it once, then try again")
                } else {
                    RowItem(icon = null, title = "No data from this source", subtitle = null)
                }
            }
        }

        GroupLabel("Details")
        Group {
            ToggleRow("Condition", null, { prefs.weatherShowCondition }, { prefs.weatherShowCondition = it }, bump)
            RowDivider()
            ToggleRow("Feels like", null, { prefs.weatherShowFeels }, { prefs.weatherShowFeels = it }, bump)
            RowDivider()
            ToggleRow("High and low", null, { prefs.weatherShowRange }, { prefs.weatherShowRange = it }, bump)
            RowDivider()
            ToggleRow("Humidity", null, { prefs.weatherShowHumidity }, { prefs.weatherShowHumidity = it }, bump)
            RowDivider()
            ToggleRow("Wind", null, { prefs.weatherShowWind }, { prefs.weatherShowWind = it }, bump)
            RowDivider()
            ToggleRow("City name", null, { prefs.weatherShowCity }, { prefs.weatherShowCity = it }, bump)
        }

        GroupLabel("When to show")
        Group {
            ChoiceRow(
                title = "Show",
                subtitle = null,
                options = listOf("always" to "Any time", "scheduled" to "At a set time"),
                selected = mode
            ) { key ->
                mode = key
                prefs.weatherMode = key
                bump()
            }
            if (mode == "scheduled") {
                RowDivider()
                RowItem(icon = null, title = "Start time", subtitle = null) {
                    TextButton(onClick = {
                        Haptics.tick(view)
                        TimePickerDialog(ctx, { _, h, m ->
                            hour = h
                            minute = m
                            prefs.weatherHour = h
                            prefs.weatherMinute = m
                            bump()
                        }, hour, minute, DateFormat.is24HourFormat(ctx)).show()
                    }) { Text(String.format(Locale.US, "%02d:%02d", hour, minute)) }
                }
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Stays visible", fontSize = 17.sp)
                        Text("$duration min", color = MaterialTheme.colorScheme.primary)
                    }
                    GlanceSlider(
                        value = duration.toFloat(),
                        onValueChange = {
                            val n = it.toInt()
                            if (n != duration) {
                                duration = n
                                prefs.weatherDuration = n
                                bump()
                            }
                        },
                        valueRange = 5f..120f,
                        steps = 22
                    )
                }
            }
        }

        if (source != WeatherSources.SYSTEM || alertsOn) GroupLabel("City")
        if (source != WeatherSources.SYSTEM || alertsOn) Group {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search for a city") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) searchTick++ })
                )
            }
            if (status.isNotEmpty()) {
                RowDivider()
                RowItem(icon = null, title = status, subtitle = null)
            }
            results.forEach { c ->
                RowDivider()
                RowItem(
                    icon = null,
                    title = c.name,
                    subtitle = listOf(c.region, c.country).filter { it.isNotBlank() }.joinToString(", "),
                    onClick = {
                        Haptics.tick(view)
                        prefs.weatherCity = c.label
                        prefs.weatherLat = c.lat.toString()
                        prefs.weatherLon = c.lon.toString()
                        prefs.weatherEnabled = true
                        WeatherTracker.reset()
                        cityKey = prefs.weatherLat + prefs.weatherLon
                        results = emptyList()
                        query = ""
                        bump()
                    }
                )
            }
        }

        OtherModules(6)
    }
}
