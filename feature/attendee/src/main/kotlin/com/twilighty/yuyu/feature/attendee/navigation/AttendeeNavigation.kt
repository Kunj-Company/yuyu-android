package com.twilighty.yuyu.feature.attendee.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.twilighty.yuyu.feature.attendee.PlaceholderScreen
import kotlinx.serialization.Serializable

/** The attendee nested graph (bottom-nav "Tickets" tab). */
@Serializable
data object AttendeeGraph

/** Home: my upcoming/past registrations + a "Discover" entry. */
@Serializable
data object MyTicketsRoute

/** Discover / search public events. [query] pre-fills the search box. */
@Serializable
data class DiscoverRoute(val query: String? = null)

/** Public event page. Also the target of `/{org}/{event}` links and resolved short links. */
@Serializable
data class EventDetailRoute(val orgSlug: String, val eventSlug: String)

/** Registration form for an event. */
@Serializable
data class RegisterRoute(val orgSlug: String, val eventSlug: String)

/**
 * The live ticket for [token] (from `/ticket/{token}` links, a registration or a
 * fresh RSVP). Works offline from the encrypted ticket cache.
 */
@Serializable
data class TicketRoute(val token: String) {
    // Ticket tokens are secrets: keep them out of logs and crash reports.
    override fun toString(): String = "TicketRoute(token=██)"
}

/** Feedback form (`/{org}/{event}/feedback`). */
@Serializable
data class FeedbackRoute(val orgSlug: String, val eventSlug: String)

fun NavController.navigateToDiscover(query: String? = null, builder: NavOptionsBuilder.() -> Unit = {}) =
    navigate(DiscoverRoute(query), builder)

fun NavController.navigateToEventDetail(orgSlug: String, eventSlug: String, builder: NavOptionsBuilder.() -> Unit = {}) =
    navigate(EventDetailRoute(orgSlug, eventSlug), builder)

fun NavController.navigateToRegister(orgSlug: String, eventSlug: String) = navigate(RegisterRoute(orgSlug, eventSlug))

fun NavController.navigateToTicket(token: String, builder: NavOptionsBuilder.() -> Unit = {}) =
    navigate(TicketRoute(token), builder)

fun NavController.navigateToFeedback(orgSlug: String, eventSlug: String, builder: NavOptionsBuilder.() -> Unit = {}) =
    navigate(FeedbackRoute(orgSlug, eventSlug), builder)

/**
 * Registers every attendee screen. Owned by `:feature:attendee`: replace the
 * placeholders with real screens here without touching `:app`.
 */
fun NavGraphBuilder.attendeeGraph(navController: NavController) {
    navigation<AttendeeGraph>(startDestination = MyTicketsRoute) {
        composable<MyTicketsRoute> {
            PlaceholderScreen(
                title = "Tickets",
                detail = "Your upcoming and past registrations will appear here.",
                actions = listOf("Discover events" to { navController.navigateToDiscover() }),
            )
        }
        composable<DiscoverRoute> {
            PlaceholderScreen("Discover", "Search and browse public events.", onBack = navController::popBackStack)
        }
        composable<EventDetailRoute> { entry ->
            val route = entry.toRoute<EventDetailRoute>()
            PlaceholderScreen(
                title = "Event",
                detail = "Event page for ${route.orgSlug}/${route.eventSlug}.",
                onBack = navController::popBackStack,
                actions = listOf(
                    "Register" to { navController.navigateToRegister(route.orgSlug, route.eventSlug) },
                    "Feedback" to { navController.navigateToFeedback(route.orgSlug, route.eventSlug) },
                ),
            )
        }
        composable<RegisterRoute> {
            PlaceholderScreen("Register", "The registration form will be here.", onBack = navController::popBackStack)
        }
        composable<TicketRoute> {
            PlaceholderScreen("Ticket", "Your live ticket will be here.", onBack = navController::popBackStack)
        }
        composable<FeedbackRoute> {
            PlaceholderScreen("Feedback", "The feedback form will be here.", onBack = navController::popBackStack)
        }
    }
}
