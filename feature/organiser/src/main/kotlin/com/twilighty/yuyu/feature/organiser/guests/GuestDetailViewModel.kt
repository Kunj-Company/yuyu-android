package com.twilighty.yuyu.feature.organiser.guests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.CheckInRepository
import com.twilighty.yuyu.core.data.repository.GuestsRepository
import com.twilighty.yuyu.core.data.repository.OrganiserEventsRepository
import com.twilighty.yuyu.core.network.model.GuestDetailDto
import com.twilighty.yuyu.core.network.model.GuestDto
import com.twilighty.yuyu.feature.organiser.common.RsvpStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A finished removal, handed back to the list so it can offer Undo. */
data class RemovedGuest(val rsvpId: String, val undoId: String, val guestName: String)

data class GuestDetailUiState(
    val loading: Boolean = true,
    val guest: GuestDetailDto? = null,
    val timezone: String = "UTC",
    val canManage: Boolean = false,
    val canCheckIn: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val confirmRemove: Boolean = false,
    val message: String? = null,
    /** Latest server copy of the guest, for the list to reconcile. */
    val updated: GuestDto? = null,
    val removed: RemovedGuest? = null,
) {
    val actions: GuestRowActions
        get() = guest?.let { GuestRowActions.of(it.toGuest(), canManage) } ?: GuestRowActions.None

    /** Check-in from here: checkIn permission, confirmed and not yet in. */
    val showCheckIn: Boolean
        get() = canCheckIn && guest?.status == RsvpStatus.CONFIRMED && guest.checkedInAt == null
}

internal fun GuestDetailDto.toGuest() = GuestDto(rsvpId, displayName, email, status, checkedInAt, registeredAt, isGuest)

private fun GuestDetailDto.merge(g: GuestDto) =
    copy(displayName = g.displayName, email = g.email, status = g.status, checkedInAt = g.checkedInAt, registeredAt = g.registeredAt, isGuest = g.isGuest)

@HiltViewModel
class GuestDetailViewModel @Inject constructor(
    private val guestsRepository: GuestsRepository,
    private val eventsRepository: OrganiserEventsRepository,
    private val checkInRepository: CheckInRepository,
) : ViewModel() {
    private val state = MutableStateFlow(GuestDetailUiState())
    val uiState: StateFlow<GuestDetailUiState> = state.asStateFlow()

    private var orgSlug = ""
    private var eventId = ""
    private var rsvpId = ""

    fun start(orgSlug: String, eventId: String, rsvpId: String) {
        if (this.orgSlug == orgSlug && this.eventId == eventId && this.rsvpId == rsvpId) return
        this.orgSlug = orgSlug
        this.eventId = eventId
        this.rsvpId = rsvpId
        load()
    }

    fun retry() = load()

    private fun load() {
        state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val event = async { eventsRepository.event(orgSlug, eventId) }
            val guest = guestsRepository.guest(orgSlug, eventId, rsvpId)
            val detail = (event.await() as? AppResult.Success)?.data
            state.update {
                val base = if (detail != null) {
                    it.copy(
                        timezone = detail.event.timezone,
                        canManage = detail.myPermissions.manageRegistrations,
                        canCheckIn = detail.myPermissions.checkIn,
                    )
                } else {
                    it
                }
                when (guest) {
                    is AppResult.Success -> base.copy(loading = false, guest = guest.data)
                    is AppResult.Failure -> base.copy(loading = false, error = guest.error.message)
                }
            }
        }
    }

    fun approve() = lifecycle(RsvpStatus.CONFIRMED, "Approved") { guestsRepository.approve(orgSlug, eventId, rsvpId) }

    fun decline() = lifecycle(RsvpStatus.REJECTED, "Declined") { guestsRepository.reject(orgSlug, eventId, rsvpId) }

    fun promote() = lifecycle(RsvpStatus.CONFIRMED, "Spot given") { guestsRepository.promote(orgSlug, eventId, rsvpId) }

    private fun lifecycle(optimisticStatus: String, successMessage: String, call: suspend () -> AppResult<GuestDto>) {
        val previous = state.value.guest ?: return
        if (state.value.busy || !state.value.canManage) return
        state.update { it.copy(busy = true, guest = previous.copy(status = optimisticStatus)) }
        viewModelScope.launch {
            val result = call()
            state.update {
                when (result) {
                    is AppResult.Success -> it.copy(
                        busy = false,
                        guest = (it.guest ?: previous).merge(result.data),
                        updated = result.data,
                        message = successMessage,
                    )
                    is AppResult.Failure -> it.copy(busy = false, guest = previous, message = result.error.message)
                }
            }
        }
    }

    fun checkIn() {
        val guest = state.value.guest ?: return
        if (state.value.busy || !state.value.showCheckIn) return
        state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = checkInRepository.checkInByRsvp(orgSlug, eventId, guest.rsvpId)) {
                is AppResult.Success -> state.update {
                    val updated = (it.guest ?: guest).copy(checkedInAt = result.data.checkedInAt, status = result.data.status)
                    it.copy(
                        busy = false,
                        guest = updated,
                        updated = updated.toGuest(),
                        message = if (result.data.alreadyCheckedIn) "${guest.displayName} was already checked in" else "${guest.displayName} checked in",
                    )
                }
                is AppResult.Failure -> state.update { it.copy(busy = false, message = result.error.message) }
            }
        }
    }

    fun askRemove(show: Boolean) = state.update { it.copy(confirmRemove = show) }

    fun confirmRemove() {
        val guest = state.value.guest ?: return
        state.update { it.copy(confirmRemove = false, busy = true) }
        viewModelScope.launch {
            when (val result = guestsRepository.delete(orgSlug, eventId, guest.rsvpId)) {
                is AppResult.Success -> state.update {
                    it.copy(busy = false, removed = RemovedGuest(guest.rsvpId, result.data, guest.displayName))
                }
                is AppResult.Failure -> state.update { it.copy(busy = false, message = result.error.message) }
            }
        }
    }

    fun messageShown() = state.update { it.copy(message = null) }

    fun updateConsumed() = state.update { it.copy(updated = null) }

    fun removedConsumed() = state.update { it.copy(removed = null) }
}
