package com.dang.glancecaps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dang.glancecaps.R
import com.dang.glancecaps.island.GlanceCapsNotifications
import com.dang.glancecaps.steps.HealthSteps
import com.dang.glancecaps.battery.BatterySnapshot
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.island.ConnectivityMonitor
import com.dang.glancecaps.island.IslandSupport
import com.dang.glancecaps.island.IslandText
import com.dang.glancecaps.steps.StepReading
import com.dang.glancecaps.steps.Steps
import kotlin.math.roundToInt

@Composable
private fun PageScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ScreenFrame(title = title, spacing = 14.dp, onBack = onBack, content = content)
}

@Composable
fun ChargingPage(
    s: BatterySnapshot,
    eta: Int?,
    prefs: ModulePrefs,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    var rev by remember { mutableStateOf(0) }
    val bump: () -> Unit = { rev++; onChanged() }
    val wallpaper = rememberWallpaper()

    val hyper = GlanceCapsNotifications.useHyper(prefs)
    PageScaffold("Charging", onBack) {
        PreviewStage(wallpaper) { ChargingPreview(s, eta, prefs, rev) }

        GroupLabel("Island content")
        Group {
            ToggleRow("Charging power", null, { prefs.chgShowWatts }, { prefs.chgShowWatts = it }, bump)
            RowDivider()
            ToggleRow("Battery percentage", null, { prefs.chgShowPercent }, { prefs.chgShowPercent = it }, bump)
            RowDivider()
            ToggleRow("Finish time", null, { prefs.chgShowClock }, { prefs.chgShowClock = it }, bump)
            RowDivider()
            ToggleRow("Time left", null, { prefs.chgShowRemaining }, { prefs.chgShowRemaining = it }, bump)
        }

        GroupLabel("Appearance")
        Group {
            ToggleRow("Charging icon", null, { prefs.chgShowIcon }, { prefs.chgShowIcon = it }, bump, lockedReason = if (hyper) null else "Not used in Live Update style")
            RowDivider()
            ToggleRow("Progress ring around icon", null, { prefs.chgShowRing }, { prefs.chgShowRing = it }, bump, lockedReason = if (hyper) null else "Not used in Live Update style")
            RowDivider()
            ToggleRow("Progress bar", null, { prefs.chgShowBar }, { prefs.chgShowBar = it }, bump)
            RowDivider()
            ColorSwatches(prefs.chgBarColor) { prefs.chgBarColor = it; bump() }
        }

        GroupLabel("Behavior")
        Group {
            ToggleRow("Expand when charger is connected", null, { prefs.chgPopOnPlug }, { prefs.chgPopOnPlug = it }, bump, lockedReason = if (hyper) null else "Not used in Live Update style")
        }

        GroupLabel("Island")
        Group {
            IslandPaddingRows({ prefs.chgPadAuto }, { prefs.chgPadAuto = it }, { prefs.chgPadLen }, { prefs.chgPadLen = it }, bump)
        }

        OtherModules(1)
    }
}

@Composable
fun TempPage(
    s: BatterySnapshot,
    prefs: ModulePrefs,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val view = LocalView.current
    var rev by remember { mutableStateOf(0) }
    val bump: () -> Unit = { rev++; onChanged() }
    val wallpaper = rememberWallpaper()
    var useF by remember { mutableStateOf(prefs.tempUseF) }
    var merged by remember { mutableStateOf(prefs.tempMerge) }

    val hyper = GlanceCapsNotifications.useHyper(prefs)
    PageScaffold("Temperature", onBack) {
        PreviewStage(wallpaper) { TempPreview(s, prefs, rev) }

        GroupLabel("Placement")
        Group {
            ToggleRow(
                "Merge with charging", null,
                { prefs.tempMerge },
                { prefs.tempMerge = it; merged = it },
                bump,
                lockedReason = if (hyper) null else "Temperature stays separate in Live Update style"
            )
            if (!merged) {
                RowDivider()
                ToggleRow("Show only while charging", null, { prefs.tempOnlyCharging }, { prefs.tempOnlyCharging = it }, bump)
            }
        }

        GroupLabel("Appearance")
        Group {
            ToggleRow("Thermometer icon", null, { prefs.tempShowIcon }, { prefs.tempShowIcon = it }, bump, lockedReason = if (hyper) null else "Not used in Live Update style")
            RowDivider()
            ToggleRow("State label", null, { prefs.tempShowLabel }, { prefs.tempShowLabel = it }, bump)
            RowDivider()
            ToggleRow("Outline color follows temperature", null, { prefs.tempColorFollows }, { prefs.tempColorFollows = it }, bump, lockedReason = if (hyper) null else "Not used in Live Update style")
            RowDivider()
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Unit", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp))
                FilterChip(
                    selected = !useF,
                    onClick = { useF = false; prefs.tempUseF = false; Haptics.tick(view); bump() },
                    label = { Text("°C") }
                )
                FilterChip(
                    selected = useF,
                    onClick = { useF = true; prefs.tempUseF = true; Haptics.tick(view); bump() },
                    label = { Text("°F") }
                )
            }
        }

        GroupLabel("Island")
        Group {
            IslandPaddingRows({ prefs.tempPadAuto }, { prefs.tempPadAuto = it }, { prefs.tempPadLen }, { prefs.tempPadLen = it }, bump)
        }

        OtherModules(2)
    }
}

@Composable
fun StepsPage(
    steps: StepReading?,
    prefs: ModulePrefs,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onAllow: () -> Unit,
    onAllowHealth: () -> Unit = {},
    permRev: Int = 0,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var rev by remember { mutableStateOf(0) }
    val bump: () -> Unit = { rev++; onChanged() }
    val wallpaper = rememberWallpaper()
    var goal by remember { mutableStateOf(prefs.stepGoal) }
    val missing = Steps.missingPermissions(ctx).isNotEmpty()

    var avail by remember { mutableStateOf<Map<String, Steps.Availability>>(emptyMap()) }
    LaunchedEffect(rev, missing, permRev) { avail = withContext(Dispatchers.IO) { Steps.allAvailability(ctx) } }
    val hyper = GlanceCapsNotifications.useHyper(prefs)
    PageScaffold("Steps", onBack) {
        PreviewStage(wallpaper) { StepsPreview(steps?.steps, prefs, rev) }

        GroupLabel("Goal")
        Group {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Daily goal", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        IslandText.count(goal),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                GlanceSlider(
                    value = goal.toFloat(),
                    onValueChange = { v ->
                        val n = (v / 500f).roundToInt() * 500
                        if (n != goal) {
                            goal = n
                            prefs.stepGoal = n
                            Haptics.tick(view)
                            bump()
                        }
                    },
                    valueRange = 2000f..30000f,
                    steps = 55
                )
            }
        }

        GroupLabel("Appearance")
        Group {
            ToggleRow("Walker progress bar", null, { prefs.stepTaxi }, { prefs.stepTaxi = it }, bump, lockedReason = if (hyper) null else "Not used in Live Update style")
            RowDivider()
            ToggleRow("Walking icon", null, { prefs.stepShowIcon }, { prefs.stepShowIcon = it }, bump, lockedReason = if (hyper) null else "Not used in Live Update style")
            RowDivider()
            ToggleRow("Progress ring around icon", null, { prefs.stepShowRing }, { prefs.stepShowRing = it }, bump)
            RowDivider()
            ToggleRow("Percentage", null, { prefs.stepShowPercent }, { prefs.stepShowPercent = it }, bump)
            RowDivider()
            ToggleRow("Progress bar", null, { prefs.stepShowBar }, { prefs.stepShowBar = it }, bump)
            RowDivider()
            ColorSwatches(prefs.stepColor) { prefs.stepColor = it; bump() }
        }

        GroupLabel("Behavior")
        Group {
            ToggleRow("Always show", null, { prefs.stepAlways }, { prefs.stepAlways = it }, bump)
            RowDivider()
            ToggleRow("Expand when goal is reached", null, { prefs.stepPopOnGoal }, { prefs.stepPopOnGoal = it }, bump, lockedReason = if (hyper) null else "Not used in Live Update style")
        }

        GroupLabel("Data source")
        Group {
            StepSourceRow(prefs, avail, steps?.source, bump)
            if (avail[Steps.SRC_HEALTH]?.reason == HealthSteps.NEEDS_PERMISSION || avail[Steps.SRC_ZEPP]?.reason == HealthSteps.NEEDS_PERMISSION) {
                RowDivider()
                RowItem(
                    icon = null,
                    title = "Health Connect access",
                    subtitle = "Needed to read steps from Mi Fitness and Zepp Life"
                ) {
                    TextButton(onClick = { Haptics.tick(view); onAllowHealth() }) { Text("Allow") }
                }
            }
            if (missing) {
                RowDivider()
                RowItem(
                    icon = null,
                    title = "Permissions",
                    subtitle = "Physical activity is needed to read the step counter"
                ) {
                    TextButton(onClick = { Haptics.tick(view); onAllow() }) { Text("Allow") }
                }
                RowDivider()
                RowItem(
                    icon = null,
                    title = "Permission previously denied?",
                    subtitle = "Enable Physical activity in the app info page",
                    onClick = { Haptics.tick(view); IslandSupport.openAppInfo(ctx) }
                )
            }
        }

        GroupLabel("Island")
        Group {
            IslandPaddingRows({ prefs.stepPadAuto }, { prefs.stepPadAuto = it }, { prefs.stepPadLen }, { prefs.stepPadLen = it }, bump)
        }

        OtherModules(3)
    }
}

@Composable
fun ConnectivityPage(
    prefs: ModulePrefs,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onAllow: () -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var rev by remember { mutableStateOf(0) }
    val bump: () -> Unit = { rev++; onChanged() }
    val wallpaper = rememberWallpaper()
    var seconds by remember { mutableStateOf(prefs.connSeconds) }
    val missing = ConnectivityMonitor.missingPermissions(ctx).isNotEmpty()

    PageScaffold("Connectivity", onBack) {
        PreviewStage(wallpaper) { ConnectivityPreview(prefs, rev) }

        GroupLabel("Show island for")
        Group {
            ToggleRow("Wi-Fi", null, { prefs.connWifi }, { prefs.connWifi = it }, bump)
            RowDivider()
            ToggleRow("Bluetooth", null, { prefs.connBluetooth }, { prefs.connBluetooth = it }, bump)
            RowDivider()
            ToggleRow("Airplane mode", null, { prefs.connAirplane }, { prefs.connAirplane = it }, bump)
        }

        GroupLabel("Names")
        Group {
            ToggleRow(
                "Show names", null,
                { prefs.connShowNames }, { prefs.connShowNames = it }, bump
            )
            if (prefs.connShowNames && missing) {
                RowDivider()
                RowItem(icon = null, title = "Permissions", subtitle = "Location and nearby devices") {
                    TextButton(onClick = { Haptics.tick(view); onAllow() }) { Text("Allow") }
                }
            }
        }

        GroupLabel("Duration")
        Group {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Display time", style = MaterialTheme.typography.bodyLarge)
                    Text("$seconds s", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                GlanceSlider(
                    value = seconds.toFloat(),
                    onValueChange = { v ->
                        val n = v.roundToInt()
                        if (n != seconds) {
                            seconds = n
                            prefs.connSeconds = n
                            Haptics.tick(view)
                            bump()
                        }
                    },
                    valueRange = 2f..10f,
                    steps = 7
                )
            }
        }

        GroupLabel("Island")
        Group {
            IslandPaddingRows({ prefs.connPadAuto }, { prefs.connPadAuto = it }, { prefs.connPadLen }, { prefs.connPadLen = it }, bump)
        }

        OtherModules(4)
    }
}

@Composable
private fun StepSourceRow(
    prefs: ModulePrefs,
    avail: Map<String, Steps.Availability>,
    current: String?,
    onChanged: () -> Unit,
) {
    val view = LocalView.current
    var open by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf(prefs.stepSource) }
    val anyOk = avail[Steps.SRC_AUTO]?.ok ?: true
    val detail = when {
        !anyOk -> avail[Steps.SRC_AUTO]?.reason ?: "No step source is available on this phone"
        key == Steps.SRC_AUTO -> if (current != null) "Using $current" else "Picks the best source automatically"
        else -> avail[key]?.takeIf { !it.ok }?.reason ?: current ?: Steps.label(key)
    }
    val openMenu: () -> Unit = { Haptics.tick(view); open = true }
    val cs = MaterialTheme.colorScheme
    val options = (listOf(Steps.SRC_AUTO) + Steps.SOURCES).map { k ->
        val a = avail[k]
        MenuOption(k, Steps.label(k), a?.ok ?: (k == Steps.SRC_AUTO), a?.reason)
    }
    Box(
        Modifier
            .alpha(if (anyOk) 1f else 0.45f)
    ) {
        RowItem(
            icon = null,
            title = "Step source",
            subtitle = detail,
            onClick = if (anyOk) openMenu else null
        ) {
            Box {
                DropdownTrigger(Steps.label(key), anyOk, openMenu)
                OptionMenu(
                    expanded = open,
                    onDismiss = { open = false },
                    options = options,
                    selected = key,
                    onSelect = { k ->
                        key = k
                        prefs.stepSource = k
                        open = false
                        Haptics.tick(view)
                        onChanged()
                    }
                )
            }
        }
    }
}
