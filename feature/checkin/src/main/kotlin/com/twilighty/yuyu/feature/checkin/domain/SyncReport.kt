package com.twilighty.yuyu.feature.checkin.domain

import com.twilighty.yuyu.core.database.entity.PendingCheckInEntity
import com.twilighty.yuyu.core.network.model.SyncCheckInsResultDto

sealed interface SyncOutcome {
    data object Synced : SyncOutcome
    data object AlreadyCheckedIn : SyncOutcome
    data class Failed(val reason: String) : SyncOutcome

    /** The server didn't mention it: it stays queued and is retried. */
    data object StillQueued : SyncOutcome
}

data class SyncItem(val rsvpId: String, val displayName: String, val outcome: SyncOutcome)

data class SyncReport(val items: List<SyncItem>) {
    val synced: Int get() = items.count { it.outcome == SyncOutcome.Synced }
    val alreadyCheckedIn: Int get() = items.count { it.outcome == SyncOutcome.AlreadyCheckedIn }
    val failed: Int get() = items.count { it.outcome is SyncOutcome.Failed }
    val stillQueued: Int get() = items.count { it.outcome == SyncOutcome.StillQueued }

    val summary: String
        get() = buildList {
            if (synced > 0) add("$synced synced")
            if (alreadyCheckedIn > 0) add("$alreadyCheckedIn already checked in")
            if (failed > 0) add("$failed need attention")
            if (stillQueued > 0) add("$stillQueued still waiting")
        }.ifEmpty { listOf("Nothing to sync") }.joinToString(" · ")
}

/** A queued offline check-in, for the "waiting to sync" list. */
data class PendingItem(
    val rsvpId: String,
    val displayName: String,
    val checkedInAt: String,
    val attempts: Int,
    val lastError: String?,
)

/**
 * Maps a `POST …/check-in/sync` response onto the items that were sent, in the
 * order they were queued. Precedence follows what the repository does with the
 * queue: synced and already-checked-in ids are removed (so they win over a
 * stray `failed` entry), failed ones stay queued with their reason.
 */
object SyncReportMapper {
    fun map(
        sent: List<PendingCheckInEntity>,
        result: SyncCheckInsResultDto,
        names: Map<String, String> = emptyMap(),
    ): SyncReport {
        val synced = result.syncedIds.toSet()
        val already = result.alreadyCheckedInIds.toSet()
        val failures = result.failed.associate { it.rsvpId to it.error }
        val items = sent.distinctBy { it.rsvpId }.map { item ->
            val outcome = when (item.rsvpId) {
                in synced -> SyncOutcome.Synced
                in already -> SyncOutcome.AlreadyCheckedIn
                in failures -> SyncOutcome.Failed(failures.getValue(item.rsvpId).ifBlank { "The server couldn't check this guest in." })
                else -> SyncOutcome.StillQueued
            }
            SyncItem(item.rsvpId, names[item.rsvpId] ?: "Guest", outcome)
        }
        return SyncReport(items)
    }

    fun pendingItems(pending: List<PendingCheckInEntity>, names: Map<String, String>): List<PendingItem> =
        pending.map { PendingItem(it.rsvpId, names[it.rsvpId] ?: "Guest", it.checkedInAt, it.attempts, it.lastError) }
}
