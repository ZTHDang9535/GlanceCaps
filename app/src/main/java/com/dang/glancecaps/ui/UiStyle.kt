package com.dang.glancecaps.ui

import androidx.compose.foundation.BorderStroke
import android.os.Build
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dang.glancecaps.R
import com.dang.glancecaps.data.ModulePrefs

object UiState {
    var themeMode by mutableStateOf("system")

    fun load(p: ModulePrefs) {
        themeMode = p.themeMode
    }
}

@Composable
fun glanceSwitchColors(): SwitchColors = SwitchDefaults.colors()

private class TabItem(val icon: Int, val label: String)

@Composable
fun AppTabBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        TabItem(R.drawable.ic_grid_view, "Modules"),
        TabItem(R.drawable.ic_tune, "Settings"),
        TabItem(R.drawable.ic_person, "About"),
    )
    MaterialTabBar(items, selected, onSelect)
}

@Composable
private fun MaterialTabBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar {
        items.forEachIndexed { i, item ->
            NavigationBarItem(
                selected = selected == i,
                onClick = { onSelect(i) },
                icon = { Icon(painterResource(item.icon), contentDescription = null) },
                label = { Text(item.label) }
            )
        }
    }
}

@Composable
fun ChoiceRow(
    title: String,
    subtitle: String?,
    options: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    val view = LocalView.current
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: ""
    val openMenu: () -> Unit = { Haptics.tick(view); open = true }
    val cs = MaterialTheme.colorScheme
    val menuOptions = options.map { MenuOption(it.first, it.second) }
    Box(Modifier.alpha(if (enabled) 1f else 0.45f)) {
        RowItem(icon = null, title = title, subtitle = subtitle, onClick = if (enabled) openMenu else null) {
            Box {
                DropdownTrigger(label, enabled, openMenu)
                OptionMenu(
                    expanded = open,
                    onDismiss = { open = false },
                    options = menuOptions,
                    selected = selected,
                    onSelect = { key ->
                        open = false
                        Haptics.tick(view)
                        onSelect(key)
                    }
                )
            }
        }
    }
}
