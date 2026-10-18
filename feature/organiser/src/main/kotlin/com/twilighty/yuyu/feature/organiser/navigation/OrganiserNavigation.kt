package com.twilighty.yuyu.feature.organiser.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.twilighty.yuyu.feature.organiser.PlaceholderScreen
import kotlinx.serialization.Serializable

/** The organiser nested graph (bottom-nav "Organise" tab). */
@Serializable
data object OrganiserGraph

/** Org switcher + the selected organisation's events. [orgSlug] null = last used / first. */
@Serializable
data class OrganiserHomeRoute(val orgSlug: String? = null)

/** One event's day-of hub: counts, quick actions (check-in, guests, emails, day-of). */
@Serializable
data class EventHomeRoute(val orgSlug: String, val eventId: String)

@Serializable
data class GuestsRoute(val orgSlug: String, val eventId: String)

@Serializable
data class GuestDetailRoute(val orgSlug: String, val eventId: String, val rsvpId: String)

/** Live programme delays, feedback open/close, close sign-ups. */
@Serializable
data class DayOfRoute(val orgSlug: String, val eventId: String)

/**
 * Cross-feature exits, wired by `:app` so feature modules never depend on each other.
 * Call these instead of navigating to another module's routes directly.
 */
data class OrganiserExternalNavigation(
    val openScanner: (orgSlug: String, eventId: String) -> Unit,
    val openStation: (orgSlug: String, eventSlug: String) -> Unit,
    val openBroadcastCompose: (orgSlug: String, eventId: String) -> Unit,
    val openBroadcastHistory: (orgSlug: String, eventId: String) -> Unit,
    val openPublicEvent: (orgSlug: String, eventSlug: String) -> Unit,
)

fun NavController.navigateToEventHome(orgSlug: String, eventId: String) = navigate(EventHomeRoute(orgSlug, eventId))
fun NavController.navigateToGuests(orgSlug: String, eventId: String) = navigate(GuestsRoute(orgSlug, eventId))
fun NavController.navigateToGuestDetail(orgSlug: String, eventId: String, rsvpId: String) =
    navigate(GuestDetailRoute(orgSlug, eventId, rsvpId))
fun NavController.navigateToDayOf(orgSlug: String, eventId: String) = navigate(DayOfRoute(orgSlug, eventId))

/** Registers every organiser screen. Owned by `:feature:organiser`. */
fun NavGraphBuilder.organiserGraph(navController: NavController, external: OrganiserExternalNavigation) {
    navigation<OrganiserGraph>(startDestination = OrganiserHomeRoute()) {
        composable<OrganiserHomeRoute> {
            PlaceholderScreen(
                title = "Organise",
                detail = "Switch organisation and see your events.",
            )
        }
        composable<EventHomeRoute> { entry ->
            val route = entry.toRoute<EventHomeRoute>()
            PlaceholderScreen(
                title = "Event",
                detail = "Day-of hub for this event.",
                onBack = navController::popBackStack,
                actions = listOf(
                    "Guests" to { navController.navigateToGuests(route.orgSlug, route.eventId) },
                    "Scan tickets" to { external.openScanner(route.orgSlug, route.eventId) },
                    "Emails" to { external.openBroadcastCompose(route.orgSlug, route.eventId) },
                    "Day-of controls" to { navController.navigateToDayOf(route.orgSlug, route.eventId) },
                ),
            )
        }
        composable<GuestsRoute> {
            PlaceholderScreen("Guests", "Search, approve and manage guests.", onBack = navController::popBackStack)
        }
        composable<GuestDetailRoute> {
            PlaceholderScreen("Guest", "One guest's details and answers.", onBack = navController::popBackStack)
        }
        composable<DayOfRoute> {
            PlaceholderScreen("Day-of", "Running late, feedback and sign-ups.", onBack = navController::popBackStack)
        }
    }
}
