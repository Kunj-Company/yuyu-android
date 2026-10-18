package com.twilighty.yuyu.feature.broadcasts.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.BroadcastsRepository
import com.twilighty.yuyu.core.network.model.BroadcastContentRequest
import com.twilighty.yuyu.core.network.model.BroadcastPreviewDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PreviewWidth { Mobile, Desktop }
enum class PreviewTab { Email, PlainText }

data class BroadcastPreviewUiState(
    val loading: Boolean = true,
    val preview: BroadcastPreviewDto? = null,
    val error: String? = null,
    val width: PreviewWidth = PreviewWidth.Mobile,
    val tab: PreviewTab = PreviewTab.Email,
    /** A link tapped in the preview, waiting for "Open in browser?" confirmation. */
    val pendingLink: String? = null,
)

/** Server-rendered preview of the Compose draft (`POST …/broadcasts/preview`). */
@HiltViewModel
class BroadcastPreviewViewModel @Inject constructor(
    private val repository: BroadcastsRepository,
) : ViewModel() {
    private val state = MutableStateFlow(BroadcastPreviewUiState())
    val uiState: StateFlow<BroadcastPreviewUiState> = state.asStateFlow()

    private var lastRequest: Triple<String, String, BroadcastContentRequest>? = null
    private var job: Job? = null

    /** Renders [content]; repeated calls with the same content are ignored. */
    fun load(orgSlug: String, eventId: String, content: BroadcastContentRequest, force: Boolean = false) {
        val request = Triple(orgSlug, eventId, content)
        if (!force && request == lastRequest && state.value.error == null) return
        lastRequest = request
        job?.cancel()
        state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            when (val result = repository.preview(orgSlug, eventId, content)) {
                is AppResult.Success -> state.update { it.copy(loading = false, preview = result.data) }
                is AppResult.Failure -> state.update { it.copy(loading = false, error = result.error.message) }
            }
        }
    }

    fun retry() {
        lastRequest?.let { (org, event, content) -> load(org, event, content, force = true) }
    }

    fun setWidth(width: PreviewWidth) = state.update { it.copy(width = width) }
    fun setTab(tab: PreviewTab) = state.update { it.copy(tab = tab) }
    fun onLinkTapped(url: String) = state.update { it.copy(pendingLink = url) }
    fun dismissLink() = state.update { it.copy(pendingLink = null) }
}
