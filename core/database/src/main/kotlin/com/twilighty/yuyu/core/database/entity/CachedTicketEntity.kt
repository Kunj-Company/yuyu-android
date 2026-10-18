package com.twilighty.yuyu.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The signed-in user's own registrations, cached so tickets open offline at
 * the door. [encryptedTicketToken] is AES-GCM ciphertext (Android Keystore,
 * see `:core:data` SecretCipher): ticket tokens are never stored in clear.
 */
@Entity(tableName = "cached_tickets")
data class CachedTicketEntity(
    @PrimaryKey val rsvpId: String,
    val status: String,
    val checkedInAt: String?,
    val registeredAt: String,
    val canCancel: Boolean,
    val encryptedTicketToken: String?,
    val isUpcoming: Boolean,
    val eventId: String,
    val eventSlug: String,
    val eventTitle: String,
    val coverImageUrl: String?,
    val startDateTime: String,
    val endDateTime: String?,
    val timezone: String,
    val location: String?,
    val mapLinkUrl: String?,
    val isOnline: Boolean,
    val accentColor: String?,
    val eventStatus: String?,
    val orgSlug: String,
    val orgName: String,
    val orgLogoUrl: String?,
    val instanceId: String?,
    /** Epoch millis of the last successful refresh from the server. */
    val cachedAt: Long,
)
