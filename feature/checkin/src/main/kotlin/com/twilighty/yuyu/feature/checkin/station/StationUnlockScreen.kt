package com.twilighty.yuyu.feature.checkin.station

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonSize
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

@Composable
internal fun StationUnlockScreen(
    onBack: () -> Unit,
    onUnlocked: () -> Unit,
    onChangeStation: () -> Unit,
    viewModel: StationUnlockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.unlocked) {
        if (state.unlocked) {
            viewModel.onNavigated()
            onUnlocked()
        }
    }
    StationUnlockContent(
        state = state,
        onBack = onBack,
        onDigit = viewModel::digit,
        onBackspace = viewModel::backspace,
        onClear = viewModel::clear,
        onSubmit = viewModel::submit,
        onChangeStation = onChangeStation,
    )
}

@Composable
internal fun StationUnlockContent(
    state: StationUnlockUiState,
    onBack: () -> Unit,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onChangeStation: () -> Unit,
) {
    val c = YuyuTheme.colors
    Scaffold(
        topBar = { YuyuTopBar("Door station", onBack = onBack) },
        containerColor = c.bgCanvas,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Enter the station PIN", style = YuyuTheme.typography.h1, color = c.text, textAlign = TextAlign.Center)
            Text(
                "Ask the organiser for the 8-digit PIN for ${state.orgSlug}/${state.eventSlug}. No Yuyu account needed.",
                style = YuyuTheme.typography.bodyUi,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
            PinDots(state.pinLength)
            state.error?.let {
                Text(
                    it,
                    style = YuyuTheme.typography.bodyUiMedium,
                    color = c.dangerFg,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            PinPad(enabled = !state.busy, onDigit = onDigit, onBackspace = onBackspace, onClear = onClear)
            YuyuButton(
                "Open check-in",
                onSubmit,
                size = YuyuButtonSize.Large,
                enabled = state.canSubmit,
                loading = state.busy,
                loadingText = "Opening…",
                fullWidth = true,
                modifier = Modifier.widthIn(max = 360.dp),
            )
            YuyuButton("Open a different station", onChangeStation, variant = YuyuButtonVariant.Quiet)
        }
    }
}

@Composable
private fun PinDots(length: Int) {
    val c = YuyuTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$length of ${StationUnlockUiState.PIN_LENGTH} digits entered"
        },
    ) {
        repeat(StationUnlockUiState.PIN_LENGTH) { i ->
            Box(
                Modifier
                    .size(16.dp)
                    .clip(YuyuRadius.pill)
                    .background(if (i < length) c.text else c.bgCanvas)
                    .border(1.5.dp, c.borderStrong, YuyuRadius.pill),
            )
        }
    }
}

@Composable
private fun PinPad(enabled: Boolean, onDigit: (Char) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 360.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d -> PinKey(d.toString(), enabled, Modifier.weight(1f)) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PinKey("Clear", enabled, Modifier.weight(1f), onClick = onClear)
            PinKey("0", enabled, Modifier.weight(1f)) { onDigit('0') }
            PinKey("Delete", enabled, Modifier.weight(1f), icon = true, onClick = onBackspace)
        }
    }
}

@Composable
private fun PinKey(label: String, enabled: Boolean, modifier: Modifier, icon: Boolean = false, onClick: () -> Unit) {
    val c = YuyuTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(YuyuRadius.large)
            .background(c.bgMuted)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (icon) {
            Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null, tint = c.text)
        } else {
            Text(label, style = if (label.length == 1) YuyuTheme.typography.h2 else YuyuTheme.typography.label, color = c.text)
        }
    }
}
