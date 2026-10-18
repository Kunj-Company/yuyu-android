package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.theme.YuyuElevation
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/**
 * Ticket outline: 24dp corners with two half-circle notches cut at
 * [perforationY] (distance from the top), so the page shows through like a
 * real perforated stub.
 */
class TicketShape(
    private val perforationY: Dp,
    private val cornerRadius: Dp = 24.dp,
    private val notchRadius: Dp = 12.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val r = cornerRadius.toPx()
        val n = notchRadius.toPx()
        val y = perforationY.toPx().coerceIn(n, size.height - n)
        val card = Path().apply {
            addRoundRect(RoundRect(Rect(Offset.Zero, size), androidx.compose.ui.geometry.CornerRadius(r)))
        }
        val notches = Path().apply {
            addOval(Rect(Offset(0f, y), n))
            addOval(Rect(Offset(size.width, y), n))
        }
        Outline.Generic(Path.combine(PathOperation.Difference, card, notches))
    }
}

/**
 * Ticket stub (DESIGN §4.11): [header] (put the accent band here), then a
 * dashed perforation between notches, then [stub] (QR on a white plate).
 * [headerHeight] positions the perforation. Uses shadow-ticket elevation.
 */
@Composable
fun TicketStub(
    modifier: Modifier = Modifier,
    headerHeight: Dp,
    header: @Composable ColumnScope.() -> Unit,
    stub: @Composable ColumnScope.() -> Unit,
) {
    val c = YuyuTheme.colors
    val shape = TicketShape(perforationY = headerHeight)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(YuyuElevation.ticket, shape, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(shape)
            .background(c.bgCanvas),
    ) {
        Column(Modifier.fillMaxWidth().height(headerHeight), content = header)
        Canvas(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp)) {
            drawLine(
                color = c.borderStrong,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
            )
        }
        Column(Modifier.fillMaxWidth(), content = stub)
    }
}
