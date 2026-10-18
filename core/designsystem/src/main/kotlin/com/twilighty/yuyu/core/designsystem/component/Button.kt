package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twilighty.yuyu.core.designsystem.theme.YuyuElevation
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/** DESIGN §4.1. One `Primary` per view; destructive actions are never primary. */
enum class YuyuButtonVariant { Primary, Accent, Secondary, Quiet, Danger, DangerQuiet, Inverse }

enum class YuyuButtonSize(val height: Dp, val horizontal: Dp, val fontSize: Int) {
    Small(36.dp, 14.dp, 14),
    Medium(44.dp, 18.dp, 15),
    Large(52.dp, 24.dp, 17),
}

private data class ButtonColors(val container: Color, val content: Color, val border: BorderStroke?)

@Composable
private fun buttonColors(variant: YuyuButtonVariant, enabled: Boolean): ButtonColors {
    val c = YuyuTheme.colors
    val a = YuyuTheme.accent
    if (!enabled) {
        return when (variant) {
            YuyuButtonVariant.Primary, YuyuButtonVariant.Accent, YuyuButtonVariant.Inverse ->
                ButtonColors(c.bgEmphasis, c.textMuted, null)
            YuyuButtonVariant.Secondary -> ButtonColors(c.bgCanvas, c.textMuted, BorderStroke(1.dp, c.borderStrong))
            YuyuButtonVariant.Danger -> ButtonColors(c.bgCanvas, c.textMuted, BorderStroke(1.5.dp, c.borderStrong))
            else -> ButtonColors(Color.Transparent, c.textMuted, null)
        }
    }
    return when (variant) {
        YuyuButtonVariant.Primary -> ButtonColors(c.primary, c.onPrimary, null)
        YuyuButtonVariant.Accent -> ButtonColors(a.accent, a.onAccent, null)
        YuyuButtonVariant.Secondary -> ButtonColors(c.bgCanvas, c.text, BorderStroke(YuyuElevation.borderWidth, c.borderStrong))
        YuyuButtonVariant.Quiet -> ButtonColors(Color.Transparent, c.textSecondary, null)
        YuyuButtonVariant.Danger -> ButtonColors(c.bgCanvas, c.dangerFg, BorderStroke(YuyuElevation.strongBorderWidth, c.dangerFg))
        YuyuButtonVariant.DangerQuiet -> ButtonColors(Color.Transparent, c.dangerFg, null)
        YuyuButtonVariant.Inverse -> ButtonColors(c.onInverse, c.inverseSurface, null)
    }
}

/**
 * Pill button. While [loading] the width is kept, the label swaps to a spinner
 * + [loadingText] and clicks are ignored. Touch target is at least 48dp.
 */
@Composable
fun YuyuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: YuyuButtonVariant = YuyuButtonVariant.Primary,
    size: YuyuButtonSize = YuyuButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    fullWidth: Boolean = false,
) {
    val colors = buttonColors(variant, enabled)
    val quiet = variant == YuyuButtonVariant.Quiet || variant == YuyuButtonVariant.DangerQuiet
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = YuyuRadius.pill
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .scale(if (pressed && enabled && !loading) 0.98f else 1f)
            .defaultMinSize(minHeight = size.height)
            .clip(shape)
            .background(colors.container, shape)
            .then(colors.border?.let { Modifier.border(it, shape) } ?: Modifier)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(color = colors.content),
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { if (loading) stateDescription = loadingText ?: "Loading" }
            .padding(PaddingValues(horizontal = if (quiet) 8.dp else size.horizontal, vertical = 6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), color = colors.content, strokeWidth = 2.dp)
            } else if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = colors.content, modifier = Modifier.size(18.dp))
            }
            Text(
                text = if (loading) loadingText ?: text else text,
                style = YuyuTheme.typography.button.copy(
                    fontSize = size.fontSize.sp,
                    textDecoration = if (quiet && pressed) TextDecoration.Underline else null,
                ),
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!loading && trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, tint = colors.content, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Circular icon-only button with a required [contentDescription]. */
@Composable
fun YuyuIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = YuyuTheme.colors.text,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(YuyuRadius.pill)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) tint else YuyuTheme.colors.textDisabled, modifier = Modifier.size(22.dp))
    }
}
