package com.dang.glancecaps.ui

import com.dang.glancecaps.island.CallPlaceholderStore
import com.dang.glancecaps.island.PinnedIconStore
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import com.dang.glancecaps.call.CallApp
import com.dang.glancecaps.call.CallApps
import com.dang.glancecaps.call.CallTracker
import com.dang.glancecaps.call.CallAccess
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
    val backgroundLocation = androidx.core.content.ContextCompat.checkSelfPermission(
        ctx, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

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
            } else if (prefs.connShowNames && !backgroundLocation) {
                RowDivider()
                RowItem(
                    icon = null,
                    title = "Names while the app is closed",
                    subtitle = "Set location to Allow all the time"
                ) {
                    TextButton(onClick = { Haptics.tick(view); IslandSupport.openAppInfo(ctx) }) { Text("Open") }
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
fun CallsPage(
    prefs: ModulePrefs,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var rev by remember { mutableStateOf(0) }
    val bump: () -> Unit = { rev++; onChanged() }
    val wallpaper = rememberWallpaper()
    var granted by remember { mutableStateOf(CallAccess.granted(ctx)) }
    var showApps by remember { mutableStateOf(false) }
    val offCount = prefs.callAppOffSet().size
    var btn by remember { mutableIntStateOf(0) }
    var cAnswer by remember { mutableStateOf(prefs.callColorAnswer) }
    var cEnd by remember { mutableStateOf(prefs.callColorEnd) }
    var cSpeaker by remember { mutableStateOf(prefs.callColorSpeaker) }
    var cSpeakerOn by remember { mutableStateOf(prefs.callColorSpeakerOn) }
    var inText by remember { mutableStateOf(prefs.callTextIncoming) }
    var outText by remember { mutableStateOf(prefs.callTextOutgoing) }
    var showAvatar by remember { mutableStateOf(prefs.callShowAvatar) }
    var phMode by remember { mutableIntStateOf(prefs.callPlaceholderMode) }
    var phColor by remember { mutableStateOf(prefs.callPlaceholderColor) }
    val pickPlaceholder = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && CallPlaceholderStore.saveFrom(ctx, uri)) {
            prefs.callPlaceholderMode = 2
            phMode = 2
            Haptics.confirm(view)
            rev++
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            granted = CallAccess.granted(ctx)
            delay(1000L)
        }
    }

    if (showApps) {
        BackHandler { showApps = false }
        CallAppsPage(prefs, onBack = { showApps = false }, onChanged = bump)
        return
    }

    PageScaffold("Calls", onBack) {
        PreviewStage(wallpaper) { CallPreview(prefs, rev) }

        GroupLabel("Access")
        Group {
            RowItem(
                icon = R.drawable.ic_lock,
                title = "Notification access",
                subtitle = if (granted) "Allowed" else "Needed to detect calls",
                onClick = { Haptics.tick(view); CallAccess.openSettings(ctx) }
            ) {
                Text(if (granted) "Manage" else "Allow", color = MaterialTheme.colorScheme.primary)
            }
        }

        GroupLabel("Island")
        Group {
            ToggleRow(
                "Call buttons", "Speaker, hang up, answer and decline buttons on the expanded island",
                { prefs.callButtons }, { prefs.callButtons = it }, bump
            )
        }

        GroupLabel("Buttons")
        Group {
            SegmentedControl(
                options = listOf("Answer", "End", "Speaker", "Speaker on"),
                selected = btn,
                onSelect = { btn = it },
                title = "Button colour"
            )
            val shown = when (btn) {
                0 -> cAnswer
                1 -> cEnd
                2 -> cSpeaker
                else -> cSpeakerOn
            }
            ColorSwatches(shown) { hex ->
                when (btn) {
                    0 -> { cAnswer = hex; prefs.callColorAnswer = hex }
                    1 -> { cEnd = hex; prefs.callColorEnd = hex }
                    2 -> { cSpeaker = hex; prefs.callColorSpeaker = hex }
                    else -> { cSpeakerOn = hex; prefs.callColorSpeakerOn = hex }
                }
                bump()
            }
        }

        GroupLabel("Text")
        Group {
            OutlinedTextField(
                value = inText,
                onValueChange = {
                    inText = it.take(16)
                    prefs.callTextIncoming = inText
                    rev++
                },
                singleLine = true,
                label = { Text("Incoming call text") },
                placeholder = { Text("Incoming call") },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            )
            OutlinedTextField(
                value = outText,
                onValueChange = {
                    outText = it.take(16)
                    prefs.callTextOutgoing = outText
                    rev++
                },
                singleLine = true,
                label = { Text("Calling text") },
                placeholder = { Text("Calling...") },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            )
        }

        GroupLabel("Profile picture")
        Group {
            ToggleRow(
                "Show profile pictures", "Uses the caller's photo when the call app provides one",
                { prefs.callShowAvatar }, { prefs.callShowAvatar = it },
                { showAvatar = prefs.callShowAvatar; bump() }
            )
            if (showAvatar) {
                RowDivider()
                SegmentedControl(
                    options = listOf("Initial", "Icon", "Image"),
                    selected = phMode,
                    onSelect = { mode ->
                        if (mode == 2 && !CallPlaceholderStore.has(ctx)) {
                            pickPlaceholder.launch("image/*")
                        } else {
                            phMode = mode
                            prefs.callPlaceholderMode = mode
                            bump()
                        }
                    },
                    title = "When there is no photo"
                )
                RowDivider()
                if (phMode == 2) {
                    RowItem(
                        icon = null,
                        title = "Choose a different image",
                        subtitle = null,
                        onClick = { Haptics.tick(view); pickPlaceholder.launch("image/*") }
                    )
                } else {
                    Text(
                        "Placeholder colour",
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ColorSwatches(phColor) {
                        phColor = it
                        prefs.callPlaceholderColor = it
                        bump()
                    }
                }
            }
        }

        GroupLabel("Apps")
        Group {
            RowItem(
                icon = R.drawable.ic_grid_view,
                title = "Apps",
                subtitle = if (offCount == 0) "All apps" else "$offCount apps off",
                onClick = { Haptics.tick(view); showApps = true }
            )
            RowDivider()
            ToggleRow(
                "Phone app", "Also show for the Phone app, which already has its own island",
                { prefs.callPhoneApp }, { prefs.callPhoneApp = it }, bump
            )
        }

        OtherModules(8)
    }
}

@Composable
private fun CallAppsPage(prefs: ModulePrefs, onBack: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<CallApp>?>(null) }
    var off by remember { mutableStateOf(prefs.callAppOffSet()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { CallApps.load(ctx) } }

    PageScaffold("Apps", onBack) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search apps") },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        )
        val list = apps
        if (list == null) {
            Text("Loading...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val shown = list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            val groups = listOf("Suggested" to shown.filter { it.suggested }, "Other apps" to shown.filter { !it.suggested })
            for ((label, items) in groups) {
                if (items.isEmpty()) continue
                GroupLabel(label)
                Group {
                    items.forEachIndexed { i, app ->
                        if (i > 0) RowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlyphIcon(app.icon, 40.dp)
                            androidx.compose.material3.Text(
                                app.label,
                                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            HSwitch(app.pkg !in off) { on ->
                                prefs.setCallApp(app.pkg, on)
                                off = prefs.callAppOffSet()
                                if (!on && CallTracker.current?.pkg == app.pkg) CallTracker.end(ctx)
                                onChanged()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinnedPage(
    prefs: ModulePrefs,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var rev by remember { mutableStateOf(0) }
    val bump: () -> Unit = { rev++; onChanged() }
    val wallpaper = rememberWallpaper()
    val hyper = GlanceCapsNotifications.useHyper(prefs)
    var iconMode by remember {
        mutableIntStateOf(if (!prefs.pinnedShowIcon) 0 else if (prefs.pinnedIconCustom) 2 else 1)
    }
    var tinted by remember { mutableStateOf(prefs.pinnedIconColor.isNotBlank()) }
    var showText by remember { mutableStateOf(prefs.pinnedShowText) }
    var textSide by remember { mutableIntStateOf(prefs.pinnedTextSide) }
    var text by remember { mutableStateOf(prefs.pinnedText) }
    var text2 by remember { mutableStateOf(prefs.pinnedText2) }
    var iconColor by remember { mutableStateOf(prefs.pinnedIconColor) }
    var granted by remember { mutableStateOf(CallAccess.granted(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            granted = CallAccess.granted(ctx)
            delay(1000L)
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && PinnedIconStore.saveFrom(ctx, uri)) {
            prefs.pinnedShowIcon = true
            prefs.pinnedIconCustom = true
            iconMode = 2
            Haptics.confirm(view)
            bump()
        }
    }

    PageScaffold("Pinned Island", onBack) {
        PreviewStage(wallpaper) { PinnedPreview(prefs, rev) }

        GroupLabel("Icon")
        Group {
            SegmentedControl(
                options = listOf("None", "App logo", "Image"),
                selected = iconMode,
                onSelect = { mode ->
                    when (mode) {
                        0 -> {
                            prefs.pinnedShowIcon = false
                            iconMode = 0
                            bump()
                        }
                        1 -> {
                            prefs.pinnedShowIcon = true
                            prefs.pinnedIconCustom = false
                            iconMode = 1
                            bump()
                        }
                        else -> {
                            if (PinnedIconStore.has(ctx)) {
                                prefs.pinnedShowIcon = true
                                prefs.pinnedIconCustom = true
                                iconMode = 2
                                bump()
                            } else {
                                pick.launch("image/*")
                            }
                        }
                    }
                }
            )
            if (iconMode == 2) {
                RowDivider()
                RowItem(
                    icon = null,
                    title = "Choose a different image",
                    subtitle = "A transparent PNG silhouette works best",
                    onClick = { Haptics.tick(view); pick.launch("image/*") }
                )
            }
            if (iconMode != 0) {
                RowDivider()
                SegmentedControl(
                    options = listOf("Original colours", "Tinted"),
                    selected = if (tinted) 1 else 0,
                    onSelect = { mode ->
                        tinted = mode == 1
                        iconColor = if (tinted) "#000000" else ""
                        prefs.pinnedIconColor = iconColor
                        bump()
                    },
                    title = "Icon colour"
                )
                if (tinted) {
                    ColorSwatches(iconColor.ifBlank { "#000000" }) {
                        iconColor = it
                        prefs.pinnedIconColor = it
                        bump()
                    }
                    Text(
                        "Black on the black pill makes the icon blend in.",
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        GroupLabel("Text")
        Group {
            ToggleRow(
                "Show text", "Leave off for an empty pill, like an iOS Dynamic Island",
                { prefs.pinnedShowText }, { prefs.pinnedShowText = it },
                { showText = prefs.pinnedShowText; bump() }
            )
            if (showText) {
                SegmentedControl(
                    options = listOf("Left", "Right", "Both"),
                    selected = textSide,
                    onSelect = { mode ->
                        textSide = mode
                        prefs.pinnedTextSide = mode
                        bump()
                    },
                    title = "Text position"
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.take(16)
                        prefs.pinnedText = text
                        rev++
                    },
                    singleLine = true,
                    label = { Text(if (textSide == 2) "Left text" else "Custom text") },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                )
                if (textSide == 2) {
                    OutlinedTextField(
                        value = text2,
                        onValueChange = {
                            text2 = it.take(16)
                            prefs.pinnedText2 = text2
                            rev++
                        },
                        singleLine = true,
                        label = { Text("Right text") },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    )
                }
            }
        }

        if (hyper) {
            GroupLabel("Outline")
            Group {
                ColorSwatches(prefs.pinnedColor) { prefs.pinnedColor = it; bump() }
            }
        }

        GroupLabel("Size")
        Group {
            IslandPaddingRows({ prefs.pinnedPadAuto }, { prefs.pinnedPadAuto = it }, { prefs.pinnedPadLen }, { prefs.pinnedPadLen = it }, bump)
        }

        GroupLabel("Behavior")
        Group {
            RowItem(
                icon = R.drawable.ic_lock,
                title = "Hide when other islands show",
                subtitle = if (granted) "Active" else "Needs notification access",
                onClick = { Haptics.tick(view); CallAccess.openSettings(ctx) }
            ) {
                Text(if (granted) "Manage" else "Allow", color = MaterialTheme.colorScheme.primary)
            }
        }

        if (!hyper) {
            GroupLabel("Note")
            Group {
                RowItem(
                    icon = R.drawable.ic_error,
                    title = "Needs Super Island",
                    subtitle = "Pinned Island only works with HyperOS Super Island"
                )
            }
        }

        OtherModules(7)
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
