package com.twilighty.yuyu.feature.attendee.ticket

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.twilighty.yuyu.core.common.dispatchers.ApplicationScope
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.AuthRepository
import com.twilighty.yuyu.core.data.repository.MeRepository
import com.twilighty.yuyu.core.data.repository.SessionState
import com.twilighty.yuyu.core.network.api.YuyuWebApi
import com.twilighty.yuyu.core.network.model.RegistrationDto
import com.twilighty.yuyu.core.network.model.RsvpStatus
import com.twilighty.yuyu.feature.attendee.navigation.TicketRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class TicketUiState(
    val loading: Boolean = true,
    /** Event and attendee details; null when this device knows nothing beyond the token. */
    val context: TicketContext? = null,
    val checkedIn: Boolean = false,
    val checkedInAt: String? = null,
    /** The check-in happened while the ticket was on screen: play the tear-away once. */
    val justCheckedIn: Boolean = false,
    val liveMode: LiveMode = LiveMode.Idle,
    val shareUrl: String? = null,
    val askCancel: Boolean = false,
    val cancelling: Boolean = false,
    val cancelled: Boolean = false,
    val message: String? = null,
) {
    /** A token without known details is treated as a confirmed ticket (tokens are only issued when confirmed). */
    val isConfirmed: Boolean get() = context == null || context.status == RsvpStatus.CONFIRMED

    /** The QR is shown only for a confirmed ticket that hasn't been used. */
    val showQr: Boolean get() = !loading && isConfirmed && !checkedIn

    val canCancel: Boolean get() = context?.canCancel == true && context.rsvpId != null && !checkedIn
}

@HiltViewModel
class TicketViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val meRepository: MeRepository,
    private val authRepository: AuthRepository,
    private val recentTickets: RecentTicketStore,
    private val watcher: TicketLiveWatcher,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    /** The secret ticket token. Never logged. */
    val token: String = savedStateHandle.toRoute<TicketRoute>().token

    private val state = MutableStateFlow(TicketUiState(shareUrl = authRepository.webUrl(YuyuWebApi.ticketPagePath(token))))
    val uiState: StateFlow<TicketUiState> = state.asStateFlow()

    private var watchJob: Job? = null
    private var visible = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val userName = (authRepository.sessionState.firstOrNull { it !is SessionState.Loading } as? SessionState.SignedIn)?.user?.name
        var context = findCached(userName) ?: recentTickets.get(token)
        if (context == null) {
            // Not cached yet (e.g. a link from an email): refresh the user's registrations once.
            if (meRepository.registrations() is AppResult.Success) context = findCached(userName)
        }
        state.update {
            it.copy(
                loading = false,
                context = context,
                checkedIn = it.checkedIn || context?.checkedInAt != null,
                checkedInAt = it.checkedInAt ?: context?.checkedInAt,
            )
        }
        if (visible) startWatching()
    }

    private suspend fun findCached(userName: String?): TicketContext? {
        val cached = meRepository.cachedRegistrations().first()
        val reg = (cached.upcoming + cached.past).firstOrNull { it.ticketToken == token } ?: return null
        return reg.toContext(userName)
    }

    /** Call while the ticket is on screen (lifecycle STARTED). */
    fun onVisible() {
        visible = true
        if (!state.value.loading) startWatching()
    }

    /** Call when the ticket leaves the screen: stops the live connection. */
    fun onHidden() {
        visible = false
        watchJob?.cancel()
        watchJob = null
        state.update { it.copy(liveMode = LiveMode.Idle) }
    }

    private fun startWatching() {
        val s = state.value
        if (watchJob?.isActive == true || s.checkedIn || !s.isConfirmed) return
        watchJob = viewModelScope.launch {
            watcher.watch(token).collect { update ->
                when (update) {
                    is LiveUpdate.Mode -> state.update { it.copy(liveMode = update.mode) }
                    is LiveUpdate.CheckedIn -> {
                        state.update {
                            it.copy(
                                checkedIn = true,
                                checkedInAt = update.checkedInAt ?: it.checkedInAt,
                                justCheckedIn = update.live && !it.checkedIn,
                                liveMode = LiveMode.Idle,
                            )
                        }
                        // Update the offline cache so the ticket stays torn without a connection.
                        appScope.launch { meRepository.registrations() }
                    }
                }
            }
        }
    }

    /** The tear-away animation finished. */
    fun onCheckInAnimationDone() {
        state.update { it.copy(justCheckedIn = false) }
    }

    fun askCancel() {
        if (state.value.canCancel) state.update { it.copy(askCancel = true) }
    }

    fun dismissCancel() {
        if (!state.value.cancelling) state.update { it.copy(askCancel = false) }
    }

    fun confirmCancel() {
        val rsvpId = state.value.context?.rsvpId ?: return
        state.update { it.copy(cancelling = true) }
        viewModelScope.launch {
            when (val result = meRepository.cancelRegistration(rsvpId)) {
                is AppResult.Success -> {
                    appScope.launch { meRepository.registrations() }
                    state.update { it.copy(cancelling = false, askCancel = false, cancelled = true) }
                }
                is AppResult.Failure -> state.update {
                    it.copy(
                        cancelling = false,
                        askCancel = false,
                        message = if (result.error is AppError.Network) "You're offline. Try cancelling again when you're connected." else result.error.message,
                    )
                }
            }
        }
    }

    fun onMessageShown() = state.update { it.copy(message = null) }

    private fun RegistrationDto.toContext(userName: String?) = TicketContext(
        rsvpId = rsvpId,
        status = status,
        checkedInAt = checkedInAt,
        canCancel = canCancel,
        attendeeName = userName,
        event = event,
    )
}
