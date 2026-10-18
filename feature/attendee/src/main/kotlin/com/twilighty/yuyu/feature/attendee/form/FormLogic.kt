package com.twilighty.yuyu.feature.attendee.form

import androidx.compose.runtime.Immutable
import com.twilighty.yuyu.core.network.model.FieldType
import com.twilighty.yuyu.core.network.model.RegistrationFieldDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** The free-text answer behind a choice question's "Other" option. */
@Immutable
data class OtherAnswer(val chosen: Boolean = false, val text: String = "")

/**
 * Raw form state, as the controls hold it. Text-like and single-choice answers
 * live in [text], multi-select answers in [multi], yes/no answers in [bools];
 * "Other" answers are kept apart in [others] and only merged when submitting
 * (mirrors `components/rsvp/registrationAnswers.ts` on the web).
 */
@Immutable
data class AnswerState(
    val text: Map<String, String> = emptyMap(),
    val multi: Map<String, List<String>> = emptyMap(),
    val bools: Map<String, Boolean> = emptyMap(),
    val others: Map<String, OtherAnswer> = emptyMap(),
) {
    fun without(keys: Set<String>): AnswerState = if (keys.isEmpty()) this else AnswerState(
        text = text - keys,
        multi = multi - keys,
        bools = bools - keys,
        others = others - keys,
    )
}

/**
 * What the server behind a form understands. Registration (`/api/rsvp`)
 * supports "Other" answers and "show only if" logic; feedback (`/api/feedback`)
 * supports neither: it rejects answers that aren't options and requires every
 * required question regardless of logic, so both are switched off there.
 */
@Immutable
data class FormRules(val allowOther: Boolean = true, val conditionalLogic: Boolean = true) {
    companion object {
        val Registration = FormRules()
        val Feedback = FormRules(allowOther = false, conditionalLogic = false)
    }
}

/**
 * Pure registration-form logic: visibility ("show only if"), "Other" answers,
 * client-side validation mirroring the server (`lib/rsvpCore.ts`
 * `validateAndNormalizeAnswers`) and the exact `answers` payload the web form
 * sends. The server stays the authority.
 */
object FormLogic {
    const val OTHER_MAX_LENGTH = 200
    private val DEFAULT_MAX_LENGTH = mapOf(FieldType.TEXT to 200, FieldType.TEXTAREA to 5000)
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    private val SUPPORTS_OTHER = setOf(FieldType.RADIO, FieldType.SELECT, FieldType.MULTI_SELECT)

    fun allowsOther(field: RegistrationFieldDto, rules: FormRules = FormRules.Registration): Boolean =
        rules.allowOther && field.settings.allowOther == true && field.type in SUPPORTS_OTHER && field.options.isNotEmpty()

    private fun isOption(field: RegistrationFieldDto, value: String) = field.options.isEmpty() || value in field.options

    /**
     * The value sent for one question, or null when it has no answer. "Other"
     * sends the typed text itself (single choice: the string; multi-select: one
     * extra array item). Selections that are no longer options are dropped.
     * NUMBER and DATE are sent as the typed strings, CHECKBOX as a boolean, like the web form.
     */
    fun submittedValue(field: RegistrationFieldDto, state: AnswerState, rules: FormRules = FormRules.Registration): JsonElement? {
        val other = state.others[field.key]?.takeIf { it.chosen && allowsOther(field, rules) }
        val useOther = other != null
        val otherText = other?.text?.trim().orEmpty()
        return when (field.type) {
            FieldType.RADIO, FieldType.SELECT -> {
                val value = if (useOther) otherText else state.text[field.key]?.takeIf { it.isNotEmpty() && isOption(field, it) }.orEmpty()
                value.takeIf { it.isNotBlank() }?.let(::JsonPrimitive)
            }
            FieldType.MULTI_SELECT -> {
                val picked = state.multi[field.key].orEmpty().filter { isOption(field, it) }.toMutableList()
                if (otherText.isNotEmpty() && otherText !in picked) picked += otherText
                if (picked.isEmpty() && field.key !in state.multi && !useOther) null else JsonArray(picked.map(::JsonPrimitive))
            }
            FieldType.CHECKBOX -> state.bools[field.key]?.let(::JsonPrimitive)
            else -> state.text[field.key]?.trim()?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive)
        }
    }

    /** The answer values a trigger question currently has ("Yes"/"No" for yes/no). */
    fun triggerValues(field: RegistrationFieldDto, state: AnswerState, rules: FormRules = FormRules.Registration): List<String> {
        if (field.type == FieldType.CHECKBOX) return if (state.bools[field.key] == true) listOf("Yes") else listOf("No")
        return when (val value = submittedValue(field, state, rules)) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
            is JsonPrimitive -> listOfNotNull(value.content.trim().takeIf(String::isNotEmpty))
            else -> emptyList()
        }
    }

    /**
     * Which questions are shown. A question with `showIf` is visible only when
     * its trigger (an earlier question) is itself visible and answered with one
     * of the chosen values. A broken rule (trigger missing or later) is ignored.
     */
    fun visibleKeys(fields: List<RegistrationFieldDto>, state: AnswerState, rules: FormRules = FormRules.Registration): Set<String> {
        if (!rules.conditionalLogic) return fields.mapTo(LinkedHashSet()) { it.key }
        val visible = LinkedHashSet<String>()
        val seen = HashMap<String, RegistrationFieldDto>()
        for (field in fields) {
            val rule = field.showIf
            val trigger = rule?.let { seen[it.fieldKey] }
            if (rule == null || trigger == null) {
                visible += field.key
            } else if (trigger.key in visible) {
                if (triggerValues(trigger, state, rules).any { it in rule.values }) visible += field.key
            }
            seen[field.key] = field
        }
        return visible
    }

    /** Drops the answers of questions that logic has hidden, so they're never kept or sent. */
    fun pruneHidden(fields: List<RegistrationFieldDto>, state: AnswerState, rules: FormRules = FormRules.Registration): AnswerState {
        val visible = visibleKeys(fields, state, rules)
        val hidden = fields.map { it.key }.filterNot { it in visible }.toSet()
        return state.without(hidden)
    }

    /** The `answers` payload: submitted values of visible questions only. */
    fun buildPayload(fields: List<RegistrationFieldDto>, state: AnswerState, rules: FormRules = FormRules.Registration): Map<String, JsonElement> {
        val visible = visibleKeys(fields, state, rules)
        val payload = LinkedHashMap<String, JsonElement>()
        for (field in fields) {
            if (field.key !in visible) continue
            submittedValue(field, state, rules)?.let { payload[field.key] = it }
        }
        return payload
    }

    // ---- Limits ------------------------------------------------------------------------------

    data class TextLimits(val min: Int?, val max: Int?)

    fun textLimits(field: RegistrationFieldDto): TextLimits {
        if (field.type != FieldType.TEXT && field.type != FieldType.TEXTAREA) return TextLimits(null, null)
        val cap = DEFAULT_MAX_LENGTH[field.type] ?: 5000
        return TextLimits(
            min = field.settings.minLength?.takeIf { it > 0 },
            max = minOf(field.settings.maxLength ?: cap, 5000),
        )
    }

    fun selectionCount(field: RegistrationFieldDto, state: AnswerState, rules: FormRules = FormRules.Registration): Int {
        val picked = state.multi[field.key].orEmpty().count { isOption(field, it) }
        return picked + if (allowsOther(field, rules) && state.others[field.key]?.chosen == true) 1 else 0
    }

    /** Whether another choice can be ticked under "choose up to N". */
    fun canSelectMore(field: RegistrationFieldDto, state: AnswerState, rules: FormRules = FormRules.Registration): Boolean {
        val max = field.settings.maxSelections ?: return true
        return selectionCount(field, state, rules) < max
    }

    /** A short hint about a question's limits, or null. */
    fun limitHint(field: RegistrationFieldDto): String? {
        val s = field.settings
        return when (field.type) {
            FieldType.TEXT, FieldType.TEXTAREA -> s.minLength?.takeIf { it > 0 }?.let { "At least ${plural(it, "character")}." }
            FieldType.NUMBER -> when {
                s.min != null && s.max != null -> "Enter a number from ${num(s.min)} to ${num(s.max)}."
                s.min != null -> "Enter ${num(s.min)} or more."
                s.max != null -> "Enter ${num(s.max)} or less."
                else -> null
            }
            FieldType.DATE -> when {
                s.minDate != null && s.maxDate != null -> "Choose a date from ${formatIsoDate(s.minDate!!)} to ${formatIsoDate(s.maxDate!!)}."
                s.minDate != null -> "Choose a date on or after ${formatIsoDate(s.minDate!!)}."
                s.maxDate != null -> "Choose a date on or before ${formatIsoDate(s.maxDate!!)}."
                else -> null
            }
            FieldType.MULTI_SELECT -> s.maxSelections?.let { "Choose up to $it." } ?: "Choose all that apply."
            else -> null
        }
    }

    // ---- Validation --------------------------------------------------------------------------

    /** A friendly message for one question, or null when it is fine. Mirrors the server checks. */
    fun validateField(field: RegistrationFieldDto, state: AnswerState, rules: FormRules = FormRules.Registration): String? {
        val s = field.settings
        val other = state.others[field.key]
        if (allowsOther(field, rules) && other?.chosen == true) {
            val typed = other.text.trim()
            if (typed.isEmpty()) return "Type your answer for “Other”."
            if (typed.length > OTHER_MAX_LENGTH) return "Keep “Other” to ${plural(OTHER_MAX_LENGTH, "character")} or fewer."
        }
        val value = submittedValue(field, state, rules)
        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
        return when (field.type) {
            FieldType.TEXT, FieldType.TEXTAREA -> {
                if (text.isEmpty()) return if (field.required) "Please answer this question." else null
                val (min, max) = textLimits(field)
                when {
                    max != null && text.length > max -> "Use ${plural(max, "character")} or fewer."
                    min != null && text.length < min -> "Use at least ${plural(min, "character")} (you have ${text.length})."
                    else -> null
                }
            }
            FieldType.EMAIL -> when {
                text.isEmpty() -> if (field.required) "Please enter an email address." else null
                EMAIL.matches(text.lowercase(Locale.ROOT)) -> null
                else -> "Enter a valid email address, like name@example.com."
            }
            FieldType.PHONE -> {
                val phone = normalizePhone(text)
                when {
                    phone.isEmpty() -> if (field.required) "Please enter a phone number." else null
                    isValidPhone(phone) -> null
                    phone.startsWith("+91") -> "Enter a valid 10-digit Indian mobile number."
                    else -> "Enter a valid phone number, including the country code."
                }
            }
            FieldType.RADIO, FieldType.SELECT ->
                if (text.isEmpty() && field.required) "Please choose an option." else null
            FieldType.MULTI_SELECT -> {
                val count = (value as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }.toSet().size
                when {
                    count == 0 -> if (field.required) "Please choose at least one option." else null
                    s.maxSelections != null && count > s.maxSelections!! -> "Choose up to ${s.maxSelections}."
                    else -> null
                }
            }
            FieldType.CHECKBOX -> if (field.required && state.bools[field.key] != true) "Please tick this box to continue." else null
            FieldType.NUMBER -> {
                if (text.isEmpty()) return if (field.required) "Please enter a number." else null
                val n = text.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return "Enter a number."
                if ((s.min != null && n < s.min!!) || (s.max != null && n > s.max!!)) limitHint(field) else null
            }
            FieldType.DATE -> {
                if (text.isEmpty()) return if (field.required) "Please choose a date." else null
                val date = parseIsoDate(text) ?: return "Enter a valid date."
                val day = date.toString()
                if ((s.minDate != null && day < s.minDate!!) || (s.maxDate != null && day > s.maxDate!!)) limitHint(field) else null
            }
            else -> null
        }
    }

    /** Messages for every visible question that needs attention, keyed by question key. */
    fun validate(fields: List<RegistrationFieldDto>, state: AnswerState, rules: FormRules = FormRules.Registration): Map<String, String> {
        val visible = visibleKeys(fields, state, rules)
        val errors = LinkedHashMap<String, String>()
        for (field in fields) {
            if (field.key !in visible) continue
            validateField(field, state, rules)?.let { errors[field.key] = it }
        }
        return errors
    }

    /**
     * Server answer errors are a single message naming the question
     * ("Dietary needs is required."). Returns the key of the visible question
     * whose label starts the message (longest label wins), or null.
     */
    fun fieldForServerMessage(fields: List<RegistrationFieldDto>, message: String): String? =
        fields.filter { it.label.isNotBlank() && message.startsWith(it.label) }.maxByOrNull { it.label.length }?.key

    // ---- Mutations ---------------------------------------------------------------------------

    fun setText(fields: List<RegistrationFieldDto>, state: AnswerState, key: String, value: String, rules: FormRules = FormRules.Registration): AnswerState {
        val field = fields.firstOrNull { it.key == key }
        // Picking a listed option un-chooses "Other" for single-choice questions.
        val others = if (field != null && field.type != FieldType.MULTI_SELECT && state.others[key]?.chosen == true) {
            state.others + (key to state.others.getValue(key).copy(chosen = false))
        } else {
            state.others
        }
        return pruneHidden(fields, state.copy(text = state.text + (key to value), others = others), rules)
    }

    fun setBool(fields: List<RegistrationFieldDto>, state: AnswerState, key: String, value: Boolean, rules: FormRules = FormRules.Registration): AnswerState =
        pruneHidden(fields, state.copy(bools = state.bools + (key to value)), rules)

    /** Ticks/unticks a multi-select option; refuses to go past `maxSelections`. */
    fun toggleOption(fields: List<RegistrationFieldDto>, state: AnswerState, key: String, option: String, rules: FormRules = FormRules.Registration): AnswerState {
        val field = fields.firstOrNull { it.key == key } ?: return state
        val current = state.multi[key].orEmpty()
        val next = if (option in current) {
            current - option
        } else {
            if (!canSelectMore(field, state, rules)) return state
            current + option
        }
        return pruneHidden(fields, state.copy(multi = state.multi + (key to next)), rules)
    }

    /** Chooses (or for multi-select toggles) the "Other" option. Single choice clears the listed option. */
    fun setOtherChosen(fields: List<RegistrationFieldDto>, state: AnswerState, key: String, chosen: Boolean, rules: FormRules = FormRules.Registration): AnswerState {
        val field = fields.firstOrNull { it.key == key } ?: return state
        if (chosen && field.type == FieldType.MULTI_SELECT && state.others[key]?.chosen != true && !canSelectMore(field, state, rules)) return state
        val other = (state.others[key] ?: OtherAnswer()).copy(chosen = chosen)
        val text = if (chosen && field.type != FieldType.MULTI_SELECT) state.text - key else state.text
        return pruneHidden(fields, state.copy(text = text, others = state.others + (key to other)), rules)
    }

    fun setOtherText(fields: List<RegistrationFieldDto>, state: AnswerState, key: String, value: String, rules: FormRules = FormRules.Registration): AnswerState {
        val other = OtherAnswer(chosen = true, text = value.take(OTHER_MAX_LENGTH))
        return pruneHidden(fields, state.copy(others = state.others + (key to other)), rules)
    }

    // ---- Helpers -----------------------------------------------------------------------------

    fun normalizePhone(value: String): String = value.trim().replace(Regex("[()\\s-]+"), "")

    fun isValidPhone(value: String): Boolean {
        if (!Regex("^\\+\\d{8,15}$").matches(value)) return false
        return !value.startsWith("+91") || Regex("^\\+91[6-9]\\d{9}$").matches(value)
    }

    fun parseIsoDate(value: String): LocalDate? =
        if (ISO_DATE.matches(value)) runCatching { LocalDate.parse(value) }.getOrNull() else null

    /** "2026-10-15" → "15 Oct 2026". */
    fun formatIsoDate(iso: String): String =
        parseIsoDate(iso)?.let { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK).format(it) } ?: iso

    private fun plural(count: Int, word: String) = "%,d %s%s".format(Locale.UK, count, word, if (count == 1) "" else "s")

    /** 5.0 → "5", 2.5 → "2.5". */
    fun num(value: Double?): String = when {
        value == null -> ""
        value == Math.floor(value) && !value.isInfinite() -> value.toLong().toString()
        else -> value.toString()
    }
}
