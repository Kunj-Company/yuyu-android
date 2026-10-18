package com.twilighty.yuyu.feature.checkin

import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.crypto.sha256Hex
import com.twilighty.yuyu.core.data.repository.CheckInRepositoryImpl
import com.twilighty.yuyu.core.data.sync.CheckInSyncScheduler
import com.twilighty.yuyu.core.database.dao.PendingCheckInDao
import com.twilighty.yuyu.core.database.dao.RosterDao
import com.twilighty.yuyu.core.database.entity.PendingCheckInEntity
import com.twilighty.yuyu.core.database.entity.RosterRowEntity
import com.twilighty.yuyu.core.network.NetworkJson
import com.twilighty.yuyu.core.network.api.YuyuMobileApi
import com.twilighty.yuyu.feature.checkin.scanner.OrganiserOfflineBackend
import com.twilighty.yuyu.feature.checkin.flow.OfflineMatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * The offline roster keeps ticket tokens only as SHA-256: a scanned QR (any
 * format the server accepts) is hashed and looked up; the raw token is never
 * stored in any column.
 */
class OfflineRosterHashTest {
    private val server = MockWebServer()
    private val rosterDao = FakeRosterDao()
    private val pendingDao = FakePendingDao()
    private lateinit var repository: CheckInRepositoryImpl

    private val token = "tkSecretToken0123456789"

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(NetworkJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(YuyuMobileApi::class.java)
        repository = CheckInRepositoryImpl(api, rosterDao, pendingDao, CheckInSyncScheduler { error("WorkManager is not used here") })
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """
                {"ok":true,"data":{"generatedAt":"2026-10-18T09:00:00.000Z","rows":[
                  {"rsvpId":"r1","displayName":"Ada Lovelace","email":"ada@example.org","status":"CONFIRMED","checkedInAt":null,
                   "registeredAt":"2026-10-01T09:00:00.000Z","isGuest":false,"ticketToken":"$token",
                   "checkInDetails":[{"label":"T-shirt","value":"M"}],"registrationDetails":[]},
                  {"rsvpId":"r2","displayName":"Grace Hopper","email":null,"status":"WAITLISTED","checkedInAt":null,
                   "registeredAt":"2026-10-02T09:00:00.000Z","isGuest":true,"ticketToken":"tkOtherToken9876543210",
                   "checkInDetails":[],"registrationDetails":[]}
                ]}}
                """.trimIndent(),
            ).build(),
        )
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun rosterStoresOnlyHashesAndMatchesScannedCodes() = runTest {
        val info = repository.downloadRoster("civic-tech", "ev1")
        assertEquals(2, (info as AppResult.Success).data.count)

        val stored = rosterDao.rows.value
        assertEquals(sha256Hex(token), stored.first { it.rsvpId == "r1" }.ticketTokenSha256)
        assertEquals(64, stored.first().ticketTokenSha256!!.length)
        assertFalse("raw token must not be stored", stored.any { it.toString().contains(token) })

        // Every QR/typed form the server accepts resolves to the same guest.
        listOf(token, "https://yuyu.example/ticket/$token", "/ticket/$token?src=qr", "  $token ").forEach { input ->
            assertEquals(input, "r1", repository.findOfflineByCode("ev1", input)?.rsvpId)
        }
        assertNull(repository.findOfflineByCode("ev1", "tkUnknownToken00000000"))
        assertNull("rows are scoped to their event", repository.findOfflineByCode("ev2", token))
        assertEquals("M", repository.findOfflineByCode("ev1", token)!!.checkInDetails.single().value)
    }

    @Test
    fun offlineBackendGatesLocallyAndReportsMisses() = runTest {
        repository.downloadRoster("civic-tech", "ev1")
        val backend = OrganiserOfflineBackend(repository, "civic-tech", "ev1")
        val found = backend.matchCode("https://yuyu.example/ticket/tkOtherToken9876543210") as OfflineMatch.Found
        assertEquals("r2", found.guest.rsvpId)
        assertTrue(found.guest.gate.needsForce)
        assertTrue(found.guest.offline)
        assertEquals(OfflineMatch.NotInRoster, backend.matchCode("tkUnknownToken00000000"))
        assertEquals(OfflineMatch.NoRoster, OrganiserOfflineBackend(repository, "civic-tech", "ev-empty").matchCode(token))
    }
}

private class FakeRosterDao : RosterDao {
    val rows = MutableStateFlow<List<RosterRowEntity>>(emptyList())
    override fun observeRoster(eventId: String): Flow<List<RosterRowEntity>> = rows.map { r -> r.filter { it.eventId == eventId } }
    override suspend fun count(eventId: String) = rows.value.count { it.eventId == eventId }
    override suspend fun generatedAt(eventId: String) = rows.value.filter { it.eventId == eventId }.maxOfOrNull { it.generatedAt }
    override suspend fun findByTokenHash(eventId: String, tokenHash: String) =
        rows.value.firstOrNull { it.eventId == eventId && it.ticketTokenSha256 == tokenHash }
    override suspend fun find(eventId: String, rsvpId: String) = rows.value.firstOrNull { it.eventId == eventId && it.rsvpId == rsvpId }
    override suspend fun search(eventId: String, query: String, limit: Int) =
        rows.value.filter { it.eventId == eventId && it.displayName.contains(query, true) }.take(limit)
    override suspend fun markCheckedIn(eventId: String, rsvpId: String, checkedInAt: String?) {
        rows.value = rows.value.map { if (it.eventId == eventId && it.rsvpId == rsvpId) it.copy(checkedInAt = checkedInAt) else it }
    }
    override suspend fun insertAll(rows: List<RosterRowEntity>) {
        this.rows.value = this.rows.value + rows
    }
    override suspend fun deleteForEvent(eventId: String) {
        rows.value = rows.value.filterNot { it.eventId == eventId }
    }
    override suspend fun clear() {
        rows.value = emptyList()
    }
}

private class FakePendingDao : PendingCheckInDao {
    val rows = MutableStateFlow<List<PendingCheckInEntity>>(emptyList())
    override fun observeForEvent(eventId: String) = rows.map { r -> r.filter { it.eventId == eventId } }
    override fun observeTotalCount() = rows.map { it.size }
    override suspend fun all() = rows.value
    override suspend fun forEvent(eventId: String) = rows.value.filter { it.eventId == eventId }
    override suspend fun enqueue(checkIn: PendingCheckInEntity): Long {
        if (rows.value.any { it.eventId == checkIn.eventId && it.rsvpId == checkIn.rsvpId }) return -1
        rows.value = rows.value + checkIn
        return rows.value.size.toLong()
    }
    override suspend fun remove(eventId: String, rsvpIds: List<String>) {
        rows.value = rows.value.filterNot { it.eventId == eventId && it.rsvpId in rsvpIds }
    }
    override suspend fun markFailed(eventId: String, rsvpId: String, error: String?) {
        rows.value = rows.value.map { if (it.eventId == eventId && it.rsvpId == rsvpId) it.copy(attempts = it.attempts + 1, lastError = error) else it }
    }
    override suspend fun clear() {
        rows.value = emptyList()
    }
}
