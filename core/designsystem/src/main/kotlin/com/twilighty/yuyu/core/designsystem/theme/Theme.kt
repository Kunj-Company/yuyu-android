package com.twilighty.yuyu.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.twilighty.yuyu.core.common.AppearanceMode
import com.twilighty.yuyu.core.designsystem.accent.AccentColors
import com.twilighty.yuyu.core.designsystem.accent.AccentMath
import com.twilighty.yuyu.core.designsystem.accent.LocalAccentColors
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion

/**
 * Yuyu theme. Provides [YuyuColors], [YuyuTypography] and the default (ink)
 * accent, and maps them onto Material 3 so stock components match.
 */
@Composable
fun YuyuTheme(
    appearance: AppearanceMode = AppearanceMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (appearance) {
        AppearanceMode.System -> isSystemInDarkTheme()
        AppearanceMode.Light -> false
        AppearanceMode.Dark -> true
    }
    YuyuTheme(darkTheme = dark, content = content)
}

@Composable
fun YuyuTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkYuyuColors else LightYuyuColors
    val typography = DefaultYuyuTypography
    CompositionLocalProvider(
        LocalYuyuColors provides colors,
        LocalYuyuTypography provides typography,
        LocalAccentColors provides AccentColors.from(AccentMath.tokens(AccentMath.INK_ACCENT, darkTheme)),
        LocalReducedMotion provides rememberReducedMotion(),
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = typography.toMaterial(),
            shapes = Shapes(
                extraSmall = YuyuRadius.small,
                small = YuyuRadius.medium,
                medium = YuyuRadius.large,
                large = YuyuRadius.extraLarge,
                extraLarge = YuyuRadius.extraExtraLarge,
            ),
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.text, content = content)
        }
    }
}

/** Accessors: `YuyuTheme.colors.text`, `YuyuTheme.typography.h1`, `YuyuTheme.accent.accent`. */
object YuyuTheme {
    val colors: YuyuColors
        @Composable @ReadOnlyComposable get() = LocalYuyuColors.current
    val typography: YuyuTypography
        @Composable @ReadOnlyComposable get() = LocalYuyuTypography.current
    val accent: AccentColors
        @Composable @ReadOnlyComposable get() = LocalAccentColors.current
}

internal fun YuyuColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = bgEmphasis,
        onPrimaryContainer = text,
        inversePrimary = onPrimary,
        secondary = textSecondary,
        onSecondary = bgCanvas,
        secondaryContainer = bgEmphasis,
        onSecondaryContainer = text,
        tertiary = brandMarigoldDeep,
        onTertiary = text,
        tertiaryContainer = tintPeach,
        onTertiaryContainer = text,
        background = bgCanvas,
        onBackground = text,
        surface = bgCanvas,
        onSurface = text,
        surfaceVariant = bgMuted,
        onSurfaceVariant = textMuted,
        surfaceTint = bgCanvas,
        inverseSurface = inverseSurface,
        inverseOnSurface = onInverse,
        error = dangerFg,
        onError = bgCanvas,
        errorContainer = dangerBg,
        onErrorContainer = dangerFg,
        outline = borderStrong,
        outlineVariant = border,
        scrim = overlay,
        surfaceBright = bgCanvas,
        surfaceDim = bgSubtle,
        surfaceContainerLowest = bgCanvas,
        surfaceContainerLow = bgSubtle,
        surfaceContainer = bgSubtle,
        surfaceContainerHigh = bgMuted,
        surfaceContainerHighest = bgEmphasis,
    )
}
