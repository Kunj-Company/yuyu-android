package com.twilighty.yuyu.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs

private typealias Scope = AnimatedContentTransitionScope<NavBackStackEntry>

/**
 * Screen transitions for a NavHost (Material shared-axis style):
 * - pushing a screen slides it in from the end edge over a slightly receding
 *   previous screen; popping (including predictive back) reverses that;
 * - switching between bottom-bar tabs fades through, without sideways motion,
 *   because tabs are siblings rather than a deeper level;
 * - with reduced motion, everything becomes a short cross-fade.
 */
class YuyuNavTransitions(
    private val reduced: Boolean,
    private val isTopLevel: (NavDestination) -> Boolean = { false },
) {
    private fun Scope.isTabSwitch() = isTopLevel(initialState.destination) && isTopLevel(targetState.destination)

    val enter: Scope.() -> EnterTransition = {
        when {
            reduced -> fadeIn(tween(FADE_MS))
            isTabSwitch() -> fadeIn(YuyuSpecs.standard(210, delayMillis = 90)) + scaleIn(YuyuSpecs.standard(210, delayMillis = 90), initialScale = 0.96f)
            else -> slideInHorizontally(YuyuSpecs.standard(PUSH_MS)) { it / 6 } + fadeIn(YuyuSpecs.standard(220, delayMillis = 40))
        }
    }

    val exit: Scope.() -> ExitTransition = {
        when {
            reduced -> fadeOut(tween(FADE_MS))
            isTabSwitch() -> fadeOut(tween(90))
            else -> slideOutHorizontally(YuyuSpecs.standard(PUSH_MS)) { -it / 10 } + fadeOut(YuyuSpecs.standard(PUSH_MS))
        }
    }

    val popEnter: Scope.() -> EnterTransition = {
        when {
            reduced -> fadeIn(tween(FADE_MS))
            isTabSwitch() -> fadeIn(YuyuSpecs.standard(210, delayMillis = 90))
            else -> slideInHorizontally(YuyuSpecs.standard(PUSH_MS)) { -it / 10 } + fadeIn(YuyuSpecs.standard(PUSH_MS))
        }
    }

    val popExit: Scope.() -> ExitTransition = {
        when {
            reduced -> fadeOut(tween(FADE_MS))
            isTabSwitch() -> fadeOut(tween(90))
            else -> slideOutHorizontally(YuyuSpecs.standard(PUSH_MS)) { it / 6 } + fadeOut(YuyuSpecs.standard(200))
        }
    }

    private companion object {
        const val PUSH_MS = 320
        const val FADE_MS = 150
    }
}
