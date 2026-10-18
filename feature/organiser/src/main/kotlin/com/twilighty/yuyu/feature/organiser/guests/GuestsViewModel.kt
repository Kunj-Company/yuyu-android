package com.twilighty.yuyu.feature.organiser.guests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.GuestQuery
import com.twilighty.yuyu.core.data.repository.GuestsRepository
import com.twilighty.yuyu.core.data.repository.OrganiserEventsRepository
import com.twilighty.yuyu.core.network.model.GuestDto
import com.twilighty.yuyu.core.network.model.GuestFilter
import com.twilighty.yuyu.feature.organiser.common.RsvpStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GuestStatusFilter(val label: String, val apiValue: String) {
    All("All", GuestFilter.STATUS_ALL),
    Going("Going", RsvpStatus.CONFIRMED),
    Waitlist("Waitlist", RsvpStatus.WAITLISTED),
    Pending("Pending", RsvpStatus.PENDING_APPROVAL),
    Declined("Declined", RsvpStatus.REJECTED),
}

enum class CheckedInFilter(val label: String, val apiValue: String) {
    Any("Anyone", GuestFilter.CHECKED_IN_ANY),
    Yes("Checked in", GuestFilter.CHECKED_IN_YES),
    No("Not checked in", GuestFilter.CHECKED_IN_NO),
}

/** A removal the person can still undo (the server keeps it for five minutes). */
data class UndoOffer(val undoId: String, val guestName: String)

data class AddGuestForm(
    val open: Boolean = false,
    val saving: Boolean = false,
    val nameError: String? = null,
    val emailError: String? = null,
    val error: String? = null,
)

/** What a guest row may offer; read-only for check-in-only helpers. */
data class GuestRowActions(val approveDecline: Boolean, val promote: Boolean, val remove: Boolean) {
    val any: Boolean get() = approveDecline || promote || remove

    companion object {
        val None = GuestRowActions(approveDecline = false, promote = false, remove = false)

        fun of(guest: GuestDto, canManage: Boolean): GuestRowActions = if (!canManage) {
            None
        } else {
            GuestRowActions(
                approveDecline = guest.status == RsvpStatus.PENDING_APPROVAL,
                promote = guest.status == RsvpStatus.WAITLISTED,
                remove = true,
            )
        }
    }
}

data class GuestsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val eventTitle: String? = null,
    val timezone: String = "UTC",
    /** `manageRegistrations`: approve, decline, give a spot, add and remove. */
    val canManage: Boolean = false,
    /** `checkIn`: check a confirmed guest in from their detail. */
    val canCheckIn: Boolean = false,
    val status: GuestStatusFilter = GuestStatusFilter.All,
    val checkedIn: CheckedInFilter = CheckedInFilter.Any,
    val query: String = "",
    val guests: List<GuestDto> = emptyList(),
    val nextCursor: String? = null,
    val error: String? = null,
    /** Rows with a lifecycle call in flight. */
    val busyIds: Set<String> = emptySet(),
    val confirmRemove: GuestDto? = null,
    val addGuest: AddGuestForm = AddGuestForm(),
    val message: String? = null,
    val undoOffer: UndoOffer? = null,
) {
    val hasMore: Boolean get() = nextCursor != null
    val filtersActive: Boolean get() = status != GuestStatusFilter.All || checkedIn != CheckedInFilter.Any || query.isNotBlank()

    fun actionsFor(guest: GuestDto): GuestRowActions = GuestRowActions.of(guest, canManage)
}

@HiltViewModel
class GuestsViewModel @Inject constructor(
    private val guestsRepository: GuestsRepository,
    private val eventsRepository: OrganiserEventsRepository,
) : ViewModel() {
    private val state = MutableStateFlow(GuestsUiState())
    val uiState: StateFlow<GuestsUiState> = state.asStateFlow()

    private var orgSlug = ""
    private var eventId = ""
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var generation = 0

    fun start(orgSlug: String, eventId: String) {
        if (this.orgSlug == orgSlug && this.eventId == eventId) return
        this.orgSlug = orgSlug
        this.eventId = eventId
        viewModelScope.launch {
            val result = eventsRepository.event(orgSlug, eventId)
            if (result is AppResult.Success) {
                val detail = result.data
                state.update {
                    it.copy(
                        eventTitle = detail.event.title,
                        timezone = detail.event.timezone,
                        canManage = detail.myPermissions.manageRegistrations,
                        canCheckIn = detail.myPermissions.checkIn,
                    )
                }
            }
        }
        reload()
    }

    fun setStatus(filter: GuestStatusFilter) {
        if (filter == state.value.status) return
        state.update { it.copy(status = filter) }
        reload()
    }

    fun setCheckedIn(filter: CheckedInFilter) {
        if (filter == state.value.checkedIn) return
        state.update { it.copy(checkedIn = filter) }
        reload()
    }

    /** Search is debounced so typing doesn't fire a request per keystroke. */
    fun onQueryChange(query: String) {
        if (query == state.value.query) return
        state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            reload()
        }
    }

    fun refresh() = reload(refreshing = true)

    fun retry() = reload()

    fun loadMore() {
        val s = state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore || s.loading) return
        val gen = generation
        state.update { it.copy(loadingMore = true) }
        loadJob = viewModelScope.launch {
            val result = guestsRepository.guests(orgSlug, eventId, query(cursor))
            if (gen != generation) return@launch
            state.update {
                when (result) {
                    is AppResult.Success -> {
                        val known = it.guests.map(GuestDto::rsvpId).toSet()
                        it.copy(
                            loadingMore = false,
                            guests = it.guests + result.data.guests.filterNot { g -> g.rsvpId in known },
                            nextCursor = result.data.nextCursor,
                        )
                    }
                    is AppResult.Failure -> it.copy(loadingMore = false, message = result.error.message)
                }
            }
        }
    }

    private fun query(cursor: String? = null) = state.value.let {
        GuestQuery(status = it.status.apiValue, checkedIn = it.checkedIn.apiValue, search = it.query.trim().ifEmpty { null }, cursor = cursor)
    }

    private fun reload(refreshing: Boolean = false) {
        if (orgSlug.isEmpty()) return
        loadJob?.cancel()
        val gen = ++generation
        state.update {
            if (refreshing) it.copy(refreshing = true, loadingMore = false) else it.copy(loading = true, loadingMore = false, error = null)
        }
        loadJob = viewModelScope.launch {
            val result = guestsRepository.guests(orgSlug, eventId, query())
            if (gen != generation) return@launch
            state.update {
                when (result) {
                    is AppResult.Success -> it.copy(
                        loading = false,
                        refreshing = false,
                        guests = result.data.guests,
                        nextCursor = result.data.nextCursor,
                        error = null,
                    )
                    is AppResult.Failure -> if (refreshing && it.guests.isNotEmpty()) {
                        it.copy(refreshing = false, message = result.error.message)
                    } else {
                        it.copy(loading = false, refreshing = false, guests = emptyList(), nextCursor = null, error = result.error.message)
                    }
                }
            }
        }
    }

    fun approve(guest: GuestDto) = lifecycle(guest, RsvpStatus.CONFIRMED, "Approved") { guestsRepository.approve(orgSlug, eventId, it) }

    fun decline(guest: GuestDto) = lifecycle(guest, RsvpStatus.REJECTED, "Declined") { guestsRepository.reject(orgSlug, eventId, it) }

    fun promote(guest: GuestDto) = lifecycle(guest, RsvpStatus.CONFIRMED, "Spot given") { guestsRepository.promote(orgSlug, eventId, it) }

    /** Optimistically shows [optimisticStatus], then reconciles with the server's guest or rolls back. */
    private fun lifecycle(
        guest: GuestDto,
        optimisticStatus: String,
        successMessage: String,
        call: suspend (String) -> AppResult<GuestDto>,
    ) {
        val id = guest.rsvpId
        if (id in state.value.busyIds || !state.value.canManage) return
        val previous = state.value.guests.firstOrNull { it.rsvpId == id } ?: guest
        state.update { s ->
            s.copy(guests = s.guests.replace(id) { it.copy(status = optimisticStatus) }, busyIds = s.busyIds + id)
        }
        viewModelScope.launch {
            val result = call(id)
            state.update { s ->
                when (result) {
                    is AppResult.Success -> s.copy(
                        guests = s.guests.replace(id) { result.data },
                        busyIds = s.busyIds - id,
                        message = successMessage,
                    )
                    is AppResult.Failure -> s.copy(
                        guests = s.guests.replace(id) { previous },
                        busyIds = s.busyIds - id,
                        message = result.error.message,
                    )
                }
            }
        }
    }

    fun askRemove(guest: GuestDto?) = state.update { it.copy(confirmRemove = guest) }

    fun confirmRemove() {
        val guest = state.value.confirmRemove ?: return
        val id = guest.rsvpId
        val index = state.value.guests.indexOfFirst { it.rsvpId == id }
        state.update { s -> s.copy(confirmRemove = null, guests = s.guests.filterNot { it.rsvpId == id }, busyIds = s.busyIds + id) }
        viewModelScope.launch {
            when (val result = guestsRepository.delete(orgSlug, eventId, id)) {
                is AppResult.Success -> state.update {
                    it.copy(busyIds = it.busyIds - id, undoOffer = UndoOffer(result.data, guest.displayName))
                }
                is AppResult.Failure -> state.update { s ->
                    val restored = if (s.guests.any { it.rsvpId == id }) {
                        s.guests
                    } else {
                        s.guests.toMutableList().apply { add(index.coerceIn(0, size), guest) }
                    }
                    s.copy(guests = restored, busyIds = s.busyIds - id, message = result.error.message)
                }
            }
        }
    }

    /** Called when a guest was removed from the detail screen; offers the same undo. */
    fun onGuestRemoved(rsvpId: String, undoId: String, guestName: String) {
        state.update { s -> s.copy(guests = s.guests.filterNot { it.rsvpId == rsvpId }, undoOffer = UndoOffer(undoId, guestName)) }
    }

    /** Called when the detail screen changed a guest (approve, check-in…). */
    fun onGuestUpdated(guest: GuestDto) {
        state.update { s -> s.copy(guests = s.guests.replace(guest.rsvpId) { guest }) }
    }

    fun undoRemove(offer: UndoOffer) {
        state.update { it.copy(undoOffer = null) }
        viewModelScope.launch {
            when (val result = guestsRepository.restore(orgSlug, eventId, offer.undoId)) {
                is AppResult.Success -> {
                    state.update { it.copy(message = "${offer.guestName} restored") }
                    reload(refreshing = true)
                }
                is AppResult.Failure -> state.update {
                    it.copy(message = if (result.error is AppError.NotFound) "This removal can no longer be undone." else result.error.message)
                }
            }
        }
    }

    fun undoOfferShown() = state.update { it.copy(undoOffer = null) }

    fun messageShown() = state.update { it.copy(message = null) }

    fun openAddGuest(open: Boolean) = state.update { it.copy(addGuest = AddGuestForm(open = open)) }

    fun addGuest(name: String, email: String) {
        if (state.value.addGuest.saving) return
        val nameError = if (name.isBlank()) "Enter the guest's name." else null
        val emailError = when {
            email.isBlank() -> "Enter the guest's email address."
            !EMAIL.matches(email.trim()) -> "Enter a valid email address."
            else -> null
        }
        if (nameError != null || emailError != null) {
            state.update { it.copy(addGuest = it.addGuest.copy(nameError = nameError, emailError = emailError, error = null)) }
            return
        }
        state.update { it.copy(addGuest = it.addGuest.copy(saving = true, nameError = null, emailError = null, error = null)) }
        viewModelScope.launch {
            when (val result = guestsRepository.add(orgSlug, eventId, name, email)) {
                is AppResult.Success -> state.update { s ->
                    s.copy(
                        addGuest = AddGuestForm(),
                        guests = listOf(result.data) + s.guests.filterNot { it.rsvpId == result.data.rsvpId },
                        message = "${result.data.displayName} added",
                    )
                }
                is AppResult.Failure -> {
                    val error = result.error
                    val fields = (error as? AppError.InvalidRequest)?.fieldErrors.orEmpty()
                    state.update {
                        it.copy(
                            addGuest = it.addGuest.copy(
                                saving = false,
                                nameError = fields["name"]?.firstOrNull(),
                                emailError = fields["email"]?.firstOrNull(),
                                error = if (fields.containsKey("name") || fields.containsKey("email")) null else error.message,
                            ),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

internal inline fun List<GuestDto>.replace(rsvpId: String, transform: (GuestDto) -> GuestDto): List<GuestDto> =
    map { if (it.rsvpId == rsvpId) transform(it) else it }
