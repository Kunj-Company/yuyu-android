package com.twilighty.yuyu.core.network.api

import com.twilighty.yuyu.core.network.model.ApiEnvelope
import com.twilighty.yuyu.core.network.model.FeedbackSubmitRequest
import com.twilighty.yuyu.core.network.model.FeedbackSubmitResultDto
import com.twilighty.yuyu.core.network.model.HealthDto
import com.twilighty.yuyu.core.network.model.TicketStatusDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Existing public web endpoints the app reuses unchanged (docs/MOBILE_API.md
 * "Existing public endpoints reused unchanged"). The live ticket SSE stream is
 * in [com.twilighty.yuyu.core.network.sse.TicketEventsClient]; the station
 * endpoint is in [StationApi] (separate cookie-jar client).
 */
interface YuyuWebApi {
    /** Bare JSON `{ ok, uptimeMs, time }`; used to validate a server URL. */
    @GET("api/health")
    suspend fun health(): HealthDto

    /** Bare JSON `{ checkedIn, checkedInAt }`; 404/429 have an empty body. */
    @GET("api/ticket/{token}/status")
    suspend fun ticketStatus(@Path("token") token: String): TicketStatusDto

    /** `{ ok:true, data:{ certificateToken } }` or `{ ok:false, error:"text" }`. */
    @POST("api/feedback")
    suspend fun submitFeedback(@Body body: FeedbackSubmitRequest): ApiEnvelope<FeedbackSubmitResultDto>

    companion object {
        /** Relative path of the certificate JPEG for a token from [submitFeedback]. */
        fun certificatePath(certificateToken: String): String = "api/feedback/certificate/$certificateToken"

        /** Relative path of the website ticket page (for sharing / "open on web"). */
        fun ticketPagePath(ticketToken: String): String = "ticket/$ticketToken"
    }
}
