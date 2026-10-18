package com.twilighty.yuyu.feature.checkin.domain

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface StationLink {
    data class Valid(val orgSlug: String, val eventSlug: String) : StationLink
    data class Invalid(val message: String) : StationLink
}

/**
 * Reads what a volunteer typed or scanned to open a door station:
 * the station URL (`https://host/{org}/{event}/check-in`), the event URL
 * (`https://host/{org}/{event}`), or just `{org}/{event}`. The station API is
 * always called on the connected server, so a link to another host is refused
 * with a clear message instead of failing later.
 */
object StationLinkParser {
    private val SLUG = Regex("^[A-Za-z0-9][A-Za-z0-9_-]*$")
    private val SCHEME = Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE)

    /** Website sections that are never organisation slugs (as in the app's deep-link parser). */
    private val RESERVED = setOf(
        "about", "account", "actions", "api", "dashboard", "discover", "embed", "features", "join",
        "login", "privacy", "reset-password", "search", "terms", "ticket", "verify-email", "_next",
    )

    fun parse(input: String, server: HttpUrl?): StationLink {
        val text = input.trim()
        if (text.isEmpty()) return StationLink.Invalid("Enter the station link, or the organisation and event.")
        val segments: List<String>
        if (SCHEME.containsMatchIn(text) || looksLikeHost(text)) {
            val url = (if (SCHEME.containsMatchIn(text)) text else "https://$text").toHttpUrlOrNull()
                ?: return StationLink.Invalid(NOT_A_STATION)
            if (server != null && !url.host.equals(server.host, ignoreCase = true)) {
                return StationLink.Invalid(
                    "This station is on another Yuyu server (${url.host}). You're connected to ${server.host}.",
                )
            }
            val base = server?.pathSegments?.filter { it.isNotEmpty() }.orEmpty()
            val path = url.pathSegments.filter { it.isNotEmpty() }
            segments = if (base.isNotEmpty() && path.take(base.size) == base) path.drop(base.size) else path
        } else {
            segments = text.substringBefore('?').split('/').map { it.trim() }.filter { it.isNotEmpty() }
        }
        val slugs = when {
            segments.size == 2 -> segments
            segments.size == 3 && segments[2] == "check-in" -> segments.take(2)
            else -> return StationLink.Invalid(NOT_A_STATION)
        }
        if (!slugs.all { SLUG.matches(it) } || slugs[0].lowercase() in RESERVED) return StationLink.Invalid(NOT_A_STATION)
        return StationLink.Valid(slugs[0], slugs[1])
    }

    private fun looksLikeHost(text: String): Boolean {
        val first = text.substringBefore('/')
        return '.' in first || first.startsWith("localhost") || ':' in first
    }

    private const val NOT_A_STATION = "That isn't a check-in station link. It looks like https://your-server/organisation/event/check-in."
}
