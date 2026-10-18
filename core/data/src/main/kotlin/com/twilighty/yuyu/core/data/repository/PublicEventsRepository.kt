package com.twilighty.yuyu.core.data.repository

import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.server.ServerStore
import com.twilighty.yuyu.core.network.api.YuyuMobileApi
import com.twilighty.yuyu.core.network.api.YuyuWebApi
import com.twilighty.yuyu.core.network.error.apiCall
import com.twilighty.yuyu.core.network.error.rawCall
import com.twilighty.yuyu.core.network.interceptor.BaseUrlInterceptor
import com.twilighty.yuyu.core.network.model.EventPageDto
import com.twilighty.yuyu.core.network.model.FeedbackFormDto
import com.twilighty.yuyu.core.network.model.FeedbackSubmitRequest
import com.twilighty.yuyu.core.network.model.FeedbackSubmitResultDto
import com.twilighty.yuyu.core.network.model.PublicEventResponseDto
import com.twilighty.yuyu.core.network.model.RsvpRequest
import com.twilighty.yuyu.core.network.model.RsvpResultDto
import com.twilighty.yuyu.core.network.model.ShortLinkDto
import com.twilighty.yuyu.core.network.model.TicketEvent
import com.twilighty.yuyu.core.network.model.TicketStatusDto
import com.twilighty.yuyu.core.network.sse.TicketEventsClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface PublicEventsRepository {
    suspend fun discover(cursor: String? = null, limit: Int? = null): AppResult<EventPageDto>
    suspend fun search(query: String): AppResult<EventPageDto>
    suspend fun resolveShortLink(code: String): AppResult<ShortLinkDto>
    suspend fun event(orgSlug: String, eventSlug: String): AppResult<PublicEventResponseDto>
    suspend fun register(request: RsvpRequest): AppResult<RsvpResultDto>
    suspend fun feedbackForm(orgSlug: String, eventSlug: String): AppResult<FeedbackFormDto>
    suspend fun submitFeedback(request: FeedbackSubmitRequest): AppResult<FeedbackSubmitResultDto>

    /** Bare `{ checkedIn, checkedInAt }` (polling fallback for the live ticket). */
    suspend fun ticketStatus(ticketToken: String): AppResult<TicketStatusDto>

    /** Live check-in stream; see [TicketEventsClient.events] for completion/failure semantics. */
    fun ticketEvents(ticketToken: String): Flow<TicketEvent>

    /** Absolute certificate JPEG URL for a token from [submitFeedback]. */
    fun certificateUrl(certificateToken: String): String?
}

@Singleton
class PublicEventsRepositoryImpl @Inject constructor(
    private val api: YuyuMobileApi,
    private val webApi: YuyuWebApi,
    private val ticketEventsClient: TicketEventsClient,
    private val serverStore: ServerStore,
) : PublicEventsRepository {
    override suspend fun discover(cursor: String?, limit: Int?) = apiCall { api.discover(cursor, limit) }
    override suspend fun search(query: String) = apiCall { api.search(query.trim()) }
    override suspend fun resolveShortLink(code: String) = apiCall { api.resolveShortLink(code) }
    override suspend fun event(orgSlug: String, eventSlug: String) = apiCall { api.publicEvent(orgSlug, eventSlug) }
    override suspend fun register(request: RsvpRequest) = apiCall { api.rsvp(request) }
    override suspend fun feedbackForm(orgSlug: String, eventSlug: String) = apiCall { api.feedbackForm(orgSlug, eventSlug) }
    override suspend fun submitFeedback(request: FeedbackSubmitRequest): AppResult<FeedbackSubmitResultDto> =
        apiCall { webApi.submitFeedback(request) }
    override suspend fun ticketStatus(ticketToken: String) = rawCall { webApi.ticketStatus(ticketToken) }
    override fun ticketEvents(ticketToken: String): Flow<TicketEvent> = ticketEventsClient.events(ticketToken)
    override fun certificateUrl(certificateToken: String): String? = serverStore.currentServer()?.let {
        BaseUrlInterceptor.resolve(it, YuyuWebApi.certificatePath(certificateToken)).toString()
    }
}
