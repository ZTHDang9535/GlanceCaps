package com.dang.glancecaps.ui

import com.dang.glancecaps.island.CallAvatar
import android.graphics.Bitmap
import kotlin.math.roundToInt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.offset
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.width
import com.dang.glancecaps.R
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dang.glancecaps.battery.BatterySnapshot
import com.dang.glancecaps.battery.ThermalState
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.island.GlanceCapsNotifications
import com.dang.glancecaps.island.GlanceCapsNotifications.ConnKind
import com.dang.glancecaps.island.IconStore
import com.dang.glancecaps.island.IslandIcons
import com.dang.glancecaps.island.IslandText
import com.dang.glancecaps.island.WallpaperStore
import com.dang.glancecaps.match.MatchInfo
import com.dang.glancecaps.match.MatchLogos
import com.dang.glancecaps.weather.Weather
import com.dang.glancecaps.weather.WeatherCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberWallpaper(): Bitmap? {
    val ctx = LocalContext.current
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) { bmp = withContext(Dispatchers.IO) { WallpaperStore.load(ctx) } }
    return bmp
}

@Composable
fun PreviewStage(wallpaper: Bitmap?, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
    ) {
        if (wallpaper != null) {
            val img = remember(wallpaper) { wallpaper.asImageBitmap() }
            Image(
                bitmap = img,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                alignment = Alignment.TopCenter,
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(cs.primary.copy(alpha = 0.55f), cs.tertiary.copy(alpha = 0.5f), cs.surfaceContainerHigh)
                        )
                    )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun PillShell(outline: Color, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(Color.Black)
                .border(1.5.dp, outline.copy(alpha = 0.95f), shape)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            content = content
        )
    }
}

@Composable
private fun RingIcon(icon: Bitmap, progress: Float?, ringColor: Color, size: Dp) {
    val img = remember(icon) { icon.asImageBitmap() }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (progress != null) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 2.5.dp.toPx()
                val arcSize = Size(this.size.width - sw, this.size.height - sw)
                val topLeft = Offset(sw / 2f, sw / 2f)
                drawArc(
                    color = Color(0xFF444444), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = sw)
                )
                drawArc(
                    color = ringColor, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }
        }
        Image(bitmap = img, contentDescription = null, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
private fun TitleBlock(title: String, subtitle: String) {
    Column {
        Text(
            title, color = Color.White,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
        )
        if (subtitle.isNotEmpty()) {
            Text(subtitle, color = Color(0xFFB5B5B5), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun PlainBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(CircleShape)
            .background(Color(0xFF1C1C1E))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
private fun ParkingCard(
    title: String,
    content: String,
    sub: String?,
    accent: Color,
    icon: Bitmap?,
    progress: Float?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black)
    ) {
        ParkingCardBody(title, content, sub, accent, icon, progress)
    }
}

@Composable
private fun ParkingCardBody(
    title: String,
    content: String,
    sub: String?,
    accent: Color,
    icon: Bitmap?,
    progress: Float?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    title, color = Color.White,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium
                )
                Text(
                    buildAnnotatedString {
                        append(content)
                        if (!sub.isNullOrEmpty()) {
                            append("  ")
                            withStyle(SpanStyle(color = accent)) { append(sub) }
                        }
                    },
                    color = Color(0xFFB5B5B5),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (icon != null) {
                Spacer(Modifier.width(12.dp))
                GlyphIcon(icon, 40.dp)
            }
        }
        if (progress != null) PlainBar(progress, accent)
    }
}

@Composable
fun ChargingPreview(s: BatterySnapshot, eta: Int?, prefs: ModulePrefs, rev: Int) {
    val ctx = LocalContext.current
    val live = s.charging && !s.full
    val demo = if (live) s else s.copy(charging = true, full = false, watts = 25.4f, plugType = "USB")
    val demoEta = if (live) eta else 24
    val t = IslandText.charging(ctx, demo, demoEta, prefs)
    val bar = Color(hex(prefs.chgBarColor))
    val merged = prefs.tempEnabled && prefs.tempMerge
    val bolt = remember(rev) { IconStore.bolt(ctx) }
    val state = ThermalState.of(s.tempC)
    val outline = if (merged && prefs.tempColorFollows) Color(hex(state.hex)) else bar

    PillShell(outline) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prefs.chgShowIcon) {
                RingIcon(bolt, if (prefs.chgShowRing) demo.level / 100f else null, bar, 36.dp)
                Spacer(Modifier.width(10.dp))
            }
            TitleBlock(t.title, t.subtitle)
        }
        if (merged) {
            val tt = IslandText.temperature(s, prefs)
            val thermo = remember(state) { GlanceCapsNotifications.thermoBitmap(ctx, s) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tt.title, color = Color.White, style = MaterialTheme.typography.titleSmall)
                if (prefs.tempShowIcon) {
                    Spacer(Modifier.width(6.dp))
                    GlyphIcon(thermo, 24.dp)
                }
            }
        }
    }

    ParkingCard(
        title = t.cardTitle, content = t.content, sub = t.sub, accent = bar,
        icon = bolt, progress = if (prefs.chgShowBar) demo.level / 100f else null
    )

}

@Composable
fun TempPreview(s: BatterySnapshot, prefs: ModulePrefs, rev: Int) {
    val ctx = LocalContext.current
    val state = ThermalState.of(s.tempC)
    val base = hex(state.hex)
    val tt = IslandText.temperature(s, prefs)
    val thermo = remember(state, rev) { GlanceCapsNotifications.thermoBitmap(ctx, s) }
    val outline = if (prefs.tempColorFollows) Color(base) else Color.White

    if (prefs.tempMerge) {
        val bolt = remember(rev) { IconStore.bolt(ctx) }
        val bar = Color(hex(prefs.chgBarColor))
        PillShell(outline) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingIcon(bolt, 0.63f, bar, 36.dp)
                Spacer(Modifier.width(10.dp))
                TitleBlock("25.4 W", "63% - 22:41 - 24 min")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tt.title, color = Color.White, style = MaterialTheme.typography.titleSmall)
                if (prefs.tempShowIcon) {
                    Spacer(Modifier.width(6.dp))
                    GlyphIcon(thermo, 24.dp)
                }
            }
        }
    } else {
        PillShell(outline) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (prefs.tempShowIcon) {
                    GlyphIcon(thermo, 32.dp)
                    Spacer(Modifier.width(10.dp))
                }
                TitleBlock(tt.title, tt.subtitle)
            }
        }
        ParkingCard(
            title = tt.cardTitle, content = tt.content, sub = tt.sub, accent = Color(base),
            icon = if (prefs.tempShowIcon) thermo else null, progress = null
        )
    }
}

@Composable
fun StepsPreview(steps: Int?, prefs: ModulePrefs, rev: Int) {
    val ctx = LocalContext.current
    val live = steps != null
    val n = steps ?: 6248
    val goal = prefs.stepGoal.coerceAtLeast(1)
    val pct = (n * 100L / goal).toInt().coerceIn(0, 100)
    val t = IslandText.steps(n, goal, prefs)
    val color = Color(hex(prefs.stepColor))
    val walk = remember(rev) { GlanceCapsNotifications.walkBitmap(ctx, prefs) }

    PillShell(color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prefs.stepShowIcon) {
                RingIcon(walk, if (prefs.stepShowRing) pct / 100f else null, color, 36.dp)
                Spacer(Modifier.width(10.dp))
            }
            TitleBlock(t.title, t.subtitle)
        }
        if (prefs.stepShowPercent) {
            Text("$pct%", color = Color.White, style = MaterialTheme.typography.titleSmall)
        }
    }
    if (prefs.stepTaxi) {
        TaxiCard(
            title = t.cardTitle, content = t.content, sub = t.sub, accent = color,
            walker = walk, flag = remember(rev) { GlanceCapsNotifications.flagBitmap(ctx) },
            progress = if (prefs.stepShowBar) pct / 100f else null
        )
    } else {
        ParkingCard(
            title = t.cardTitle, content = t.content, sub = t.sub, accent = color,
            icon = if (prefs.stepShowIcon) walk else null,
            progress = if (prefs.stepShowBar) pct / 100f else null
        )
    }
}

@Composable
private fun TaxiCard(
    title: String,
    content: String,
    sub: String?,
    accent: Color,
    walker: Bitmap,
    flag: Bitmap,
    progress: Float?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            title, color = Color.White,
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium
        )
        Text(
            buildAnnotatedString {
                append(content)
                if (!sub.isNullOrEmpty()) {
                    append("  ")
                    withStyle(SpanStyle(color = accent)) { append(sub) }
                }
            },
            color = Color(0xFFB5B5B5),
            style = MaterialTheme.typography.bodyMedium
        )
        if (progress != null) {
            val f = progress.coerceIn(0.04f, 0.96f)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(f)
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.55f))))
                )
                GlyphIcon(walker, 30.dp)
                Box(
                    Modifier
                        .weight(1f - f)
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1C1E))
                )
                Spacer(Modifier.width(6.dp))
                GlyphIcon(flag, 22.dp)
            }
        }
    }
}

@Composable
fun ConnectivityPreview(prefs: ModulePrefs, rev: Int) {
    val ctx = LocalContext.current
    val rows = ArrayList<Triple<ConnKind, Boolean, String?>>()
    if (prefs.connWifi) rows.add(Triple(ConnKind.WIFI, true, if (prefs.connShowNames) "Home Wi-Fi" else null))
    if (prefs.connBluetooth) rows.add(Triple(ConnKind.BLUETOOTH, true, if (prefs.connShowNames) "Headphones" else null))
    if (prefs.connAirplane) rows.add(Triple(ConnKind.AIRPLANE, true, null))
    rows.forEach { (kind, on, detail) ->
        val bmp = remember(rev, kind) { GlanceCapsNotifications.connBitmap(ctx, kind, on) }
        val accent = Color(hex(GlanceCapsNotifications.connAccent(kind, on)))
        val name = GlanceCapsNotifications.connName(kind, on, detail)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(Color.Black)
                .border(1.5.dp, accent.copy(alpha = 0.95f), RoundedCornerShape(30.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(bmp, 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    GlanceCapsNotifications.connState(kind, on, name != null), color = Color.White,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium
                )
            }
            if (name != null) {
                Text(name, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
fun PinnedPreview(prefs: ModulePrefs, rev: Int) {
    val ctx = LocalContext.current
    val bmp = remember(rev) { GlanceCapsNotifications.pinnedBitmap(ctx, prefs) }
    val outline = remember(rev) {
        try {
            Color(android.graphics.Color.parseColor(prefs.pinnedColor.trim()))
        } catch (e: Throwable) {
            Color.Black
        }
    }
    val side = prefs.pinnedTextSide
    val text = if (prefs.pinnedShowText) prefs.pinnedText.trim() else ""
    val text2 = if (prefs.pinnedShowText && side == 2) prefs.pinnedText2.trim() else ""
    val shape = RoundedCornerShape(30.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(Color.Black)
                .then(if (GlanceCapsNotifications.useHyper(prefs)) Modifier.border(2.dp, outline, shape) else Modifier)
                .padding(horizontal = 14.dp, vertical = 9.dp)
                .widthIn(min = (if (prefs.pinnedPadAuto) 52 else prefs.pinnedPadLen * 11).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val iconSlot: @Composable () -> Unit = {
                if (prefs.pinnedShowIcon) GlyphIcon(bmp, 26.dp) else Spacer(Modifier.size(26.dp))
            }
            val textSlot: @Composable (String) -> Unit = { t ->
                androidx.compose.material3.Text(
                    t, color = Color.White, maxLines = 1,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            when {
                text.isEmpty() && text2.isEmpty() -> iconSlot()
                side == 0 -> {
                    textSlot(text)
                    Spacer(Modifier.width(14.dp))
                    iconSlot()
                }
                side == 2 -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        iconSlot()
                        if (text.isNotEmpty()) textSlot(text)
                    }
                    Spacer(Modifier.width(14.dp))
                    if (text2.isNotEmpty()) textSlot(text2)
                }
                else -> {
                    iconSlot()
                    Spacer(Modifier.width(14.dp))
                    textSlot(text)
                }
            }
        }
    }
}

@Composable
fun UpdatePreview(version: String) {
    val ctx = LocalContext.current
    val bmp = remember { GlanceCapsNotifications.appIconBitmap(ctx) }
    PillShell(Color(0xFF3D8BFF)) {
        GlyphIcon(bmp, 26.dp)
        Text(
            GlanceCapsNotifications.updateLabel(version), color = Color.White,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium
        )
    }
}

private fun hexColor(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex.trim()))
} catch (e: Throwable) {
    fallback
}

@Composable
fun CallPreview(prefs: ModulePrefs, rev: Int) {
    val ctx = LocalContext.current
    val green = remember(rev) { IslandIcons.flat(ctx, R.drawable.ic_call, android.graphics.Color.parseColor("#22C841")) }
    val avatar = remember(rev) { if (prefs.callShowAvatar) CallAvatar.placeholder(ctx, "Contact name", prefs) else null }
    var incoming by remember { mutableStateOf(false) }
    val status = if (incoming) {
        prefs.callTextIncoming.trim().take(16).ifEmpty { "Incoming call" }
    } else {
        prefs.callTextOutgoing.trim().take(16).ifEmpty { "Calling..." }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilterChip(selected = !incoming, onClick = { incoming = false }, label = { Text("Outgoing") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = incoming, onClick = { incoming = true }, label = { Text("Incoming") })
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.Black)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(36.dp)
            ) {
                GlyphIcon(avatar ?: green, 26.dp)
                androidx.compose.material3.Text(status, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (prefs.callButtons) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(34.dp))
                    .background(Color(0xFF1E2229))
                    .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (avatar != null) {
                    GlyphIcon(avatar, 46.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Contact name", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    androidx.compose.material3.Text(status, color = Color(0xFF9AA0B4), style = MaterialTheme.typography.bodyMedium)
                }
                if (incoming) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(hexColor(prefs.callColorEnd, Color(0xFFFF3B30))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(R.drawable.ic_call_end), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(hexColor(prefs.callColorAnswer, Color(0xFF22C841))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(R.drawable.ic_call), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                } else {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(hexColor(prefs.callColorSpeaker, Color(0xFF3A3F52))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(R.drawable.ic_volume_up), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(hexColor(prefs.callColorEnd, Color(0xFFFF3B30))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(R.drawable.ic_call_end), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

private class MatchLogoSet(val home: Bitmap, val away: Bitmap, val pair: Bitmap)

@Composable
private fun rememberMatchLogos(m: MatchInfo): MatchLogoSet? {
    val set by produceState<MatchLogoSet?>(null, m.home.logo, m.away.logo) {
        value = withContext(Dispatchers.IO) {
            val h = MatchLogos.load(m.home.logo)
            val a = MatchLogos.load(m.away.logo)
            MatchLogoSet(h ?: MatchLogos.placeholder(), a ?: MatchLogos.placeholder(), MatchLogos.pair(h, a))
        }
    }
    return set
}

@Composable
fun MatchPreview(m: MatchInfo, prefs: ModulePrefs, rev: Int) {
    val ctx = LocalContext.current
    val logos = rememberMatchLogos(m)
    val accent = if (m.live) Color(0xFF34C759) else Color(0xFF8E8E93)
    val placeholder = remember { MatchLogos.placeholder() }
    val home = logos?.home ?: placeholder
    val away = logos?.away ?: placeholder

    PillShell(accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(home, 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(m.homeScore, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.awayScore, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            GlyphIcon(away, 26.dp)
        }
    }
    ParkingCard(
        title = GlanceCapsNotifications.matchTitle(m),
        content = GlanceCapsNotifications.matchStatusLine(ctx, m),
        sub = null,
        accent = accent,
        icon = logos?.pair ?: placeholder,
        progress = if (m.live) (m.minute * 100 / 90).coerceIn(0, 100) / 100f else null
    )
}

@Composable
fun WeatherPreview(w: Weather, prefs: ModulePrefs, rev: Int) {
    val ctx = LocalContext.current
    val bmp = remember(w.code, w.day) { GlanceCapsNotifications.weatherBitmap(ctx, w) }
    val accent = Color(hex(WeatherCodes.accent(w.code, w.day)))
    val label = if (prefs.weatherShowCondition) WeatherCodes.label(w.code, w.day) else GlanceCapsNotifications.weatherRange(w, prefs)

    PillShell(accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(bmp, 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                GlanceCapsNotifications.weatherTemp(w.temp, prefs),
                color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.titleSmall)
    }
    ParkingCard(
        title = GlanceCapsNotifications.weatherHeadline(w, prefs),
        content = GlanceCapsNotifications.weatherRange(w, prefs),
        sub = null,
        accent = accent,
        icon = bmp,
        progress = null
    )
}
