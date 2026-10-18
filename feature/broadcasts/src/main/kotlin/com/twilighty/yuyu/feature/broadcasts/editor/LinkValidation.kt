package com.twilighty.yuyu.feature.broadcasts.editor

import java.net.URI

/** Link and button targets: absolute http(s) URLs, or a link placeholder the server fills per recipient. */
object LinkValidation {
    /** Placeholders the server accepts as a link destination (`BROADCAST_URL_PLACEHOLDERS`). */
    val URL_PLACEHOLDERS = listOf("{{ticketLink}}", "{{eventUrl}}", "{{feedbackUrl}}", "{{scheduleUrl}}")

    const val ERROR = "Enter a full web address starting with https:// or http://, or a link placeholder."

    /** Returns the trimmed, accepted target, or null when [input] is not a safe link. */
    fun normalise(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        if (value in URL_PLACEHOLDERS) return value
        if (value.any { it.isWhitespace() || it.code < 0x20 || it == '"' || it == '<' || it == '>' }) return null
        if (!value.startsWith("http://", ignoreCase = true) && !value.startsWith("https://", ignoreCase = true)) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank() && uri.rawAuthority.isNullOrBlank()) return null
        return value
    }

    fun isValid(input: String): Boolean = normalise(input) != null

    /** Error text for the link dialog, or null when valid. */
    fun error(input: String): String? = if (isValid(input)) null else ERROR
}
