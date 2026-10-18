package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/** Quiet canvas top bar with an optional back button (h4 title, left-aligned). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YuyuTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = YuyuTheme.colors
    TopAppBar(
        title = {
            Text(
                title,
                style = YuyuTheme.typography.h4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        },
        navigationIcon = {
            if (onBack != null) YuyuIconButton(Icons.AutoMirrored.Outlined.ArrowBack, backLabel, onBack)
        },
        actions = actions,
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = c.bgCanvas,
            scrolledContainerColor = c.bgCanvas,
            titleContentColor = c.text,
            navigationIconContentColor = c.text,
            actionIconContentColor = c.text,
        ),
    )
}
