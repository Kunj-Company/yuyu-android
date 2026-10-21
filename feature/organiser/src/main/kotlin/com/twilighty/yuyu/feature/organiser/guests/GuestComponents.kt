package com.twilighty.yuyu.feature.organiser.guests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.common.time.EventTimeFormatter
import com.twilighty.yuyu.core.designsystem.component.ConfirmDialog
import com.twilighty.yuyu.core.designsystem.component.DialogActions
import com.twilighty.yuyu.core.designsystem.component.SectionHeader
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.YuyuAvatar
import com.twilighty.yuyu.core.designsystem.component.YuyuBottomSheet
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonSize
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuIconButton
import com.twilighty.yuyu.core.designsystem.component.YuyuTextField
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.GuestDetailDto
import com.twilighty.yuyu.core.network.model.GuestDto
import com.twilighty.yuyu.feature.organiser.common.AnimatedStatusChip
import com.twilighty.yuyu.feature.organiser.common.RsvpStatus
import com.twilighty.yuyu.feature.organiser.common.checkedInLabel
import com.twilighty.yuyu.feature.organiser.common.guestStatusLabel
import kotlinx.coroutines.launch

@Composable
fun GuestStatusChip(status: String, modifier: Modifier = Modifier) {
    val label = guestStatusLabel(status)
    AnimatedStatusChip(label.text, label.tone, modifier)
}

/**
 * One guest in the list. Lifecycle controls appear only when [actions] allow
 * them (never for check-in-only helpers). Pending and waitlisted rows can also
 * be swiped: right to approve / give a spot, left to decline.
 */
@Composable
fun GuestRow(
    guest: GuestDto,
    timezone: String,
    actions: GuestRowActions,
    busy: Boolean,
    onClick: () -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onPromote: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val swipeable = (actions.approveDecline || actions.promote) && !busy
    if (!swipeable) {
        GuestRowContent(guest, timezone, actions, busy, selected, onClick, onApprove, onDecline, onPromote, onRemove, modifier)
        return
    }
    val approve by rememberUpdatedState(if (actions.approveDecline) onApprove else onPromote)
    val decline by rememberUpdatedState(onDecline)
    val canDecline = actions.approveDecline
    val swipe = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        enableDismissFromEndToStart = canDecline,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> approve()
                SwipeToDismissBoxValue.EndToStart -> if (canDecline) decline()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // Snap back: the row stays and shows its new status optimistically.
            scope.launch { swipe.reset() }
        },
        backgroundContent = {
            val c = YuyuTheme.colors
            val direction = swipe.dismissDirection
            val toStart = direction == SwipeToDismissBoxValue.EndToStart
            if (direction == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
            Box(
                Modifier
                    .fillMaxSize()
                    // Decorative: the same actions are buttons and accessibility actions on the row.
                    .clearAndSetSemantics { }
                    .background(if (toStart) c.dangerBg else c.successBg)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (toStart) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Text(
                    when {
                        toStart -> "Decline"
                        actions.approveDecline -> "Approve"
                        else -> "Give a spot"
                    },
                    style = YuyuTheme.typography.label,
                    color = if (toStart) c.dangerFg else c.successFg,
                )
            }
        },
    ) {
        GuestRowContent(guest, timezone, actions, busy, selected, onClick, onApprove, onDecline, onPromote, onRemove)
    }
}

@Composable
private fun GuestRowContent(
    guest: GuestDto,
    timezone: String,
    actions: GuestRowActions,
    busy: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onPromote: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    val name = guest.displayName
    val a11yActions = buildList {
        if (actions.approveDecline) {
            add(CustomAccessibilityAction("Approve") { onApprove(); true })
            add(CustomAccessibilityAction("Decline") { onDecline(); true })
        }
        if (actions.promote) add(CustomAccessibilityAction("Give a spot") { onPromote(); true })
        if (actions.remove) add(CustomAccessibilityAction("Remove RSVP") { onRemove(); true })
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(if (selected) c.bgMuted else c.bgCanvas)
            .clickable(onClickLabel = "Open $name", onClick = onClick)
            .semantics {
                this.selected = selected
                if (a11yActions.isNotEmpty()) customActions = a11yActions
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            YuyuAvatar(name, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = t.bodyUiMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                guest.email?.let { Text(it, style = t.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                checkedInLabel(guest.checkedInAt, timezone)?.let {
                    Text(it, style = t.caption, color = c.successFg)
                }
            }
            GuestStatusChip(guest.status)
        }
        if (actions.any) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (actions.approveDecline) {
                    YuyuButton("Approve", onApprove, variant = YuyuButtonVariant.Secondary, size = YuyuButtonSize.Small, enabled = !busy, loading = busy)
                    YuyuButton("Decline", onDecline, variant = YuyuButtonVariant.DangerQuiet, size = YuyuButtonSize.Small, enabled = !busy)
                }
                if (actions.promote) {
                    YuyuButton("Give a spot", onPromote, variant = YuyuButtonVariant.Secondary, size = YuyuButtonSize.Small, enabled = !busy, loading = busy)
                }
                if (actions.remove) {
                    YuyuIconButton(Icons.Outlined.DeleteOutline, "Remove $name", onClick = onRemove, enabled = !busy, tint = c.textMuted)
                }
            }
        }
    }
}

/** Guest detail body, shared by the phone screen and the tablet detail pane. */
@Composable
fun GuestDetailContent(
    guest: GuestDetailDto,
    timezone: String,
    actions: GuestRowActions,
    showCheckIn: Boolean,
    busy: Boolean,
    onCheckIn: () -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onPromote: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            YuyuAvatar(guest.displayName, size = 56.dp)
            Column(Modifier.weight(1f)) {
                Text(guest.displayName, style = t.h3, color = c.text, modifier = Modifier.semantics { heading() })
                guest.email?.let { Text(it, style = t.bodyUi, color = c.textMuted) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GuestStatusChip(guest.status)
            if (guest.checkedInAt != null) StatusChip("Checked in", com.twilighty.yuyu.core.designsystem.component.StatusTone.Success)
        }

        YuyuCard {
            DetailLine("Status", guestStatusLabel(guest.status).text)
            HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 8.dp))
            DetailLine("Registered", formatMoment(guest.registeredAt, timezone) ?: "—")
            HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 8.dp))
            DetailLine(
                "Checked in",
                formatMoment(guest.checkedInAt, timezone) ?: if (guest.status == RsvpStatus.CONFIRMED) "Not yet" else "—",
            )
            if (guest.isGuest) {
                HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 8.dp))
                DetailLine("Account", "Added as a guest (no Yuyu account)")
            }
        }

        if (showCheckIn) {
            YuyuButton("Check in", onCheckIn, leadingIcon = Icons.Outlined.HowToReg, fullWidth = true, loading = busy, loadingText = "Checking in…")
        }
        if (actions.approveDecline) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                YuyuButton("Approve", onApprove, Modifier.weight(1f), variant = YuyuButtonVariant.Secondary, leadingIcon = Icons.Outlined.CheckCircle, enabled = !busy, fullWidth = true)
                YuyuButton("Decline", onDecline, Modifier.weight(1f), variant = YuyuButtonVariant.DangerQuiet, enabled = !busy, fullWidth = true)
            }
        }
        if (actions.promote) {
            YuyuButton("Give a spot", onPromote, variant = YuyuButtonVariant.Secondary, enabled = !busy, fullWidth = true)
        }

        if (guest.answers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Registration answers")
                YuyuCard {
                    guest.answers.forEachIndexed { index, answer ->
                        if (index > 0) HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 8.dp))
                        Text(answer.label, style = t.caption, color = c.textMuted)
                        Text(answer.value, style = t.bodyUi, color = c.text)
                    }
                }
            }
        }

        if (actions.remove) {
            YuyuButton("Remove RSVP", onRemove, variant = YuyuButtonVariant.DangerQuiet, leadingIcon = Icons.Outlined.DeleteOutline, enabled = !busy)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = t.bodyUi, color = c.textMuted, modifier = Modifier.weight(0.4f))
        Text(value, style = t.bodyUiMedium, color = c.text, modifier = Modifier.weight(0.6f))
    }
}

/** `Wed 14 Oct, 9:30 AM BST` in the event timezone. */
internal fun formatMoment(iso: String?, timezone: String): String? {
    val at = EventTimeFormatter.parseInstant(iso) ?: return null
    return EventTimeFormatter.dayShort(at, timezone) + ", " + EventTimeFormatter.time(at, timezone) + " " + EventTimeFormatter.zoneLabel(at, timezone)
}

@Composable
fun RemoveGuestDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "Remove $name?",
        body = "Their RSVP is removed and their ticket stops working. You can undo this for a few minutes.",
        confirmLabel = "Remove",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        danger = true,
    )
}

@Composable
fun AddGuestSheet(form: AddGuestForm, onSubmit: (name: String, email: String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    YuyuBottomSheet(onDismiss = onDismiss, title = "Add guest") {
        Text(
            "Add someone to this event's guest list.",
            style = YuyuTheme.typography.bodyUi,
            color = YuyuTheme.colors.textMuted,
        )
        YuyuTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            error = form.nameError,
            enabled = !form.saving,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        YuyuTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            error = form.emailError,
            enabled = !form.saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            onImeAction = { onSubmit(name, email) },
        )
        form.error?.let { Text(it, style = YuyuTheme.typography.caption, color = YuyuTheme.colors.dangerFg) }
        DialogActions {
            YuyuButton("Cancel", onDismiss, variant = YuyuButtonVariant.Secondary)
            YuyuButton("Add guest", { onSubmit(name, email) }, loading = form.saving, loadingText = "Adding…")
        }
    }
}
