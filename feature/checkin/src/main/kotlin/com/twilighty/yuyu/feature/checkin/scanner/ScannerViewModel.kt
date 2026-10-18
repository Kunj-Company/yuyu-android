package com.twilighty.yuyu.feature.checkin.scanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.CheckInRepository
import com.twilighty.yuyu.core.data.repository.OrganiserEventsRepository
import com.twilighty.yuyu.feature.checkin.connectivity.ConnectivityObserver
import com.twilighty.yuyu.feature.checkin.domain.CheckInCounts
import com.twilighty.yuyu.feature.checkin.domain.DeskMode
import com.twilighty.yuyu.feature.checkin.domain.FeedbackKind
import com.twilighty.yuyu.feature.checkin.domain.PendingItem
import com.twilighty.yuyu.feature.checkin.domain.RecentCheckIn
import com.twilighty.yuyu.feature.checkin.domain.SyncReport
import com.twilighty.yuyu.feature.checkin.domain.SyncReportMapper
import com.twilighty.yuyu.feature.checkin.flow.CheckInFlow
import com.twilighty.yuyu.feature.checkin.flow.LookupState
import com.twilighty.yuyu.feature.checkin.flow.SheetState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScannerUiState(
    val eventTitle: String? = null,
    /** Set when the server says this account can't check guests in for the event. */
    val noAccessMessage: String? = null,
    val mode: DeskMode = DeskMode.Scan,
    val fastMode: Boolean = false,
    val counts: CheckInCounts? = null,
    val recent: List<RecentCheckIn> = emptyList(),
    val online: Boolean = true,
    val pending: List<PendingItem> = emptyList(),
    /** Server `generatedAt` of the offline guest list on this phone, if any. */
    val rosterGeneratedAt: String? = null,
    val rosterCount: Int = 0,
    val downloading: Boolean = false,
    val syncing: Boolean = false,
    val syncReport: SyncReport? = null,
    val message: String? = null,
    val sheet: SheetState = SheetState.Hidden,
    val lookup: LookupState = LookupState(),
) {
    val pendingCount: Int get() = pending.size
    val hasRoster: Boolean get() = rosterCount > 0
    val showOfflineBanner: Boolean get() = !online || pending.isNotEmpty()
}

/**
 * Organiser scanner for one event. Online it uses the mobile check-in API;
 * offline (known from connectivity, or after an IO failure) it falls back to
 * the downloaded roster and queues check-ins for `…/check-in/sync`.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CheckInRepository,
    private val events: OrganiserEventsRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    val orgSlug: String = checkNotNull(savedStateHandle["orgSlug"]) { "orgSlug missing" }
    val eventId: String = checkNotNull(savedStateHandle["eventId"]) { "eventId missing" }

    private data class Local(
        val eventTitle: String? = null,
        val noAccessMessage: String? = null,
        val mode: DeskMode = DeskMode.Scan,
        val fastMode: Boolean = false,
        val counts: CheckInCounts? = null,
        val recent: List<RecentCheckIn> = emptyList(),
        val rosterGeneratedAt: String? = null,
        val rosterCount: Int = 0,
        val downloading: Boolean = false,
        val syncing: Boolean = false,
        val syncReport: SyncReport? = null,
        val message: String? = null,
    )

    private val local = MutableStateFlow(Local())
    private val online = MutableStateFlow(true)
    private var statsJob: Job? = null

    private val flow = CheckInFlow(
        scope = viewModelScope,
        backend = OrganiserCheckInBackend(repository, orgSlug, eventId),
        offline = OrganiserOfflineBackend(repository, orgSlug, eventId),
        isOffline = { !online.value },
        onCheckInsChanged = ::refreshStats,
        onFailure = ::onFailure,
    )

    /** One event per result for haptics and sound. */
    val feedback: SharedFlow<FeedbackKind> = flow.feedback

    private val names: Flow<Map<String, String>> =
        repository.observeRoster(eventId).map { rows -> rows.associate { it.rsvpId to it.displayName } }

    private val pending: Flow<List<PendingItem>> =
        combine(repository.observePending(eventId), names) { queue, byId -> SyncReportMapper.pendingItems(queue, byId) }

    val uiState: StateFlow<ScannerUiState> =
        combine(local, flow.sheet, flow.lookup, pending, online) { l, sheet, lookup, queue, isOnline ->
            ScannerUiState(
                eventTitle = l.eventTitle,
                noAccessMessage = l.noAccessMessage,
                mode = l.mode,
                fastMode = l.fastMode,
                counts = l.counts,
                recent = l.recent,
                online = isOnline,
                pending = queue,
                rosterGeneratedAt = l.rosterGeneratedAt,
                rosterCount = l.rosterCount,
                downloading = l.downloading,
                syncing = l.syncing,
                syncReport = l.syncReport,
                message = l.message,
                sheet = sheet,
                lookup = lookup,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ScannerUiState())

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { now ->
                val wasOnline = online.value
                online.value = now
                if (now && !wasOnline) onReconnected()
            }
        }
        viewModelScope.launch { loadEvent() }
        viewModelScope.launch {
            val info = repository.rosterInfo(eventId)
            local.update { it.copy(rosterGeneratedAt = info.generatedAt, rosterCount = info.count) }
        }
        refreshStats()
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
    fun dismissSyncReport() = local.update { it.copy(syncReport = null) }
    fun messageShown() = local.update { it.copy(message = null) }

    /** Refreshes the counter every 30 s; run while the screen is visible. */
    suspend fun pollStats() {
        while (true) {
            delay(STATS_INTERVAL_MILLIS)
            if (online.value) refreshStats()
        }
    }

    fun refreshStats() {
        if (statsJob?.isActive == true) return
        statsJob = viewModelScope.launch {
            when (val result = repository.stats(orgSlug, eventId)) {
                is AppResult.Success -> local.update { state ->
                    state.copy(
                        counts = CheckInCounts(result.data.checkedIn, result.data.confirmed),
                        recent = result.data.recent.map { RecentCheckIn(it.rsvpId, it.displayName, it.checkedInAt) },
                    )
                }
                is AppResult.Failure -> onFailure(result.error)
            }
        }
    }

    fun downloadRoster() {
        if (local.value.downloading) return
        if (!online.value) {
            local.update { it.copy(message = "Connect to the internet to download the guest list.") }
            return
        }
        local.update { it.copy(downloading = true) }
        viewModelScope.launch {
            when (val result = repository.downloadRoster(orgSlug, eventId)) {
                is AppResult.Success -> local.update {
                    it.copy(
                        downloading = false,
                        rosterGeneratedAt = result.data.generatedAt,
                        rosterCount = result.data.count,
                        message = "Guest list saved on this phone (${result.data.count} ${if (result.data.count == 1) "guest" else "guests"}).",
                    )
                }
                is AppResult.Failure -> local.update { it.copy(downloading = false, message = result.error.message) }
            }
        }
    }

    /** Pushes this event's queued offline check-ins now and shows what happened to each. */
    fun syncNow() {
        if (local.value.syncing) return
        local.update { it.copy(syncing = true) }
        viewModelScope.launch {
            val sent = repository.observePending(eventId).first()
            if (sent.isEmpty()) {
                local.update { it.copy(syncing = false) }
                return@launch
            }
            when (val result = repository.syncPending(orgSlug, eventId)) {
                is AppResult.Success -> {
                    val report = SyncReportMapper.map(sent, result.data, names.first())
                    local.update { it.copy(syncing = false, syncReport = report) }
                    refreshStats()
                }
                is AppResult.Failure -> local.update {
                    it.copy(
                        syncing = false,
                        message = if (result.error is AppError.Network) "Still offline. Check-ins will sync when you're connected." else result.error.message,
                    )
                }
            }
        }
    }

    private suspend fun onReconnected() {
        refreshStats()
        if (repository.observePending(eventId).first().isNotEmpty()) syncNow()
    }

    private suspend fun loadEvent() {
        when (val result = events.event(orgSlug, eventId)) {
            is AppResult.Success -> local.update {
                it.copy(
                    eventTitle = result.data.event.title,
                    noAccessMessage = if (result.data.myPermissions.checkIn) null else NO_ACCESS,
                )
            }
            is AppResult.Failure -> when (val error = result.error) {
                is AppError.Forbidden -> onFailure(error)
                is AppError.NotFound -> local.update { it.copy(noAccessMessage = error.message) }
                else -> Unit // Offline at the door: the scanner still works from the roster.
            }
        }
    }

    private fun onFailure(error: AppError) {
        if (error is AppError.Forbidden) local.update { it.copy(noAccessMessage = NO_ACCESS) }
    }

    companion object {
        const val STATS_INTERVAL_MILLIS = 30_000L
        const val NO_ACCESS = "You don't have check-in access for this event. Ask an organisation admin to give you the check-in permission."
    }
}
