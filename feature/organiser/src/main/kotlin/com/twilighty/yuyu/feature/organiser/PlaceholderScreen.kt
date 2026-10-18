package com.twilighty.yuyu.feature.organiser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.component.ComingSoon
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/** Temporary screen for a route this module hasn't built yet. Replace per route. */
@Composable
internal fun PlaceholderScreen(
    title: String,
    detail: String,
    onBack: (() -> Unit)? = null,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    Scaffold(
        topBar = { YuyuTopBar(title, onBack = onBack) },
        containerColor = YuyuTheme.colors.bgCanvas,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ComingSoon(title = "Coming soon", body = detail, modifier = Modifier.weight(1f))
            actions.forEach { (label, action) ->
                YuyuButton(label, action, variant = YuyuButtonVariant.Secondary, fullWidth = true)
            }
        }
    }
}
