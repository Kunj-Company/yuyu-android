package com.twilighty.yuyu.feature.checkin.station

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.common.result.map
import com.twilighty.yuyu.core.data.repository.StationKey
import com.twilighty.yuyu.core.data.repository.StationRepository
import com.twilighty.yuyu.feature.checkin.domain.AdmitResult
import com.twilighty.yuyu.feature.checkin.domain.LookupGuest
import com.twilighty.yuyu.feature.checkin.domain.toLookup
import com.twilighty.yuyu.feature.checkin.domain.toPreview
import com.twilighty.yuyu.feature.checkin.flow.CheckInBackend
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Keeps one [StationRepository] (and so one in-memory proof-cookie jar) per
 * door station while its unlock and kiosk screens are open, so the cookie set
 * by `unlock` is the one `bootstrap` sends. Memory only: nothing survives the
 * process, and [end] forgets the cookie when the station is exited.
 */
@Singleton
class StationSessions @Inject constructor(
    private val repositories: Provider<StationRepository>,
) {
    private val sessions = ConcurrentHashMap<StationKey, StationRepository>()

    fun get(key: StationKey): StationRepository = sessions.getOrPut(key) { repositories.get() }

    fun end(key: StationKey) {
        sessions.remove(key)?.lock()
    }
}

/**
 * The door-station endpoint as a [CheckInBackend]. Station failures arrive as
 * `{ ok:false, error:"text", needsForce? }`; the repository maps them to
 * `AppError.NeedsForce` (override possible) or another error with the text.
 */
internal class StationCheckInBackend(
    private val repository: StationRepository,
    private val key: StationKey,
) : CheckInBackend {
    override suspend fun previewCode(rawInput: String) = repository.previewCode(key, rawInput).map { it.toPreview() }

    override suspend fun previewRsvp(rsvpId: String) = repository.previewRsvp(key, rsvpId).map { it.toPreview() }

    override suspend fun checkIn(rsvpId: String, rawInput: String?, force: Boolean): AppResult<AdmitResult> =
        repository.checkIn(key, rsvpId, force).map { AdmitResult(it.toPreview(), it.checkedInAt, it.alreadyCheckedIn) }

    override suspend fun undo(rsvpId: String) = repository.undo(key, rsvpId)

    override suspend fun lookup(query: String): AppResult<List<LookupGuest>> =
        repository.lookup(key, query).map { dto -> dto.rows.map { it.toLookup() } }
}

/** Plain-language messages for station failures. Server texts are UI-safe and kept. */
object StationErrors {
    /** The station proof is missing or expired: unlock again. */
    fun isAccessLost(error: AppError): Boolean = error is AppError.Unauthenticated

    fun forUnlock(error: AppError): String = when (error) {
        is AppError.Unauthenticated -> error.message.takeUnless { it == AppError.Unauthenticated().message }
            ?: "Unable to open this check-in station."
        is AppError.RateLimited -> error.message
        is AppError.InvalidRequest -> error.message.takeUnless { it.startsWith("Check the highlighted") }
            ?: "That PIN isn't valid. Enter all 8 digits."
        is AppError.Network -> "You're offline or the server can't be reached. Door stations need a connection."
        is AppError.NoServer -> error.message
        else -> error.message
    }
}
