package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

enum class StatusTone { Success, Warning, Danger, Neutral }

/** Status chip: soft bg + strong fg + icon + word — never colour alone (DESIGN §2.4). */
@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = defaultStatusIcon(tone),
) {
    val c = YuyuTheme.colors
    val (bg, fg) = when (tone) {
        StatusTone.Success -> c.successBg to c.successFg
        StatusTone.Warning -> c.warningBg to c.warningFg
        StatusTone.Danger -> c.dangerBg to c.dangerFg
        StatusTone.Neutral -> c.bgMuted to c.textSecondary
    }
    ChipSurface(text, bg, fg, modifier, icon)
}

private fun defaultStatusIcon(tone: StatusTone): ImageVector? = when (tone) {
    StatusTone.Success -> Icons.Outlined.CheckCircle
    StatusTone.Warning -> Icons.Outlined.Schedule
    StatusTone.Danger -> Icons.Outlined.Block
    StatusTone.Neutral -> null
}

enum class YuyuChipStyle { Neutral, Accent, Info }

/** Plain chip/badge: neutral (muted bg) or accent (accent-soft + accent-text). */
@Composable
fun YuyuChip(text: String, modifier: Modifier = Modifier, style: YuyuChipStyle = YuyuChipStyle.Neutral, icon: ImageVector? = null) {
    val c = YuyuTheme.colors
    val a = YuyuTheme.accent
    when (style) {
        YuyuChipStyle.Neutral -> ChipSurface(text, c.bgMuted, c.textSecondary, modifier, icon)
        YuyuChipStyle.Accent -> ChipSurface(text, a.accentSoft, a.accentText, modifier, icon)
        YuyuChipStyle.Info -> ChipSurface(text, c.bgMuted, c.textSecondary, modifier, icon ?: Icons.Outlined.Info)
    }
}

@Composable
private fun ChipSurface(text: String, bg: Color, fg: Color, modifier: Modifier, icon: ImageVector?) {
    Row(
        modifier
            .background(bg, YuyuRadius.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Text(text, style = YuyuTheme.typography.chip, color = fg, maxLines = 1)
    }
}

/**
 * Filter / toggle chip (DESIGN §4.3): pill with 1dp border; selected = ink
 * fill + white text with a check. Exposes toggle semantics (aria-pressed).
 */
@Composable
fun YuyuFilterChip(
    text: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = YuyuTheme.colors
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(YuyuRadius.pill)
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = onSelectedChange),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .background(if (selected) c.primary else c.bgCanvas, YuyuRadius.pill)
                .border(if (selected) 1.5.dp else 1.dp, if (selected) c.primary else c.borderStrong, YuyuRadius.pill)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
            Text(text, style = YuyuTheme.typography.chip, color = if (selected) c.onPrimary else c.text)
        }
    }
}
