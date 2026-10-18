package com.twilighty.yuyu.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every Yuyu colour token (docs/DESIGN.md §2, app/styles/tokens.css) for one
 * mode. Components read these via `YuyuTheme.colors`, never hard-coded hex.
 */
@Immutable
data class YuyuColors(
    val isDark: Boolean,
    // Neutrals
    val bgCanvas: Color,
    val bgSubtle: Color,
    val bgMuted: Color,
    val bgEmphasis: Color,
    val bgTrack: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    // Yuyu primary ("ink"), inverts in dark
    val primary: Color,
    val onPrimary: Color,
    val inverseSurface: Color,
    val onInverse: Color,
    // Brand (dot sign) — Yuyu's own surfaces only
    val brandMarigold: Color,
    val brandMarigoldDeep: Color,
    val brandMarigoldSmall: Color,
    val brandDotBase: Color,
    val brandDotOff: Color,
    val highlightPeach: Color,
    val tintPeach: Color,
    // Status: soft bg + strong fg (+ solid for kiosk panels)
    val successBg: Color,
    val successFg: Color,
    val successSolid: Color,
    val warningBg: Color,
    val warningFg: Color,
    val warningSolid: Color,
    val dangerBg: Color,
    val dangerFg: Color,
    val dangerSolid: Color,
    val dangerBorder: Color,
    // Misc
    val overlay: Color,
    val shadow: Color,
)

val LightYuyuColors = YuyuColors(
    isDark = false,
    bgCanvas = Color(0xFFFFFFFF),
    bgSubtle = Color(0xFFFAFAF9),
    bgMuted = Color(0xFFF4F4F2),
    bgEmphasis = Color(0xFFEFEEEA),
    bgTrack = Color(0xFFF0EFEB),
    border = Color(0xFFE7E6E2),
    borderStrong = Color(0xFFD4D3CE),
    text = Color(0xFF18181B),
    textSecondary = Color(0xFF3F3F46),
    textMuted = Color(0xFF5F5E5A),
    textDisabled = Color(0xFFA1A09A),
    primary = Color(0xFF18181B),
    onPrimary = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF18181B),
    onInverse = Color(0xFFFFFFFF),
    brandMarigold = Color(0xFFFFB547),
    brandMarigoldDeep = Color(0xFFE8930C),
    brandMarigoldSmall = Color(0xFFE8930C),
    brandDotBase = Color(0xFF18181B),
    brandDotOff = Color(0xFFE7E6E2),
    highlightPeach = Color(0xFFFDE7C8),
    tintPeach = Color(0xFFFFF4E5),
    successBg = Color(0xFFDCFCE7),
    successFg = Color(0xFF166534),
    successSolid = Color(0xFF15803D),
    warningBg = Color(0xFFFEF3C7),
    warningFg = Color(0xFF92400E),
    warningSolid = Color(0xFFF59E0B),
    dangerBg = Color(0xFFFEE2E2),
    dangerFg = Color(0xFFB91C1C),
    dangerSolid = Color(0xFFB91C1C),
    dangerBorder = Color(0xFFFCA5A5),
    overlay = Color(0x7A111113), // rgba(17,17,19,.48)
    shadow = Color(0xFF18181B),
)

val DarkYuyuColors = YuyuColors(
    isDark = true,
    bgCanvas = Color(0xFF111113),
    bgSubtle = Color(0xFF18181B),
    bgMuted = Color(0xFF232326),
    bgEmphasis = Color(0xFF2A2A2E),
    bgTrack = Color(0xFF2A2A2E),
    border = Color(0xFF2A2A2E),
    borderStrong = Color(0xFF3F3F46),
    text = Color(0xFFF4F4F5),
    textSecondary = Color(0xFFD4D4D8),
    textMuted = Color(0xFFA1A1AA),
    textDisabled = Color(0xFF71717A),
    primary = Color(0xFFF4F4F5),
    onPrimary = Color(0xFF18181B),
    inverseSurface = Color(0xFF1C1C1F),
    onInverse = Color(0xFFF4F4F5),
    brandMarigold = Color(0xFFFFB547),
    brandMarigoldDeep = Color(0xFFE8930C),
    brandMarigoldSmall = Color(0xFFFFB547),
    brandDotBase = Color(0xFFF4F4F5),
    brandDotOff = Color(0xFF2A2A2E),
    highlightPeach = Color(0xFF4A3418),
    tintPeach = Color(0xFF2A1F10),
    successBg = Color(0xFF13301F),
    successFg = Color(0xFF86EFAC),
    successSolid = Color(0xFF15803D),
    warningBg = Color(0xFF33270B),
    warningFg = Color(0xFFFCD34D),
    warningSolid = Color(0xFFF59E0B),
    dangerBg = Color(0xFF3A1414),
    dangerFg = Color(0xFFF87171),
    dangerSolid = Color(0xFFB91C1C),
    dangerBorder = Color(0xFF7F1D1D),
    overlay = Color(0x7A111113),
    shadow = Color(0xFF000000),
)

val LocalYuyuColors = staticCompositionLocalOf { LightYuyuColors }
