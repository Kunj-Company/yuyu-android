package com.twilighty.yuyu.feature.checkin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.brand.DotLoader
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonSize
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.LabelValueDto
import com.twilighty.yuyu.feature.checkin.domain.CheckInTime
import com.twilighty.yuyu.feature.checkin.domain.GuestPreview
import com.twilighty.yuyu.feature.checkin.domain.statusLabel
import com.twilighty.yuyu.feature.checkin.flow.SheetState

/** Kiosk-sized result body: solid success / warning / danger panels per DESIGN §4.12. */
@Composable
internal fun ResultSheetContent(
    state: SheetState,
    onConfirm: () -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state) {
            SheetState.Hidden -> Unit
            is SheetState.Loading -> Box(Modifier.fillMaxWidth().heightIn(min = 180.dp), contentAlignment = Alignment.Center) {
                DotLoader(size = 56.dp, label = state.label)
            }
            is SheetState.Ready -> ReadyContent(state, onConfirm, onDismiss)
            is SheetState.Admitted -> AdmittedContent(state, onUndo, onDismiss)
            is SheetState.AlreadyIn -> AlreadyInContent(state, onDismiss)
            is SheetState.Blocked -> BlockedContent(state, onDismiss)
            is SheetState.Undone -> {
                GuestHeader(state.guest)
                Text("Check-in undone. The guest can be checked in again.", style = YuyuTheme.typography.bodyUi, color = YuyuTheme.colors.textSecondary)
                YuyuButton("Next guest", onDismiss, size = YuyuButtonSize.Large, fullWidth = true)
            }
        }
    }
}

@Composable
private fun ColumnScope.ReadyContent(state: SheetState.Ready, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = YuyuTheme.colors
    if (state.override) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.warningBg, YuyuRadius.extraLarge)
                .border(BorderStroke(2.dp, c.warningSolid), YuyuRadius.extraLarge)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (state.guest.status == "WAITLISTED") "On the waitlist" else "Needs an override",
                style = YuyuTheme.typography.h3,
                color = c.warningFg,
            )
            Text(
                state.reason ?: "This guest isn't confirmed. You can still check them in at the door.",
                style = YuyuTheme.typography.bodyUi,
                color = c.warningFg,
            )
        }
    }
    GuestHeader(state.guest)
    if (state.guest.offline) OfflineNote("Checked against the guest list saved on this phone.")
    DetailRows("At the door", state.guest.checkInDetails)
    DetailRows("Registration", state.guest.registrationDetails)
    state.error?.let { Text(it, style = YuyuTheme.typography.bodyUiMedium, color = c.dangerFg) }
    YuyuButton(
        text = if (state.override) "Check in anyway" else "Check in",
        onClick = onConfirm,
        size = YuyuButtonSize.Large,
        variant = if (state.override) YuyuButtonVariant.Accent else YuyuButtonVariant.Primary,
        loading = state.submitting,
        loadingText = "Checking in…",
        fullWidth = true,
        modifier = Modifier.heightIn(min = 64.dp),
    )
    YuyuButton("Cancel", onDismiss, variant = YuyuButtonVariant.Quiet, fullWidth = true, enabled = !state.submitting)
}

@Composable
private fun AdmittedContent(state: SheetState.Admitted, onUndo: () -> Unit, onDismiss: () -> Unit) {
    val c = YuyuTheme.colors
    SolidPanel(background = c.successSolid, icon = Icons.Outlined.CheckCircle, title = "Checked in") {
        Text(state.guest.displayName, style = YuyuTheme.typography.displayM, color = OnSolid)
        val time = CheckInTime.time(state.checkedInAt)
        Text(
            listOfNotNull(time?.let { "at $it" }, if (state.offline) "saved offline · will sync" else null).joinToString(" · "),
            style = YuyuTheme.typography.bodyUiMedium,
            color = OnSolid,
        )
        state.guest.checkInDetails.forEach { detail ->
            Text("${detail.label}: ${detail.value}", style = YuyuTheme.typography.bodyUi, color = OnSolid)
        }
        state.undoError?.let { Text(it, style = YuyuTheme.typography.bodyUiMedium, color = OnSolid, fontWeight = FontWeight.Bold) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            YuyuButton(
                "Next guest",
                onDismiss,
                variant = YuyuButtonVariant.Inverse,
                size = YuyuButtonSize.Large,
                enabled = !state.undoing,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
            )
            YuyuButton(
                "Undo",
                onUndo,
                variant = YuyuButtonVariant.Secondary,
                size = YuyuButtonSize.Large,
                leadingIcon = Icons.Outlined.Undo,
                loading = state.undoing,
                modifier = Modifier.heightIn(min = 64.dp),
            )
        }
    }
}

@Composable
private fun AlreadyInContent(state: SheetState.AlreadyIn, onDismiss: () -> Unit) {
    val c = YuyuTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.warningBg, YuyuRadius.extraLarge)
            .border(BorderStroke(2.dp, c.warningSolid), YuyuRadius.extraLarge)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = c.warningFg, modifier = Modifier.size(28.dp))
            Text("Already checked in", style = YuyuTheme.typography.h2, color = c.warningFg)
        }
        Text(state.guest.displayName, style = YuyuTheme.typography.displayM, color = c.text)
        val at = CheckInTime.dayTime(state.checkedInAt)
        Text(
            if (at != null) "Checked in at $at" else "This ticket was already used.",
            style = YuyuTheme.typography.bodyUiMedium,
            color = c.warningFg,
        )
        state.guest.checkInDetails.forEach { detail ->
            Text("${detail.label}: ${detail.value}", style = YuyuTheme.typography.bodyUi, color = c.text)
        }
        YuyuButton("Next guest", onDismiss, size = YuyuButtonSize.Large, fullWidth = true, modifier = Modifier.padding(top = 8.dp).heightIn(min = 64.dp))
    }
}

@Composable
private fun BlockedContent(state: SheetState.Blocked, onDismiss: () -> Unit) {
    SolidPanel(background = YuyuTheme.colors.dangerSolid, icon = Icons.Outlined.HighlightOff, title = state.title) {
        state.guest?.let { guest ->
            Text(guest.displayName, style = YuyuTheme.typography.h1, color = OnSolid)
            Text(statusLabel(guest.status), style = YuyuTheme.typography.label, color = OnSolid)
        }
        Text(state.message, style = YuyuTheme.typography.bodyL, color = OnSolid)
        YuyuButton(
            "Scan again",
            onDismiss,
            variant = YuyuButtonVariant.Inverse,
            size = YuyuButtonSize.Large,
            fullWidth = true,
            modifier = Modifier.padding(top = 8.dp).heightIn(min = 64.dp),
        )
    }
}

/** White on the solid fills: 5.0:1 on success `#15803D`, more on danger (DESIGN §2.5). */
private val OnSolid = Color.White

@Composable
private fun SolidPanel(background: Color, icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(background, YuyuRadius.extraLarge)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = OnSolid, modifier = Modifier.size(36.dp))
            Text(title, style = YuyuTheme.typography.h2, color = OnSolid)
        }
        content()
    }
}

@Composable
private fun GuestHeader(guest: GuestPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(guest.displayName, style = YuyuTheme.typography.h1, color = YuyuTheme.colors.text)
        guest.email?.let { Text(it, style = YuyuTheme.typography.bodyUi, color = YuyuTheme.colors.textSecondary) }
        StatusChip(statusLabel(guest.status), statusTone(guest.status))
    }
}

@Composable
private fun OfflineNote(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = YuyuTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
        Text(text, style = YuyuTheme.typography.caption, color = YuyuTheme.colors.textSecondary)
    }
}

@Composable
private fun DetailRows(title: String, rows: List<LabelValueDto>) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title.uppercase(), style = YuyuTheme.typography.overline, color = YuyuTheme.colors.textMuted)
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${row.label}:", style = YuyuTheme.typography.bodyUi, color = YuyuTheme.colors.textSecondary)
                Text(row.value, style = YuyuTheme.typography.bodyUiMedium, color = YuyuTheme.colors.text)
            }
        }
    }
}

internal fun statusTone(status: String): StatusTone = when (status) {
    "CONFIRMED" -> StatusTone.Success
    "WAITLISTED", "PENDING_APPROVAL" -> StatusTone.Warning
    "REJECTED" -> StatusTone.Danger
    else -> StatusTone.Neutral
}
