package com.twilighty.yuyu.feature.checkin.station

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.StationKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The PIN itself is never in UI state (state is printable); only its length is. */
data class StationUnlockUiState(
    val orgSlug: String,
    val eventSlug: String,
    val pinLength: Int = 0,
    val busy: Boolean = false,
    val error: String? = null,
    val unlocked: Boolean = false,
) {
    val canSubmit: Boolean get() = pinLength == PIN_LENGTH && !busy

    companion object {
        const val PIN_LENGTH = 8
    }
}

/** 8-digit PIN pad → `action: unlock`. The PIN is sent once and then wiped from memory. */
@HiltViewModel
class StationUnlockViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: StationSessions,
) : ViewModel() {
    private val key = StationKey(
        checkNotNull(savedStateHandle["orgSlug"]) { "orgSlug missing" },
        checkNotNull(savedStateHandle["eventSlug"]) { "eventSlug missing" },
    )
    private val pin = StringBuilder(StationUnlockUiState.PIN_LENGTH)
    private val _uiState = MutableStateFlow(StationUnlockUiState(key.orgSlug, key.eventSlug))
    val uiState: StateFlow<StationUnlockUiState> = _uiState.asStateFlow()

    fun digit(d: Char) {
        if (!d.isDigit() || _uiState.value.busy || pin.length >= StationUnlockUiState.PIN_LENGTH) return
        pin.append(d)
        _uiState.update { it.copy(pinLength = pin.length, error = null) }
    }

    fun backspace() {
        if (pin.isEmpty() || _uiState.value.busy) return
        pin.deleteCharAt(pin.length - 1)
        _uiState.update { it.copy(pinLength = pin.length) }
    }

    fun clear() {
        wipe()
        _uiState.update { it.copy(pinLength = 0) }
    }

    fun submit() {
        if (!_uiState.value.canSubmit) return
        val entered = pin.toString()
        wipe()
        _uiState.update { it.copy(busy = true, error = null, pinLength = 0) }
        viewModelScope.launch {
            when (val result = sessions.get(key).unlock(key, entered)) {
                is AppResult.Success -> _uiState.update { it.copy(busy = false, unlocked = true) }
                is AppResult.Failure -> _uiState.update { it.copy(busy = false, error = StationErrors.forUnlock(result.error)) }
            }
        }
    }

    /** Navigation to the station happened. */
    fun onNavigated() = _uiState.update { it.copy(unlocked = false) }

    private fun wipe() {
        for (i in pin.indices) pin.setCharAt(i, '0')
        pin.setLength(0)
    }

    override fun onCleared() {
        wipe()
        // Leaving without unlocking: drop the (empty) session. An unlocked one is handed to the station.
        if (!sessions.get(key).isUnlocked) sessions.end(key)
    }
}
