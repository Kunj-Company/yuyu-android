package com.twilighty.yuyu.feature.organiser.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.designsystem.accent.EventAccentScope
import com.twilighty.yuyu.core.designsystem.component.ErrorState
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.SectionHeader
import com.twilighty.yuyu.core.designsystem.component.StatTile
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuIconButton
import com.twilighty.yuyu.core.designsystem.component.YuyuSnackbarHost
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.motion.YuyuStateTransition
import com.twilighty.yuyu.core.designsystem.motion.appearOnEnter
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.designsystem.util.openInBrowser
import com.twilighty.yuyu.core.network.model.EventCountsDto
import com.twilighty.yuyu.core.network.model.OrgEventDetailResponseDto
import com.twilighty.yuyu.feature.organiser.common.AnimatedStatusChip
import com.twilighty.yuyu.feature.organiser.common.EventDateBlock
import com.twilighty.yuyu.feature.organiser.common.RevealVisibility
import com.twilighty.yuyu.feature.organiser.common.EventStatusChip
import com.twilighty.yuyu.feature.organiser.common.copyText
import com.twilighty.yuyu.feature.organiser.common.eventDateLine
import com.twilighty.yuyu.feature.organiser.common.shareLink
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventHomeScreen(
    orgSlug: String,
    eventId: String,
    onBack: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenGuests: () -> Unit,
    onOpenEmails: () -> Unit,
    onOpenDayOf: () -> Unit,
    onOpenSchedule: () -> Unit,
    viewModel: EventHomeViewModel = hiltViewModel(),
) {
    LaunchedEffect(orgSlug, eventId) { viewModel.start(orgSlug, eventId) }
    var resumedOnce by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumedOnce) viewModel.refresh() else resumedOnce = true
        onPauseOrDispose { }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    Scaffold(
        topBar = { YuyuTopBar(state.detail?.event?.title ?: "Event", onBack = onBack) },
        snackbarHost = { YuyuSnackbarHost(snackbar) },
        containerColor = YuyuTheme.colors.bgCanvas,
    ) { padding ->
        YuyuStateTransition(
            targetState = state,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentKey = { it.phase() },
            label = "eventHome",
        ) { shown ->
            val detail = shown.detail
            Column(Modifier.fillMaxSize()) {
                when {
                    shown.loading -> LoadingState(label = "Loading event")
                    detail == null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ErrorState(shown.error ?: "We couldn't load this event.", onRetry = viewModel::retry, modifier = Modifier.weight(1f))
                        // Offline at the venue: the programme saved earlier still opens.
                        if (shown.hasSavedSchedule) {
                            YuyuButton(
                                "Open saved schedule",
                                onOpenSchedule,
                                modifier = Modifier.padding(16.dp),
                                variant = YuyuButtonVariant.Secondary,
                                leadingIcon = Icons.Outlined.CalendarMonth,
                                fullWidth = true,
                            )
                        }
                    }
                    else -> PullToRefreshBox(isRefreshing = shown.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                        EventHomeContent(
                            detail = detail,
                            actions = shown.actions,
                            snackbar = snackbar,
                            onOpenScanner = onOpenScanner,
                            onOpenGuests = onOpenGuests,
                            onOpenEmails = onOpenEmails,
                            onOpenDayOf = onOpenDayOf,
                            onOpenSchedule = onOpenSchedule,
                            onOpenWeb = { viewModel.dashboardUrl()?.let { openInBrowser(context, it) } },
                        )
                    }
                }
            }
        }
    }
}

private enum class EventPhase { Loading, Error, Content }

private fun EventHomeUiState.phase(): EventPhase = when {
    loading -> EventPhase.Loading
    detail == null -> EventPhase.Error
    else -> EventPhase.Content
}

@Composable
private fun EventHomeContent(
    detail: OrgEventDetailResponseDto,
    actions: EventActions,
    snackbar: SnackbarHostState,
    onOpenScanner: () -> Unit,
    onOpenGuests: () -> Unit,
    onOpenEmails: () -> Unit,
    onOpenDayOf: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenWeb: () -> Unit,
) {
    val event = detail.event
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 840.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .widthIn(max = 1000.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.appearOnEnter(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                EventDateBlock(event.startDateTime, event.timezone, event.accentColor, draft = event.status == "DRAFT", modifier = Modifier)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(event.title, style = t.h2, color = c.text, modifier = Modifier.semantics { heading() })
                    Text(eventDateLine(event.startDateTime, event.endDateTime, event.timezone), style = t.bodyUi, color = c.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EventStatusChip(event.status)
                        RevealVisibility(!event.registrationOpen) { StatusChip("Sign-ups closed", StatusTone.Neutral) }
                    }
                }
            }

            val links = listOfNotNull(event.eventUrl?.let { "Event link" to it }, event.shortUrl?.let { "Short link" to it })
            if (links.isNotEmpty()) {
                YuyuCard(
                    modifier = Modifier.appearOnEnter(delayMillis = 30),
                    contentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    links.forEach { (label, url) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(label, style = t.caption, color = c.textMuted)
                                Text(url, style = t.bodyUi, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            YuyuIconButton(Icons.Outlined.ContentCopy, "Copy ${label.lowercase()}", onClick = {
                                scope.launch {
                                    clipboard.copyText(label, url)
                                    snackbar.showSnackbar("${label} copied")
                                }
                            })
                            YuyuIconButton(Icons.Outlined.Share, "Share ${label.lowercase()}", onClick = { shareLink(context, event.title, url) })
                        }
                    }
                }
            }

            val counts = detail.counts
            if (counts != null) {
                Column(Modifier.appearOnEnter(delayMillis = 60), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("At a glance")
                    StatGrid(counts, event.accentColor, columns)
                }
            }

            val tiles = buildList {
                if (actions.checkIn) add(ActionTileSpec("Check in guests", "Scan tickets at the door", Icons.Outlined.QrCodeScanner, null, onOpenScanner))
                if (actions.guests) {
                    val pending = counts?.pending ?: 0
                    add(ActionTileSpec("Guests", "Search, approve and manage", Icons.Outlined.Groups, pending.takeIf { it > 0 }, onOpenGuests))
                }
                if (actions.emails) add(ActionTileSpec("Email guests", "Send an update to your guests", Icons.Outlined.MailOutline, null, onOpenEmails))
                if (actions.schedule) add(ActionTileSpec("Schedule", "Sessions, speakers and time left, offline too", Icons.Outlined.CalendarMonth, null, onOpenSchedule))
                if (actions.dayOf) add(ActionTileSpec("Day-of controls", "Running late, feedback, sign-ups", Icons.Outlined.Tune, null, onOpenDayOf))
            }
            if (tiles.isNotEmpty()) {
                Column(Modifier.appearOnEnter(delayMillis = 90), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Actions")
                    tiles.chunked(if (columns >= 4) 4 else 2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { ActionTile(it, Modifier.weight(1f)) }
                            repeat((if (columns >= 4) 4 else 2) - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            YuyuButton(
                "Open full dashboard on web",
                onOpenWeb,
                modifier = Modifier.appearOnEnter(delayMillis = 100),
                variant = YuyuButtonVariant.Quiet,
                trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                fullWidth = true,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatGrid(counts: EventCountsDto, accentColor: String?, columns: Int) {
    val capacity = counts.capacity
    data class Stat(val label: String, val value: String, val progress: Float? = null, val hint: String? = null, val attention: Boolean = false)
    val stats = listOf(
        // Fill compares capacityUsed (teams + solo places on team events) with capacity; "Going" stays people.
        Stat(
            "Going",
            counts.confirmed.toString(),
            progress = capacity?.takeIf { it > 0 }?.let { counts.placesTaken.toFloat() / it },
            hint = if (counts.placesTaken != counts.confirmed) "${counts.placesTaken} places taken" else null,
        ),
        Stat("Waitlist", counts.waitlisted.toString()),
        Stat("Pending approval", counts.pending.toString(), attention = counts.pending > 0, hint = if (counts.pending > 0) "Needs approval" else null),
        Stat(
            "Checked in",
            counts.checkedIn.toString(),
            progress = counts.confirmed.takeIf { it > 0 }?.let { counts.checkedIn.toFloat() / it },
            hint = "of ${counts.confirmed} going",
        ),
        Stat(
            "Capacity",
            capacity?.toString() ?: "No limit",
            hint = capacity?.let { "${(it - counts.placesTaken).coerceAtLeast(0)} places left" },
        ),
    )
    EventAccentScope(accentColor) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            stats.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { s ->
                        StatTile(s.label, s.value, Modifier.weight(1f), progress = s.progress, hint = s.hint, needsAttention = s.attention)
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private data class ActionTileSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badge: Int?,
    val onClick: () -> Unit,
)

@Composable
private fun ActionTile(spec: ActionTileSpec, modifier: Modifier = Modifier) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    YuyuCard(modifier = modifier.fillMaxWidth(), onClick = spec.onClick, onClickLabel = spec.title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(spec.icon, contentDescription = null, tint = c.text, modifier = Modifier.size(28.dp))
            Spacer(Modifier.weight(1f))
            // The pending count pops in when approvals arrive and cross-fades as it changes.
            RevealVisibility(spec.badge != null) {
                // Keep the last count while the chip fades out.
                val lastBadge = remember { intArrayOf(0) }
                spec.badge?.let { lastBadge[0] = it }
                AnimatedStatusChip("${lastBadge[0]} pending", StatusTone.Warning, showIcon = false)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(spec.title, style = t.h4, color = c.text)
        Text(spec.subtitle, style = t.caption, color = c.textMuted)
    }
}
