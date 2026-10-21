package com.twilighty.yuyu.feature.checkin.station

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.checkin.ui.shakeOn

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
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.error) {
        if (state.error != null) haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }
    // Keep the last message while it fades out once the volunteer starts typing again.
    var lastError by remember { mutableStateOf(state.error) }
    if (state.error != null) lastError = state.error
    val tapDigit: (Char) -> Unit = { d ->
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        onDigit(d)
    }
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
            // A wrong PIN shakes the dots, the way a lock says no.
            PinDots(state.pinLength, busy = state.busy, modifier = Modifier.shakeOn(state.error))
            AnimatedVisibility(
                visible = state.error != null,
                enter = if (reduced) EnterTransition.None else fadeIn(YuyuSpecs.quick()) + expandVertically(YuyuSpecs.standard()),
                exit = if (reduced) ExitTransition.None else fadeOut(YuyuSpecs.quick()) + shrinkVertically(YuyuSpecs.standard()),
            ) {
                Text(
                    lastError.orEmpty(),
                    style = YuyuTheme.typography.bodyUiMedium,
                    color = c.dangerFg,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            PinPad(enabled = !state.busy, onDigit = tapDigit, onBackspace = onBackspace, onClear = onClear)
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
private fun PinDots(length: Int, busy: Boolean, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$length of ${StationUnlockUiState.PIN_LENGTH} digits entered"
        },
    ) {
        if (busy && !reduced) {
            // Opening the station: the dots ripple while the PIN is checked.
            val wave = rememberInfiniteTransition(label = "pinWave")
            repeat(StationUnlockUiState.PIN_LENGTH) { i ->
                val lift by wave.animateFloat(
                    initialValue = 0f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        keyframes {
                            durationMillis = PIN_WAVE_MS
                            0f at i * 60
                            1f at i * 60 + 160
                            0f at i * 60 + 320
                        },
                    ),
                    label = "pinWaveDot",
                )
                PinDot(filled = lift > 0.5f, scale = 1f + 0.25f * lift)
            }
        } else {
            repeat(StationUnlockUiState.PIN_LENGTH) { i ->
                val filled = i < length
                val scale by animateFloatAsState(
                    targetValue = if (filled) 1f else 0.85f,
                    animationSpec = if (filled && !reduced) YuyuSpecs.pop() else YuyuSpecs.quick(),
                    label = "pinDot",
                )
                PinDot(filled = filled, scale = if (reduced) 1f else scale)
            }
        }
    }
}

@Composable
private fun PinDot(filled: Boolean, scale: Float) {
    val c = YuyuTheme.colors
    val fill by animateColorAsState(if (filled) c.text else c.bgCanvas, YuyuSpecs.quick(), label = "pinDotFill")
    Box(
        Modifier
            .size(16.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(YuyuRadius.pill)
            .background(fill)
            .border(1.5.dp, c.borderStrong, YuyuRadius.pill),
    )
}

private const val PIN_WAVE_MS = 60 * StationUnlockUiState.PIN_LENGTH + 520

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
