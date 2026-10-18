package com.twilighty.yuyu.feature.checkin

import androidx.lifecycle.SavedStateHandle
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.network.model.CheckInGateDto
import com.twilighty.yuyu.core.network.model.SyncCheckInsResultDto
import com.twilighty.yuyu.core.network.model.SyncFailureDto
import com.twilighty.yuyu.feature.checkin.domain.FeedbackKind
import com.twilighty.yuyu.feature.checkin.domain.SyncOutcome
import com.twilighty.yuyu.feature.checkin.flow.SheetState
import com.twilighty.yuyu.feature.checkin.scanner.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {
    private val repo = FakeCheckInRepository()
    private val events = FakeEvents()
    private val connectivity = FakeConnectivity(initial = true)
    private val code = "https://yuyu.example/ticket/tkABCDEFGH123"

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ScannerViewModel(
        SavedStateHandle(mapOf("orgSlug" to "civic-tech", "eventId" to "ev1")),
        repo,
        events,
        connectivity,
    )

    private fun TestScope.feedbackOf(vm: ScannerViewModel): MutableList<FeedbackKind> {
        val events = mutableListOf<FeedbackKind>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.feedback.collect { events += it } }
        return events
    }

    private val ScannerViewModel.sheet get() = uiState.value.sheet

    @Test
    fun previewConfirmSuccessUndo() = runTest {
        val vm = viewModel()
        val feedback = feedbackOf(vm)
        assertEquals(3, vm.uiState.value.counts?.checkedIn)

        vm.onScanned(code)
        val ready = vm.sheet as SheetState.Ready
        assertEquals(false, ready.override)
        assertEquals("Ada Lovelace", ready.guest.displayName)
        assertEquals(listOf(code), repo.previews)

        vm.confirm()
        // Scanned guests are checked in by their raw code (the server records a scan).
        assertEquals(Triple("code", code, false), repo.checkIns.single())
        val admitted = vm.sheet as SheetState.Admitted
        assertEquals(false, admitted.offline)
        assertEquals("2026-10-18T10:00:00Z", admitted.checkedInAt)
        assertEquals(listOf(FeedbackKind.Valid), feedback)

        vm.undo()
        assertEquals(listOf("r1"), repo.undos)
        assertTrue(vm.sheet is SheetState.Undone)

        vm.dismissSheet()
        assertEquals(SheetState.Hidden, vm.sheet)
        assertTrue("stats refresh after each action", repo.statsCalls >= 3)
    }

    @Test
    fun waitlistNeedsForceThenOverride() = runTest {
        repo.previewResult = AppResult.Success(
            previewDto(status = "WAITLISTED", gate = CheckInGateDto(ok = false, reason = "Guest is on the waitlist", needsForce = true)),
        )
        val vm = viewModel()
        vm.onScanned(code)
        val ready = vm.sheet as SheetState.Ready
        assertTrue(ready.override)
        assertEquals("Guest is on the waitlist", ready.reason)

        vm.confirm()
        assertEquals(Triple("code", code, true), repo.checkIns.single())
        assertTrue(vm.sheet is SheetState.Admitted)
    }

    @Test
    fun serverNeedsForceOnCheckInTurnsIntoOverride() = runTest {
        repo.checkInResults.addLast(AppResult.Failure(AppError.NeedsForce("Now waitlisted. Check in anyway?")))
        val vm = viewModel()
        vm.onScanned(code)
        vm.confirm()
        val ready = vm.sheet as SheetState.Ready
        assertTrue(ready.override)
        assertEquals("Now waitlisted. Check in anyway?", ready.reason)
        vm.confirm()
        assertEquals(true, repo.checkIns.last().third)
        assertTrue(vm.sheet is SheetState.Admitted)
    }

    @Test
    fun blockedStatusShowsTheReason() = runTest {
        repo.previewResult = AppResult.Success(
            previewDto(status = "REJECTED", gate = CheckInGateDto(ok = false, reason = "This registration was rejected.", blocked = true)),
        )
        val vm = viewModel()
        val feedback = feedbackOf(vm)
        vm.onScanned(code)
        val blocked = vm.sheet as SheetState.Blocked
        assertEquals("This registration was rejected.", blocked.message)
        assertEquals("Ada Lovelace", blocked.guest?.displayName)
        assertEquals(listOf(FeedbackKind.Invalid), feedback)
        vm.confirm()
        assertTrue(repo.checkIns.isEmpty())
    }

    @Test
    fun alreadyCheckedInShowsTheTime() = runTest {
        repo.previewResult = AppResult.Success(previewDto(alreadyCheckedIn = true, checkedInAt = "2026-10-18T09:30:00Z"))
        val vm = viewModel()
        val feedback = feedbackOf(vm)
        vm.onScanned(code)
        val already = vm.sheet as SheetState.AlreadyIn
        assertEquals("2026-10-18T09:30:00Z", already.checkedInAt)
        assertEquals(listOf(FeedbackKind.Already), feedback)
    }

    @Test
    fun unknownTicketIsInvalid() = runTest {
        repo.previewResult = AppResult.Failure(AppError.NotFound("Ticket not found for this event."))
        val vm = viewModel()
        vm.onScanned(code)
        val blocked = vm.sheet as SheetState.Blocked
        assertEquals("Ticket not found", blocked.title)
        assertNull(blocked.guest)
    }

    @Test
    fun networkErrorFallsBackToTheOfflineRosterAndQueues() = runTest {
        repo.previewResult = AppResult.Failure(AppError.Network())
        repo.roster.value = mapOf("r1" to offlineGuest())
        repo.codes[code] = "r1"
        val vm = viewModel()
        vm.onScanned(code)
        val ready = vm.sheet as SheetState.Ready
        assertTrue(ready.guest.offline)

        vm.confirm()
        assertTrue("no online check-in after an IO failure", repo.checkIns.isEmpty())
        assertEquals(listOf("r1" to false), repo.recorded)
        val admitted = vm.sheet as SheetState.Admitted
        assertTrue(admitted.offline)
        assertEquals(1, vm.uiState.value.pendingCount)
        assertEquals("Ada Lovelace", vm.uiState.value.pending.single().displayName)

        // Undo removes the unsynced item from the queue instead of calling the server.
        vm.undo()
        assertEquals(listOf("r1"), repo.offlineUndos)
        assertTrue(repo.undos.isEmpty())
        assertTrue(vm.sheet is SheetState.Undone)
        assertEquals(0, vm.uiState.value.pendingCount)
    }

    @Test
    fun checkInNetworkErrorAfterOnlinePreviewIsQueued() = runTest {
        repo.roster.value = mapOf("r1" to offlineGuest())
        repo.checkInResults.addLast(AppResult.Failure(AppError.Network()))
        val vm = viewModel()
        vm.onScanned(code)
        vm.confirm()
        assertEquals(listOf("r1" to false), repo.recorded)
        assertTrue((vm.sheet as SheetState.Admitted).offline)
    }

    @Test
    fun knownOfflineSkipsTheNetworkAndGatesLocally() = runTest {
        connectivity.online.value = false
        repo.roster.value = mapOf("r1" to offlineGuest(status = "WAITLISTED"))
        repo.codes[code] = "r1"
        val vm = viewModel()
        assertTrue(vm.uiState.value.showOfflineBanner)
        vm.onScanned(code)
        assertTrue("no request while offline", repo.previews.isEmpty())
        val ready = vm.sheet as SheetState.Ready
        assertTrue(ready.override)
        assertEquals("Guest is on the waitlist — use “Override” to check in at the door.", ready.reason)
        vm.confirm()
        assertEquals(listOf("r1" to true), repo.recorded)
    }

    @Test
    fun offlineMissesExplainWhy() = runTest {
        connectivity.online.value = false
        val vm = viewModel()
        vm.onScanned(code)
        assertEquals("You're offline", (vm.sheet as SheetState.Blocked).title)
        vm.dismissSheet()

        repo.roster.value = mapOf("r2" to offlineGuest("r2"))
        vm.onScanned("https://yuyu.example/ticket/otherTOKEN999")
        assertEquals("Not on the offline guest list", (vm.sheet as SheetState.Blocked).title)
    }

    @Test
    fun locallyCheckedInGuestIsAlreadyIn() = runTest {
        connectivity.online.value = false
        repo.roster.value = mapOf("r1" to offlineGuest(checkedInAt = "2026-10-18T09:00:00Z"))
        repo.codes[code] = "r1"
        val vm = viewModel()
        vm.onScanned(code)
        assertTrue(vm.sheet is SheetState.AlreadyIn)
    }

    @Test
    fun sameCodeIsIgnoredWhileShowingAndForThreeSecondsAfter() = runTest {
        val vm = viewModel()
        vm.onScanned(code)
        vm.onScanned(code) // sheet showing: analysis is paused
        vm.onScanned("https://yuyu.example/ticket/someoneELSE123")
        assertEquals(1, repo.previews.size)
        vm.dismissSheet()
        vm.onScanned(code) // still inside the 3 s window
        assertEquals(1, repo.previews.size)
        vm.onScanned("https://yuyu.example/ticket/someoneELSE123")
        assertEquals(2, repo.previews.size)
    }

    @Test
    fun fastModeChecksInWithoutTheConfirmTap() = runTest {
        val vm = viewModel()
        vm.setFastMode(true)
        vm.onScanned(code)
        assertEquals(1, repo.checkIns.size)
        assertTrue(vm.sheet is SheetState.Admitted)
    }

    @Test
    fun reconnectSyncsAndReportsEachItem() = runTest {
        connectivity.online.value = false
        repo.roster.value = mapOf("r1" to offlineGuest("r1"), "r2" to offlineGuest("r2", name = "Grace"), "r3" to offlineGuest("r3", name = "Linus"))
        val vm = viewModel()
        repo.recordOfflineCheckIn("civic-tech", "ev1", "r1", false)
        repo.recordOfflineCheckIn("civic-tech", "ev1", "r2", false)
        repo.recordOfflineCheckIn("civic-tech", "ev1", "r3", false)
        assertEquals(3, vm.uiState.value.pendingCount)
        repo.syncResult = AppResult.Success(
            SyncCheckInsResultDto(
                syncedIds = listOf("r1"),
                alreadyCheckedInIds = listOf("r2"),
                failed = listOf(SyncFailureDto("r3", "This RSVP cannot be checked in.")),
            ),
        )

        connectivity.online.value = true
        assertEquals(1, repo.syncCalls)
        val report = vm.uiState.value.syncReport!!
        assertEquals(SyncOutcome.Synced, report.items[0].outcome)
        assertEquals(SyncOutcome.AlreadyCheckedIn, report.items[1].outcome)
        assertEquals(SyncOutcome.Failed("This RSVP cannot be checked in."), report.items[2].outcome)
        assertEquals("Linus", report.items[2].displayName)
        // The failed one is never dropped.
        assertEquals(listOf("r3"), repo.pending.value.map { it.rsvpId })
    }

    @Test
    fun forbiddenShowsNoAccess() = runTest {
        repo.statsResult = AppResult.Failure(AppError.Forbidden())
        val vm = viewModel()
        assertEquals(ScannerViewModel.NO_ACCESS, vm.uiState.value.noAccessMessage)
    }
}
