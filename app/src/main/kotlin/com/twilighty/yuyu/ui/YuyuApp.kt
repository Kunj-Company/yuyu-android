package com.twilighty.yuyu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.twilighty.yuyu.AppUiState
import com.twilighty.yuyu.AppViewModel
import com.twilighty.yuyu.BuildConfig
import com.twilighty.yuyu.core.data.repository.SessionState
import com.twilighty.yuyu.core.designsystem.component.YuyuSnackbarHost
import com.twilighty.yuyu.core.designsystem.gallery.DesignSystemGallery
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.attendee.navigation.AttendeeGraph
import com.twilighty.yuyu.feature.attendee.navigation.MyTicketsRoute
import com.twilighty.yuyu.feature.attendee.navigation.attendeeGraph
import com.twilighty.yuyu.feature.attendee.navigation.navigateToDiscover
import com.twilighty.yuyu.feature.attendee.navigation.navigateToEventDetail
import com.twilighty.yuyu.feature.attendee.navigation.navigateToFeedback
import com.twilighty.yuyu.feature.attendee.navigation.navigateToTicket
import com.twilighty.yuyu.feature.auth.navigation.AccountRoute
import com.twilighty.yuyu.feature.auth.navigation.ServerSetupRoute
import com.twilighty.yuyu.feature.auth.navigation.SignInRoute
import com.twilighty.yuyu.feature.auth.navigation.accountScreen
import com.twilighty.yuyu.feature.auth.navigation.signedOutGraph
import com.twilighty.yuyu.feature.broadcasts.navigation.broadcastsGraph
import com.twilighty.yuyu.feature.broadcasts.navigation.navigateToBroadcastCompose
import com.twilighty.yuyu.feature.broadcasts.navigation.navigateToBroadcastHistory
import com.twilighty.yuyu.feature.checkin.navigation.checkInGraph
import com.twilighty.yuyu.feature.checkin.navigation.navigateToScanner
import com.twilighty.yuyu.feature.checkin.navigation.navigateToStationUnlock
import com.twilighty.yuyu.feature.organiser.navigation.OrganiserExternalNavigation
import com.twilighty.yuyu.feature.organiser.navigation.OrganiserGraph
import com.twilighty.yuyu.feature.organiser.navigation.OrganiserHomeRoute
import com.twilighty.yuyu.feature.organiser.navigation.organiserGraph
import com.twilighty.yuyu.navigation.DeepLink
import kotlinx.serialization.Serializable

/** Debug-only design-system gallery (reached from Account). */
@Serializable
data object DesignGalleryRoute

private enum class TopLevel(val label: String, val icon: ImageVector, val graph: Any) {
    Tickets("Tickets", Icons.Outlined.ConfirmationNumber, AttendeeGraph),
    Organise("Organise", Icons.Outlined.Groups, OrganiserGraph),
    Account("Account", Icons.Outlined.AccountCircle, AccountRoute),
}

@Composable
fun YuyuApp(state: AppUiState, viewModel: AppViewModel) {
    val c = YuyuTheme.colors
    when (val session = state.session) {
        SessionState.Loading -> Box(Modifier.fillMaxSize().background(c.bgCanvas))
        SessionState.NoServer, is SessionState.SignedOut -> SignedOutHost(hasServer = session !is SessionState.NoServer)
        is SessionState.SignedIn -> SignedInApp(state, viewModel)
    }
}

@Composable
private fun SignedOutHost(hasServer: Boolean) {
    // Recreated whenever the step changes; the state (not navigation) decides the screen.
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (hasServer) SignInRoute else ServerSetupRoute,
        modifier = Modifier.fillMaxSize().background(YuyuTheme.colors.bgCanvas),
    ) {
        signedOutGraph()
    }
    LaunchedEffect(hasServer) {
        // Only move when the step really changed (e.g. server saved, or "Use a different server").
        val current = navController.currentDestination ?: return@LaunchedEffect
        val onTarget = if (hasServer) current.hasRoute<SignInRoute>() else current.hasRoute<ServerSetupRoute>()
        if (!onTarget) {
            navController.navigate(if (hasServer) SignInRoute else ServerSetupRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }
}

@Composable
private fun SignedInApp(state: AppUiState, viewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val snackbar = remember { SnackbarHostState() }
    val pending by viewModel.pendingDeepLink.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(pending) {
        val link = pending ?: return@LaunchedEffect
        viewModel.consumeDeepLink()
        when (link) {
            is DeepLink.Ticket -> navController.navigateToTicket(link.token)
            is DeepLink.Event -> navController.navigateToEventDetail(link.orgSlug, link.eventSlug)
            is DeepLink.Feedback -> navController.navigateToFeedback(link.orgSlug, link.eventSlug)
            is DeepLink.Discover -> navController.navigateToDiscover(link.query)
            is DeepLink.ShortCode -> {
                val target = viewModel.resolveShortLink(link.code)
                if (target != null) {
                    navController.navigateToEventDetail(target.orgSlug, target.eventSlug)
                } else {
                    snackbar.showSnackbar("That link doesn't lead to an event.")
                }
            }
            is DeepLink.OpenInBrowser -> com.twilighty.yuyu.core.designsystem.util.openInBrowser(context, link.url)
        }
    }

    val tabs = TopLevel.entries.filter { it != TopLevel.Organise || state.showOrganise }
    val showBottomBar = destination != null && (
        destination.hasRoute<MyTicketsRoute>() || destination.hasRoute<OrganiserHomeRoute>() || destination.hasRoute<AccountRoute>()
        )

    Scaffold(
        containerColor = YuyuTheme.colors.bgCanvas,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { YuyuSnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) YuyuBottomBar(tabs, destination, navController)
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AttendeeGraph,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            attendeeGraph(navController)
            organiserGraph(
                navController,
                OrganiserExternalNavigation(
                    openScanner = navController::navigateToScanner,
                    openStation = navController::navigateToStationUnlock,
                    openBroadcastCompose = navController::navigateToBroadcastCompose,
                    openBroadcastHistory = navController::navigateToBroadcastHistory,
                    openPublicEvent = { org, event -> navController.navigateToEventDetail(org, event) },
                ),
            )
            checkInGraph(navController)
            broadcastsGraph(navController)
            accountScreen(onOpenDesignGallery = { navController.navigate(DesignGalleryRoute) })
            if (BuildConfig.DEBUG) {
                composable<DesignGalleryRoute> { DesignSystemGallery(onBack = navController::popBackStack) }
            }
        }
    }
}

@Composable
private fun YuyuBottomBar(tabs: List<TopLevel>, destination: NavDestination?, navController: NavHostController) {
    val c = YuyuTheme.colors
    NavigationBar(containerColor = c.bgCanvas, contentColor = c.text, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
        tabs.forEach { tab ->
            val selected = destination?.hierarchy?.any { it.hasRoute(tab.graph::class) } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.graph) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label, style = YuyuTheme.typography.chip) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.text,
                    selectedTextColor = c.text,
                    indicatorColor = c.bgEmphasis,
                    unselectedIconColor = c.textMuted,
                    unselectedTextColor = c.textMuted,
                ),
            )
        }
    }
}
