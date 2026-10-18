package com.twilighty.yuyu.feature.auth.server

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.AppBuildInfo
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.AuthRepository
import com.twilighty.yuyu.core.data.server.ServerUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerSetupUiState(
    val checking: Boolean = false,
    val error: String? = null,
    val showDebugHint: Boolean = false,
)

@HiltViewModel
class ServerSetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val buildInfo: AppBuildInfo,
) : ViewModel() {
    private val state = MutableStateFlow(ServerSetupUiState(showDebugHint = buildInfo.isDebug))
    val uiState: StateFlow<ServerSetupUiState> = state.asStateFlow()

    /**
     * Text input lives in snapshot state (not the StateFlow) so fast typing and
     * IME edits are never dropped by an asynchronous round trip.
     */
    var address by mutableStateOf("")
        private set

    fun onAddressChange(value: String) {
        address = value
        if (state.value.error != null) state.update { it.copy(error = null) }
    }

    /** Validates the address, checks it's a Yuyu server, then saves it (the app moves to sign-in). */
    fun connect() {
        if (state.value.checking) return
        when (val normalized = ServerUrl.normalize(address, allowDebugCleartext = buildInfo.isDebug)) {
            is ServerUrl.Result.Invalid -> state.update { it.copy(error = normalized.message) }
            is ServerUrl.Result.Valid -> {
                state.update { it.copy(checking = true, error = null) }
                viewModelScope.launch {
                    when (val result = authRepository.probeServer(normalized.url)) {
                        is AppResult.Success -> authRepository.selectServer(result.data.url)
                        is AppResult.Failure -> state.update { it.copy(checking = false, error = result.error.message) }
                    }
                }
            }
        }
    }
}
