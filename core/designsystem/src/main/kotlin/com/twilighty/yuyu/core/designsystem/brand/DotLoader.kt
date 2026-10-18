package com.twilighty.yuyu.core.designsystem.brand

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.designsystem.theme.rememberReducedMotion

private val JumpEasing = CubicBezierEasing(0.45f, 0f, 0.3f, 1f)

/**
 * Loading indicator: two marigold dots and an ink dot hopping left to right
 * in turn (1.2s loop, 160ms stagger). [size] is the overall width. With
 * reduced motion the dots rest on the baseline. Announces [label] politely.
 */
@Composable
fun DotLoader(modifier: Modifier = Modifier, size: Dp = 48.dp, label: String = "Loading") {
    val dot = size / 4.2f
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "dot-loader")
    val colors = listOf(YuyuTheme.colors.brandMarigold, YuyuTheme.colors.brandMarigold, YuyuTheme.colors.primary)
    Row(
        modifier
            .semantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            }
            .height(dot * 1.8f),
        horizontalArrangement = Arrangement.spacedBy(dot * 0.6f),
        verticalAlignment = Alignment.Bottom,
    ) {
        colors.forEachIndexed { index, color ->
            val offset by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0f at 0 using JumpEasing
                        -0.8f at 240 using JumpEasing
                        0f at 540
                        0f at 1200
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(index * 160),
                ),
                label = "dot-$index",
            )
            Box(
                Modifier
                    .size(dot)
                    .graphicsLayer { translationY = if (reduced) 0f else offset * dot.toPx() }
                    .background(color, CircleShape),
            )
        }
    }
}
