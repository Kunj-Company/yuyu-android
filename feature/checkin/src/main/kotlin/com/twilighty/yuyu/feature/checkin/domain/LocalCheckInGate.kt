package com.twilighty.yuyu.feature.checkin.domain

import com.twilighty.yuyu.core.network.model.RsvpStatus

/**
 * Port of the server's `gateCheckInForStatus` (lib/checkIn.ts), applied to the
 * offline roster. Reasons are the website's texts. A local decision is only a
 * hint: the server re-applies the rule when the queue syncs.
 */
object LocalCheckInGate {
    fun gate(status: String, force: Boolean): GateInfo = when {
        status == RsvpStatus.CONFIRMED -> GateInfo.Ok
        status == RsvpStatus.REJECTED -> GateInfo(
            ok = false,
            reason = "This registration was rejected.",
            needsForce = false,
            blocked = true,
        )
        force && status == RsvpStatus.WAITLISTED -> GateInfo.Ok
        status == RsvpStatus.WAITLISTED -> GateInfo(
            ok = false,
            reason = "Guest is on the waitlist — use “Override” to check in at the door.",
            needsForce = true,
        )
        status == RsvpStatus.PENDING_APPROVAL -> GateInfo(
            ok = false,
            reason = "Registration is pending approval and cannot be checked in.",
            needsForce = false,
            blocked = true,
        )
        else -> GateInfo(
            ok = false,
            reason = "This RSVP cannot be checked in.",
            needsForce = false,
            blocked = true,
        )
    }
}
