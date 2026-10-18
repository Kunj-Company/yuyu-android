package com.twilighty.yuyu.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A check-in recorded offline, waiting for `POST …/check-in/sync`. */
@Entity(
    tableName = "pending_check_ins",
    indices = [Index(value = ["eventId", "rsvpId"], unique = true)],
)
data class PendingCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orgSlug: String,
    val eventId: String,
    val rsvpId: String,
    /** ISO-8601 UTC time the guest was admitted at the door. */
    val checkedInAt: String,
    val force: Boolean,
    val createdAt: Long,
    /** Idempotency key sent with the sync so a retried upload never double-counts. */
    val clientMutationId: String,
    val attempts: Int = 0,
    /** Last server error for this row (from `failed[]`), shown to the organiser. */
    val lastError: String? = null,
)
