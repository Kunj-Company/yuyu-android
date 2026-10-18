package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/** 1–2 letter initials for a name or email. */
fun initialsOf(name: String?): String {
    val words = name.orEmpty().trim().substringBefore('@').split(Regex("[\\s._-]+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}

/**
 * Circular avatar: the photo when it loads, otherwise initials (700) on a tint
 * (bg-emphasis for users; pass the accent-soft on event pages). Never shows a
 * broken image.
 */
@Composable
fun YuyuAvatar(
    name: String?,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    size: Dp = 40.dp,
    background: Color = YuyuTheme.colors.bgEmphasis,
    contentColor: Color = YuyuTheme.colors.text,
) {
    var failed by remember(imageUrl) { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .clip(YuyuRadius.pill)
            .background(background)
            .clearAndSetSemantics { contentDescription = name ?: "Avatar" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initialsOf(name),
            style = YuyuTheme.typography.label.copy(fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp),
            color = contentColor,
        )
        if (!imageUrl.isNullOrBlank() && !failed) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.size(size),
            )
        }
    }
}
