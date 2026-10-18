package com.twilighty.yuyu.feature.checkin.station

import androidx.lifecycle.ViewModel
import com.twilighty.yuyu.core.data.server.ServerStore
import com.twilighty.yuyu.core.data.server.ServerUrl
import com.twilighty.yuyu.feature.checkin.domain.StationLink
import com.twilighty.yuyu.feature.checkin.domain.StationLinkParser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OpenStationUiState(
    val serverLabel: String? = null,
    val scanning: Boolean = false,
    val error: String? = null,
    /** Set once a valid station was entered; the screen navigates and calls [OpenStationViewModel.onNavigated]. */
    val target: StationLink.Valid? = null,
)

/** "Open a check-in station": a typed station/event link, `org/event`, or a scanned station QR. */
@HiltViewModel
class OpenStationViewModel @Inject constructor(
    private val serverStore: ServerStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        OpenStationUiState(serverLabel = serverStore.currentServer()?.let(ServerUrl::display)),
    )
    val uiState: StateFlow<OpenStationUiState> = _uiState.asStateFlow()

    fun submit(input: String) {
        val server = serverStore.currentServer()
        if (server == null) {
            _uiState.update { it.copy(error = "Choose your Yuyu server first.") }
            return
        }
        when (val link = StationLinkParser.parse(input, server)) {
            is StationLink.Valid -> _uiState.update { it.copy(target = link, error = null, scanning = false) }
            is StationLink.Invalid -> _uiState.update { it.copy(error = link.message) }
        }
    }

    fun setScanning(scanning: Boolean) = _uiState.update { it.copy(scanning = scanning, error = null) }

    fun onNavigated() = _uiState.update { it.copy(target = null) }
}
