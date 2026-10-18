package com.twilighty.yuyu.feature.broadcasts.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkValidationTest {
    @Test
    fun acceptsAbsoluteHttpAndHttpsUrls() {
        listOf(
            "https://example.com",
            "http://example.com/path?q=1&r=2#frag",
            "HTTPS://Example.com/x",
            "https://sub.example.co.uk:8443/a/b",
        ).forEach { assertTrue(it, LinkValidation.isValid(it)) }
        assertEquals("https://example.com", LinkValidation.normalise("  https://example.com  "))
    }

    @Test
    fun acceptsLinkPlaceholders() {
        LinkValidation.URL_PLACEHOLDERS.forEach { assertTrue(it, LinkValidation.isValid(it)) }
    }

    @Test
    fun rejectsEverythingElse() {
        listOf(
            "", " ", "example.com", "www.example.com", "/relative", "//example.com", "ftp://example.com",
            "javascript:alert(1)", "JAVASCRIPT:alert(1)", "data:text/html,hi", "mailto:a@b.c", "file:///etc/passwd",
            "https://", "http:///nohost", "https://exa mple.com", "https://example.com/\"onmouseover", "{{name}}", "{{ ticketLink }}",
            "https://example.com/<script>",
        ).forEach { assertFalse(it, LinkValidation.isValid(it)) }
        assertNull(LinkValidation.normalise("javascript:alert(1)"))
        assertEquals(LinkValidation.ERROR, LinkValidation.error("nope"))
    }
}
