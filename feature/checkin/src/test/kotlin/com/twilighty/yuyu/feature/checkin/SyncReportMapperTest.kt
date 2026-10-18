package com.twilighty.yuyu.feature.checkin

import com.twilighty.yuyu.core.database.entity.PendingCheckInEntity
import com.twilighty.yuyu.core.network.model.SyncCheckInsResultDto
import com.twilighty.yuyu.core.network.model.SyncFailureDto
import com.twilighty.yuyu.feature.checkin.domain.SyncItem
import com.twilighty.yuyu.feature.checkin.domain.SyncOutcome
import com.twilighty.yuyu.feature.checkin.domain.SyncReportMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncReportMapperTest {
    private fun pending(id: String, at: Long) =
        PendingCheckInEntity(orgSlug = "org", eventId = "ev", rsvpId = id, checkedInAt = "2026-10-18T10:00:00Z", force = false, createdAt = at, clientMutationId = "cm-$id-0000")

    private val names = mapOf("r1" to "Ada", "r2" to "Grace", "r3" to "Linus", "r4" to "Katherine")

    @Test
    fun mapsSyncedDuplicatesAndFailuresInQueueOrder() {
        val sent = listOf(pending("r1", 1), pending("r2", 2), pending("r3", 3), pending("r4", 4))
        val result = SyncCheckInsResultDto(
            syncedIds = listOf("r1"),
            alreadyCheckedInIds = listOf("r2"),
            failed = listOf(SyncFailureDto("r3", "Check-in is closed for this event.")),
        )
        val report = SyncReportMapper.map(sent, result, names)
        assertEquals(
            listOf(
                SyncItem("r1", "Ada", SyncOutcome.Synced),
                SyncItem("r2", "Grace", SyncOutcome.AlreadyCheckedIn),
                SyncItem("r3", "Linus", SyncOutcome.Failed("Check-in is closed for this event.")),
                SyncItem("r4", "Katherine", SyncOutcome.StillQueued),
            ),
            report.items,
        )
        assertEquals("1 synced · 1 already checked in · 1 need attention · 1 still waiting", report.summary)
    }

    @Test
    fun removedIdsWinOverAStrayFailureAndDuplicatesCollapse() {
        val sent = listOf(pending("r1", 1), pending("r1", 2))
        val result = SyncCheckInsResultDto(syncedIds = listOf("r1"), failed = listOf(SyncFailureDto("r1", "x")))
        val report = SyncReportMapper.map(sent, result, emptyMap())
        assertEquals(listOf(SyncItem("r1", "Guest", SyncOutcome.Synced)), report.items)
    }

    @Test
    fun blankFailureReasonGetsAPlainDefault() {
        val report = SyncReportMapper.map(listOf(pending("r1", 1)), SyncCheckInsResultDto(failed = listOf(SyncFailureDto("r1", " "))), names)
        assertEquals(SyncOutcome.Failed("The server couldn't check this guest in."), report.items.single().outcome)
        assertEquals(1, report.failed)
    }

    @Test
    fun pendingItemsCarryNamesAndLastErrors() {
        val items = SyncReportMapper.pendingItems(listOf(pending("r1", 1).copy(attempts = 2, lastError = "Nope")), names)
        assertEquals("Ada", items.single().displayName)
        assertEquals("Nope", items.single().lastError)
        assertEquals(2, items.single().attempts)
    }
}
