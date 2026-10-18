package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/** Stat tile (DESIGN §4.7): label over a big Bricolage number, optional progress and hint. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    hint: String? = null,
    needsAttention: Boolean = false,
) {
    YuyuCard(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        tone = if (needsAttention) YuyuCardTone.Tint else YuyuCardTone.Default,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = YuyuTheme.typography.label.copy(fontSize = 14.sp), color = YuyuTheme.colors.textMuted)
            Text(value, style = YuyuTheme.typography.h1.copy(fontSize = 32.sp, lineHeight = 34.sp), color = YuyuTheme.colors.text)
            if (progress != null) YuyuProgressBar(progress)
            if (hint != null) Text(hint, style = YuyuTheme.typography.caption, color = YuyuTheme.colors.textMuted)
        }
    }
}
