package com.twilighty.yuyu.feature.organiser

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.network.model.EventPermissionsDto
import com.twilighty.yuyu.core.network.model.OrgScheduleDto
import com.twilighty.yuyu.core.network.model.OrgSessionDto
import com.twilighty.yuyu.core.network.model.OrgTrackDto
import com.twilighty.yuyu.core.network.model.RegistrationStateDto
import com.twilighty.yuyu.feature.organiser.dayof.DayOfViewModel
import com.twilighty.yuyu.feature.organiser.dayof.DelayInput
import com.twilighty.yuyu.feature.organiser.dayof.REOPEN_CONFLICT_MESSAGE
import com.twilighty.yuyu.feature.organiser.dayof.parseDelay
import com.twilighty.yuyu.feature.organiser.schedule.toTrackUi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayOfViewModelTest : MainDispatcherTest() {
    private val events = FakeEventsRepository()
    private val dayOf = FakeDayOfRepository().apply {
        schedule = AppResult.Success(
            OrgScheduleDto(
                listOf(
                    OrgTrackDto(
                        "t1",
                        "Main stage",
                        listOf(
                            // Deliberately out of order: the app sorts by planned start like the website.
                            OrgSessionDto("s3", "Closing", "2030-05-10T20:00:00Z", "2030-05-10T20:30:00Z"),
                            OrgSessionDto("s1", "Welcome", "2030-05-10T17:00:00Z", "2030-05-10T17:30:00Z", delayMinutes = 10),
                            OrgSessionDto("s2", "Talks", "2030-05-10T18:00:00Z", "2030-05-10T19:00:00Z", delayMinutes = 5),
                        ),
                    ),
                    OrgTrackDto("t2", "Workshop room", listOf(OrgSessionDto("w1", "Workshop", "2030-05-10T18:00:00Z"))),
                ),
            ),
        )
    }

    private fun started() = DayOfViewModel(events, dayOf).also { it.start("civic-tech", "e1") }

    @Test
    fun parseDelayAcceptsOnlyWholeMinutesInRange() {
        assertEquals(DelayInput.Valid(0), parseDelay("0"))
        assertEquals(DelayInput.Valid(15), parseDelay(" 15 "))
        assertEquals(DelayInput.Valid(1440), parseDelay("1440"))
        assertTrue(parseDelay("") is DelayInput.Invalid)
        assertTrue(parseDelay("1441") is DelayInput.Invalid)
        assertTrue(parseDelay("-5") is DelayInput.Invalid)
        assertTrue(parseDelay("7.5") is DelayInput.Invalid)
        assertTrue(parseDelay("ten") is DelayInput.Invalid)
    }

    @Test
    fun delaysShiftOnlyLaterSessionsInTheSameTrackAndAddUp() {
        val tracks = (dayOf.schedule as AppResult.Success).data.toTrackUi()
        val main = tracks.first()
        assertEquals(listOf("s1", "s2", "s3"), main.sessions.map { it.session.id })
        // s1's own delay doesn't move s1; s2 is 10 behind; s3 is 10 + 5 behind.
        assertEquals(listOf(0, 10, 15), main.sessions.map { it.shiftMinutes })
        // Another track is untouched.
        assertEquals(listOf(0), tracks[1].sessions.map { it.shiftMinutes })
    }

    @Test
    fun sectionsFollowPermissions() = runTest {
        events.detail = AppResult.Success(eventDetail(permissions = EventPermissionsDto(schedule = true)))
        val vm = started()
        val state = vm.uiState.value
        assertTrue(state.showSchedule)
        assertFalse(state.showFeedback)
        assertFalse(state.showSignUps)
        assertEquals(2, state.tracks.size)

        events.detail = AppResult.Success(eventDetail(permissions = CheckInOnly))
        val none = started().uiState.value
        assertFalse(none.hasAnySection)
        assertTrue(none.tracks.isEmpty())
    }

    @Test
    fun quickChipsAndStepperStayWithinRange() = runTest {
        val vm = started()
        vm.openDelay("s2")
        assertEquals("5", vm.uiState.value.delayEditor?.input)
        vm.addDelay(10)
        assertEquals("15", vm.uiState.value.delayEditor?.input)
        vm.stepDelay(-5)
        vm.stepDelay(-5)
        vm.stepDelay(-5)
        vm.stepDelay(-5)
        assertEquals("0", vm.uiState.value.delayEditor?.input)
        vm.setDelayInput("1430")
        vm.addDelay(30)
        assertEquals("1440", vm.uiState.value.delayEditor?.input)
        vm.setDelayInput("12a")
        assertEquals("12", vm.uiState.value.delayEditor?.input)
    }

    @Test
    fun invalidDelayIsNotSent() = runTest {
        val vm = started()
        vm.openDelay("s1")
        vm.setDelayInput("2000")
        vm.saveDelay()
        assertEquals("A delay can be 0 to 1440 minutes (24 hours).", vm.uiState.value.delayEditor?.error)
        vm.setDelayInput("")
        vm.saveDelay()
        assertEquals("Enter the delay in minutes.", vm.uiState.value.delayEditor?.error)
        assertTrue(dayOf.delayCalls.isEmpty())
    }

    @Test
    fun savingADelayUpdatesLaterSessions() = runTest {
        val vm = started()
        vm.openDelay("s1")
        vm.setDelayInput("20")
        vm.saveDelay()
        assertEquals(listOf("s1" to 20), dayOf.delayCalls)
        val state = vm.uiState.value
        assertNull(state.delayEditor)
        assertEquals(listOf(0, 20, 25), state.tracks.first().sessions.map { it.shiftMinutes })
        assertEquals("Welcome: 20 min delay saved", state.message)
    }

    @Test
    fun zeroClearsTheDelay() = runTest {
        val vm = started()
        vm.openDelay("s1")
        vm.clearDelayInput()
        vm.saveDelay()
        assertEquals(listOf("s1" to 0), dayOf.delayCalls)
        assertEquals("Delay cleared for Welcome", vm.uiState.value.message)
    }

    @Test
    fun delayErrorKeepsTheSheetOpen() = runTest {
        dayOf.delayResult = AppResult.Failure(AppError.Forbidden("You do not have permission to manage the programme."))
        val vm = started()
        vm.openDelay("s1")
        vm.saveDelay()
        val editor = vm.uiState.value.delayEditor
        assertFalse(editor!!.saving)
        assertEquals("You do not have permission to manage the programme.", editor.error)
    }

    @Test
    fun closingFeedbackAsksFirst() = runTest {
        val vm = started()
        vm.onFeedbackToggle(true)
        assertTrue(vm.uiState.value.feedbackOpen)
        vm.onFeedbackToggle(false)
        assertTrue(vm.uiState.value.confirmCloseFeedback)
        assertTrue(vm.uiState.value.feedbackOpen)
        vm.confirmCloseFeedback()
        assertFalse(vm.uiState.value.feedbackOpen)
        assertEquals("Feedback form is closed", vm.uiState.value.message)
    }

    @Test
    fun openingFeedbackWithoutQuestionsExplainsWhy() = runTest {
        dayOf.feedbackResult = { AppResult.Failure(AppError.Conflict("Add a question first.")) }
        val vm = started()
        vm.onFeedbackToggle(true)
        assertFalse(vm.uiState.value.feedbackOpen)
        assertEquals("Add at least one feedback question on the website before opening the form.", vm.uiState.value.message)
    }

    @Test
    fun closeSignUpsAfterConfirm() = runTest {
        val vm = started()
        vm.askCloseSignUps(true)
        assertTrue(vm.uiState.value.confirmCloseSignUps)
        vm.closeSignUps()
        assertEquals(listOf(false), dayOf.registrationCalls)
        val reg = vm.uiState.value.registration
        assertFalse(reg.registrationOpen)
        assertTrue(reg.registrationManuallyClosed)
        assertEquals("Sign-ups closed", vm.uiState.value.message)
    }

    @Test
    fun reopenIsOnlySentAfterAManualClose() = runTest {
        events.detail = AppResult.Success(eventDetail(registrationOpen = false, manuallyClosed = false))
        val vm = started()
        vm.reopenSignUps()
        assertTrue(dayOf.registrationCalls.isEmpty())
    }

    @Test
    fun reopenConflictGetsAFriendlyMessageAndResyncs() = runTest {
        events.detail = AppResult.Success(eventDetail(registrationOpen = false, manuallyClosed = true))
        dayOf.registrationResult = { AppResult.Failure(AppError.Conflict("Registration can't be reopened.")) }
        val vm = started()
        val detailCallsBefore = events.detailCalls
        vm.reopenSignUps()
        assertEquals(listOf(true), dayOf.registrationCalls)
        assertEquals(REOPEN_CONFLICT_MESSAGE, vm.uiState.value.message)
        assertFalse(vm.uiState.value.registrationSaving)
        assertEquals(detailCallsBefore + 1, events.detailCalls)
    }

    @Test
    fun reopenWhoseCutoffPassedSaysSignUpsStayClosed() = runTest {
        events.detail = AppResult.Success(eventDetail(registrationOpen = false, manuallyClosed = true))
        dayOf.registrationResult = {
            AppResult.Success(RegistrationStateDto(registrationOpen = false, registrationClosedReason = "Registration has closed."))
        }
        val vm = started()
        vm.reopenSignUps()
        assertEquals("The original closing time has already passed, so sign-ups stay closed.", vm.uiState.value.message)
        assertFalse(vm.uiState.value.registration.registrationManuallyClosed)
    }
}
