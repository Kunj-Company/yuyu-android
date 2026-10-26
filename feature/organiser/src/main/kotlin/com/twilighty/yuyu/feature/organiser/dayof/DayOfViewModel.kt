package com.twilighty.yuyu.feature.organiser.dayof

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.DayOfRepository
import com.twilighty.yuyu.core.data.repository.OrganiserEventsRepository
import com.twilighty.yuyu.core.network.model.EventPermissionsDto
import com.twilighty.yuyu.core.network.model.OrgScheduleDto
import com.twilighty.yuyu.core.network.model.RegistrationStateDto
import com.twilighty.yuyu.feature.organiser.schedule.TrackUi
import com.twilighty.yuyu.feature.organiser.schedule.toTrackUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MAX_DELAY_MINUTES = 1440

sealed interface DelayInput {
    data class Valid(val minutes: Int) : DelayInput
    data class Invalid(val message: String) : DelayInput
}

/** Whole minutes from 0 to 1440; 0 clears the delay. */
fun parseDelay(input: String): DelayInput {
    val text = input.trim()
    if (text.isEmpty()) return DelayInput.Invalid("Enter the delay in minutes.")
    val minutes = text.toIntOrNull() ?: return DelayInput.Invalid("Use whole minutes, like 10.")
    if (minutes !in 0..MAX_DELAY_MINUTES) return DelayInput.Invalid("A delay can be 0 to 1440 minutes (24 hours).")
    return DelayInput.Valid(minutes)
}

data class DelayEditor(
    val sessionId: String,
    val sessionTitle: String,
    val trackName: String,
    val currentMinutes: Int,
    val input: String,
    val saving: Boolean = false,
    val error: String? = null,
)

data class DayOfUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val eventTitle: String = "",
    val timezone: String = "UTC",
    val permissions: EventPermissionsDto = EventPermissionsDto(),
    val tracks: List<TrackUi> = emptyList(),
    val scheduleLoading: Boolean = false,
    val scheduleError: String? = null,
    val delayEditor: DelayEditor? = null,
    val feedbackOpen: Boolean = false,
    val feedbackSaving: Boolean = false,
    val confirmCloseFeedback: Boolean = false,
    val registration: RegistrationStateDto = RegistrationStateDto(registrationOpen = false),
    val registrationSaving: Boolean = false,
    val confirmCloseSignUps: Boolean = false,
    val message: String? = null,
) {
    val showSchedule: Boolean get() = permissions.schedule
    val showFeedback: Boolean get() = permissions.manageFeedback
    val showSignUps: Boolean get() = permissions.manageRegistrationWindow
    val hasAnySection: Boolean get() = showSchedule || showFeedback || showSignUps
}

/** Shown when the server refuses a reopen (409): sign-ups weren't closed from the app. */
const val REOPEN_CONFLICT_MESSAGE =
    "Sign-ups can only be reopened here after they were closed from the app. To open them again, change the sign-up window on the website."

@HiltViewModel
class DayOfViewModel @Inject constructor(
    private val eventsRepository: OrganiserEventsRepository,
    private val dayOfRepository: DayOfRepository,
) : ViewModel() {
    private val state = MutableStateFlow(DayOfUiState())
    val uiState: StateFlow<DayOfUiState> = state.asStateFlow()

    private var orgSlug = ""
    private var eventId = ""

    fun start(orgSlug: String, eventId: String) {
        if (this.orgSlug == orgSlug && this.eventId == eventId) return
        this.orgSlug = orgSlug
        this.eventId = eventId
        load(refreshing = false)
    }

    fun refresh() = load(refreshing = true)

    fun retry() = load(refreshing = false)

    private fun load(refreshing: Boolean) {
        if (orgSlug.isEmpty()) return
        state.update { if (refreshing) it.copy(refreshing = true) else it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val schedule = async { dayOfRepository.schedule(orgSlug, eventId) }
            when (val result = eventsRepository.event(orgSlug, eventId)) {
                is AppResult.Success -> {
                    val event = result.data.event
                    val permissions = result.data.myPermissions
                    state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = null,
                            eventTitle = event.title,
                            timezone = event.timezone,
                            permissions = permissions,
                            feedbackOpen = event.feedbackOpen,
                            registration = RegistrationStateDto(
                                registrationOpen = event.registrationOpen,
                                registrationClosesAt = event.registrationClosesAt,
                                registrationClosedReason = event.registrationClosedReason,
                                registrationManuallyClosed = event.registrationManuallyClosed,
                            ),
                            scheduleLoading = permissions.schedule,
                        )
                    }
                    if (permissions.schedule) {
                        val s = schedule.await()
                        state.update {
                            when (s) {
                                is AppResult.Success -> it.copy(scheduleLoading = false, scheduleError = null, tracks = s.data.toTrackUi())
                                is AppResult.Failure -> it.copy(scheduleLoading = false, scheduleError = s.error.message)
                            }
                        }
                    } else {
                        schedule.cancel()
                    }
                }
                is AppResult.Failure -> {
                    schedule.cancel()
                    state.update {
                        if (refreshing && !it.loading) {
                            it.copy(refreshing = false, message = result.error.message)
                        } else {
                            it.copy(loading = false, refreshing = false, error = result.error.message)
                        }
                    }
                }
            }
        }
    }

    // --- Running late -------------------------------------------------------------

    fun openDelay(sessionId: String) {
        val (track, session) = state.value.tracks.firstNotNullOfOrNull { t ->
            t.sessions.firstOrNull { it.session.id == sessionId }?.let { t to it.session }
        } ?: return
        state.update {
            it.copy(
                delayEditor = DelayEditor(
                    sessionId = session.id,
                    sessionTitle = session.title,
                    trackName = track.name,
                    currentMinutes = session.delayMinutes,
                    input = session.delayMinutes.toString(),
                ),
            )
        }
    }

    fun closeDelay() = state.update { if (it.delayEditor?.saving == true) it else it.copy(delayEditor = null) }

    fun setDelayInput(text: String) = updateEditor { it.copy(input = text.filter(Char::isDigit).take(4), error = null) }

    /** Quick chips: add [minutes] to the value being edited, capped at 24 hours. */
    fun addDelay(minutes: Int) = updateEditor { e ->
        val base = (parseDelay(e.input) as? DelayInput.Valid)?.minutes ?: 0
        e.copy(input = (base + minutes).coerceIn(0, MAX_DELAY_MINUTES).toString(), error = null)
    }

    /** Stepper: -/+ [step] minutes within 0..1440. */
    fun stepDelay(step: Int) = addDelay(step)

    fun clearDelayInput() = updateEditor { it.copy(input = "0", error = null) }

    private fun updateEditor(transform: (DelayEditor) -> DelayEditor) = state.update { s ->
        s.delayEditor?.takeUnless { it.saving }?.let { s.copy(delayEditor = transform(it)) } ?: s
    }

    fun saveDelay() {
        val editor = state.value.delayEditor ?: return
        if (editor.saving) return
        val minutes = when (val parsed = parseDelay(editor.input)) {
            is DelayInput.Invalid -> {
                state.update { it.copy(delayEditor = editor.copy(error = parsed.message)) }
                return
            }
            is DelayInput.Valid -> parsed.minutes
        }
        state.update { it.copy(delayEditor = editor.copy(saving = true, error = null)) }
        viewModelScope.launch {
            when (val result = dayOfRepository.setSessionDelay(orgSlug, eventId, editor.sessionId, minutes)) {
                is AppResult.Success -> state.update { s ->
                    val schedule = OrgScheduleDto(
                        s.tracks.map { t ->
                            com.twilighty.yuyu.core.network.model.OrgTrackDto(
                                id = t.id,
                                name = t.name,
                                sessions = t.sessions.map { u ->
                                    if (u.session.id == editor.sessionId) u.session.copy(delayMinutes = minutes) else u.session
                                },
                            )
                        },
                    )
                    s.copy(
                        tracks = schedule.toTrackUi(),
                        delayEditor = null,
                        message = if (minutes == 0) "Delay cleared for ${editor.sessionTitle}" else "${editor.sessionTitle}: $minutes min delay saved",
                    )
                }
                is AppResult.Failure -> state.update {
                    it.copy(delayEditor = it.delayEditor?.copy(saving = false, error = result.error.message))
                }
            }
        }
    }

    // --- Feedback -----------------------------------------------------------------

    /** Opening is immediate; closing asks first because attendees stop being able to answer. */
    fun onFeedbackToggle(open: Boolean) {
        if (state.value.feedbackSaving) return
        if (open) setFeedbackOpen(true) else state.update { it.copy(confirmCloseFeedback = true) }
    }

    fun dismissCloseFeedback() = state.update { it.copy(confirmCloseFeedback = false) }

    fun confirmCloseFeedback() {
        state.update { it.copy(confirmCloseFeedback = false) }
        setFeedbackOpen(false)
    }

    private fun setFeedbackOpen(open: Boolean) {
        state.update { it.copy(feedbackSaving = true) }
        viewModelScope.launch {
            when (val result = dayOfRepository.setFeedbackOpen(orgSlug, eventId, open)) {
                is AppResult.Success -> state.update {
                    it.copy(
                        feedbackSaving = false,
                        feedbackOpen = result.data,
                        message = if (result.data) "Feedback form is open" else "Feedback form is closed",
                    )
                }
                is AppResult.Failure -> state.update {
                    it.copy(
                        feedbackSaving = false,
                        message = if (result.error is AppError.Conflict && open) {
                            "Add at least one feedback question on the website before opening the form."
                        } else {
                            result.error.message
                        },
                    )
                }
            }
        }
    }

    // --- Sign-ups -----------------------------------------------------------------

    fun askCloseSignUps(show: Boolean) = state.update { it.copy(confirmCloseSignUps = show) }

    fun closeSignUps() {
        state.update { it.copy(confirmCloseSignUps = false) }
        setRegistration(open = false)
    }

    fun reopenSignUps() {
        if (!state.value.registration.registrationManuallyClosed) return
        setRegistration(open = true)
    }

    private fun setRegistration(open: Boolean) {
        if (state.value.registrationSaving) return
        state.update { it.copy(registrationSaving = true) }
        viewModelScope.launch {
            when (val result = dayOfRepository.setRegistrationOpen(orgSlug, eventId, open)) {
                is AppResult.Success -> {
                    val reg = result.data
                    state.update {
                        it.copy(
                            registrationSaving = false,
                            registration = reg,
                            message = when {
                                !open -> "Sign-ups closed"
                                reg.registrationOpen -> "Sign-ups reopened"
                                else -> "The original closing time has already passed, so sign-ups stay closed."
                            },
                        )
                    }
                }
                is AppResult.Failure -> {
                    val conflict = result.error is AppError.Conflict
                    state.update {
                        it.copy(
                            registrationSaving = false,
                            message = if (conflict && open) REOPEN_CONFLICT_MESSAGE else result.error.message,
                        )
                    }
                    // The server state differs from what we showed: re-read it.
                    if (conflict) load(refreshing = true)
                }
            }
        }
    }

    fun messageShown() = state.update { it.copy(message = null) }
}
