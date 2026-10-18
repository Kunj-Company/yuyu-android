package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/**
 * A tappable row: optional leading slot (icon, avatar, date block), title +
 * one-line supporting text, optional trailing slot (chip, value) and chevron.
 * At least 56dp tall.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    titleColor: Color = YuyuTheme.colors.text,
    onClick: (() -> Unit)? = null,
    divider: Boolean = false,
) {
    val c = YuyuTheme.colors
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                leading != null -> leading()
                leadingIcon != null -> Icon(leadingIcon, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = YuyuTheme.typography.bodyUiMedium, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (supporting != null) {
                    Text(supporting, style = YuyuTheme.typography.caption, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing?.invoke()
            if (showChevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.textMuted)
        }
        if (divider) HorizontalDivider(color = c.border, thickness = 1.dp)
    }
}
