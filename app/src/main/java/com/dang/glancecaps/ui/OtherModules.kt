package com.dang.glancecaps.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalOpenModule = compositionLocalOf<(Int) -> Unit> { {} }

private val moduleEntries = listOf(
    1 to "Charging",
    2 to "Temperature",
    3 to "Steps",
    4 to "Connectivity",
    5 to "Matchday",
    6 to "Weather"
)

@Composable
fun OtherModules(current: Int) {
    val open = LocalOpenModule.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    GroupLabel("Need other modules?")
    Group {
        moduleEntries.filter { it.first != current }.forEachIndexed { i, entry ->
            if (i > 0) RowDivider()
            Text(
                entry.second,
                fontSize = 17.sp,
                color = primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { Haptics.tick(view); open(entry.first) }
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            )
        }
    }
}
