package com.twilighty.yuyu.feature.attendee.register

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
import com.twilighty.yuyu.core.data.repository.PublicEventsRepository
import com.twilighty.yuyu.core.data.repository.SessionState
import com.twilighty.yuyu.core.network.model.EventSummaryDto
import com.twilighty.yuyu.core.network.model.PublicEventDto
import com.twilighty.yuyu.core.network.model.RegistrationFieldDto
import com.twilighty.yuyu.core.network.model.RsvpRequest
import com.twilighty.yuyu.core.network.model.RsvpResultDto
import com.twilighty.yuyu.core.network.model.RsvpStatus
import com.twilighty.yuyu.core.network.model.UserDto
import com.twilighty.yuyu.feature.attendee.event.RegisterCta
import com.twilighty.yuyu.feature.attendee.event.registerCta
import com.twilighty.yuyu.feature.attendee.form.AnswerState
import com.twilighty.yuyu.feature.attendee.form.FormLogic
import com.twilighty.yuyu.feature.attendee.form.FormRules
import com.twilighty.yuyu.feature.attendee.navigation.RegisterRoute
import com.twilighty.yuyu.feature.attendee.ticket.RecentTicketStore
import com.twilighty.yuyu.feature.attendee.ticket.TicketContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What happened after `POST /rsvp`. */
sealed interface RegisterOutcome {
    data class Confirmed(val ticketToken: String?) : RegisterOutcome
    data object Waitlisted : RegisterOutcome
    data object PendingApproval : RegisterOutcome
}

fun RsvpResultDto.toOutcome(): RegisterOutcome = when (status) {
    RsvpStatus.WAITLISTED -> RegisterOutcome.Waitlisted
    RsvpStatus.PENDING_APPROVAL -> RegisterOutcome.PendingApproval
    else -> RegisterOutcome.Confirmed(ticketToken)
}

@Immutable
data class RegisterUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val offline: Boolean = false,
    val event: PublicEventDto? = null,
    val fields: List<RegistrationFieldDto> = emptyList(),
    val requiresApproval: Boolean = false,
    /** Set when registering isn't possible (closed, already registered…). */
    val blocked: RegisterCta? = null,
    /** Signed-in user (name/email prefilled and hidden); null for a guest. */
    val user: UserDto? = null,
    val name: String = "",
    val email: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val answers: AnswerState = AnswerState(),
    val errors: Map<String, String> = emptyMap(),
    val formError: String? = null,
    val submitting: Boolean = false,
    val outcome: RegisterOutcome? = null,
) {
    val isGuest: Boolean get() = user == null
    /** Signed-in accounts without a name must give one (the server needs it). */
    val needsName: Boolean get() = isGuest || user?.name.isNullOrBlank()
    val problemCount: Int get() = errors.size + listOfNotNull(nameError, emailError).size
    val submitLabel: String get() = when {
        requiresApproval -> "Request to join"
        event?.registrationFull == true -> "Join the waitlist"
        else -> "Register — it's free"
    }
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PublicEventsRepository,
    private val authRepository: AuthRepository,
    private val meRepository: MeRepository,
    private val recentTickets: RecentTicketStore,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<RegisterRoute>()
    private val state = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = state.asStateFlow()
    private val rules = FormRules.Registration

    init {
        load()
    }

    fun load() {
        state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val user = (authRepository.sessionState.first { it !is SessionState.Loading } as? SessionState.SignedIn)?.user
            when (val result = repository.event(route.orgSlug, route.eventSlug)) {
                is AppResult.Success -> {
                    val data = result.data
                    val cta = registerCta(data)
                    state.update {
                        it.copy(
                            loading = false,
                            event = data.event,
                            fields = data.registration.fields,
                            requiresApproval = data.registration.requiresApproval,
                            blocked = cta.takeUnless { c -> c is RegisterCta.Open },
                            user = user,
                            name = it.name.ifEmpty { user?.name.orEmpty() },
                            email = it.email.ifEmpty { user?.email.orEmpty() },
                        )
                    }
                }
                is AppResult.Failure -> state.update {
                    it.copy(loading = false, loadError = result.error.message, offline = result.error is AppError.Network)
                }
            }
        }
    }

    fun onNameChange(value: String) = state.update { it.copy(name = value.take(200), nameError = null) }

    fun onEmailChange(value: String) = state.update { it.copy(email = value.trim(), emailError = null) }

    /** Every answer change: re-applies "show only if" (dropping hidden answers) and clears that question's error. */
    fun onAnswersChange(answers: AnswerState) = state.update { s ->
        val visible = FormLogic.visibleKeys(s.fields, answers, rules)
        val changed = s.fields.map { it.key }.filter { key -> answerOf(s.answers, key) != answerOf(answers, key) }.toSet()
        s.copy(answers = answers, errors = s.errors.filterKeys { it in visible && it !in changed }, formError = null)
    }

    private fun answerOf(state: AnswerState, key: String): Any? =
        listOf(state.text[key], state.multi[key], state.bools[key], state.others[key])

    fun submit() {
        val s = state.value
        if (s.submitting || s.event == null) return
        val nameError = if (s.needsName && s.name.isBlank()) "Please enter your name." else null
        val emailError = when {
            !s.isGuest -> null
            s.email.isBlank() -> "Please enter your email address."
            !EMAIL.matches(s.email) -> "Enter a valid email address, like name@example.com."
            else -> null
        }
        val errors = FormLogic.validate(s.fields, s.answers, rules)
        if (nameError != null || emailError != null || errors.isNotEmpty()) {
            state.update { it.copy(nameError = nameError, emailError = emailError, errors = errors, formError = null) }
            return
        }
        state.update { it.copy(submitting = true, nameError = null, emailError = null, errors = emptyMap(), formError = null) }
        val request = RsvpRequest(
            orgSlug = route.orgSlug,
            eventSlug = route.eventSlug,
            name = s.name.trim().ifEmpty { null },
            guestEmail = if (s.isGuest) s.email.trim() else null,
            answers = FormLogic.buildPayload(s.fields, s.answers, rules),
        )
        viewModelScope.launch {
            when (val result = repository.register(request)) {
                is AppResult.Success -> onRegistered(s, result.data)
                is AppResult.Failure -> state.update { applyServerError(it, result.error) }
            }
        }
    }

    private fun onRegistered(s: RegisterUiState, result: RsvpResultDto) {
        val outcome = result.toOutcome()
        val event = s.event
        val token = result.ticketToken
        if (token != null && event != null) {
            recentTickets.put(
                token,
                TicketContext(
                    rsvpId = result.rsvpId,
                    status = result.status,
                    checkedInAt = null,
                    canCancel = true,
                    attendeeName = s.name.trim().ifEmpty { s.user?.name },
                    event = event.toSummary(),
                ),
            )
        }
        // Refresh the offline ticket cache (and reminders) without holding up the screen.
        if (!s.isGuest) appScope.launch { meRepository.registrations() }
        state.update { it.copy(submitting = false, outcome = outcome) }
    }

    /** Maps the server's error onto the form: field errors by key, answer messages by question label. */
    internal fun applyServerError(s: RegisterUiState, error: AppError): RegisterUiState {
        val fieldErrors = (error as? AppError.InvalidRequest)?.fieldErrors.orEmpty()
        var nameError: String? = null
        var emailError: String? = null
        val errors = LinkedHashMap<String, String>()
        val keys = s.fields.map { it.key }.toSet()
        var unmatched = false
        fieldErrors.forEach { (rawKey, messages) ->
            val message = messages.firstOrNull() ?: return@forEach
            val key = rawKey.removePrefix("answers.")
            when {
                key == "name" -> nameError = message
                key == "guestEmail" -> emailError = message
                key in keys -> errors[key] = message
                else -> unmatched = true
            }
        }
        var formError: String? = null
        if (fieldErrors.isEmpty() || unmatched) {
            val matched = FormLogic.fieldForServerMessage(s.fields, error.message)
            if (matched != null && fieldErrors.isEmpty()) errors[matched] = error.message else formError = error.message
        }
        return s.copy(submitting = false, nameError = nameError, emailError = emailError, errors = errors, formError = formError)
    }

    private fun PublicEventDto.toSummary() = EventSummaryDto(
        id = id,
        slug = slug,
        title = title,
        coverImageUrl = coverImageUrl,
        startDateTime = startDateTime,
        endDateTime = endDateTime,
        timezone = timezone,
        location = location,
        mapLinkUrl = mapLinkUrl,
        isOnline = isOnline,
        accentColor = accentColor,
        status = status,
        organisation = organisation,
    )

    private companion object {
        val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
