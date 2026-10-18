package com.twilighty.yuyu.core.data.util

import java.net.URI
import java.net.URLDecoder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Port of the server's `parseCheckInPayload` (lib/checkIn.ts), used only to
 * match a scanned QR against the offline roster. Online check-ins always send
 * the raw input and let the server parse it.
 *
 * Like the WHATWG `URL` the server uses, the path is read percent-encoded and
 * non-http schemes (`yuyu://ticket/…`, `scheme:opaque`) are understood too, so
 * offline matching extracts exactly the token the server would.
 */
object CheckInPayload {
    private val SCHEME = Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE)

    fun extractToken(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        val withProto = if (SCHEME.containsMatchIn(t)) t else "https://placeholder.local" + (if (t.startsWith("/")) "" else "/") + t
        parseUrl(withProto)?.let { url ->
            val last = url.pathname.split("/").lastOrNull { it.isNotEmpty() }
            if (last != null && last.length >= 8) {
                val clean = last.substringBefore("?")
                if (clean.isNotEmpty()) return clean
            }
            val qp = url.query("t") ?: url.query("token") ?: url.query("q")
            if (!qp.isNullOrBlank()) return qp.trim()
        }
        val tail = t.split("/").lastOrNull { it.isNotEmpty() }?.substringBefore("?")
        if (tail != null && tail.length >= 8) return tail
        return t.substringBefore("?").trim()
    }

    private class ParsedUrl(val pathname: String, private val params: List<Pair<String, String>>) {
        fun query(name: String): String? = params.firstOrNull { it.first == name }?.second
    }

    private fun parseUrl(input: String): ParsedUrl? {
        val scheme = input.substringBefore(':').lowercase()
        if (scheme == "http" || scheme == "https") {
            val url = input.toHttpUrlOrNull() ?: return null
            val params = (0 until url.querySize).map { url.queryParameterName(it) to url.queryParameterValue(it).orEmpty() }
            return ParsedUrl(url.encodedPath, params)
        }
        val uri = runCatching { URI(input) }.getOrNull() ?: return null
        return if (uri.isOpaque) {
            val ssp = uri.rawSchemeSpecificPart.orEmpty()
            ParsedUrl(ssp.substringBefore('?'), parseQuery(if ('?' in ssp) ssp.substringAfter('?') else null))
        } else {
            ParsedUrl(uri.rawPath.orEmpty(), parseQuery(uri.rawQuery))
        }
    }

    private fun parseQuery(query: String?): List<Pair<String, String>> =
        query.orEmpty().split('&').filter { it.isNotEmpty() }.map { part ->
            val name = part.substringBefore('=')
            val value = if ('=' in part) part.substringAfter('=') else ""
            decode(name) to decode(value)
        }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
