package com.twilighty.yuyu.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twilighty.yuyu.core.database.entity.PendingCheckInEntity
import com.twilighty.yuyu.core.database.entity.RosterRowEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineCheckInDaoTest {
    private lateinit var db: YuyuDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), YuyuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun row(rsvpId: String, name: String, hash: String?) = RosterRowEntity(
        eventId = "e1", rsvpId = rsvpId, orgSlug = "org", displayName = name, email = "$rsvpId@example.test",
        status = "CONFIRMED", checkedInAt = null, registeredAt = "2030-01-01T00:00:00Z", isGuest = false,
        ticketTokenSha256 = hash, checkInDetailsJson = "[]", registrationDetailsJson = "[]", generatedAt = "2030-01-02T00:00:00Z",
    )

    @Test
    fun rosterReplaceSearchAndTokenLookup() = runTest {
        val dao = db.rosterDao()
        dao.replaceRoster("e1", listOf(row("r1", "Ada", "h1"), row("r2", "Grace", "h2")))
        assertEquals("r2", dao.findByTokenHash("e1", "h2")?.rsvpId)
        assertNull(dao.findByTokenHash("e2", "h2"))
        assertEquals(listOf("Grace"), dao.search("e1", "gra").map { it.displayName })
        dao.markCheckedIn("e1", "r1", "2030-01-03T10:00:00Z")
        assertEquals("2030-01-03T10:00:00Z", dao.find("e1", "r1")?.checkedInAt)
        dao.replaceRoster("e1", listOf(row("r3", "Linus", null)))
        assertEquals(1, dao.count("e1"))
    }

    @Test
    fun pendingQueueKeepsFirstScanAndRemovesSynced() = runTest {
        val dao = db.pendingCheckInDao()
        fun pending(rsvp: String, at: String) = PendingCheckInEntity(
            orgSlug = "org", eventId = "e1", rsvpId = rsvp, checkedInAt = at, force = false,
            createdAt = 1, clientMutationId = "mutation-$rsvp-$at",
        )
        dao.enqueue(pending("r1", "10:00"))
        dao.enqueue(pending("r1", "10:05"))
        dao.enqueue(pending("r2", "10:06"))
        assertEquals(listOf("10:00", "10:06"), dao.forEvent("e1").map { it.checkedInAt })
        dao.markFailed("e1", "r2", "Not on the guest list")
        assertEquals("Not on the guest list", dao.forEvent("e1").first { it.rsvpId == "r2" }.lastError)
        dao.remove("e1", listOf("r1"))
        assertEquals(1, dao.observeTotalCount().first())
    }
}
