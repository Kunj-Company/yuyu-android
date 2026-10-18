package com.twilighty.yuyu.feature.checkin.station

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.StationKey
import com.twilighty.yuyu.feature.checkin.domain.CheckInCounts
import com.twilighty.yuyu.feature.checkin.domain.DeskMode
import com.twilighty.yuyu.feature.checkin.domain.FeedbackKind
import com.twilighty.yuyu.feature.checkin.domain.RecentCheckIn
import com.twilighty.yuyu.feature.checkin.flow.CheckInFlow
import com.twilighty.yuyu.feature.checkin.flow.LookupState
import com.twilighty.yuyu.feature.checkin.flow.SheetState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StationUiState(
    val orgSlug: String,
    val eventSlug: String,
    val loading: Boolean = true,
    val organisationName: String? = null,
    val organisationLogoUrl: String? = null,
    val eventTitle: String? = null,
    val counts: CheckInCounts? = null,
    val recent: List<RecentCheckIn> = emptyList(),
    val mode: DeskMode = DeskMode.Scan,
    val fastMode: Boolean = false,
    /** The proof cookie is missing or expired: unlock again. */
    val accessLost: Boolean = false,
    val loadError: String? = null,
    val sheet: SheetState = SheetState.Hidden,
    val lookup: LookupState = LookupState(),
)

/** The unlocked door station (kiosk): online-only, like the website's station. */
@HiltViewModel
class StationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: StationSessions,
) : ViewModel() {
    private val key = StationKey(
        checkNotNull(savedStateHandle["orgSlug"]) { "orgSlug missing" },
        checkNotNull(savedStateHandle["eventSlug"]) { "eventSlug missing" },
    )
    private val repository = sessions.get(key)
    private val local = MutableStateFlow(StationUiState(key.orgSlug, key.eventSlug, accessLost = !repository.isUnlocked))
    private var bootstrapJob: Job? = null

    private val flow = CheckInFlow(
        scope = viewModelScope,
        backend = StationCheckInBackend(repository, key),
        onCheckInsChanged = ::refresh,
        onFailure = ::onFailure,
    )

    val feedback: SharedFlow<FeedbackKind> = flow.feedback

    val uiState: StateFlow<StationUiState> = combine(local, flow.sheet, flow.lookup) { l, sheet, lookup ->
        l.copy(sheet = sheet, lookup = lookup)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, local.value)

    init {
        if (repository.isUnlocked) refresh()
    }

    fun setMode(mode: DeskMode) = local.update { it.copy(mode = mode) }

    fun setFastMode(enabled: Boolean) {
        flow.fastMode = enabled
        local.update { it.copy(fastMode = enabled) }
    }

    fun onScanned(raw: String) = flow.onScanned(raw)
    fun submitCode(raw: String) = flow.submitCode(raw)
    fun openGuest(rsvpId: String) = flow.openGuest(rsvpId)
    fun onLookupQuery(query: String) = flow.onLookupQuery(query)
    fun confirm() = flow.confirm()
    fun undo() = flow.undo()
    fun dismissSheet() = flow.dismiss()

    /** Re-reads org, event, counts and recent check-ins (`action: bootstrap`). */
    fun refresh() {
        if (bootstrapJob?.isActive == true) return
        bootstrapJob = viewModelScope.launch {
            when (val result = repository.bootstrap(key)) {
                is AppResult.Success -> {
                    val data = result.data
                    local.update {
                        it.copy(
                            loading = false,
                            loadError = null,
                            organisationName = data.organisationName,
                            organisationLogoUrl = data.organisationLogoUrl,
                            eventTitle = data.eventTitle,
                            counts = CheckInCounts(data.stats.checkedIn, data.stats.confirmed),
                            recent = data.recent.take(RECENT_LIMIT).map { r -> RecentCheckIn(r.rsvpId, r.displayName, r.checkedInAt) },
                        )
                    }
                }
                is AppResult.Failure -> {
                    onFailure(result.error)
                    local.update { it.copy(loading = false, loadError = result.error.message) }
                }
            }
        }
    }

    /** Refreshes the counter every 30 s while the station is visible. */
    suspend fun pollStats() {
        while (true) {
            delay(STATS_INTERVAL_MILLIS)
            if (!local.value.accessLost) refresh()
        }
    }

    /** Exit the station: forget the proof cookie. */
    fun exit() = sessions.end(key)

    private fun onFailure(error: AppError) {
        if (StationErrors.isAccessLost(error)) {
            sessions.end(key)
            local.update { it.copy(accessLost = true) }
        }
    }

    override fun onCleared() {
        sessions.end(key)
    }

    companion object {
        const val RECENT_LIMIT = 10
        const val STATS_INTERVAL_MILLIS = 30_000L
    }
}
