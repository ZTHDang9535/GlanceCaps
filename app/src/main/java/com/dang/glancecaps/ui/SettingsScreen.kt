package com.dang.glancecaps.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import android.os.SystemClock
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Brush
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dang.glancecaps.R
import com.dang.glancecaps.CrashLog
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.island.IconStore
import com.dang.glancecaps.island.GlanceCapsNotifications
import com.dang.glancecaps.island.IslandSupport
import com.dang.glancecaps.island.LiveUpdates
import com.dang.glancecaps.island.WallpaperStore

data class Checks(
    val notifications: Boolean,
    val autostart: Boolean?,
    val focus: Boolean?,
    val wallpaper: Boolean,
)

@Composable
private fun IslandModeRow(prefs: ModulePrefs, onChanged: () -> Unit) {
    val view = LocalView.current
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(prefs.islandMode) }
    val canIsland = GlanceCapsNotifications.isXiaomiFamily()
    val canLive = LiveUpdates.supported()
    val locked = !canIsland && !canLive
    val options = listOf(
        MenuOption("auto", "Auto"),
        MenuOption("hyper", "Xiaomi Super Island", canIsland, "Only on Xiaomi phones"),
        MenuOption("live", if (canLive) "Live Update" else "Standard notification")
    )
    val label = if (locked) "Standard notification" else options.firstOrNull { it.key == mode }?.label ?: "Auto"
    val detail = when {
        locked -> "Android 15 and older use standard notifications"
        mode == "hyper" -> "Xiaomi Super Island only"
        mode == "live" -> if (canLive) "Status-bar chip on Android 16 and later" else "Requires Android 16; a standard notification is used instead"
        else -> "Super Island on Xiaomi devices, Live Update on other devices"
    }
    val openMenu: () -> Unit = { Haptics.tick(view); open = true }
    Box(
        Modifier
            .alpha(if (locked) 0.45f else 1f)
    ) {
        RowItem(
            icon = R.drawable.ic_bolt,
            title = "Island style",
            subtitle = detail,
            onClick = if (locked) null else openMenu
        ) {
            Box {
                DropdownTrigger(label, !locked, openMenu)
                OptionMenu(
                    expanded = open,
                    onDismiss = { open = false },
                    options = options,
                    selected = mode,
                    onSelect = { key ->
                        mode = key
                        prefs.islandMode = key
                        open = false
                        Haptics.tick(view)
                        onChanged()
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    detail: String,
    ok: Boolean?,
    action: String?,
    onAction: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint) = when (ok) {
            true -> R.drawable.ic_check_circle to Color(0xFF34C759)
            false -> R.drawable.ic_error to cs.error
            null -> R.drawable.ic_help to cs.onSurfaceVariant
        }
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (action != null && ok != true) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun CategoryRow(@DrawableRes icon: Int, tile: Color, title: String, value: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { Haptics.tick(view); onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tile),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(title, fontSize = 17.sp, color = cs.onSurface, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, fontSize = 15.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ThemeTile(dark: Boolean, selected: Boolean, label: String, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val card = if (dark) Color(0xFF2B4FB8).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.3f)
    val lineStrong = if (dark) Color.White.copy(alpha = 0.55f) else Color(0xFF3A63D6)
    val lineSoft = if (dark) Color.White.copy(alpha = 0.35f) else Color(0xFF3A63D6).copy(alpha = 0.6f)
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(if (selected) 3.dp else 0.dp, if (selected) cs.primary else Color.Transparent, RoundedCornerShape(34.dp))
                .padding(5.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF4170FF), Color(0xFF58B0FF))))
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(2) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(card)
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(lineStrong))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth(0.6f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(lineSoft))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 16.sp, color = if (selected) cs.primary else cs.onSurface)
    }
}

@Composable
private fun AppearancePage(prefs: ModulePrefs, onBack: () -> Unit) {
    val view = LocalView.current
    var mode by remember { mutableStateOf(prefs.themeMode) }
    val darkNow = appDark()
    val setMode: (String) -> Unit = { m ->
        Haptics.tick(view)
        mode = m
        prefs.themeMode = m
        UiState.load(prefs)
    }
    ScreenFrame(title = "Appearance", spacing = 14.dp, onBack = onBack) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ThemeTile(false, !darkNow, "Light mode", Modifier.weight(1f)) { setMode("light") }
            ThemeTile(true, darkNow, "Dark mode", Modifier.weight(1f)) { setMode("dark") }
        }
        Group {
            RowItem(
                icon = null,
                title = "Follow system",
                subtitle = null,
                onClick = { setMode(if (mode == "system") (if (darkNow) "dark" else "light") else "system") }
            ) {
                Switch(
                    checked = mode == "system",
                    onCheckedChange = { on -> setMode(if (on) "system" else (if (darkNow) "dark" else "light")) },
                    colors = glanceSwitchColors()
                )
            }
        }
    }
}

@Composable
private fun StatusPage(checks: Checks, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val hyper = GlanceCapsNotifications.useHyper(ModulePrefs(ctx))
    val xiaomi = GlanceCapsNotifications.isXiaomiFamily()
    ScreenFrame(title = "Status", spacing = 14.dp, onBack = onBack) {
        Group {
            StatusRow(
                "Notifications",
                if (checks.notifications) "On" else "Blocked",
                checks.notifications, "Fix"
            ) { IslandSupport.openNotificationSettings(ctx) }
            RowDivider()
            if (xiaomi && hyper) {
                StatusRow(
                    "Island permission",
                    when (checks.focus) {
                        true -> "Allowed"
                        false -> "Not granted"
                        null -> "Checking"
                    },
                    checks.focus, "Open"
                ) { IslandSupport.openNotificationSettings(ctx) }
            } else {
                val promoteOk = if (LiveUpdates.supported()) LiveUpdates.canPromote(ctx) else true
                StatusRow(
                    "Live Update",
                    when {
                        !LiveUpdates.supported() -> "Not needed on this Android version"
                        promoteOk -> "Allowed"
                        else -> "Blocked"
                    },
                    promoteOk, "Open"
                ) { IslandSupport.openNotificationSettings(ctx) }
            }
            RowDivider()
            if (xiaomi) {
                StatusRow(
                    "Autostart",
                    when (checks.autostart) {
                        true -> "On"
                        false -> "Off"
                        null -> "Cannot be detected; verify manually"
                    },
                    checks.autostart, "Open"
                ) { IslandSupport.openAutostartSettings(ctx) }
            } else {
                StatusRow(
                    "Background activity",
                    "Allow unrestricted background use in the battery settings",
                    null, "Open"
                ) { IslandSupport.openAppInfo(ctx) }
            }
        }
        Group {
            RowItem(
                icon = R.drawable.ic_lock,
                title = "Lock in Recents",
                subtitle = "Long-press the app card in Recents and tap the lock icon"
            )
        }
    }
}

@Composable
private fun IslandPage(prefs: ModulePrefs, onModeChanged: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    ScreenFrame(title = "Island style", spacing = 14.dp, onBack = onBack) {
        Group {
            IslandModeRow(prefs, onModeChanged)
            if (LiveUpdates.supported() && !GlanceCapsNotifications.useHyper(prefs) && !LiveUpdates.canPromote(ctx)) {
                RowDivider()
                RowItem(icon = R.drawable.ic_error, title = "Live Update blocked", subtitle = "Enable it in the app's notification settings") {
                    TextButton(onClick = { IslandSupport.openNotificationSettings(ctx) }) { Text("Open") }
                }
            }
        }
    }
}

@Composable
private fun CustomizePage(checks: Checks, prefs: ModulePrefs, onIconChanged: () -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val view = LocalView.current
    var iconRev by remember { mutableStateOf(0) }
    val hasCustom = remember(iconRev) { IconStore.hasCustom(ctx) }
    val iconBitmap = remember(iconRev) { IconStore.bolt(ctx) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (IconStore.saveFrom(ctx, uri)) Haptics.confirm(view)
            iconRev++
            onIconChanged()
        }
    }
    ScreenFrame(title = "Customization", spacing = 14.dp, onBack = onBack) {
        Group {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) { GlyphIcon(iconBitmap, 40.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Lightning icon", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (hasCustom) "Custom image" else "Default",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                if (hasCustom) {
                    TextButton(onClick = {
                        IconStore.reset(ctx); iconRev++; Haptics.tick(view); onIconChanged()
                    }) { Text("Reset") }
                }
                OutlinedButton(onClick = { Haptics.tick(view); pick.launch("image/*") }) { Text("Change") }
            }
            RowDivider()
            RowItem(
                icon = R.drawable.ic_wallpaper,
                title = "Preview wallpaper",
                subtitle = if (checks.wallpaper) "Using your wallpaper" else "File access required"
            ) {
                if (!checks.wallpaper) {
                    TextButton(onClick = { WallpaperStore.openAccessSettings(ctx) }) { Text("Allow") }
                }
            }
            RowDivider()
            ToggleRow(
                title = "Haptic feedback",
                subtitle = null,
                get = { prefs.haptics },
                set = { prefs.haptics = it; Haptics.enabled = it }
            )
        }
    }
}

@Composable
fun SettingsScreen(checks: Checks, prefs: ModulePrefs, onIconChanged: () -> Unit, onModeChanged: () -> Unit = {}) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val hyper = GlanceCapsNotifications.useHyper(prefs)
    val xiaomi = GlanceCapsNotifications.isXiaomiFamily()

    var sub by rememberSaveable { mutableStateOf("") }
    var logText by remember { mutableStateOf<String?>(CrashLog.read(ctx)) }
    var showLog by remember { mutableStateOf(false) }
    var devOn by remember { mutableStateOf(prefs.devMode) }
    var taps by remember { mutableIntStateOf(0) }
    var tapStamp by remember { mutableLongStateOf(0L) }

    LaunchedEffect(tapStamp) {
        if (tapStamp != 0L) {
            delay(550)
            if (taps <= 2) {
                logText = CrashLog.read(ctx)
                if (logText != null) showLog = true
            }
        }
    }

    BackHandler(enabled = sub.isNotEmpty()) { sub = "" }

    val islandOk = if (xiaomi && hyper) checks.focus != false else if (LiveUpdates.supported()) LiveUpdates.canPromote(ctx) else true
    val statusOk = checks.notifications && islandOk && (!xiaomi || checks.autostart != false)
    val themeLabel = when (UiState.themeMode) {
        "light" -> "Light"
        "dark" -> "Dark"
        else -> "Follow system"
    }
    val islandLabel = when {
        !xiaomi && !LiveUpdates.supported() -> "Notification"
        prefs.islandMode == "hyper" -> "Super Island"
        prefs.islandMode == "live" -> "Live Update"
        else -> "Auto"
    }

    AnimatedContent(
        targetState = sub,
        transitionSpec = {
            val forward = targetState.isNotEmpty()
            (slideInHorizontally(tween(300)) { w -> if (forward) w else -w } + fadeIn(tween(300))) togetherWith
                (slideOutHorizontally(tween(300)) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(180)))
        },
        label = "settings"
    ) { key ->
        when (key) {
            "status" -> StatusPage(checks) { sub = "" }
            "appearance" -> AppearancePage(prefs) { sub = "" }
            "island" -> IslandPage(prefs, onModeChanged) { sub = "" }
            "customize" -> CustomizePage(checks, prefs, onIconChanged) { sub = "" }
            "dev" -> DevScreen(prefs, onDisable = { devOn = false; sub = "" }) { sub = "" }
            else -> ScreenFrame(title = "Settings", spacing = 14.dp) {
                Group {
                    CategoryRow(
                        R.drawable.ic_check_circle, Color(0xFF34C759), "Status",
                        if (statusOk) "All good" else "Needs attention"
                    ) { sub = "status" }
                }
                Group {
                    CategoryRow(R.drawable.ic_tune, Color(0xFF3482FF), "Appearance", themeLabel) { sub = "appearance" }
                    CategoryRow(R.drawable.ic_bolt, Color(0xFFFF9500), "Island style", islandLabel) { sub = "island" }
                    CategoryRow(R.drawable.ic_wallpaper, Color(0xFFAF52DE), "Customization", null) { sub = "customize" }
                }
                Group {
                    CategoryRow(
                        R.drawable.ic_help, Color(0xFF8E8E93), "Crash log",
                        if (logText != null) "Entries" else null
                    ) {
                        if (devOn) {
                            logText = CrashLog.read(ctx)
                            if (logText != null) showLog = true
                        } else {
                            val now = SystemClock.elapsedRealtime()
                            taps = if (now - tapStamp > 1500L) 1 else taps + 1
                            tapStamp = now
                            if (taps >= 10) {
                                prefs.devMode = true
                                devOn = true
                                taps = 0
                                Haptics.confirm(view)
                                Toast.makeText(ctx, "Developer options unlocked", Toast.LENGTH_SHORT).show()
                            } else if (taps >= 5) {
                                Toast.makeText(ctx, "${10 - taps} more taps to unlock developer options", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    if (devOn) {
                        CategoryRow(R.drawable.ic_tune, Color(0xFF636366), "Developer options", null) { sub = "dev" }
                    }
                }
            }
        }
    }

    if (showLog && logText != null) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("Crash log") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        logText ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val cm = ctx.getSystemService(ClipboardManager::class.java)
                        cm.setPrimaryClip(ClipData.newPlainText("GlanceCaps log", logText ?: ""))
                        Haptics.confirm(view)
                    } catch (e: Throwable) { }
                }) { Text("Copy") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        CrashLog.clear(ctx); logText = null; showLog = false
                    }) { Text("Clear") }
                    TextButton(onClick = { showLog = false }) { Text("Close") }
                }
            }
        )
    }
}
