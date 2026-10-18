package com.twilighty.yuyu.feature.auth.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.AppBuildInfo
import com.twilighty.yuyu.core.common.AppearanceMode
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.prefs.AppPreferences
import com.twilighty.yuyu.core.data.repository.AuthRepository
import com.twilighty.yuyu.core.data.repository.MeRepository
import com.twilighty.yuyu.core.data.repository.SessionState
import com.twilighty.yuyu.core.data.server.ServerUrl
import com.twilighty.yuyu.core.network.model.UserDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val user: UserDto? = null,
    val serverLabel: String = "",
    val appearance: AppearanceMode = AppearanceMode.System,
    val showDesignGallery: Boolean = false,
    val versionName: String = "",
    val editingName: Boolean = false,
    val nameError: String? = null,
    val savingName: Boolean = false,
    val confirmSignOutEverywhere: Boolean = false,
    val confirmChangeServer: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

private data class LocalState(
    val editingName: Boolean = false,
    val nameError: String? = null,
    val savingName: Boolean = false,
    val confirmSignOutEverywhere: Boolean = false,
    val confirmChangeServer: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val meRepository: MeRepository,
    private val preferences: AppPreferences,
    buildInfo: AppBuildInfo,
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<AccountUiState> = combine(authRepository.sessionState, preferences.appearance, local) { session, appearance, l ->
        val signedIn = session as? SessionState.SignedIn
        AccountUiState(
            user = signedIn?.user,
            serverLabel = signedIn?.server?.let(ServerUrl::display).orEmpty(),
            appearance = appearance,
            showDesignGallery = buildInfo.isDebug,
            versionName = buildInfo.versionName,
            editingName = l.editingName,
            nameError = l.nameError,
            savingName = l.savingName,
            confirmSignOutEverywhere = l.confirmSignOutEverywhere,
            confirmChangeServer = l.confirmChangeServer,
            busy = l.busy,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState(showDesignGallery = buildInfo.isDebug))

    init {
        viewModelScope.launch { meRepository.refreshMe() }
    }

    fun setAppearance(mode: AppearanceMode) {
        viewModelScope.launch { preferences.setAppearance(mode) }
    }

    /** Snapshot state so typing is never dropped by an async round trip. */
    var nameDraft by mutableStateOf("")
        private set

    fun startEditName() {
        nameDraft = uiState.value.user?.name.orEmpty()
        local.update { it.copy(editingName = true, nameError = null) }
    }

    fun onNameDraftChange(value: String) {
        nameDraft = value.take(120)
        if (local.value.nameError != null) local.update { it.copy(nameError = null) }
    }
    fun cancelEditName() = local.update { it.copy(editingName = false, nameError = null) }

    fun saveName() {
        val name = nameDraft.trim()
        if (name.isEmpty()) {
            local.update { it.copy(nameError = "Enter your name.") }
            return
        }
        local.update { it.copy(savingName = true) }
        viewModelScope.launch {
            when (val result = meRepository.updateName(name)) {
                is AppResult.Success -> local.update { it.copy(savingName = false, editingName = false, message = "Name saved.") }
                is AppResult.Failure -> local.update { it.copy(savingName = false, nameError = result.error.message) }
            }
        }
    }

    fun signOut() {
        local.update { it.copy(busy = true) }
        viewModelScope.launch { authRepository.signOut() }
    }

    fun askSignOutEverywhere(show: Boolean) = local.update { it.copy(confirmSignOutEverywhere = show) }

    fun signOutEverywhere() {
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = authRepository.signOutEverywhere()) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> local.update { it.copy(busy = false, confirmSignOutEverywhere = false, message = result.error.message) }
            }
        }
    }

    fun askChangeServer(show: Boolean) = local.update { it.copy(confirmChangeServer = show) }

    fun changeServer() {
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            authRepository.signOut()
            authRepository.clearServer()
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    fun webUrl(path: String): String? = authRepository.webUrl(path)
}
