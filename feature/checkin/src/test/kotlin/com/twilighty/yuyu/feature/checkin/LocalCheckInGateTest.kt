package com.twilighty.yuyu.feature.checkin

import com.twilighty.yuyu.feature.checkin.domain.GateInfo
import com.twilighty.yuyu.feature.checkin.domain.LocalCheckInGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parity with the web `gateCheckInForStatus`, including tests/unit/security-visibility.test.ts. */
class LocalCheckInGateTest {
    @Test
    fun portedWebCases() {
        assertFalse(LocalCheckInGate.gate("REJECTED", false).ok)
        LocalCheckInGate.gate("WAITLISTED", false).let { assertFalse(it.ok); assertTrue(it.needsForce) }
        assertTrue(LocalCheckInGate.gate("WAITLISTED", true).ok)
        LocalCheckInGate.gate("PENDING_APPROVAL", false).let { assertFalse(it.ok); assertTrue(it.blocked) }
        LocalCheckInGate.gate("PENDING_APPROVAL", true).let { assertFalse(it.ok); assertTrue(it.blocked) }
    }

    @Test
    fun exactShapesAndReasons() {
        assertEquals(GateInfo(ok = true), LocalCheckInGate.gate("CONFIRMED", false))
        assertEquals(GateInfo(ok = true), LocalCheckInGate.gate("CONFIRMED", true))
        assertEquals(
            GateInfo(false, "This registration was rejected.", needsForce = false, blocked = true),
            LocalCheckInGate.gate("REJECTED", true),
        )
        assertEquals(
            GateInfo(false, "Guest is on the waitlist — use “Override” to check in at the door.", needsForce = true, blocked = false),
            LocalCheckInGate.gate("WAITLISTED", false),
        )
        assertEquals(
            GateInfo(false, "Registration is pending approval and cannot be checked in.", needsForce = false, blocked = true),
            LocalCheckInGate.gate("PENDING_APPROVAL", false),
        )
        assertEquals(
            GateInfo(false, "This RSVP cannot be checked in.", needsForce = false, blocked = true),
            LocalCheckInGate.gate("CANCELLED", true),
        )
        assertEquals(GateInfo(false, "This RSVP cannot be checked in.", needsForce = false, blocked = true), LocalCheckInGate.gate("SOMETHING_NEW", false))
    }
}
