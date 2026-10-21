package com.twilighty.yuyu.feature.organiser.guests

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.designsystem.component.ErrorState
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.YuyuSnackbarHost
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.motion.YuyuStateTransition
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.GuestDto
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Phone: the guest on their own screen. Changes are reported so the list can reconcile. */
@Composable
fun GuestDetailScreen(
    orgSlug: String,
    eventId: String,
    rsvpId: String,
    onBack: () -> Unit,
    onUpdated: (GuestDto) -> Unit,
    onRemoved: (RemovedGuest) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        topBar = { YuyuTopBar("Guest", onBack = onBack) },
        snackbarHost = { YuyuSnackbarHost(snackbar) },
        containerColor = YuyuTheme.colors.bgCanvas,
    ) { padding ->
        GuestDetailPane(
            orgSlug = orgSlug,
            eventId = eventId,
            rsvpId = rsvpId,
            snackbar = snackbar,
            onUpdated = onUpdated,
            onRemoved = onRemoved,
            modifier = Modifier.padding(padding),
        )
    }
}

/** The guest's details and lifecycle actions; also the right-hand pane on wide screens. */
@Composable
fun GuestDetailPane(
    orgSlug: String,
    eventId: String,
    rsvpId: String,
    snackbar: SnackbarHostState,
    onUpdated: (GuestDto) -> Unit,
    onRemoved: (RemovedGuest) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuestDetailViewModel = hiltViewModel(key = "guest-$rsvpId"),
) {
    LaunchedEffect(orgSlug, eventId, rsvpId) { viewModel.start(orgSlug, eventId, rsvpId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updated by rememberUpdatedState(onUpdated)
    val removed by rememberUpdatedState(onRemoved)

    LaunchedEffect(viewModel) {
        launch {
            viewModel.uiState.map { it.message }.filterNotNull().collect { message ->
                viewModel.messageShown()
                launch { snackbar.showSnackbar(message) }
            }
        }
        launch {
            viewModel.uiState.map { it.updated }.filterNotNull().collect {
                viewModel.updateConsumed()
                updated(it)
            }
        }
        viewModel.uiState.map { it.removed }.filterNotNull().collect {
            viewModel.removedConsumed()
            removed(it)
        }
    }

    val guest = state.guest
    YuyuStateTransition(
        targetState = state,
        modifier = modifier.fillMaxSize(),
        contentKey = { it.phase() },
        label = "guestDetail",
    ) { shown ->
        val shownGuest = shown.guest
        Column(Modifier.fillMaxSize()) {
            when {
                shown.loading && shownGuest == null -> LoadingState(label = "Loading guest")
                shownGuest == null -> ErrorState(shown.error ?: "We couldn't load this guest.", onRetry = viewModel::retry)
                else -> GuestDetailContent(
                    guest = shownGuest,
                    timezone = shown.timezone,
                    actions = shown.actions,
                    showCheckIn = shown.showCheckIn,
                    busy = shown.busy,
                    onCheckIn = viewModel::checkIn,
                    onApprove = viewModel::approve,
                    onDecline = viewModel::decline,
                    onPromote = viewModel::promote,
                    onRemove = { viewModel.askRemove(true) },
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .widthIn(max = 720.dp),
                )
            }
        }
    }

    if (state.confirmRemove && guest != null) {
        RemoveGuestDialog(guest.displayName, onConfirm = viewModel::confirmRemove, onDismiss = { viewModel.askRemove(false) })
    }
}

private enum class DetailPhase { Loading, Error, Content }

private fun GuestDetailUiState.phase(): DetailPhase = when {
    loading && guest == null -> DetailPhase.Loading
    guest == null -> DetailPhase.Error
    else -> DetailPhase.Content
}
