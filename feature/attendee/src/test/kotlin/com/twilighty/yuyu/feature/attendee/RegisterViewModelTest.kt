package com.twilighty.yuyu.feature.attendee

import androidx.lifecycle.SavedStateHandle
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.network.model.FieldType
import com.twilighty.yuyu.core.network.model.RsvpResultDto
import com.twilighty.yuyu.core.network.model.UserDto
import com.twilighty.yuyu.feature.attendee.event.RegisterCta
import com.twilighty.yuyu.feature.attendee.form.FormLogic
import com.twilighty.yuyu.feature.attendee.register.RegisterOutcome
import com.twilighty.yuyu.feature.attendee.register.RegisterViewModel
import com.twilighty.yuyu.feature.attendee.register.toOutcome
import com.twilighty.yuyu.feature.attendee.ticket.RecentTicketStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RegisterViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val events = FakePublicEventsRepository()
    private val me = FakeMeRepository()
    private val recent = RecentTicketStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun openEvent() = Fixtures.publicEvent().let { it.copy(registration = it.registration.copy(myRsvp = null)) }

    private fun viewModel(user: UserDto? = UserDto("u1", name = "Ada Lovelace", email = "ada@example.org")) = RegisterViewModel(
        SavedStateHandle(mapOf("orgSlug" to "civic-tech", "eventSlug" to "spring-hack-night")),
        events,
        FakeAuthRepository(user),
        me,
        recent,
        CoroutineScope(dispatcher),
    )

    @Test
    fun fixtureFormLoadsWithShowIfHidingDietary() {
        events.event = AppResult.Success(openEvent())
        val vm = viewModel()
        val s = vm.uiState.value
        assertFalse(s.loading)
        assertNull(s.blocked)
        assertEquals(listOf("attending_dinner", "dietary"), s.fields.map { it.key })
        assertEquals(setOf("attending_dinner"), FormLogic.visibleKeys(s.fields, s.answers))
        // Signed in with a name: identity hidden and prefilled.
        assertFalse(s.needsName)
        assertEquals("Ada Lovelace", s.name)
    }

    @Test
    fun answeringTriggerShowsDependentAndHidingDropsAnswers() {
        events.event = AppResult.Success(openEvent())
        val vm = viewModel()
        val fields = vm.uiState.value.fields
        vm.onAnswersChange(FormLogic.setBool(fields, vm.uiState.value.answers, "attending_dinner", true))
        vm.onAnswersChange(FormLogic.toggleOption(fields, vm.uiState.value.answers, "dietary", "Vegan"))
        assertTrue("dietary" in FormLogic.visibleKeys(fields, vm.uiState.value.answers))
        vm.onAnswersChange(FormLogic.setBool(fields, vm.uiState.value.answers, "attending_dinner", false))
        assertNull(vm.uiState.value.answers.multi["dietary"])
    }

    @Test
    fun submitSendsWebAnswersShape() {
        events.event = AppResult.Success(openEvent())
        val vm = viewModel()
        val fields = vm.uiState.value.fields
        var a = FormLogic.setBool(fields, vm.uiState.value.answers, "attending_dinner", true)
        a = FormLogic.toggleOption(fields, a, "dietary", "Vegetarian")
        a = FormLogic.setOtherChosen(fields, a, "dietary", true)
        a = FormLogic.setOtherText(fields, a, "dietary", "No nuts")
        vm.onAnswersChange(a)
        vm.submit()
        val request = events.rsvpRequests.single()
        assertEquals("civic-tech", request.orgSlug)
        assertEquals("spring-hack-night", request.eventSlug)
        assertNull(request.guestEmail)
        assertEquals("Ada Lovelace", request.name)
        assertEquals(JsonPrimitive(true), request.answers["attending_dinner"])
        assertEquals(JsonArray(listOf(JsonPrimitive("Vegetarian"), JsonPrimitive("No nuts"))), request.answers["dietary"])
    }

    @Test
    fun clientValidationBlocksSubmitAndCountsProblems() {
        events.event = AppResult.Success(openEvent().let { e ->
            e.copy(registration = e.registration.copy(fields = listOf(field("terms", FieldType.CHECKBOX, label = "I agree", required = true))))
        })
        val vm = viewModel()
        vm.submit()
        assertTrue(events.rsvpRequests.isEmpty())
        assertEquals("Please tick this box to continue.", vm.uiState.value.errors["terms"])
        assertEquals(1, vm.uiState.value.problemCount)
        // Fixing the answer clears its error.
        vm.onAnswersChange(FormLogic.setBool(vm.uiState.value.fields, vm.uiState.value.answers, "terms", true))
        assertTrue(vm.uiState.value.errors.isEmpty())
    }

    @Test
    fun guestMustGiveNameAndEmail() {
        events.event = AppResult.Success(openEvent())
        val vm = viewModel(user = null)
        assertTrue(vm.uiState.value.isGuest)
        vm.submit()
        assertEquals("Please enter your name.", vm.uiState.value.nameError)
        assertEquals("Please enter your email address.", vm.uiState.value.emailError)
        vm.onNameChange("Grace")
        vm.onEmailChange("grace@example.org")
        vm.submit()
        assertEquals("grace@example.org", events.rsvpRequests.single().guestEmail)
    }

    @Test
    fun confirmedOutcomeCarriesTicketAndCachesContext() {
        events.event = AppResult.Success(openEvent())
        val vm = viewModel()
        vm.submit()
        assertEquals(RegisterOutcome.Confirmed("tkFRESHticketToken0001"), vm.uiState.value.outcome)
        assertNotNull(recent.get("tkFRESHticketToken0001"))
        assertEquals(1, me.registrationCalls)
    }

    @Test
    fun waitlistAndPendingOutcomes() {
        assertEquals(RegisterOutcome.Waitlisted, RsvpResultDto("r", "WAITLISTED", null).toOutcome())
        assertEquals(RegisterOutcome.PendingApproval, RsvpResultDto("r", "PENDING_APPROVAL", null).toOutcome())
        assertEquals(RegisterOutcome.Confirmed("t"), RsvpResultDto("r", "CONFIRMED", "t").toOutcome())

        events.event = AppResult.Success(openEvent())
        events.rsvp = { AppResult.Success(RsvpResultDto("r2", "WAITLISTED", null)) }
        val vm = viewModel()
        vm.submit()
        assertEquals(RegisterOutcome.Waitlisted, vm.uiState.value.outcome)

        events.rsvp = { AppResult.Success(RsvpResultDto("r3", "PENDING_APPROVAL", null)) }
        val vm2 = viewModel()
        vm2.submit()
        assertEquals(RegisterOutcome.PendingApproval, vm2.uiState.value.outcome)
    }

    @Test
    fun serverFieldErrorsLandOnTheirFields() {
        events.event = AppResult.Success(openEvent())
        events.rsvp = { AppResult.Failure(AppError.InvalidRequest("Invalid input.", mapOf("name" to listOf("Enter a valid name"), "dietary" to listOf("Too many")))) }
        val vm = viewModel(user = null)
        vm.onNameChange("x")
        vm.onEmailChange("x@example.org")
        vm.submit()
        val s = vm.uiState.value
        assertEquals("Enter a valid name", s.nameError)
        assertEquals("Too many", s.errors["dietary"])
        assertNull(s.formError)
        assertFalse(s.submitting)
    }

    @Test
    fun serverAnswerMessageMapsByLabelOtherwiseFormError() {
        events.event = AppResult.Success(openEvent())
        events.rsvp = { AppResult.Failure(AppError.InvalidRequest("Staying for dinner? must be checked.")) }
        val vm = viewModel()
        vm.submit()
        assertEquals("Staying for dinner? must be checked.", vm.uiState.value.errors["attending_dinner"])

        events.rsvp = { AppResult.Failure(AppError.Conflict("This event is not open for RSVP.")) }
        vm.submit()
        assertEquals("This event is not open for RSVP.", vm.uiState.value.formError)
    }

    @Test
    fun alreadyRegisteredOrClosedBlocksTheForm() {
        events.event = AppResult.Success(Fixtures.publicEvent())
        assertEquals(RegisterCta.Registered("tkEXAMPLEownerTicketToken01"), viewModel().uiState.value.blocked)

        val closed = openEvent().let { it.copy(event = it.event.copy(registrationOpen = false, registrationClosedReason = "Registration has closed.")) }
        events.event = AppResult.Success(closed)
        assertEquals(RegisterCta.Closed("Registration has closed."), viewModel().uiState.value.blocked)
    }
}
