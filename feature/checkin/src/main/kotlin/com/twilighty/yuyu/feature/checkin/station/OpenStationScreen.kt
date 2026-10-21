package com.twilighty.yuyu.feature.checkin.station

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.remember
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
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.checkin.ui.CheckInStateTransition
import com.twilighty.yuyu.feature.checkin.ui.shakeOn
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
    val reduced = LocalReducedMotion.current
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
            CheckInStateTransition(state.scanning, Modifier.fillMaxWidth(), label = "openStationMode") { scanning ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (scanning) {
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
                }
            }
            var lastError by remember { mutableStateOf(state.error) }
            if (state.error != null) lastError = state.error
            AnimatedVisibility(
                visible = state.error != null,
                enter = if (reduced) EnterTransition.None else fadeIn(YuyuSpecs.quick()) + expandVertically(YuyuSpecs.standard()),
                exit = if (reduced) ExitTransition.None else fadeOut(YuyuSpecs.quick()) + shrinkVertically(YuyuSpecs.standard()),
            ) {
                Text(lastError.orEmpty(), style = YuyuTheme.typography.bodyUiMedium, color = c.dangerFg, modifier = Modifier.shakeOn(state.error))
            }
        }
    }
}
