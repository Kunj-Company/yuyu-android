package com.twilighty.yuyu.core.designsystem.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.theme.YuyuMotion
import kotlinx.coroutines.delay

/**
 * True when the person turned animations off (Settings › Accessibility ›
 * Remove animations). Provided by `YuyuTheme`; every helper here collapses to
 * an instant or plain-fade change when it is set.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Shared animation specs built on the DESIGN §2.10 tokens. */
object YuyuSpecs {
    /** Standard eased tween for panels, colours and layout changes. */
    fun <T> standard(durationMillis: Int = YuyuMotion.PANEL, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis, YuyuMotion.Easing)

    /** Quick tween for press feedback and small state flips. */
    fun <T> quick(): FiniteAnimationSpec<T> = tween(YuyuMotion.HOVER, easing = YuyuMotion.Easing)

    /** Soft spring for things that should settle naturally (press release, indicators). */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

    /** Bouncier spring for small celebratory pops (success faces, check marks). */
    fun <T> pop(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
}

/**
 * Scales an element down a touch while pressed and springs it back on release.
 * Pass the same [interactionSource] as the element's `clickable`.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduced) pressedScale else 1f,
        animationSpec = if (pressed) YuyuSpecs.quick() else YuyuSpecs.settle(),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Fades and lifts content in once, when it first appears. Use for page
 * sections and one-off elements; for list items use [staggeredEntrance].
 */
fun Modifier.appearOnEnter(delayMillis: Int = 0, offset: Dp = 12.dp): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            if (delayMillis > 0) delay(delayMillis.toLong())
            progress.animateTo(1f, YuyuSpecs.standard(YuyuMotion.SUCCESS))
        }
    }
    val offsetPx = with(LocalDensity.current) { offset.toPx() }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}

/**
 * Remembers which list items already played their entrance, so scrolling an
 * item off and back on screen doesn't replay it. One per list.
 */
class EntranceState internal constructor() {
    internal val seen = mutableSetOf<Any>()
}

@Composable
fun rememberEntranceState(): EntranceState = remember { EntranceState() }

/**
 * Staggered fade-and-rise for list items the first time they show. [index] is
 * the item's position; the delay is capped so long lists don't wait.
 */
fun Modifier.staggeredEntrance(state: EntranceState, key: Any, index: Int): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val animate = remember(key) { !reduced && state.seen.add(key) }
    val progress = remember(key) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(key) {
        if (progress.value < 1f) {
            delay(index.coerceIn(0, MAX_STAGGERED) * STAGGER_MS)
            progress.animateTo(1f, YuyuSpecs.standard(YuyuMotion.SUCCESS))
        }
    }
    val offsetPx = with(LocalDensity.current) { 16.dp.toPx() }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}

private const val MAX_STAGGERED = 8
private const val STAGGER_MS = 35L

/** Fade-through between whole states (loading → content → error). */
fun <S> AnimatedContentTransitionScope<S>.fadeThrough(reduced: Boolean): ContentTransform =
    if (reduced) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        (fadeIn(YuyuSpecs.standard(220, delayMillis = 60)) + slideInVertically(YuyuSpecs.standard(280)) { it / 24 }) togetherWith
            fadeOut(tween(120)) using SizeTransform(clip = false)
    }

/**
 * Animated swap between screen states. [contentKey] decides what counts as a
 * new state (default: the state's class), so data refreshes inside the same
 * state don't re-animate.
 */
@Composable
fun <S> YuyuStateTransition(
    targetState: S,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (S) -> Any? = { state -> state?.let { it::class } },
    label: String = "state",
    content: @Composable (S) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = { fadeThrough(reduced) },
        contentAlignment = contentAlignment,
        contentKey = contentKey,
        label = label,
    ) { state -> content(state) }
}

/**
 * Text that rolls up (value grew) or down (value shrank) when it changes, for
 * counters such as check-in totals. Non-numeric changes cross-fade.
 */
@Composable
fun AnimatedCounterText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            val from = initialState.filter(Char::isDigit).toLongOrNull()
            val to = targetState.filter(Char::isDigit).toLongOrNull()
            (when {
                reduced -> EnterTransition.None togetherWith ExitTransition.None
                from == null || to == null || from == to ->
                    fadeIn(YuyuSpecs.quick()) togetherWith fadeOut(YuyuSpecs.quick())
                else -> {
                    val up = to > from
                    (slideInVertically(YuyuSpecs.settle()) { if (up) it else -it } + fadeIn(YuyuSpecs.quick())) togetherWith
                        (slideOutVertically(YuyuSpecs.settle()) { if (up) -it else it } + fadeOut(YuyuSpecs.quick()))
                }
            }) using SizeTransform(clip = true)
        },
        label = "counter",
    ) { value -> Text(value, style = style, color = color) }
}
