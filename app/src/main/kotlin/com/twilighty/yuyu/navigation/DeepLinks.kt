package com.twilighty.yuyu.navigation

/** Where an incoming link should take the person. */
sealed interface DeepLink {
    data class Ticket(val token: String) : DeepLink {
        override fun toString() = "Ticket(██)"
    }
    data class Event(val orgSlug: String, val eventSlug: String) : DeepLink
    data class Feedback(val orgSlug: String, val eventSlug: String) : DeepLink
    data class ShortCode(val code: String) : DeepLink
    data class Discover(val query: String?) : DeepLink

    /** Website-only pages (e.g. `/join/…`, dashboard): hand over to the browser. */
    data class OpenInBrowser(val url: String) : DeepLink
}

/**
 * Maps `https://{host}/…` App Links and `yuyu://…` links to app destinations:
 * `/ticket/{token}`, `/{org}/{event}`, `/{org}/{event}/feedback`, short codes
 * (7 characters with an uppercase letter; resolved via `GET /public/short/:code`,
 * arriving through `yuyu://` or shared links — they are not App Link claimed)
 * and `/discover`. Website-only pages go back to the browser. Pure function so
 * it is unit-tested.
 */
object DeepLinkParser {
    private val SHORT_CODE = Regex("^(?=.*[A-Z])[A-Za-z0-9]{7}$")
    private val SLUG = Regex("^[A-Za-z0-9][A-Za-z0-9_-]*$")

    /** First path segments that are Yuyu website sections, never organisation slugs. */
    private val RESERVED = setOf(
        "about", "account", "actions", "api", "dashboard", "discover", "embed", "features", "join",
        "login", "privacy", "reset-password", "search", "terms", "ticket", "verify-email", "_next",
    )

    /** Event sub-pages the app doesn't have: `/{org}/{event}/<x>[/…]`. */
    private val WEB_ONLY_EVENT_SUBPAGES = setOf("check-in", "schedule", "sessions", "speakers")

    /**
     * @param scheme `https` or `yuyu`
     * @param host the link host (for `yuyu://ticket/x` the host is the first segment)
     * @param pathSegments decoded path segments
     * @param webUrl the https URL to fall back to in the browser (null for custom-scheme links)
     */
    fun parse(scheme: String?, host: String?, pathSegments: List<String>, query: String? = null, webUrl: String?): DeepLink? {
        val segments = when (scheme?.lowercase()) {
            "yuyu" -> listOfNotNull(host?.takeIf { it.isNotBlank() }) + pathSegments
            "https", "http" -> pathSegments
            else -> return null
        }.filter { it.isNotEmpty() }

        val fallback = webUrl?.let { DeepLink.OpenInBrowser(it) }
        if (segments.isEmpty()) return fallback
        val first = segments[0]
        val firstLower = first.lowercase()
        return when {
            firstLower == "ticket" && segments.size == 2 && segments[1].length >= 8 -> DeepLink.Ticket(segments[1])
            firstLower == "discover" && segments.size == 1 -> DeepLink.Discover(query?.let { parseQuery(it)["q"] })
            firstLower in RESERVED || firstLower.startsWith("super-admin") -> fallback
            segments.size == 1 && SHORT_CODE.matches(first) -> DeepLink.ShortCode(first)
            segments.size >= 2 && (segments[1] == "i" || segments.getOrNull(2) in WEB_ONLY_EVENT_SUBPAGES) -> fallback
            segments.size == 2 && SLUG.matches(first) && SLUG.matches(segments[1]) -> DeepLink.Event(first, segments[1])
            segments.size == 3 && segments[2] == "feedback" && SLUG.matches(first) && SLUG.matches(segments[1]) ->
                DeepLink.Feedback(first, segments[1])
            else -> fallback
        }
    }

    private fun parseQuery(query: String): Map<String, String> = query.split('&').mapNotNull { part ->
        val idx = part.indexOf('=')
        if (idx <= 0) null else part.substring(0, idx) to java.net.URLDecoder.decode(part.substring(idx + 1), "UTF-8")
    }.toMap()
}
