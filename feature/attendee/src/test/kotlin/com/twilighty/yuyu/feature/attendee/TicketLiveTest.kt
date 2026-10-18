package com.twilighty.yuyu.feature.attendee

import androidx.lifecycle.SavedStateHandle
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.network.model.RegistrationsDto
import com.twilighty.yuyu.core.network.model.TicketEvent
import com.twilighty.yuyu.core.network.model.TicketStatusDto
import com.twilighty.yuyu.feature.attendee.ticket.LiveMode
import com.twilighty.yuyu.feature.attendee.ticket.LiveUpdate
import com.twilighty.yuyu.feature.attendee.ticket.RecentTicketStore
import com.twilighty.yuyu.feature.attendee.ticket.TicketLiveWatcher
import com.twilighty.yuyu.feature.attendee.ticket.TicketViewModel
import com.twilighty.yuyu.feature.attendee.ticket.checkedInHeading
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TicketLiveTest {
    private val dispatcher = StandardTestDispatcher()
    private val events = FakePublicEventsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---- Watcher ---------------------------------------------------------------------------------

    @Test
    fun liveCheckInOverSseIsAnimated() = runTest(dispatcher) {
        events.streams += flowOf(TicketEvent.State(false, null), TicketEvent.CheckedIn("2030-05-10T17:04:00.000Z"))
        val updates = TicketLiveWatcher(events).watch("tok").toList()
        assertEquals(
            listOf(LiveUpdate.Mode(LiveMode.Streaming), LiveUpdate.CheckedIn("2030-05-10T17:04:00.000Z", live = true)),
            updates,
        )
    }

    @Test
    fun alreadyCheckedInStateIsNotAnimated() = runTest(dispatcher) {
        events.streams += flowOf(TicketEvent.State(true, "2030-05-10T17:04:00.000Z"))
        val updates = TicketLiveWatcher(events).watch("tok").toList()
        assertEquals(LiveUpdate.CheckedIn("2030-05-10T17:04:00.000Z", live = false), updates.last())
    }

    @Test
    fun sseFailureFallsBackToPollingEvery15Seconds() = runTest(dispatcher) {
        events.streams += flow { emit(TicketEvent.State(false, null)); throw IOException("503") }
        events.statuses += AppResult.Success(TicketStatusDto(false, null))
        events.statuses += AppResult.Failure(AppError.Network())
        events.statuses += AppResult.Success(TicketStatusDto(true, "2030-05-10T17:04:00.000Z"))
        val updates = mutableListOf<LiveUpdate>()
        val job = backgroundScope.launch { TicketLiveWatcher(events).watch("tok").collect { updates += it } }
        runCurrent()
        assertEquals(1, events.statusCalls)
        assertEquals(listOf(LiveUpdate.Mode(LiveMode.Streaming), LiveUpdate.Mode(LiveMode.Polling)), updates)
        advanceTimeBy(15_001)
        assertEquals(2, events.statusCalls)
        assertEquals(LiveUpdate.Mode(LiveMode.Offline), updates.last())
        advanceTimeBy(15_001)
        assertEquals(3, events.statusCalls)
        assertEquals(LiveUpdate.CheckedIn("2030-05-10T17:04:00.000Z", live = true), updates.last())
        assertEquals(1, events.streamCalls)
        assertTrue(job.isCompleted)
    }

    @Test
    fun streamThatEndsNormallyIsResubscribed() = runTest(dispatcher) {
        events.streams += flowOf(TicketEvent.State(false, null))
        events.streams += flowOf(TicketEvent.CheckedIn(null))
        val updates = TicketLiveWatcher(events).watch("tok").toList()
        assertEquals(2, events.streamCalls)
        assertEquals(LiveUpdate.CheckedIn(null, live = true), updates.last())
    }

    // ---- ViewModel transitions ------------------------------------------------------------------

    private fun ticketViewModel(me: FakeMeRepository, token: String = "tkEXAMPLEownerTicketToken01") = TicketViewModel(
        SavedStateHandle(mapOf("token" to token)),
        me,
        FakeAuthRepository(),
        RecentTicketStore(),
        TicketLiveWatcher(events),
        CoroutineScope(dispatcher),
    )

    @Test
    fun ticketGoesFromLiveToJustCheckedInThenTorn() = runTest(dispatcher) {
        val me = FakeMeRepository(Fixtures.registrations())
        events.streams += flow {
            emit(TicketEvent.State(false, null))
            kotlinx.coroutines.delay(1_000)
            emit(TicketEvent.CheckedIn("2030-05-10T17:04:00.000Z"))
            awaitCancellation()
        }
        val vm = ticketViewModel(me)
        vm.onVisible()
        runCurrent()
        var s = vm.uiState.value
        if (!s.showQr) vm.onHidden()
        assertFalse(s.loading)
        assertTrue(s.showQr)
        assertEquals("Ada Lovelace", s.context?.attendeeName)
        assertEquals(LiveMode.Streaming, s.liveMode)

        advanceTimeBy(1_001)
        s = vm.uiState.value
        assertTrue(s.checkedIn)
        assertTrue(s.justCheckedIn)
        assertFalse(s.showQr)
        assertEquals("2030-05-10T17:04:00.000Z", s.checkedInAt)
        vm.onCheckInAnimationDone()
        assertFalse(vm.uiState.value.justCheckedIn)
        advanceUntilIdle()
    }

    @Test
    fun cachedCheckedInTicketRendersTornAndNeverWatches() = runTest(dispatcher) {
        val data = Fixtures.registrations()
        val checked = data.upcoming.first().copy(checkedInAt = "2030-05-10T17:04:00.000Z")
        val me = FakeMeRepository(RegistrationsDto(upcoming = listOf(checked)))
        val vm = ticketViewModel(me)
        vm.onVisible()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s.checkedIn)
        assertFalse(s.justCheckedIn)
        assertFalse(s.showQr)
        assertEquals(0, events.streamCalls)
        assertEquals(LiveMode.Idle, s.liveMode)
    }

    @Test
    fun hidingTheTicketStopsWatching() = runTest(dispatcher) {
        val me = FakeMeRepository(Fixtures.registrations())
        events.streams += flow<TicketEvent> { emit(TicketEvent.State(false, null)); awaitCancellation() }
        val vm = ticketViewModel(me)
        vm.onVisible()
        runCurrent()
        val mode = vm.uiState.value.liveMode
        vm.onHidden()
        assertEquals(LiveMode.Streaming, mode)
        assertEquals(LiveMode.Idle, vm.uiState.value.liveMode)
        advanceUntilIdle()
    }

    @Test
    fun offlineTicketOpensFromCache() = runTest(dispatcher) {
        val me = FakeMeRepository(Fixtures.registrations()).apply { remote = AppResult.Failure(AppError.Network()) }
        events.statuses += AppResult.Failure(AppError.Network())
        val vm = ticketViewModel(me)
        try {
            vm.onVisible()
            runCurrent()
            val s = vm.uiState.value
            assertEquals("Spring Hack Night", s.context?.event?.title)
            assertTrue(s.showQr)
            assertEquals(LiveMode.Offline, s.liveMode)
        } finally {
            vm.onHidden()
        }
    }

    @Test
    fun checkedInHeadingUsesEventTimezone() {
        assertEquals("Checked in at 6:04 PM", checkedInHeading("2030-05-10T17:04:00.000Z", "Europe/London"))
        assertEquals("Checked in", checkedInHeading(null, "Europe/London"))
    }
}
