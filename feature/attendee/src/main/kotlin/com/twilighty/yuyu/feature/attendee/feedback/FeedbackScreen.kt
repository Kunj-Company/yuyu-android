package com.twilighty.yuyu.feature.attendee.feedback

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.twilighty.yuyu.core.designsystem.component.YuyuSnackbarHost
import com.twilighty.yuyu.core.designsystem.component.YuyuTextField
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.theme.YuyuSpacing
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.attendee.form.DynamicForm
import com.twilighty.yuyu.feature.attendee.form.FormRules

@Composable
internal fun FeedbackRoute(
    onBack: () -> Unit,
    viewModel: FeedbackViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val share by viewModel.pendingShare.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.saveCertificate()
    }
    LaunchedEffect(share) {
        val uri = share ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(send, "Share certificate")) }
        viewModel.onShareHandled()
    }
    FeedbackScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onAnswersChange = viewModel::onAnswersChange,
        onEmailChange = viewModel::onEmailChange,
        onSubmit = viewModel::submit,
        onSaveCertificate = {
            val needsStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsStorage) storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else viewModel.saveCertificate()
        },
        onShareCertificate = viewModel::shareCertificate,
        onMessageShown = viewModel::onMessageShown,
    )
}

@Composable
internal fun FeedbackScreen(
    state: FeedbackUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAnswersChange: (com.twilighty.yuyu.feature.attendee.form.AnswerState) -> Unit,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSaveCertificate: () -> Unit,
    onShareCertificate: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        onMessageShown()
    }
    val form = state.form
    val showForm = form != null && !state.submitted && !state.closed
    EventAccentScope(state.event?.accentColor) {
        Scaffold(
            topBar = { YuyuTopBar("Feedback", onBack = onBack) },
            bottomBar = { if (showForm) SubmitBar(state, onSubmit) },
            snackbarHost = { YuyuSnackbarHost(snackbar) },
            containerColor = YuyuTheme.colors.bgCanvas,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.loading -> LoadingState(label = "Loading the feedback form")
                    state.closed -> MessageState(
                        mood = DotFaceMood.Sleepy,
                        title = "Feedback isn't open",
                        body = "The organisers haven't opened feedback for this event, or it has closed.",
                    )
                    form == null -> MessageState(
                        mood = if (state.offline) DotFaceMood.Sleepy else DotFaceMood.Sad,
                        title = if (state.offline) "You're offline" else "Couldn't load the form",
                        body = state.loadError,
                        actionLabel = "Try again",
                        onAction = onRetry,
                    )
                    state.submitted -> ThankYou(state, onSaveCertificate, onShareCertificate, onBack)
                    else -> Column(
                        Modifier
                            .fillMaxSize()
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = YuyuSpacing.gutter, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(YuyuSpacing.sectionGap),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(form.title, style = YuyuTheme.typography.h2, color = YuyuTheme.accent.accentText, modifier = Modifier.semantics { heading() })
                            state.event?.let { Text(it.title, style = YuyuTheme.typography.bodyUi, color = YuyuTheme.colors.textSecondary) }
                            form.intro?.let { Text(it, style = YuyuTheme.typography.body, color = YuyuTheme.colors.text) }
                        }
                        DynamicForm(
                            fields = form.fields,
                            state = state.answers,
                            errors = state.errors,
                            onStateChange = onAnswersChange,
                            rules = FormRules.Feedback,
                            enabled = !state.submitting,
                        )
                        if (form.certificatesEnabled) {
                            YuyuTextField(
                                value = state.email,
                                onValueChange = onEmailChange,
                                label = "Email for your certificate",
                                helper = "Use the email you registered with. We check it against confirmed registrations.",
                                error = state.emailError,
                                useAccentFocus = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                                modifier = Modifier.testTag("feedback-email"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmitBar(state: FeedbackUiState, onSubmit: () -> Unit) {
    val c = YuyuTheme.colors
    Column(Modifier.fillMaxWidth().background(c.bgCanvas).navigationBarsPadding().imePadding()) {
        HorizontalDivider(color = c.border)
        Column(Modifier.padding(horizontal = YuyuSpacing.gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val problems = state.errors.size + if (state.emailError != null) 1 else 0
            val problem = state.formError ?: when (problems) {
                0 -> null
                1 -> "One answer needs a quick fix."
                else -> "$problems answers need a quick fix."
            }
            if (problem != null) {
                Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = c.dangerFg)
                    Text(problem, style = YuyuTheme.typography.caption, color = c.dangerFg)
                }
            }
            YuyuButton(
                "Send feedback",
                onSubmit,
                variant = YuyuButtonVariant.Accent,
                size = YuyuButtonSize.Large,
                loading = state.submitting,
                loadingText = "Sending…",
                fullWidth = true,
                modifier = Modifier.testTag("submit-feedback"),
            )
        }
    }
}

@Composable
private fun ThankYou(state: FeedbackUiState, onSave: () -> Unit, onShare: () -> Unit, onDone: () -> Unit) {
    val c = YuyuTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("feedback-thanks"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        DotFace(DotFaceMood.Love, size = 112.dp, interactive = true)
        Text(
            "Thank you!",
            style = YuyuTheme.typography.displayM,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        Text(
            state.form?.thankYouMessage?.takeIf { it.isNotBlank() } ?: "Your feedback helps the organisers make the next one even better.",
            style = YuyuTheme.typography.bodyL,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (state.certificateUrl != null) {
            Text("Your certificate is ready.", style = YuyuTheme.typography.h4, color = c.text)
            YuyuButton(
                if (state.certificateSaved) "Saved to Pictures/Yuyu" else "Save certificate",
                onSave,
                variant = YuyuButtonVariant.Accent,
                size = YuyuButtonSize.Large,
                leadingIcon = Icons.Outlined.Download,
                loading = state.certificateBusy,
                enabled = !state.certificateBusy,
                fullWidth = true,
            )
            YuyuButton("Share certificate", onShare, variant = YuyuButtonVariant.Secondary, leadingIcon = Icons.Outlined.Share, enabled = !state.certificateBusy, fullWidth = true)
        }
        YuyuButton("Done", onDone, variant = YuyuButtonVariant.Quiet)
    }
}
