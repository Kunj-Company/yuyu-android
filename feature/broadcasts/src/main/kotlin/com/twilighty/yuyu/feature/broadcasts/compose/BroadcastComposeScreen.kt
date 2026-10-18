package com.twilighty.yuyu.feature.broadcasts.compose

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.designsystem.brand.DotFaceMood
import com.twilighty.yuyu.core.designsystem.component.ConfirmDialog
import com.twilighty.yuyu.core.designsystem.component.DialogActions
import com.twilighty.yuyu.core.designsystem.component.ErrorState
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.MessageState
import com.twilighty.yuyu.core.designsystem.component.SectionHeader
import com.twilighty.yuyu.core.designsystem.component.SegmentedControl
import com.twilighty.yuyu.core.designsystem.component.YuyuBottomSheet
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuCardTone
import com.twilighty.yuyu.core.designsystem.component.YuyuFilterChip
import com.twilighty.yuyu.core.designsystem.component.YuyuIconButton
import com.twilighty.yuyu.core.designsystem.component.YuyuSnackbarHost
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.BroadcastDesignDto
import com.twilighty.yuyu.core.network.model.BroadcastTemplateDto
import com.twilighty.yuyu.feature.broadcasts.BroadcastCopy
import com.twilighty.yuyu.feature.broadcasts.editor.EmailHtml
import com.twilighty.yuyu.feature.broadcasts.editor.RichEmailEditor
import java.text.NumberFormat

/**
 * Email guests: template → audience (live count) → subject and message →
 * design, then test, review and send with a 5-second undo.
 */
@Composable
fun BroadcastComposeScreen(
    viewModel: BroadcastComposeViewModel,
    onBack: () -> Unit,
    onOpenPreview: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var showAllTemplates by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.onMessageShown()
    }

    // Leaving during the countdown cancels the send rather than firing it unseen.
    val counting = state.countdownSeconds != null
    BackHandler(enabled = counting) { confirmLeave = true }
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestViewModel by rememberUpdatedState(viewModel)
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) latestViewModel.cancelSendBecauseLeft()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val back = { if (counting) confirmLeave = true else onBack() }

    Scaffold(
        topBar = {
            YuyuTopBar(
                "Email guests",
                onBack = back,
                actions = {
                    if (state.sentCount == null) {
                        YuyuIconButton(Icons.Outlined.History, "Sent emails", onOpenHistory, enabled = !state.sendPending)
                        YuyuIconButton(
                            Icons.Outlined.Visibility,
                            "Preview",
                            onOpenPreview,
                            enabled = !state.sendPending && viewModel.subject.text.isNotBlank() && !viewModel.editor.isEmpty,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state.sentCount == null && !state.metaLoading && state.metaError == null) SendBar(viewModel, state)
        },
        snackbarHost = { YuyuSnackbarHost(snackbar) },
        containerColor = YuyuTheme.colors.bgCanvas,
    ) { padding ->
        val sentCount = state.sentCount
        when {
            sentCount != null -> MessageState(
                mood = DotFaceMood.Cheering,
                title = "Your email is on its way",
                body = "Queued for ${BroadcastCopy.people(sentCount)}. Messages go out in the background, a few at a time, so large audiences take a few minutes.",
                actionLabel = "Write another",
                onAction = viewModel::startAnother,
                secondaryLabel = "Done",
                onSecondary = onBack,
                modifier = Modifier.padding(padding),
            )
            state.metaLoading && state.templates.isEmpty() -> LoadingState(Modifier.padding(padding), label = "Loading email tools")
            state.metaError != null && state.templates.isEmpty() -> ErrorState(
                state.metaError!!,
                Modifier.padding(padding),
                title = "Couldn't load emails",
                onRetry = viewModel::retryMeta,
            )
            else -> ComposeForm(viewModel, state, Modifier.padding(padding), onShowAllTemplates = { showAllTemplates = true }, onOpenPreview = onOpenPreview)
        }
    }

    if (showAllTemplates) {
        TemplatesSheet(
            templates = state.templates,
            selectedId = state.templateId,
            onPick = {
                showAllTemplates = false
                viewModel.pickTemplate(it)
            },
            onDismiss = { showAllTemplates = false },
        )
    }

    state.review?.let { review -> ReviewSheet(review, viewModel) }

    state.pendingReplace?.let { pending ->
        ConfirmDialog(
            title = "Replace your draft?",
            body = when (pending) {
                is PendingReplace.Template -> "Your subject, message and audience will be replaced by the “${pending.template.name}” template."
                is PendingReplace.History -> "Your subject, message, design and audience will be replaced by this email."
            },
            confirmLabel = "Replace draft",
            onConfirm = viewModel::confirmReplace,
            onDismiss = viewModel::dismissReplace,
        )
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Cancel sending?",
            body = "Your email hasn't gone out yet. If you leave now, sending is cancelled and nothing is sent.",
            confirmLabel = "Leave and cancel",
            dismissLabel = "Stay",
            danger = true,
            onConfirm = {
                confirmLeave = false
                viewModel.cancelSendBecauseLeft()
                onBack()
            },
            onDismiss = { confirmLeave = false },
        )
    }
}

@Composable
private fun StepHeading(step: Int, title: String, aside: String? = null) {
    val c = YuyuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
            Text("$step", style = YuyuTheme.typography.label, color = c.onPrimary)
        }
        Column {
            Text(title, style = YuyuTheme.typography.h4, color = c.text, modifier = Modifier.semantics { heading() })
            if (aside != null) Text(aside, style = YuyuTheme.typography.caption, color = c.textMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposeForm(
    viewModel: BroadcastComposeViewModel,
    state: BroadcastComposeUiState,
    modifier: Modifier,
    onShowAllTemplates: () -> Unit,
    onOpenPreview: () -> Unit,
) {
    val c = YuyuTheme.colors
    val editable = !state.sendPending
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        YuyuCard(tone = YuyuCardTone.Subtle) {
            Text(
                "Emails go only to people who already registered for this event. They're queued and delivered in the background. Send yourself a test first.",
                style = YuyuTheme.typography.bodyUi,
                color = c.textSecondary,
            )
        }

        // 1. Template
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StepHeading(1, "Pick a starting point", "Everything stays editable.")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BroadcastCopy.featuredTemplates(state.templates).forEach { template ->
                    TemplateCard(template, selected = template.id == state.templateId, enabled = editable, modifier = Modifier.width(220.dp)) {
                        viewModel.pickTemplate(template.id)
                    }
                }
                if (state.templates.isNotEmpty()) {
                    YuyuCard(
                        modifier = Modifier.width(180.dp).heightIn(min = 104.dp),
                        tone = YuyuCardTone.Dashed,
                        onClick = if (editable) onShowAllTemplates else null,
                        onClickLabel = "Show all templates",
                    ) {
                        Text("All templates", style = YuyuTheme.typography.bodyUiMedium, color = c.text)
                        Text("${state.templates.size} templates by moment", style = YuyuTheme.typography.caption, color = c.textMuted)
                    }
                }
            }
        }

        // 2. Audience
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepHeading(2, "Who gets it")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BroadcastCopy.STATUSES.forEach { status ->
                    YuyuFilterChip(
                        text = BroadcastCopy.statusChipLabel(status),
                        selected = status in state.statuses,
                        onSelectedChange = { viewModel.toggleStatus(status) },
                        enabled = editable,
                    )
                }
            }
            SegmentedControl(
                options = BroadcastCopy.ATTENDANCE.map(BroadcastCopy::attendanceLabel),
                selectedIndex = BroadcastCopy.ATTENDANCE.indexOf(state.attendance).coerceAtLeast(0),
                onSelect = { if (editable) viewModel.setAttendance(BroadcastCopy.ATTENDANCE[it]) },
            )
            RecipientLine(state)
            state.audienceError?.let { Text(it, style = YuyuTheme.typography.caption, color = c.dangerFg) }
        }

        // 3. Message
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StepHeading(3, "Write it")
            SubjectField(
                value = viewModel.subject,
                onValueChange = viewModel::onSubjectChange,
                error = state.subjectError,
                enabled = editable,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BroadcastCopy.SUBJECT_PLACEHOLDERS.filter { it in state.placeholders }.forEach { token ->
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .clip(YuyuRadius.pill)
                            .clickable(enabled = editable, role = Role.Button, onClickLabel = "Add $token to the subject") { viewModel.insertSubjectPlaceholder(token) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "+ ${BroadcastCopy.placeholderLabel(token)}",
                            style = YuyuTheme.typography.chip,
                            color = c.text,
                            modifier = Modifier.border(1.dp, c.borderStrong, YuyuRadius.pill).padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.size(4.dp))
            Text("Message", style = YuyuTheme.typography.label, color = c.text)
            RichEmailEditor(
                state = viewModel.editor,
                placeholders = state.placeholders,
                enabled = editable,
                isError = state.bodyError != null || viewModel.editor.lengthExceeded,
            )
            val length = viewModel.editor.html.length
            val format = remember { NumberFormat.getIntegerInstance() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    state.bodyError ?: if (length > EmailHtml.BODY_MAX || viewModel.editor.lengthExceeded) "The message is too long." else "Placeholders are filled in for each recipient.",
                    style = YuyuTheme.typography.caption,
                    color = if (state.bodyError != null || length > EmailHtml.BODY_MAX || viewModel.editor.lengthExceeded) c.dangerFg else c.textMuted,
                    modifier = Modifier.weight(1f),
                )
                Text("${format.format(length)} / ${format.format(EmailHtml.BODY_MAX)}", style = YuyuTheme.typography.caption, color = c.textMuted)
            }
        }

        // 4. Design
        if (state.designs.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StepHeading(4, "Choose a design", "Designs change only the frame around your message.")
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.designs.forEach { design ->
                        DesignCard(design, selected = design.id == state.design, enabled = editable) { viewModel.setDesign(design.id) }
                    }
                }
            }
        }

        YuyuButton(
            "Preview",
            onOpenPreview,
            variant = YuyuButtonVariant.Secondary,
            leadingIcon = Icons.Outlined.Visibility,
            fullWidth = true,
            enabled = editable && viewModel.subject.text.isNotBlank() && !viewModel.editor.isEmpty,
        )
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun RecipientLine(state: BroadcastComposeUiState) {
    val c = YuyuTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(YuyuRadius.medium)
            .background(c.bgSubtle)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when (val count = state.count) {
            RecipientCount.Counting -> Text("Counting recipients…", style = YuyuTheme.typography.bodyUi, color = c.textSecondary)
            is RecipientCount.Failed -> Text(count.message, style = YuyuTheme.typography.bodyUi, color = c.dangerFg)
            is RecipientCount.Ready -> {
                Text(BroadcastCopy.recipientLine(count.count), style = YuyuTheme.typography.bodyUiMedium, color = c.text)
                if (count.count > 0) {
                    Text(
                        "Matches ${BroadcastCopy.describeAudience(state.statuses, state.attendance)}. Someone who registered twice still gets one email.",
                        style = YuyuTheme.typography.caption,
                        color = c.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, error: String?, enabled: Boolean) {
    val c = YuyuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Subject", style = YuyuTheme.typography.label, color = c.text)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            isError = error != null,
            textStyle = YuyuTheme.typography.bodyUi.copy(color = c.text),
            shape = YuyuRadius.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.primary,
                unfocusedBorderColor = c.borderStrong,
                errorBorderColor = c.dangerSolid,
                cursorColor = c.primary,
                focusedContainerColor = c.bgCanvas,
                unfocusedContainerColor = c.bgCanvas,
                disabledContainerColor = c.bgSubtle,
            ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Subject" },
        )
        Row {
            Text(error ?: "", style = YuyuTheme.typography.caption, color = c.dangerFg, modifier = Modifier.weight(1f))
            Text("${value.text.length}/${BroadcastCopy.SUBJECT_MAX}", style = YuyuTheme.typography.caption, color = c.textMuted)
        }
    }
}

@Composable
private fun TemplateCard(template: BroadcastTemplateDto, selected: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = YuyuTheme.colors
    Column(
        modifier
            .heightIn(min = 104.dp)
            .clip(YuyuRadius.large)
            .background(if (selected) c.tintPeach else c.bgCanvas)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.border, YuyuRadius.large)
            .selectable(selected = selected, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(template.name, style = YuyuTheme.typography.bodyUiMedium, color = c.text)
        template.description?.let {
            Text(it, style = YuyuTheme.typography.caption, color = c.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TemplatesSheet(templates: List<BroadcastTemplateDto>, selectedId: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    YuyuBottomSheet(onDismiss = onDismiss, title = "Templates") {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BroadcastCopy.templateSections(templates).forEach { section ->
                SectionHeader(section.title)
                section.templates.forEach { template ->
                    TemplateCard(template, selected = template.id == selectedId, enabled = true, modifier = Modifier.fillMaxWidth()) { onPick(template.id) }
                }
            }
        }
    }
}

@Composable
private fun DesignCard(design: BroadcastDesignDto, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val c = YuyuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(YuyuRadius.large)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.border, YuyuRadius.large)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(design.label, style = YuyuTheme.typography.bodyUiMedium, color = c.text)
            design.description?.let { Text(it, style = YuyuTheme.typography.caption, color = c.textMuted) }
        }
        if (selected) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = c.primary)
    }
}

@Composable
private fun SendBar(viewModel: BroadcastComposeViewModel, state: BroadcastComposeUiState) {
    val c = YuyuTheme.colors
    val countdown = state.countdownSeconds
    Column(Modifier.fillMaxWidth().background(c.bgCanvas).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().size(1.dp).background(c.border))
        if (countdown != null) {
            Row(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
                    .clip(YuyuRadius.pill)
                    .background(c.inverseSurface)
                    .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sending to ${BroadcastCopy.people(state.pendingSendCount)} in $countdown…",
                    style = YuyuTheme.typography.bodyUiMedium,
                    color = c.onInverse,
                    modifier = Modifier.weight(1f),
                )
                YuyuButton("Undo", viewModel::undoSend, variant = YuyuButtonVariant.Inverse)
            }
            return@Column
        }
        val blocker = viewModel.blocker()
        val count = state.recipientCount ?: 0
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (state.sending) "Sending…" else blocker ?: "Ready to send to ${BroadcastCopy.people(count)}",
                style = YuyuTheme.typography.caption,
                color = c.textSecondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                YuyuButton(
                    "Send me a test",
                    viewModel::sendTest,
                    variant = YuyuButtonVariant.Secondary,
                    leadingIcon = Icons.Outlined.Science,
                    loading = state.testSending,
                    loadingText = "Sending test",
                    enabled = !state.sendPending,
                    modifier = Modifier.weight(1f),
                )
                YuyuButton(
                    if (blocker == null && count > 0) "Send to ${BroadcastCopy.people(count)}" else "Send",
                    viewModel::openReview,
                    leadingIcon = Icons.AutoMirrored.Outlined.Send,
                    enabled = blocker == null,
                    loading = state.sending,
                    loadingText = "Sending",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReviewSheet(review: ReviewState, viewModel: BroadcastComposeViewModel) {
    val c = YuyuTheme.colors
    val people = BroadcastCopy.people(review.count)
    YuyuBottomSheet(onDismiss = viewModel::dismissReview, title = "Send to $people?") {
        Text("Emails can't be recalled once delivery starts.", style = YuyuTheme.typography.bodyUi, color = c.textSecondary)
        YuyuCard {
            ReviewRow("To", "$people · ${review.audience}")
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).size(1.dp).background(c.border))
            ReviewRow("Subject", review.subject.ifBlank { "(no subject)" })
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(YuyuRadius.medium)
                .toggleable(value = review.checked, role = Role.Checkbox, onValueChange = viewModel::setReviewChecked)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = review.checked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = c.primary, uncheckedColor = c.borderStrong, checkmarkColor = c.onPrimary),
            )
            Column(Modifier.weight(1f)) {
                Text("I've checked this email and want to send it to $people", style = YuyuTheme.typography.bodyUiMedium, color = c.text)
                Text("You'll have ${BroadcastCopy.UNDO_SECONDS} seconds to undo after this.", style = YuyuTheme.typography.caption, color = c.textMuted)
            }
        }
        DialogActions {
            YuyuButton("Cancel", viewModel::dismissReview, variant = YuyuButtonVariant.Secondary)
            YuyuButton("Send to $people", { viewModel.confirmSend() }, enabled = review.checked, leadingIcon = Icons.AutoMirrored.Outlined.Send)
        }
    }
}

@Composable
private fun ColumnScope.ReviewRow(label: String, value: String) {
    Text(label, style = YuyuTheme.typography.caption, color = YuyuTheme.colors.textMuted)
    Text(value, style = YuyuTheme.typography.bodyUi, color = YuyuTheme.colors.text)
}
