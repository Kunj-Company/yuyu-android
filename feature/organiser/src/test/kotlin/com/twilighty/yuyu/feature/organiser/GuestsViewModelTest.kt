package com.twilighty.yuyu.feature.organiser

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.network.model.GuestPageDto
import com.twilighty.yuyu.feature.organiser.guests.CheckedInFilter
import com.twilighty.yuyu.feature.organiser.guests.GuestRowActions
import com.twilighty.yuyu.feature.organiser.guests.GuestStatusFilter
import com.twilighty.yuyu.feature.organiser.guests.GuestsViewModel
import com.twilighty.yuyu.feature.organiser.guests.UndoOffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuestsViewModelTest : MainDispatcherTest() {
    private val guests = FakeGuestsRepository()
    private val events = FakeEventsRepository()

    private fun started(): GuestsViewModel = GuestsViewModel(guests, events).also { it.start("civic-tech", "e1") }

    private fun GuestsViewModel.status(id: String) = uiState.value.guests.first { it.rsvpId == id }.status

    @Test
    fun loadsFirstPageWithPermissionsAndTimezone() = runTest {
        val vm = started()
        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals(listOf("a", "b", "c"), state.guests.map { it.rsvpId })
        assertTrue(state.canManage)
        assertEquals("Europe/London", state.timezone)
        assertEquals("ALL", guests.queries.single().status)
        assertEquals("any", guests.queries.single().checkedIn)
    }

    @Test
    fun statusAndCheckedInFiltersAreSentToTheServer() = runTest {
        val vm = started()
        vm.setStatus(GuestStatusFilter.Pending)
        vm.setCheckedIn(CheckedInFilter.No)
        vm.setStatus(GuestStatusFilter.Waitlist)
        val sent = guests.queries.map { it.status to it.checkedIn }
        assertEquals(
            listOf("ALL" to "any", "PENDING_APPROVAL" to "any", "PENDING_APPROVAL" to "no", "WAITLISTED" to "no"),
            sent,
        )
        vm.setStatus(GuestStatusFilter.Declined)
        assertEquals("REJECTED", guests.queries.last().status)
        vm.setStatus(GuestStatusFilter.Going)
        assertEquals("CONFIRMED", guests.queries.last().status)
        assertTrue(vm.uiState.value.filtersActive)
    }

    @Test
    fun searchIsDebounced() = runTest {
        val vm = started()
        vm.onQueryChange("a")
        vm.onQueryChange("ad")
        vm.onQueryChange("ada ")
        advanceTimeBy(299)
        runCurrent()
        assertEquals(1, guests.queries.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, guests.queries.size)
        assertEquals("ada", guests.queries.last().search)
        assertEquals("ada ", vm.uiState.value.query)
    }

    @Test
    fun pagingFollowsNextCursorUntilTheEnd() = runTest {
        guests.pages = { q ->
            when (q.cursor) {
                null -> AppResult.Success(GuestPageDto(listOf(guest("1"), guest("2")), nextCursor = "c2"))
                "c2" -> AppResult.Success(GuestPageDto(listOf(guest("2"), guest("3")), nextCursor = "c3"))
                else -> AppResult.Success(GuestPageDto(listOf(guest("4")), nextCursor = null))
            }
        }
        val vm = started()
        assertTrue(vm.uiState.value.hasMore)
        vm.loadMore()
        // Duplicates across pages are dropped.
        assertEquals(listOf("1", "2", "3"), vm.uiState.value.guests.map { it.rsvpId })
        vm.loadMore()
        assertEquals(listOf("1", "2", "3", "4"), vm.uiState.value.guests.map { it.rsvpId })
        assertFalse(vm.uiState.value.hasMore)
        vm.loadMore() // at the end: no request
        assertEquals(listOf(null, "c2", "c3"), guests.queries.map { it.cursor })
    }

    @Test
    fun filterChangeRestartsPagingFromTheFirstPage() = runTest {
        guests.pages = { q -> AppResult.Success(GuestPageDto(listOf(guest(q.cursor ?: "first")), nextCursor = if (q.cursor == null) "c2" else null)) }
        val vm = started()
        vm.loadMore()
        vm.setStatus(GuestStatusFilter.Going)
        assertNull(guests.queries.last().cursor)
        assertEquals(listOf("first"), vm.uiState.value.guests.map { it.rsvpId })
    }

    @Test
    fun approveIsOptimisticThenTakesTheServerCopy() = runTest {
        val vm = started()
        val gate = CompletableDeferred<Unit>().also { guests.gate = it }
        vm.approve(vm.uiState.value.guests.first { it.rsvpId == "b" })
        assertEquals("CONFIRMED", vm.status("b"))
        assertTrue("b" in vm.uiState.value.busyIds)
        gate.complete(Unit)
        runCurrent()
        val row = vm.uiState.value.guests.first { it.rsvpId == "b" }
        assertEquals("Server b", row.displayName)
        assertFalse("b" in vm.uiState.value.busyIds)
        assertEquals("Approved", vm.uiState.value.message)
    }

    @Test
    fun approveRollsBackOnError() = runTest {
        val vm = started()
        guests.lifecycleResult = { _, _ -> AppResult.Failure(AppError.Conflict("Only pending requests can be approved.")) }
        vm.approve(vm.uiState.value.guests.first { it.rsvpId == "b" })
        assertEquals("PENDING_APPROVAL", vm.status("b"))
        assertEquals("Only pending requests can be approved.", vm.uiState.value.message)
        assertTrue(vm.uiState.value.busyIds.isEmpty())
    }

    @Test
    fun declineIsOptimisticAndRollsBack() = runTest {
        val vm = started()
        val gate = CompletableDeferred<Unit>().also { guests.gate = it }
        guests.lifecycleResult = { _, _ -> AppResult.Failure(AppError.Network()) }
        vm.decline(vm.uiState.value.guests.first { it.rsvpId == "b" })
        assertEquals("REJECTED", vm.status("b"))
        gate.complete(Unit)
        runCurrent()
        assertEquals("PENDING_APPROVAL", vm.status("b"))
        assertEquals(listOf("reject:b"), guests.calls)
    }

    @Test
    fun promoteSucceedsAndIgnoresDoubleTaps() = runTest {
        val vm = started()
        val gate = CompletableDeferred<Unit>().also { guests.gate = it }
        val c = vm.uiState.value.guests.first { it.rsvpId == "c" }
        vm.promote(c)
        vm.promote(c)
        assertEquals("CONFIRMED", vm.status("c"))
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("promote:c"), guests.calls)
        assertEquals("CONFIRMED", vm.status("c"))
        assertEquals("Spot given", vm.uiState.value.message)
    }

    @Test
    fun removeThenUndoRestoresAndReloads() = runTest {
        val vm = started()
        val target = vm.uiState.value.guests.first { it.rsvpId == "b" }
        vm.askRemove(target)
        assertEquals(target, vm.uiState.value.confirmRemove)
        vm.confirmRemove()
        assertEquals(listOf("a", "c"), vm.uiState.value.guests.map { it.rsvpId })
        val offer = vm.uiState.value.undoOffer
        assertEquals(UndoOffer("undo-1", target.displayName), offer)

        vm.undoOfferShown()
        val queriesBefore = guests.queries.size
        vm.undoRemove(offer!!)
        assertEquals(listOf("delete:b", "restore:undo-1"), guests.calls)
        assertEquals(queriesBefore + 1, guests.queries.size)
        assertEquals(listOf("a", "b", "c"), vm.uiState.value.guests.map { it.rsvpId })
        assertEquals("${target.displayName} restored", vm.uiState.value.message)
    }

    @Test
    fun removeFailurePutsTheGuestBackInPlace() = runTest {
        val vm = started()
        guests.deleteResult = AppResult.Failure(AppError.Forbidden())
        vm.askRemove(vm.uiState.value.guests.first { it.rsvpId == "b" })
        vm.confirmRemove()
        assertEquals(listOf("a", "b", "c"), vm.uiState.value.guests.map { it.rsvpId })
        assertNull(vm.uiState.value.undoOffer)
        assertEquals("You don't have permission to do that.", vm.uiState.value.message)
    }

    @Test
    fun expiredUndoSaysSoPlainly() = runTest {
        val vm = started()
        guests.restoreResult = AppResult.Failure(AppError.NotFound())
        vm.undoRemove(UndoOffer("old", "Grace"))
        assertEquals("This removal can no longer be undone.", vm.uiState.value.message)
    }

    @Test
    fun checkInOnlyHelpersGetAReadOnlyList() = runTest {
        events.detail = AppResult.Success(eventDetail(permissions = CheckInOnly))
        val vm = started()
        val state = vm.uiState.value
        assertFalse(state.canManage)
        assertTrue(state.canCheckIn)
        state.guests.forEach { assertEquals(GuestRowActions.None, state.actionsFor(it)) }
        vm.approve(state.guests.first { it.rsvpId == "b" })
        assertTrue(guests.calls.isEmpty())
        assertEquals("PENDING_APPROVAL", vm.status("b"))
    }

    @Test
    fun managersSeeActionsMatchingEachStatus() = runTest {
        val vm = started()
        val state = vm.uiState.value
        assertEquals(GuestRowActions(approveDecline = false, promote = false, remove = true), state.actionsFor(guest("x", "CONFIRMED")))
        assertEquals(GuestRowActions(approveDecline = true, promote = false, remove = true), state.actionsFor(guest("x", "PENDING_APPROVAL")))
        assertEquals(GuestRowActions(approveDecline = false, promote = true, remove = true), state.actionsFor(guest("x", "WAITLISTED")))
    }

    @Test
    fun addGuestValidatesThenPrepends() = runTest {
        val vm = started()
        vm.openAddGuest(true)
        vm.addGuest(" ", "not-an-email")
        assertEquals("Enter the guest's name.", vm.uiState.value.addGuest.nameError)
        assertEquals("Enter a valid email address.", vm.uiState.value.addGuest.emailError)
        assertTrue(guests.calls.isEmpty())

        vm.addGuest("Ada", "ada@example.test")
        assertFalse(vm.uiState.value.addGuest.open)
        assertEquals("new", vm.uiState.value.guests.first().rsvpId)
        assertEquals("Ada added", vm.uiState.value.message)
    }

    @Test
    fun addGuestShowsServerFieldErrors() = runTest {
        val vm = started()
        vm.openAddGuest(true)
        guests.addResult = AppResult.Failure(AppError.InvalidRequest("Invalid input.", mapOf("email" to listOf("This email is already registered."))))
        vm.addGuest("Ada", "ada@example.test")
        val form = vm.uiState.value.addGuest
        assertTrue(form.open)
        assertFalse(form.saving)
        assertEquals("This email is already registered.", form.emailError)
        assertNull(form.error)
    }
}
