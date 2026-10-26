package com.twilighty.yuyu.feature.attendee.discover

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.PublicEventsRepository
import com.twilighty.yuyu.core.network.model.EventSummaryDto
import com.twilighty.yuyu.feature.attendee.navigation.DiscoverRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class DiscoverUiState(
    val query: String = "",
    val loading: Boolean = true,
    val events: List<EventSummaryDto> = emptyList(),
    val nextCursor: String? = null,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
    val loadMoreError: String? = null,
) {
    val isSearch: Boolean get() = query.isNotBlank()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PublicEventsRepository,
) : ViewModel() {
    private val initialQuery = runCatching { savedStateHandle.toRoute<DiscoverRoute>().query }.getOrNull().orEmpty()
    private val state = MutableStateFlow(DiscoverUiState(query = initialQuery))
    val uiState: StateFlow<DiscoverUiState> = state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(initialQuery)
        viewModelScope.launch {
            state.map { it.query.trim() }
                .distinctUntilChanged()
                .drop(1)
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .collect { load(it) }
        }
    }

    fun onQueryChange(query: String) {
        state.update { it.copy(query = query) }
    }

    /** Search now (keyboard search action) without waiting for the debounce. */
    fun searchNow() = load(state.value.query.trim())

    fun retry() = load(state.value.query.trim())

    private fun load(query: String) {
        loadJob?.cancel()
        state.update { it.copy(loading = true, error = null, loadMoreError = null) }
        loadJob = viewModelScope.launch {
            val result = if (query.isBlank()) repository.discover(limit = PAGE_SIZE) else repository.search(query)
            when (result) {
                is AppResult.Success -> state.update {
                    it.copy(loading = false, events = result.data.events.distinctBy { e -> e.id }, nextCursor = result.data.nextCursor, offline = false)
                }
                is AppResult.Failure -> state.update {
                    it.copy(loading = false, events = emptyList(), nextCursor = null, error = result.error.message, offline = result.error is AppError.Network)
                }
            }
        }
    }

    /** Next discover page (search results are a single page). */
    fun loadMore() {
        val current = state.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore || current.isSearch) return
        state.update { it.copy(loadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            when (val result = repository.discover(cursor = cursor, limit = PAGE_SIZE)) {
                is AppResult.Success -> state.update { s ->
                    val known = s.events.map { it.id }.toSet()
                    s.copy(
                        loadingMore = false,
                        events = s.events + result.data.events.filter { it.id !in known },
                        nextCursor = result.data.nextCursor,
                    )
                }
                is AppResult.Failure -> state.update { it.copy(loadingMore = false, loadMoreError = result.error.message) }
            }
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 350L
        const val PAGE_SIZE = 20
    }
}
