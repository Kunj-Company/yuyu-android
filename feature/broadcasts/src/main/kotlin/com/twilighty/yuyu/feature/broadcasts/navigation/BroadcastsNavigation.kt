package com.twilighty.yuyu.feature.broadcasts.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.twilighty.yuyu.feature.broadcasts.PlaceholderScreen
import kotlinx.serialization.Serializable

/** Write an email to an event's guests (template, audience, design, send with 5s undo). */
@Serializable
data class BroadcastComposeRoute(val orgSlug: String, val eventId: String)

/**
 * Server-rendered preview of the draft. The draft itself is not a route
 * argument: share it with a ViewModel scoped to the Compose entry
 * (`navController.getBackStackEntry<BroadcastComposeRoute>()`).
 */
@Serializable
data class BroadcastPreviewRoute(val orgSlug: String, val eventId: String)

/** Sent emails for the event. */
@Serializable
data class BroadcastHistoryRoute(val orgSlug: String, val eventId: String)

fun NavController.navigateToBroadcastCompose(orgSlug: String, eventId: String) = navigate(BroadcastComposeRoute(orgSlug, eventId))
fun NavController.navigateToBroadcastPreview(orgSlug: String, eventId: String) = navigate(BroadcastPreviewRoute(orgSlug, eventId))
fun NavController.navigateToBroadcastHistory(orgSlug: String, eventId: String) = navigate(BroadcastHistoryRoute(orgSlug, eventId))

/** Registers the email screens. Owned by `:feature:broadcasts`. */
fun NavGraphBuilder.broadcastsGraph(navController: NavController) {
    composable<BroadcastComposeRoute> { entry ->
        val route = entry.toRoute<BroadcastComposeRoute>()
        PlaceholderScreen(
            title = "Email guests",
            detail = "Pick a template, an audience and a design.",
            onBack = navController::popBackStack,
            actions = listOf(
                "Preview" to { navController.navigateToBroadcastPreview(route.orgSlug, route.eventId) },
                "Sent emails" to { navController.navigateToBroadcastHistory(route.orgSlug, route.eventId) },
            ),
        )
    }
    composable<BroadcastPreviewRoute> {
        PlaceholderScreen("Preview", "How the email will look.", onBack = navController::popBackStack)
    }
    composable<BroadcastHistoryRoute> {
        PlaceholderScreen("Sent emails", "Everything sent for this event.", onBack = navController::popBackStack)
    }
}
