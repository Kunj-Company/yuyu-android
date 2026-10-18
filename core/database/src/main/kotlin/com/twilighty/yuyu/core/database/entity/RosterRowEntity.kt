package com.twilighty.yuyu.core.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * One guest of an offline check-in roster (`GET …/check-in/roster`), per event.
 * The ticket token itself is not stored: [ticketTokenSha256] (hex) lets a
 * scanned QR be matched offline without keeping the secret on disk.
 * Detail lists are JSON arrays of `{label,value}`.
 */
@Entity(
    tableName = "roster_rows",
    primaryKeys = ["eventId", "rsvpId"],
    indices = [Index(value = ["eventId", "ticketTokenSha256"]), Index(value = ["eventId", "displayName"])],
)
data class RosterRowEntity(
    val eventId: String,
    val rsvpId: String,
    val orgSlug: String,
    val displayName: String,
    val email: String?,
    val status: String,
    val checkedInAt: String?,
    val registeredAt: String,
    val isGuest: Boolean,
    val ticketTokenSha256: String?,
    val checkInDetailsJson: String,
    val registrationDetailsJson: String,
    /** Server `generatedAt` of the roster download this row came from. */
    val generatedAt: String,
)
