package com.twilighty.yuyu.feature.broadcasts.navigation

import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.twilighty.yuyu.feature.broadcasts.compose.BroadcastComposeScreen
import com.twilighty.yuyu.feature.broadcasts.compose.BroadcastComposeViewModel
import com.twilighty.yuyu.feature.broadcasts.history.BroadcastHistoryScreen
import com.twilighty.yuyu.feature.broadcasts.history.BroadcastHistoryViewModel
import com.twilighty.yuyu.feature.broadcasts.preview.BroadcastPreviewScreen
import com.twilighty.yuyu.feature.broadcasts.preview.BroadcastPreviewUnavailable
import com.twilighty.yuyu.feature.broadcasts.preview.BroadcastPreviewViewModel
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

/** The Compose entry for this event below the current one, if any (Preview/History share its ViewModel). */
private fun NavController.composeEntryFor(orgSlug: String, eventId: String): NavBackStackEntry? =
    runCatching { getBackStackEntry<BroadcastComposeRoute>() }.getOrNull()?.takeIf { entry ->
        val route = entry.toRoute<BroadcastComposeRoute>()
        route.orgSlug == orgSlug && route.eventId == eventId
    }

/** Registers the email screens. Owned by `:feature:broadcasts`. */
fun NavGraphBuilder.broadcastsGraph(navController: NavController) {
    composable<BroadcastComposeRoute> { entry ->
        val route = entry.toRoute<BroadcastComposeRoute>()
        val viewModel: BroadcastComposeViewModel = hiltViewModel(entry)
        BroadcastComposeScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onOpenPreview = { navController.navigateToBroadcastPreview(route.orgSlug, route.eventId) },
            onOpenHistory = { navController.navigateToBroadcastHistory(route.orgSlug, route.eventId) },
        )
    }
    composable<BroadcastPreviewRoute> { entry ->
        val route = entry.toRoute<BroadcastPreviewRoute>()
        val composeEntry = remember(entry) { navController.composeEntryFor(route.orgSlug, route.eventId) }
        if (composeEntry == null) {
            BroadcastPreviewUnavailable(onBack = { navController.popBackStack() })
        } else {
            val composeViewModel: BroadcastComposeViewModel = hiltViewModel(composeEntry)
            val previewViewModel: BroadcastPreviewViewModel = hiltViewModel(entry)
            val content = remember(composeViewModel) { composeViewModel.currentContent() }
            BroadcastPreviewScreen(
                orgSlug = route.orgSlug,
                eventId = route.eventId,
                content = content,
                viewModel = previewViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
    composable<BroadcastHistoryRoute> { entry ->
        val route = entry.toRoute<BroadcastHistoryRoute>()
        val composeEntry = remember(entry) { navController.composeEntryFor(route.orgSlug, route.eventId) }
        val composeViewModel: BroadcastComposeViewModel? = composeEntry?.let { hiltViewModel(it) }
        BroadcastHistoryScreen(
            viewModel = hiltViewModel<BroadcastHistoryViewModel>(entry),
            reuseTarget = composeViewModel,
            onBack = { navController.popBackStack() },
            onReused = {
                if (composeEntry != null) {
                    navController.popBackStack(composeEntry.destination.id, inclusive = false)
                } else {
                    // Opened from the event hub: the reused email was stored as the draft; Compose restores it.
                    navController.navigate(BroadcastComposeRoute(route.orgSlug, route.eventId)) {
                        popUpTo<BroadcastHistoryRoute> { inclusive = true }
                    }
                }
            },
        )
    }
}
