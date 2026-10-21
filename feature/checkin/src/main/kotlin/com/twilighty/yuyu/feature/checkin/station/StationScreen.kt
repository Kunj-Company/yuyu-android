package com.twilighty.yuyu.feature.checkin.station

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.twilighty.yuyu.core.designsystem.brand.DotFaceMood
import com.twilighty.yuyu.core.designsystem.component.ConfirmDialog
import com.twilighty.yuyu.core.designsystem.component.ErrorState
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.MessageState
import com.twilighty.yuyu.core.designsystem.component.YuyuAvatar
import com.twilighty.yuyu.core.designsystem.component.YuyuBottomSheet
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuSwitchRow
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.checkin.flow.SheetState
import com.twilighty.yuyu.feature.checkin.ui.CheckInStateTransition
import com.twilighty.yuyu.feature.checkin.ui.CounterTile
import com.twilighty.yuyu.feature.checkin.ui.FindGuestPanel
import com.twilighty.yuyu.feature.checkin.ui.ImmersiveMode
import com.twilighty.yuyu.feature.checkin.ui.KeepScreenOn
import com.twilighty.yuyu.feature.checkin.ui.RecentCheckIns
import com.twilighty.yuyu.feature.checkin.ui.ResultFeedback
import com.twilighty.yuyu.feature.checkin.ui.ResultSheetContent
import com.twilighty.yuyu.feature.checkin.ui.ScreenPinning

/**
 * The venue kiosk: immersive, screen kept on, optional screen pinning. Leaving
 * (Exit or Back, after a confirm) forgets the station's proof cookie.
 */
@Composable
internal fun StationScreen(
    onExit: () -> Unit,
    onUnlockAgain: () -> Unit,
    viewModel: StationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var confirmExit by remember { mutableStateOf(false) }
    var showPinHelp by remember { mutableStateOf(false) }
    var pinned by remember { mutableStateOf(ScreenPinning.isPinned(context)) }

    KeepScreenOn()
    ImmersiveMode()
    ResultFeedback(viewModel.feedback)
    LifecycleResumeEffect(Unit) {
        pinned = ScreenPinning.isPinned(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(lifecycle, viewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.pollStats() }
    }
    BackHandler { confirmExit = true }

    val exit = {
        confirmExit = false
        ScreenPinning.unpin(context)
        viewModel.exit()
        onExit()
    }

    val phase = when {
        state.accessLost -> StationPhase.AccessLost
        state.loading -> StationPhase.Loading
        state.eventTitle == null -> StationPhase.Failed
        else -> StationPhase.Desk
    }
    CheckInStateTransition(
        phase,
        Modifier.fillMaxSize().safeDrawingPadding(),
        label = "stationPhase",
    ) { shown ->
        Box(Modifier.fillMaxSize()) {
            when (shown) {
                StationPhase.AccessLost -> MessageState(
                    mood = DotFaceMood.Sleepy,
                    title = "Station access has expired",
                    body = "Enter the station PIN again to keep checking guests in.",
                    actionLabel = "Unlock again",
                    onAction = {
                        ScreenPinning.unpin(context)
                        onUnlockAgain()
                    },
                    secondaryLabel = "Exit station",
                    onSecondary = exit,
                    modifier = Modifier.align(Alignment.Center),
                )
                StationPhase.Loading -> LoadingState(label = "Opening the station")
                StationPhase.Failed -> ErrorState(
                    state.loadError ?: "This station couldn't be opened.",
                    onRetry = viewModel::refresh,
                )
                StationPhase.Desk -> StationDesk(
                    state = state,
                    viewModel = viewModel,
                    pinned = pinned,
                    onPinChange = { on ->
                        if (on) {
                            showPinHelp = true
                        } else {
                            ScreenPinning.unpin(context)
                            pinned = false
                        }
                    },
                    onExit = { confirmExit = true },
                )
            }
        }
    }

    if (state.sheet != SheetState.Hidden && !state.accessLost) {
        YuyuBottomSheet(onDismiss = viewModel::dismissSheet) {
            ResultSheetContent(
                state = state.sheet,
                onConfirm = viewModel::confirm,
                onUndo = viewModel::undo,
                onDismiss = viewModel::dismissSheet,
            )
        }
    }
    if (confirmExit) {
        ConfirmDialog(
            title = "Exit the door station?",
            body = "The station is locked on this device. You'll need the PIN to open it again.",
            confirmLabel = "Exit station",
            onConfirm = exit,
            onDismiss = { confirmExit = false },
        )
    }
    if (showPinHelp) {
        ConfirmDialog(
            title = "Lock to this app",
            body = "This pins Yuyu to the screen so guests can't leave it. Android will ask you to confirm.\n\n" +
                "To unpin later: with gesture navigation, swipe up from the bottom and hold. With buttons, " +
                "touch and hold Back and Overview together. Then enter the device's PIN if asked.",
            confirmLabel = "Lock",
            onConfirm = {
                showPinHelp = false
                pinned = ScreenPinning.pin(context)
            },
            onDismiss = { showPinHelp = false },
        )
    }
}

private enum class StationPhase { AccessLost, Loading, Failed, Desk }

@Composable
private fun StationDesk(
    state: StationUiState,
    viewModel: StationViewModel,
    pinned: Boolean,
    onPinChange: (Boolean) -> Unit,
    onExit: () -> Unit,
) {
    val c = YuyuTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            YuyuAvatar(name = state.organisationName, imageUrl = state.organisationLogoUrl, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(state.organisationName.orEmpty(), style = YuyuTheme.typography.caption, color = c.textSecondary)
                Text(state.eventTitle.orEmpty(), style = YuyuTheme.typography.h3, color = c.text)
            }
            YuyuButton("Exit", onExit, variant = YuyuButtonVariant.Secondary)
        }
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
        RecentCheckIns(state.recent)
        YuyuSwitchRow(
            title = "Lock to this app",
            description = "Pin the screen so the device stays on the door station.",
            checked = pinned,
            onCheckedChange = onPinChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
