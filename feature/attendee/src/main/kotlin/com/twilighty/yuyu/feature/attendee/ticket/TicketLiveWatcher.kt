package com.twilighty.yuyu.feature.attendee.ticket

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.PublicEventsRepository
import com.twilighty.yuyu.core.network.model.EventSummaryDto
import com.twilighty.yuyu.core.network.model.TicketEvent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** How the live ticket is currently learning about its check-in. */
enum class LiveMode {
    /** Not watching (screen hidden, ticket not live). */
    Idle,

    /** Subscribed to `GET /api/ticket/:token/events`. */
    Streaming,

    /** SSE failed: polling `GET /api/ticket/:token/status` every 15 s. */
    Polling,

    /** Polling, but the server can't be reached right now. */
    Offline,
}

sealed interface LiveUpdate {
    data class Mode(val mode: LiveMode) : LiveUpdate

    /** [live] is true when the check-in happened while watching (animate the tear). */
    data class CheckedIn(val checkedInAt: String?, val live: Boolean) : LiveUpdate
}

/**
 * Watches one ticket for its check-in: SSE first; if the stream fails, falls
 * back to polling `/status` every [pollIntervalMillis]. A stream that simply
 * ends (the server closes it after ~10 minutes) is resubscribed. The flow
 * completes after the first check-in. Collect it only while the ticket is visible.
 */
class TicketLiveWatcher @Inject constructor(private val repository: PublicEventsRepository) {
    var pollIntervalMillis: Long = POLL_INTERVAL_MILLIS
    var resubscribeDelayMillis: Long = 1_000

    fun watch(token: String): Flow<LiveUpdate> = flow {
        var useStream = true
        var sawNotCheckedIn = false
        while (true) {
            if (useStream) {
                emit(LiveUpdate.Mode(LiveMode.Streaming))
                var checkedIn: LiveUpdate.CheckedIn? = null
                try {
                    repository.ticketEvents(token).collect { event ->
                        if (checkedIn != null) return@collect
                        when (event) {
                            is TicketEvent.State ->
                                if (event.checkedIn) checkedIn = LiveUpdate.CheckedIn(event.checkedInAt, live = sawNotCheckedIn) else sawNotCheckedIn = true
                            is TicketEvent.CheckedIn -> checkedIn = LiveUpdate.CheckedIn(event.checkedInAt, live = true)
                        }
                        checkedIn?.let { throw StopWatching(it) }
                    }
                    delay(resubscribeDelayMillis)
                } catch (stop: StopWatching) {
                    emit(stop.update)
                    return@flow
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    useStream = false
                }
            } else {
                when (val status = repository.ticketStatus(token)) {
                    is AppResult.Success -> {
                        if (status.data.checkedIn) {
                            emit(LiveUpdate.CheckedIn(status.data.checkedInAt, live = sawNotCheckedIn))
                            return@flow
                        }
                        sawNotCheckedIn = true
                        emit(LiveUpdate.Mode(LiveMode.Polling))
                    }
                    is AppResult.Failure ->
                        emit(LiveUpdate.Mode(if (status.error is AppError.Network) LiveMode.Offline else LiveMode.Polling))
                }
                delay(pollIntervalMillis)
            }
        }
    }

    /** Ends the SSE collection early without being mistaken for a transport failure. */
    private class StopWatching(val update: LiveUpdate.CheckedIn) : CancellationException("checked in") {
        override fun fillInStackTrace(): Throwable = this
    }

    companion object {
        const val POLL_INTERVAL_MILLIS = 15_000L
    }
}

/** What a ticket shows besides its token: the event and who it's for. */
data class TicketContext(
    val rsvpId: String?,
    val status: String,
    val checkedInAt: String?,
    val canCancel: Boolean,
    val attendeeName: String?,
    val event: EventSummaryDto?,
)

/**
 * In-memory hand-off of a fresh RSVP's event to its ticket screen (the
 * registrations cache refreshes in the background). Never persisted.
 */
@Singleton
class RecentTicketStore @Inject constructor() {
    private val tickets = ConcurrentHashMap<String, TicketContext>()
    fun put(token: String, context: TicketContext) {
        tickets[token] = context
    }

    fun get(token: String): TicketContext? = tickets[token]
}
