package com.dang.glancecaps.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.MutableTransitionState
import kotlin.math.pow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.lerp as mix
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dang.glancecaps.R
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.i18n.Lang
import com.dang.glancecaps.i18n.tr
import com.dang.glancecaps.island.GlanceCapsNotifications
import com.dang.glancecaps.island.IslandSupport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class SetupMode { FULL, WHATS_NEW }

object SetupState {
    var mode by mutableStateOf<SetupMode?>(null)
}

fun appVersionInfo(ctx: Context): Pair<Int, String> {
    return try {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        info.longVersionCode.toInt() to (info.versionName ?: "")
    } catch (e: Throwable) {
        0 to ""
    }
}

private enum class Style { WELCOME, PLAIN, DONE }

private enum class Step(val style: Style = Style.PLAIN) {
    WELCOME(Style.WELCOME), LANGUAGE, APPEARANCE, TERMS, CHARGING, STEPS, CONN, MATCH, WEATHER, NOTIF, ISLAND, RUN, PERMS, DONE(Style.DONE),
    NEW_LANG, NEW_CHINA, NEW_WEATHER, NEW_DONE(Style.DONE)
}

private class Look(
    val base: Color,
    val blobs: List<Color>,
    val text: Color,
    val sub: Color,
    val card: Color,
    val button: Color,
    val onButton: Color,
    val wordmark: List<Color>,
    val accent: Color = BlueFixed,
)

private val BlueFixed = Color(0xFF2F5BFF)

private val Blue: Color
    @Composable get() = LocalLook.current.accent

private fun lookOf(style: Style, dark: Boolean, scheme: ColorScheme?): Look =
    if (scheme != null) dynamicLookOf(style, dark, scheme) else fixedLookOf(style, dark)

private fun fixedLookOf(style: Style, dark: Boolean): Look = when (style) {
    Style.WELCOME -> if (dark) {
        Look(
            Color(0xFF120F33),
            listOf(Color(0xFF4B36C9), Color(0xFF7A4DFF), Color(0xFF2B3FA8), Color(0xFFB04CFF), Color(0xFF3A27A0)),
            Color(0xFFF1EEFF), Color(0xFFC9C2F5), Color.White.copy(alpha = 0.10f),
            Color(0xFFD0C6FF), Color(0xFF1F1A4D), listOf(Color(0xFFF1EEFF), Color(0xFFD7CEFF))
        )
    } else {
        Look(
            Color(0xFFA494FF),
            listOf(Color(0xFFE9E2FF), Color(0xFF6D55F2), Color(0xFFFF9CE0), Color(0xFF6FD3FF), Color(0xFFCDBBFF)),
            Color(0xFF1F1A4D), Color(0xFF3B3470), Color.White.copy(alpha = 0.55f),
            Color(0xFF4B3FA0), Color.White, listOf(Color(0xFF1F1A4D), Color(0xFF3A2E8A))
        )
    }
    Style.PLAIN -> if (dark) {
        Look(
            Color(0xFF0F0E18),
            listOf(Color(0xFF4130A8), Color(0xFF1D52B8), Color(0xFF8A33A8), Color(0xFF263C8A), Color(0xFF5640D0)),
            Color(0xFFEDEAF5), Color(0xFFB8B3C8), Color.White.copy(alpha = 0.09f),
            Color(0xFF4D7BFF), Color.White, listOf(Color(0xFFEDEAF5), Color(0xFFEDEAF5))
        )
    } else {
        Look(
            Color(0xFFD9D0FF),
            listOf(Color(0xFFA996FF), Color(0xFF9FC2FF), Color(0xFFFFBFEA), Color(0xFFFFFFFF), Color(0xFFBBA9FF)),
            Color(0xFF1C1B22), Color(0xFF49454F), Color.White.copy(alpha = 0.6f),
            BlueFixed, Color.White, listOf(Color(0xFF1C1B22), Color(0xFF1C1B22))
        )
    }
    Style.DONE -> if (dark) {
        Look(
            Color(0xFF1E1015),
            listOf(Color(0xFF9A3F52), Color(0xFF6A3FB0), Color(0xFFA8622F), Color(0xFF8A3478), Color(0xFFB84C5E)),
            Color(0xFFFFD9D2), Color(0xFFE8C4C0), Color.White.copy(alpha = 0.10f),
            Color(0xFFE6D2F5), Color(0xFF3A2447), listOf(Color(0xFFFFB4A8), Color(0xFFFFD7A8))
        )
    } else {
        Look(
            Color(0xFFFFD6CE),
            listOf(Color(0xFFFF9A86), Color(0xFFFFB8DC), Color(0xFFC2A2FF), Color(0xFFFFE07F), Color(0xFFFF86AE)),
            Color(0xFF6B1F2A), Color(0xFF5A2A33), Color.White.copy(alpha = 0.55f),
            Color(0xFF5A3F70), Color.White, listOf(Color(0xFF8E1B2D), Color(0xFFB5543A))
        )
    }
}

private fun dynamicLookOf(style: Style, dark: Boolean, c: ColorScheme): Look = when (style) {
    Style.WELCOME -> if (dark) {
        Look(
            base = mix(c.surface, c.primaryContainer, 0.45f),
            blobs = listOf(
                mix(c.surface, c.primary, 0.5f), mix(c.surface, c.tertiary, 0.45f), mix(c.surface, c.secondary, 0.4f),
                mix(c.surface, c.primary, 0.7f), mix(c.surface, c.primaryContainer, 0.8f)
            ),
            text = c.onSurface, sub = c.onSurfaceVariant, card = Color.White.copy(alpha = 0.10f),
            button = c.primary, onButton = c.onPrimary, wordmark = listOf(c.onSurface, c.primary), accent = c.primary
        )
    } else {
        Look(
            base = mix(c.primaryContainer, c.primary, 0.3f),
            blobs = listOf(
                mix(Color.White, c.primaryContainer, 0.5f), c.primary, mix(c.tertiaryContainer, c.tertiary, 0.3f),
                mix(c.secondaryContainer, c.secondary, 0.2f), mix(c.primaryContainer, Color.White, 0.3f)
            ),
            text = c.onPrimaryContainer, sub = mix(c.onPrimaryContainer, c.primaryContainer, 0.2f), card = Color.White.copy(alpha = 0.55f),
            button = c.onPrimaryContainer, onButton = c.primaryContainer, wordmark = listOf(c.onPrimaryContainer, c.primary), accent = c.primary
        )
    }
    Style.PLAIN -> if (dark) {
        Look(
            base = c.surface,
            blobs = listOf(
                mix(c.surface, c.primary, 0.35f), mix(c.surface, c.secondary, 0.3f), mix(c.surface, c.tertiary, 0.35f),
                mix(c.surface, c.primary, 0.2f), mix(c.surface, c.tertiary, 0.2f)
            ),
            text = c.onSurface, sub = c.onSurfaceVariant, card = Color.White.copy(alpha = 0.09f),
            button = c.primary, onButton = c.onPrimary, wordmark = listOf(c.onSurface, c.onSurface), accent = c.primary
        )
    } else {
        Look(
            base = mix(c.surface, c.primaryContainer, 0.55f),
            blobs = listOf(
                c.primaryContainer, c.secondaryContainer, c.tertiaryContainer,
                mix(c.surface, Color.White, 0.5f), mix(c.primaryContainer, c.primary, 0.3f)
            ),
            text = c.onSurface, sub = c.onSurfaceVariant, card = Color.White.copy(alpha = 0.6f),
            button = c.primary, onButton = c.onPrimary, wordmark = listOf(c.onSurface, c.onSurface), accent = c.primary
        )
    }
    Style.DONE -> if (dark) {
        Look(
            base = mix(c.surface, c.tertiaryContainer, 0.4f),
            blobs = listOf(
                mix(c.surface, c.tertiary, 0.5f), mix(c.surface, c.primary, 0.4f), mix(c.surface, c.secondary, 0.4f),
                mix(c.surface, c.tertiary, 0.3f), mix(c.surface, c.primary, 0.55f)
            ),
            text = c.onSurface, sub = c.onSurfaceVariant, card = Color.White.copy(alpha = 0.10f),
            button = c.tertiary, onButton = c.onTertiary, wordmark = listOf(c.tertiary, c.primary), accent = c.tertiary
        )
    } else {
        Look(
            base = c.tertiaryContainer,
            blobs = listOf(
                mix(c.tertiaryContainer, c.tertiary, 0.5f), c.primaryContainer, c.secondaryContainer,
                mix(c.tertiaryContainer, Color.White, 0.5f), mix(c.tertiary, c.tertiaryContainer, 0.3f)
            ),
            text = c.onTertiaryContainer, sub = mix(c.onTertiaryContainer, c.tertiaryContainer, 0.25f), card = Color.White.copy(alpha = 0.55f),
            button = c.tertiary, onButton = c.onTertiary, wordmark = listOf(c.onTertiaryContainer, c.tertiary), accent = c.tertiary
        )
    }
}

private val LocalLook = compositionLocalOf { fixedLookOf(Style.PLAIN, false) }

private fun titleOf(step: Step, version: String): String = when (step) {
    Step.WELCOME -> "GlanceCaps"
    Step.LANGUAGE -> "Choose language"
    Step.APPEARANCE -> "Appearance"
    Step.TERMS -> "Terms and conditions"
    Step.CHARGING -> "Charging, at a glance"
    Step.STEPS -> "Steps with a finish line"
    Step.CONN -> "Connections, instantly"
    Step.MATCH -> "Live match scores"
    Step.WEATHER -> "Weather and alerts"
    Step.NOTIF -> "Allow notifications"
    Step.ISLAND -> "Choose your island"
    Step.RUN -> "Keep it running"
    Step.PERMS -> "Optional permissions"
    Step.DONE -> "Setup complete"
    Step.NEW_LANG -> "Now speaks Vietnamese"
    Step.NEW_CHINA -> "China ROM support"
    Step.NEW_WEATHER -> "Smarter weather timing"
    Step.NEW_DONE -> if (version.isEmpty()) "You're up to date" else "Updated to $version"
}

private fun subtitleOf(step: Step, xiaomi: Boolean): String = when (step) {
    Step.WELCOME -> "Everything important, at a glance..."
    Step.LANGUAGE -> ""
    Step.APPEARANCE -> "Pick light, dark, or let your phone decide."
    Step.TERMS -> ""
    Step.CHARGING -> "Watts, percentage and time left in the island, with battery temperature alongside."
    Step.STEPS -> "Follow your daily goal as the orange walker icon moves toward the flag (or any color u choose idk man)."
    Step.CONN -> "A quick island pop when Wi-Fi, Bluetooth or airplane mode changes."
    Step.MATCH -> "Follow a match from the V.League to the World Cup right from the island."
    Step.WEATHER -> "Choose when it shows, and get warned about storms, floods and earthquakes near you."
    Step.NOTIF -> "Islands are notifications, so GlanceCaps needs this permission."
    Step.ISLAND -> "Auto uses Super Island on Xiaomi phones and Live Update everywhere else."
    Step.RUN -> if (xiaomi) "Allow autostart so HyperOS doesn't stop GlanceCaps in the background (if u didnt turn it on dont blame me)."
    else "Allow unrestricted background use so Android doesn't stop GlanceCaps (if u didnt turn it on dont blame me)."
    Step.PERMS -> "Steps need activity recognition. Wi-Fi and Bluetooth names need location and nearby devices."
    Step.DONE -> "Change anything later from the Modules and Settings tabs."
    Step.NEW_LANG -> "GlanceCaps follows your phone's language, or you can pick one below."
    Step.NEW_CHINA -> "A Shizuku workaround can bring Super Island to HyperOS China ROMs. Find it in Settings, Island style."
    Step.NEW_WEATHER -> "Show the weather between two times, when it changes, or every few hours."
    Step.NEW_DONE -> "Background crashes are also harder to trigger now."
}

@Composable
fun SetupFlow(mode: SetupMode, prefs: ModulePrefs, versionName: String, onFinish: () -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val dark = appDark()
    val ctx = LocalContext.current
    val scheme = remember(dark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        } else null
    }
    val xiaomi = remember { GlanceCapsNotifications.isXiaomiFamily() }
    val steps = remember(mode) {
        if (mode == SetupMode.FULL) {
            listOf(
                Step.WELCOME, Step.LANGUAGE, Step.APPEARANCE, Step.TERMS, Step.CHARGING, Step.STEPS, Step.CONN, Step.MATCH, Step.WEATHER,
                Step.NOTIF, Step.ISLAND, Step.RUN, Step.PERMS, Step.DONE
            )
        } else {
            listOf(Step.NEW_LANG, Step.NEW_CHINA, Step.NEW_WEATHER, Step.NEW_DONE)
        }
    }
    val pagerState = rememberPagerState(pageCount = { steps.size })
    val page = pagerState.currentPage.coerceIn(0, steps.lastIndex)
    val current = steps[page]
    val look = lookOf(current.style, dark, scheme)
    val last = page == steps.lastIndex
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(page) {
        pulse.snapTo(0f)
        pulse.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        pulse.animateTo(0f, tween(1100, easing = FastOutSlowInEasing))
    }
    val go: (Int) -> Unit = { target ->
        scope.launch { pagerState.animateScrollToPage(target.coerceIn(0, steps.lastIndex), animationSpec = tween(520, easing = FastOutSlowInEasing)) }
    }
    var accepted by remember { mutableStateOf(prefs.termsAccepted) }
    var showCode by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val expected = remember { termsCodeOf(prefs) }
    val termsIndex = steps.indexOf(Step.TERMS)
    val needsTerms = termsIndex >= 0 && !accepted
    val next: () -> Unit = {
        Haptics.tick(view)
        when {
            current == Step.TERMS && !accepted -> {
                typed = ""
                wrong = false
                showCode = true
            }
            last -> onFinish()
            else -> go(page + 1)
        }
    }
    val skip: () -> Unit = {
        Haptics.tick(view)
        if (needsTerms && page < termsIndex) go(termsIndex) else onFinish()
    }

    BackHandler(enabled = page > 0 || !needsTerms) { if (page > 0) go(page - 1) else onFinish() }

    if (showCode) {
        GlanceDialog(
            title = "Enter the code",
            message = "",
            cancelText = "Cancel",
            confirmText = "Verify",
            onCancel = { showCode = false },
            onConfirm = {
                if (typed.trim().equals(expected, ignoreCase = true)) {
                    prefs.termsAccepted = true
                    accepted = true
                    showCode = false
                    go(page + 1)
                } else {
                    wrong = true
                }
            },
            body = { CodeBody(typed, wrong) { value -> typed = value; wrong = false } }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Aurora(look) { pulse.value }
        HorizontalPager(state = pagerState, userScrollEnabled = false, modifier = Modifier.fillMaxSize()) { index ->
            val offset = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
            val pageLook = lookOf(steps[index].style, dark, scheme)
            CompositionLocalProvider(LocalLook provides pageLook) {
                SetupPage(steps[index], offset, prefs, versionName, xiaomi)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page > 0 && !last) {
                IconButton(onClick = { Haptics.tick(view); go(page - 1) }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = look.text)
                }
            }
            Spacer(Modifier.weight(1f))
            if (!last && !(current == Step.TERMS && !accepted)) {
                TextButton(onClick = skip) { Text("Skip", color = look.sub) }
            }
        }
        if (current == Step.WELCOME) {
            WelcomeArrow(look, next, Modifier.align(Alignment.BottomCenter))
        } else {
            ContinueButton(look, next, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun pressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.93f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 520f),
        label = "press"
    )
    return scale
}

@Composable
private fun ContinueButton(look: Look, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Button(
        onClick = onClick,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        interactionSource = interaction,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = look.button, contentColor = look.onButton)
    ) {
        Text("Continue", fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WelcomeArrow(look: Look, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val press = pressScale(interaction)
    val t = rememberInfiniteTransition(label = "arrow")
    val breathe by t.animateFloat(
        1f, 1.07f,
        infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val ring by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )
    val nudge by t.animateFloat(
        -2f, 4f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "nudge"
    )
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 44.dp)
            .size(68.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(68.dp)) {
            val radius = size.minDimension / 2f * (1f + ring * 0.9f)
            drawCircle(
                look.button.copy(alpha = (1f - ring) * 0.35f),
                radius = radius,
                style = Stroke(2.dp.toPx())
            )
        }
        Box(
            modifier = Modifier
                .size(68.dp)
                .graphicsLayer {
                    val s = press * breathe
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(look.button)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_arrow_forward),
                contentDescription = "Get started",
                tint = look.onButton,
                modifier = Modifier.size(30.dp).offset(x = nudge.dp)
            )
        }
    }
}

@Composable
private fun Aurora(look: Look, pulse: () -> Float) {
    val base by animateColorAsState(look.base, tween(800), label = "base")
    val b0 by animateColorAsState(look.blobs[0], tween(800), label = "b0")
    val b1 by animateColorAsState(look.blobs[1], tween(800), label = "b1")
    val b2 by animateColorAsState(look.blobs[2], tween(800), label = "b2")
    val b3 by animateColorAsState(look.blobs[3], tween(800), label = "b3")
    val b4 by animateColorAsState(look.blobs[4], tween(800), label = "b4")
    val t = rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(base)
        val w = size.width
        val h = size.height
        val p = pulse()
        val r = maxOf(w, h) * 0.72f * (1f + p * 0.28f)
        val ph = phase + p * 1.6f
        fun blob(color: Color, cx: Float, cy: Float, ax: Float, ay: Float, k: Int, shift: Float) {
            val c = Offset(w * (cx + ax * sin(ph * k + shift)), h * (cy + ay * cos(ph * k + shift)))
            drawCircle(
                brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = c, radius = r),
                radius = r,
                center = c
            )
        }
        blob(b0, 0.15f, 0.2f, 0.26f, 0.16f, 1, 0f)
        blob(b1, 0.9f, 0.3f, 0.2f, 0.2f, 1, 2f)
        blob(b2, 0.2f, 0.8f, 0.26f, 0.15f, 2, 4f)
        blob(b3, 0.85f, 0.85f, 0.2f, 0.18f, 1, 1f)
        blob(b4, 0.5f, 0.5f, 0.3f, 0.24f, 3, 3f)
    }
}

@Composable
private fun SetupPage(step: Step, offset: Float, prefs: ModulePrefs, versionName: String, xiaomi: Boolean) {
    val look = LocalLook.current
    val a = abs(offset).coerceIn(0f, 1f)
    when (step.style) {
        Style.WELCOME, Style.DONE -> SplashPage(step, offset, a, versionName)
        Style.PLAIN -> if (step == Step.LANGUAGE) {
            LanguagePage(offset, a, prefs)
        } else if (step == Step.TERMS) {
            TermsPage(offset, a, prefs)
        } else if (step == Step.NOTIF || step == Step.RUN) {
            ActionPage(step, offset, a, xiaomi)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 112.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationX = offset * size.width * 0.55f
                            val s = 1f - 0.14f * a
                            scaleX = s
                            scaleY = s
                            alpha = 1f - a
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SetupHero(step, prefs, xiaomi)
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    titleOf(step, versionName),
                    modifier = Modifier.graphicsLayer {
                        val q = a.pow(0.8f)
                        translationX = offset * size.width * 0.25f
                        translationY = q * 36.dp.toPx()
                        alpha = 1f - q
                    },
                    fontSize = 27.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = look.text,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    subtitleOf(step, xiaomi),
                    modifier = Modifier.graphicsLayer {
                        val q = a.pow(0.55f)
                        translationX = offset * size.width * 0.2f
                        translationY = q * 52.dp.toPx()
                        alpha = 1f - q
                    },
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = look.sub,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RippleRings(color: Color) {
    val t = rememberInfiniteTransition(label = "rings")
    val p by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "p"
    )
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension * 0.8f
        for (i in 0 until 3) {
            val q = (p + i / 3f) % 1f
            drawCircle(
                color.copy(alpha = (1f - q) * 0.28f),
                radius = maxRadius * q,
                center = center,
                style = Stroke(2.dp.toPx())
            )
        }
    }
}

@Composable
private fun SplashPage(step: Step, offset: Float, a: Float, versionName: String) {
    val look = LocalLook.current
    val done = step.style == Style.DONE
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxSize()) {
        if (done) RippleRings(look.text)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 120.dp)
                .graphicsLayer {
                    translationX = offset * size.width * 0.3f
                    alpha = 1f - a
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "GlanceCaps",
                modifier = Modifier.graphicsLayer {
                    val e = enter.value
                    val s = 0.82f + 0.18f * e
                    scaleX = s
                    scaleY = s
                    alpha = e
                },
                style = TextStyle(
                    brush = Brush.horizontalGradient(look.wordmark),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Text(
                if (done) titleOf(step, versionName) else subtitleOf(step, false),
                modifier = Modifier.graphicsLayer {
                    val e = ((enter.value - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    alpha = e
                    translationY = (1f - e) * 28.dp.toPx()
                },
                fontSize = if (done) 22.sp else 17.sp,
                color = look.sub,
                textAlign = TextAlign.Center
            )
            if (done) {
                Spacer(Modifier.height(10.dp))
                Text(
                    subtitleOf(step, false),
                    modifier = Modifier.graphicsLayer {
                        val e = ((enter.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
                        alpha = e * 0.85f
                        translationY = (1f - e) * 20.dp.toPx()
                    },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = look.sub,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ActionPage(step: Step, offset: Float, a: Float, xiaomi: Boolean) {
    val look = LocalLook.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val tick = rememberTick()
    val pop = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f)) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> denied = !ok }
    val notif = step == Step.NOTIF
    val allowed = notif && tick >= 0 && ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    val needsSettings = denied || Build.VERSION.SDK_INT < 33
    val icon = if (notif) R.drawable.ic_bell_ring else R.drawable.ic_rocket
    val label = when {
        notif && needsSettings -> "Open settings"
        notif -> "Allow"
        xiaomi -> "Open autostart"
        else -> "Open settings"
    }
    val interaction = remember { MutableInteractionSource() }
    val press = pressScale(interaction)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(allowed) { shown = allowed }
    val doneScale by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.4f, stiffness = 380f), label = "allowed")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 56.dp, bottom = 112.dp)
            .graphicsLayer {
                translationX = offset * size.width * 0.3f
                alpha = 1f - a
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painterResource(icon), contentDescription = null, tint = Blue,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    rotationZ = (1f - pop.value) * -40f
                }
        )
        Spacer(Modifier.height(18.dp))
        Text(titleOf(step, ""), fontSize = 28.sp, color = look.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(subtitleOf(step, xiaomi), fontSize = 15.sp, lineHeight = 21.sp, color = look.sub, textAlign = TextAlign.Center)
        Spacer(Modifier.height(30.dp))
        if (allowed) {
            Row(
                modifier = Modifier
                    .height(52.dp)
                    .graphicsLayer {
                        scaleX = doneScale
                        scaleY = doneScale
                        alpha = doneScale.coerceIn(0f, 1f)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = Green, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text("Allowed", fontSize = 17.sp, color = look.text)
            }
        } else {
            Button(
                onClick = {
                    Haptics.tick(view)
                    if (notif) {
                        if (needsSettings) IslandSupport.openNotificationSettings(ctx) else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (xiaomi) {
                        IslandSupport.openAutostartSettings(ctx)
                    } else {
                        IslandSupport.openAppInfo(ctx)
                    }
                },
                modifier = Modifier
                    .height(52.dp)
                    .graphicsLayer {
                        scaleX = press
                        scaleY = press
                    },
                interactionSource = interaction,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = look.button, contentColor = look.onButton)
            ) {
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun LanguagePage(offset: Float, a: Float, prefs: ModulePrefs) {
    val look = LocalLook.current
    val pop = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f)) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 84.dp, bottom = 112.dp)
            .graphicsLayer {
                translationX = offset * size.width * 0.3f
                alpha = 1f - a
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painterResource(R.drawable.ic_language), contentDescription = null, tint = Blue,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    rotationZ = (1f - pop.value) * -40f
                }
        )
        Spacer(Modifier.height(14.dp))
        Text(titleOf(Step.LANGUAGE, ""), fontSize = 28.sp, color = look.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        LanguageList(prefs)
    }
}

@Composable
private fun LanguageRow(index: Int, key: String, label: String, prefs: ModulePrefs) {
    val look = LocalLook.current
    val view = LocalView.current
    val selected = Lang.setting == key
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(140L + 110L * index)
        enter.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    val highlight by animateColorAsState(if (selected) Blue.copy(alpha = 0.14f) else Color.Transparent, tween(260), label = "hl")
    val checkScale by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 420f), label = "check")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 28.dp.toPx()
            }
            .clip(RoundedCornerShape(18.dp))
            .background(highlight)
            .clickable {
                Haptics.tick(view)
                Lang.setting = key
                prefs.language = key
            }
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 18.sp, color = look.text, modifier = Modifier.weight(1f))
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_check), contentDescription = null, tint = Blue,
                modifier = Modifier.size(26.dp).graphicsLayer {
                    scaleX = checkScale
                    scaleY = checkScale
                    alpha = checkScale.coerceIn(0f, 1f)
                }
            )
        }
    }
}

@Composable
private fun LanguageList(prefs: ModulePrefs) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Lang.SYSTEM to "System default", Lang.EN to "English", Lang.VI to "Tiếng Việt", Lang.ES to "Español (Latinoamérica)").forEachIndexed { index, (key, label) ->
            LanguageRow(index, key, label, prefs)
        }
    }
}

@Composable
private fun SetupHero(step: Step, prefs: ModulePrefs, xiaomi: Boolean) {
    when (step) {
        Step.APPEARANCE -> AppearanceHero(prefs)
        Step.CHARGING -> ChargingHero()
        Step.STEPS -> StepsHero()
        Step.CONN -> ConnHero()
        Step.MATCH -> MatchHero()
        Step.WEATHER -> WeatherHero()
        Step.ISLAND -> IslandHero(prefs, xiaomi)
        Step.PERMS -> PermissionsHero()
        Step.NEW_LANG -> LanguageHero(prefs)
        Step.NEW_CHINA -> ChinaHero()
        Step.NEW_WEATHER -> ScheduleHero()
        else -> Unit
    }
}

private val SkyTop = Color(0xFF5E9BD6)
private val SkyBottom = Color(0xFFA9CDEA)
private val GlassDark = Color(0xE60F1D2A)

@Composable
private fun SkyStage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(SkyTop, SkyBottom)))
            .padding(horizontal = 14.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
private fun RealPill(widthFraction: Float = 0.94f, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(42.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content
    )
}

@Composable
private fun RealCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(GlassDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            content = content
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .width(44.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.28f))
        )
    }
}

@Composable
private fun CardLines(title: String, sub: String, tail: String?, tailColor: Color, endIcon: @Composable (() -> Unit)?) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 21.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                buildAnnotatedString {
                    append(tr(sub))
                    if (tail != null) withStyle(SpanStyle(color = tailColor)) { append(tr(tail)) }
                },
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 14.sp
            )
        }
        endIcon?.invoke()
    }
}

@Composable
private fun SoftBar(progress: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(12.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

private val Gold = Color(0xFFFFC107)
private val Green = Color(0xFF34C759)
private val Orange = Color(0xFFFF9F0A)
private val SkyBlue = Color(0xFF8AB4FF)

@Composable
private fun <T> CycleStage(states: List<T>, periodMs: Long, content: @Composable (T) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(states.size) {
        while (true) {
            delay(periodMs)
            index = (index + 1) % states.size
        }
    }
    SkyStage {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                (fadeIn(tween(420)) + scaleIn(tween(420), initialScale = 0.9f) + slideInVertically(tween(420)) { -it / 4 }) togetherWith
                    (fadeOut(tween(240)) + scaleOut(tween(240), targetScale = 0.95f))
            },
            label = "cycle"
        ) { i -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { content(states[i]) } }
    }
}

@Composable
private fun ChargingHero() {
    CycleStage(listOf(0, 1), 2700) { state ->
        if (state == 0) {
            RealCard {
                CardLines("1.6 W", "63%  -  12:38  ", "3h 14m", Green) {
                    Icon(painterResource(R.drawable.ic_bolt), contentDescription = null, tint = Gold, modifier = Modifier.size(52.dp))
                }
                Spacer(Modifier.height(12.dp))
                SoftBar(0.63f, Green)
            }
        } else {
            RealPill {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_bolt), contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("1.6 W", color = Color.White, fontSize = 15.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("37.0°C", color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    Icon(painterResource(R.drawable.ic_thermostat), contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun StepsTrack(progress: Float) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(40.dp)) {
        val total = maxWidth
        Canvas(Modifier.fillMaxWidth().height(14.dp).align(Alignment.BottomCenter)) {
            drawRoundRect(Color.White.copy(alpha = 0.14f), cornerRadius = CornerRadius(size.height / 2, size.height / 2))
            val dash = 7.dp.toPx()
            val gap = 8.dp.toPx()
            var x = 14.dp.toPx()
            while (x < size.width - 14.dp.toPx()) {
                drawRoundRect(
                    Color.White.copy(alpha = 0.3f),
                    topLeft = Offset(x, size.height / 2 - 1.5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(dash, 3.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                x += dash + gap
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progress.coerceIn(0.05f, 1f))
                .height(14.dp)
                .clip(CircleShape)
                .background(Orange)
        )
        Icon(
            painterResource(R.drawable.ic_directions_walk), contentDescription = null, tint = Orange,
            modifier = Modifier.align(Alignment.BottomStart).offset(x = total * progress - 6.dp, y = (-8).dp).size(28.dp)
        )
        Icon(
            painterResource(R.drawable.ic_flag_goal), contentDescription = null, tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 10.dp).size(22.dp)
        )
    }
}

@Composable
private fun StepsHero() {
    CycleStage(listOf(0, 1), 2800) { state ->
        if (state == 0) {
            RealPill {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_directions_walk), contentDescription = null, tint = Orange, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("846 steps", color = Color.White, fontSize = 15.sp)
                }
                Text("8%", color = Color.White, fontSize = 15.sp)
            }
        } else {
            RealCard {
                CardLines("846 steps", "Goal 10,000  ", "8%", Orange, null)
                Spacer(Modifier.height(10.dp))
                StepsTrack(0.08f)
            }
        }
    }
}

@Composable
private fun ConnHero() {
    CycleStage(listOf(0, 1, 2, 3), 2200) { state ->
        when (state) {
            0 -> RealPill(0.6f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_wifi_on), contentDescription = null, tint = SkyBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Wi-Fi on", color = Color.White, fontSize = 15.sp)
                }
            }
            1 -> RealPill(0.9f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_wifi_on), contentDescription = null, tint = SkyBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Wi-Fi connected", color = Color.White, fontSize = 13.sp)
                }
                Text("Home Sweet Home", color = Color.White, fontSize = 16.sp)
            }
            2 -> RealPill(0.9f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_bluetooth_on), contentDescription = null, tint = SkyBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bluetooth connected", color = Color.White, fontSize = 13.sp)
                }
                Text("Headphones", color = Color.White, fontSize = 16.sp)
            }
            else -> RealPill(0.7f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_airplane_on), contentDescription = null, tint = Orange, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Airplane mode on", color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun SetupBadge(label: String, color: Color) {
    Box(
        modifier = Modifier.size(28.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun MatchHero() {
    CycleStage(listOf(0, 1, 2, 3), 2200) { state ->
        val home = if (state == 0) 0 else 1
        val away = if (state >= 3) 1 else 0
        if (state == 2) {
            RealCard {
                CardLines("HAN  $home - $away  SGN", "V.League 1  -  ", "67'", Green, null)
                Spacer(Modifier.height(12.dp))
                SoftBar(67f / 90f, Green)
            }
        } else {
            RealPill {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SetupBadge("HAN", Color(0xFFD32F2F))
                    Spacer(Modifier.width(10.dp))
                    Text("$home", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$away", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    SetupBadge("SGN", Color(0xFF1976D2))
                }
            }
        }
    }
}

@Composable
private fun WeatherHero() {
    val red = Color(0xFFFF6B5E)
    CycleStage(listOf(0, 1, 2, 3), 2300) { state ->
        when (state) {
            0 -> RealPill {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_wx_partly), contentDescription = null, tint = SkyBlue, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("29°C", color = Color.White, fontSize = 15.sp)
                }
                Text("Partly cloudy", color = Color.White, fontSize = 15.sp)
            }
            1 -> RealCard {
                CardLines("29°C  Partly cloudy", "Feels 33°", null, Color.White, {
                    Icon(painterResource(R.drawable.ic_wx_partly), contentDescription = null, tint = SkyBlue, modifier = Modifier.size(48.dp))
                })
            }
            2 -> RealPill {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = red, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cyclone", color = Color.White, fontSize = 15.sp)
                }
                Text("180 km", color = Color.White, fontSize = 15.sp)
            }
            else -> RealCard {
                CardLines("Typhoon approaching", "GDACS  -  180 km away", null, Color.White, {
                    Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = red, modifier = Modifier.size(44.dp))
                })
            }
        }
    }
}

@Composable
private fun SetupAction(
    title: String,
    status: String?,
    done: Boolean,
    action: String,
    icon: Int? = null,
    tile: Color = Blue,
    onClick: () -> Unit,
) {
    val look = LocalLook.current
    val t = rememberInfiniteTransition(label = "tile")
    val pulse by t.animateFloat(
        1f, 1.1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val tilt by t.animateFloat(
        -8f, 8f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tilt"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(look.card)
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .graphicsLayer {
                        val s = if (done) 1f else pulse
                        scaleX = s
                        scaleY = s
                        rotationZ = if (done) 0f else tilt
                    }
                    .clip(RoundedCornerShape(13.dp))
                    .background(tile),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
        } else {
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = look.text)
            if (status != null) Text(status, fontSize = 13.sp, color = look.sub)
        }
        if (done) {
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            val doneScale by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.4f, stiffness = 380f), label = "done")
            Box(Modifier.padding(end = 10.dp).size(26.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_check_circle), contentDescription = "Done", tint = Green,
                    modifier = Modifier.size(26.dp).graphicsLayer {
                        scaleX = doneScale
                        scaleY = doneScale
                        alpha = doneScale.coerceIn(0f, 1f)
                    }
                )
            }
        } else {
            TextButton(onClick = onClick) { Text(action, color = Blue) }
        }
    }
}

@Composable
private fun rememberTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(800)
            tick++
        }
    }
    return tick
}

@Composable
private fun SetupOption(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(look.card)
            .border(2.dp, if (selected) Blue else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = look.text)
            Text(sub, fontSize = 13.sp, color = look.sub)
        }
        if (selected) {
            Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = Blue, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun IslandHero(prefs: ModulePrefs, xiaomi: Boolean) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var mode by remember { mutableStateOf(prefs.islandMode) }
    val pick: (String) -> Unit = { key ->
        Haptics.tick(view)
        mode = key
        prefs.islandMode = key
    }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SetupOption("Auto", "The best option for your phone", mode == "auto") { pick("auto") }
        SetupOption("Xiaomi Super Island", "Xiaomi HyperOS only", mode == "hyper") { pick("hyper") }
        SetupOption("Live Update", "Android 16 and newer", mode == "live") { pick("live") }
        SetupAction(
            title = if (xiaomi) "Island permission" else "Live Update permission",
            status = "Check it in the notification settings",
            done = false,
            action = "Open",
            icon = R.drawable.ic_bolt,
            tile = Orange
        ) { IslandSupport.openNotificationSettings(ctx) }
    }
}

@Composable
private fun PermissionsHero() {
    val ctx = LocalContext.current
    val tick = rememberTick()
    val activity = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val names = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val activityOk = tick >= 0 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    val locationOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val backgroundOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    val nearbyOk = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SetupAction("Activity recognition", "For the Steps module", activityOk, "Allow", R.drawable.ic_directions_walk, Green) {
            activity.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        SetupAction("Location and nearby devices", "For Wi-Fi and Bluetooth names", locationOk && nearbyOk, "Allow", R.drawable.ic_map_marker, Blue) {
            val wanted = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 31) wanted.add(Manifest.permission.BLUETOOTH_CONNECT)
            names.launch(wanted.toTypedArray())
        }
        SetupAction("Location, all the time", "So names show while the app is closed", backgroundOk, "Open", R.drawable.ic_map_marker, Color(0xFF00A99D)) {
            IslandSupport.openAppInfo(ctx)
        }
    }
}

@Composable
private fun LanguageHero(prefs: ModulePrefs) {
    val look = LocalLook.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(look.card)
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        LanguageList(prefs)
    }
}

@Composable
private fun ChinaHero() {
    val look = LocalLook.current
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1700)
            index = (index + 1) % 3
        }
    }
    val states = listOf("Off", "Permission needed", "Ready")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(look.card)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Shizuku workaround", fontSize = 17.sp, color = look.text)
            Text(states[index], fontSize = 13.sp, color = look.sub)
        }
        Switch(checked = index > 0, onCheckedChange = null)
    }
}

@Composable
private fun ScheduleHero() {
    val look = LocalLook.current
    val options = remember { listOf("Any time", "Between two times", "When the weather changes", "Every few hours") }
    var index by remember { mutableIntStateOf(2) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1700)
            index = (index + 1) % options.size
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            val selected = i == index
            val shape = RoundedCornerShape(20.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(look.card)
                    .border(2.dp, if (selected) Blue else Color.Transparent, shape)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontSize = 16.sp, color = look.text, modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Blue, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun AppearanceHero(prefs: ModulePrefs) {
    val look = LocalLook.current
    val view = LocalView.current
    var mode by remember { mutableStateOf(prefs.themeMode) }
    val darkNow = appDark()
    val set: (String) -> Unit = { value ->
        Haptics.tick(view)
        mode = value
        prefs.themeMode = value
        UiState.load(prefs)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ThemeTile(false, !darkNow, "Light mode", Modifier.weight(1f)) { set("light") }
            ThemeTile(true, darkNow, "Dark mode", Modifier.weight(1f)) { set("dark") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(look.card)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Follow system", fontSize = 17.sp, color = look.text, modifier = Modifier.weight(1f))
            Switch(
                checked = mode == "system",
                onCheckedChange = { on -> set(if (on) "system" else if (darkNow) "dark" else "light") }
            )
        }
    }
}

private fun termsCodeOf(prefs: ModulePrefs): String {
    if (prefs.termsCode.isEmpty()) prefs.termsCode = "IREADTHETERMS_" + (1000..9999).random()
    return prefs.termsCode
}

private val termsSections = listOf(
    "Hi there" to "GlanceCaps is a free, open source hobby project made by one person (me, Dang). It is provided as is, and I can't promise it will always work.",
    "What it can access" to "To show islands, GlanceCaps reads your battery, steps, Wi-Fi and Bluetooth state and, if you allow it, their names and your location. All of that stays on your phone.",
    "What goes online" to "Weather, match scores and weather alerts come from public services like Open-Meteo, ESPN, GDACS and USGS. Your saved city coordinates are sent to them, and any API key you enter stays on your phone.",
    "The Shizuku workaround" to "If you turn it on, GlanceCaps asks Shizuku to switch Xiaomi's push service off and on again for a moment. That can affect other notifications while it runs, so use it at your own risk.",
    "No warranty" to "I'm not responsible for missed notifications, wrong data, battery use or any damage to your device. Please check important things like weather alerts with official sources.",
    "Not affiliated" to "GlanceCaps is not made by, or connected to, Xiaomi, Google or any service it uses. All names and logos belong to their owners.",
    "Changes" to "These terms may change in future versions. If you keep using the app after an update, that means you're okay with the new ones."
)

@Composable
private fun TermsPage(offset: Float, a: Float, prefs: ModulePrefs) {
    val look = LocalLook.current
    val code = remember { termsCodeOf(prefs) }
    val t = rememberInfiniteTransition(label = "terms")
    val bob by t.animateFloat(
        -4f, 4f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 112.dp)
            .graphicsLayer {
                translationX = offset * size.width * 0.3f
                alpha = 1f - a
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painterResource(R.drawable.ic_terms), contentDescription = null, tint = Blue,
            modifier = Modifier.size(52.dp).graphicsLayer { translationY = bob.dp.toPx() }
        )
        Spacer(Modifier.height(10.dp))
        Text(titleOf(Step.TERMS, ""), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = look.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(look.card)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            termsSections.forEach { (heading, body) ->
                Text(heading, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = look.text)
                Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = look.sub)
                Spacer(Modifier.height(8.dp))
            }
            Text("Almost done! To continue, type this code:", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = look.text)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Blue.copy(alpha = 0.14f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(code, fontFamily = FontFamily.Monospace, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Blue)
            }
        }
    }
}

@Composable
private fun CodeBody(value: String, wrong: Boolean, onChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text("Type the code from the end of the terms to continue.", fontSize = 15.sp, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            isError = wrong,
            placeholder = { Text("IREADTHETERMS_0000") },
            modifier = Modifier.fillMaxWidth()
        )
        if (wrong) {
            Spacer(Modifier.height(8.dp))
            Text("That code isn't right, check the end of the terms.", fontSize = 13.sp, color = cs.error)
        }
    }
}
