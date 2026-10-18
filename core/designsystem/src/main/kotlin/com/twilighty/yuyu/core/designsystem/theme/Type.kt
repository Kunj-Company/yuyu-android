package com.twilighty.yuyu.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.twilighty.yuyu.core.designsystem.R

/** Display / headings: Bricolage Grotesque 600 and 800 (static instances, opsz 48). */
val BricolageGrotesque = FontFamily(
    Font(R.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
)

/** Body / UI: Figtree 400–700. */
val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

/** Codes, IDs: JetBrains Mono 400/500. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/**
 * The Yuyu type scale (docs/DESIGN.md §2.6), using the **mobile** sizes since
 * every phone is ≤ 640dp. Sizes are in sp so system font scaling works.
 * Tracking never goes tighter than −0.035em.
 */
@Immutable
data class YuyuTypography(
    val displayXl: TextStyle,
    val displayL: TextStyle,
    val displayM: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val h4: TextStyle,
    val bodyL: TextStyle,
    val body: TextStyle,
    val bodyUi: TextStyle,
    val bodyUiMedium: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    val mono: TextStyle,
    /** Button text (Figtree 600). */
    val button: TextStyle,
    /** Chip / badge text (Figtree 600, 13). */
    val chip: TextStyle,
)

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun display(size: Int, lineHeight: Float, tracking: TextUnit, weight: FontWeight = FontWeight.ExtraBold) = TextStyle(
    fontFamily = BricolageGrotesque,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking,
    lineHeightStyle = trim,
)

private fun figtree(size: Int, lineHeight: Float, weight: FontWeight = FontWeight.Normal, tracking: TextUnit = 0.sp) = TextStyle(
    fontFamily = Figtree,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking,
    lineHeightStyle = trim,
)

val DefaultYuyuTypography = YuyuTypography(
    displayXl = display(44, 0.98f, (-0.035).em),
    displayL = display(40, 1.0f, (-0.03).em),
    displayM = display(36, 1.0f, (-0.03).em),
    h1 = display(30, 1.1f, (-0.02).em),
    h2 = display(24, 1.15f, (-0.01).em),
    h3 = display(20, 1.2f, 0.em),
    h4 = display(18, 1.25f, 0.em),
    bodyL = figtree(17, 1.6f),
    body = figtree(16, 1.6f),
    bodyUi = figtree(15, 1.5f),
    bodyUiMedium = figtree(15, 1.5f, FontWeight.Medium),
    label = figtree(14, 1.4f, FontWeight.SemiBold),
    caption = figtree(13, 1.45f),
    overline = figtree(12, 1.3f, FontWeight.Bold, 0.06.em),
    mono = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.5.sp),
    button = figtree(15, 1.3f, FontWeight.SemiBold),
    chip = figtree(13, 1.3f, FontWeight.SemiBold),
)

val LocalYuyuTypography = staticCompositionLocalOf { DefaultYuyuTypography }

/** Maps the Yuyu scale onto Material 3 roles so stock M3 components look right. */
internal fun YuyuTypography.toMaterial(): Typography = Typography(
    displayLarge = displayL,
    displayMedium = displayM,
    displaySmall = h1,
    headlineLarge = h1,
    headlineMedium = h2,
    headlineSmall = h3,
    titleLarge = h3,
    titleMedium = h4,
    titleSmall = label,
    bodyLarge = body,
    bodyMedium = bodyUi,
    bodySmall = caption,
    labelLarge = button,
    labelMedium = label,
    labelSmall = chip,
)
