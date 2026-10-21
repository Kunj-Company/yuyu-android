package com.twilighty.yuyu.feature.checkin.scanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.twilighty.yuyu.core.designsystem.component.ErrorState
import com.twilighty.yuyu.core.designsystem.component.ListRow
import com.twilighty.yuyu.core.designsystem.component.SectionHeader
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.designsystem.component.YuyuBottomSheet
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuCardTone
import com.twilighty.yuyu.core.designsystem.component.YuyuSnackbarHost
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.motion.AnimatedCounterText
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.checkin.domain.CheckInTime
import com.twilighty.yuyu.feature.checkin.domain.SyncOutcome
import com.twilighty.yuyu.feature.checkin.domain.SyncReport
import com.twilighty.yuyu.feature.checkin.flow.SheetState
import com.twilighty.yuyu.feature.checkin.ui.CounterTile
import com.twilighty.yuyu.feature.checkin.ui.FindGuestPanel
import com.twilighty.yuyu.feature.checkin.ui.KeepScreenOn
import com.twilighty.yuyu.feature.checkin.ui.RecentCheckIns
import com.twilighty.yuyu.feature.checkin.ui.ResultFeedback
import com.twilighty.yuyu.feature.checkin.ui.ResultSheetContent

@Composable
internal fun ScannerScreen(onBack: () -> Unit, viewModel: ScannerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = LocalReducedMotion.current

    KeepScreenOn()
    ResultFeedback(viewModel.feedback)
    LaunchedEffect(lifecycle, viewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.pollStats() }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.messageShown()
    }

    Scaffold(
        topBar = { YuyuTopBar(state.eventTitle ?: "Check-in", onBack = onBack) },
        snackbarHost = { YuyuSnackbarHost(snackbar) },
        containerColor = YuyuTheme.colors.bgCanvas,
    ) { padding ->
        val noAccess = state.noAccessMessage
        if (noAccess != null) {
            ErrorState(noAccess, modifier = Modifier.padding(padding), title = "No check-in access")
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedVisibility(
                visible = state.showOfflineBanner,
                enter = bannerEnter(reduced),
                exit = bannerExit(reduced),
            ) { OfflineBanner(state, onSync = viewModel::syncNow) }
            CounterTile(state.counts)
            FindGuestPanel(
                mode = state.mode,
                onModeChange = viewModel::setMode,
                cameraPaused = state.sheet != SheetState.Hidden,
                onScanned = viewModel::onScanned,
                onSubmitCode = viewModel::submitCode,
                lookup = state.lookup,
                onLookupQuery = viewModel::onLookupQuery,
                onPickGuest = viewModel::openGuest,
                fastMode = state.fastMode,
                onFastModeChange = viewModel::setFastMode,
            )
            // Keep the last report while it animates out after "Done".
            var lastReport by remember { mutableStateOf(state.syncReport) }
            state.syncReport?.let { lastReport = it }
            AnimatedVisibility(
                visible = state.syncReport != null,
                enter = bannerEnter(reduced),
                exit = bannerExit(reduced),
            ) { lastReport?.let { SyncReportCard(it, onDismiss = viewModel::dismissSyncReport) } }
            OfflineReadiness(state, onDownload = viewModel::downloadRoster)
            AnimatedVisibility(
                visible = state.pending.isNotEmpty(),
                enter = bannerEnter(reduced),
                exit = bannerExit(reduced),
            ) { PendingList(state) }
            RecentCheckIns(state.recent)
        }
    }

    if (state.sheet != SheetState.Hidden) {
        YuyuBottomSheet(onDismiss = viewModel::dismissSheet) {
            ResultSheetContent(
                state = state.sheet,
                onConfirm = viewModel::confirm,
                onUndo = viewModel::undo,
                onDismiss = viewModel::dismissSheet,
            )
        }
    }
}

private fun bannerEnter(reduced: Boolean): EnterTransition =
    if (reduced) EnterTransition.None else expandVertically(YuyuSpecs.standard()) + fadeIn(YuyuSpecs.standard(delayMillis = 60))

private fun bannerExit(reduced: Boolean): ExitTransition =
    if (reduced) ExitTransition.None else fadeOut(YuyuSpecs.quick()) + shrinkVertically(YuyuSpecs.standard())

@Composable
private fun OfflineBanner(state: ScannerUiState, onSync: () -> Unit) {
    val c = YuyuTheme.colors
    YuyuCard(tone = YuyuCardTone.Tint, modifier = Modifier.animateContentSize(YuyuSpecs.standard())) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Crossfade(state.online, animationSpec = YuyuSpecs.quick(), label = "bannerIcon") { online ->
                Icon(if (online) Icons.Outlined.CloudSync else Icons.Outlined.CloudOff, contentDescription = null, tint = c.warningFg)
            }
            Column(Modifier.weight(1f)) {
                val waiting = when (state.pendingCount) {
                    0 -> "no check-ins waiting to sync"
                    1 -> "1 check-in waiting to sync"
                    else -> "${state.pendingCount} check-ins waiting to sync"
                }
                AnimatedCounterText(
                    if (state.online) waiting.replaceFirstChar { it.uppercase() } else "Offline \u2014 $waiting",
                    style = YuyuTheme.typography.label,
                    color = c.text,
                )
                Text(
                    if (state.hasRoster) {
                        "Guest list downloaded ${CheckInTime.dayTime(state.rosterGeneratedAt) ?: ""} (${state.rosterCount} guests)."
                    } else {
                        "No guest list on this phone: tickets can't be checked until you're back online."
                    },
                    style = YuyuTheme.typography.caption,
                    color = c.textSecondary,
                )
            }
        }
        if (state.online && state.pendingCount > 0) {
            YuyuButton(
                "Sync now",
                onSync,
                variant = YuyuButtonVariant.Secondary,
                loading = state.syncing,
                loadingText = "Syncing…",
                fullWidth = true,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun OfflineReadiness(state: ScannerUiState, onDownload: () -> Unit) {
    val c = YuyuTheme.colors
    YuyuCard(tone = YuyuCardTone.Subtle) {
        Text("Offline check-in", style = YuyuTheme.typography.h4, color = c.text)
        Text(
            if (state.hasRoster) {
                "Guest list saved ${CheckInTime.dayTime(state.rosterGeneratedAt) ?: ""} · ${state.rosterCount} guests. " +
                    "If the connection drops, tickets are checked against it and synced later."
            } else {
                "Download the guest list before doors open so check-in keeps working without a connection. Ticket codes are stored only as fingerprints."
            },
            style = YuyuTheme.typography.bodyUi,
            color = c.textSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        YuyuButton(
            if (state.hasRoster) "Download again" else "Download for offline",
            onDownload,
            variant = YuyuButtonVariant.Secondary,
            leadingIcon = Icons.Outlined.Download,
            loading = state.downloading,
            loadingText = "Downloading…",
            fullWidth = true,
        )
    }
}

@Composable
private fun PendingList(state: ScannerUiState) {
    Column(Modifier.animateContentSize(YuyuSpecs.standard())) {
        SectionHeader("Waiting to sync")
        state.pending.forEach { item ->
            ListRow(
                title = item.displayName,
                supporting = item.lastError?.let { "Not synced: $it" } ?: "Checked in ${CheckInTime.time(item.checkedInAt) ?: ""} offline",
                leadingIcon = if (item.lastError != null) Icons.Outlined.ErrorOutline else Icons.Outlined.Schedule,
                divider = true,
            )
        }
    }
}

@Composable
private fun SyncReportCard(report: SyncReport, onDismiss: () -> Unit) {
    val c = YuyuTheme.colors
    YuyuCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Offline check-ins synced", style = YuyuTheme.typography.h4, color = c.text, modifier = Modifier.weight(1f))
            YuyuButton("Done", onDismiss, variant = YuyuButtonVariant.Quiet)
        }
        Text(report.summary, style = YuyuTheme.typography.bodyUi, color = c.textSecondary)
        report.items.forEach { item ->
            val (label, tone) = when (val outcome = item.outcome) {
                SyncOutcome.Synced -> "Synced" to StatusTone.Success
                SyncOutcome.AlreadyCheckedIn -> "Already checked in" to StatusTone.Warning
                is SyncOutcome.Failed -> "Failed" to StatusTone.Danger
                SyncOutcome.StillQueued -> "Waiting" to StatusTone.Neutral
            }
            ListRow(
                title = item.displayName,
                supporting = (item.outcome as? SyncOutcome.Failed)?.reason,
                leading = {
                    Icon(
                        if (item.outcome is SyncOutcome.Failed) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = c.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailing = { StatusChip(label, tone) },
                divider = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
