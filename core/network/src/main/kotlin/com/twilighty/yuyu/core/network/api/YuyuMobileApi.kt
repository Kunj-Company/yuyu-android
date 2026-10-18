package com.twilighty.yuyu.core.network.api

import com.twilighty.yuyu.core.network.model.AddGuestRequest
import com.twilighty.yuyu.core.network.model.ApiEnvelope
import com.twilighty.yuyu.core.network.model.AuthConfigDto
import com.twilighty.yuyu.core.network.model.BroadcastContentRequest
import com.twilighty.yuyu.core.network.model.BroadcastCountRequest
import com.twilighty.yuyu.core.network.model.BroadcastMetaDto
import com.twilighty.yuyu.core.network.model.BroadcastPreviewDto
import com.twilighty.yuyu.core.network.model.BroadcastTestResultDto
import com.twilighty.yuyu.core.network.model.CheckInPreviewDto
import com.twilighty.yuyu.core.network.model.CheckInPreviewRequest
import com.twilighty.yuyu.core.network.model.CheckInRequest
import com.twilighty.yuyu.core.network.model.CheckInResultDto
import com.twilighty.yuyu.core.network.model.CheckInStatsDto
import com.twilighty.yuyu.core.network.model.CountDto
import com.twilighty.yuyu.core.network.model.EventPageDto
import com.twilighty.yuyu.core.network.model.FeedbackFormDto
import com.twilighty.yuyu.core.network.model.FeedbackStateDto
import com.twilighty.yuyu.core.network.model.GoogleLoginRequest
import com.twilighty.yuyu.core.network.model.GuestDetailDto
import com.twilighty.yuyu.core.network.model.GuestDto
import com.twilighty.yuyu.core.network.model.GuestPageDto
import com.twilighty.yuyu.core.network.model.LoginRequest
import com.twilighty.yuyu.core.network.model.LookupRowsDto
import com.twilighty.yuyu.core.network.model.MeDto
import com.twilighty.yuyu.core.network.model.OpenStateRequest
import com.twilighty.yuyu.core.network.model.OrgEventDetailResponseDto
import com.twilighty.yuyu.core.network.model.OrgEventsDto
import com.twilighty.yuyu.core.network.model.OrgScheduleDto
import com.twilighty.yuyu.core.network.model.PublicEventResponseDto
import com.twilighty.yuyu.core.network.model.RefreshRequest
import com.twilighty.yuyu.core.network.model.RegistrationStateDto
import com.twilighty.yuyu.core.network.model.RegistrationsDto
import com.twilighty.yuyu.core.network.model.RestoreGuestRequest
import com.twilighty.yuyu.core.network.model.RosterDto
import com.twilighty.yuyu.core.network.model.RsvpIdRequest
import com.twilighty.yuyu.core.network.model.RsvpRequest
import com.twilighty.yuyu.core.network.model.RsvpResultDto
import com.twilighty.yuyu.core.network.model.SendBroadcastRequest
import com.twilighty.yuyu.core.network.model.SendBroadcastResultDto
import com.twilighty.yuyu.core.network.model.SessionDelayRequest
import com.twilighty.yuyu.core.network.model.SessionDto
import com.twilighty.yuyu.core.network.model.ShortLinkDto
import com.twilighty.yuyu.core.network.model.SyncCheckInsRequest
import com.twilighty.yuyu.core.network.model.SyncCheckInsResultDto
import com.twilighty.yuyu.core.network.model.UndoIdDto
import com.twilighty.yuyu.core.network.model.UpdateMeRequest
import com.twilighty.yuyu.core.network.model.UserDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * `/api/mobile/v1` — the whole user-authenticated mobile contract
 * (docs/MOBILE_API.md). Paths are relative; the server is chosen at runtime by
 * [com.twilighty.yuyu.core.network.interceptor.BaseUrlInterceptor]. The bearer
 * is attached by [com.twilighty.yuyu.core.network.interceptor.AuthInterceptor]
 * and refreshed once on 401 by [com.twilighty.yuyu.core.network.auth.TokenAuthenticator].
 *
 * Non-2xx responses surface as `retrofit2.HttpException`; always call through
 * [com.twilighty.yuyu.core.network.error.apiCall] to get a typed result.
 * Endpoints whose success `data` is `null` return `ApiEnvelope<JsonElement>`.
 */
interface YuyuMobileApi {

    // ---- Sessions -------------------------------------------------------------------------

    @POST("$PREFIX/auth/login")
    suspend fun login(@Body body: LoginRequest): ApiEnvelope<SessionDto>

    @POST("$PREFIX/auth/google")
    suspend fun loginWithGoogle(@Body body: GoogleLoginRequest): ApiEnvelope<SessionDto>

    /** Normally only called by [com.twilighty.yuyu.core.network.auth.TokenRefresher]; exposed for completeness. */
    @POST("$PREFIX/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): ApiEnvelope<SessionDto>

    @POST("$PREFIX/auth/logout")
    suspend fun logout(@Body body: JsonObject = EMPTY_BODY): ApiEnvelope<JsonElement>

    @POST("$PREFIX/auth/logout-all")
    suspend fun logoutAll(@Body body: JsonObject = EMPTY_BODY): ApiEnvelope<JsonElement>

    @GET("$PREFIX/auth/config")
    suspend fun authConfig(): ApiEnvelope<AuthConfigDto>

    // ---- Me -------------------------------------------------------------------------------

    @GET("$PREFIX/me")
    suspend fun me(): ApiEnvelope<MeDto>

    @PATCH("$PREFIX/me")
    suspend fun updateMe(@Body body: UpdateMeRequest): ApiEnvelope<UserDto>

    @GET("$PREFIX/me/registrations")
    suspend fun myRegistrations(): ApiEnvelope<RegistrationsDto>

    @POST("$PREFIX/me/registrations/{rsvpId}/cancel")
    suspend fun cancelRegistration(
        @Path("rsvpId") rsvpId: String,
        @Body body: JsonObject = EMPTY_BODY,
    ): ApiEnvelope<JsonElement>

    // ---- Public ---------------------------------------------------------------------------

    @GET("$PREFIX/public/discover")
    suspend fun discover(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): ApiEnvelope<EventPageDto>

    @GET("$PREFIX/public/search")
    suspend fun search(@Query("q") query: String): ApiEnvelope<EventPageDto>

    @GET("$PREFIX/public/short/{code}")
    suspend fun resolveShortLink(@Path("code") code: String): ApiEnvelope<ShortLinkDto>

    @GET("$PREFIX/public/events/{orgSlug}/{eventSlug}")
    suspend fun publicEvent(
        @Path("orgSlug") orgSlug: String,
        @Path("eventSlug") eventSlug: String,
    ): ApiEnvelope<PublicEventResponseDto>

    @POST("$PREFIX/rsvp")
    suspend fun rsvp(@Body body: RsvpRequest): ApiEnvelope<RsvpResultDto>

    @GET("$PREFIX/public/events/{orgSlug}/{eventSlug}/feedback")
    suspend fun feedbackForm(
        @Path("orgSlug") orgSlug: String,
        @Path("eventSlug") eventSlug: String,
    ): ApiEnvelope<FeedbackFormDto>

    // ---- Organiser: events ----------------------------------------------------------------

    /** [filter]: upcoming | past | all (see OrgEventsFilter). */
    @GET("$PREFIX/orgs/{orgSlug}/events")
    suspend fun orgEvents(
        @Path("orgSlug") orgSlug: String,
        @Query("filter") filter: String? = null,
    ): ApiEnvelope<OrgEventsDto>

    @GET("$PREFIX/orgs/{orgSlug}/events/{eventId}")
    suspend fun orgEvent(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
    ): ApiEnvelope<OrgEventDetailResponseDto>

    // ---- Organiser: guests ----------------------------------------------------------------

    @GET("$PREFIX/orgs/{orgSlug}/events/{eventId}/guests")
    suspend fun guests(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Query("status") status: String? = null,
        @Query("checkedIn") checkedIn: String? = null,
        @Query("q") query: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): ApiEnvelope<GuestPageDto>

    @GET("$PREFIX/orgs/{orgSlug}/events/{eventId}/guests/{rsvpId}")
    suspend fun guest(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Path("rsvpId") rsvpId: String,
    ): ApiEnvelope<GuestDetailDto>

    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/guests/{rsvpId}/approve")
    suspend fun approveGuest(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Path("rsvpId") rsvpId: String,
        @Body body: JsonObject = EMPTY_BODY,
    ): ApiEnvelope<GuestDto>

    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/guests/{rsvpId}/reject")
    suspend fun rejectGuest(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Path("rsvpId") rsvpId: String,
        @Body body: JsonObject = EMPTY_BODY,
    ): ApiEnvelope<GuestDto>

    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/guests/{rsvpId}/promote")
    suspend fun promoteGuest(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Path("rsvpId") rsvpId: String,
        @Body body: JsonObject = EMPTY_BODY,
    ): ApiEnvelope<GuestDto>

    /** 201 Created. */
    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/guests")
    suspend fun addGuest(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Body body: AddGuestRequest,
    ): ApiEnvelope<GuestDto>

    @DELETE("$PREFIX/orgs/{orgSlug}/events/{eventId}/guests/{rsvpId}")
    suspend fun deleteGuest(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Path("rsvpId") rsvpId: String,
    ): ApiEnvelope<UndoIdDto>

    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/guests/restore")
    suspend fun restoreGuest(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Body body: RestoreGuestRequest,
    ): ApiEnvelope<JsonElement>

    // ---- Organiser: check-in --------------------------------------------------------------

    /** Body: exactly one of `rawInput` / `rsvpId`. */
    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/check-in/preview")
    suspend fun checkInPreview(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Body body: CheckInPreviewRequest,
    ): ApiEnvelope<CheckInPreviewDto>

    /** NEEDS_FORCE (409) when a status override is possible: retry with `force = true`. */
    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/check-in")
    suspend fun checkIn(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Body body: CheckInRequest,
    ): ApiEnvelope<CheckInResultDto>

    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/check-in/undo")
    suspend fun undoCheckIn(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Body body: RsvpIdRequest,
    ): ApiEnvelope<JsonElement>

    /** [query] must be at least 2 characters. */
    @GET("$PREFIX/orgs/{orgSlug}/events/{eventId}/check-in/lookup")
    suspend fun checkInLookup(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Query("q") query: String,
    ): ApiEnvelope<LookupRowsDto>

    @GET("$PREFIX/orgs/{orgSlug}/events/{eventId}/check-in/stats")
    suspend fun checkInStats(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
    ): ApiEnvelope<CheckInStatsDto>

    @GET("$PREFIX/orgs/{orgSlug}/events/{eventId}/check-in/roster")
    suspend fun checkInRoster(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
    ): ApiEnvelope<RosterDto>

    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/check-in/sync")
    suspend fun syncCheckIns(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Body body: SyncCheckInsRequest,
    ): ApiEnvelope<SyncCheckInsResultDto>

    // ---- Organiser: emails ----------------------------------------------------------------

    @GET("$PREFIX/orgs/{orgSlug}/events/{eventId}/broadcasts/meta")
    suspend fun broadcastMeta(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
    ): ApiEnvelope<BroadcastMetaDto>

    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/broadcasts/count")
    suspend fun broadcastCount(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Body body: BroadcastCountRequest,
    ): ApiEnvelope<CountDto>

    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/broadcasts/preview")
    suspend fun broadcastPreview(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Body body: BroadcastContentRequest,
    ): ApiEnvelope<BroadcastPreviewDto>

    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/broadcasts/test")
    suspend fun broadcastTest(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Body body: BroadcastContentRequest,
    ): ApiEnvelope<BroadcastTestResultDto>

    /** 201 Created. The 5-second undo is client-side: delay this call, don't call-then-cancel. */
    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/broadcasts")
    suspend fun sendBroadcast(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Body body: SendBroadcastRequest,
    ): ApiEnvelope<SendBroadcastResultDto>

    // ---- Organiser: day-of ----------------------------------------------------------------

    @GET("$PREFIX/orgs/{orgSlug}/events/{eventId}/schedule")
    suspend fun schedule(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
    ): ApiEnvelope<OrgScheduleDto>

    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/schedule/sessions/{sessionId}/delay")
    suspend fun setSessionDelay(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Path("sessionId") sessionId: String,
        @Body body: SessionDelayRequest,
    ): ApiEnvelope<JsonElement>

    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/feedback/state")
    suspend fun setFeedbackState(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Body body: OpenStateRequest,
    ): ApiEnvelope<FeedbackStateDto>

    @POST("$PREFIX/orgs/{orgSlug}/events/{eventId}/registration/state")
    suspend fun setRegistrationState(
        @Path("orgSlug") orgSlug: String,
        @Path("eventId") eventId: String,
        @Body body: OpenStateRequest,
    ): ApiEnvelope<RegistrationStateDto>

    companion object {
        const val PREFIX = "api/mobile/v1"
        val EMPTY_BODY: JsonObject = buildJsonObject { }
    }
}
