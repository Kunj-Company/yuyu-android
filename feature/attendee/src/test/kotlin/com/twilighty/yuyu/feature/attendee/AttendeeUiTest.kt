package com.twilighty.yuyu.feature.attendee

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.RsvpStatus
import com.twilighty.yuyu.feature.attendee.form.AnswerState
import com.twilighty.yuyu.feature.attendee.form.DynamicForm
import com.twilighty.yuyu.feature.attendee.form.fieldTag
import com.twilighty.yuyu.feature.attendee.ticket.TicketContext
import com.twilighty.yuyu.feature.attendee.ticket.TicketScreen
import com.twilighty.yuyu.feature.attendee.ticket.TicketUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AttendeeUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun formRendersAllTenTypesAndShowIfRevealsDependent() {
        compose.setContent {
            YuyuTheme(darkTheme = false) {
                var answers by remember { mutableStateOf(AnswerState()) }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DynamicForm(fields = allTypeFields, state = answers, errors = mapOf("company" to "Use at least 2 characters (you have 1)."), onStateChange = { answers = it })
                }
            }
        }
        val visibleAtStart = allTypeFields.map { it.key } - "dietary"
        visibleAtStart.forEach { key -> compose.onNodeWithTag(fieldTag(key)).assertExists() }
        // Hidden by showIf until "Staying for dinner?" is ticked.
        compose.onNodeWithTag(fieldTag("dietary")).assertDoesNotExist()
        compose.onNodeWithText("Use at least 2 characters (you have 1).").assertExists()
        compose.onNodeWithText("Choose a date from 1 Jan 1900 to 31 Dec 2020.").assertExists()

        compose.onNodeWithText("Staying for dinner?").performScrollTo().performClick()
        compose.onNodeWithTag(fieldTag("dietary")).assertExists()
        compose.onNodeWithText("Choose up to 2.").assertExists()

        // Untick: hidden again.
        compose.onNodeWithText("Staying for dinner?").performScrollTo().performClick()
        compose.onNodeWithTag(fieldTag("dietary")).assertDoesNotExist()
    }

    @Test
    fun choosingOtherShowsItsTextInput() {
        compose.setContent {
            YuyuTheme(darkTheme = true) {
                var answers by remember { mutableStateOf(AnswerState()) }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DynamicForm(fields = allTypeFields, state = answers, errors = emptyMap(), onStateChange = { answers = it })
                }
            }
        }
        compose.onNodeWithTag("other-heard").assertDoesNotExist()
        compose.onNodeWithText("Other").performScrollTo().performClick()
        compose.onNodeWithTag("other-heard").assertExists()
    }

    private val event = Fixtures.registrations().upcoming.first().event

    private fun ticketState(checkedInAt: String?) = TicketUiState(
        loading = false,
        context = TicketContext("r1", RsvpStatus.CONFIRMED, checkedInAt, canCancel = true, attendeeName = "Ada Lovelace", event = event),
        checkedIn = checkedInAt != null,
        checkedInAt = checkedInAt,
    )

    private fun showTicket(state: TicketUiState, dark: Boolean = false) {
        compose.setContent {
            YuyuTheme(darkTheme = dark) {
                TicketScreen(
                    token = "tkEXAMPLEownerTicketToken01",
                    state = state,
                    onBack = {},
                    onOpenEvent = { _, _ -> },
                    onAskCancel = {},
                    onConfirmCancel = {},
                    onDismissCancel = {},
                    onAnimationDone = {},
                    onMessageShown = {},
                )
            }
        }
    }

    @Test
    fun checkedInTicketRendersTornWithoutQr() {
        showTicket(ticketState("2030-05-10T17:04:00.000Z"), dark = true)
        compose.onNodeWithTag("ticket-torn").assertExists()
        compose.onNodeWithTag("ticket-qr").assertDoesNotExist()
        compose.onNodeWithText("You're checked in").assertExists()
        compose.onNodeWithText("Checked in at 6:04 PM").assertExists()
        compose.onNodeWithText("Cancel registration").assertDoesNotExist()
    }

    @Test
    fun liveTicketShowsQrStub() {
        showTicket(ticketState(null))
        compose.onNodeWithTag("ticket-qr").assertExists()
        compose.onNodeWithTag("ticket-torn").assertDoesNotExist()
        compose.onNodeWithText("You're in, Ada!").assertIsDisplayed()
        compose.onNodeWithText("Spring Hack Night").assertExists()
    }

    @Test
    fun liveCheckInTearsTheStubAndCelebrates() {
        var state by mutableStateOf(ticketState(null))
        var animationDone = false
        compose.setContent {
            YuyuTheme(darkTheme = false) {
                TicketScreen(
                    token = "tkEXAMPLEownerTicketToken01",
                    state = state,
                    onBack = {},
                    onOpenEvent = { _, _ -> },
                    onAskCancel = {},
                    onConfirmCancel = {},
                    onDismissCancel = {},
                    onAnimationDone = { animationDone = true },
                    onMessageShown = {},
                )
            }
        }
        compose.onNodeWithTag("ticket-qr").assertExists()

        state = state.copy(checkedIn = true, checkedInAt = "2030-05-10T17:04:00.000Z", justCheckedIn = true)
        compose.waitForIdle()

        compose.onNodeWithTag("ticket-torn").assertExists()
        compose.onNodeWithTag("ticket-qr").assertDoesNotExist()
        compose.onNodeWithText("Checked in at 6:04 PM").assertIsDisplayed()
        compose.onNodeWithText("You're checked in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel registration").assertDoesNotExist()
        assertTrue(animationDone)
    }
}
