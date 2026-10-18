package com.twilighty.yuyu.feature.checkin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.component.ListRow
import com.twilighty.yuyu.core.designsystem.component.SectionHeader
import com.twilighty.yuyu.core.designsystem.component.SegmentedControl
import com.twilighty.yuyu.core.designsystem.component.StatTile
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuSearchField
import com.twilighty.yuyu.core.designsystem.component.YuyuSwitchRow
import com.twilighty.yuyu.core.designsystem.component.YuyuTextField
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.checkin.camera.QrScanner
import com.twilighty.yuyu.feature.checkin.domain.CheckInCounts
import com.twilighty.yuyu.feature.checkin.domain.CheckInTime
import com.twilighty.yuyu.feature.checkin.domain.DeskMode
import com.twilighty.yuyu.feature.checkin.domain.RecentCheckIn
import com.twilighty.yuyu.feature.checkin.domain.statusLabel
import com.twilighty.yuyu.feature.checkin.flow.LookupState

/** "checked in / confirmed" with a progress bar. */
@Composable
internal fun CounterTile(counts: CheckInCounts?, modifier: Modifier = Modifier, hint: String? = null) {
    StatTile(
        label = "Checked in",
        value = counts?.let { "${it.checkedIn} / ${it.confirmed}" } ?: "–",
        progress = counts?.progress,
        hint = hint ?: counts?.let { "of ${it.confirmed} confirmed" },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Scan / Type code / Search, plus the panel for the chosen mode. */
@Composable
internal fun FindGuestPanel(
    mode: DeskMode,
    onModeChange: (DeskMode) -> Unit,
    cameraPaused: Boolean,
    onScanned: (String) -> Unit,
    onSubmitCode: (String) -> Unit,
    lookup: LookupState,
    onLookupQuery: (String) -> Unit,
    onPickGuest: (String) -> Unit,
    fastMode: Boolean,
    onFastModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedControl(
            options = DeskMode.entries.map { it.label },
            selectedIndex = mode.ordinal,
            onSelect = { onModeChange(DeskMode.entries[it]) },
        )
        when (mode) {
            DeskMode.Scan -> {
                QrScanner(
                    paused = cameraPaused,
                    onCode = onScanned,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                YuyuSwitchRow(
                    title = "Fast mode",
                    description = "Check in valid tickets straight away, without the confirm tap.",
                    checked = fastMode,
                    onCheckedChange = onFastModeChange,
                )
            }
            DeskMode.Code -> ManualCodeEntry(onSubmitCode)
            DeskMode.Search -> GuestSearch(lookup, onLookupQuery, onPickGuest)
        }
    }
}

@Composable
private fun ManualCodeEntry(onSubmit: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (code.isNotBlank()) {
            onSubmit(code)
            code = ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        YuyuTextField(
            value = code,
            onValueChange = { code = it },
            label = "Ticket code",
            helper = "The code printed under the QR, or the ticket link.",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            onImeAction = submit,
        )
        YuyuButton("Find ticket", submit, enabled = code.isNotBlank(), fullWidth = true)
    }
}

@Composable
private fun GuestSearch(lookup: LookupState, onQuery: (String) -> Unit, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf(lookup.query) }
    val c = YuyuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        YuyuSearchField(
            value = query,
            onValueChange = {
                query = it
                onQuery(it)
            },
            placeholder = "Search by name or email",
        )
        when {
            query.trim().length < LookupState.MIN_QUERY ->
                Text("Type at least 2 letters.", style = YuyuTheme.typography.caption, color = c.textMuted)
            lookup.loading -> Text("Searching…", style = YuyuTheme.typography.caption, color = c.textMuted)
            lookup.error != null -> Text(lookup.error, style = YuyuTheme.typography.caption, color = c.dangerFg)
            lookup.results.isEmpty() && lookup.query == query ->
                Text("No guests match “${query.trim()}”.", style = YuyuTheme.typography.caption, color = c.textMuted)
        }
        if (lookup.offline && lookup.results.isNotEmpty()) {
            Text("From the guest list saved on this phone.", style = YuyuTheme.typography.caption, color = c.textSecondary)
        }
        lookup.results.forEach { guest ->
            ListRow(
                title = guest.displayName,
                supporting = guest.email,
                leadingIcon = if (guest.checkedInAt != null) Icons.Outlined.CheckCircle else Icons.Outlined.Person,
                trailing = {
                    if (guest.checkedInAt != null) {
                        StatusChip("Checked in", StatusTone.Success)
                    } else {
                        StatusChip(statusLabel(guest.status), statusTone(guest.status))
                    }
                },
                onClick = { onPick(guest.rsvpId) },
                divider = true,
                modifier = Modifier.heightIn(min = 56.dp),
            )
        }
    }
}

/** Newest check-ins first. */
@Composable
internal fun RecentCheckIns(recent: List<RecentCheckIn>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader("Recent check-ins")
        if (recent.isEmpty()) {
            Text("No one checked in yet.", style = YuyuTheme.typography.bodyUi, color = YuyuTheme.colors.textMuted)
        }
        recent.forEach { row ->
            ListRow(
                title = row.displayName,
                supporting = CheckInTime.time(row.checkedInAt),
                leadingIcon = Icons.Outlined.CheckCircle,
                divider = true,
            )
        }
    }
}
