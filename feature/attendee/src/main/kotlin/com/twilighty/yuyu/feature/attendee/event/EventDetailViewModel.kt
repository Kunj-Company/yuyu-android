package com.twilighty.yuyu.feature.attendee.event

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.AuthRepository
import com.twilighty.yuyu.core.data.repository.PublicEventsRepository
import com.twilighty.yuyu.core.network.model.PublicEventResponseDto
import com.twilighty.yuyu.core.network.model.RsvpStatus
import com.twilighty.yuyu.feature.attendee.navigation.EventDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the sticky register bar offers (DESIGN §4.10 states). */
sealed interface RegisterCta {
    /** Open for sign-ups: [label] is "Register — it's free", "Join the waitlist" or "Request to join". */
    data class Open(val label: String, val waitlist: Boolean, val approval: Boolean) : RegisterCta

    /** Registration closed (disabled), with the server's reason. */
    data class Closed(val reason: String?) : RegisterCta

    /** Already registered and confirmed: links to the ticket. */
    data class Registered(val ticketToken: String?) : RegisterCta

    /** Registered but waiting: waitlist or approval. */
    data class Waiting(val status: String) : RegisterCta
}

fun registerCta(data: PublicEventResponseDto): RegisterCta {
    val event = data.event
    val mine = data.registration.myRsvp
    if (mine != null && mine.status != RsvpStatus.CANCELLED) {
        return when (mine.status) {
            RsvpStatus.CONFIRMED -> RegisterCta.Registered(mine.ticketToken)
            RsvpStatus.WAITLISTED, RsvpStatus.PENDING_APPROVAL -> RegisterCta.Waiting(mine.status)
            else -> RegisterCta.Closed(if (mine.status == RsvpStatus.REJECTED) "Your request to join wasn't approved." else null)
        }
    }
    if (!event.registrationOpen) return RegisterCta.Closed(event.registrationClosedReason)
    val approval = data.registration.requiresApproval
    return when {
        approval -> RegisterCta.Open("Request to join", waitlist = false, approval = true)
        event.registrationFull -> RegisterCta.Open("Join the waitlist", waitlist = true, approval = false)
        else -> RegisterCta.Open("Register — it's free", waitlist = false, approval = false)
    }
}

@Immutable
data class EventDetailUiState(
    val loading: Boolean = true,
    val data: PublicEventResponseDto? = null,
    val error: String? = null,
    val offline: Boolean = false,
    val notFound: Boolean = false,
    val shareUrl: String? = null,
) {
    val cta: RegisterCta? get() = data?.let(::registerCta)
}

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PublicEventsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<EventDetailRoute>()
    private val state = MutableStateFlow(EventDetailUiState(shareUrl = authRepository.webUrl("${route.orgSlug}/${route.eventSlug}")))
    val uiState: StateFlow<EventDetailUiState> = state.asStateFlow()

    init {
        load()
    }

    /** Reloads (also after returning from registration, so `myRsvp` is fresh). */
    fun load() {
        state.update { it.copy(loading = it.data == null, error = null) }
        viewModelScope.launch {
            when (val result = repository.event(route.orgSlug, route.eventSlug)) {
                is AppResult.Success -> state.update { it.copy(loading = false, data = result.data, offline = false, notFound = false) }
                is AppResult.Failure -> state.update {
                    it.copy(
                        loading = false,
                        error = if (it.data == null) result.error.message else null,
                        offline = result.error is AppError.Network,
                        notFound = result.error is AppError.NotFound,
                    )
                }
            }
        }
    }
}
