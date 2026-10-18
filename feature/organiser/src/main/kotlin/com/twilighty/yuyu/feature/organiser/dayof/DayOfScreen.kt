package com.twilighty.yuyu.feature.organiser.dayof

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.common.time.EventTimeFormatter
import com.twilighty.yuyu.core.designsystem.component.ConfirmDialog
import com.twilighty.yuyu.core.designsystem.component.DialogActions
import com.twilighty.yuyu.core.designsystem.component.EmptyState
import com.twilighty.yuyu.core.designsystem.component.ErrorState
import com.twilighty.yuyu.core.designsystem.component.ListRow
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.SectionHeader
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.designsystem.component.YuyuBottomSheet
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonSize
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuIconButton
import com.twilighty.yuyu.core.designsystem.component.YuyuSnackbarHost
import com.twilighty.yuyu.core.designsystem.component.YuyuSwitchRow
import com.twilighty.yuyu.core.designsystem.component.YuyuTextField
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.RegistrationStateDto
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayOfScreen(
    orgSlug: String,
    eventId: String,
    onBack: () -> Unit,
    viewModel: DayOfViewModel = hiltViewModel(),
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
        topBar = { YuyuTopBar("Day-of controls", onBack = onBack) },
        snackbarHost = { YuyuSnackbarHost(snackbar) },
        containerColor = YuyuTheme.colors.bgCanvas,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState(label = "Loading day-of controls")
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::retry)
                !state.hasAnySection -> EmptyState(
                    title = "Nothing to control here",
                    body = "Day-of controls are for organisation admins and people who manage the programme.",
                )
                else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .widthIn(max = 840.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        if (state.showSchedule) {
                            RunningLateSection(state, onSessionClick = viewModel::openDelay, onRetry = viewModel::refresh)
                        }
                        if (state.showFeedback) {
                            FeedbackSection(open = state.feedbackOpen, saving = state.feedbackSaving, onToggle = viewModel::onFeedbackToggle)
                        }
                        if (state.showSignUps) {
                            SignUpsSection(
                                registration = state.registration,
                                timezone = state.timezone,
                                saving = state.registrationSaving,
                                onClose = { viewModel.askCloseSignUps(true) },
                                onReopen = viewModel::reopenSignUps,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    state.delayEditor?.let { editor ->
        DelaySheet(
            editor = editor,
            onInput = viewModel::setDelayInput,
            onAdd = viewModel::addDelay,
            onStep = viewModel::stepDelay,
            onClear = viewModel::clearDelayInput,
            onSave = viewModel::saveDelay,
            onDismiss = viewModel::closeDelay,
        )
    }
    if (state.confirmCloseSignUps) {
        ConfirmDialog(
            title = "Close sign-ups now?",
            body = "Nobody new can register from now on. People already registered keep their place. You can reopen sign-ups from the app afterwards.",
            confirmLabel = "Close sign-ups",
            onConfirm = viewModel::closeSignUps,
            onDismiss = { viewModel.askCloseSignUps(false) },
            danger = true,
        )
    }
    if (state.confirmCloseFeedback) {
        ConfirmDialog(
            title = "Close the feedback form?",
            body = "Attendees won't be able to send feedback until you open it again. Answers you already have are kept.",
            confirmLabel = "Close form",
            onConfirm = viewModel::confirmCloseFeedback,
            onDismiss = viewModel::dismissCloseFeedback,
            danger = true,
        )
    }
}

@Composable
private fun SectionTitle(title: String, body: String) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = t.h4, color = c.text, modifier = Modifier.semantics { heading() })
        Text(body, style = t.bodyUi, color = c.textMuted)
    }
}

@Composable
internal fun RunningLateSection(state: DayOfUiState, onSessionClick: (String) -> Unit, onRetry: () -> Unit) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    val tz = state.timezone
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(
            "Running late",
            "Set a delay on the session that's running over. Every later session in the same track moves back by that " +
                "many minutes, and delays add up down the track. Planned times and other tracks don't change.",
        )
        when {
            state.scheduleLoading -> Text("Loading the programme…", style = t.bodyUi, color = c.textMuted)
            state.scheduleError != null -> ErrorState(state.scheduleError, title = "Couldn't load the programme", onRetry = onRetry)
            state.tracks.all { it.sessions.isEmpty() } -> Text(
                "This event has no programme sessions yet. Add them on the website.",
                style = t.bodyUi,
                color = c.textMuted,
            )
            else -> state.tracks.filter { it.sessions.isNotEmpty() }.forEach { track ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(track.name)
                    YuyuCard(contentPadding = PaddingValues(0.dp)) {
                        track.sessions.forEachIndexed { index, item ->
                            val s = item.session
                            val start = EventTimeFormatter.parseInstant(s.startDateTime)
                            val end = EventTimeFormatter.parseInstant(s.endDateTime)
                            val planned = start?.let { "Planned " + EventTimeFormatter.timeRange(it, end, tz) }
                            val live = if (item.shiftMinutes > 0 && start != null) {
                                val shift = item.shiftMinutes * 60L
                                "Live " + EventTimeFormatter.timeRange(start.plusSeconds(shift), end?.plusSeconds(shift), tz) +
                                    " (${item.shiftMinutes} min behind)"
                            } else {
                                null
                            }
                            ListRow(
                                title = s.title,
                                supporting = listOfNotNull(planned, live).joinToString("\n"),
                                trailing = {
                                    if (s.delayMinutes > 0) StatusChip("+${s.delayMinutes} min", StatusTone.Warning)
                                },
                                showChevron = true,
                                onClick = { onSessionClick(s.id) },
                                divider = index < track.sessions.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FeedbackSection(open: Boolean, saving: Boolean, onToggle: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Feedback form", "Open the form when you'd like attendees to tell you how it went.")
        YuyuCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            YuyuSwitchRow(
                title = if (open) "Feedback form is open" else "Feedback form is closed",
                description = if (saving) "Saving…" else if (open) "Attendees can send feedback now." else "Attendees can't send feedback yet.",
                checked = open,
                onCheckedChange = onToggle,
                enabled = !saving,
            )
        }
    }
}

@Composable
internal fun SignUpsSection(
    registration: RegistrationStateDto,
    timezone: String,
    saving: Boolean,
    onClose: () -> Unit,
    onReopen: () -> Unit,
) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Sign-ups", "Close sign-ups when the room is full or the event is about to start.")
        YuyuCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (registration.registrationOpen) {
                    StatusChip("Open", StatusTone.Success)
                } else {
                    StatusChip("Closed", StatusTone.Neutral)
                }
                Text(
                    if (registration.registrationOpen) "Sign-ups are open" else "Sign-ups are closed",
                    style = t.bodyUiMedium,
                    color = c.text,
                )
            }
            val closesAt = EventTimeFormatter.parseInstant(registration.registrationClosesAt)
            val detail = when {
                registration.registrationOpen && closesAt != null ->
                    "Closes " + EventTimeFormatter.dayShort(closesAt, timezone) + ", " +
                        EventTimeFormatter.time(closesAt, timezone) + " " + EventTimeFormatter.zoneLabel(closesAt, timezone)
                !registration.registrationOpen -> registration.registrationClosedReason
                else -> null
            }
            if (detail != null) {
                Spacer(Modifier.height(6.dp))
                Text(detail, style = t.bodyUi, color = c.textMuted)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = c.border)
            Spacer(Modifier.height(12.dp))
            if (registration.registrationOpen) {
                YuyuButton("Close sign-ups now", onClose, variant = YuyuButtonVariant.Danger, loading = saving, loadingText = "Closing…")
            }
            if (registration.registrationManuallyClosed) {
                YuyuButton("Reopen sign-ups", onReopen, variant = YuyuButtonVariant.Secondary, loading = saving, loadingText = "Reopening…")
            } else if (!registration.registrationOpen) {
                Text(
                    "To open sign-ups again, change the sign-up window on the website.",
                    style = t.caption,
                    color = c.textMuted,
                )
            }
        }
    }
}

@Composable
private fun DelaySheet(
    editor: DelayEditor,
    onInput: (String) -> Unit,
    onAdd: (Int) -> Unit,
    onStep: (Int) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    YuyuBottomSheet(onDismiss = onDismiss, title = editor.sessionTitle) {
        Text(
            "Later sessions in ${editor.trackName} move back by this many minutes. This session keeps its own time. 0 clears the delay.",
            style = t.bodyUi,
            color = c.textMuted,
        )
        Text("Add time", style = t.label, color = c.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30).forEach { minutes ->
                YuyuButton("+$minutes", { onAdd(minutes) }, variant = YuyuButtonVariant.Secondary, size = YuyuButtonSize.Small, enabled = !editor.saving)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            YuyuIconButton(Icons.Outlined.Remove, "5 minutes less", onClick = { onStep(-5) }, enabled = !editor.saving)
            YuyuTextField(
                value = editor.input,
                onValueChange = onInput,
                label = "Delay (minutes)",
                helper = "0 to 1440",
                error = editor.error,
                enabled = !editor.saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                onImeAction = onSave,
                modifier = Modifier.weight(1f),
            )
            YuyuIconButton(Icons.Outlined.Add, "5 minutes more", onClick = { onStep(5) }, enabled = !editor.saving)
        }
        if (editor.currentMinutes > 0 || editor.input != "0") {
            YuyuButton("Clear delay (set to 0)", onClear, variant = YuyuButtonVariant.Quiet, enabled = !editor.saving)
        }
        DialogActions {
            YuyuButton("Cancel", onDismiss, variant = YuyuButtonVariant.Secondary, enabled = !editor.saving)
            YuyuButton("Save", onSave, loading = editor.saving, loadingText = "Saving…")
        }
        Spacer(Modifier.fillMaxWidth().height(4.dp))
    }
}
