package com.twilighty.yuyu

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.common.AppearanceMode
import com.twilighty.yuyu.core.data.repository.SessionState
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.designsystem.util.openInBrowser
import com.twilighty.yuyu.navigation.DeepLink
import com.twilighty.yuyu.navigation.DeepLinkParser
import com.twilighty.yuyu.ui.YuyuApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { viewModel.uiState.value.session is SessionState.Loading }
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val dark = when (state.appearance) {
                AppearanceMode.System -> isSystemInDarkTheme()
                AppearanceMode.Light -> false
                AppearanceMode.Dark -> true
            }
            // Status/navigation bar icons follow the app's appearance, not only the system's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            YuyuTheme(darkTheme = dark) {
                YuyuApp(state = state, viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val webUrl = when (uri.scheme) {
            "https", "http" -> uri.toString()
            else -> null
        }
        val link = DeepLinkParser.parse(uri.scheme, uri.host, uri.pathSegments.orEmpty(), uri.encodedQuery, webUrl) ?: return
        if (link is DeepLink.OpenInBrowser) {
            openInBrowser(this, link.url)
        } else {
            viewModel.onDeepLink(link)
        }
    }
}
