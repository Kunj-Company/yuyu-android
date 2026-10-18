package com.twilighty.yuyu.feature.auth.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.designsystem.brand.DotLockup
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonSize
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuCardTone
import com.twilighty.yuyu.core.designsystem.component.YuyuTextField
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

@Composable
fun ServerSetupScreen(viewModel: ServerSetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ServerSetupContent(state, viewModel.address, viewModel::onAddressChange, viewModel::connect)
}

@Composable
internal fun ServerSetupContent(
    state: ServerSetupUiState,
    address: String,
    onAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    val c = YuyuTheme.colors
    Surface(color = c.bgCanvas, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DotLockup()
                Spacer(Modifier.height(16.dp))
                Text(
                    "Connect to your Yuyu",
                    style = YuyuTheme.typography.h1,
                    color = c.text,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Yuyu is self-hosted, so each community runs its own server. Enter the web address you use to open it.",
                    style = YuyuTheme.typography.body,
                    color = c.textSecondary,
                )
                YuyuTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    label = "Server address",
                    placeholder = "events.example.org",
                    error = state.error,
                    helper = "Usually the address of your community's event pages.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    onImeAction = onConnect,
                    enabled = !state.checking,
                )
                YuyuButton(
                    text = "Continue",
                    onClick = onConnect,
                    size = YuyuButtonSize.Large,
                    fullWidth = true,
                    loading = state.checking,
                    loadingText = "Checking…",
                    enabled = address.isNotBlank(),
                )
                if (state.showDebugHint) {
                    YuyuCard(tone = YuyuCardTone.Subtle) {
                        Text(
                            "Debug build: use http://10.0.2.2:3000 for a Yuyu running on this computer (emulator).",
                            style = YuyuTheme.typography.caption,
                            color = c.textMuted,
                        )
                    }
                }
            }
        }
    }
}

