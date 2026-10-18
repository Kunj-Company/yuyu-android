package com.twilighty.yuyu.feature.checkin.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.twilighty.yuyu.feature.checkin.scanner.ScannerScreen
import com.twilighty.yuyu.feature.checkin.station.OpenStationScreen
import com.twilighty.yuyu.feature.checkin.station.StationScreen
import com.twilighty.yuyu.feature.checkin.station.StationUnlockScreen
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

/** "Open a check-in station": enter or scan a station link. */
@Serializable
data object OpenStationRoute

fun NavController.navigateToScanner(orgSlug: String, eventId: String) = navigate(ScannerRoute(orgSlug, eventId))
fun NavController.navigateToStationUnlock(orgSlug: String, eventSlug: String) = navigate(StationUnlockRoute(orgSlug, eventSlug))
fun NavController.navigateToStation(orgSlug: String, eventSlug: String) = navigate(StationRoute(orgSlug, eventSlug)) {
    popUpTo<StationUnlockRoute> { inclusive = true }
}
fun NavController.navigateToOpenStation() = navigate(OpenStationRoute)

/** Registers the check-in screens (scanner + door station). Owned by `:feature:checkin`. */
fun NavGraphBuilder.checkInGraph(navController: NavController) {
    composable<ScannerRoute> {
        ScannerScreen(onBack = navController::popBackStack)
    }
    stationGraph(navController)
}

/**
 * Door-station screens only. They need no Yuyu account, so `:app` can also
 * register them in the signed-out host (deep links to `/{org}/{event}/check-in`).
 */
fun NavGraphBuilder.stationGraph(navController: NavController) {
    composable<OpenStationRoute> {
        OpenStationScreen(
            onBack = navController::popBackStack,
            onOpen = { org, event -> navController.navigateToStationUnlock(org, event) },
        )
    }
    composable<StationUnlockRoute> { entry ->
        val route = entry.toRoute<StationUnlockRoute>()
        StationUnlockScreen(
            onBack = navController::popBackStack,
            onUnlocked = { navController.navigateToStation(route.orgSlug, route.eventSlug) },
            onChangeStation = {
                navController.navigate(OpenStationRoute) { popUpTo<StationUnlockRoute> { inclusive = true } }
            },
        )
    }
    composable<StationRoute> { entry ->
        val route = entry.toRoute<StationRoute>()
        StationScreen(
            onExit = navController::popBackStack,
            onUnlockAgain = {
                navController.navigate(StationUnlockRoute(route.orgSlug, route.eventSlug)) {
                    popUpTo<StationRoute> { inclusive = true }
                }
            },
        )
    }
}
