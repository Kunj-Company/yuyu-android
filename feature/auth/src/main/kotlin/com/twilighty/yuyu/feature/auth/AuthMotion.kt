package com.twilighty.yuyu.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs

/** A short horizontal "no" shake each time [trigger] changes to a non-null value. */
internal fun Modifier.shakeOn(trigger: Any?, amplitude: Dp = 8.dp): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (reduced || trigger == null) return@LaunchedEffect
        offset.snapTo(0f)
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = 320
                -1f at 40
                1f at 100
                -0.7f at 160
                0.45f at 220
                -0.2f at 280
            },
        )
    }
    val px = with(LocalDensity.current) { amplitude.toPx() }
    graphicsLayer { translationX = offset.value * px }
}

/**
 * Shows [message] with a fade-and-expand, and keeps the last one on screen
 * while it animates out after being cleared.
 */
@Composable
internal fun AnimatedMessage(message: String?, content: @Composable (String) -> Unit) {
    val reduced = LocalReducedMotion.current
    var last by remember { mutableStateOf(message) }
    if (message != null) last = message
    AnimatedVisibility(
        visible = message != null,
        enter = if (reduced) EnterTransition.None else fadeIn(YuyuSpecs.quick()) + expandVertically(YuyuSpecs.standard()),
        exit = if (reduced) ExitTransition.None else fadeOut(YuyuSpecs.quick()) + shrinkVertically(YuyuSpecs.standard()),
    ) {
        last?.let { content(it) }
    }
}
