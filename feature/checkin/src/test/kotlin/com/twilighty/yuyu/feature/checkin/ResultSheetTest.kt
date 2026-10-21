package com.twilighty.yuyu.feature.checkin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.LabelValueDto
import com.twilighty.yuyu.feature.checkin.domain.GateInfo
import com.twilighty.yuyu.feature.checkin.domain.GuestPreview
import com.twilighty.yuyu.feature.checkin.flow.SheetState
import com.twilighty.yuyu.feature.checkin.ui.ResultSheetContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResultSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val guest = GuestPreview(
        rsvpId = "r1",
        displayName = "Ada Lovelace",
        email = "ada@example.org",
        status = "CONFIRMED",
        alreadyCheckedIn = false,
        checkedInAt = null,
        checkInDetails = listOf(LabelValueDto("T-shirt", "M")),
        registrationDetails = listOf(LabelValueDto("Company", "Analytical Engines")),
    )

    private var confirms = 0
    private var undos = 0
    private var dismissals = 0

    private fun show(initial: SheetState): (SheetState) -> Unit {
        var state by mutableStateOf(initial)
        compose.setContent {
            YuyuTheme(darkTheme = false) {
                ResultSheetContent(state, onConfirm = { confirms++ }, onUndo = { undos++ }, onDismiss = { dismissals++ })
            }
        }
        return { state = it }
    }

    private fun clickButton(label: String) =
        compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()

    @Test
    fun readyShowsGuestDetailsAndOneBigCheckInButton() {
        show(SheetState.Ready(guest, override = false))
        compose.onNodeWithText("Ada Lovelace").assertIsDisplayed()
        compose.onNodeWithText("Confirmed").assertIsDisplayed()
        compose.onNodeWithText("M").assertIsDisplayed()
        compose.onNodeWithText("Analytical Engines").assertExists()
        clickButton("Check in")
        assertEquals(1, confirms)
    }

    @Test
    fun overrideAsksToCheckInAnyway() {
        val waitlisted = guest.copy(status = "WAITLISTED", gate = GateInfo(false, "Guest is on the waitlist", needsForce = true))
        show(SheetState.Ready(waitlisted, override = true, reason = "Guest is on the waitlist"))
        compose.onNodeWithText("On the waitlist").assertIsDisplayed()
        compose.onNodeWithText("Guest is on the waitlist").assertIsDisplayed()
        clickButton("Check in anyway")
        assertEquals(1, confirms)
    }

    @Test
    fun admittedShowsSuccessPanelWithUndo() {
        val update = show(SheetState.Admitted(guest, "2026-10-18T10:00:00Z", offline = false))
        compose.onNodeWithText("Checked in").assertIsDisplayed()
        clickButton("Undo")
        assertEquals(1, undos)
        clickButton("Next guest")
        assertEquals(1, dismissals)

        update(SheetState.Admitted(guest, "2026-10-18T10:00:00Z", offline = true))
        compose.onNodeWithText("saved offline", substring = true).assertIsDisplayed()

        update(SheetState.Admitted(guest, null, offline = false, undoing = true))
        compose.onNode(hasText("Next guest") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun alreadyCheckedInUsesTheWarningStyleWithTime() {
        show(SheetState.AlreadyIn(guest, "2026-10-18T09:30:00Z"))
        compose.onNodeWithText("Already checked in").assertIsDisplayed()
        compose.onNodeWithText("Checked in at", substring = true).assertIsDisplayed()
    }

    @Test
    fun blockedShowsTheReasonAndScanAgain() {
        show(SheetState.Blocked(guest.copy(status = "REJECTED"), "Can't check in", "This registration was rejected."))
        compose.onNodeWithText("Can't check in").assertIsDisplayed()
        compose.onNodeWithText("This registration was rejected.").assertIsDisplayed()
        compose.onNodeWithText("Rejected").assertIsDisplayed()
        clickButton("Scan again")
        assertEquals(1, dismissals)
    }

    @Test
    fun readyErrorAndUndoneStates() {
        val update = show(SheetState.Ready(guest, override = false, error = "Too many check-in requests."))
        compose.onNodeWithText("Too many check-in requests.").assertIsDisplayed()
        update(SheetState.Undone(guest))
        compose.onNodeWithText("Check-in undone", substring = true).assertIsDisplayed()
    }

    private fun showFromLoading(reducedMotion: Boolean): (SheetState) -> Unit {
        compose.mainClock.autoAdvance = false
        var state by mutableStateOf<SheetState>(SheetState.Loading("Checking ticket…"))
        compose.setContent {
            YuyuTheme(darkTheme = false) {
                CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                    ResultSheetContent(state, onConfirm = {}, onUndo = {}, onDismiss = {})
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, compose.onAllNodesWithContentDescription("Checking ticket…").fetchSemanticsNodes().size)
        return { state = it }
    }

    @Test
    fun resultIsReadableWithinTheMotionBudget() {
        val update = showFromLoading(reducedMotion = false)
        update(SheetState.Admitted(guest, null, offline = false))
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Checked in").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithContentDescription("Checking ticket…").fetchSemanticsNodes().size)
    }

    @Test
    fun reducedMotionSwapsResultsImmediately() {
        val update = showFromLoading(reducedMotion = true)
        update(SheetState.Blocked(null, "Ticket not found", "This code doesn't match a ticket."))
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Ticket not found").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithContentDescription("Checking ticket…").fetchSemanticsNodes().size)
    }
}
