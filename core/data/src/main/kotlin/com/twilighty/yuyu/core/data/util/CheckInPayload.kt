package com.twilighty.yuyu.core.data.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Port of the server's `parseCheckInPayload` (lib/checkIn.ts), used only to
 * match a scanned QR against the offline roster. Online check-ins always send
 * the raw input and let the server parse it.
 */
object CheckInPayload {
    fun extractToken(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        val withProto = if (Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            t
        } else {
            "https://placeholder.local" + (if (t.startsWith("/")) "" else "/") + t
        }
        withProto.toHttpUrlOrNull()?.let { url ->
            val last = url.pathSegments.lastOrNull { it.isNotEmpty() }
            if (last != null && last.length >= 8) return last
            val qp = url.queryParameter("t") ?: url.queryParameter("token") ?: url.queryParameter("q")
            if (!qp.isNullOrBlank()) return qp.trim()
        }
        val tail = t.split("/").lastOrNull { it.isNotEmpty() }?.substringBefore("?")
        if (tail != null && tail.length >= 8) return tail
        return t.substringBefore("?").trim()
    }
}
