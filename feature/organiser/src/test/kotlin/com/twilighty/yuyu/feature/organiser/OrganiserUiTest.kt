package com.twilighty.yuyu.feature.organiser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.RegistrationStateDto
import com.twilighty.yuyu.feature.organiser.dayof.SignUpsSection
import com.twilighty.yuyu.feature.organiser.guests.GuestRow
import com.twilighty.yuyu.feature.organiser.guests.GuestRowActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OrganiserUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showRow(status: String, canManage: Boolean, onApprove: () -> Unit = {}) {
        val g = guest("r1", status, name = "Linus Member")
        compose.setContent {
            YuyuTheme(darkTheme = false) {
                GuestRow(
                    guest = g,
                    timezone = "Europe/London",
                    actions = GuestRowActions.of(g, canManage),
                    busy = false,
                    onClick = {},
                    onApprove = onApprove,
                    onDecline = {},
                    onPromote = {},
                    onRemove = {},
                )
            }
        }
    }

    @Test
    fun pendingRowOffersApproveDeclineAndRemoveToManagers() {
        var approved = 0
        showRow("PENDING_APPROVAL", canManage = true, onApprove = { approved++ })
        compose.onNodeWithText("Approve").assertIsDisplayed().performClick()
        compose.onNodeWithText("Decline").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Linus Member").assertIsDisplayed()
        assertEquals(1, approved)
    }

    @Test
    fun checkInOnlyHelpersSeeNoLifecycleControls() {
        showRow("PENDING_APPROVAL", canManage = false)
        compose.onNodeWithText("Linus Member").assertIsDisplayed()
        compose.onNodeWithText("To approve").assertIsDisplayed()
        compose.onNodeWithText("Approve").assertDoesNotExist()
        compose.onNodeWithText("Decline").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove Linus Member").assertDoesNotExist()
    }

    @Test
    fun waitlistRowForCheckInOnlyHasNoGiveASpot() {
        showRow("WAITLISTED", canManage = false)
        compose.onNodeWithText("Give a spot").assertDoesNotExist()
    }

    private fun showSignUps(registration: RegistrationStateDto) {
        compose.setContent {
            YuyuTheme(darkTheme = false) {
                SignUpsSection(registration = registration, timezone = "Europe/London", saving = false, onClose = {}, onReopen = {})
            }
        }
    }

    @Test
    fun reopenIsShownWhenSignUpsWereClosedFromTheApp() {
        showSignUps(RegistrationStateDto(registrationOpen = false, registrationClosedReason = "Registration has closed.", registrationManuallyClosed = true))
        compose.onNodeWithText("Reopen sign-ups").assertIsDisplayed()
        compose.onNodeWithText("Close sign-ups now").assertDoesNotExist()
        compose.onNodeWithText("Registration has closed.").assertIsDisplayed()
    }

    @Test
    fun reopenIsHiddenWhenSignUpsClosedOnTheirOwn() {
        showSignUps(RegistrationStateDto(registrationOpen = false, registrationClosedReason = "Registration has closed.", registrationManuallyClosed = false))
        compose.onNodeWithText("Reopen sign-ups").assertDoesNotExist()
        compose.onNodeWithText("To open sign-ups again, change the sign-up window on the website.").assertIsDisplayed()
    }

    @Test
    fun openSignUpsOfferCloseButNotReopen() {
        showSignUps(RegistrationStateDto(registrationOpen = true, registrationClosesAt = "2030-05-10T16:00:00Z"))
        compose.onNodeWithText("Close sign-ups now").assertIsDisplayed()
        compose.onNodeWithText("Reopen sign-ups").assertDoesNotExist()
        compose.onNodeWithText("Closes Fri 10 May, 5:00 PM BST").assertIsDisplayed()
    }
}
