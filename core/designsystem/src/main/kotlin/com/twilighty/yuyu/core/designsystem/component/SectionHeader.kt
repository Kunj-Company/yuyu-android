package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/** Overline section header ("UPCOMING", "VENUE") with an optional trailing action. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text.uppercase(),
            style = YuyuTheme.typography.overline,
            color = YuyuTheme.colors.textMuted,
            modifier = Modifier.semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            YuyuButton(actionLabel, onAction, variant = YuyuButtonVariant.Quiet, size = YuyuButtonSize.Small)
        }
    }
}
