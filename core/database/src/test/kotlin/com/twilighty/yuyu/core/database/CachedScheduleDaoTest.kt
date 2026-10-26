package com.twilighty.yuyu.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twilighty.yuyu.core.database.entity.CachedScheduleEntity
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
class CachedScheduleDaoTest {
    private lateinit var db: YuyuDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), YuyuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun schedule(org: String, event: String, syncedAt: Long, json: String = "{}") = CachedScheduleEntity(
        orgSlug = org, eventId = event, eventTitle = "Event $event", timezone = "UTC", canEditDelays = false,
        scheduleJson = json, generatedAt = null, syncedAt = syncedAt,
    )

    @Test
    fun keepsOneCopyPerEventNewestFirst() = runTest {
        val dao = db.cachedScheduleDao()
        dao.upsert(schedule("org", "e1", syncedAt = 1))
        dao.upsert(schedule("org", "e2", syncedAt = 2))
        dao.upsert(schedule("org", "e1", syncedAt = 3, json = """{"tracks":[]}"""))
        assertEquals(listOf("e1", "e2"), dao.observeAll().first().map { it.eventId })
        assertEquals("""{"tracks":[]}""", dao.get("org", "e1")?.scheduleJson)
        // The same event id under another organisation slug is a different copy.
        assertNull(dao.get("other", "e1"))
        dao.delete("org", "e1")
        assertNull(dao.observe("org", "e1").first())
    }

    @Test
    fun clearedWithTheRestOfTheDatabase() = runTest {
        val dao = db.cachedScheduleDao()
        dao.upsert(schedule("org", "e1", syncedAt = 1))
        db.clearAllTables()
        assertEquals(emptyList<CachedScheduleEntity>(), dao.observeAll().first())
    }
}
