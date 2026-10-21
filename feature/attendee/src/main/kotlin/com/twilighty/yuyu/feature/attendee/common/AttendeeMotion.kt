package com.twilighty.yuyu.feature.attendee.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.twilighty.yuyu.core.designsystem.motion.EntranceState
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.motion.staggeredEntrance
import kotlinx.coroutines.delay

/**
 * Lazy-list item motion: a staggered first appearance plus smooth placement
 * when the list reorders, inserts or removes items. The entrance handles the
 * fade-in, so `animateItem` only animates placement and removal.
 */
internal fun Modifier.listItemMotion(scope: LazyItemScope, entrance: EntranceState, key: Any, index: Int): Modifier = composed {
    val reduced = LocalReducedMotion.current
    with(scope) {
        animateItem(
            fadeInSpec = null,
            placementSpec = if (reduced) null else YuyuSpecs.settle(),
            fadeOutSpec = if (reduced) null else YuyuSpecs.quick(),
        )
    }.staggeredEntrance(entrance, key, index)
}

/**
 * A small celebratory pop for success moments (registration confirmed,
 * feedback sent, checked in): scales up from [from] on a bouncy spring after
 * [delayMillis]. Only plays when [enabled]; reduced motion shows it at rest.
 * Purely visual, so it never blocks input or hides anything from TalkBack.
 */
internal fun Modifier.successPop(enabled: Boolean = true, from: Float = 0.6f, delayMillis: Int = 0): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val play = enabled && !reduced
    val scale = remember { Animatable(if (play) from else 1f) }
    LaunchedEffect(Unit) {
        if (scale.value < 1f) {
            if (delayMillis > 0) delay(delayMillis.toLong())
            scale.animateTo(1f, YuyuSpecs.pop())
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Expands and fades conditional content in and out (form questions revealed
 * by a "show only if" rule, inline problems). Instant under reduced motion.
 */
@Composable
internal fun AnimatedReveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) EnterTransition.None else expandVertically(YuyuSpecs.standard()) + fadeIn(YuyuSpecs.standard(delayMillis = 60)),
        exit = if (reduced) ExitTransition.None else shrinkVertically(YuyuSpecs.standard()) + fadeOut(YuyuSpecs.quick()),
        label = "reveal",
    ) { content() }
}

/**
 * [AnimatedReveal] for an optional value (an inline problem, a field error):
 * shows while [value] is non-null and keeps the last value on screen while it
 * animates out, so the text doesn't vanish before the collapse.
 */
@Composable
internal fun <T : Any> AnimatedRevealValue(value: T?, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    AnimatedReveal(visible = value != null, modifier = modifier) {
        @Suppress("UNCHECKED_CAST")
        (last[0] as T?)?.let { content(it) }
    }
}
