package com.twilighty.yuyu.feature.organiser.dayof

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.motion.YuyuStateTransition
import com.twilighty.yuyu.core.designsystem.motion.appearOnEnter
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.organiser.common.AnimatedStatusChip
import com.twilighty.yuyu.feature.organiser.common.RevealVisibility
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
        YuyuStateTransition(
            targetState = state,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentKey = { it.phase() },
            label = "dayOf",
        ) { shown ->
            Column(Modifier.fillMaxSize()) {
                when (shown.phase()) {
                    DayOfPhase.Loading -> LoadingState(label = "Loading day-of controls")
                    DayOfPhase.Error -> ErrorState(shown.error.orEmpty(), onRetry = viewModel::retry)
                    DayOfPhase.Empty -> EmptyState(
                        title = "Nothing to control here",
                        body = "Day-of controls are for organisation admins and people who manage the programme.",
                    )
                    DayOfPhase.Content -> PullToRefreshBox(isRefreshing = shown.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .widthIn(max = 840.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            if (shown.showSchedule) {
                                Box(Modifier.appearOnEnter()) {
                                    RunningLateSection(shown, onSessionClick = viewModel::openDelay, onRetry = viewModel::refresh)
                                }
                            }
                            if (shown.showFeedback) {
                                Box(Modifier.appearOnEnter(delayMillis = 40)) {
                                    FeedbackSection(open = shown.feedbackOpen, saving = shown.feedbackSaving, onToggle = viewModel::onFeedbackToggle)
                                }
                            }
                            if (shown.showSignUps) {
                                Box(Modifier.appearOnEnter(delayMillis = 80)) {
                                    SignUpsSection(
                                        registration = shown.registration,
                                        timezone = shown.timezone,
                                        saving = shown.registrationSaving,
                                        onClose = { viewModel.askCloseSignUps(true) },
                                        onReopen = viewModel::reopenSignUps,
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
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

private enum class DayOfPhase { Loading, Error, Empty, Content }

private fun DayOfUiState.phase(): DayOfPhase = when {
    loading -> DayOfPhase.Loading
    error != null -> DayOfPhase.Error
    !hasAnySection -> DayOfPhase.Empty
    else -> DayOfPhase.Content
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
        // The programme fades in once loaded; delay edits then animate in place.
        YuyuStateTransition(state, contentKey = { it.schedulePhase() }, label = "schedule") { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (shown.schedulePhase()) {
                    SchedulePhase.Loading -> Text("Loading the programme…", style = t.bodyUi, color = c.textMuted)
                    SchedulePhase.Error -> ErrorState(shown.scheduleError.orEmpty(), title = "Couldn't load the programme", onRetry = onRetry)
                    SchedulePhase.Empty -> Text(
                        "This event has no programme sessions yet. Add them on the website.",
                        style = t.bodyUi,
                        color = c.textMuted,
                    )
                    SchedulePhase.Tracks -> shown.tracks.filter { it.sessions.isNotEmpty() }.forEach { track ->
                        TrackCard(track, tz, onSessionClick)
                    }
                }
            }
        }
    }
}

private enum class SchedulePhase { Loading, Error, Empty, Tracks }

private fun DayOfUiState.schedulePhase(): SchedulePhase = when {
    scheduleLoading -> SchedulePhase.Loading
    scheduleError != null -> SchedulePhase.Error
    tracks.all { it.sessions.isEmpty() } -> SchedulePhase.Empty
    else -> SchedulePhase.Tracks
}

@Composable
private fun TrackCard(track: TrackUi, tz: String, onSessionClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(track.name)
        // Live times grow or shrink smoothly when a delay changes.
        YuyuCard(Modifier.animateContentSize(YuyuSpecs.standard()), contentPadding = PaddingValues(0.dp)) {
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
                        // The delay chip pops in when a delay is set and cross-fades as it changes.
                        RevealVisibility(s.delayMinutes > 0) {
                            val last = remember { intArrayOf(0) }
                            if (s.delayMinutes > 0) last[0] = s.delayMinutes
                            AnimatedStatusChip("+${last[0]} min", StatusTone.Warning)
                        }
                    },
                    showChevron = true,
                    onClick = { onSessionClick(s.id) },
                    divider = index < track.sessions.lastIndex,
                )
            }
        }
    }
}

@Composable
internal fun FeedbackSection(open: Boolean, saving: Boolean, onToggle: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Feedback form", "Open the form when you'd like attendees to tell you how it went.")
        YuyuCard(Modifier.animateContentSize(YuyuSpecs.standard()), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
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
        YuyuCard(Modifier.animateContentSize(YuyuSpecs.standard())) {
            val open = registration.registrationOpen
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedStatusChip(if (open) "Open" else "Closed", if (open) StatusTone.Success else StatusTone.Neutral)
                FadeText(if (open) "Sign-ups are open" else "Sign-ups are closed", style = t.bodyUiMedium, color = c.text)
            }
            val closesAt = EventTimeFormatter.parseInstant(registration.registrationClosesAt)
            val detail = when {
                open && closesAt != null ->
                    "Closes " + EventTimeFormatter.dayShort(closesAt, timezone) + ", " +
                        EventTimeFormatter.time(closesAt, timezone) + " " + EventTimeFormatter.zoneLabel(closesAt, timezone)
                !open -> registration.registrationClosedReason
                else -> null
            }
            RevealVisibility(detail != null) {
                Column {
                    Spacer(Modifier.height(6.dp))
                    FadeText(detail.orEmpty(), style = t.bodyUi, color = c.textMuted)
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = c.border)
            Spacer(Modifier.height(12.dp))
            RevealVisibility(open) {
                YuyuButton("Close sign-ups now", onClose, variant = YuyuButtonVariant.Danger, loading = saving, loadingText = "Closing…")
            }
            RevealVisibility(registration.registrationManuallyClosed) {
                YuyuButton("Reopen sign-ups", onReopen, variant = YuyuButtonVariant.Secondary, loading = saving, loadingText = "Reopening…")
            }
            RevealVisibility(!registration.registrationManuallyClosed && !open) {
                Text(
                    "To open sign-ups again, change the sign-up window on the website.",
                    style = t.caption,
                    color = c.textMuted,
                )
            }
        }
    }
}

/** Text that cross-fades when it changes (state words, reasons). */
@Composable
private fun FadeText(text: String, style: TextStyle, color: Color) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            if (reduced) EnterTransition.None togetherWith ExitTransition.None else fadeIn(YuyuSpecs.quick()) togetherWith fadeOut(YuyuSpecs.quick())
        },
        label = "fadeText",
    ) { shown -> Text(shown, style = style, color = color) }
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
        RevealVisibility(editor.currentMinutes > 0 || editor.input != "0") {
            YuyuButton("Clear delay (set to 0)", onClear, variant = YuyuButtonVariant.Quiet, enabled = !editor.saving)
        }
        DialogActions {
            YuyuButton("Cancel", onDismiss, variant = YuyuButtonVariant.Secondary, enabled = !editor.saving)
            YuyuButton("Save", onSave, loading = editor.saving, loadingText = "Saving…")
        }
        Spacer(Modifier.fillMaxWidth().height(4.dp))
    }
}
