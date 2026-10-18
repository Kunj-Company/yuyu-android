package com.twilighty.yuyu.core.designsystem.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/** Default colour per pattern letter; brand tokens follow light/dark mode. */
@Composable
fun defaultDotColors(): Map<Char, Color> {
    val c = YuyuTheme.colors
    return mapOf(
        'X' to c.brandMarigold,
        'O' to c.brandDotBase,
        '.' to c.brandDotOff,
        'D' to c.brandMarigoldDeep,
        'W' to Color.White,
        'm' to Color(0xFFF0A537),
    )
}

/**
 * Renders a dot pattern as perfect circles. [dot] is the diameter; the gap is
 * a quarter of a dot (brand rule). With [label] the grid is announced as an
 * image; otherwise it is decorative. [dotScale] lets animations scale dots
 * per cell (row, column) around their centre.
 */
@Composable
fun DotGrid(
    pattern: String,
    dot: Dp,
    modifier: Modifier = Modifier,
    gap: Dp = dot / 4,
    colors: Map<Char, Color> = emptyMap(),
    label: String? = null,
    dotScale: ((row: Int, column: Int) -> Float)? = null,
) {
    val rows = remember(pattern) { DotPatterns.parse(pattern) }
    val columns = rows.firstOrNull()?.size ?: 0
    val palette = defaultDotColors() + colors
    val width = dot * columns + gap * (columns - 1).coerceAtLeast(0)
    val height = dot * rows.size + gap * (rows.size - 1).coerceAtLeast(0)
    val semantics = if (label != null) {
        Modifier.semantics {
            contentDescription = label
            role = Role.Image
        }
    } else {
        Modifier
    }
    Canvas(modifier.then(semantics).size(width, height)) {
        val d = dot.toPx()
        val g = gap.toPx()
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, cell ->
                val color = palette[cell] ?: return@forEachIndexed
                if (cell == ' ') return@forEachIndexed
                val scale = dotScale?.invoke(r, c) ?: 1f
                drawCircle(
                    color = color,
                    radius = d / 2f * scale,
                    center = Offset(c * (d + g) + d / 2f, r * (d + g) + d / 2f),
                )
            }
        }
    }
}
