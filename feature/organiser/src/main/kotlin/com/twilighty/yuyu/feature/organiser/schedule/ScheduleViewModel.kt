package com.twilighty.yuyu.feature.organiser.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.SavedSchedule
import com.twilighty.yuyu.core.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScheduleUiState(
    /** True until the saved copy (if any) has been read. */
    val loading: Boolean = true,
    val syncing: Boolean = false,
    val saved: SavedSchedule? = null,
    val tracks: List<TrackUi> = emptyList(),
    /** Why the last sync failed; the saved copy, when there is one, stays on screen. */
    val syncError: String? = null,
    val offline: Boolean = false,
    val message: String? = null,
) {
    val isEmpty: Boolean get() = tracks.all { it.sessions.isEmpty() }
}

/**
 * The organiser programme for any organiser of the event (the check-in desk
 * included). The saved copy shows at once, offline too; opening the screen and
 * the sync button refresh it.
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
) : ViewModel() {
    private val state = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = state.asStateFlow()

    private var orgSlug = ""
    private var eventId = ""
    private var observer: Job? = null
    private var syncJob: Job? = null

    fun start(orgSlug: String, eventId: String) {
        if (this.orgSlug == orgSlug && this.eventId == eventId) return
        this.orgSlug = orgSlug
        this.eventId = eventId
        observer?.cancel()
        observer = viewModelScope.launch {
            repository.observe(orgSlug, eventId).collect { saved ->
                state.update { it.copy(loading = false, saved = saved, tracks = saved?.schedule?.toTrackUi().orEmpty()) }
            }
        }
        sync(userAsked = false)
    }

    /** The sync button. */
    fun sync() = sync(userAsked = true)

    private fun sync(userAsked: Boolean) {
        if (orgSlug.isEmpty() || syncJob?.isActive == true) return
        state.update { it.copy(syncing = true) }
        syncJob = viewModelScope.launch {
            when (val result = repository.sync(orgSlug, eventId)) {
                is AppResult.Success -> state.update {
                    it.copy(syncing = false, syncError = null, offline = false, message = if (userAsked) "Schedule synced" else null)
                }
                is AppResult.Failure -> state.update {
                    val offline = result.error is AppError.Network
                    it.copy(
                        syncing = false,
                        syncError = result.error.message,
                        offline = offline,
                        // The banner already says why; a snackbar only answers the button.
                        message = if (userAsked && it.saved != null) result.error.message else null,
                    )
                }
            }
        }
    }

    fun messageShown() = state.update { it.copy(message = null) }
}
