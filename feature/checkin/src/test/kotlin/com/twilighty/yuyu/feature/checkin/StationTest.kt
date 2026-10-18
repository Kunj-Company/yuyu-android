package com.twilighty.yuyu.feature.checkin

import androidx.lifecycle.SavedStateHandle
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.StationKey
import com.twilighty.yuyu.core.data.repository.StationRepositoryImpl
import com.twilighty.yuyu.core.network.auth.ServerUrlProvider
import com.twilighty.yuyu.core.network.interceptor.BaseUrlInterceptor
import com.twilighty.yuyu.core.network.station.StationClientFactory
import com.twilighty.yuyu.feature.checkin.flow.SheetState
import com.twilighty.yuyu.feature.checkin.station.StationCheckInBackend
import com.twilighty.yuyu.feature.checkin.station.StationErrors
import com.twilighty.yuyu.feature.checkin.station.StationSessions
import com.twilighty.yuyu.feature.checkin.station.StationUnlockViewModel
import com.twilighty.yuyu.feature.checkin.station.StationViewModel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Door station over `POST /api/check-in/station` against a MockWebServer. */
@OptIn(ExperimentalCoroutinesApi::class)
class StationTest {
    private val server = MockWebServer()
    private lateinit var factory: StationClientFactory
    private val key = StationKey("civic-tech", "hack-night")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.start()
        val base: HttpUrl = server.url("/")
        val provider = object : ServerUrlProvider {
            override fun currentServer() = base
        }
        val bare = OkHttpClient.Builder().addInterceptor(BaseUrlInterceptor(provider)).readTimeout(5, TimeUnit.SECONDS).build()
        factory = StationClientFactory(bare)
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    private fun json(code: Int, body: String, vararg headers: Pair<String, String>) =
        MockResponse.Builder().code(code).body(body).apply {
            addHeader("Content-Type", "application/json")
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build()

    private val unlocked = json(200, """{"ok":true}""", "Set-Cookie" to "yuyu_checkin_station=proof123; Path=/; HttpOnly; Max-Age=3600")
    private val bootstrap = json(
        200,
        """{"ok":true,"data":{"organisationName":"Civic Tech","organisationLogoUrl":null,"eventId":"ev1","eventTitle":"Hack Night",
           "registrationFields":[],"stats":{"confirmed":40,"checkedIn":12},
           "recent":[{"rsvpId":"r9","displayName":"Grace","email":null,"checkedInAt":"2026-10-18T10:00:00.000Z"}]}}""",
    )

    @Test
    fun wrongPinShowsTheServerMessage() = runTest {
        server.enqueue(json(401, """{"ok":false,"error":"Unable to open this check-in station."}"""))
        val repo = StationRepositoryImpl(factory)
        val result = repo.unlock(key, "12345678")
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Unauthenticated)
        assertEquals("Unable to open this check-in station.", StationErrors.forUnlock(error))
        assertFalse(repo.isUnlocked)
        val request = server.takeRequest()
        assertEquals("/api/check-in/station", request.url.encodedPath)
        assertTrue(request.body!!.utf8().contains("\"action\":\"unlock\""))
        assertNull("no bearer is ever sent", request.headers["Authorization"])
    }

    @Test
    fun tooManyAttemptsIsRateLimited() = runTest {
        server.enqueue(json(429, """{"ok":false,"error":"Too many attempts. Please try again shortly."}""", "Retry-After" to "30"))
        val error = (StationRepositoryImpl(factory).unlock(key, "12345678") as AppResult.Failure).error
        assertTrue(error is AppError.RateLimited)
        assertEquals(30L, (error as AppError.RateLimited).retryAfterSeconds)
        assertEquals("Too many attempts. Please try again shortly.", StationErrors.forUnlock(error))
    }

    @Test
    fun closedStationAndOfflineMessages() {
        assertEquals("This check-in station has closed.", StationErrors.forUnlock(AppError.Unauthenticated("This check-in station has closed.")))
        assertEquals("Unable to open this check-in station.", StationErrors.forUnlock(AppError.Unauthenticated()))
        assertEquals("Station access is unavailable.", StationErrors.forUnlock(AppError.InvalidRequest("Station access is unavailable.")))
        assertTrue(StationErrors.forUnlock(AppError.Network()).contains("offline"))
        assertTrue(StationErrors.isAccessLost(AppError.Unauthenticated("Station access has expired.")))
        assertFalse(StationErrors.isAccessLost(AppError.RateLimited()))
    }

    @Test
    fun unlockKeepsTheProofCookieForLaterCalls() = runTest {
        server.enqueue(unlocked)
        server.enqueue(bootstrap)
        val repo = StationRepositoryImpl(factory)
        assertTrue(repo.unlock(key, "12345678") is AppResult.Success)
        assertTrue(repo.isUnlocked)
        val data = (repo.bootstrap(key) as AppResult.Success).data
        assertEquals("Hack Night", data.eventTitle)
        assertEquals(12, data.stats.checkedIn)
        server.takeRequest()
        assertEquals("yuyu_checkin_station=proof123", server.takeRequest().headers["Cookie"])
        repo.lock()
        assertFalse(repo.isUnlocked)
    }

    @Test
    fun gateFailuresMapToOverrideOrBlocked() = runTest {
        val backend = StationCheckInBackend(StationRepositoryImpl(factory), key)
        server.enqueue(json(200, """{"ok":false,"error":"Guest is on the waitlist — use “Override” to check in at the door.","needsForce":true}"""))
        val needsForce = (backend.checkIn("r1", null, force = false) as AppResult.Failure).error
        assertTrue(needsForce is AppError.NeedsForce)
        assertTrue(needsForce.message.startsWith("Guest is on the waitlist"))

        server.enqueue(json(200, """{"ok":false,"error":"This registration was rejected.","needsForce":false}"""))
        val blocked = (backend.checkIn("r1", null, force = false) as AppResult.Failure).error
        assertFalse(blocked is AppError.NeedsForce)
        assertEquals("This registration was rejected.", blocked.message)

        server.enqueue(json(401, """{"ok":false,"error":"Station access has expired."}"""))
        val expired = (backend.previewCode("tkABCDEFGH123") as AppResult.Failure).error
        assertTrue(StationErrors.isAccessLost(expired))
        assertEquals("Station access has expired.", expired.message)
    }

    /** Responses resume on OkHttp's thread, so wait for the state rather than for the request. */
    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting" }
            Thread.sleep(10)
        }
    }

    @Test
    fun unlockViewModelSendsThePinOnceAndHandsOverTheSession() {
        val sessions = StationSessions { StationRepositoryImpl(factory) }
        val handle = SavedStateHandle(mapOf("orgSlug" to key.orgSlug, "eventSlug" to key.eventSlug))
        val vm = StationUnlockViewModel(handle, sessions)

        server.enqueue(json(401, """{"ok":false,"error":"Unable to open this check-in station."}"""))
        "1234567".forEach(vm::digit)
        assertFalse(vm.uiState.value.canSubmit)
        vm.digit('8')
        vm.digit('9') // ignored: 8 digits max
        assertEquals(8, vm.uiState.value.pinLength)
        assertFalse("PIN never appears in UI state", vm.uiState.value.toString().contains("12345678"))
        vm.submit()
        awaitUntil { vm.uiState.value.error != null }
        assertEquals("Unable to open this check-in station.", vm.uiState.value.error)
        assertEquals(0, vm.uiState.value.pinLength)
        assertTrue(server.takeRequest().body!!.utf8().contains("\"pin\":\"12345678\""))

        server.enqueue(unlocked)
        "87654321".forEach(vm::digit)
        vm.submit()
        awaitUntil { vm.uiState.value.unlocked }
        server.takeRequest()
        assertTrue(sessions.get(key).isUnlocked)

        // The station screen uses the same cookie jar.
        server.enqueue(bootstrap)
        val station = StationViewModel(handle, sessions)
        awaitUntil { station.uiState.value.eventTitle != null }
        assertEquals("yuyu_checkin_station=proof123", server.takeRequest().headers["Cookie"])
        assertEquals("Hack Night", station.uiState.value.eventTitle)
        assertEquals(12, station.uiState.value.counts?.checkedIn)

        // Expiry mid-shift: reported, and the session is dropped.
        server.enqueue(json(401, """{"ok":false,"error":"Station access has expired."}"""))
        station.submitCode("tkABCDEFGH123")
        awaitUntil { station.uiState.value.accessLost && station.uiState.value.sheet is SheetState.Blocked }
        assertTrue(station.uiState.value.sheet is SheetState.Blocked)
        station.exit()
        assertFalse(sessions.get(key).isUnlocked)
    }
}
