package com.twilighty.yuyu.core.common

import com.twilighty.yuyu.core.common.time.EventTimeFormatter
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class EventTimeFormatterTest {
    private val start = Instant.parse("2026-10-14T04:00:00Z") // 9:30 AM IST
    private val end = Instant.parse("2026-10-14T12:00:00Z") // 5:30 PM IST

    @Test
    fun formatsInTheEventTimezone() {
        assertEquals("9:30 AM", EventTimeFormatter.time(start, "Asia/Kolkata"))
        assertEquals("Wed 14 Oct", EventTimeFormatter.dayShort(start, "Asia/Kolkata"))
        assertEquals("Wednesday, 14 October", EventTimeFormatter.dayLong(start, "Asia/Kolkata"))
        assertEquals(EventTimeFormatter.DateBlock("OCT", "14"), EventTimeFormatter.dateBlock(start, "Asia/Kolkata"))
        assertEquals("9:30 AM – 5:30 PM IST", EventTimeFormatter.timeRange(start, end, "Asia/Kolkata"))
    }

    @Test
    fun dateBlockFollowsEventZoneAcrossMidnight() {
        val lateUtc = Instant.parse("2026-10-14T20:00:00Z")
        assertEquals("15", EventTimeFormatter.dateBlock(lateUtc, "Asia/Kolkata").day)
        assertEquals("14", EventTimeFormatter.dateBlock(lateUtc, "Europe/London").day)
    }

    @Test
    fun relativeDayUsesCalendarDays() {
        val now = Instant.parse("2026-10-13T10:00:00Z")
        assertEquals("Tomorrow", EventTimeFormatter.relativeDay(start, "Asia/Kolkata", now))
        assertEquals("Today", EventTimeFormatter.relativeDay(now, "Asia/Kolkata", now))
    }

    @Test
    fun unknownZoneFallsBackToUtc() {
        assertEquals("4:00 AM", EventTimeFormatter.time(start, "Not/AZone"))
    }
}
