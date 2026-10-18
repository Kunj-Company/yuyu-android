package com.twilighty.yuyu.feature.broadcasts.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.designsystem.component.ConfirmDialog
import com.twilighty.yuyu.core.designsystem.component.ErrorState
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.SegmentedControl
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.designsystem.util.openInBrowser
import com.twilighty.yuyu.core.network.model.BroadcastContentRequest

/** Preview of the draft as sent: subject, the rendered email (phone or desktop width) and its plain-text part. */
@Composable
fun BroadcastPreviewScreen(
    orgSlug: String,
    eventId: String,
    content: BroadcastContentRequest,
    viewModel: BroadcastPreviewViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(orgSlug, eventId, content) { viewModel.load(orgSlug, eventId, content) }

    Scaffold(
        topBar = { YuyuTopBar("Preview", onBack = onBack) },
        containerColor = YuyuTheme.colors.bgCanvas,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedControl(
                options = listOf("Email", "Plain text"),
                selectedIndex = state.tab.ordinal,
                onSelect = { viewModel.setTab(PreviewTab.entries[it]) },
            )
            val preview = state.preview
            when {
                state.loading && preview == null -> LoadingState(label = "Rendering preview")
                state.error != null && preview == null -> ErrorState(state.error!!, title = "Couldn't render the preview", onRetry = viewModel::retry)
                preview != null -> {
                    Text(
                        buildAnnotatedString {
                            append("Subject: ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = YuyuTheme.colors.text)) { append(preview.subject) }
                        },
                        style = YuyuTheme.typography.label,
                        color = YuyuTheme.colors.textMuted,
                    )
                    if (state.error != null) Text(state.error!!, style = YuyuTheme.typography.caption, color = YuyuTheme.colors.dangerFg)
                    when (state.tab) {
                        PreviewTab.Email -> {
                            SegmentedControl(
                                options = listOf("Mobile", "Desktop"),
                                selectedIndex = state.width.ordinal,
                                onSelect = { viewModel.setWidth(PreviewWidth.entries[it]) },
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(YuyuRadius.large)
                                    .border(1.dp, YuyuTheme.colors.border, YuyuRadius.large)
                                    .background(YuyuTheme.colors.bgMuted),
                            ) {
                                SafeEmailWebView(
                                    html = preview.html,
                                    width = state.width,
                                    onLinkTapped = viewModel::onLinkTapped,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        PreviewTab.PlainText -> {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(YuyuRadius.large)
                                    .border(1.dp, YuyuTheme.colors.border, YuyuRadius.large)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                            ) {
                                SelectionContainer {
                                    Text(preview.text.ifBlank { "(No plain-text part)" }, style = YuyuTheme.typography.bodyUi, color = YuyuTheme.colors.text)
                                }
                            }
                        }
                    }
                    Text(
                        "Rendered by the server with example values, exactly as it will arrive. Each attendee sees their own name and, when confirmed, their own ticket link.",
                        style = YuyuTheme.typography.caption,
                        color = YuyuTheme.colors.textMuted,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
        }
    }

    state.pendingLink?.let { url ->
        ConfirmDialog(
            title = "Open this link?",
            body = "$url\n\nIt opens in your browser, outside Yuyu.",
            confirmLabel = "Open in browser",
            onConfirm = {
                viewModel.dismissLink()
                openInBrowser(context, url)
            },
            onDismiss = viewModel::dismissLink,
        )
    }
}

/** Shown when Preview is opened without a draft to preview (e.g. restored on its own). */
@Composable
fun BroadcastPreviewUnavailable(onBack: () -> Unit) {
    Scaffold(topBar = { YuyuTopBar("Preview", onBack = onBack) }, containerColor = YuyuTheme.colors.bgCanvas) { padding ->
        ErrorState(
            message = "Open the email you're writing, then tap Preview again.",
            title = "Nothing to preview",
            modifier = Modifier.padding(padding),
        )
    }
}
