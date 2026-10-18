package com.twilighty.yuyu.feature.checkin.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.twilighty.yuyu.feature.checkin.PlaceholderScreen
import kotlinx.serialization.Serializable

/** Organiser scanner (bearer-authenticated, needs the event CHECK_IN permission). */
@Serializable
data class ScannerRoute(val orgSlug: String, val eventId: String)

/** Door-station PIN entry (cookie-based station proof; no Yuyu account needed). */
@Serializable
data class StationUnlockRoute(val orgSlug: String, val eventSlug: String)

/** Unlocked door station (kiosk). */
@Serializable
data class StationRoute(val orgSlug: String, val eventSlug: String)

fun NavController.navigateToScanner(orgSlug: String, eventId: String) = navigate(ScannerRoute(orgSlug, eventId))
fun NavController.navigateToStationUnlock(orgSlug: String, eventSlug: String) = navigate(StationUnlockRoute(orgSlug, eventSlug))
fun NavController.navigateToStation(orgSlug: String, eventSlug: String) = navigate(StationRoute(orgSlug, eventSlug)) {
    popUpTo<StationUnlockRoute> { inclusive = true }
}

/** Registers the check-in screens. Owned by `:feature:checkin`. */
fun NavGraphBuilder.checkInGraph(navController: NavController) {
    composable<ScannerRoute> {
        PlaceholderScreen("Check-in", "Scan tickets or search guests at the door.", onBack = navController::popBackStack)
    }
    composable<StationUnlockRoute> {
        PlaceholderScreen("Door station", "Enter the 8-digit station PIN.", onBack = navController::popBackStack)
    }
    composable<StationRoute> {
        PlaceholderScreen("Door station", "Kiosk check-in.", onBack = navController::popBackStack)
    }
}
