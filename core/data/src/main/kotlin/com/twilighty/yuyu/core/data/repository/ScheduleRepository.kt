package com.twilighty.yuyu.core.data.repository

import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.database.dao.CachedScheduleDao
import com.twilighty.yuyu.core.database.entity.CachedScheduleEntity
import com.twilighty.yuyu.core.network.NetworkJson
import com.twilighty.yuyu.core.network.api.YuyuMobileApi
import com.twilighty.yuyu.core.network.error.apiCall
import com.twilighty.yuyu.core.network.model.OrgScheduleDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** An organiser's offline copy of one event's programme. */
data class SavedSchedule(
    val orgSlug: String,
    val eventId: String,
    val eventTitle: String,
    val timezone: String,
    /** Where the event happens (free text), for the venue line; halls are the programme's tracks. */
    val location: String?,
    val mapLinkUrl: String?,
    val canEditDelays: Boolean,
    val schedule: OrgScheduleDto,
    /** Server time of the copy, when the server sends it. */
    val generatedAt: String?,
    /** Epoch millis of the last successful sync on this device. */
    val syncedAt: Long,
)

/**
 * The organiser programme, kept on the device so it opens offline. The screen
 * shows the saved copy at once and [sync] replaces it; a failed sync leaves the
 * saved copy untouched. Wiped with the rest of the database on sign-out.
 */
interface ScheduleRepository {
    fun observe(orgSlug: String, eventId: String): Flow<SavedSchedule?>

    /** Every saved programme, most recently synced first. */
    fun observeAll(): Flow<List<SavedSchedule>>

    /** Downloads the event header and programme together and saves them. */
    suspend fun sync(orgSlug: String, eventId: String): AppResult<SavedSchedule>
}

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val api: YuyuMobileApi,
    private val dao: CachedScheduleDao,
) : ScheduleRepository {
    override fun observe(orgSlug: String, eventId: String): Flow<SavedSchedule?> =
        dao.observe(orgSlug, eventId).map { it?.decodeOrNull() }

    override fun observeAll(): Flow<List<SavedSchedule>> = dao.observeAll().map { rows -> rows.mapNotNull { it.decodeOrNull() } }

    override suspend fun sync(orgSlug: String, eventId: String): AppResult<SavedSchedule> = coroutineScope {
        val schedule = async { apiCall { api.schedule(orgSlug, eventId) } }
        val detail = apiCall { api.orgEvent(orgSlug, eventId) }
        val programme = schedule.await()
        when {
            detail is AppResult.Failure -> detail
            programme is AppResult.Failure -> programme
            else -> {
                val event = (detail as AppResult.Success).data
                val dto = (programme as AppResult.Success).data
                val entity = CachedScheduleEntity(
                    orgSlug = orgSlug,
                    eventId = eventId,
                    eventTitle = event.event.title,
                    timezone = event.event.timezone,
                    location = event.event.location?.takeIf { it.isNotBlank() },
                    mapLinkUrl = event.event.mapLinkUrl,
                    canEditDelays = event.myPermissions.schedule,
                    scheduleJson = NetworkJson.encodeToString(OrgScheduleDto.serializer(), dto),
                    generatedAt = dto.generatedAt,
                    syncedAt = System.currentTimeMillis(),
                )
                dao.upsert(entity)
                AppResult.Success(entity.toSaved(dto))
            }
        }
    }
}

/** A copy written by an older app version that no longer decodes is treated as not saved. */
private fun CachedScheduleEntity.decodeOrNull(): SavedSchedule? =
    runCatching { NetworkJson.decodeFromString(OrgScheduleDto.serializer(), scheduleJson) }.getOrNull()?.let(::toSaved)

private fun CachedScheduleEntity.toSaved(schedule: OrgScheduleDto) =
    SavedSchedule(orgSlug, eventId, eventTitle, timezone, location, mapLinkUrl, canEditDelays, schedule, generatedAt, syncedAt)
