package com.twilighty.yuyu.feature.organiser.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.common.time.EventTimeFormatter
import com.twilighty.yuyu.core.designsystem.component.EmptyState
import com.twilighty.yuyu.core.designsystem.component.ErrorState
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.SectionHeader
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuCardTone
import com.twilighty.yuyu.core.designsystem.component.YuyuIconButton
import com.twilighty.yuyu.core.designsystem.component.YuyuSnackbarHost
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun ScheduleScreen(
    orgSlug: String,
    eventId: String,
    onBack: () -> Unit,
    onOpenDayOf: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    LaunchedEffect(orgSlug, eventId) { viewModel.start(orgSlug, eventId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.uiState.map { it.message }.filterNotNull().collect { message ->
            viewModel.messageShown()
            launch { snackbar.showSnackbar(message) }
        }
    }

    Scaffold(
        topBar = {
            YuyuTopBar("Schedule", onBack = onBack, actions = {
                if (state.syncing) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = YuyuTheme.colors.textSecondary)
                    }
                } else {
                    YuyuIconButton(Icons.Outlined.Sync, "Sync schedule", onClick = viewModel::sync)
                }
            })
        },
        snackbarHost = { YuyuSnackbarHost(snackbar) },
        containerColor = YuyuTheme.colors.bgCanvas,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val saved = state.saved
            when {
                state.loading || (saved == null && state.syncing) -> LoadingState(label = "Loading the schedule")
                saved == null -> ErrorState(
                    state.syncError ?: "Couldn't load the schedule.",
                    title = if (state.offline) "No saved copy yet" else "Couldn't load the schedule",
                    onRetry = viewModel::sync,
                )
                else -> ScheduleContent(state, onOpenDayOf)
            }
        }
    }
}

@Composable
private fun ScheduleContent(state: ScheduleUiState, onOpenDayOf: () -> Unit) {
    val saved = state.saved ?: return
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    val tz = saved.timezone
    val now = rememberNow()
    val summary = state.tracks.nowAndNext(now)
    LazyColumn(
        Modifier.fillMaxSize().widthIn(max = 840.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(saved.eventTitle, style = t.h3, color = c.text, modifier = Modifier.semantics { heading() })
                SyncStatus(state, now)
            }
        }
        if (saved.canEditDelays) {
            item(key = "late") {
                YuyuButton(
                    "Running late? Set a delay",
                    onOpenDayOf,
                    variant = YuyuButtonVariant.Secondary,
                    leadingIcon = Icons.Outlined.Timer,
                    fullWidth = true,
                )
            }
        }
        if (state.isEmpty) {
            item(key = "empty") {
                EmptyState(title = "No sessions yet", body = "Add sessions to the programme on the website, then sync.")
            }
            return@LazyColumn
        }
        if (summary.now.isNotEmpty()) {
            item(key = "now-header") { SectionHeader("Happening now") }
            items(summary.now, key = { "now-" + it.item.session.id }) { NowCard(it, tz, now) }
        }
        if (summary.next.isNotEmpty()) {
            item(key = "next") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader("Up next")
                    YuyuCard(contentPadding = PaddingValues(0.dp)) {
                        summary.next.forEachIndexed { index, next ->
                            Column {
                                Text(
                                    next.trackName,
                                    style = t.caption,
                                    color = c.textMuted,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                                )
                                SessionRow(next.item, tz, now, divider = index < summary.next.lastIndex)
                            }
                        }
                    }
                }
            }
        }
        state.tracks.filter { it.sessions.isNotEmpty() }.forEach { track ->
            item(key = "track-" + (track.id ?: track.name)) { TrackSection(track, tz, now) }
        }
        item(key = "end") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TrackSection(track: TrackUi, tz: String, now: Instant) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    // Multi-day programmes get a day line whenever the day changes.
    val days = track.sessions.map { it.liveStart?.let { start -> EventTimeFormatter.dayShort(start, tz) } }
    val multiDay = days.filterNotNull().distinct().size > 1
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(track.name)
        YuyuCard(contentPadding = PaddingValues(0.dp)) {
            track.sessions.forEachIndexed { index, item ->
                if (multiDay && days[index] != null && (index == 0 || days[index] != days[index - 1])) {
                    Text(
                        days[index].orEmpty(),
                        style = t.overline,
                        color = c.textMuted,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                }
                SessionRow(item, tz, now, divider = index < track.sessions.lastIndex)
            }
        }
    }
}

@Composable
private fun SyncStatus(state: ScheduleUiState, now: Instant) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    val saved = state.saved ?: return
    val synced = EventTimeFormatter.relativeTime(Instant.ofEpochMilli(saved.syncedAt), ZoneId.systemDefault(), now)
    val error = state.syncError
    if (error == null) {
        Text("Saved for offline use · synced ${synced.replaceFirstChar { it.lowercase() }}", style = t.caption, color = c.textMuted)
        return
    }
    YuyuCard(Modifier.fillMaxWidth(), tone = YuyuCardTone.Subtle) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = c.warningFg, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.offline) "You're offline" else "Couldn't sync",
                    style = t.bodyUiMedium,
                    color = c.text,
                )
                Text(
                    "Showing the copy saved ${synced.replaceFirstChar { it.lowercase() }}. Live delays set since then aren't shown.",
                    style = t.caption,
                    color = c.textMuted,
                )
            }
        }
    }
}
