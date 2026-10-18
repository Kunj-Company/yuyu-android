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
import com.twilighty.yuyu.core.data.sync.UnsyncedCheckIns
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

/** What the person asked for when we stopped to warn about offline check-ins. */
enum class SignOutAction { SignOut, SignOutEverywhere, ChangeServer }

/** Offline check-ins live in the local database, which signing out or changing server wipes. */
data class UnsyncedWarning(val action: SignOutAction, val count: Int, val syncing: Boolean = false)

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
    val unsyncedWarning: UnsyncedWarning? = null,
)

private data class LocalState(
    val editingName: Boolean = false,
    val nameError: String? = null,
    val savingName: Boolean = false,
    val confirmSignOutEverywhere: Boolean = false,
    val confirmChangeServer: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val unsyncedWarning: UnsyncedWarning? = null,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val meRepository: MeRepository,
    private val preferences: AppPreferences,
    private val unsyncedCheckIns: UnsyncedCheckIns,
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
            unsyncedWarning = l.unsyncedWarning,
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

    fun signOut() = guarded(SignOutAction.SignOut)

    fun askSignOutEverywhere(show: Boolean) = local.update { it.copy(confirmSignOutEverywhere = show) }

    fun signOutEverywhere() = guarded(SignOutAction.SignOutEverywhere)

    fun askChangeServer(show: Boolean) = local.update { it.copy(confirmChangeServer = show) }

    fun changeServer() = guarded(SignOutAction.ChangeServer)

    /** Push the waiting check-ins, then carry on only if nothing is left behind. */
    fun syncNowAndContinue() {
        val warning = local.value.unsyncedWarning ?: return
        local.update { it.copy(unsyncedWarning = warning.copy(syncing = true)) }
        viewModelScope.launch {
            val left = runCatching { unsyncedCheckIns.trySyncNow() }.getOrElse { warning.count }
            if (left == 0) {
                local.update { it.copy(unsyncedWarning = null, message = "Check-ins synced.") }
                perform(warning.action)
            } else {
                local.update {
                    it.copy(
                        unsyncedWarning = warning.copy(count = left, syncing = false),
                        message = "$left ${if (left == 1) "check-in" else "check-ins"} still couldn't sync. Check your connection.",
                    )
                }
            }
        }
    }

    /** The person chose to lose the waiting check-ins. */
    fun continueWithoutSyncing() {
        val warning = local.value.unsyncedWarning ?: return
        local.update { it.copy(unsyncedWarning = null) }
        perform(warning.action)
    }

    fun dismissUnsyncedWarning() = local.update {
        it.copy(unsyncedWarning = null, confirmSignOutEverywhere = false, confirmChangeServer = false)
    }

    private fun guarded(action: SignOutAction) {
        viewModelScope.launch {
            val waiting = runCatching { unsyncedCheckIns.current() }.getOrDefault(0)
            if (waiting > 0) {
                local.update { it.copy(unsyncedWarning = UnsyncedWarning(action, waiting)) }
            } else {
                perform(action)
            }
        }
    }

    private fun perform(action: SignOutAction) {
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (action) {
                SignOutAction.SignOut -> authRepository.signOut()
                SignOutAction.SignOutEverywhere -> when (val result = authRepository.signOutEverywhere()) {
                    is AppResult.Success -> Unit
                    is AppResult.Failure -> local.update {
                        it.copy(busy = false, confirmSignOutEverywhere = false, message = result.error.message)
                    }
                }
                SignOutAction.ChangeServer -> {
                    authRepository.signOut()
                    authRepository.clearServer()
                }
            }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    fun webUrl(path: String): String? = authRepository.webUrl(path)
}
