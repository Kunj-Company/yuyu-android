package com.twilighty.yuyu.feature.organiser

import com.twilighty.yuyu.core.network.model.OrgScheduleDto
import com.twilighty.yuyu.core.network.model.OrgSessionDto
import com.twilighty.yuyu.core.network.model.OrgTrackDto
import com.twilighty.yuyu.feature.organiser.schedule.LiveStatus
import com.twilighty.yuyu.feature.organiser.schedule.formatCountdown
import com.twilighty.yuyu.feature.organiser.schedule.label
import com.twilighty.yuyu.feature.organiser.schedule.liveStatus
import com.twilighty.yuyu.feature.organiser.schedule.nowAndNext
import com.twilighty.yuyu.feature.organiser.schedule.toTrackUi
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleModelTest {
    private val tracks = OrgScheduleDto(
        listOf(
            OrgTrackDto(
                "t1",
                "Main stage",
                listOf(
                    OrgSessionDto("s1", "Welcome", "2030-05-10T10:00:00Z", "2030-05-10T10:30:00Z", delayMinutes = 10),
                    OrgSessionDto("s2", "Talks", "2030-05-10T10:30:00Z", "2030-05-10T11:30:00Z"),
                ),
            ),
            OrgTrackDto("t2", "Workshops", listOf(OrgSessionDto("w1", "Workshop", "2030-05-10T12:00:00Z", "2030-05-10T13:00:00Z"))),
        ),
    ).toTrackUi()

    private fun at(time: String) = Instant.parse("2030-05-10T${time}Z")

    @Test
    fun liveTimesFollowEarlierDelaysInTheTrack() {
        val talks = tracks[0].sessions[1]
        assertEquals(at("10:40:00"), talks.liveStart)
        assertEquals(at("11:40:00"), talks.liveEnd)
    }

    @Test
    fun statusCountsDownToTheLiveEnd() {
        val talks = tracks[0].sessions[1]
        assertEquals(LiveStatus.Upcoming(Duration.ofMinutes(5)), talks.liveStatus(at("10:35:00")))
        val live = talks.liveStatus(at("11:10:00")) as LiveStatus.Live
        assertEquals(Duration.ofMinutes(30), live.remaining)
        assertEquals(0.5f, live.progress, 0.001f)
        assertEquals("30 min left", live.label())
        assertEquals(LiveStatus.Ended, talks.liveStatus(at("11:40:00")))
    }

    @Test
    fun sessionWithoutAnEndRunsForHalfAnHour() {
        val open = OrgScheduleDto(listOf(OrgTrackDto("t", "T", listOf(OrgSessionDto("x", "Open", "2030-05-10T10:00:00Z"))))).toTrackUi()[0].sessions[0]
        assertTrue(open.liveStatus(at("10:29:00")) is LiveStatus.Live)
        assertEquals(LiveStatus.Ended, open.liveStatus(at("10:30:00")))
    }

    @Test
    fun countdownFormats() {
        assertEquals("45 s", formatCountdown(Duration.ofSeconds(45)))
        assertEquals("9:05", formatCountdown(Duration.ofSeconds(545)))
        assertEquals("25 min", formatCountdown(Duration.ofMinutes(25)))
        assertEquals("1 h 5 min", formatCountdown(Duration.ofMinutes(65)))
        assertEquals("2 h", formatCountdown(Duration.ofHours(2)))
        assertEquals("3 days", formatCountdown(Duration.ofDays(3)))
        assertEquals("0 s", formatCountdown(Duration.ofSeconds(-5)))
    }

    @Test
    fun nowAndNextAcrossTracks() {
        val summary = tracks.nowAndNext(at("10:15:00"))
        assertEquals(listOf("s1"), summary.now.map { it.item.session.id })
        // The next session in each track, soonest first.
        assertEquals(listOf("s2", "w1"), summary.next.map { it.item.session.id })
        assertEquals("Main stage", summary.next[0].trackName)
        val later = tracks.nowAndNext(at("12:30:00"))
        assertEquals(listOf("w1"), later.now.map { it.item.session.id })
        assertTrue(later.next.isEmpty())
    }
}
