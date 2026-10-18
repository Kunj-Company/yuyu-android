package com.twilighty.yuyu.feature.auth.signin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.AuthRepository
import com.twilighty.yuyu.core.network.model.AuthConfigDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignInStep { Credentials, Mfa }

data class SignInUiState(
    val config: AuthConfigDto? = null,
    val configLoading: Boolean = true,
    val step: SignInStep = SignInStep.Credentials,
    val useRecoveryCode: Boolean = false,
    val submitting: Boolean = false,
    val googleSubmitting: Boolean = false,
    val error: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val needsEmailVerification: Boolean = false,
    val serverLabel: String = "",
) {
    val googleEnabled: Boolean get() = config?.googleSignIn?.enabled == true && !config.googleSignIn.webClientId.isNullOrBlank()
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val state = MutableStateFlow(SignInUiState(serverLabel = authRepository.webUrl("")?.removeSuffix("/").orEmpty()))
    val uiState: StateFlow<SignInUiState> = state.asStateFlow()

    init {
        loadConfig()
    }

    fun loadConfig() {
        state.update { it.copy(configLoading = true) }
        viewModelScope.launch {
            val result = authRepository.authConfig()
            state.update {
                it.copy(configLoading = false, config = (result as? AppResult.Success)?.data ?: it.config)
            }
        }
    }

    // Text input lives in snapshot state (not the StateFlow) so fast typing is never dropped.
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var code by mutableStateOf("")
        private set

    fun canSubmit(s: SignInUiState = state.value): Boolean = !s.submitting && when (s.step) {
        SignInStep.Credentials -> email.isNotBlank() && password.isNotEmpty()
        SignInStep.Mfa -> code.isNotBlank()
    }

    fun onEmailChange(value: String) {
        email = value
        clearErrors()
    }

    fun onPasswordChange(value: String) {
        password = value
        clearErrors()
    }

    fun onCodeChange(value: String) {
        code = value.take(32)
        clearErrors()
    }

    private fun clearErrors() {
        val s = state.value
        if (s.error != null || s.emailError != null || s.passwordError != null) {
            state.update { it.copy(error = null, emailError = null, passwordError = null) }
        }
    }

    fun toggleRecoveryCode() {
        code = ""
        state.update { it.copy(useRecoveryCode = !it.useRecoveryCode, error = null) }
    }

    /** Back from the code step keeps the email but clears the password. */
    fun backToCredentials() {
        code = ""
        password = ""
        state.update { it.copy(step = SignInStep.Credentials, error = null) }
    }

    fun submit() {
        val s = state.value
        if (!canSubmit(s)) return
        state.update { it.copy(submitting = true, error = null, needsEmailVerification = false) }
        viewModelScope.launch {
            val totp = if (s.step == SignInStep.Mfa) code else null
            when (val result = authRepository.signIn(email, password, totp)) {
                // Success: the session store updates and the app swaps to the signed-in UI.
                is AppResult.Success -> {
                    password = ""
                    code = ""
                    state.update { it.copy(submitting = false) }
                }
                is AppResult.Failure -> handleError(result.error)
            }
        }
    }

    fun onGoogleIdToken(idToken: String) {
        state.update { it.copy(googleSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.signInWithGoogle(idToken)) {
                is AppResult.Success -> state.update { it.copy(googleSubmitting = false) }
                is AppResult.Failure -> state.update { it.copy(googleSubmitting = false, error = result.error.message) }
            }
        }
    }

    fun onGoogleError(message: String) = state.update { it.copy(error = message) }

    private fun handleError(error: AppError) {
        if (error is AppError.MfaRequired) code = ""
        state.update { s ->
            when (error) {
                is AppError.MfaRequired -> s.copy(
                    submitting = false,
                    // A code was sent and rejected again: stay on the step and say so.
                    error = if (s.step == SignInStep.Mfa) "That code didn't work. Check it and try again." else null,
                    step = SignInStep.Mfa,
                )
                is AppError.EmailVerificationRequired -> s.copy(submitting = false, error = error.message, needsEmailVerification = true)
                is AppError.InvalidRequest -> s.copy(
                    submitting = false,
                    error = error.message.takeIf { error.fieldErrors.isEmpty() },
                    emailError = error.fieldErrors["email"]?.firstOrNull(),
                    passwordError = error.fieldErrors["password"]?.firstOrNull(),
                )
                is AppError.RateLimited -> s.copy(
                    submitting = false,
                    error = error.retryAfterSeconds?.let { secs ->
                        "Too many attempts. Try again in ${if (secs >= 60) "${(secs + 59) / 60} min" else "$secs s"}."
                    } ?: error.message,
                )
                else -> s.copy(submitting = false, error = error.message)
            }
        }
    }

    fun webUrl(path: String): String? = authRepository.webUrl(path)

    fun changeServer() {
        viewModelScope.launch { authRepository.clearServer() }
    }
}
