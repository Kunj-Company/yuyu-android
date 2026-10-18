package com.twilighty.yuyu.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4dp-based spacing; only these values (docs/DESIGN.md §2.7). */
object YuyuSpacing {
    val s2: Dp = 2.dp
    val s4: Dp = 4.dp
    val s6: Dp = 6.dp
    val s8: Dp = 8.dp
    val s10: Dp = 10.dp
    val s12: Dp = 12.dp
    val s14: Dp = 14.dp
    val s16: Dp = 16.dp
    val s20: Dp = 20.dp
    val s24: Dp = 24.dp
    val s28: Dp = 28.dp
    val s32: Dp = 32.dp
    val s40: Dp = 40.dp
    val s48: Dp = 48.dp
    val s56: Dp = 56.dp
    val s64: Dp = 64.dp
    val s72: Dp = 72.dp
    val s80: Dp = 80.dp

    /** Page side gutter on phones. */
    val gutter: Dp = s16
    /** Gap between form fields. */
    val fieldGap: Dp = s14
    /** Card padding (app). */
    val cardPadding: Dp = s16
    /** Gap between dashboard sections. */
    val sectionGap: Dp = s24
    /** Gap between event-page sections. */
    val eventSectionGap: Dp = s40
    /** Icon + label, chip gaps. */
    val inlineGap: Dp = s8
}

/** Corner radii (docs/DESIGN.md §2.8). All buttons, chips and avatars are pills. */
object YuyuRadius {
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp

    val small = RoundedCornerShape(sm)
    val medium = RoundedCornerShape(md)
    val large = RoundedCornerShape(lg)
    val extraLarge = RoundedCornerShape(xl)
    val extraExtraLarge = RoundedCornerShape(xxl)
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * Elevation (docs/DESIGN.md §2.9): flat by default — cards use a 1dp border.
 * Shadows are approximated with Compose `shadow()` elevations tinted with
 * `YuyuColors.shadow`; use these sparingly.
 */
object YuyuElevation {
    val flat: Dp = 0.dp
    /** shadow-raised: auth card, registration card. */
    val raised: Dp = 6.dp
    /** shadow-float: menus, dialogs, sheets. */
    val float: Dp = 12.dp
    /** shadow-ticket: ticket stub. */
    val ticket: Dp = 10.dp
    /** Segmented-control selected pill. */
    val segment: Dp = 1.dp
    /** Card border width. */
    val borderWidth: Dp = 1.dp
    /** Danger / selected emphasis border. */
    val strongBorderWidth: Dp = 1.5.dp
}

/** Motion (docs/DESIGN.md §2.10). Respect reduced motion via `rememberReducedMotion()`. */
object YuyuMotion {
    const val PRESS = 120
    const val HOVER = 180
    const val PANEL = 240
    const val SUCCESS = 360
    val Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    /** Gravity exits (e.g. the ticket stub dropping). */
    val GravityEasing = CubicBezierEasing(0.45f, 0f, 0.85f, 0.35f)
}

/** Minimum touch target on phones (DESIGN says 44; Android a11y requires 48). */
val MinTouchTarget: Dp = 48.dp
