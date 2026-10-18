package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/**
 * Confirmation dialog (DESIGN §4.15): title, body, actions right-aligned
 * (secondary then primary/danger). [danger] makes the confirm button the
 * danger variant; destructive labels should be specific ("Sign out everywhere").
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    danger: Boolean = false,
    confirmLoading: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = YuyuRadius.extraExtraLarge,
        containerColor = YuyuTheme.colors.bgCanvas,
        title = { Text(title, style = YuyuTheme.typography.h3, color = YuyuTheme.colors.text) },
        text = { Text(body, style = YuyuTheme.typography.bodyUi, color = YuyuTheme.colors.textSecondary) },
        confirmButton = {
            YuyuButton(
                text = confirmLabel,
                onClick = onConfirm,
                variant = if (danger) YuyuButtonVariant.Danger else YuyuButtonVariant.Primary,
                loading = confirmLoading,
            )
        },
        dismissButton = { YuyuButton(dismissLabel, onDismiss, variant = YuyuButtonVariant.Secondary) },
    )
}

/** Bottom sheet with Yuyu surface, 24dp top corners and a title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YuyuBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = YuyuTheme.colors.bgCanvas,
        scrimColor = YuyuTheme.colors.overlay,
        shape = YuyuRadius.extraExtraLarge,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title != null) Text(title, style = YuyuTheme.typography.h3, color = YuyuTheme.colors.text)
            content()
        }
    }
}

/** Right-aligned action row for dialogs and sheets. */
@Composable
fun DialogActions(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) { content() }
}
