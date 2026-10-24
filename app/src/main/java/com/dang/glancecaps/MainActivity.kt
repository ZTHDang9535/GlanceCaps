package com.dang.glancecaps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.SideEffect
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import com.dang.glancecaps.ui.UiDim
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import com.dang.glancecaps.battery.BatteryReader
import com.dang.glancecaps.battery.ChargeEstimator
import com.dang.glancecaps.data.ModulePrefs
import androidx.health.connect.client.PermissionController
import com.dang.glancecaps.island.ConnectivityMonitor
import com.dang.glancecaps.steps.HealthSteps
import com.dang.glancecaps.island.GlanceCapsNotifications
import com.dang.glancecaps.island.IslandSupport
import com.dang.glancecaps.island.GlanceCapsService
import com.dang.glancecaps.island.WallpaperStore
import com.dang.glancecaps.steps.StepReading
import com.dang.glancecaps.steps.StepSensor
import com.dang.glancecaps.steps.Steps
import com.dang.glancecaps.ui.AboutScreen
import com.dang.glancecaps.ui.AppTabBar
import com.dang.glancecaps.ui.GlanceCapsTheme
import com.dang.glancecaps.ui.UiState
import com.dang.glancecaps.ui.appDark
import com.dang.glancecaps.ui.LocalOpenModule
import com.dang.glancecaps.ui.DialogLayer
import com.dang.glancecaps.ui.ChargingPage
import com.dang.glancecaps.ui.ConnectivityPage
import com.dang.glancecaps.ui.MatchPage
import com.dang.glancecaps.ui.WeatherPage
import com.dang.glancecaps.ui.Checks
import com.dang.glancecaps.ui.Haptics
import com.dang.glancecaps.ui.ModulesScreen
import com.dang.glancecaps.ui.SettingsScreen
import com.dang.glancecaps.ui.StepsPage
import com.dang.glancecaps.ui.TempPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private fun requestMaxRefreshRate() {
        try {
            @Suppress("DEPRECATION")
            val d = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
            if (d == null) return
            val cur = d.mode
            val best = d.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            val lp = window.attributes
            lp.preferredDisplayModeId = best.modeId
            lp.preferredRefreshRate = best.refreshRate
            window.attributes = lp
        } catch (e: Throwable) { }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMaxRefreshRate()
        enableEdgeToEdge()
        val startPrefs = ModulePrefs(this)
        UiState.load(startPrefs)
        setContent {
            val dark = appDark()
            SideEffect {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            GlanceCapsTheme { App() }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val view = LocalView.current
    val prefs = remember { ModulePrefs(ctx) }
    val estimator = remember { ChargeEstimator() }
    remember { Haptics.enabled = prefs.haptics; true }

    var tab by rememberSaveable { mutableStateOf(0) }
    var page by rememberSaveable { mutableStateOf(0) }
    var snap by remember { mutableStateOf(BatteryReader.read(ctx)) }
    var eta by remember { mutableStateOf<Int?>(null) }
    var checks by remember { mutableStateOf(Checks(true, null, null, WallpaperStore.hasAccess())) }

    var steps by remember { mutableStateOf<StepReading?>(null) }
    val stepSensor = remember { StepSensor(ctx) }
    DisposableEffect(Unit) { onDispose { stepSensor.stop() } }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val stepsPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {

        stepSensor.start()
        GlanceCapsService.sync(ctx)
    }
    val askStepPermissions: () -> Unit = {
        val missing = Steps.missingPermissions(ctx)
        if (missing.isNotEmpty()) {
            try { stepsPermLauncher.launch(missing) } catch (e: Throwable) { CrashLog.append(ctx, "stepsPermission", e) }
        }
    }

    var healthRev by remember { mutableIntStateOf(0) }
    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        healthRev++
        GlanceCapsService.sync(ctx)
    }
    val askHealthPermission: () -> Unit = {
        try {
            healthLauncher.launch(HealthSteps.permissions())
        } catch (e: Throwable) {
            CrashLog.append(ctx, "healthPermission", e)
        }
    }
    val connPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        GlanceCapsService.sync(ctx)
    }
    val askConnPermissions: () -> Unit = {
        val missing = ConnectivityMonitor.missingPermissions(ctx)
        if (missing.isNotEmpty()) {
            try { connPermLauncher.launch(missing) } catch (e: Throwable) { CrashLog.append(ctx, "connPermission", e) }
        }
    }

    BackHandler(enabled = tab == 0 && page != 0) { page = 0 }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        GlanceCapsService.sync(ctx)

        var focus: Boolean? = null
        var n = 0
        while (true) {
            val s = BatteryReader.read(ctx)
            snap = s
            if (!s.charging) estimator.reset()
            eta = estimator.etaMinutes(s)

            stepSensor.start()
            steps = withContext(Dispatchers.IO) { Steps.read(ctx) }

            if (n % 5 == 0) {
                focus = if (GlanceCapsNotifications.isXiaomiFamily()) {
                    withContext(Dispatchers.IO) { IslandSupport.status(ctx).focusPermission }
                } else null
            }
            val focusNow = focus
            checks = withContext(Dispatchers.IO) {
                Checks(
                    notifications = IslandSupport.notificationsEnabled(ctx, !GlanceCapsNotifications.useHyper(prefs)),
                    autostart = IslandSupport.autostartEnabled(ctx),
                    focus = focusNow,
                    wallpaper = WallpaperStore.hasAccess()
                )
            }
            n++
            delay(2000)
        }
    }

    val onTab: (Int) -> Unit = { i ->
        if (tab != i) Haptics.tick(view)
        if (i == 0 && tab == 0) page = 0
        tab = i
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = { AppTabBar(selected = tab, onSelect = onTab) }
    ) { pad ->
        val screenKey = if (tab == 0) page else 10 + tab
        val screenModifier = Modifier.padding(pad)
        val renderScreen: @Composable (Int) -> Unit = { key ->
            when (key) {
                0 -> ModulesScreen(
                    s = snap,
                    steps = steps,
                    prefs = prefs,
                    onChanged = { GlanceCapsService.sync(ctx) },
                    onStepsToggled = { on ->
                        if (on) askStepPermissions()
                        GlanceCapsService.sync(ctx)
                    },
                    onMatchToggled = { GlanceCapsService.sync(ctx) },
                    onWeatherToggled = { GlanceCapsService.sync(ctx) },
                    onConnToggled = { on ->
                        if (on && prefs.connShowNames) askConnPermissions()
                        GlanceCapsService.sync(ctx)
                    },
                    onOpen = { page = it }
                )
                1 -> ChargingPage(
                    s = snap,
                    eta = eta,
                    prefs = prefs,
                    onBack = { page = 0 },
                    onChanged = { GlanceCapsService.sync(ctx) }
                )
                2 -> TempPage(
                    s = snap,
                    prefs = prefs,
                    onBack = { page = 0 },
                    onChanged = { GlanceCapsService.sync(ctx) }
                )
                3 -> StepsPage(
                    steps = steps,
                    prefs = prefs,
                    onBack = { page = 0 },
                    onChanged = { GlanceCapsService.sync(ctx) },
                    onAllow = askStepPermissions,
                    onAllowHealth = askHealthPermission,
                    permRev = healthRev
                )
                6 -> WeatherPage(
                    prefs = prefs,
                    onBack = { page = 0 },
                    onChanged = { GlanceCapsService.sync(ctx) }
                )
                5 -> MatchPage(
                    prefs = prefs,
                    onBack = { page = 0 },
                    onChanged = { GlanceCapsService.sync(ctx) }
                )
                4 -> ConnectivityPage(
                    prefs = prefs,
                    onBack = { page = 0 },
                    onChanged = { GlanceCapsService.sync(ctx) },
                    onAllow = askConnPermissions
                )
                11 -> SettingsScreen(checks = checks, prefs = prefs, onIconChanged = { }, onModeChanged = { GlanceCapsService.sync(ctx) })
                else -> AboutScreen()
            }
        }
        CompositionLocalProvider(LocalOpenModule provides { target -> page = target }) {
            Crossfade(targetState = screenKey, modifier = screenModifier, label = "screen") { key -> renderScreen(key) }
        }
    }
    val dim by animateFloatAsState(if (UiDim.count > 0) 0.55f else 0f, label = "dim")
    if (dim > 0.001f) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
    DialogLayer()
    }
}
