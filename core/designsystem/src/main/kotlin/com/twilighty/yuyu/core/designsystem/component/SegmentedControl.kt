package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/**
 * Segmented control (DESIGN §4.3): muted pill track with 4dp padding; the
 * selected segment is canvas with a soft shadow, and slides between options.
 * Exposes radio semantics.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = YuyuTheme.colors
    val reduced = LocalReducedMotion.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(c.bgMuted, YuyuRadius.pill)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val target = segment * selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
        val indicatorOffset by animateDpAsState(target, if (reduced) YuyuSpecs.quick() else YuyuSpecs.settle(), label = "segment")
        if (selectedIndex in options.indices) {
            Box(
                Modifier
                    .offset(x = if (reduced) target else indicatorOffset)
                    .width(segment)
                    .height(44.dp)
                    .shadow(1.dp, YuyuRadius.pill, ambientColor = c.shadow, spotColor = c.shadow)
                    .background(c.bgCanvas, YuyuRadius.pill),
            )
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(if (selected) c.text else c.textMuted, YuyuSpecs.quick(), label = "segmentText")
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(YuyuRadius.pill)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option,
                        style = YuyuTheme.typography.label,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
