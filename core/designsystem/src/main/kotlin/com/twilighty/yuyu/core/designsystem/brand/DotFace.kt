package com.twilighty.yuyu.core.designsystem.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.theme.rememberReducedMotion
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * A dot face (7×7). Faces carry meaning — see [DotFaceMood]. With
 * [interactive], tapping ripples the dots out from the centre and lifts/tilts
 * the face (the touch version of the web hover); still under reduced motion.
 */
@Composable
fun DotFace(
    mood: DotFaceMood,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    label: String? = null,
    colors: Map<Char, Color> = emptyMap(),
    interactive: Boolean = false,
) {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(0f) }
    var trigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(trigger) {
        if (trigger > 0 && !reduced) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(900))
            progress.snapTo(0f)
        }
    }
    val p = progress.value
    val lift = if (p == 0f) 0f else sin(p * Math.PI).toFloat()
    val tap = if (interactive) {
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { trigger++ }
    } else {
        Modifier
    }
    DotGrid(
        pattern = mood.pattern,
        // 7 dots + 6 quarter-dot gaps = 8.5 dots across.
        dot = size / 8.5f,
        colors = colors,
        label = label,
        modifier = modifier
            .then(tap)
            .graphicsLayer {
                translationY = -4.dp.toPx() * lift
                rotationZ = -4f * lift
            },
        dotScale = if (p == 0f) null else { row, column ->
            // Each dot pops 16% with a delay proportional to its distance from the centre.
            val delay = (hypot((row - 3).toDouble(), (column - 3).toDouble()) * 45 / 900).toFloat()
            val local = ((p - delay) / 0.62f).coerceIn(0f, 1f)
            val pop = 1f + 0.16f * sin(local * Math.PI).toFloat()
            val blink = if (mood.blinks && row in 1..2 && mood.pattern.split("|")[row][column] == 'O') {
                val b = ((p - 0.45f) / 0.25f).coerceIn(0f, 1f)
                max(0.15f, 1f - sin(b * Math.PI).toFloat())
            } else {
                1f
            }
            pop * blink
        },
    )
}
