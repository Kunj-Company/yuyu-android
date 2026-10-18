package com.twilighty.yuyu.feature.checkin.flow

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.feature.checkin.domain.AdmitResult
import com.twilighty.yuyu.feature.checkin.domain.FeedbackKind
import com.twilighty.yuyu.feature.checkin.domain.GuestPreview
import com.twilighty.yuyu.feature.checkin.domain.LookupGuest
import com.twilighty.yuyu.feature.checkin.domain.ScanDebouncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The result bottom sheet. */
sealed interface SheetState {
    data object Hidden : SheetState

    data class Loading(val label: String) : SheetState

    /** Waiting for the volunteer: "Check in", or "Check in anyway" when [override] (waitlist). */
    data class Ready(
        val guest: GuestPreview,
        val override: Boolean,
        val reason: String? = null,
        val submitting: Boolean = false,
        val error: String? = null,
    ) : SheetState

    data class Admitted(
        val guest: GuestPreview,
        val checkedInAt: String?,
        val offline: Boolean,
        val undoing: Boolean = false,
        val undoError: String? = null,
    ) : SheetState

    data class AlreadyIn(val guest: GuestPreview, val checkedInAt: String?) : SheetState

    /** Invalid ticket, a blocked status (rejected, pending approval…) or another failure. */
    data class Blocked(val guest: GuestPreview?, val title: String, val message: String) : SheetState

    data class Undone(val guest: GuestPreview) : SheetState
}

data class LookupState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<LookupGuest> = emptyList(),
    val error: String? = null,
    /** Results come from the offline guest list. */
    val offline: Boolean = false,
) {
    val tooShort: Boolean get() = query.trim().length < MIN_QUERY

    companion object {
        const val MIN_QUERY = 2
    }
}

/**
 * The check-in state machine shared by the organiser scanner and the door
 * station: scan / type / search → preview → confirm (or override) → result →
 * undo. When [offline] is given, network failures (and a known-offline device,
 * [isOffline]) fall back to the local roster and queue.
 *
 * Raw ticket input is kept in memory only while its sheet is open and is
 * never logged.
 */
@OptIn(FlowPreview::class)
class CheckInFlow(
    private val scope: CoroutineScope,
    private val backend: CheckInBackend,
    private val offline: OfflineCheckInBackend? = null,
    private val isOffline: () -> Boolean = { false },
    private val onCheckInsChanged: () -> Unit = {},
    private val onFailure: (AppError) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    lookupDebounceMillis: Long = 300,
) {
    private val _sheet = MutableStateFlow<SheetState>(SheetState.Hidden)
    val sheet: StateFlow<SheetState> = _sheet.asStateFlow()

    private val _feedback = MutableSharedFlow<FeedbackKind>(extraBufferCapacity = 8)

    /** One event per result, for the haptic + sound. */
    val feedback: SharedFlow<FeedbackKind> = _feedback.asSharedFlow()

    private val _lookup = MutableStateFlow(LookupState())
    val lookup: StateFlow<LookupState> = _lookup.asStateFlow()
    private val lookupQuery = MutableStateFlow("")

    /** Fast mode: check in valid tickets without the confirm tap, and clear the result after a moment. */
    @Volatile var fastMode: Boolean = false

    private val debouncer = ScanDebouncer()
    private var rawInput: String? = null
    private var job: Job? = null
    private var autoDismiss: Job? = null

    /** Nesting depth of in-flight writes (check-in / undo); the sheet can't be closed meanwhile. */
    @Volatile private var writeDepth = 0
    private val writing: Boolean get() = writeDepth > 0

    init {
        scope.launch {
            lookupQuery.debounce(lookupDebounceMillis).distinctUntilChanged().collectLatest { runLookup(it) }
        }
    }

    /** A QR read by the camera. Ignored while a result is showing and for repeats of the same code. */
    fun onScanned(raw: String) {
        if (_sheet.value != SheetState.Hidden) return
        if (raw.isBlank() || !debouncer.accept(raw, clock())) return
        previewCode(raw)
    }

    /** A typed ticket code. */
    fun submitCode(raw: String) {
        val code = raw.trim()
        if (code.isEmpty() || writing) return
        previewCode(code)
    }

    /** A guest picked from the search results. */
    fun openGuest(rsvpId: String) {
        if (writing) return
        launchStep("Finding guest…") {
            rawInput = null
            if (offline != null && isOffline()) return@launchStep offlineRsvp(rsvpId)
            when (val result = backend.previewRsvp(rsvpId)) {
                is AppResult.Success -> present(result.data)
                is AppResult.Failure ->
                    if (result.error is AppError.Network && offline != null) offlineRsvp(rsvpId) else problem(result.error)
            }
        }
    }

    fun onLookupQuery(query: String) {
        lookupQuery.value = query
        if (query.trim().length < LookupState.MIN_QUERY) _lookup.value = LookupState(query = query)
    }

    /** "Check in" / "Check in anyway". */
    fun confirm() {
        val state = _sheet.value as? SheetState.Ready ?: return
        if (state.submitting || writing) return
        _sheet.value = state.copy(submitting = true, error = null)
        job = scope.launch { write { admit(state.guest, force = state.override) } }
    }

    fun undo() {
        val state = _sheet.value as? SheetState.Admitted ?: return
        if (state.undoing || writing) return
        autoDismiss?.cancel()
        _sheet.value = state.copy(undoing = true, undoError = null)
        job = scope.launch {
            write {
                val removedLocally = state.offline && offline?.undo(state.guest.rsvpId) == true
                if (removedLocally) {
                    undone(state.guest)
                    return@write
                }
                when (val result = backend.undo(state.guest.rsvpId)) {
                    is AppResult.Success -> undone(state.guest)
                    is AppResult.Failure -> {
                        onFailure(result.error)
                        _sheet.value = state.copy(undoing = false, undoError = result.error.message)
                    }
                }
            }
        }
    }

    /** Closes the sheet ("Next guest"). A write in flight is never abandoned. */
    fun dismiss() {
        if (writing) return
        autoDismiss?.cancel()
        if (_sheet.value is SheetState.Loading) job?.cancel()
        _sheet.value = SheetState.Hidden
        rawInput = null
        debouncer.restart(clock())
    }

    private fun previewCode(raw: String) = launchStep("Checking ticket…") {
        rawInput = raw
        if (offline != null && isOffline()) return@launchStep offlineCode(raw)
        when (val result = backend.previewCode(raw)) {
            is AppResult.Success -> present(result.data)
            is AppResult.Failure ->
                if (result.error is AppError.Network && offline != null) offlineCode(raw) else problem(result.error)
        }
    }

    private fun launchStep(label: String, block: suspend () -> Unit) {
        job?.cancel()
        autoDismiss?.cancel()
        _sheet.value = SheetState.Loading(label)
        job = scope.launch { block() }
    }

    private suspend fun write(block: suspend () -> Unit) {
        writeDepth++
        try {
            block()
        } finally {
            writeDepth--
        }
    }

    private suspend fun present(guest: GuestPreview) {
        when {
            guest.alreadyCheckedIn -> already(guest, guest.checkedInAt)
            guest.gate.ok ->
                if (fastMode) {
                    _sheet.value = SheetState.Loading("Checking in…")
                    write { admit(guest, force = false) }
                } else {
                    _sheet.value = SheetState.Ready(guest, override = false)
                }
            guest.gate.needsForce -> _sheet.value = SheetState.Ready(guest, override = true, reason = guest.gate.reason)
            else -> blocked(guest, "Can't check in", guest.gate.reason ?: "This guest can't be checked in.")
        }
    }

    private suspend fun admit(guest: GuestPreview, force: Boolean) {
        if (guest.offline && offline != null) return recordOffline(guest, force)
        when (val result = backend.checkIn(guest.rsvpId, rawInput, force)) {
            is AppResult.Success -> {
                onCheckInsChanged()
                if (result.data.alreadyCheckedIn) already(result.data.guest, result.data.checkedInAt) else admitted(result.data)
            }
            is AppResult.Failure -> when (val error = result.error) {
                is AppError.NeedsForce -> _sheet.value = SheetState.Ready(guest, override = true, reason = error.message)
                is AppError.Network -> if (offline != null) admitOfflineAfterNetworkError(guest, force) else readyError(guest, force, error)
                else -> readyError(guest, force, error)
            }
        }
    }

    /** The request couldn't reach the server: decide from the local roster instead. */
    private suspend fun admitOfflineAfterNetworkError(guest: GuestPreview, force: Boolean) {
        val local = offline?.findRsvp(guest.rsvpId, force)
        when {
            local == null -> readyError(guest, force, AppError.Network("You're offline and this guest isn't in the offline guest list."))
            local.alreadyCheckedIn -> already(local, local.checkedInAt)
            local.gate.ok -> recordOffline(local, force)
            else -> present(local)
        }
    }

    private suspend fun recordOffline(guest: GuestPreview, force: Boolean) {
        val result = offline?.record(guest, force) ?: return
        onCheckInsChanged()
        admitted(result)
    }

    private fun readyError(guest: GuestPreview, override: Boolean, error: AppError) {
        onFailure(error)
        _sheet.value = SheetState.Ready(guest, override = override || guest.gate.needsForce, reason = guest.gate.reason, error = error.message)
        _feedback.tryEmit(FeedbackKind.Invalid)
    }

    private suspend fun offlineCode(raw: String) {
        when (val match = offline?.matchCode(raw) ?: OfflineMatch.NoRoster) {
            is OfflineMatch.Found -> present(match.guest)
            OfflineMatch.NotInRoster -> blocked(
                null,
                "Not on the offline guest list",
                "This ticket isn't in the guest list saved on this phone. Check the code, or try again when you're back online.",
            )
            OfflineMatch.NoRoster -> blocked(
                null,
                "You're offline",
                "Download the guest list for offline use while you have a connection, so tickets can be checked without one.",
            )
        }
    }

    private suspend fun offlineRsvp(rsvpId: String) {
        val guest = offline?.findRsvp(rsvpId, force = false)
        if (guest != null) present(guest) else blocked(null, "You're offline", "This guest isn't in the offline guest list.")
    }

    private fun admitted(result: AdmitResult) {
        _sheet.value = SheetState.Admitted(result.guest, result.checkedInAt, result.offline)
        _feedback.tryEmit(FeedbackKind.Valid)
        if (fastMode) {
            autoDismiss?.cancel()
            autoDismiss = scope.launch {
                delay(FAST_MODE_RESULT_MILLIS)
                if (_sheet.value is SheetState.Admitted && !writing) dismiss()
            }
        }
    }

    private fun already(guest: GuestPreview, checkedInAt: String?) {
        _sheet.value = SheetState.AlreadyIn(guest, checkedInAt)
        _feedback.tryEmit(FeedbackKind.Already)
    }

    private fun blocked(guest: GuestPreview?, title: String, message: String) {
        _sheet.value = SheetState.Blocked(guest, title, message)
        _feedback.tryEmit(FeedbackKind.Invalid)
    }

    private fun undone(guest: GuestPreview) {
        onCheckInsChanged()
        _sheet.value = SheetState.Undone(guest)
    }

    private fun problem(error: AppError) {
        onFailure(error)
        val title = when (error) {
            is AppError.NotFound -> "Ticket not found"
            is AppError.Network -> "Can't reach the server"
            is AppError.RateLimited -> "Slow down a moment"
            is AppError.Forbidden, is AppError.Unauthenticated -> "No access"
            else -> "Couldn't check this ticket"
        }
        blocked(null, title, error.message)
    }

    private suspend fun runLookup(query: String) {
        val q = query.trim()
        if (q.length < LookupState.MIN_QUERY) return
        _lookup.update { it.copy(query = query, loading = true, error = null) }
        if (offline != null && isOffline()) {
            _lookup.value = LookupState(query, results = offline.lookup(q), offline = true)
            return
        }
        _lookup.value = when (val result = backend.lookup(q)) {
            is AppResult.Success -> LookupState(query, results = result.data)
            is AppResult.Failure -> {
                if (result.error is AppError.Network && offline != null) {
                    LookupState(query, results = offline.lookup(q), offline = true)
                } else {
                    onFailure(result.error)
                    LookupState(query, error = result.error.message)
                }
            }
        }
    }

    companion object {
        const val FAST_MODE_RESULT_MILLIS = 2_500L
    }
}
