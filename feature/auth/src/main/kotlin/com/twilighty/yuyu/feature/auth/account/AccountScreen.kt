package com.twilighty.yuyu.feature.auth.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.common.AppearanceMode
import com.twilighty.yuyu.core.designsystem.component.ConfirmDialog
import com.twilighty.yuyu.core.designsystem.component.ListRow
import com.twilighty.yuyu.core.designsystem.component.SectionHeader
import com.twilighty.yuyu.core.designsystem.component.SegmentedControl
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.designsystem.component.YuyuAvatar
import com.twilighty.yuyu.core.designsystem.component.YuyuBottomSheet
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuCardTone
import com.twilighty.yuyu.core.designsystem.component.YuyuSnackbarHost
import com.twilighty.yuyu.core.designsystem.component.YuyuTextField
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.designsystem.util.openInBrowser
import com.twilighty.yuyu.feature.auth.google.clearGoogleCredentialState
import com.twilighty.yuyu.feature.auth.signin.WebPaths
import kotlinx.coroutines.launch

private val AppearanceOptions = listOf(AppearanceMode.Light, AppearanceMode.Dark, AppearanceMode.System)

@Composable
fun AccountScreen(
    onOpenDesignGallery: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    Scaffold(
        topBar = { YuyuTopBar("Account") },
        snackbarHost = { YuyuSnackbarHost(snackbar) },
        containerColor = c.bgCanvas,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            YuyuCard(shape = com.twilighty.yuyu.core.designsystem.theme.YuyuRadius.extraExtraLarge) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    YuyuAvatar(state.user?.name ?: state.user?.email, imageUrl = state.user?.imageUrl, size = 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.user?.name?.ifBlank { null } ?: "Add your name",
                            style = t.h3,
                            color = c.text,
                            modifier = Modifier.semantics { heading() },
                        )
                        state.user?.email?.let { Text(it, style = t.bodyUi, color = c.textMuted) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.user?.emailVerified == true) StatusChip("Email verified", StatusTone.Success) else StatusChip("Email not verified", StatusTone.Warning)
                    if (state.user?.mfaEnabled == true) StatusChip("Two-step on", StatusTone.Success) else StatusChip("Two-step off", StatusTone.Warning)
                }
                Spacer(Modifier.height(4.dp))
                YuyuButton("Edit name", viewModel::startEditName, variant = YuyuButtonVariant.Quiet, leadingIcon = Icons.Outlined.Edit)
            }

            SectionHeader("Appearance")
            SegmentedControl(
                options = listOf("Light", "Dark", "System"),
                selectedIndex = AppearanceOptions.indexOf(state.appearance),
                onSelect = { viewModel.setAppearance(AppearanceOptions[it]) },
            )

            SectionHeader("Security")
            YuyuCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                ListRow(
                    title = "Open security settings on the web",
                    supporting = "Password, two-step sign-in and sign-in methods",
                    leadingIcon = Icons.Outlined.Shield,
                    trailing = { androidx.compose.material3.Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = c.textMuted) },
                    onClick = { viewModel.webUrl(WebPaths.SECURITY)?.let { openInBrowser(context, it) } },
                    divider = true,
                )
                ListRow(
                    title = "Server",
                    supporting = state.serverLabel,
                    leadingIcon = Icons.Outlined.Dns,
                    trailing = { YuyuButton("Change", { viewModel.askChangeServer(true) }, variant = YuyuButtonVariant.Quiet) },
                    divider = state.showDesignGallery,
                )
                if (state.showDesignGallery) {
                    ListRow(
                        title = "Design system",
                        supporting = "Debug builds only",
                        leadingIcon = Icons.Outlined.Palette,
                        showChevron = true,
                        onClick = onOpenDesignGallery,
                    )
                }
            }

            YuyuButton(
                "Sign out",
                {
                    scope.launch { clearGoogleCredentialState(context) }
                    viewModel.signOut()
                },
                variant = YuyuButtonVariant.Secondary,
                fullWidth = true,
                enabled = !state.busy,
            )

            YuyuCard(tone = YuyuCardTone.Danger) {
                Text("Sign out everywhere", style = t.h4, color = c.dangerFg)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Signs you out on every phone and browser, including this one.",
                    style = t.bodyUi,
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                YuyuButton("Sign out everywhere…", { viewModel.askSignOutEverywhere(true) }, variant = YuyuButtonVariant.Danger)
            }

            Text(
                "Yuyu for Android ${state.versionName}",
                style = t.caption,
                color = c.textMuted,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
        }
    }

    if (state.editingName) {
        YuyuBottomSheet(onDismiss = viewModel::cancelEditName, title = "Your name") {
            YuyuTextField(
                value = viewModel.nameDraft,
                onValueChange = viewModel::onNameDraftChange,
                label = "Name",
                helper = "Shown to organisers and on your tickets.",
                error = state.nameError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onImeAction = viewModel::saveName,
            )
            YuyuButton("Save name", viewModel::saveName, fullWidth = true, loading = state.savingName, loadingText = "Saving…")
        }
    }
    if (state.confirmSignOutEverywhere) {
        ConfirmDialog(
            title = "Sign out everywhere?",
            body = "You'll be signed out on every phone and browser, including this one. You can sign back in at any time.",
            confirmLabel = "Sign out everywhere",
            danger = true,
            confirmLoading = state.busy,
            onConfirm = {
                scope.launch { clearGoogleCredentialState(context) }
                viewModel.signOutEverywhere()
            },
            onDismiss = { viewModel.askSignOutEverywhere(false) },
        )
    }
    if (state.confirmChangeServer) {
        ConfirmDialog(
            title = "Change server?",
            body = "You'll be signed out and offline tickets on this phone will be removed.",
            confirmLabel = "Change server",
            confirmLoading = state.busy,
            onConfirm = viewModel::changeServer,
            onDismiss = { viewModel.askChangeServer(false) },
        )
    }
}
