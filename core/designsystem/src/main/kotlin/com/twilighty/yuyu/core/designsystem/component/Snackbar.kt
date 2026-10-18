package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/** Toast (DESIGN §4.15): ink background, white text, bottom-centre, optional Undo. */
@Composable
fun YuyuSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val c = YuyuTheme.colors
    SnackbarHost(hostState, modifier.semantics { liveRegion = LiveRegionMode.Polite }) { data ->
        Snackbar(
            snackbarData = data,
            shape = YuyuRadius.large,
            containerColor = c.inverseSurface,
            contentColor = c.onInverse,
            actionColor = c.brandMarigold,
            actionContentColor = c.brandMarigold,
        )
    }
}

/** Shows "[message] · Undo" for ~4s; returns true if the person tapped Undo. */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String = "Undo"): Boolean =
    showSnackbar(message, actionLabel = undoLabel, withDismissAction = false, duration = SnackbarDuration.Short) ==
        SnackbarResult.ActionPerformed
