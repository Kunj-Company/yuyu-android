package com.twilighty.yuyu.feature.checkin

import com.twilighty.yuyu.feature.checkin.domain.ScanDebouncer
import com.twilighty.yuyu.feature.checkin.domain.StationLink
import com.twilighty.yuyu.feature.checkin.domain.StationLinkParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainHelpersTest {
    @Test
    fun debouncerIgnoresTheSameCodeForThreeSeconds() {
        val d = ScanDebouncer(3_000)
        assertTrue(d.accept("A", 0))
        assertFalse(d.accept("A", 2_999))
        assertTrue(d.accept("B", 3_000))
        assertTrue(d.accept("A", 3_001))
        d.restart(10_000)
        assertFalse(d.accept("A", 12_000))
        assertTrue(d.accept("A", 13_000))
    }

    private val server = "https://events.example.org/".toHttpUrl()

    @Test
    fun stationLinksAreParsed() {
        val ok = StationLink.Valid("civic-tech", "hack-night")
        assertEquals(ok, StationLinkParser.parse("https://events.example.org/civic-tech/hack-night/check-in", server))
        assertEquals(ok, StationLinkParser.parse("https://events.example.org/civic-tech/hack-night", server))
        assertEquals(ok, StationLinkParser.parse("events.example.org/civic-tech/hack-night/check-in", server))
        assertEquals(ok, StationLinkParser.parse(" civic-tech/hack-night ", server))
        assertEquals(ok, StationLinkParser.parse("/civic-tech/hack-night/check-in", server))
        assertEquals(
            ok,
            StationLinkParser.parse("https://example.org/yuyu/civic-tech/hack-night/check-in", "https://example.org/yuyu/".toHttpUrl()),
        )
    }

    @Test
    fun otherHostsAndOtherPagesAreRefused() {
        val other = StationLinkParser.parse("https://other.example/civic-tech/hack-night/check-in", server)
        assertTrue(other is StationLink.Invalid && other.message.contains("other.example"))
        assertTrue(StationLinkParser.parse("https://events.example.org/ticket/tkABCDEFGH123", server) is StationLink.Invalid)
        assertTrue(StationLinkParser.parse("https://events.example.org/civic-tech/hack-night/feedback", server) is StationLink.Invalid)
        assertTrue(StationLinkParser.parse("civic-tech", server) is StationLink.Invalid)
        assertTrue(StationLinkParser.parse("a/b/c/d", server) is StationLink.Invalid)
        assertTrue(StationLinkParser.parse("", server) is StationLink.Invalid)
        assertTrue(StationLinkParser.parse("civic tech/hack night", server) is StationLink.Invalid)
    }
}
