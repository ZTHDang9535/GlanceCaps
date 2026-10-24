package com.dang.glancecaps.ui

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun hex(h: String): Int = AColor.parseColor(h)

@Composable
fun GlyphIcon(bitmap: Bitmap, size: Dp, modifier: Modifier = Modifier) {
    val img = remember(bitmap) { bitmap.asImageBitmap() }
    Image(bitmap = img, contentDescription = null, modifier = modifier.size(size))
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp)
    )
}

@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(content = content)
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun IconBadge(@DrawableRes icon: Int) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = cs.primaryContainer,
        modifier = Modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = cs.onPrimaryContainer
            )
        }
    }
}

@Composable
fun RowItem(
    @DrawableRes icon: Int?,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val base = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = base
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
fun HSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val view = LocalView.current
    Switch(checked = checked, onCheckedChange = { Haptics.toggle(view, it); onChange(it) }, colors = glanceSwitchColors())
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String? = null,
    get: () -> Boolean,
    set: (Boolean) -> Unit,
    bump: () -> Unit = {},
    lockedReason: String? = null,
) {
    val view = LocalView.current
    var value by remember { mutableStateOf(get()) }
    val locked = lockedReason != null
    val toggle: () -> Unit = {
        value = !value
        set(value)
        Haptics.toggle(view, value)
        bump()
    }
    val change: (Boolean) -> Unit = {
        value = it
        set(it)
        Haptics.toggle(view, it)
        bump()
    }
    Box(Modifier.alpha(if (locked) 0.45f else 1f)) {
        RowItem(
            icon = null,
            title = title,
            subtitle = lockedReason ?: subtitle,
            onClick = if (locked) null else toggle
        ) {
            Switch(
                checked = value,
                onCheckedChange = if (locked) null else change,
                enabled = !locked,
                colors = glanceSwitchColors()
            )
        }
    }
}

private val swatchColors = listOf(
    "#34C759", "#30D158", "#00E5FF", "#2979FF", "#B388FF", "#FF2D55", "#FF9500", "#FFD60A",
    "#FF3B30", "#5856D6", "#00C7BE", "#FFFFFF", "#A5D6A7", "#90CAF9", "#F48FB1", "#FFCC80",
    "#39FF14", "#FF00E5", "#FF5F1F", "#B026FF"
)

private val hexPattern = Regex("^#?[0-9A-Fa-f]{6}$")

@Composable
fun ColorSwatches(selected: String, onSelect: (String) -> Unit) {
    val view = LocalView.current
    val cs = MaterialTheme.colorScheme
    var current by remember { mutableStateOf(selected) }
    var custom by remember { mutableStateOf(selected.removePrefix("#")) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            swatchColors.forEach { h ->
                val isSel = h.equals(current, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(hex(h)))
                        .border(
                            BorderStroke(
                                if (isSel) 3.dp else 1.dp,
                                cs.onSurface.copy(alpha = if (isSel) 1f else 0.2f)
                            ),
                            CircleShape
                        )
                        .clickable {
                            current = h
                            custom = h.removePrefix("#")
                            Haptics.tick(view)
                            onSelect(h)
                        }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(hex(if (hexPattern.matches(custom)) normalizeHex(custom) else current)))
                    .border(BorderStroke(1.dp, cs.onSurface.copy(alpha = 0.2f)), CircleShape)
            )
            OutlinedTextField(
                value = custom,
                onValueChange = {
                    custom = it.take(7)
                    if (hexPattern.matches(custom)) {
                        val h = normalizeHex(custom)
                        current = h
                        onSelect(h)
                    }
                },
                singleLine = true,
                prefix = { Text("#") },
                placeholder = { Text("RRGGBB") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun normalizeHex(v: String): String = "#" + v.removePrefix("#").uppercase()

@Composable
fun IslandPaddingRows(
    auto: () -> Boolean,
    setAuto: (Boolean) -> Unit,
    length: () -> Int,
    setLength: (Int) -> Unit,
    bump: () -> Unit,
) {
    var isAuto by remember { mutableStateOf(auto()) }
    var len by remember { mutableIntStateOf(length()) }
    ToggleRow("Auto Island Padding", null, auto, setAuto, { isAuto = auto(); bump() })
    if (!isAuto) {
        RowDivider()
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Island length", fontSize = 17.sp)
                Text("$len", color = MaterialTheme.colorScheme.primary)
            }
            GlanceSlider(
                value = len.toFloat(),
                onValueChange = {
                    val n = it.toInt()
                    if (n != len) {
                        len = n
                        setLength(n)
                        bump()
                    }
                },
                valueRange = 4f..40f,
                steps = 35
            )
        }
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(12.dp))
            content()
        }
    }
}
