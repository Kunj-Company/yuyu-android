package com.twilighty.yuyu.core.designsystem.brand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.twilighty.yuyu.core.designsystem.theme.BricolageGrotesque
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

enum class DotLockupLayout { Horizontal, Stacked }
enum class DotLockupSize(val mark: Int, val text: Int, val gap: Int) { Small(36, 26, 10), Medium(36, 24, 10), Large(60, 54, 16) }

/** Mark + lowercase "yuyu" in Bricolage Grotesque 800, tracking −3%, in ink. */
@Composable
fun DotLockup(
    modifier: Modifier = Modifier,
    layout: DotLockupLayout = DotLockupLayout.Horizontal,
    size: DotLockupSize = DotLockupSize.Small,
    label: String? = "Yuyu",
) {
    val (mark, text, gap) = if (layout == DotLockupLayout.Stacked) Triple(72, 38, 14) else Triple(size.mark, size.text, size.gap)
    val semantics = Modifier.clearAndSetSemantics { if (label != null) contentDescription = label }
    val wordmark: @Composable () -> Unit = {
        Text(
            "yuyu",
            style = TextStyle(
                fontFamily = BricolageGrotesque,
                fontWeight = FontWeight.ExtraBold,
                fontSize = text.sp,
                lineHeight = text.sp,
                letterSpacing = (-0.03).em,
                color = YuyuTheme.colors.text,
            ),
        )
    }
    if (layout == DotLockupLayout.Stacked) {
        Column(modifier.then(semantics), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap.dp)) {
            DotMark(size = mark.dp, label = null)
            wordmark()
        }
    } else {
        Row(modifier.then(semantics), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
            DotMark(size = mark.dp, label = null)
            wordmark()
        }
    }
}
