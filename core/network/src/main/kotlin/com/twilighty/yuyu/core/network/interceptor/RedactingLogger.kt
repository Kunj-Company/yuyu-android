package com.twilighty.yuyu.core.network.interceptor

import android.util.Log
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Debug-only HTTP logger. Header values for Authorization/Cookie are redacted
 * by the interceptor itself; this logger also scrubs secrets from JSON bodies
 * and ticket tokens from URL paths before anything reaches Logcat.
 * Never installed in release builds.
 */
class RedactingLogger(private val sink: (String) -> Unit = { Log.d("YuyuHttp", it) }) : HttpLoggingInterceptor.Logger {
    override fun log(message: String) = sink(redact(message))

    companion object {
        private val SECRET_KEYS = listOf(
            "accessToken", "refreshToken", "ticketToken", "idToken", "password", "totp",
            "pin", "token", "checkInQrToken", "certificateToken", "undoId",
        )
        private val JSON_SECRET = Regex(
            "\"(${SECRET_KEYS.joinToString("|")})\"\\s*:\\s*\"[^\"]*\"",
            RegexOption.IGNORE_CASE,
        )
        private val PATH_TOKEN = Regex("/(ticket|certificate)/[^/\\s?]+")
        private val BEARER = Regex("Bearer\\s+[A-Za-z0-9._~+/=-]+")

        fun redact(message: String): String = message
            .replace(JSON_SECRET) { "\"${it.groupValues[1]}\":\"██\"" }
            .replace(PATH_TOKEN) { "/${it.groupValues[1]}/██" }
            .replace(BEARER, "Bearer ██")

        fun interceptor(): HttpLoggingInterceptor = HttpLoggingInterceptor(RedactingLogger()).apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }
    }
}
