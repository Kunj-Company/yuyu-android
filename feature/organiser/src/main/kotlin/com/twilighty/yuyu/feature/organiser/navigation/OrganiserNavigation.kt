package com.twilighty.yuyu.feature.organiser.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.feature.organiser.dayof.DayOfScreen
import com.twilighty.yuyu.feature.organiser.event.EventHomeScreen
import com.twilighty.yuyu.feature.organiser.guests.GuestDetailScreen
import com.twilighty.yuyu.feature.organiser.guests.GuestsScreen
import com.twilighty.yuyu.feature.organiser.guests.GuestsViewModel
import com.twilighty.yuyu.feature.organiser.home.OrganiserHomeScreen
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

private const val KEY_GUEST_CHANGED = "organiser.guestChanged"
private const val KEY_REMOVED_RSVP = "organiser.removedRsvp"
private const val KEY_REMOVED_UNDO = "organiser.removedUndo"
private const val KEY_REMOVED_NAME = "organiser.removedName"

/** Registers every organiser screen. Owned by `:feature:organiser`. */
fun NavGraphBuilder.organiserGraph(navController: NavController, external: OrganiserExternalNavigation) {
    navigation<OrganiserGraph>(startDestination = OrganiserHomeRoute()) {
        composable<OrganiserHomeRoute> { entry ->
            val route = entry.toRoute<OrganiserHomeRoute>()
            OrganiserHomeScreen(
                requestedOrg = route.orgSlug,
                onOpenEvent = { org, event -> navController.navigateToEventHome(org, event) },
            )
        }
        composable<EventHomeRoute> { entry ->
            val route = entry.toRoute<EventHomeRoute>()
            EventHomeScreen(
                orgSlug = route.orgSlug,
                eventId = route.eventId,
                onBack = navController::popBackStack,
                onOpenScanner = { external.openScanner(route.orgSlug, route.eventId) },
                onOpenGuests = { navController.navigateToGuests(route.orgSlug, route.eventId) },
                onOpenEmails = { external.openBroadcastCompose(route.orgSlug, route.eventId) },
                onOpenDayOf = { navController.navigateToDayOf(route.orgSlug, route.eventId) },
            )
        }
        composable<GuestsRoute> { entry ->
            val route = entry.toRoute<GuestsRoute>()
            val viewModel: GuestsViewModel = hiltViewModel()
            val handle = entry.savedStateHandle
            val changed by handle.getStateFlow(KEY_GUEST_CHANGED, false).collectAsStateWithLifecycle()
            val removedUndo by handle.getStateFlow<String?>(KEY_REMOVED_UNDO, null).collectAsStateWithLifecycle()
            LaunchedEffect(changed) {
                if (changed) {
                    handle[KEY_GUEST_CHANGED] = false
                    viewModel.refresh()
                }
            }
            LaunchedEffect(removedUndo) {
                val undoId = removedUndo ?: return@LaunchedEffect
                viewModel.onGuestRemoved(
                    rsvpId = handle.get<String>(KEY_REMOVED_RSVP).orEmpty(),
                    undoId = undoId,
                    guestName = handle.get<String>(KEY_REMOVED_NAME) ?: "guest",
                )
                handle.remove<String>(KEY_REMOVED_RSVP)
                handle.remove<String>(KEY_REMOVED_NAME)
                handle[KEY_REMOVED_UNDO] = null
            }
            GuestsScreen(
                orgSlug = route.orgSlug,
                eventId = route.eventId,
                onBack = navController::popBackStack,
                onOpenGuest = { rsvpId -> navController.navigateToGuestDetail(route.orgSlug, route.eventId, rsvpId) },
                viewModel = viewModel,
            )
        }
        composable<GuestDetailRoute> { entry ->
            val route = entry.toRoute<GuestDetailRoute>()
            GuestDetailScreen(
                orgSlug = route.orgSlug,
                eventId = route.eventId,
                rsvpId = route.rsvpId,
                onBack = navController::popBackStack,
                onUpdated = { navController.previousBackStackEntry?.savedStateHandle?.set(KEY_GUEST_CHANGED, true) },
                onRemoved = { removed ->
                    navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                        handle[KEY_REMOVED_RSVP] = removed.rsvpId
                        handle[KEY_REMOVED_NAME] = removed.guestName
                        handle[KEY_REMOVED_UNDO] = removed.undoId
                    }
                    navController.popBackStack()
                },
            )
        }
        composable<DayOfRoute> { entry ->
            val route = entry.toRoute<DayOfRoute>()
            DayOfScreen(orgSlug = route.orgSlug, eventId = route.eventId, onBack = navController::popBackStack)
        }
    }
}
