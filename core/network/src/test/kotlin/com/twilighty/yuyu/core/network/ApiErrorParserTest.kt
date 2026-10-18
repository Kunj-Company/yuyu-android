package com.twilighty.yuyu.core.network

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.network.error.ApiErrorParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorParserTest {
    private fun envelope(code: String, message: String = "msg") =
        """{"ok":false,"error":{"code":"$code","message":"$message"}}"""

    @Test
    fun mapsEveryContractCode() {
        assertTrue(ApiErrorParser.parse(401, envelope("UNAUTHENTICATED")) is AppError.Unauthenticated)
        assertTrue(ApiErrorParser.parse(401, envelope("MFA_REQUIRED")) is AppError.MfaRequired)
        assertTrue(ApiErrorParser.parse(403, envelope("EMAIL_VERIFICATION_REQUIRED")) is AppError.EmailVerificationRequired)
        assertTrue(ApiErrorParser.parse(403, envelope("FORBIDDEN")) is AppError.Forbidden)
        assertTrue(ApiErrorParser.parse(404, envelope("NOT_FOUND")) is AppError.NotFound)
        assertTrue(ApiErrorParser.parse(409, envelope("CONFLICT")) is AppError.Conflict)
        assertTrue(ApiErrorParser.parse(409, envelope("NEEDS_FORCE")) is AppError.NeedsForce)
        assertTrue(ApiErrorParser.parse(500, envelope("INTERNAL_ERROR")) is AppError.Server)
        assertTrue(ApiErrorParser.parse(400, envelope("INVALID_REQUEST")) is AppError.InvalidRequest)
    }

    @Test
    fun keepsServerMessageAndRetryAfter() {
        val error = ApiErrorParser.parse(429, envelope("RATE_LIMITED", "Slow down."), retryAfter = "60")
        assertEquals(AppError.RateLimited("Slow down.", 60), error)
    }

    @Test
    fun understandsWebStringErrorsAndNeedsForce() {
        val error = ApiErrorParser.parse(200, """{"ok":false,"error":"Guest is on the waitlist.","needsForce":true}""")
        assertEquals(AppError.NeedsForce("Guest is on the waitlist."), error)
        val station = ApiErrorParser.parse(401, """{"ok":false,"error":"Station access has expired."}""")
        assertEquals(AppError.Unauthenticated("Station access has expired."), station)
    }

    @Test
    fun fallsBackToStatusForEmptyOrHtmlBodies() {
        assertTrue(ApiErrorParser.parse(404, null) is AppError.NotFound)
        assertTrue(ApiErrorParser.parse(502, "<html>Bad gateway</html>") is AppError.Server)
        assertTrue(ApiErrorParser.parse(418, "") is AppError.Unknown)
    }
}
