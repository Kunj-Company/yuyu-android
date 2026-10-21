package com.twilighty.yuyu.feature.organiser.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.theme.YuyuMotion

/**
 * A status chip that cross-fades (colour, icon and word together) when its
 * status changes, e.g. "To approve" → "Going" after an approval.
 */
@Composable
fun AnimatedStatusChip(text: String, tone: StatusTone, modifier: Modifier = Modifier, showIcon: Boolean = true) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = text to tone,
        modifier = modifier,
        transitionSpec = {
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(YuyuSpecs.quick()) + scaleIn(YuyuSpecs.settle(), initialScale = 0.9f)) togetherWith
                    fadeOut(YuyuSpecs.quick()) using SizeTransform(clip = false) { _, _ -> YuyuSpecs.settle() }
            }
        },
        contentAlignment = Alignment.Center,
        label = "statusChip",
    ) { (label, chipTone) ->
        if (showIcon) StatusChip(label, chipTone) else StatusChip(label, chipTone, icon = null)
    }
}

/** Placement, fade-in and fade-out for lazy list rows, so filters and removals don't jump. */
@Composable
fun LazyItemScope.listItemMotion(): Modifier =
    if (LocalReducedMotion.current) {
        Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    } else {
        Modifier.animateItem(
            fadeInSpec = YuyuSpecs.standard(),
            placementSpec = YuyuSpecs.settle(),
            fadeOutSpec = YuyuSpecs.quick(),
        )
    }

/** Expand-and-fade for banners, hints and controls that come and go. */
@Composable
fun RevealVisibility(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) EnterTransition.None else fadeIn(YuyuSpecs.standard()) + expandVertically(YuyuSpecs.standard()),
        exit = if (reduced) ExitTransition.None else fadeOut(YuyuSpecs.quick()) + shrinkVertically(YuyuSpecs.standard()),
        label = "reveal",
    ) { content() }
}

/**
 * Plays a short success "pop" (slight overshoot in scale) each time [trigger]
 * changes after the first composition. For real successes only.
 */
fun Modifier.popOnChange(trigger: Any?): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(trigger) {
        if (first[0]) {
            first[0] = false
        } else if (!reduced) {
            scale.snapTo(0.9f)
            scale.animateTo(1f, YuyuSpecs.pop())
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Brief highlight (1 → 0) after [trigger] changes, used to confirm an action on
 * a row without adding content. Returns 0 under reduced motion.
 */
@Composable
fun rememberChangeFlash(trigger: Any?): Animatable<Float, AnimationVector1D> {
    val reduced = LocalReducedMotion.current
    val flash = remember { Animatable(0f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(trigger) {
        if (first[0]) {
            first[0] = false
        } else if (!reduced) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(FLASH_MS, delayMillis = YuyuMotion.HOVER, easing = YuyuMotion.Easing))
        }
    }
    return flash
}

private const val FLASH_MS = 520
