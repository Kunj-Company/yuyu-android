package com.twilighty.yuyu.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.twilighty.yuyu.feature.auth.account.AccountScreen
import com.twilighty.yuyu.feature.auth.server.ServerSetupScreen
import com.twilighty.yuyu.feature.auth.signin.SignInScreen
import kotlinx.serialization.Serializable

/** Pick the Yuyu server (shown while no server is saved). */
@Serializable
data object ServerSetupRoute

/** Email/password (+ code step) and Google sign-in (shown while signed out). */
@Serializable
data object SignInRoute

/** Bottom-nav "Account" tab. */
@Serializable
data object AccountRoute

/**
 * Signed-out destinations. Success needs no navigation callback: the session
 * state changes and `:app` swaps to the signed-in UI.
 */
fun NavGraphBuilder.signedOutGraph() {
    composable<ServerSetupRoute> { ServerSetupScreen() }
    composable<SignInRoute> { SignInScreen() }
}

/** The Account tab. [onOpenDesignGallery] is only reachable in debug builds. */
fun NavGraphBuilder.accountScreen(onOpenDesignGallery: () -> Unit) {
    composable<AccountRoute> { AccountScreen(onOpenDesignGallery = onOpenDesignGallery) }
}
