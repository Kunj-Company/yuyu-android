package com.twilighty.yuyu.core.database.entity

import androidx.room.Entity

/**
 * The last programme an organiser synced for one event, so the schedule opens
 * without a connection. [scheduleJson] is the server's `OrgScheduleDto` as
 * returned; the event header fields let the screen render without the event
 * detail call. Session titles and speaker names are not secrets, and the whole
 * database is wiped on sign-out.
 */
@Entity(tableName = "cached_schedules", primaryKeys = ["orgSlug", "eventId"])
data class CachedScheduleEntity(
    val orgSlug: String,
    val eventId: String,
    val eventTitle: String,
    val timezone: String,
    /** The event's venue (free text) and map link, shown with the programme. */
    val location: String? = null,
    val mapLinkUrl: String? = null,
    /** Whether this organiser could set live delays when the copy was saved. */
    val canEditDelays: Boolean,
    val scheduleJson: String,
    /** Server time of the copy (`generatedAt`), when the server sends it. */
    val generatedAt: String?,
    /** Epoch millis of the last successful sync on this device. */
    val syncedAt: Long,
)
