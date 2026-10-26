package com.twilighty.yuyu.feature.organiser.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.OrganiserEventsRepository
import com.twilighty.yuyu.core.data.repository.ScheduleRepository
import com.twilighty.yuyu.core.network.model.OrgEventDetailResponseDto
import com.twilighty.yuyu.feature.organiser.common.WebLinks
import com.twilighty.yuyu.feature.organiser.common.canSeeGuests
import com.twilighty.yuyu.feature.organiser.common.hasDayOfControls
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which big action tiles the event hub shows, from `myPermissions`. */
data class EventActions(
    val checkIn: Boolean = false,
    val guests: Boolean = false,
    val emails: Boolean = false,
    val dayOf: Boolean = false,
    /** Every organiser who can open the event reads the programme. */
    val schedule: Boolean = false,
)

data class EventHomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val detail: OrgEventDetailResponseDto? = null,
    val error: String? = null,
    /** A programme saved on this device, offered when the event can't load (offline). */
    val hasSavedSchedule: Boolean = false,
) {
    val actions: EventActions
        get() = detail?.myPermissions?.let {
            EventActions(checkIn = it.checkIn, guests = it.canSeeGuests, emails = it.sendEmails, dayOf = it.hasDayOfControls, schedule = true)
        } ?: EventActions()
}

@HiltViewModel
class EventHomeViewModel @Inject constructor(
    private val repository: OrganiserEventsRepository,
    private val webLinks: WebLinks,
    private val schedules: ScheduleRepository,
) : ViewModel() {
    private val state = MutableStateFlow(EventHomeUiState())
    val uiState: StateFlow<EventHomeUiState> = state.asStateFlow()

    private var orgSlug: String = ""
    private var eventId: String = ""

    fun start(orgSlug: String, eventId: String) {
        if (this.orgSlug == orgSlug && this.eventId == eventId) return
        this.orgSlug = orgSlug
        this.eventId = eventId
        viewModelScope.launch {
            schedules.observe(orgSlug, eventId).collect { saved -> state.update { it.copy(hasSavedSchedule = saved != null) } }
        }
        load(refreshing = false)
    }

    /** Re-reads counts, e.g. when coming back from check-in or guests. */
    fun refresh() = load(refreshing = true)

    fun retry() = load(refreshing = false)

    fun dashboardUrl(): String? = webLinks.eventDashboard(orgSlug, eventId)

    private fun load(refreshing: Boolean) {
        if (orgSlug.isEmpty()) return
        state.update { if (refreshing) it.copy(refreshing = true) else it.copy(loading = it.detail == null, error = null) }
        viewModelScope.launch {
            val result = repository.event(orgSlug, eventId)
            state.update {
                when (result) {
                    is AppResult.Success -> it.copy(loading = false, refreshing = false, detail = result.data, error = null)
                    is AppResult.Failure -> it.copy(loading = false, refreshing = false, error = result.error.message)
                }
            }
        }
    }
}
