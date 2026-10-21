package com.twilighty.yuyu.core.data

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.CheckInRepositoryImpl
import com.twilighty.yuyu.core.data.sync.CheckInSyncScheduler
import com.twilighty.yuyu.core.database.dao.PendingCheckInDao
import com.twilighty.yuyu.core.database.dao.RosterDao
import com.twilighty.yuyu.core.database.entity.PendingCheckInEntity
import com.twilighty.yuyu.core.network.api.YuyuMobileApi
import com.twilighty.yuyu.core.network.model.ApiEnvelope
import com.twilighty.yuyu.core.network.model.SyncCheckInsRequest
import com.twilighty.yuyu.core.network.model.SyncCheckInsResultDto
import com.twilighty.yuyu.core.network.model.SyncFailureDto
import java.lang.reflect.Proxy
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server accepts 1–500 check-ins per `POST …/check-in/sync`; the queue must go up in batches. */
class CheckInSyncBatchingTest {
    private class InMemoryPendingDao(rows: List<PendingCheckInEntity>) : PendingCheckInDao {
        val rows = rows.toMutableList()
        override fun observeForEvent(eventId: String): Flow<List<PendingCheckInEntity>> = flowOf(rows.filter { it.eventId == eventId })
        override fun observeTotalCount(): Flow<Int> = flowOf(rows.size)
        override suspend fun all() = rows.sortedBy { it.createdAt }
        override suspend fun forEvent(eventId: String) = rows.filter { it.eventId == eventId }.sortedBy { it.createdAt }
        override suspend fun enqueue(checkIn: PendingCheckInEntity): Long {
            rows += checkIn
            return rows.size.toLong()
        }
        override suspend fun remove(eventId: String, rsvpIds: List<String>) {
            rows.removeAll { it.eventId == eventId && it.rsvpId in rsvpIds }
        }
        override suspend fun markFailed(eventId: String, rsvpId: String, error: String?) {
            val i = rows.indexOfFirst { it.eventId == eventId && it.rsvpId == rsvpId }
            if (i >= 0) rows[i] = rows[i].copy(attempts = rows[i].attempts + 1, lastError = error)
        }
        override suspend fun clear() = rows.clear()
    }

    /** Fake server: rsvp ids divisible by 7 fail, by 5 are already checked in, the rest sync. */
    private class FakeServer {
        val requests = mutableListOf<SyncCheckInsRequest>()
        var failOnRequest: Int? = null

        val api: YuyuMobileApi = Proxy.newProxyInstance(
            YuyuMobileApi::class.java.classLoader,
            arrayOf(YuyuMobileApi::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "syncCheckIns" -> {
                    val request = args!![2] as SyncCheckInsRequest
                    requests += request
                    if (requests.size == failOnRequest) {
                        ApiEnvelope<SyncCheckInsResultDto>(
                            ok = false,
                            error = buildJsonObject {
                                put("code", "INTERNAL_ERROR")
                                put("message", "Something broke.")
                            },
                        )
                    } else {
                        val ids = request.checkIns.map { it.rsvpId }
                        val n = { id: String -> id.removePrefix("r").toInt() }
                        ApiEnvelope(
                            ok = true,
                            data = SyncCheckInsResultDto(
                                syncedIds = ids.filter { n(it) % 7 != 0 && n(it) % 5 != 0 },
                                alreadyCheckedInIds = ids.filter { n(it) % 7 != 0 && n(it) % 5 == 0 },
                                failed = ids.filter { n(it) % 7 == 0 }.map { SyncFailureDto(it, "Not eligible") },
                            ),
                        )
                    }
                }
                "toString" -> "FakeYuyuMobileApi"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> error("Unexpected call: ${method.name}")
            }
        } as YuyuMobileApi
    }

    private val unusedRosterDao = Proxy.newProxyInstance(
        RosterDao::class.java.classLoader,
        arrayOf(RosterDao::class.java),
    ) { _, method, _ -> error("Unexpected roster call: ${method.name}") } as RosterDao

    private val scheduler = CheckInSyncScheduler(Provider { error("WorkManager is not used by sync") })

    private fun queue(count: Int, eventId: String = "ev1") = (1..count).map { i ->
        PendingCheckInEntity(
            orgSlug = "org",
            eventId = eventId,
            rsvpId = "r$i",
            checkedInAt = "2026-10-21T10:00:00Z",
            force = false,
            createdAt = i.toLong(),
            clientMutationId = "mutation-$i",
        )
    }

    @Test
    fun queueOverFiveHundredIsSentInBatchesAndEveryResultIsApplied() = runTest {
        val total = 1203
        val dao = InMemoryPendingDao(queue(total) + queue(3, eventId = "other"))
        val server = FakeServer()
        val repository = CheckInRepositoryImpl(server.api, unusedRosterDao, dao, scheduler)

        val result = repository.syncPending("org", "ev1")

        assertEquals(listOf(500, 500, 203), server.requests.map { it.checkIns.size })
        val sent = server.requests.flatMap { r -> r.checkIns.map { it.rsvpId } }
        assertEquals((1..total).map { "r$it" }, sent) // in queue order, each once
        assertEquals((1..total).map { "mutation-$it" }, server.requests.flatMap { r -> r.checkIns.map { it.clientMutationId } })

        val data = (result as AppResult.Success).data
        val failedIds = (1..total).filter { it % 7 == 0 }.map { "r$it" }
        assertEquals(failedIds, data.failed.map { it.rsvpId })
        assertEquals((1..total).filter { it % 7 != 0 && it % 5 == 0 }.map { "r$it" }, data.alreadyCheckedInIds)
        assertEquals(total - failedIds.size - data.alreadyCheckedInIds.size, data.syncedIds.size)

        // Only the failed rows remain for this event, each marked once; the other event is untouched.
        val left = dao.forEvent("ev1")
        assertEquals(failedIds, left.map { it.rsvpId })
        assertTrue(left.all { it.attempts == 1 && it.lastError == "Not eligible" })
        assertEquals(3, dao.forEvent("other").size)
    }

    @Test
    fun failedBatchStopsTheSyncAndKeepsTheRestQueued() = runTest {
        val dao = InMemoryPendingDao(queue(1203))
        val server = FakeServer().apply { failOnRequest = 2 }
        val repository = CheckInRepositoryImpl(server.api, unusedRosterDao, dao, scheduler)

        val result = repository.syncPending("org", "ev1")

        assertTrue((result as AppResult.Failure).error is AppError.Server)
        assertEquals(2, server.requests.size) // no third batch after the error
        // The first batch was applied; batches two and three are still queued, untouched.
        val firstBatchFailed = (1..500).filter { it % 7 == 0 }.map { "r$it" }
        val left = dao.forEvent("ev1")
        assertEquals(firstBatchFailed + (501..1203).map { "r$it" }, left.map { it.rsvpId })
        assertTrue(left.filter { it.rsvpId.removePrefix("r").toInt() > 500 }.all { it.attempts == 0 && it.lastError == null })

        // The retry sends only what is left, still in batches of at most 500.
        server.failOnRequest = null
        server.requests.clear()
        val retry = repository.syncPending("org", "ev1")
        assertTrue(retry is AppResult.Success)
        assertEquals(listOf(500, left.size - 500), server.requests.map { it.checkIns.size })
        assertEquals(left.map { it.rsvpId }, server.requests.flatMap { r -> r.checkIns.map { it.rsvpId } })
    }
}
