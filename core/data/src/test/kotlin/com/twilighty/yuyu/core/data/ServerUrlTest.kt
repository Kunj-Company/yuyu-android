package com.twilighty.yuyu.core.data

import com.twilighty.yuyu.core.data.server.ServerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {
    private fun valid(input: String, debug: Boolean = false) =
        (ServerUrl.normalize(input, debug) as ServerUrl.Result.Valid).url.toString()

    @Test
    fun addsHttpsAndTrailingSlash() {
        assertEquals("https://events.example.org/", valid(" events.example.org "))
        assertEquals("https://events.example.org/", valid("https://Events.Example.org/"))
        assertEquals("https://example.org/yuyu/", valid("example.org/yuyu?x=1#top"))
    }

    @Test
    fun cleartextOnlyForEmulatorHostsInDebug() {
        assertEquals("http://10.0.2.2:3000/", valid("http://10.0.2.2:3000", debug = true))
        assertEquals("http://localhost:3000/", valid("http://localhost:3000", debug = true))
        assertTrue(ServerUrl.normalize("http://10.0.2.2:3000", allowDebugCleartext = false) is ServerUrl.Result.Invalid)
        assertTrue(ServerUrl.normalize("http://events.example.org", allowDebugCleartext = true) is ServerUrl.Result.Invalid)
    }

    @Test
    fun rejectsJunk() {
        listOf("", "   ", "not a url", "ftp://x.org", "https://user:pass@x.org", "localhost").forEach {
            assertTrue(it, ServerUrl.normalize(it, allowDebugCleartext = false) is ServerUrl.Result.Invalid)
        }
    }
}
