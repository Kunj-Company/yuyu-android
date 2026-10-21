package com.twilighty.yuyu.feature.broadcasts.history

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.designsystem.component.ConfirmDialog
import com.twilighty.yuyu.core.designsystem.component.EmptyState
import com.twilighty.yuyu.core.designsystem.component.ErrorState
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.YuyuBottomSheet
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.motion.AnimatedCounterText
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.motion.YuyuStateTransition
import com.twilighty.yuyu.core.designsystem.motion.rememberEntranceState
import com.twilighty.yuyu.core.designsystem.motion.staggeredEntrance
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.BroadcastHistoryDto
import com.twilighty.yuyu.feature.broadcasts.BroadcastCopy
import com.twilighty.yuyu.feature.broadcasts.listItemMotion
import com.twilighty.yuyu.feature.broadcasts.editor.EmailHtml

/**
 * Emails sent for the event. "Reuse" loads one into [reuseTarget] (the open
 * Compose screen) or the stored draft, then [onReused] returns to Compose.
 */
@Composable
fun BroadcastHistoryScreen(
    viewModel: BroadcastHistoryViewModel,
    reuseTarget: ReuseTarget?,
    onBack: () -> Unit,
    onReused: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var openRowId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.reused) {
        if (state.reused) {
            viewModel.onReusedHandled()
            onReused()
        }
    }

    Scaffold(
        topBar = { YuyuTopBar("Sent emails", onBack = onBack) },
        containerColor = YuyuTheme.colors.bgCanvas,
    ) { padding ->
        val entrance = rememberEntranceState()
        YuyuStateTransition(
            targetState = state,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentKey = { it.phase() },
            label = "history",
        ) { shown ->
            when (shown.phase()) {
                HistoryPhase.Loading -> LoadingState(label = "Loading sent emails")
                HistoryPhase.Error -> ErrorState(shown.error.orEmpty(), onRetry = viewModel::load)
                HistoryPhase.Empty -> EmptyState(
                    title = "Nothing sent yet",
                    body = "No emails have been sent for this event yet.",
                )
                HistoryPhase.List -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(shown.history, key = { _, row -> row.id }) { index, row ->
                        HistoryRow(
                            row = row,
                            designLabel = shown.designLabel(row.design),
                            onOpen = { openRowId = row.id },
                            onReuse = { viewModel.requestReuse(row, reuseTarget) },
                            modifier = listItemMotion().staggeredEntrance(entrance, row.id, index),
                        )
                    }
                }
            }
        }
    }

    state.history.firstOrNull { it.id == openRowId }?.let { row ->
        YuyuBottomSheet(onDismiss = { openRowId = null }, title = row.subject) {
            Text(sentLine(row), style = YuyuTheme.typography.caption, color = YuyuTheme.colors.textMuted)
            Text(
                "${state.designLabel(row.design)} design · ${BroadcastCopy.describeAudience(row.audience.statuses, row.audience.attendance)}",
                style = YuyuTheme.typography.caption,
                color = YuyuTheme.colors.textMuted,
            )
            val body = remember(row.bodyHtml) { row.bodyHtml?.let(EmailHtml::toPlainText).orEmpty() }
            if (body.isNotBlank()) {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(body, style = YuyuTheme.typography.bodyUi, color = YuyuTheme.colors.textSecondary)
                }
            }
            YuyuButton(
                "Reuse this email",
                onClick = {
                    openRowId = null
                    viewModel.requestReuse(row, reuseTarget)
                },
                fullWidth = true,
            )
        }
    }

    state.pendingReuse?.let {
        ConfirmDialog(
            title = "Replace your draft?",
            body = "The subject, message, design and audience you're writing will be replaced by this email.",
            confirmLabel = "Replace draft",
            onConfirm = { viewModel.confirmReuse(reuseTarget) },
            onDismiss = viewModel::dismissReuse,
        )
    }
}

private enum class HistoryPhase { Loading, Error, Empty, List }

private fun BroadcastHistoryUiState.phase(): HistoryPhase = when {
    loading && history.isEmpty() -> HistoryPhase.Loading
    error != null && history.isEmpty() -> HistoryPhase.Error
    history.isEmpty() -> HistoryPhase.Empty
    else -> HistoryPhase.List
}

private fun sentLine(row: BroadcastHistoryDto): String =
    "Sent to ${BroadcastCopy.people(row.recipientCount)} · ${BroadcastCopy.formatSentAt(row.sentAt)}"

@Composable
private fun HistoryRow(row: BroadcastHistoryDto, designLabel: String, onOpen: () -> Unit, onReuse: () -> Unit, modifier: Modifier = Modifier) {
    val c = YuyuTheme.colors
    YuyuCard(modifier.animateContentSize(YuyuSpecs.standard()), onClick = onOpen, onClickLabel = "Show email") {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.subject, style = YuyuTheme.typography.bodyUiMedium, color = c.text, modifier = Modifier.semantics { heading() })
                AnimatedCounterText(sentLine(row), style = YuyuTheme.typography.caption, color = c.textMuted)
                Text(
                    "$designLabel design · ${BroadcastCopy.describeAudience(row.audience.statuses, row.audience.attendance)}",
                    style = YuyuTheme.typography.caption,
                    color = c.textMuted,
                )
                val preview = remember(row.bodyHtml) { row.bodyHtml?.let(EmailHtml::toPlainText).orEmpty() }
                if (preview.isNotBlank()) {
                    Text(preview, style = YuyuTheme.typography.caption, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            YuyuButton("Reuse", onReuse, variant = YuyuButtonVariant.Quiet)
        }
    }
}
