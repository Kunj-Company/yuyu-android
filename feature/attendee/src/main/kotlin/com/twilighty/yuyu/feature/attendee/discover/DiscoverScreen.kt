package com.twilighty.yuyu.feature.attendee.discover

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.motion.YuyuStateTransition
import com.twilighty.yuyu.core.designsystem.motion.rememberEntranceState
import com.twilighty.yuyu.core.designsystem.theme.YuyuSpacing
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.attendee.common.EventCard
import com.twilighty.yuyu.feature.attendee.common.listItemMotion

@Composable
internal fun DiscoverRoute(
    /** Null on the Explore tab, where Discover is a root with no back arrow. */
    onBack: (() -> Unit)?,
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
    onBack: (() -> Unit)?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenEvent: (String, String) -> Unit,
) {
    Scaffold(
        topBar = { YuyuTopBar(if (onBack == null) "Explore" else "Discover", onBack = onBack) },
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
            // While a new search runs over existing results, keep the last settled view (dimmed)
            // so results change in place instead of flashing through a loader.
            val lastSettled = remember { arrayOfNulls<DiscoverPhase>(1) }
            val phase = if (!state.loading) {
                state.settledPhase.also { lastSettled[0] = it }
            } else {
                lastSettled[0]?.takeIf { it != DiscoverPhase.Error } ?: DiscoverPhase.Loading
            }
            val busy = state.loading && phase != DiscoverPhase.Loading
            val reduced = LocalReducedMotion.current
            val dim by animateFloatAsState(if (busy && !reduced) 0.55f else 1f, YuyuSpecs.quick(), label = "searchDim")
            Box(Modifier.fillMaxSize()) {
                YuyuStateTransition(
                    targetState = phase to state,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = dim },
                    contentKey = { it.first },
                    label = "discover",
                ) { (shownPhase, shown) ->
                    when (shownPhase) {
                        DiscoverPhase.Loading -> LoadingState(label = if (shown.isSearch) "Searching" else "Loading events")
                        DiscoverPhase.Error -> MessageState(
                            mood = if (shown.offline) DotFaceMood.Sleepy else DotFaceMood.Sad,
                            title = if (shown.offline) "You're offline" else "Couldn't load events",
                            body = shown.error,
                            actionLabel = "Try again",
                            onAction = onRetry,
                        )
                        DiscoverPhase.Empty -> EmptyState(
                            title = if (shown.isSearch) "No events match “${shown.query.trim()}”" else "Nothing coming up yet",
                            body = if (shown.isSearch) "Try another word, a place or an organiser's name." else "Public events from this Yuyu will show up here.",
                        )
                        DiscoverPhase.Results -> EventList(shown, onLoadMore, onOpenEvent)
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = busy,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                    enter = fadeIn(YuyuSpecs.quick()),
                    exit = fadeOut(YuyuSpecs.quick()),
                    label = "searchBusy",
                ) {
                    DotLoader(size = 32.dp, label = if (state.isSearch) "Searching" else "Loading events")
                }
            }
        }
    }
}

private enum class DiscoverPhase { Loading, Error, Empty, Results }

/** The view once loading has finished. */
private val DiscoverUiState.settledPhase: DiscoverPhase
    get() = when {
        error != null -> DiscoverPhase.Error
        events.isEmpty() -> DiscoverPhase.Empty
        else -> DiscoverPhase.Results
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
    val entrance = rememberEntranceState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = YuyuSpacing.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(state.events, key = { _, event -> event.id }) { index, event ->
            EventCard(
                event,
                onClick = { onOpenEvent(event.organisation.slug, event.slug) },
                modifier = Modifier.listItemMotion(this, entrance, event.id, index),
            )
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
