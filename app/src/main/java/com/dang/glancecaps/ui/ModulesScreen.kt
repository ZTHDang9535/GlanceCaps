package com.dang.glancecaps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.dang.glancecaps.R
import com.dang.glancecaps.battery.BatterySnapshot
import com.dang.glancecaps.battery.fmt1
import com.dang.glancecaps.battery.formatTemp
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.island.IslandText
import com.dang.glancecaps.steps.StepReading

@Composable
fun ModulesScreen(
    s: BatterySnapshot,
    steps: StepReading?,
    prefs: ModulePrefs,
    onChanged: () -> Unit,
    onStepsToggled: (Boolean) -> Unit,
    onConnToggled: (Boolean) -> Unit,
    onMatchToggled: (Boolean) -> Unit,
    onWeatherToggled: (Boolean) -> Unit,
    onOpen: (Int) -> Unit,
) {
    val view = LocalView.current
    var chargingOn by remember { mutableStateOf(prefs.chargingEnabled) }
    var tempOn by remember { mutableStateOf(prefs.tempEnabled) }
    var stepsOn by remember { mutableStateOf(prefs.stepsEnabled) }
    var connOn by remember { mutableStateOf(prefs.connEnabled) }
    var matchOn by remember { mutableStateOf(prefs.matchEnabled) }
    var weatherOn by remember { mutableStateOf(prefs.weatherEnabled) }

    val chargingLine = when {
        !chargingOn -> "Off"
        s.charging -> "${fmt1(s.watts)} W  -  ${s.level}%"
        else -> "Waiting for charger"
    }
    val tempLine = when {
        !tempOn -> "Off"
        else -> formatTemp(s.tempC, prefs.tempUseF) + if (prefs.tempMerge) "  -  Merged" else "  -  Separate"
    }
    val stepsLine = when {
        !stepsOn -> "Off"
        steps != null -> "${IslandText.count(steps.steps)} / ${IslandText.count(prefs.stepGoal)}"
        else -> "Permission required"
    }

    val connLine = if (!connOn) "Off" else listOfNotNull(
        "Wi-Fi".takeIf { prefs.connWifi },
        "Bluetooth".takeIf { prefs.connBluetooth },
        "Airplane".takeIf { prefs.connAirplane }
    ).joinToString("  -  ").ifEmpty { "No events selected" }

    ScreenFrame(title = "Modules", subtitle = "Everything important, at a glance...", spacing = 16.dp) {

        Group {
            RowItem(
                icon = R.drawable.ic_bolt,
                title = "Charging",
                subtitle = chargingLine,
                onClick = { Haptics.tick(view); onOpen(1) }
            ) {
                HSwitch(chargingOn) {
                    chargingOn = it
                    prefs.chargingEnabled = it
                    onChanged()
                }
            }
            RowDivider()
            RowItem(
                icon = R.drawable.ic_thermostat,
                title = "Temperature",
                subtitle = tempLine,
                onClick = { Haptics.tick(view); onOpen(2) }
            ) {
                HSwitch(tempOn) {
                    tempOn = it
                    prefs.tempEnabled = it
                    onChanged()
                }
            }
            RowDivider()
            RowItem(
                icon = R.drawable.ic_directions_walk,
                title = "Steps",
                subtitle = stepsLine,
                onClick = { Haptics.tick(view); onOpen(3) }
            ) {
                HSwitch(stepsOn) {
                    stepsOn = it
                    prefs.stepsEnabled = it
                    onStepsToggled(it)
                }
            }
            RowDivider()
            RowItem(
                icon = R.drawable.ic_wifi_on,
                title = "Connectivity",
                subtitle = connLine,
                onClick = { Haptics.tick(view); onOpen(4) }
            ) {
                HSwitch(connOn) {
                    connOn = it
                    prefs.connEnabled = it
                    onConnToggled(it)
                }
            }
            RowDivider()
            RowItem(
                icon = R.drawable.ic_soccer,
                title = "Matchday",
                subtitle = if (!matchOn) "Off" else if (prefs.matchLabel.isBlank()) "Pick a match" else prefs.matchLabel,
                onClick = { Haptics.tick(view); onOpen(5) }
            ) {
                HSwitch(matchOn) {
                    matchOn = it
                    prefs.matchEnabled = it
                    onMatchToggled(it)
                }
            }
            RowDivider()
            RowItem(
                icon = R.drawable.ic_wx_partly,
                title = "Weather",
                subtitle = if (!weatherOn) "Off" else if (prefs.weatherSource == "system") "System weather app" else if (prefs.weatherCity.isBlank()) "Pick a city" else prefs.weatherCity.substringBefore(","),
                onClick = { Haptics.tick(view); onOpen(6) }
            ) {
                HSwitch(weatherOn) {
                    weatherOn = it
                    prefs.weatherEnabled = it
                    onWeatherToggled(it)
                }
            }
        }
    }
}
