package com.twilighty.yuyu.core.data.repository

import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.network.NetworkJson
import com.twilighty.yuyu.core.network.error.ApiErrorParser
import com.twilighty.yuyu.core.network.error.apiCallUnit
import com.twilighty.yuyu.core.network.error.rawCall
import com.twilighty.yuyu.core.network.model.StationAction
import com.twilighty.yuyu.core.network.model.StationBootstrapDto
import com.twilighty.yuyu.core.network.model.StationCheckInDto
import com.twilighty.yuyu.core.network.model.StationLookupDto
import com.twilighty.yuyu.core.network.model.StationRequest
import com.twilighty.yuyu.core.network.station.StationClient
import com.twilighty.yuyu.core.network.station.StationClientFactory
import javax.inject.Inject
import kotlinx.serialization.DeserializationStrategy

/** Identifies the door station: `/{orgSlug}/{eventSlug}/check-in` on the web. */
data class StationKey(val orgSlug: String, val eventSlug: String)

/**
 * Door-station mode over `POST /api/check-in/station`. Each instance owns one
 * isolated in-memory cookie jar, so scope it to the station screen
 * (inject into a ViewModel — it is deliberately not a singleton) and call
 * [lock] when leaving. The PIN is passed straight through and never stored.
 */
interface StationRepository {
    val isUnlocked: Boolean
    suspend fun unlock(key: StationKey, pin: String): AppResult<Unit>
    suspend fun bootstrap(key: StationKey): AppResult<StationBootstrapDto>
    suspend fun previewCode(key: StationKey, rawInput: String): AppResult<StationCheckInDto>
    suspend fun previewRsvp(key: StationKey, rsvpId: String): AppResult<StationCheckInDto>

    /** Fails with NeedsForce when an override is possible. */
    suspend fun checkIn(key: StationKey, rsvpId: String, force: Boolean = false): AppResult<StationCheckInDto>
    suspend fun lookup(key: StationKey, query: String): AppResult<StationLookupDto>
    suspend fun undo(key: StationKey, rsvpId: String): AppResult<Unit>
    fun lock()
}

class StationRepositoryImpl @Inject constructor(
    factory: StationClientFactory,
) : StationRepository {
    private val client: StationClient = factory.create()

    override val isUnlocked: Boolean get() = client.isUnlocked

    override suspend fun unlock(key: StationKey, pin: String) =
        apiCallUnit { client.api.call(request(key, StationAction.UNLOCK).copy(pin = pin)) }

    override suspend fun bootstrap(key: StationKey) =
        call(request(key, StationAction.BOOTSTRAP), StationBootstrapDto.serializer())

    override suspend fun previewCode(key: StationKey, rawInput: String) =
        call(request(key, StationAction.PREVIEW_TOKEN).copy(rawInput = rawInput.trim()), StationCheckInDto.serializer())

    override suspend fun previewRsvp(key: StationKey, rsvpId: String) =
        call(request(key, StationAction.PREVIEW_RSVP).copy(rsvpId = rsvpId), StationCheckInDto.serializer())

    override suspend fun checkIn(key: StationKey, rsvpId: String, force: Boolean) =
        call(request(key, StationAction.CHECK_IN_RSVP).copy(rsvpId = rsvpId, force = force), StationCheckInDto.serializer())

    override suspend fun lookup(key: StationKey, query: String) =
        call(request(key, StationAction.LOOKUP).copy(query = query.trim()), StationLookupDto.serializer())

    override suspend fun undo(key: StationKey, rsvpId: String) =
        apiCallUnit { client.api.call(request(key, StationAction.UNDO).copy(rsvpId = rsvpId)) }

    override fun lock() = client.lock()

    private fun request(key: StationKey, action: String) = StationRequest(key.orgSlug, key.eventSlug, action)

    private suspend fun <T> call(body: StationRequest, deserializer: DeserializationStrategy<T>): AppResult<T> {
        val raw = rawCall { client.api.call(body) }
        if (raw is AppResult.Failure) return raw
        val envelope = (raw as AppResult.Success).data
        val data = envelope.data
        return when {
            !envelope.ok -> AppResult.Failure(ApiErrorParser.fromEnvelope(envelope))
            data == null -> AppResult.Failure(com.twilighty.yuyu.core.common.result.AppError.Unknown())
            else -> runCatching { AppResult.Success(NetworkJson.decodeFromJsonElement(deserializer, data)) }
                .getOrElse { AppResult.Failure(com.twilighty.yuyu.core.common.result.AppError.Unknown()) }
        }
    }
}
