package com.twilighty.yuyu.feature.organiser.guests

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.designsystem.component.EmptyState
import com.twilighty.yuyu.core.designsystem.component.ErrorState
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.YuyuFilterChip
import com.twilighty.yuyu.core.designsystem.component.YuyuIconButton
import com.twilighty.yuyu.core.designsystem.component.YuyuSearchField
import com.twilighty.yuyu.core.designsystem.component.YuyuSnackbarHost
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.motion.YuyuStateTransition
import com.twilighty.yuyu.core.designsystem.motion.rememberEntranceState
import com.twilighty.yuyu.core.designsystem.motion.staggeredEntrance
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.organiser.common.RevealVisibility
import com.twilighty.yuyu.feature.organiser.common.listItemMotion
import com.twilighty.yuyu.core.network.model.GuestDto
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Width from which the list and the selected guest show side by side. */
private val TwoPaneMinWidth = 840.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestsScreen(
    orgSlug: String,
    eventId: String,
    onBack: () -> Unit,
    onOpenGuest: (rsvpId: String) -> Unit,
    viewModel: GuestsViewModel = hiltViewModel(),
) {
    LaunchedEffect(orgSlug, eventId) { viewModel.start(orgSlug, eventId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        launch {
            viewModel.uiState.map { it.message }.filterNotNull().collect { message ->
                viewModel.messageShown()
                launch { snackbar.showSnackbar(message) }
            }
        }
        viewModel.uiState.map { it.undoOffer }.filterNotNull().distinctUntilChanged().collect { offer ->
            viewModel.undoOfferShown()
            launch {
                val result = snackbar.showSnackbar(
                    message = "Removed ${offer.guestName}",
                    actionLabel = "Undo",
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove(offer)
            }
        }
    }

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            YuyuTopBar(
                title = "Guests",
                onBack = onBack,
                actions = {
                    if (state.canManage) {
                        YuyuIconButton(Icons.Outlined.PersonAddAlt, "Add guest", onClick = { viewModel.openAddGuest(true) })
                    }
                },
            )
        },
        snackbarHost = { YuyuSnackbarHost(snackbar) },
        containerColor = YuyuTheme.colors.bgCanvas,
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val twoPane = maxWidth >= TwoPaneMinWidth
            val listWidth = maxWidth * 0.42f
            if (twoPane) {
                Row(Modifier.fillMaxSize()) {
                    GuestsListPane(
                        state = state,
                        viewModel = viewModel,
                        selectedId = selectedId,
                        onOpenGuest = { selectedId = it },
                        modifier = Modifier.width(listWidth).fillMaxHeight(),
                    )
                    VerticalDivider(color = YuyuTheme.colors.border)
                    // Picking another guest cross-fades the detail pane.
                    YuyuStateTransition(
                        targetState = selectedId,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentKey = { it },
                        label = "guestPane",
                    ) { id ->
                      Box(Modifier.fillMaxSize()) {
                        if (id == null) {
                            EmptyState(
                                title = "Pick a guest",
                                body = "Choose someone from the list to see their registration.",
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            GuestDetailPane(
                                orgSlug = orgSlug,
                                eventId = eventId,
                                rsvpId = id,
                                snackbar = snackbar,
                                onUpdated = viewModel::onGuestUpdated,
                                onRemoved = { removed ->
                                    selectedId = null
                                    viewModel.onGuestRemoved(removed.rsvpId, removed.undoId, removed.guestName)
                                },
                            )
                        }
                      }
                    }
                }
            } else {
                GuestsListPane(state, viewModel, selectedId = null, onOpenGuest = onOpenGuest, modifier = Modifier.fillMaxSize())
            }
        }
    }

    state.confirmRemove?.let { guest ->
        RemoveGuestDialog(guest.displayName, onConfirm = viewModel::confirmRemove, onDismiss = { viewModel.askRemove(null) })
    }
    if (state.addGuest.open) {
        AddGuestSheet(state.addGuest, onSubmit = viewModel::addGuest, onDismiss = { viewModel.openAddGuest(false) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuestsListPane(
    state: GuestsUiState,
    viewModel: GuestsViewModel,
    selectedId: String?,
    onOpenGuest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = YuyuTheme.colors
    Column(modifier) {
        YuyuSearchField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = "Search name or email",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GuestStatusFilter.entries.forEach { filter ->
                YuyuFilterChip(filter.label, selected = state.status == filter, onSelectedChange = { viewModel.setStatus(filter) })
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CheckedInFilter.entries.forEach { filter ->
                YuyuFilterChip(filter.label, selected = state.checkedIn == filter, onSelectedChange = { viewModel.setCheckedIn(filter) })
            }
        }
        RevealVisibility(!state.canManage && state.canCheckIn) {
            Text(
                "You can view guests and check them in. Approving and removing guests is for organisation admins.",
                style = YuyuTheme.typography.caption,
                color = c.textMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider(color = c.border, modifier = Modifier.padding(top = 8.dp))

        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            // Keyed by phase only: filter and search changes inside the list animate row by row instead.
            YuyuStateTransition(state, Modifier.fillMaxSize(), contentKey = { it.listPhase() }, label = "guestList") { shown ->
                when (shown.listPhase()) {
                    ListPhase.Loading -> LoadingState(label = "Loading guests")
                    ListPhase.Error -> ScrollableMessage {
                        ErrorState(shown.error.orEmpty(), onRetry = viewModel::retry)
                    }
                    ListPhase.Empty -> ScrollableMessage {
                        EmptyState(
                            title = if (shown.filtersActive) "No guests match" else "No guests yet",
                            body = if (shown.filtersActive) "Try another filter or search." else "Registrations appear here as people sign up.",
                        )
                    }
                    ListPhase.List -> GuestList(shown, viewModel, selectedId, onOpenGuest)
                }
            }
        }
    }
}

private enum class ListPhase { Loading, Error, Empty, List }

private fun GuestsUiState.listPhase(): ListPhase = when {
    loading && guests.isEmpty() -> ListPhase.Loading
    error != null && guests.isEmpty() -> ListPhase.Error
    guests.isEmpty() -> ListPhase.Empty
    else -> ListPhase.List
}

/** Whether [guest] still belongs under the current status and check-in filters. */
private fun GuestsUiState.matchesFilters(guest: GuestDto): Boolean {
    val statusOk = status == GuestStatusFilter.All || guest.status == status.apiValue
    val checkedInOk = when (checkedIn) {
        CheckedInFilter.Any -> true
        CheckedInFilter.Yes -> guest.checkedInAt != null
        CheckedInFilter.No -> guest.checkedInAt == null
    }
    return statusOk && checkedInOk
}

@Composable
private fun ScrollableMessage(content: @Composable () -> Unit) {
    // Scrollable so pull-to-refresh still works on an empty or failed list.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { content() }
}

@Composable
private fun GuestList(state: GuestsUiState, viewModel: GuestsViewModel, selectedId: String?, onOpenGuest: (String) -> Unit) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(listState, state.hasMore, state.guests.size) {
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { viewModel.loadMore() }
    }
    val entrance = rememberEntranceState()
    // How each row looked when the current filters first showed it. A row changed here
    // (approved, declined, given a spot) that no longer fits the filters slides out right
    // away rather than waiting for the next refresh; it comes back if the change fails.
    val shownAs = remember(state.status, state.checkedIn) { mutableMapOf<String, Pair<String, Boolean>>() }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        itemsIndexed(state.guests, key = { _, guest -> guest.rsvpId }) { index, guest ->
            val now = guest.status to (guest.checkedInAt != null)
            val changed = shownAs.getOrPut(guest.rsvpId) { now } != now
            RevealVisibility(
                visible = !changed || state.matchesFilters(guest),
                modifier = listItemMotion().staggeredEntrance(entrance, guest.rsvpId, index),
            ) {
                Column {
                    GuestRow(
                        guest = guest,
                        timezone = state.timezone,
                        actions = state.actionsFor(guest),
                        busy = guest.rsvpId in state.busyIds,
                        selected = guest.rsvpId == selectedId,
                        onClick = { onOpenGuest(guest.rsvpId) },
                        onApprove = { viewModel.approve(guest) },
                        onDecline = { viewModel.decline(guest) },
                        onPromote = { viewModel.promote(guest) },
                        onRemove = { viewModel.askRemove(guest) },
                    )
                    HorizontalDivider(color = YuyuTheme.colors.border)
                }
            }
        }
        if (state.loadingMore) {
            item(key = "loading-more") {
                Box(listItemMotion().fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = YuyuTheme.colors.text)
                }
            }
        }
    }
}
