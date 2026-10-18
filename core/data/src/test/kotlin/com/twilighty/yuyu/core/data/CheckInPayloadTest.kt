package com.twilighty.yuyu.core.data

import com.twilighty.yuyu.core.data.util.CheckInPayload
import org.junit.Assert.assertEquals
import org.junit.Test

/** Mirrors the server's parseCheckInPayload so offline matching agrees with online. */
class CheckInPayloadTest {
    @Test
    fun extractsTokenFromUrlsPathsAndQueries() {
        assertEquals("tkABCDEFGH123", CheckInPayload.extractToken("https://yuyu.example/ticket/tkABCDEFGH123"))
        assertEquals("tkABCDEFGH123", CheckInPayload.extractToken("/ticket/tkABCDEFGH123?src=qr"))
        assertEquals("abc", CheckInPayload.extractToken("https://yuyu.example/c?t=abc"))
        assertEquals("tkABCDEFGH123", CheckInPayload.extractToken("  tkABCDEFGH123  "))
        assertEquals("", CheckInPayload.extractToken("   "))
    }
}
