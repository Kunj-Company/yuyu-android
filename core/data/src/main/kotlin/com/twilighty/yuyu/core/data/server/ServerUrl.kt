package com.twilighty.yuyu.core.data.server

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Normalises what a person types into a Yuyu server base URL. */
object ServerUrl {
    private val DEBUG_CLEARTEXT_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")

    sealed interface Result {
        data class Valid(val url: HttpUrl) : Result
        data class Invalid(val message: String) : Result
    }

    /**
     * Trims, adds `https://` when no scheme is given, drops query/fragment,
     * keeps a base path and guarantees a trailing `/`. `http` is accepted only
     * in debug builds and only for the emulator host / localhost.
     */
    fun normalize(input: String, allowDebugCleartext: Boolean): Result {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return Result.Invalid("Enter your Yuyu server's address.")
        if (trimmed.any { it.isWhitespace() }) return Result.Invalid("A web address can't contain spaces.")
        val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        val parsed = withScheme.toHttpUrlOrNull()
            ?: return Result.Invalid("That doesn't look like a web address. Try something like events.example.org.")
        if (parsed.host.isBlank() || (!parsed.host.contains('.') && !(allowDebugCleartext && parsed.host in DEBUG_CLEARTEXT_HOSTS))) {
            return Result.Invalid("That doesn't look like a web address. Try something like events.example.org.")
        }
        if (parsed.scheme == "http") {
            val allowed = allowDebugCleartext && parsed.host in DEBUG_CLEARTEXT_HOSTS
            if (!allowed) return Result.Invalid("Use a secure https:// address.")
        }
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return Result.Invalid("Remove the username and password from the address.")
        }
        val path = parsed.encodedPath.trimEnd('/') + "/"
        val url = parsed.newBuilder().encodedPath(path).query(null).fragment(null).build()
        return Result.Valid(url)
    }

    /** `events.example.org` or `events.example.org/yuyu` for display. */
    fun display(url: HttpUrl): String {
        val port = if (url.port != HttpUrl.defaultPort(url.scheme)) ":${url.port}" else ""
        return url.host + port + url.encodedPath.trimEnd('/')
    }
}
