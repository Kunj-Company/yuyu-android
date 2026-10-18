package com.twilighty.yuyu.feature.attendee.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.designsystem.brand.DotFaceMood
import com.twilighty.yuyu.core.designsystem.brand.DotLoader
import com.twilighty.yuyu.core.designsystem.component.EmptyState
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.MessageState
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuSearchField
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.theme.YuyuSpacing
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.attendee.common.EventCard

@Composable
internal fun DiscoverRoute(
    onBack: () -> Unit,
    onOpenEvent: (String, String) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DiscoverScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::searchNow,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpenEvent = onOpenEvent,
    )
}

@Composable
internal fun DiscoverScreen(
    state: DiscoverUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenEvent: (String, String) -> Unit,
) {
    Scaffold(
        topBar = { YuyuTopBar("Discover", onBack = onBack) },
        containerColor = YuyuTheme.colors.bgCanvas,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            YuyuSearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = "Search events, places, organisers",
                onSearch = onSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = YuyuSpacing.gutter, vertical = 8.dp),
            )
            Box(Modifier.fillMaxSize()) {
                when {
                    state.loading -> LoadingState(label = if (state.isSearch) "Searching" else "Loading events")
                    state.error != null -> MessageState(
                        mood = if (state.offline) DotFaceMood.Sleepy else DotFaceMood.Sad,
                        title = if (state.offline) "You're offline" else "Couldn't load events",
                        body = state.error,
                        actionLabel = "Try again",
                        onAction = onRetry,
                    )
                    state.events.isEmpty() -> EmptyState(
                        title = if (state.isSearch) "No events match “${state.query.trim()}”" else "Nothing coming up yet",
                        body = if (state.isSearch) "Try another word, a place or an organiser's name." else "Public events from this Yuyu will show up here.",
                    )
                    else -> EventList(state, onLoadMore, onOpenEvent)
                }
            }
        }
    }
}

@Composable
private fun EventList(state: DiscoverUiState, onLoadMore: () -> Unit, onOpenEvent: (String, String) -> Unit) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.nextCursor) {
        if (nearEnd && state.nextCursor != null) onLoadMore()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = YuyuSpacing.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.events, key = { it.id }) { event ->
            EventCard(event, onClick = { onOpenEvent(event.organisation.slug, event.slug) })
        }
        if (state.loadingMore) {
            item("more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { DotLoader(size = 32.dp, label = "Loading more events") }
            }
        }
        state.loadMoreError?.let { message ->
            item("more-error") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message, style = YuyuTheme.typography.bodyUi, color = YuyuTheme.colors.textMuted)
                    YuyuButton("Try again", onLoadMore, variant = YuyuButtonVariant.Secondary)
                }
            }
        }
    }
}
