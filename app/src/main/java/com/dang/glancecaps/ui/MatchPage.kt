package com.dang.glancecaps.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import kotlinx.coroutines.delay
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dang.glancecaps.R
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.island.GlanceCapsNotifications
import com.dang.glancecaps.match.MatchApi
import com.dang.glancecaps.match.MatchInfo
import com.dang.glancecaps.match.MatchSources
import com.dang.glancecaps.match.MatchTeam
import com.dang.glancecaps.match.MatchTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private fun kickoffText(m: MatchInfo): String =
    SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(m.kickoff))

private fun statusText(m: MatchInfo): String = when (m.state) {
    "in" -> m.detail.ifBlank { "Live" }
    "post" -> "Full time  -  " + kickoffText(m)
    else -> kickoffText(m)
}

private fun dayLabel(offset: Int): String = when (offset) {
    0 -> "Today"
    1 -> "Tomorrow"
    -1 -> "Yesterday"
    else -> LocalDate.now().plusDays(offset.toLong()).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
}

@Composable
fun MatchPage(prefs: ModulePrefs, onBack: () -> Unit, onChanged: () -> Unit) {
    val view = LocalView.current
    val wallpaper = rememberWallpaper()
    var dayOffset by remember { mutableIntStateOf(0) }
    var source by remember { mutableStateOf(prefs.matchSource) }
    var apiKey by remember { mutableStateOf(prefs.matchApiKey) }
    var browse by remember { mutableStateOf(prefs.matchBrowse) }
    var selected by remember { mutableStateOf(prefs.matchId) }
    var picked by remember { mutableStateOf(MatchTracker.current?.takeIf { it.id == prefs.matchId }) }
    var matches by remember { mutableStateOf<List<MatchInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var rev by remember { mutableIntStateOf(0) }
    val bump: () -> Unit = { rev++; onChanged() }

    LaunchedEffect(dayOffset, reload, source, apiKey) {
        loading = true
        if (MatchSources.needsKey(source) && apiKey.isBlank()) {
            matches = emptyList()
            failed = false
            loading = false
            return@LaunchedEffect
        }
        delay(if (MatchSources.needsKey(source)) 700L else 0L)
        val day = LocalDate.now().plusDays(dayOffset.toLong())
        val result = withContext(Dispatchers.IO) { MatchApi.fetchLocalDay(day, source, apiKey) }
        failed = result == null
        matches = result ?: emptyList()
        loading = false
    }

    val competitions = remember(matches) { MatchApi.competitionsOf(matches) }
    val activeLeague = if (browse.isNotBlank() && competitions.any { it.key == browse }) browse else ""
    val shown = remember(matches, activeLeague) {
        if (activeLeague.isBlank()) matches else matches.filter { it.leagueKey == activeLeague }
    }
    val dayOptions = remember { (-1..6).map { it.toString() to dayLabel(it) } }
    val leagueOptions = remember(competitions) {
        listOf("" to "All leagues") + competitions.map { it.key to it.label }
    }
    val sample = remember {
        MatchInfo(
            id = "", leagueKey = "", leagueName = "Premier League", country = "England",
            kickoff = System.currentTimeMillis(), state = "in", detail = "67'", minute = 67,
            home = MatchTeam("Home", "HOM", "", 2), away = MatchTeam("Away", "AWY", "", 1)
        )
    }
    val preview = matches.firstOrNull { it.id == selected } ?: picked ?: sample

    ScreenFrame(title = "Matchday", spacing = 14.dp, onBack = onBack) {
        PreviewStage(wallpaper) { MatchPreview(preview, prefs, rev) }

        GroupLabel("Tracking")
        Group {
            ToggleRow(
                "Show tracked match", prefs.matchLabel.ifBlank { null },
                { prefs.matchEnabled }, { prefs.matchEnabled = it }, bump
            )
        }

        GroupLabel("Island")
        Group {
            IslandPaddingRows({ prefs.matchPadAuto }, { prefs.matchPadAuto = it }, { prefs.matchPadLen }, { prefs.matchPadLen = it }, bump)
        }

        GroupLabel("Find a match")
        Group {
            ChoiceRow(
                title = "Source",
                subtitle = null,
                options = MatchSources.all,
                selected = source
            ) { key ->
                source = key
                prefs.matchSource = key
                browse = ""
                prefs.matchBrowse = ""
            }
            if (MatchSources.needsKey(source)) {
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            prefs.matchApiKey = it.trim()
                            MatchTracker.reset()
                        },
                        singleLine = true,
                        placeholder = { Text("API key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            RowDivider()
            ChoiceRow(
                title = "Day",
                subtitle = null,
                options = dayOptions,
                selected = dayOffset.toString()
            ) { key -> dayOffset = key.toInt() }
            RowDivider()
            ChoiceRow(
                title = "League",
                subtitle = null,
                options = leagueOptions,
                selected = activeLeague
            ) { key ->
                browse = key
                prefs.matchBrowse = key
            }
        }

        GroupLabel("Matches")
        Group {
            when {
                loading -> RowItem(icon = null, title = "Loading", subtitle = null)
                failed -> RowItem(
                    icon = null,
                    title = "Could not load matches",
                    subtitle = "Tap to retry or change the source",
                    onClick = { Haptics.tick(view); reload++ }
                )
                shown.isEmpty() -> RowItem(icon = null, title = "No matches", subtitle = null)
                else -> shown.forEachIndexed { i, m ->
                    if (i > 0) RowDivider()
                    val on = m.id == selected
                    RowItem(
                        icon = null,
                        title = m.home.name + "  " + m.scoreText + "  " + m.away.name,
                        subtitle = if (activeLeague.isBlank()) statusText(m) + "  -  " + m.competition else statusText(m),
                        onClick = {
                            Haptics.tick(view)
                            selected = m.id
                            picked = m
                            prefs.matchId = m.id
                            prefs.matchLabel = m.home.short + " vs " + m.away.short
                            prefs.matchLeagueName = m.leagueName
                            prefs.matchKickoff = m.kickoff.toString()
                            prefs.matchEnabled = true
                            MatchTracker.reset()
                            bump()
                        }
                    ) {
                        if (on) {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        } else if (m.live) {
                            Text("LIVE", color = Color(0xFFFF3B30), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        OtherModules(5)
    }
}
