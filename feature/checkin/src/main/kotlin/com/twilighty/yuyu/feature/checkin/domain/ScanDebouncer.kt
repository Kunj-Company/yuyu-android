package com.twilighty.yuyu.feature.checkin.domain

/**
 * Drops repeat reads of the same QR: the camera sees the same code many times
 * a second, and it is usually still in view when a result sheet closes. The
 * same code is ignored for [windowMillis] after it was last read or its result
 * was dismissed; a different code is accepted at once.
 */
class ScanDebouncer(private val windowMillis: Long = 3_000) {
    private var lastCode: String? = null
    private var lastAt: Long = Long.MIN_VALUE

    @Synchronized
    fun accept(code: String, nowMillis: Long): Boolean {
        if (code == lastCode && nowMillis - lastAt < windowMillis) return false
        lastCode = code
        lastAt = nowMillis
        return true
    }

    /** Restarts the window, e.g. when the result for the last code is dismissed. */
    @Synchronized
    fun restart(nowMillis: Long) {
        if (lastCode != null) lastAt = nowMillis
    }

    override fun toString(): String = "ScanDebouncer(window=$windowMillis)"
}
