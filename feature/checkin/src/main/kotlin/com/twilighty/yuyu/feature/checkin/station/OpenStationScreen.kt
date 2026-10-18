package com.twilighty.yuyu.feature.checkin.station

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuTextField
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.checkin.camera.QrScanner

/** "Open a check-in station": type the link or `org/event`, or scan the station QR. */
@Composable
internal fun OpenStationScreen(
    onBack: () -> Unit,
    onOpen: (orgSlug: String, eventSlug: String) -> Unit,
    viewModel: OpenStationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val c = YuyuTheme.colors
    LaunchedEffect(state.target) {
        val target = state.target ?: return@LaunchedEffect
        viewModel.onNavigated()
        onOpen(target.orgSlug, target.eventSlug)
    }
    Scaffold(
        topBar = { YuyuTopBar("Open a check-in station", onBack = onBack) },
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
            Text(
                "Volunteers can check guests in without a Yuyu account. The organiser shares a station link (or its QR code) and an 8-digit PIN.",
                style = YuyuTheme.typography.bodyUi,
                color = c.textSecondary,
            )
            state.serverLabel?.let {
                Text("Server: $it", style = YuyuTheme.typography.caption, color = c.textMuted)
            }
            if (state.scanning) {
                QrScanner(
                    paused = false,
                    onCode = { code -> viewModel.submit(code) },
                    hint = "Point the camera at the station QR code",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                YuyuButton("Type the link instead", { viewModel.setScanning(false) }, variant = YuyuButtonVariant.Quiet, fullWidth = true)
            } else {
                YuyuTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = "Station link, or organisation/event",
                    placeholder = "https://events.example.org/my-org/my-event/check-in",
                    helper = "You can also type just my-org/my-event.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    onImeAction = { viewModel.submit(input) },
                )
                YuyuButton("Continue", { viewModel.submit(input) }, enabled = input.isNotBlank(), fullWidth = true)
                YuyuButton(
                    "Scan the station QR",
                    { viewModel.setScanning(true) },
                    variant = YuyuButtonVariant.Secondary,
                    leadingIcon = Icons.Outlined.QrCodeScanner,
                    fullWidth = true,
                )
            }
            state.error?.let { Text(it, style = YuyuTheme.typography.bodyUiMedium, color = c.dangerFg) }
        }
    }
}
