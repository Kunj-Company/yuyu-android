package com.twilighty.yuyu.feature.checkin

import com.twilighty.yuyu.core.data.util.CheckInPayload
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Parity with the web `parseCheckInPayload` (lib/checkIn.ts). The web repo has
 * no dedicated cases for it, so every expectation below was produced by running
 * the TypeScript function itself (Node, WHATWG `URL`) on the same input.
 */
class CheckInPayloadParityTest {
    private fun parse(raw: String) = CheckInPayload.extractToken(raw)

    @Test
    fun emptyAndBlankInputGiveNothing() {
        assertEquals("", parse(""))
        assertEquals("", parse("   \n\t"))
    }

    @Test
    fun rawTokensAreTrimmedAndKept() {
        assertEquals("tkABCDEFGH123", parse("tkABCDEFGH123"))
        assertEquals("tkABCDEFGH123", parse("  tkABCDEFGH123  "))
    }

    @Test
    fun ticketUrlsUseTheLastPathSegment() {
        assertEquals("tkABCDEFGH123", parse("https://yuyu.example/ticket/tkABCDEFGH123"))
        assertEquals("tkABCDEFGH123", parse("https://yuyu.example/ticket/tkABCDEFGH123?src=qr"))
        assertEquals("tkABCDEFGH123", parse("https://yuyu.example/ticket/tkABCDEFGH123/"))
        assertEquals("tkABCDEFGH123", parse("http://localhost:3000/ticket/tkABCDEFGH123#top"))
        assertEquals("tkABCDEFGH123", parse("HTTPS://YUYU.EXAMPLE/ticket/tkABCDEFGH123"))
    }

    @Test
    fun relativePathsAndBareHostsAreReadAsPaths() {
        assertEquals("tkABCDEFGH123", parse("/ticket/tkABCDEFGH123?src=qr"))
        assertEquals("tkABCDEFGH123", parse("ticket/tkABCDEFGH123"))
        assertEquals("tkABCDEFGH123", parse("yuyu.example/ticket/tkABCDEFGH123"))
    }

    @Test
    fun queryParametersApplyOnlyWhenTheLastSegmentIsShort() {
        assertEquals("abc", parse("https://yuyu.example/c?t=abc"))
        assertEquals("xyz", parse("https://yuyu.example/c?token=xyz"))
        assertEquals("qqq", parse("https://yuyu.example/c?q=qqq"))
        // t wins over token and q.
        assertEquals("first", parse("https://yuyu.example/c?q=third&token=second&t=first"))
        // Decoded and trimmed like URLSearchParams.get(...).trim().
        assertEquals("abc", parse("https://yuyu.example/c?t=%20abc%20"))
        assertEquals("a b", parse("https://yuyu.example/c?t=a+b"))
        // A long last segment wins over the query.
        assertEquals("tkABCDEFGH123", parse("https://yuyu.example/ticket/tkABCDEFGH123?t=other"))
    }

    @Test
    fun customSchemesParseLikeTheWhatwgUrl() {
        assertEquals("abcdefgh12", parse("yuyu://ticket/abcdefgh12"))
        assertEquals("abc", parse("yuyu://x/c?t=abc"))
        // Opaque URL: path "abc" is short, so the whole input is the fallback.
        assertEquals("tk:abc", parse("tk:abc"))
        assertEquals("ticketTOKEN123", parse("localhost:3000/ticket/ticketTOKEN123"))
    }

    @Test
    fun shortInputsFallBackToTheTextBeforeTheQuery() {
        assertEquals("abc", parse("abc"))
        assertEquals("abc", parse("abc?x=1"))
        assertEquals("https://yuyu.example/ticket/short", parse("https://yuyu.example/ticket/short"))
        // Empty t= is ignored, as `qp?.trim()` is falsy.
        assertEquals("https://yuyu.example/x", parse("https://yuyu.example/x?t="))
    }

    @Test
    fun pathSegmentsStayPercentEncodedLikeUrlPathname() {
        assertEquals("tk%20ABCDEFGH", parse("https://yuyu.example/ticket/tk%20ABCDEFGH"))
    }
}
