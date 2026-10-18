package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/**
 * The signature date block (DESIGN §4.6): month band (white on the event
 * accent, or neutral for drafts) over the day number in Bricolage 800.
 * Place inside an [com.twilighty.yuyu.core.designsystem.accent.EventAccentScope].
 */
@Composable
fun DateBlock(
    month: String,
    day: String,
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    draft: Boolean = false,
    accessibleDate: String? = null,
) {
    val c = YuyuTheme.colors
    val a = YuyuTheme.accent
    Column(
        modifier
            .width(width)
            .clip(YuyuRadius.medium)
            .border(1.dp, c.border, YuyuRadius.medium)
            .background(c.bgCanvas)
            .clearAndSetSemantics { contentDescription = accessibleDate ?: "$day $month" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(if (draft) c.textDisabled else a.accent)
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                month.uppercase(),
                style = YuyuTheme.typography.overline.copy(fontSize = 11.sp, letterSpacing = 0.08.em, fontWeight = FontWeight.Bold),
                color = if (draft) c.bgCanvas else a.onAccent,
            )
        }
        Text(
            day,
            style = YuyuTheme.typography.h2.copy(fontSize = (width.value * 0.42f).sp, lineHeight = (width.value * 0.5f).sp),
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}
