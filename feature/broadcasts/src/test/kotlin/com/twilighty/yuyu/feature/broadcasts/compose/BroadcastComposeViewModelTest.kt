package com.twilighty.yuyu.feature.broadcasts.compose

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.network.model.BroadcastAttendance
import com.twilighty.yuyu.feature.broadcasts.FakeBroadcastsRepository
import com.twilighty.yuyu.feature.broadcasts.FakeDraftStore
import com.twilighty.yuyu.feature.broadcasts.HISTORY_ROW
import com.twilighty.yuyu.feature.broadcasts.draft.BroadcastDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BroadcastComposeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeBroadcastsRepository()
    private val store = FakeDraftStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun handle(draft: BroadcastDraft? = null) = SavedStateHandle(
        buildMap {
            put("orgSlug", "acme")
            put("eventId", "ev1")
            if (draft != null) put(BroadcastComposeViewModel.KEY_DRAFT, draft.encode())
        },
    )

    private fun TestScope.ready(savedStateHandle: SavedStateHandle = handle()): BroadcastComposeViewModel {
        val vm = BroadcastComposeViewModel(savedStateHandle, repository, store)
        advanceUntilIdle()
        return vm
    }

    private fun TestScope.reviewAndConfirm(vm: BroadcastComposeViewModel) {
        vm.openReview()
        vm.setReviewChecked(true)
        assertTrue(vm.confirmSend())
        runCurrent()
    }

    @Test
    fun loadsMetaAndStartsFromTheFirstFeaturedTemplate() = runTest(dispatcher) {
        val vm = ready()
        val state = vm.uiState.value
        assertFalse(state.metaLoading)
        assertEquals("update", state.templateId)
        assertEquals("Update about {{event}}", vm.subject.text)
        assertEquals(TEMPLATE_UPDATE_BODY, vm.editor.html)
        assertEquals(RecipientCount.Ready(42), state.count)
        assertFalse(vm.isEdited())
    }

    @Test
    fun recipientCountIsDebounced() = runTest(dispatcher) {
        val vm = ready()
        assertEquals(1, repository.countCalls.size)

        vm.toggleStatus("WAITLISTED")
        advanceTimeBy(100)
        vm.toggleStatus("PENDING_APPROVAL")
        advanceTimeBy(100)
        vm.setAttendance(BroadcastAttendance.CHECKED_IN)
        runCurrent()
        assertEquals(RecipientCount.Counting, vm.uiState.value.count)
        advanceTimeBy(399)
        assertEquals(1, repository.countCalls.size)
        advanceTimeBy(2)
        assertEquals(2, repository.countCalls.size)
        val request = repository.countCalls.last()
        assertEquals(listOf("CONFIRMED", "WAITLISTED", "PENDING_APPROVAL"), request.statuses)
        assertEquals(BroadcastAttendance.CHECKED_IN, request.attendance)
    }

    @Test
    fun noStatusesMeansZeroRecipientsWithoutAskingTheServer() = runTest(dispatcher) {
        val vm = ready()
        vm.toggleStatus("CONFIRMED")
        advanceUntilIdle()
        assertEquals(RecipientCount.Ready(0), vm.uiState.value.count)
        assertEquals(1, repository.countCalls.size)
        assertEquals("Pick at least one group to send to", vm.blocker())
    }

    @Test
    fun zeroRecipientsDisablesSend() = runTest(dispatcher) {
        repository.countResult = { AppResult.Success(0) }
        val vm = ready()
        assertEquals("No one matches this audience yet", vm.blocker())
        vm.openReview()
        assertNull(vm.uiState.value.review)
    }

    @Test
    fun emptyBodyBlocksSending() = runTest(dispatcher) {
        val vm = ready()
        vm.editor.setHtml("<p><strong> </strong></p>")
        assertEquals("Add a subject and a message to send", vm.blocker())
    }

    @Test
    fun reviewCheckboxGatesTheSend() = runTest(dispatcher) {
        val vm = ready()
        vm.openReview()
        val review = checkNotNull(vm.uiState.value.review)
        assertEquals(42, review.count)
        assertEquals("confirmed registrations", review.audience)
        assertEquals("Update about {{event}}", review.subject)
        assertFalse(vm.confirmSend())
        assertNull(vm.uiState.value.countdownSeconds)
        vm.setReviewChecked(true)
        assertTrue(vm.confirmSend())
        assertNull(vm.uiState.value.review)
        assertEquals(5, vm.uiState.value.countdownSeconds)
    }

    @Test
    fun theRequestFiresOnlyAfterFiveSeconds() = runTest(dispatcher) {
        val vm = ready()
        reviewAndConfirm(vm)
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(1, vm.uiState.value.countdownSeconds)
        advanceTimeBy(999)
        assertTrue(repository.sendCalls.isEmpty())
        advanceTimeBy(2)
        assertEquals(1, repository.sendCalls.size)
        val sent = repository.sendCalls.single()
        assertEquals(listOf("CONFIRMED"), sent.statuses)
        assertEquals("Update about {{event}}", sent.subject)
        assertEquals(TEMPLATE_UPDATE_BODY, sent.body)
        assertEquals("classic", sent.design)
        advanceUntilIdle()
        assertEquals(42, vm.uiState.value.sentCount)
    }

    @Test
    fun editsDuringTheCountdownDontChangeWhatIsSent() = runTest(dispatcher) {
        val vm = ready()
        reviewAndConfirm(vm)
        vm.onSubjectChange(TextFieldValue("Changed my mind"))
        advanceUntilIdle()
        assertEquals("Update about {{event}}", repository.sendCalls.single().subject)
    }

    @Test
    fun undoCancelsTheSend() = runTest(dispatcher) {
        val vm = ready()
        reviewAndConfirm(vm)
        advanceTimeBy(2_000)
        vm.undoSend()
        advanceUntilIdle()
        assertTrue(repository.sendCalls.isEmpty())
        assertNull(vm.uiState.value.countdownSeconds)
        assertEquals("Sending cancelled. Nothing was sent.", vm.uiState.value.message)
        assertNull(vm.uiState.value.sentCount)
    }

    @Test
    fun leavingTheScreenCancelsTheSendWithAWarning() = runTest(dispatcher) {
        val vm = ready()
        reviewAndConfirm(vm)
        advanceTimeBy(4_500)
        vm.cancelSendBecauseLeft()
        advanceUntilIdle()
        assertTrue(repository.sendCalls.isEmpty())
        assertEquals("You left the screen, so sending was cancelled. Nothing was sent.", vm.uiState.value.message)
    }

    @Test
    fun successClearsTheSavedDraftAndRefreshesHistory() = runTest(dispatcher) {
        val vm = ready()
        vm.onSubjectChange(TextFieldValue("Doors at 6"))
        advanceUntilIdle()
        assertNotNull(store.drafts["acme/ev1"])
        val metaCallsBefore = repository.metaCalls
        reviewAndConfirm(vm)
        advanceUntilIdle()
        assertEquals(42, vm.uiState.value.sentCount)
        assertNull(store.drafts["acme/ev1"])
        assertEquals(metaCallsBefore + 1, repository.metaCalls)
    }

    @Test
    fun draftIsAutosavedAndRestoredAfterTheAppIsKilled() = runTest(dispatcher) {
        val savedStateHandle = handle()
        val vm = ready(savedStateHandle)
        vm.onSubjectChange(TextFieldValue("Parking update"))
        vm.editor.setHtml("<p>Use the <em>north</em> entrance.</p>")
        vm.toggleStatus("WAITLISTED")
        vm.setDesign("letter")
        advanceTimeBy(BroadcastComposeViewModel.AUTOSAVE_DELAY_MS - 1)
        assertNull(store.drafts["acme/ev1"]?.takeIf { it.subject == "Parking update" })
        advanceUntilIdle()
        val saved = store.drafts.getValue("acme/ev1")
        assertEquals("Parking update", saved.subject)
        assertEquals("<p>Use the <em>north</em> entrance.</p>", saved.bodyHtml)
        assertEquals(listOf("CONFIRMED", "WAITLISTED"), saved.statuses)
        assertEquals("letter", saved.design)
        // SavedStateHandle is updated immediately for process death.
        assertEquals("Parking update", BroadcastDraft.decode(savedStateHandle[BroadcastComposeViewModel.KEY_DRAFT])?.subject)

        // A new screen (fresh SavedStateHandle) restores from DataStore instead of loading a template.
        val restored = ready(handle())
        assertEquals("Parking update", restored.subject.text)
        assertEquals("<p>Use the <em>north</em> entrance.</p>", restored.editor.html)
        assertEquals(setOf("CONFIRMED", "WAITLISTED"), restored.uiState.value.statuses)
        assertEquals("letter", restored.uiState.value.design)
        assertEquals("update", restored.uiState.value.templateId)
        assertTrue(restored.isEdited())
    }

    @Test
    fun savedStateWinsOverTheStoredDraft() = runTest(dispatcher) {
        store.drafts["acme/ev1"] = BroadcastDraft(subject = "Older", bodyHtml = "<p>old</p>")
        val vm = ready(handle(BroadcastDraft(subject = "Newer", bodyHtml = "<p>new</p>")))
        assertEquals("Newer", vm.subject.text)
        assertEquals("<p>new</p>", vm.editor.html)
    }

    @Test
    fun pickingATemplateOverAnEditedDraftAsksFirst() = runTest(dispatcher) {
        val vm = ready()
        vm.pickTemplate("reminder")
        assertNull(vm.uiState.value.pendingReplace)
        assertEquals("reminder", vm.uiState.value.templateId)

        vm.onSubjectChange(TextFieldValue("My own words"))
        vm.pickTemplate("thank-you")
        assertTrue(vm.uiState.value.pendingReplace is PendingReplace.Template)
        assertEquals("My own words", vm.subject.text)
        vm.confirmReplace()
        assertEquals("Thank you for joining {{event}}", vm.subject.text)
        assertEquals(BroadcastAttendance.CHECKED_IN, vm.uiState.value.attendance)
    }

    @Test
    fun reusingAPreviousEmailLoadsEverythingAfterConfirming() = runTest(dispatcher) {
        val vm = ready()
        vm.reuse(HISTORY_ROW)
        assertTrue(vm.uiState.value.pendingReplace is PendingReplace.History)
        vm.confirmReplace()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals("Doors open at 6", vm.subject.text)
        assertEquals("<p>See you <strong>tomorrow</strong>!</p>", vm.editor.html)
        assertEquals("letter", state.design)
        assertEquals(setOf("CONFIRMED", "WAITLISTED"), state.statuses)
        assertEquals(BroadcastAttendance.CHECKED_IN, state.attendance)
        assertNull(state.pendingReplace)
    }

    @Test
    fun reusingIntoAnEmptyDraftNeedsNoConfirmation() = runTest(dispatcher) {
        val vm = ready()
        vm.onSubjectChange(TextFieldValue(""))
        vm.editor.setHtml("")
        vm.reuse(HISTORY_ROW)
        assertNull(vm.uiState.value.pendingReplace)
        assertEquals("Doors open at 6", vm.subject.text)
    }

    @Test
    fun serverFieldErrorsAndTheEmptyMessageMapOntoFields() = runTest(dispatcher) {
        repository.sendResult = AppResult.Failure(AppError.InvalidRequest("The message is empty."))
        val vm = ready()
        reviewAndConfirm(vm)
        advanceUntilIdle()
        assertEquals("The message is empty.", vm.uiState.value.bodyError)

        repository.sendResult = AppResult.Failure(AppError.InvalidRequest("Check the form.", mapOf("subject" to listOf("Subject is too long."))))
        reviewAndConfirm(vm)
        advanceUntilIdle()
        assertEquals("Subject is too long.", vm.uiState.value.subjectError)
        vm.onSubjectChange(TextFieldValue("Shorter"))
        assertNull(vm.uiState.value.subjectError)
    }

    @Test
    fun rateLimitedBlocksSendingUntilRetryAfter() = runTest(dispatcher) {
        repository.sendResult = AppResult.Failure(AppError.RateLimited("Too many broadcasts. Try again later.", retryAfterSeconds = 30))
        val vm = ready()
        reviewAndConfirm(vm)
        advanceTimeBy(5_100)
        assertEquals("Too many broadcasts. Try again later.", vm.uiState.value.message)
        assertEquals(30, vm.uiState.value.rateLimitSeconds)
        assertEquals("Too many emails. Try again in 30 s", vm.blocker())
        advanceTimeBy(30_000)
        assertEquals(0, vm.uiState.value.rateLimitSeconds)
        assertNull(vm.blocker())
    }

    @Test
    fun sendMeATestReportsTheAddress() = runTest(dispatcher) {
        val vm = ready()
        vm.sendTest()
        advanceUntilIdle()
        assertEquals("Test sent to you@example.test", vm.uiState.value.message)
        assertEquals("Update about {{event}}", repository.testCalls.single().subject)
    }

    @Test
    fun subjectPlaceholderRespectsTheLimit() = runTest(dispatcher) {
        val vm = ready()
        vm.onSubjectChange(TextFieldValue("Hi ", TextRange(3)))
        vm.insertSubjectPlaceholder("{{name}}")
        assertEquals("Hi {{name}}", vm.subject.text)
        vm.onSubjectChange(TextFieldValue("x".repeat(148)))
        vm.insertSubjectPlaceholder("{{name}}")
        assertEquals(148, vm.subject.text.length)
        vm.onSubjectChange(TextFieldValue("x".repeat(151)))
        assertEquals(148, vm.subject.text.length)
    }

    private companion object {
        const val TEMPLATE_UPDATE_BODY =
            "<p>Hello {{name}},</p><p>Here is an update about <strong>{{event}}</strong> on {{eventDate}} at {{location}}.</p>" +
                "<p>(Write your message here.)</p><p><a class=\"cta\" href=\"{{ticketLink}}\">View your ticket</a></p>" +
                "<p>See you there,<br>{{organisation}}</p>"
    }
}
