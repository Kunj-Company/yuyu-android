package com.twilighty.yuyu.core.network.sse

import com.twilighty.yuyu.core.network.NetworkJson
import com.twilighty.yuyu.core.network.di.StreamingClient
import com.twilighty.yuyu.core.network.interceptor.BaseUrlInterceptor
import com.twilighty.yuyu.core.network.model.TicketCheckedInPayload
import com.twilighty.yuyu.core.network.model.TicketEvent
import com.twilighty.yuyu.core.network.model.TicketStatusDto
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * `GET /api/ticket/:token/events` as a cold [Flow]: an initial `state`, then a
 * single `checked-in`, then the server closes the stream (it also closes after
 * ~10 minutes). The flow completes when the stream ends and fails with an
 * [IOException] on transport errors or 404/429/503, so callers can fall back to
 * polling `/status` and resubscribe. Collect it only while the ticket is visible.
 */
@Singleton
class TicketEventsClient @Inject constructor(
    @param:StreamingClient private val client: OkHttpClient,
) {
    fun events(ticketToken: String): Flow<TicketEvent> = callbackFlow {
        val request = Request.Builder()
            .url(BaseUrlInterceptor.PLACEHOLDER_BASE_URL + "api/ticket/" + ticketToken + "/events")
            .header("Accept", "text/event-stream")
            .build()
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                parse(type, data)?.let { trySend(it) }
            }

            override fun onClosed(eventSource: EventSource) {
                channel.close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val code = response?.code
                channel.close(t ?: IOException("Ticket stream failed" + (code?.let { " ($it)" } ?: "")))
            }
        }
        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    companion object {
        fun parse(type: String?, data: String): TicketEvent? = runCatching {
            when (type) {
                // Named `state` events, or unnamed `message` events carrying the same payload.
                "state", "message", null -> NetworkJson.decodeFromString(TicketStatusDto.serializer(), data)
                    .let { TicketEvent.State(it.checkedIn, it.checkedInAt) }
                "checked-in" -> NetworkJson.decodeFromString(TicketCheckedInPayload.serializer(), data)
                    .let { TicketEvent.CheckedIn(it.checkedInAt) }
                else -> null
            }
        }.getOrNull()
    }
}
