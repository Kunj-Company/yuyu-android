package com.twilighty.yuyu

import com.twilighty.yuyu.navigation.DeepLink
import com.twilighty.yuyu.navigation.DeepLinkParser
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepLinkParserTest {
    private fun https(path: String, query: String? = null): DeepLink? {
        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
        return DeepLinkParser.parse("https", "yuyu.example", segments, query, "https://yuyu.example$path")
    }

    @Test
    fun mapsAppDestinations() {
        assertEquals(DeepLink.Ticket("tkABCDEFGH123"), https("/ticket/tkABCDEFGH123"))
        assertEquals(DeepLink.Event("civic-tech", "spring-hack-night"), https("/civic-tech/spring-hack-night"))
        assertEquals(DeepLink.Feedback("civic-tech", "spring-hack-night"), https("/civic-tech/spring-hack-night/feedback"))
        assertEquals(DeepLink.ShortCode("aB3x9Kq"), https("/aB3x9Kq"))
        assertEquals(DeepLink.Discover("music"), https("/discover", "q=music"))
    }

    @Test
    fun websiteOnlyPagesOpenInTheBrowser() {
        listOf(
            "/join/abc123", "/dashboard/civic-tech", "/account", "/privacy", "/civic-tech", "/a/b/c/d",
            "/super-admin", "/super-admin-mfa", "/api/uploads/x", "/login", "/reset-password", "/verify-email", "/embed/x/y",
            "/civic-tech/hack/check-in", "/civic-tech/i/clinstance1", "/civic-tech/hack/schedule",
            "/civic-tech/hack/sessions/s1", "/civic-tech/hack/speakers/ada",
            // 7 characters but no uppercase letter: an organisation page, not a short code.
            "/abcdefg",
        ).forEach { path ->
            assertEquals(path, DeepLink.OpenInBrowser("https://yuyu.example$path"), https(path))
        }
    }

    @Test
    fun customSchemeTreatsHostAsFirstSegment() {
        assertEquals(DeepLink.Ticket("tkABCDEFGH123"), DeepLinkParser.parse("yuyu", "ticket", listOf("tkABCDEFGH123"), webUrl = null))
        assertEquals(DeepLink.Event("org", "event"), DeepLinkParser.parse("yuyu", "org", listOf("event"), webUrl = null))
        assertEquals(null, DeepLinkParser.parse("yuyu", "join", listOf("x"), webUrl = null))
        assertEquals(null, DeepLinkParser.parse("ftp", "x", emptyList(), webUrl = null))
    }

    @Test
    fun ticketTokensAreNotPrinted() {
        assertEquals("Ticket(██)", DeepLink.Ticket("tkSECRET").toString())
    }
}
