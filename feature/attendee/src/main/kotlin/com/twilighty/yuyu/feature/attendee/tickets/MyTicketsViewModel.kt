package com.twilighty.yuyu.feature.attendee.tickets

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.MeRepository
import com.twilighty.yuyu.core.network.model.CertificateDto
import com.twilighty.yuyu.core.network.model.RegistrationDto
import com.twilighty.yuyu.feature.attendee.common.CertificateStore
import com.twilighty.yuyu.feature.attendee.reminders.NotificationPermission
import com.twilighty.yuyu.feature.attendee.reminders.ReminderPreferences
import com.twilighty.yuyu.feature.attendee.reminders.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class MyTicketsUiState(
    /** First load with nothing cached yet. */
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val upcoming: List<RegistrationDto> = emptyList(),
    val past: List<RegistrationDto> = emptyList(),
    val certificates: List<CertificateDto> = emptyList(),
    /** The last refresh couldn't reach the server; cached tickets are shown. */
    val offline: Boolean = false,
    /** A failure with nothing cached to show. */
    val error: String? = null,
    val cancelTarget: RegistrationDto? = null,
    val cancelling: Boolean = false,
    val remindersEnabled: Boolean = true,
    /** Show the "why we'd like to notify you" rationale before the system prompt. */
    val showNotificationRationale: Boolean = false,
    val certificateBusy: String? = null,
    val message: String? = null,
) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && past.isEmpty() && certificates.isEmpty()
}

@HiltViewModel
class MyTicketsViewModel @Inject constructor(
    private val meRepository: MeRepository,
    private val reminderScheduler: ReminderScheduler,
    private val reminderPreferences: ReminderPreferences,
    private val notificationPermission: NotificationPermission,
    private val certificateStore: CertificateStore,
) : ViewModel() {
    private val state = MutableStateFlow(MyTicketsUiState())
    val uiState: StateFlow<MyTicketsUiState> = state.asStateFlow()

    private val shareUris = MutableStateFlow<Uri?>(null)

    /** A certificate ready to hand to the share sheet (consume with [onShareHandled]). */
    val pendingShare: StateFlow<Uri?> = shareUris.asStateFlow()

    init {
        viewModelScope.launch {
            meRepository.cachedRegistrations().collect { cached ->
                state.update {
                    it.copy(
                        upcoming = cached.upcoming,
                        past = cached.past,
                        loading = it.loading && cached.upcoming.isEmpty() && cached.past.isEmpty(),
                    )
                }
            }
        }
        viewModelScope.launch {
            reminderPreferences.remindersEnabled.collect { enabled -> state.update { it.copy(remindersEnabled = enabled) } }
        }
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        if (state.value.refreshing) return
        state.update { it.copy(refreshing = !initial, error = null) }
        viewModelScope.launch {
            when (val result = meRepository.registrations()) {
                is AppResult.Success -> {
                    val data = result.data
                    state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            offline = false,
                            upcoming = data.upcoming,
                            past = data.past,
                            certificates = data.certificates,
                        )
                    }
                    syncReminders(data.upcoming)
                }
                is AppResult.Failure -> {
                    val hasCache = state.value.upcoming.isNotEmpty() || state.value.past.isNotEmpty()
                    state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            offline = result.error is AppError.Network,
                            error = if (hasCache) null else result.error.message,
                            message = if (hasCache && result.error !is AppError.Network) result.error.message else it.message,
                        )
                    }
                    // Keep reminders for what we know about, even offline.
                    if (hasCache) syncReminders(state.value.upcoming)
                }
            }
        }
    }

    private suspend fun syncReminders(upcoming: List<RegistrationDto>) {
        val enabled = reminderPreferences.remindersEnabled.first()
        val planned = reminderScheduler.sync(upcoming, enabled)
        if (planned.isNotEmpty() && notificationPermission.needsRequest() && !reminderPreferences.permissionPromptShown.first()) {
            state.update { it.copy(showNotificationRationale = true) }
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            reminderPreferences.setRemindersEnabled(enabled)
            val planned = reminderScheduler.sync(state.value.upcoming, enabled)
            // Turning reminders on is an explicit request: ask for the permission again if it's missing.
            if (enabled && planned.isNotEmpty() && notificationPermission.needsRequest()) {
                state.update { it.copy(showNotificationRationale = true) }
            }
        }
    }

    /** The rationale was answered (either way); the system prompt follows only on "Allow". */
    fun onNotificationRationaleDone() {
        state.update { it.copy(showNotificationRationale = false) }
        viewModelScope.launch { reminderPreferences.setPermissionPromptShown() }
    }

    fun askCancel(registration: RegistrationDto) {
        if (registration.canCancel) state.update { it.copy(cancelTarget = registration) }
    }

    fun dismissCancel() {
        if (!state.value.cancelling) state.update { it.copy(cancelTarget = null) }
    }

    fun confirmCancel() {
        val target = state.value.cancelTarget ?: return
        state.update { it.copy(cancelling = true) }
        viewModelScope.launch {
            when (val result = meRepository.cancelRegistration(target.rsvpId)) {
                is AppResult.Success -> {
                    state.update { it.copy(cancelling = false, cancelTarget = null, message = "Registration cancelled for ${target.event.title}.") }
                    refresh()
                }
                is AppResult.Failure -> state.update { it.copy(cancelling = false, cancelTarget = null, message = result.error.message) }
            }
        }
    }

    fun saveCertificate(certificate: CertificateDto) {
        state.update { it.copy(certificateBusy = certificate.certificateUrl) }
        viewModelScope.launch {
            val message = when (val r = certificateStore.saveToGallery(certificate.certificateUrl, certificate.eventTitle)) {
                is AppResult.Success -> "Certificate saved to Pictures/Yuyu."
                is AppResult.Failure -> r.error.message
            }
            state.update { it.copy(certificateBusy = null, message = message) }
        }
    }

    fun shareCertificate(certificate: CertificateDto) {
        state.update { it.copy(certificateBusy = certificate.certificateUrl) }
        viewModelScope.launch {
            when (val r = certificateStore.prepareShare(certificate.certificateUrl, certificate.eventTitle)) {
                is AppResult.Success -> {
                    shareUris.value = r.data
                    state.update { it.copy(certificateBusy = null) }
                }
                is AppResult.Failure -> state.update { it.copy(certificateBusy = null, message = r.error.message) }
            }
        }
    }

    fun onShareHandled() {
        shareUris.value = null
    }

    fun onMessageShown() {
        state.update { it.copy(message = null) }
    }
}
