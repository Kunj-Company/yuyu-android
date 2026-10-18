package com.twilighty.yuyu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.AppearanceMode
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.prefs.AppPreferences
import com.twilighty.yuyu.core.data.repository.AuthRepository
import com.twilighty.yuyu.core.data.repository.MeRepository
import com.twilighty.yuyu.core.data.repository.PublicEventsRepository
import com.twilighty.yuyu.core.data.repository.SessionState
import com.twilighty.yuyu.core.network.model.ShortLinkDto
import com.twilighty.yuyu.navigation.DeepLink
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val session: SessionState = SessionState.Loading,
    val appearance: AppearanceMode = AppearanceMode.System,
    /** Organise tab only for people with organisations or event collaborations. */
    val showOrganise: Boolean = false,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val meRepository: MeRepository,
    private val publicEvents: PublicEventsRepository,
    preferences: AppPreferences,
) : ViewModel() {

    val uiState: StateFlow<AppUiState> = combine(authRepository.sessionState, preferences.appearance, meRepository.me) { session, appearance, me ->
        AppUiState(
            session = session,
            appearance = appearance,
            showOrganise = session is SessionState.SignedIn && me != null && (me.organisations.isNotEmpty() || me.collaborations.isNotEmpty()),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    private val pending = MutableStateFlow<DeepLink?>(null)

    /** A link waiting for the signed-in UI (kept across sign-in). */
    val pendingDeepLink: StateFlow<DeepLink?> = pending.asStateFlow()

    private val signedOutByServer = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val forcedSignOut: SharedFlow<Unit> = signedOutByServer.asSharedFlow()

    init {
        viewModelScope.launch { authRepository.initialize() }
        viewModelScope.launch {
            authRepository.sessionState
                .distinctUntilChangedBy { (it as? SessionState.SignedIn)?.user?.id }
                .collect { if (it is SessionState.SignedIn) meRepository.refreshMe() }
        }
        viewModelScope.launch { authRepository.forcedSignOuts.collect { signedOutByServer.tryEmit(Unit) } }
    }

    fun onDeepLink(link: DeepLink) {
        pending.value = link
    }

    fun consumeDeepLink() {
        pending.value = null
    }

    suspend fun resolveShortLink(code: String): ShortLinkDto? =
        (publicEvents.resolveShortLink(code) as? AppResult.Success)?.data

    fun refreshMe() {
        viewModelScope.launch { meRepository.refreshMe() }
    }
}
