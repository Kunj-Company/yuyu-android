package com.twilighty.yuyu.feature.broadcasts

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
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs

/** Expand-and-fade for sections, hints and errors that come and go. */
@Composable
internal fun RevealVisibility(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) EnterTransition.None else fadeIn(YuyuSpecs.standard()) + expandVertically(YuyuSpecs.standard()),
        exit = if (reduced) ExitTransition.None else fadeOut(YuyuSpecs.quick()) + shrinkVertically(YuyuSpecs.standard()),
        label = "reveal",
    ) { content() }
}

/** Placement and fades for lazy rows, so list changes settle instead of jumping. */
@Composable
internal fun LazyItemScope.listItemMotion(): Modifier =
    if (LocalReducedMotion.current) {
        Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    } else {
        Modifier.animateItem(fadeInSpec = YuyuSpecs.standard(), placementSpec = YuyuSpecs.settle(), fadeOutSpec = YuyuSpecs.quick())
    }

/**
 * A one-off celebratory pop (scale from [from] with the bouncy spring, well
 * under 600 ms) when the element first appears. For real successes only.
 */
internal fun Modifier.popIn(from: Float = 0.92f): Modifier = composed {
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
