package com.twilighty.yuyu.feature.checkin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.motion.fadeThrough

/** Door-desk motion: quick, readable at arm's length, never longer than ~400 ms. */
internal object CheckInMotion {
    const val SHAKE_MS = 320
    const val FLASH_MS = 360
}

/**
 * A short horizontal "no" shake each time [trigger] changes while [active].
 * Pass `Unit` to shake once when the element appears.
 */
internal fun Modifier.shakeOn(trigger: Any?, active: Boolean = trigger != null, amplitude: Dp = 10.dp): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (reduced || !active) return@LaunchedEffect
        offset.snapTo(0f)
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = CheckInMotion.SHAKE_MS
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

/** A light flash over [shape] that fades out once, when the element appears (admitted). */
internal fun Modifier.flashOnEnter(shape: Shape, color: Color = Color.White, peakAlpha: Float = 0.4f): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val alpha = remember { Animatable(if (reduced) 0f else peakAlpha) }
    LaunchedEffect(Unit) {
        if (alpha.value > 0f) alpha.animateTo(0f, tween(CheckInMotion.FLASH_MS))
    }
    drawWithContent {
        drawContent()
        if (alpha.value > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), color.copy(alpha = alpha.value))
    }
}

/** Springs an element (a result icon) up from [from] with the success pop. */
internal fun Modifier.popOnEnter(from: Float = 0.4f): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(if (reduced) 1f else from) }
    LaunchedEffect(Unit) {
        if (scale.value < 1f) scale.animateTo(1f, YuyuSpecs.pop())
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * A hard swap with no size animation either. `None togetherWith None` alone
 * still gets the default spring [androidx.compose.animation.SizeTransform],
 * which keeps the old content on screen while the size settles.
 */
internal fun instantSwap(): ContentTransform =
    ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)

/** Fade-through between whole states, keyed on [contentKey]; an instant swap under reduced motion. */
@Composable
internal fun <S> CheckInStateTransition(
    targetState: S,
    modifier: Modifier = Modifier,
    contentKey: (S) -> Any? = { it },
    label: String = "state",
    content: @Composable (S) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = { if (reduced) instantSwap() else fadeThrough(reduced = false) },
        contentKey = contentKey,
        label = label,
    ) { content(it) }
}
