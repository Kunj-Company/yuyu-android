package com.twilighty.yuyu.feature.attendee

import com.twilighty.yuyu.core.network.model.FieldSettingsDto
import com.twilighty.yuyu.core.network.model.FieldType
import com.twilighty.yuyu.core.network.model.ShowIfDto
import com.twilighty.yuyu.feature.attendee.form.AnswerState
import com.twilighty.yuyu.feature.attendee.form.FormLogic
import com.twilighty.yuyu.feature.attendee.form.FormRules
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormLogicTest {
    private val fields = allTypeFields

    @Test
    fun showIfHidesDependentUntilTriggerMatches() {
        val empty = AnswerState()
        assertFalse("dietary" in FormLogic.visibleKeys(fields, empty))
        val yes = FormLogic.setBool(fields, empty, "dinner", true)
        assertTrue("dietary" in FormLogic.visibleKeys(fields, yes))
    }

    @Test
    fun hidingAFieldDropsItsAnswers() {
        var s = FormLogic.setBool(fields, AnswerState(), "dinner", true)
        s = FormLogic.toggleOption(fields, s, "dietary", "Vegan")
        s = FormLogic.setOtherText(fields, s, "dietary", "No mushrooms")
        assertEquals(listOf("Vegan"), s.multi["dietary"])
        s = FormLogic.setBool(fields, s, "dinner", false)
        assertNull(s.multi["dietary"])
        assertNull(s.others["dietary"])
        assertFalse("dietary" in FormLogic.buildPayload(fields, s))
    }

    @Test
    fun chainedShowIfHidesGrandchildrenWhenTriggerHides() {
        val chain = listOf(
            field("a", FieldType.CHECKBOX),
            field("b", FieldType.RADIO, options = listOf("x", "y"), showIf = ShowIfDto("a", listOf("Yes"))),
            field("c", FieldType.TEXT, showIf = ShowIfDto("b", listOf("x"))),
        )
        var s = FormLogic.setBool(chain, AnswerState(), "a", true)
        s = FormLogic.setText(chain, s, "b", "x")
        s = FormLogic.setText(chain, s, "c", "hello")
        assertEquals(setOf("a", "b", "c"), FormLogic.visibleKeys(chain, s))
        s = FormLogic.setBool(chain, s, "a", false)
        assertEquals(setOf("a"), FormLogic.visibleKeys(chain, s))
        assertNull(s.text["b"])
        assertNull(s.text["c"])
    }

    @Test
    fun brokenRuleIsIgnored() {
        val broken = listOf(field("late", FieldType.TEXT, showIf = ShowIfDto("missing", listOf("Yes"))))
        assertEquals(setOf("late"), FormLogic.visibleKeys(broken, AnswerState()))
    }

    @Test
    fun otherSendsTypedTextForSingleChoiceAndExtraItemForMulti() {
        var s = FormLogic.setOtherChosen(fields, AnswerState(), "heard", true)
        s = FormLogic.setOtherText(fields, s, "heard", "  A poster ")
        s = FormLogic.setBool(fields, s, "dinner", true)
        s = FormLogic.toggleOption(fields, s, "dietary", "Vegan")
        s = FormLogic.setOtherChosen(fields, s, "dietary", true)
        s = FormLogic.setOtherText(fields, s, "dietary", "Halal")
        val payload = FormLogic.buildPayload(fields, s)
        assertEquals(JsonPrimitive("A poster"), payload["heard"])
        assertEquals(JsonArray(listOf(JsonPrimitive("Vegan"), JsonPrimitive("Halal"))), payload["dietary"])
        assertEquals(JsonPrimitive(true), payload["dinner"])
    }

    @Test
    fun choosingAListedOptionClearsOther() {
        var s = FormLogic.setOtherChosen(fields, AnswerState(), "heard", true)
        s = FormLogic.setOtherText(fields, s, "heard", "Poster")
        s = FormLogic.setText(fields, s, "heard", "Friend")
        assertEquals(JsonPrimitive("Friend"), FormLogic.buildPayload(fields, s)["heard"])
    }

    @Test
    fun otherWithoutTextIsAnError() {
        val s = FormLogic.setOtherChosen(fields, AnswerState(), "heard", true)
        assertEquals("Type your answer for “Other”.", FormLogic.validate(fields, s)["heard"])
    }

    @Test
    fun maxSelectionsCountsOtherAndBlocksMore() {
        var s = FormLogic.setBool(fields, AnswerState(), "dinner", true)
        s = FormLogic.toggleOption(fields, s, "dietary", "Vegan")
        s = FormLogic.setOtherChosen(fields, s, "dietary", true)
        val dietary = fields.first { it.key == "dietary" }
        assertEquals(2, FormLogic.selectionCount(dietary, s))
        assertFalse(FormLogic.canSelectMore(dietary, s))
        val blocked = FormLogic.toggleOption(fields, s, "dietary", "Vegetarian")
        assertEquals(listOf("Vegan"), blocked.multi["dietary"])
        // Unticking still works.
        val fewer = FormLogic.toggleOption(fields, s, "dietary", "Vegan")
        assertEquals(emptyList<String>(), fewer.multi["dietary"])
    }

    @Test
    fun validationMirrorsSettings() {
        val required = listOf(
            field("name", FieldType.TEXT, required = true, settings = FieldSettingsDto(minLength = 3)),
            field("email", FieldType.EMAIL),
            field("phone", FieldType.PHONE),
            field("n", FieldType.NUMBER, settings = FieldSettingsDto(min = 1.0, max = 5.0)),
            field("d", FieldType.DATE, settings = FieldSettingsDto(minDate = "2026-01-01")),
            field("terms", FieldType.CHECKBOX, required = true),
            field("pick", FieldType.MULTI_SELECT, required = true, options = listOf("a", "b")),
        )
        var s = AnswerState()
        var errors = FormLogic.validate(required, s)
        assertEquals("Please answer this question.", errors["name"])
        assertEquals("Please tick this box to continue.", errors["terms"])
        assertEquals("Please choose at least one option.", errors["pick"])
        assertNull(errors["email"])

        s = s.copy(
            text = mapOf("name" to "Al", "email" to "nope", "phone" to "+91 12345", "n" to "9", "d" to "2025-05-05"),
            bools = mapOf("terms" to true),
            multi = mapOf("pick" to listOf("a")),
        )
        errors = FormLogic.validate(required, s)
        assertEquals("Use at least 3 characters (you have 2).", errors["name"])
        assertEquals("Enter a valid email address, like name@example.com.", errors["email"])
        assertEquals("Enter a valid 10-digit Indian mobile number.", errors["phone"])
        assertEquals("Enter a number from 1 to 5.", errors["n"])
        assertEquals("Choose a date on or after 1 Jan 2026.", errors["d"])

        s = s.copy(text = mapOf("name" to "Ada", "email" to "ada@example.org", "phone" to "+44 7700 900123", "n" to "2", "d" to "2026-03-01"))
        assertEquals(emptyMap<String, String>(), FormLogic.validate(required, s))
    }

    @Test
    fun payloadMatchesWebShapes() {
        val s = AnswerState(
            text = mapOf("company" to " Acme ", "guests" to "2", "birthday" to "1990-04-01", "tshirt" to "M", "bio" to ""),
            bools = mapOf("dinner" to false),
        )
        val payload = FormLogic.buildPayload(fields, s)
        assertEquals(JsonPrimitive("Acme"), payload["company"])
        // NUMBER and DATE are sent as the typed strings, like the web inputs.
        assertEquals(JsonPrimitive("2"), payload["guests"])
        assertEquals(JsonPrimitive("1990-04-01"), payload["birthday"])
        assertEquals(JsonPrimitive("M"), payload["tshirt"])
        assertEquals(JsonPrimitive(false), payload["dinner"])
        assertFalse("bio" in payload)
        assertFalse("dietary" in payload)
    }

    @Test
    fun feedbackRulesIgnoreLogicAndOther() {
        val s = FormLogic.setOtherChosen(fields, AnswerState(), "heard", true, FormRules.Feedback)
        assertTrue("dietary" in FormLogic.visibleKeys(fields, s, FormRules.Feedback))
        assertFalse(FormLogic.allowsOther(fields.first { it.key == "heard" }, FormRules.Feedback))
        assertNull(FormLogic.buildPayload(fields, s, FormRules.Feedback)["heard"])
    }

    @Test
    fun serverMessageMapsToQuestionByLabel() {
        assertEquals("dietary", FormLogic.fieldForServerMessage(fields, "Dietary needs contains an invalid option."))
        assertNull(FormLogic.fieldForServerMessage(fields, "This event is not open for RSVP."))
    }
}
