package com.twilighty.yuyu.feature.attendee.feedback

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.common.result.getOrNull
import com.twilighty.yuyu.core.data.repository.AuthRepository
import com.twilighty.yuyu.core.data.repository.PublicEventsRepository
import com.twilighty.yuyu.core.data.repository.SessionState
import com.twilighty.yuyu.core.network.model.FeedbackFormDto
import com.twilighty.yuyu.core.network.model.FeedbackSubmitRequest
import com.twilighty.yuyu.core.network.model.PublicEventDto
import com.twilighty.yuyu.feature.attendee.common.CertificateStore
import com.twilighty.yuyu.feature.attendee.form.AnswerState
import com.twilighty.yuyu.feature.attendee.form.FormLogic
import com.twilighty.yuyu.feature.attendee.form.FormRules
import com.twilighty.yuyu.feature.attendee.navigation.FeedbackRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class FeedbackUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val offline: Boolean = false,
    val closed: Boolean = false,
    val form: FeedbackFormDto? = null,
    val event: PublicEventDto? = null,
    val answers: AnswerState = AnswerState(),
    val errors: Map<String, String> = emptyMap(),
    val email: String = "",
    val emailError: String? = null,
    val formError: String? = null,
    val submitting: Boolean = false,
    val submitted: Boolean = false,
    /** Absolute certificate JPEG URL when one was issued. */
    val certificateUrl: String? = null,
    val certificateBusy: Boolean = false,
    val certificateSaved: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PublicEventsRepository,
    private val authRepository: AuthRepository,
    private val certificateStore: CertificateStore,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<FeedbackRoute>()
    private val state = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = state.asStateFlow()
    private val rules = FormRules.Feedback

    private val shareUris = MutableStateFlow<Uri?>(null)
    val pendingShare: StateFlow<Uri?> = shareUris.asStateFlow()

    init {
        load()
    }

    fun load() {
        state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val email = (authRepository.sessionState.firstOrNull { it !is SessionState.Loading } as? SessionState.SignedIn)?.user?.email
            val eventCall = async { repository.event(route.orgSlug, route.eventSlug) }
            when (val result = repository.feedbackForm(route.orgSlug, route.eventSlug)) {
                is AppResult.Success -> {
                    val event = eventCall.await().getOrNull()?.event
                    state.update {
                        it.copy(
                            loading = false,
                            form = result.data,
                            event = event,
                            closed = !result.data.open,
                            email = it.email.ifEmpty { email.orEmpty() },
                        )
                    }
                }
                is AppResult.Failure -> {
                    eventCall.cancel()
                    state.update {
                        it.copy(
                            loading = false,
                            loadError = result.error.message,
                            offline = result.error is AppError.Network,
                            closed = result.error is AppError.NotFound,
                        )
                    }
                }
            }
        }
    }

    fun onAnswersChange(answers: AnswerState) = state.update { s ->
        val fields = s.form?.fields.orEmpty()
        val changed = fields.map { it.key }.filter { key ->
            listOf(s.answers.text[key], s.answers.multi[key], s.answers.bools[key]) != listOf(answers.text[key], answers.multi[key], answers.bools[key])
        }.toSet()
        s.copy(answers = answers, errors = s.errors - changed, formError = null)
    }

    fun onEmailChange(value: String) = state.update { it.copy(email = value.trim(), emailError = null) }

    fun submit() {
        val s = state.value
        val form = s.form ?: return
        if (s.submitting) return
        val errors = FormLogic.validate(form.fields, s.answers, rules)
        val emailError = when {
            !form.certificatesEnabled -> null
            s.email.isBlank() -> "Enter the email you registered with to get your certificate."
            !EMAIL.matches(s.email) -> "Enter a valid email address, like name@example.com."
            else -> null
        }
        if (errors.isNotEmpty() || emailError != null) {
            state.update { it.copy(errors = errors, emailError = emailError) }
            return
        }
        state.update { it.copy(submitting = true, formError = null) }
        // Same body as the web form: email only for certificate-enabled forms.
        val request = FeedbackSubmitRequest(
            orgSlug = route.orgSlug,
            eventSlug = route.eventSlug,
            email = if (form.certificatesEnabled) s.email.trim().lowercase() else null,
            answers = FormLogic.buildPayload(form.fields, s.answers, rules),
        )
        viewModelScope.launch {
            when (val result = repository.submitFeedback(request)) {
                is AppResult.Success -> state.update {
                    it.copy(
                        submitting = false,
                        submitted = true,
                        certificateUrl = result.data.certificateToken?.let(repository::certificateUrl),
                    )
                }
                is AppResult.Failure -> state.update {
                    val message = result.error.message
                    val key = FormLogic.fieldForServerMessage(form.fields, message)
                    when {
                        key != null -> it.copy(submitting = false, errors = mapOf(key to message))
                        form.certificatesEnabled && message.contains("email", ignoreCase = true) ||
                            message.startsWith("Certificate verification") -> it.copy(
                            submitting = false,
                            emailError = if (message.startsWith("Certificate verification")) {
                                "We couldn't find a confirmed registration for this email."
                            } else {
                                message
                            },
                        )
                        else -> it.copy(submitting = false, formError = message)
                    }
                }
            }
        }
    }

    fun saveCertificate() {
        val url = state.value.certificateUrl ?: return
        state.update { it.copy(certificateBusy = true) }
        viewModelScope.launch {
            when (val r = certificateStore.saveToGallery(url, state.value.form?.title ?: route.eventSlug)) {
                is AppResult.Success -> state.update { it.copy(certificateBusy = false, certificateSaved = true, message = "Certificate saved to Pictures/Yuyu.") }
                is AppResult.Failure -> state.update { it.copy(certificateBusy = false, message = r.error.message) }
            }
        }
    }

    fun shareCertificate() {
        val url = state.value.certificateUrl ?: return
        state.update { it.copy(certificateBusy = true) }
        viewModelScope.launch {
            when (val r = certificateStore.prepareShare(url, state.value.form?.title ?: route.eventSlug)) {
                is AppResult.Success -> {
                    shareUris.value = r.data
                    state.update { it.copy(certificateBusy = false) }
                }
                is AppResult.Failure -> state.update { it.copy(certificateBusy = false, message = r.error.message) }
            }
        }
    }

    fun onShareHandled() {
        shareUris.value = null
    }

    fun onMessageShown() = state.update { it.copy(message = null) }

    private companion object {
        val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
