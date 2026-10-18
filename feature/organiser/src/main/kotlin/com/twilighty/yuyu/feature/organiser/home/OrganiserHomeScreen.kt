package com.twilighty.yuyu.feature.organiser.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.designsystem.component.EmptyState
import com.twilighty.yuyu.core.designsystem.component.ErrorState
import com.twilighty.yuyu.core.designsystem.component.ListRow
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.SectionHeader
import com.twilighty.yuyu.core.designsystem.component.SegmentedControl
import com.twilighty.yuyu.core.designsystem.component.YuyuAvatar
import com.twilighty.yuyu.core.designsystem.component.YuyuBottomSheet
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.designsystem.util.openInBrowser
import com.twilighty.yuyu.feature.organiser.common.OrgEventCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganiserHomeScreen(
    requestedOrg: String?,
    onOpenEvent: (orgSlug: String, eventId: String) -> Unit,
    viewModel: OrganiserHomeViewModel = hiltViewModel(),
) {
    LaunchedEffect(requestedOrg) { viewModel.start(requestedOrg) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = YuyuTheme.colors
    val openDashboard: () -> Unit = { viewModel.dashboardUrl()?.let { openInBrowser(context, it) } }

    Scaffold(
        containerColor = c.bgCanvas,
        topBar = {
            OrgSwitcherBar(
                org = state.selectedOrg,
                canSwitch = state.orgs.size > 1,
                onClick = { viewModel.openSwitcher(true) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loadingOrgs -> LoadingState(label = "Loading your organisations")
                state.orgs.isEmpty() && state.error != null -> ErrorState(state.error!!, onRetry = viewModel::retry)
                state.orgs.isEmpty() -> EmptyState(
                    title = "Nothing to organise yet",
                    body = "When you join an organisation or are invited to help with an event on the website, it shows up here.",
                )
                else -> {
                    SegmentedControl(
                        options = EventsFilter.entries.map { it.label },
                        selectedIndex = state.filter.ordinal,
                        onSelect = { viewModel.selectFilter(EventsFilter.entries[it]) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        EventsList(state, onOpenEvent, openDashboard, viewModel::retry)
                    }
                }
            }
        }
    }

    if (state.switcherOpen) {
        OrgSwitcherSheet(
            state = state,
            onSelect = viewModel::selectOrg,
            onDismiss = { viewModel.openSwitcher(false) },
        )
    }
}

@Composable
private fun EventsList(
    state: OrganiserHomeUiState,
    onOpenEvent: (String, String) -> Unit,
    openDashboard: () -> Unit,
    onRetry: () -> Unit,
) {
    val org = state.selectedOrg ?: return
    when {
        state.loadingEvents && state.events.isEmpty() -> LoadingState(label = "Loading events")
        state.error != null && state.events.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
            item { ErrorState(state.error, onRetry = onRetry) }
        }
        state.events.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
            item {
                EmptyState(
                    title = when (state.filter) {
                        EventsFilter.Upcoming -> "No upcoming events"
                        EventsFilter.Past -> "No past events"
                        EventsFilter.All -> "No events yet"
                    },
                    body = if (org.isCollaboration) {
                        "Events you're invited to help with appear here."
                    } else {
                        "Events are created on the website. Create one in your dashboard and it will show up here."
                    },
                    actionLabel = if (org.isCollaboration) null else "Open dashboard on the web",
                    onAction = if (org.isCollaboration) null else openDashboard,
                )
            }
        }
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (org.isCollaboration) {
                item { SectionHeader("Events you help with") }
            }
            items(state.events, key = { it.id }) { event ->
                OrgEventCard(
                    title = event.title,
                    startDateTime = event.startDateTime,
                    endDateTime = event.endDateTime,
                    timezone = event.timezone,
                    accentColor = event.accentColor,
                    status = event.status,
                    location = event.location,
                    isOnline = event.isOnline,
                    counts = event.counts,
                    onClick = { onOpenEvent(event.organisation.slug, event.id) },
                )
            }
            if (!org.isCollaboration) {
                item {
                    YuyuButton(
                        "Open dashboard on the web",
                        openDashboard,
                        variant = YuyuButtonVariant.Quiet,
                        trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                        fullWidth = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrgSwitcherBar(org: OrgOption?, canSwitch: Boolean, onClick: () -> Unit) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 4.dp)) {
        Text("Organise", style = t.overline, color = c.textMuted)
        Row(
            Modifier
                .defaultMinSize(minHeight = 48.dp)
                .then(
                    if (canSwitch) {
                        Modifier.clickable(role = Role.Button, onClickLabel = "Switch organisation", onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (org != null) YuyuAvatar(org.name, imageUrl = org.logoUrl, size = 32.dp)
            Text(
                org?.name ?: "Organise",
                style = t.h3,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            )
            if (canSwitch) Icon(Icons.Outlined.UnfoldMore, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun OrgSwitcherSheet(state: OrganiserHomeUiState, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val c = YuyuTheme.colors
    YuyuBottomSheet(onDismiss = onDismiss, title = "Switch organisation") {
        LazyColumn(Modifier.widthIn(max = 640.dp)) {
            if (state.memberships.isNotEmpty()) {
                item { SectionHeader("Your organisations") }
                items(state.memberships, key = { "m-" + it.slug }) { org ->
                    OrgRow(org, selected = org.slug == state.selectedOrg?.slug, onSelect = onSelect)
                }
            }
            if (state.collaborations.isNotEmpty()) {
                item { SectionHeader("Events you help with") }
                items(state.collaborations, key = { "c-" + it.slug }) { org ->
                    OrgRow(org, selected = org.slug == state.selectedOrg?.slug, onSelect = onSelect)
                }
            }
        }
        Text(
            "Organisations and events are created on the website.",
            style = YuyuTheme.typography.caption,
            color = c.textMuted,
        )
    }
}

@Composable
private fun OrgRow(org: OrgOption, selected: Boolean, onSelect: (String) -> Unit) {
    ListRow(
        title = org.name,
        supporting = if (org.isCollaboration) org.collaboratedEvents.joinToString(" · ") else org.roleLabel,
        leading = { YuyuAvatar(org.name, imageUrl = org.logoUrl, size = 36.dp) },
        trailing = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = YuyuTheme.colors.text) }
        } else {
            null
        },
        onClick = { onSelect(org.slug) },
    )
}
