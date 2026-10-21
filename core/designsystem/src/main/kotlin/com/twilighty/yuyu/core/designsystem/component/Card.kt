package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.motion.pressScale
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuSpacing
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

enum class YuyuCardTone {
    /** Canvas + 1dp border (default, flat). */
    Default,
    /** Quiet panel: subtle background, border. */
    Subtle,
    /** Peach tint, no border (feature/principle cards). */
    Tint,
    /** 1.5dp dashed strong border (empty states, drafts). */
    Dashed,
    /** Danger-zone outline. */
    Danger,
}

/**
 * Base card (DESIGN §4.7): flat, 16dp radius, padding 16. Clickable cards are
 * one target. Don't nest more than two rounded containers.
 */
@Composable
fun YuyuCard(
    modifier: Modifier = Modifier,
    tone: YuyuCardTone = YuyuCardTone.Default,
    shape: Shape = YuyuRadius.large,
    contentPadding: PaddingValues = PaddingValues(YuyuSpacing.cardPadding),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = YuyuTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val background = when (tone) {
        YuyuCardTone.Subtle -> c.bgSubtle
        YuyuCardTone.Tint -> c.tintPeach
        else -> c.bgCanvas
    }
    val border: Modifier = when (tone) {
        YuyuCardTone.Default, YuyuCardTone.Subtle -> Modifier.border(1.dp, c.border, shape)
        YuyuCardTone.Danger -> Modifier.border(1.5.dp, c.dangerBorder, shape)
        YuyuCardTone.Dashed -> Modifier.drawBehind {
            drawRoundRect(
                color = c.borderStrong,
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        }
        YuyuCardTone.Tint -> Modifier
    }
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressScale(interaction, pressedScale = 0.985f) else Modifier)
            .clip(shape)
            .background(background, shape)
            .then(border)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = ripple(), onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}
