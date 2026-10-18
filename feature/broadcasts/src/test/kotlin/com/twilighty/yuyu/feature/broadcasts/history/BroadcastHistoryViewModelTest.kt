package com.twilighty.yuyu.feature.broadcasts.history

import androidx.lifecycle.SavedStateHandle
import com.twilighty.yuyu.core.network.model.BroadcastHistoryDto
import com.twilighty.yuyu.feature.broadcasts.BroadcastCopy
import com.twilighty.yuyu.feature.broadcasts.FakeBroadcastsRepository
import com.twilighty.yuyu.feature.broadcasts.FakeDraftStore
import com.twilighty.yuyu.feature.broadcasts.HISTORY_ROW
import com.twilighty.yuyu.feature.broadcasts.draft.BroadcastDraft
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class BroadcastHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeBroadcastsRepository()
    private val store = FakeDraftStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = BroadcastHistoryViewModel(SavedStateHandle(mapOf("orgSlug" to "acme", "eventId" to "ev1")), repository, store)

    private class FakeTarget(var nonEmpty: Boolean) : ReuseTarget {
        val applied = mutableListOf<BroadcastHistoryDto>()
        override fun hasNonEmptyDraft() = nonEmpty
        override fun applyHistory(row: BroadcastHistoryDto) {
            applied += row
        }
    }

    @Test
    fun loadsHistoryWithDesignLabels() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        assertEquals(listOf(HISTORY_ROW), vm.uiState.value.history)
        assertEquals("Letter", vm.uiState.value.designLabel("letter"))
        assertEquals("confirmed, waitlisted registrations who checked in", BroadcastCopy.describeAudience(HISTORY_ROW.audience.statuses, HISTORY_ROW.audience.attendance))
    }

    @Test
    fun sentAtUsesTheDeviceLocaleAndZone() {
        val text = BroadcastCopy.formatSentAt("2030-05-09T12:00:00.000Z", Locale.UK, ZoneId.of("Europe/London"))
        assertTrue(text, text.contains("2030") && text.contains("13:00"))
    }

    @Test
    fun reuseWithoutAComposeScreenStoresTheDraft() = runTest(dispatcher) {
        val vm = vm()
        vm.requestReuse(HISTORY_ROW, target = null)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.reused)
        val draft = store.drafts.getValue("acme/ev1")
        assertEquals("Doors open at 6", draft.subject)
        assertEquals(HISTORY_ROW.bodyHtml, draft.bodyHtml)
        assertEquals("letter", draft.design)
        assertEquals(listOf("CONFIRMED", "WAITLISTED"), draft.statuses)
        assertEquals("CHECKED_IN", draft.attendance)
    }

    @Test
    fun reuseOverANonEmptyStoredDraftAsksFirst() = runTest(dispatcher) {
        store.drafts["acme/ev1"] = BroadcastDraft(subject = "Mine", bodyHtml = "<p>work in progress</p>")
        val vm = vm()
        vm.requestReuse(HISTORY_ROW, target = null)
        advanceUntilIdle()
        assertEquals(HISTORY_ROW, vm.uiState.value.pendingReuse)
        assertEquals("Mine", store.drafts.getValue("acme/ev1").subject)
        vm.confirmReuse(target = null)
        advanceUntilIdle()
        assertNull(vm.uiState.value.pendingReuse)
        assertEquals("Doors open at 6", store.drafts.getValue("acme/ev1").subject)
        assertTrue(vm.uiState.value.reused)
    }

    @Test
    fun reuseIntoTheOpenComposeScreen() = runTest(dispatcher) {
        val vm = vm()
        val target = FakeTarget(nonEmpty = true)
        vm.requestReuse(HISTORY_ROW, target)
        advanceUntilIdle()
        assertEquals(HISTORY_ROW, vm.uiState.value.pendingReuse)
        assertTrue(target.applied.isEmpty())
        vm.confirmReuse(target)
        advanceUntilIdle()
        assertEquals(listOf(HISTORY_ROW), target.applied)
        assertTrue(store.drafts.isEmpty())

        val empty = FakeTarget(nonEmpty = false)
        vm.onReusedHandled()
        vm.requestReuse(HISTORY_ROW, empty)
        advanceUntilIdle()
        assertEquals(listOf(HISTORY_ROW), empty.applied)
    }
}
