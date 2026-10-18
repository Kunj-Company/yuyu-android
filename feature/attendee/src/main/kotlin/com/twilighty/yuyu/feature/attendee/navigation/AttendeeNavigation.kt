package com.twilighty.yuyu.feature.attendee.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.twilighty.yuyu.feature.attendee.discover.DiscoverRoute as DiscoverScreenRoute
import com.twilighty.yuyu.feature.attendee.event.EventDetailRoute as EventDetailScreenRoute
import com.twilighty.yuyu.feature.attendee.feedback.FeedbackRoute as FeedbackScreenRoute
import com.twilighty.yuyu.feature.attendee.register.RegisterRoute as RegisterScreenRoute
import com.twilighty.yuyu.feature.attendee.ticket.TicketRoute as TicketScreenRoute
import com.twilighty.yuyu.feature.attendee.tickets.MyTicketsRoute as MyTicketsScreenRoute
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

/** Registers every attendee screen. Owned by `:feature:attendee`. */
fun NavGraphBuilder.attendeeGraph(navController: NavController) {
    navigation<AttendeeGraph>(startDestination = MyTicketsRoute) {
        composable<MyTicketsRoute> {
            MyTicketsScreenRoute(
                onOpenTicket = { navController.navigateToTicket(it) { launchSingleTop = true } },
                onOpenEvent = { org, event -> navController.navigateToEventDetail(org, event) },
                onDiscover = { navController.navigateToDiscover() },
            )
        }
        composable<DiscoverRoute> {
            DiscoverScreenRoute(
                onBack = navController::popBackStack,
                onOpenEvent = { org, event -> navController.navigateToEventDetail(org, event) },
            )
        }
        composable<EventDetailRoute> { entry ->
            val route = entry.toRoute<EventDetailRoute>()
            EventDetailScreenRoute(
                onBack = navController::popBackStack,
                onRegister = { navController.navigateToRegister(route.orgSlug, route.eventSlug) },
                onOpenTicket = { navController.navigateToTicket(it) { launchSingleTop = true } },
                onFeedback = { navController.navigateToFeedback(route.orgSlug, route.eventSlug) },
            )
        }
        composable<RegisterRoute> {
            RegisterScreenRoute(
                onBack = navController::popBackStack,
                onOpenTicket = { token ->
                    // Replace the form with the ticket so Back returns to the event page.
                    navController.navigateToTicket(token) {
                        popUpTo<RegisterRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<TicketRoute> {
            TicketScreenRoute(
                onBack = navController::popBackStack,
                onOpenEvent = { org, event -> navController.navigateToEventDetail(org, event) { launchSingleTop = true } },
            )
        }
        composable<FeedbackRoute> {
            FeedbackScreenRoute(onBack = navController::popBackStack)
        }
    }
}
