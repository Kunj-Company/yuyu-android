package com.twilighty.yuyu.core.designsystem.brand

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

enum class DotMarkVariant { Auto, Full, Bright, OnMarigold, Mono }

/** Smallest size the mark may be drawn at. */
val DotMarkMinSize: Dp = 16.dp

/**
 * The Yuyu dot sign. [DotMarkVariant.Auto] shows the full grid (with soft
 * dots) at 48dp and above and bright dots only below; under 32dp the lit dots
 * use deep marigold so they stay visible on light backgrounds. Pass
 * `label = null` when a visible wordmark already names the brand.
 */
@Composable
fun DotMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    variant: DotMarkVariant = DotMarkVariant.Auto,
    label: String? = "Yuyu",
    monoColor: Color = YuyuTheme.colors.text,
) {
    val px = max(DotMarkMinSize, size)
    // 5 dots + 4 quarter-dot gaps = 6 dots across.
    val dot = px / 6
    val resolved = when (variant) {
        DotMarkVariant.Auto -> if (px >= 48.dp) DotMarkVariant.Full else DotMarkVariant.Bright
        else -> variant
    }
    val pattern = when (resolved) {
        DotMarkVariant.Full -> DotPatterns.MARK
        DotMarkVariant.OnMarigold -> DotPatterns.MARK_ON_MARIGOLD
        else -> DotPatterns.MARK_BRIGHT
    }
    val colors = when {
        resolved == DotMarkVariant.Mono -> mapOf('X' to monoColor, 'O' to monoColor)
        resolved == DotMarkVariant.Bright && px < 32.dp -> mapOf('X' to YuyuTheme.colors.brandMarigoldSmall)
        else -> emptyMap()
    }
    Box(modifier.size(px), contentAlignment = Alignment.Center) {
        DotGrid(pattern = pattern, dot = dot, colors = colors, label = label)
    }
}
