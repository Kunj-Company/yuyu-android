package com.twilighty.yuyu.core.network

import com.twilighty.yuyu.core.network.interceptor.RedactingLogger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactingLoggerTest {
    @Test
    fun scrubsTokensFromBodiesPathsAndHeaders() {
        val logged = RedactingLogger.redact(
            """{"accessToken":"yma_secret","refreshToken":"ymr_secret","ticketToken":"tk123","password":"hunter2","name":"Ada"}""",
        )
        listOf("yma_secret", "ymr_secret", "tk123", "hunter2").forEach { assertFalse(logged.contains(it)) }
        assertTrue(logged.contains("\"name\":\"Ada\""))
        assertFalse(RedactingLogger.redact("--> GET https://x.org/api/ticket/tkSECRET123/status").contains("tkSECRET123"))
        assertFalse(RedactingLogger.redact("Authorization: Bearer yma_abc.def").contains("yma_abc"))
    }
}
