package com.dang.glancecaps.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.island.DevTests

@Composable
fun DevScreen(prefs: ModulePrefs, onDisable: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val cs = MaterialTheme.colorScheme
    ScreenFrame(title = "Developer options", spacing = 14.dp, onBack = onBack) {
        DevTests.modules.forEach { module ->
            GroupLabel(module.name)
            Group {
                module.cases.forEachIndexed { i, case ->
                    if (i > 0) RowDivider()
                    RowItem(
                        icon = null,
                        title = case.label,
                        subtitle = null,
                        onClick = {
                            Haptics.tick(view)
                            try {
                                case.run(ctx, prefs)
                            } catch (e: Throwable) {
                                com.dang.glancecaps.CrashLog.append(ctx, "dev.test", e)
                            }
                        }
                    ) {
                        Text("Show", color = cs.primary)
                    }
                }
            }
        }
        Group {
            RowItem(
                icon = null,
                title = "Clear test islands",
                subtitle = null,
                onClick = { Haptics.tick(view); DevTests.clear(ctx) }
            )
            RowDivider()
            RowItem(
                icon = null,
                title = "Disable developer options",
                subtitle = null,
                onClick = {
                    Haptics.tick(view)
                    DevTests.clear(ctx)
                    prefs.devMode = false
                    onDisable()
                }
            )
        }
    }
}
