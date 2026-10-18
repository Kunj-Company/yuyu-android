package com.twilighty.yuyu.feature.broadcasts.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.BroadcastsRepository
import com.twilighty.yuyu.core.network.model.BroadcastDesign
import com.twilighty.yuyu.core.network.model.BroadcastDesignDto
import com.twilighty.yuyu.core.network.model.BroadcastHistoryDto
import com.twilighty.yuyu.feature.broadcasts.BroadcastCopy
import com.twilighty.yuyu.feature.broadcasts.draft.BroadcastDraft
import com.twilighty.yuyu.feature.broadcasts.draft.BroadcastDraftStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where "Reuse" puts a previous email: the open Compose screen's draft. */
interface ReuseTarget {
    fun hasNonEmptyDraft(): Boolean
    fun applyHistory(row: BroadcastHistoryDto)
}

data class BroadcastHistoryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val history: List<BroadcastHistoryDto> = emptyList(),
    val designs: List<BroadcastDesignDto> = emptyList(),
    /** Row waiting for "Replace your draft?" confirmation. */
    val pendingReuse: BroadcastHistoryDto? = null,
    /** Set once a row was loaded into the draft; the screen then returns to Compose. */
    val reused: Boolean = false,
) {
    fun designLabel(id: String?): String =
        designs.firstOrNull { it.id == id }?.label ?: (id ?: BroadcastDesign.CLASSIC).replaceFirstChar { it.uppercase() }
}

/** Emails already sent for the event (`meta.history`, latest 20). */
@HiltViewModel
class BroadcastHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BroadcastsRepository,
    private val draftStore: BroadcastDraftStore,
) : ViewModel() {
    val orgSlug: String = checkNotNull(savedStateHandle.get<String>("orgSlug")) { "orgSlug missing" }
    val eventId: String = checkNotNull(savedStateHandle.get<String>("eventId")) { "eventId missing" }

    private val state = MutableStateFlow(BroadcastHistoryUiState())
    val uiState: StateFlow<BroadcastHistoryUiState> = state.asStateFlow()

    init {
        load()
    }

    fun load() {
        state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.meta(orgSlug, eventId)) {
                is AppResult.Success -> state.update { it.copy(loading = false, history = result.data.history, designs = result.data.designs) }
                is AppResult.Failure -> state.update { it.copy(loading = false, error = result.error.message) }
            }
        }
    }

    /**
     * Reuse [row]. With an open Compose screen ([target]) its draft is replaced;
     * otherwise the stored draft is, and Compose restores it when opened.
     * Asks first when that draft has any content.
     */
    fun requestReuse(row: BroadcastHistoryDto, target: ReuseTarget?) {
        viewModelScope.launch {
            val nonEmpty = target?.hasNonEmptyDraft() ?: (draftStore.load(orgSlug, eventId)?.isBlank() == false)
            if (nonEmpty) state.update { it.copy(pendingReuse = row) } else reuse(row, target)
        }
    }

    fun confirmReuse(target: ReuseTarget?) {
        val row = state.value.pendingReuse ?: return
        state.update { it.copy(pendingReuse = null) }
        viewModelScope.launch { reuse(row, target) }
    }

    fun dismissReuse() = state.update { it.copy(pendingReuse = null) }

    fun onReusedHandled() = state.update { it.copy(reused = false) }

    private suspend fun reuse(row: BroadcastHistoryDto, target: ReuseTarget?) {
        if (target != null) {
            target.applyHistory(row)
        } else {
            val existing = draftStore.load(orgSlug, eventId)
            draftStore.save(orgSlug, eventId, row.toDraft(fallbackBody = existing?.bodyHtml.orEmpty()))
        }
        state.update { it.copy(reused = true) }
    }
}

internal fun BroadcastHistoryDto.toDraft(fallbackBody: String = ""): BroadcastDraft = BroadcastDraft(
    subject = subject.take(BroadcastCopy.SUBJECT_MAX),
    bodyHtml = bodyHtml ?: fallbackBody,
    statuses = BroadcastCopy.STATUSES.filter { it in audience.statuses },
    attendance = audience.attendance,
    design = design ?: BroadcastDesign.CLASSIC,
    templateId = null,
)
