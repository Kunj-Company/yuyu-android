package com.twilighty.yuyu.feature.attendee.register

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.designsystem.accent.EventAccentScope
import com.twilighty.yuyu.core.designsystem.brand.DotFace
import com.twilighty.yuyu.core.designsystem.brand.DotFaceMood
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.MessageState
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonSize
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuTextField
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.motion.YuyuStateTransition
import com.twilighty.yuyu.core.designsystem.motion.appearOnEnter
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuSpacing
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.RsvpStatus
import com.twilighty.yuyu.feature.attendee.common.AnimatedRevealValue
import com.twilighty.yuyu.feature.attendee.common.eventWhen
import com.twilighty.yuyu.feature.attendee.common.successPop
import com.twilighty.yuyu.feature.attendee.event.RegisterCta
import com.twilighty.yuyu.feature.attendee.form.DynamicForm

@Composable
internal fun RegisterRoute(
    onBack: () -> Unit,
    onOpenTicket: (String) -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RegisterScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onAnswersChange = viewModel::onAnswersChange,
        onSubmit = viewModel::submit,
        onOpenTicket = onOpenTicket,
    )
}

@Composable
internal fun RegisterScreen(
    state: RegisterUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onAnswersChange: (com.twilighty.yuyu.feature.attendee.form.AnswerState) -> Unit,
    onSubmit: () -> Unit,
    onOpenTicket: (String) -> Unit,
) {
    val showForm = state.event != null && state.outcome == null && state.blocked == null
    EventAccentScope(state.event?.accentColor) {
        Scaffold(
            topBar = { YuyuTopBar(if (state.outcome != null) "" else "Register", onBack = onBack, backLabel = if (state.outcome != null) "Close" else "Back") },
            bottomBar = {
                val reduced = LocalReducedMotion.current
                AnimatedVisibility(
                    visible = showForm,
                    enter = if (reduced) EnterTransition.None else slideInVertically(YuyuSpecs.standard(delayMillis = 120)) { it } + fadeIn(YuyuSpecs.standard(delayMillis = 120)),
                    exit = if (reduced) ExitTransition.None else slideOutVertically(YuyuSpecs.standard()) { it } + fadeOut(YuyuSpecs.quick()),
                    label = "submitBar",
                ) { SubmitBar(state, onSubmit) }
            },
            containerColor = YuyuTheme.colors.bgCanvas,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                YuyuStateTransition(
                    targetState = state,
                    modifier = Modifier.fillMaxSize(),
                    contentKey = { it.phase },
                    label = "register",
                ) { shown ->
                    val outcome = shown.outcome
                    val blocked = shown.blocked
                    when (shown.phase) {
                        RegisterPhase.Loading -> LoadingState(label = "Loading the form")
                        RegisterPhase.LoadError -> MessageState(
                            mood = if (shown.offline) DotFaceMood.Sleepy else DotFaceMood.Sad,
                            title = if (shown.offline) "You're offline" else "Couldn't load the form",
                            body = shown.loadError,
                            actionLabel = "Try again",
                            onAction = onRetry,
                        )
                        RegisterPhase.Outcome -> if (outcome != null) OutcomeView(outcome, shown, onOpenTicket, onBack)
                        RegisterPhase.Blocked -> if (blocked != null) BlockedView(blocked, onOpenTicket, onBack)
                        RegisterPhase.Form -> RegisterForm(shown, onNameChange, onEmailChange, onAnswersChange)
                    }
                }
            }
        }
    }
}

private enum class RegisterPhase { Loading, LoadError, Outcome, Blocked, Form }

private val RegisterUiState.phase: RegisterPhase
    get() = when {
        loading -> RegisterPhase.Loading
        event == null -> RegisterPhase.LoadError
        outcome != null -> RegisterPhase.Outcome
        blocked != null -> RegisterPhase.Blocked
        else -> RegisterPhase.Form
    }

@Composable
private fun RegisterForm(
    state: RegisterUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onAnswersChange: (com.twilighty.yuyu.feature.attendee.form.AnswerState) -> Unit,
) {
    val c = YuyuTheme.colors
    val event = state.event ?: return
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = YuyuSpacing.gutter, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(YuyuSpacing.sectionGap),
    ) {
        Column(Modifier.appearOnEnter(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(event.title, style = YuyuTheme.typography.h2, color = YuyuTheme.accent.accentText, modifier = Modifier.semantics { heading() })
            Text(eventWhen(event.startDateTime, event.timezone), style = YuyuTheme.typography.bodyUi, color = c.textSecondary)
        }
        val user = state.user
        if (user != null && !state.needsName) {
            Column(
                Modifier.fillMaxWidth().appearOnEnter(delayMillis = 60).background(c.bgSubtle, YuyuRadius.large).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Registering as ${user.name}", style = YuyuTheme.typography.label, color = c.text)
                user.email?.let { Text(it, style = YuyuTheme.typography.caption, color = c.textMuted) }
            }
        }
        if (state.needsName) {
            YuyuTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = "Your name",
                error = state.nameError,
                useAccentFocus = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.appearOnEnter(delayMillis = 60).testTag("identity-name"),
            )
        }
        if (state.isGuest) {
            YuyuTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = "Email",
                helper = "We'll send your ticket here.",
                error = state.emailError,
                useAccentFocus = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.appearOnEnter(delayMillis = 90).testTag("identity-email"),
            )
        }
        if (state.fields.isNotEmpty()) {
            DynamicForm(
                modifier = Modifier.appearOnEnter(delayMillis = 120),
                fields = state.fields,
                state = state.answers,
                errors = state.errors,
                onStateChange = onAnswersChange,
                enabled = !state.submitting,
            )
        }
    }
}

@Composable
private fun SubmitBar(state: RegisterUiState, onSubmit: () -> Unit) {
    val c = YuyuTheme.colors
    Column(Modifier.fillMaxWidth().background(c.bgCanvas).navigationBarsPadding().imePadding()) {
        HorizontalDivider(color = c.border)
        Column(Modifier.padding(horizontal = YuyuSpacing.gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val problem = state.formError ?: when (state.problemCount) {
                0 -> null
                1 -> "One answer needs a quick fix before you can register."
                else -> "${state.problemCount} answers need a quick fix before you can register."
            }
            AnimatedRevealValue(problem) { shownProblem ->
                Row(
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("form-problem"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = c.dangerFg)
                    Text(shownProblem, style = YuyuTheme.typography.caption, color = c.dangerFg)
                }
            }
            YuyuButton(
                state.submitLabel,
                onSubmit,
                variant = YuyuButtonVariant.Accent,
                size = YuyuButtonSize.Large,
                loading = state.submitting,
                loadingText = "Registering…",
                fullWidth = true,
                modifier = Modifier.testTag("submit-registration"),
            )
        }
    }
}

@Composable
private fun OutcomeView(outcome: RegisterOutcome, state: RegisterUiState, onOpenTicket: (String) -> Unit, onDone: () -> Unit) {
    val firstName = state.name.trim().ifEmpty { state.user?.name.orEmpty() }.substringBefore(' ').ifEmpty { null }
    when (outcome) {
        is RegisterOutcome.Confirmed -> ResultView(
            mood = DotFaceMood.Cheering,
            title = if (firstName != null) "You're in, $firstName!" else "You're in!",
            body = "Your ticket is ready. Show its QR code at the door; it works offline too.",
            primaryLabel = outcome.ticketToken?.let { "View ticket" },
            onPrimary = { outcome.ticketToken?.let(onOpenTicket) },
            secondaryLabel = "Back to the event",
            onSecondary = onDone,
            tag = "outcome-confirmed",
            celebrate = true,
        )
        RegisterOutcome.Waitlisted -> ResultView(
            mood = DotFaceMood.Waiting,
            title = "You're on the waitlist",
            body = "The event is full right now. If a place opens up, we'll confirm you and email your ticket.",
            primaryLabel = null,
            onPrimary = {},
            secondaryLabel = "Back to the event",
            onSecondary = onDone,
            tag = "outcome-waitlisted",
        )
        RegisterOutcome.PendingApproval -> ResultView(
            mood = DotFaceMood.Happy,
            title = "Request sent",
            body = "The organisers review each request. We'll email you as soon as they respond.",
            primaryLabel = null,
            onPrimary = {},
            secondaryLabel = "Back to the event",
            onSecondary = onDone,
            tag = "outcome-pending",
        )
    }
}

@Composable
private fun BlockedView(blocked: RegisterCta, onOpenTicket: (String) -> Unit, onBack: () -> Unit) {
    when (blocked) {
        is RegisterCta.Registered -> ResultView(
            mood = DotFaceMood.Happy,
            title = "You're already registered",
            body = "Your place is saved.",
            primaryLabel = blocked.ticketToken?.let { "View ticket" },
            onPrimary = { blocked.ticketToken?.let(onOpenTicket) },
            secondaryLabel = "Back to the event",
            onSecondary = onBack,
            tag = "outcome-already",
        )
        is RegisterCta.Waiting -> ResultView(
            mood = DotFaceMood.Waiting,
            title = if (blocked.status == RsvpStatus.WAITLISTED) "You're on the waitlist" else "Your request is with the organisers",
            body = "We'll email you when anything changes.",
            primaryLabel = null,
            onPrimary = {},
            secondaryLabel = "Back to the event",
            onSecondary = onBack,
            tag = "outcome-waiting",
        )
        is RegisterCta.Closed -> ResultView(
            mood = DotFaceMood.Sleepy,
            title = "Registration is closed",
            body = blocked.reason ?: "This event isn't taking registrations right now.",
            primaryLabel = null,
            onPrimary = {},
            secondaryLabel = "Back to the event",
            onSecondary = onBack,
            tag = "outcome-closed",
        )
        is RegisterCta.Open -> Unit
    }
}

@Composable
private fun ResultView(
    mood: DotFaceMood,
    title: String,
    body: String,
    primaryLabel: String?,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    tag: String,
    celebrate: Boolean = false,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        // A confirmed registration gets a quick pop; the other outcomes just settle in.
        DotFace(mood = mood, size = 112.dp, interactive = true, modifier = Modifier.successPop(enabled = celebrate, from = 0.5f).appearOnEnter())
        Text(
            title,
            style = YuyuTheme.typography.displayM,
            color = YuyuTheme.colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.appearOnEnter(delayMillis = 60).semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        Text(body, style = YuyuTheme.typography.bodyL, color = YuyuTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.appearOnEnter(delayMillis = 100))
        Column(Modifier.fillMaxWidth().appearOnEnter(delayMillis = 140), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (primaryLabel != null) {
                YuyuButton(primaryLabel, onPrimary, variant = YuyuButtonVariant.Accent, size = YuyuButtonSize.Large, leadingIcon = Icons.Outlined.ConfirmationNumber, fullWidth = true)
            }
            YuyuButton(secondaryLabel, onSecondary, variant = YuyuButtonVariant.Secondary, fullWidth = true)
        }
    }
}
