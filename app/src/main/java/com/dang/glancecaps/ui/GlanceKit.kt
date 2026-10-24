package com.dang.glancecaps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dang.glancecaps.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt

@Composable
fun menuShape(): Shape = MenuDefaults.shape

@Composable
fun DropdownTrigger(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) { Text(label + "  \u25BE") }
}

@Composable
fun ScreenFrame(
    title: String,
    subtitle: String? = null,
    spacing: Dp = 14.dp,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val view = LocalView.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        if (onBack != null) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { Haptics.tick(view); onBack() }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                }
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        } else {
            ScreenTitle(title, subtitle)
        }
        content()
    }
}

object UiDim {
    var count by mutableIntStateOf(0)
}

class MenuOption(val key: String, val label: String, val enabled: Boolean = true, val note: String? = null)

@Composable
fun OptionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<MenuOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = menuShape()) {
        options.forEach { o ->
            DropdownMenuItem(
                enabled = o.enabled,
                text = {
                    Column {
                        Text(o.label)
                        if (!o.enabled && o.note != null) {
                            Text(
                                o.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                onClick = { onSelect(o.key) }
            )
        }
    }
}

@Composable
fun GlanceSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    modifier: Modifier = Modifier,
) {
    Slider(value = value, onValueChange = onValueChange, modifier = modifier, valueRange = valueRange, steps = steps)
}

@Composable
private fun DialogButton(text: String, fill: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(fill)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 15.sp, lineHeight = 20.sp, color = content, textAlign = TextAlign.Center)
    }
}

class DialogSpec(
    val title: String,
    val message: String,
    val cancelText: String,
    val confirmText: String?,
    val onCancel: () -> Unit,
    val onConfirm: () -> Unit,
    val body: (@Composable () -> Unit)? = null,
)

object DialogHost {
    var spec by mutableStateOf<DialogSpec?>(null)
}

@Composable
fun GlanceDialog(
    title: String,
    message: String,
    cancelText: String,
    confirmText: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit = {},
    body: (@Composable () -> Unit)? = null,
) {
    val cancel by rememberUpdatedState(onCancel)
    val confirm by rememberUpdatedState(onConfirm)
    DisposableEffect(title, message, cancelText, confirmText, body != null) {
        val s = DialogSpec(title, message, cancelText, confirmText, { cancel() }, { confirm() }, body)
        DialogHost.spec = s
        onDispose { if (DialogHost.spec === s) DialogHost.spec = null }
    }
}

@Composable
fun DialogLayer() {
    val spec = DialogHost.spec ?: return
    val cs = MaterialTheme.colorScheme
    val gray = if (appDark()) Color(0xFF454545) else Color(0xFFE6E6E6)
    BackHandler(onBack = spec.onCancel)
    var shown by remember(spec) { mutableStateOf(false) }
    LaunchedEffect(spec) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "dialog"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f * progress.coerceIn(0f, 1f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = spec.onCancel),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (1f - progress) * 140.dp.toPx()
                    alpha = progress.coerceIn(0f, 1f)
                }
                .clip(RoundedCornerShape(32.dp))
                .background(cs.surfaceContainerHigh)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (spec.title.isNotEmpty()) {
                Text(spec.title, fontSize = 20.sp, color = cs.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
            }
            if (spec.body != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) { spec.body.invoke() }
            } else {
                Text(
                    spec.message,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(26.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DialogButton(spec.cancelText, gray, cs.onSurface, Modifier.weight(1f), spec.onCancel)
                if (spec.confirmText != null) {
                    DialogButton(spec.confirmText, cs.primary, cs.onPrimary, Modifier.weight(1f), spec.onConfirm)
                }
            }
        }
    }
}
