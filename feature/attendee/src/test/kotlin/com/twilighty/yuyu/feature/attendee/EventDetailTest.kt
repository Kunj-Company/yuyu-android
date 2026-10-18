package com.twilighty.yuyu.feature.attendee

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import com.twilighty.yuyu.core.network.model.MyRsvpDto
import com.twilighty.yuyu.feature.attendee.event.RegisterCta
import com.twilighty.yuyu.feature.attendee.event.RichText
import com.twilighty.yuyu.feature.attendee.event.delayLabel
import com.twilighty.yuyu.feature.attendee.event.registerCta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDetailTest {
    private val fixture = Fixtures.publicEvent()
    private val open = fixture.copy(registration = fixture.registration.copy(myRsvp = null))

    @Test
    fun registerBarStates() {
        assertEquals(RegisterCta.Registered("tkEXAMPLEownerTicketToken01"), registerCta(fixture))
        assertEquals(RegisterCta.Open("Register — it's free", waitlist = false, approval = false), registerCta(open))
        assertEquals("Join the waitlist", (registerCta(open.copy(event = open.event.copy(registrationFull = true))) as RegisterCta.Open).label)
        assertEquals("Request to join", (registerCta(open.copy(registration = open.registration.copy(requiresApproval = true))) as RegisterCta.Open).label)
        assertEquals(
            RegisterCta.Closed("Registration closed 2 hours before the start."),
            registerCta(open.copy(event = open.event.copy(registrationOpen = false, registrationClosedReason = "Registration closed 2 hours before the start."))),
        )
        assertEquals(
            RegisterCta.Waiting("WAITLISTED"),
            registerCta(open.copy(registration = open.registration.copy(myRsvp = MyRsvpDto("r", "WAITLISTED", null)))),
        )
        // A cancelled RSVP can register again.
        assertTrue(registerCta(open.copy(registration = open.registration.copy(myRsvp = MyRsvpDto("r", "CANCELLED", null)))) is RegisterCta.Open)
    }

    @Test
    fun liveDelayIsShownFromTheFixture() {
        val late = fixture.schedule.flatMap { it.sessions }.single { it.delayMinutes > 0 }
        assertEquals("Running 10 min late", delayLabel(late.delayMinutes))
    }

    @Test
    fun richTextKeepsFormattingAndOnlySafeLinks() {
        val html = "<h2>Plan</h2><p>Bring <strong>a laptop</strong> &amp; snacks.</p><ul><li>One</li><li>Two</li></ul>" +
            "<p><a href=\"https://example.org/x\">Map</a> <a href=\"javascript:alert(1)\">bad</a></p>"
        val text = RichText.toAnnotatedString(html, RichText.Styles(linkColor = Color.Blue))
        assertEquals("Plan\n\nBring a laptop & snacks.\n\n• One\n• Two\n\nMap bad", text.text)
        val links = text.getLinkAnnotations(0, text.length).map { (it.item as LinkAnnotation.Url).url }
        assertEquals(listOf("https://example.org/x"), links)
        assertFalse(RichText.isAllowedLink("javascript:alert(1)"))
    }

    @Test
    fun plainTextDescriptionStaysAsIs() {
        assertEquals("Build something useful for your neighbourhood.", RichText.toAnnotatedString(fixture.event.description!!, RichText.Styles(Color.Blue)).text)
    }
}
