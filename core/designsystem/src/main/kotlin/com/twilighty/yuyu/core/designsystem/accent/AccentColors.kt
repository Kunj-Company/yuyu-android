package com.twilighty.yuyu.core.designsystem.accent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/** Contrast-guarded event accent colours for the current mode. */
@Immutable
data class AccentColors(
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val accentText: Color,
) {
    companion object {
        fun from(tokens: AccentMath.Tokens) = AccentColors(
            accent = tokens.accent.toColor(),
            accentSoft = tokens.accentSoft.toColor(),
            onAccent = tokens.onAccent.toColor(),
            accentText = tokens.accentText.toColor(),
        )
    }
}

internal fun String.toColor(): Color = Color(0xFF000000 or removePrefix("#").toLong(16))

/**
 * Outside an [EventAccentScope] the accent is ink (DESIGN: an organisation
 * without an accent falls back to ink). Yuyu chrome never reads it.
 */
val LocalAccentColors = staticCompositionLocalOf {
    AccentColors.from(AccentMath.tokens(AccentMath.INK_ACCENT, dark = false))
}

/** Derives accent tokens for [hex] (null/invalid → Ember) in the current light/dark mode. */
@Composable
fun rememberAccentColors(hex: String?): AccentColors {
    val dark = YuyuTheme.colors.isDark
    return remember(hex, dark) { AccentColors.from(AccentMath.tokens(hex, dark)) }
}

/**
 * Applies an event's accent to [content]. Use only on event-facing screens
 * (event detail, register, ticket, feedback) — never on Yuyu chrome.
 */
@Composable
fun EventAccentScope(accentHex: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccentColors provides rememberAccentColors(accentHex), content = content)
}
