package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.brand.DotFace
import com.twilighty.yuyu.core.designsystem.brand.DotFaceMood
import com.twilighty.yuyu.core.designsystem.brand.DotLoader
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/**
 * Centred face + title + body + optional action. Faces carry meaning:
 * Sleepy for empty, Sad for errors, Thinking for loading, Cheering for success.
 */
@Composable
fun MessageState(
    mood: DotFaceMood,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DotFace(mood = mood, size = 88.dp, interactive = true)
        Text(
            title,
            style = YuyuTheme.typography.h3,
            color = YuyuTheme.colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) {
            Text(body, style = YuyuTheme.typography.bodyUi, color = YuyuTheme.colors.textMuted, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            YuyuButton(actionLabel, onAction, variant = YuyuButtonVariant.Secondary)
        }
        if (secondaryLabel != null && onSecondary != null) {
            YuyuButton(secondaryLabel, onSecondary, variant = YuyuButtonVariant.Quiet)
        }
    }
}

/** Empty list/state (Sleepy face). */
@Composable
fun EmptyState(title: String, modifier: Modifier = Modifier, body: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) =
    MessageState(DotFaceMood.Sleepy, title, modifier, body, actionLabel, onAction)

/** Error state (Sad face) with a retry. */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong",
    onRetry: (() -> Unit)? = null,
) = MessageState(DotFaceMood.Sad, title, modifier, message, if (onRetry != null) "Try again" else null, onRetry)

/** Full-area loading with the dot loader. */
@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = "Loading") {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DotLoader(size = 56.dp, label = label)
    }
}

/** "Coming soon" placeholder used by routes a feature module hasn't built yet. */
@Composable
fun ComingSoon(title: String, modifier: Modifier = Modifier, body: String = "This screen is on its way.") {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        MessageState(DotFaceMood.Thinking, title, body = body)
    }
}
