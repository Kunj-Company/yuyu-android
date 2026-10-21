package com.twilighty.yuyu.feature.attendee.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.component.YuyuBottomSheet
import com.twilighty.yuyu.core.designsystem.component.YuyuIconButton
import com.twilighty.yuyu.core.designsystem.component.YuyuTextField
import com.twilighty.yuyu.core.designsystem.theme.MinTouchTarget
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuSpacing
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.FieldType
import com.twilighty.yuyu.core.network.model.RegistrationFieldDto
import com.twilighty.yuyu.feature.attendee.common.AnimatedReveal
import com.twilighty.yuyu.feature.attendee.common.AnimatedRevealValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Test tag of a question's container. */
fun fieldTag(key: String) = "field-$key"

/**
 * Renders [fields] as a form (all 10 field types), hiding questions whose
 * "show only if" rule isn't met. State lives in the caller; every change goes
 * through [FormLogic] so hidden answers are dropped.
 */
@Composable
fun DynamicForm(
    fields: List<RegistrationFieldDto>,
    state: AnswerState,
    errors: Map<String, String>,
    onStateChange: (AnswerState) -> Unit,
    modifier: Modifier = Modifier,
    rules: FormRules = FormRules.Registration,
    enabled: Boolean = true,
) {
    val visible = FormLogic.visibleKeys(fields, state, rules)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(YuyuSpacing.sectionGap)) {
        fields.forEach { field ->
            // "Show only if" questions expand into place instead of popping in.
            AnimatedReveal(field.key in visible) {
                QuestionField(
                    field = field,
                    fields = fields,
                    state = state,
                    error = errors[field.key],
                    rules = rules,
                    enabled = enabled,
                    onStateChange = onStateChange,
                    modifier = Modifier.testTag(fieldTag(field.key)),
                )
            }
        }
    }
}

@Composable
private fun QuestionField(
    field: RegistrationFieldDto,
    fields: List<RegistrationFieldDto>,
    state: AnswerState,
    error: String?,
    rules: FormRules,
    enabled: Boolean,
    onStateChange: (AnswerState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val help = field.helpText?.trim()?.takeIf { it.isNotEmpty() }
    val hint = FormLogic.limitHint(field)
    val placeholder = field.placeholder?.trim()?.takeIf { it.isNotEmpty() }
    val key = field.key
    when (field.type) {
        FieldType.TEXT, FieldType.TEXTAREA, FieldType.EMAIL, FieldType.PHONE, FieldType.NUMBER -> {
            val text = state.text[key].orEmpty()
            val limits = FormLogic.textLimits(field)
            val counter = limits.max?.takeIf { field.settings.maxLength != null || text.length >= it * 0.8 }
                ?.let { "${text.length} / $it" }
            val keyboard = when (field.type) {
                FieldType.EMAIL -> KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                FieldType.PHONE -> KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
                FieldType.NUMBER -> KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                FieldType.TEXTAREA -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                else -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
            }
            YuyuTextField(
                value = text,
                onValueChange = { value ->
                    val capped = limits.max?.let { value.take(it) } ?: value
                    onStateChange(FormLogic.setText(fields, state, key, capped, rules))
                },
                label = field.label,
                modifier = modifier,
                helper = listOfNotNull(help, hint, counter).joinToString("\n").ifEmpty { null },
                error = error,
                optional = !field.required,
                placeholder = placeholder ?: if (field.type == FieldType.PHONE) "+44 7700 900123" else null,
                enabled = enabled,
                singleLine = field.type != FieldType.TEXTAREA,
                minLines = 3,
                useAccentFocus = true,
                keyboardOptions = keyboard,
            )
        }
        FieldType.DATE -> DateQuestion(field, state.text[key].orEmpty(), help, hint, error, enabled, modifier) { value ->
            onStateChange(FormLogic.setText(fields, state, key, value, rules))
        }
        FieldType.CHECKBOX -> {
            val checked = state.bools[key] == true
            Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceRow(
                    text = field.label,
                    selected = checked,
                    multi = true,
                    enabled = enabled,
                    trailingOptional = !field.required,
                    onClick = { onStateChange(FormLogic.setBool(fields, state, key, !checked, rules)) },
                )
                help?.let { Caption(it) }
                AnimatedRevealValue(error) { FieldError(it) }
            }
        }
        FieldType.RADIO, FieldType.SELECT, FieldType.MULTI_SELECT -> ChoiceQuestion(
            field = field,
            fields = fields,
            state = state,
            help = help,
            hint = hint,
            error = error,
            rules = rules,
            enabled = enabled,
            onStateChange = onStateChange,
            modifier = modifier,
        )
        else -> {
            // Unknown future type: fall back to a plain text answer.
            YuyuTextField(
                value = state.text[key].orEmpty(),
                onValueChange = { onStateChange(FormLogic.setText(fields, state, key, it, rules)) },
                label = field.label,
                modifier = modifier,
                helper = help,
                error = error,
                optional = !field.required,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ChoiceQuestion(
    field: RegistrationFieldDto,
    fields: List<RegistrationFieldDto>,
    state: AnswerState,
    help: String?,
    hint: String?,
    error: String?,
    rules: FormRules,
    enabled: Boolean,
    onStateChange: (AnswerState) -> Unit,
    modifier: Modifier,
) {
    val key = field.key
    val withOther = FormLogic.allowsOther(field, rules)
    val other = state.others[key] ?: OtherAnswer()
    val otherChosen = withOther && other.chosen
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        QuestionLabel(field.label, optional = !field.required)
        help?.let { Caption(it) }
        when (field.type) {
            FieldType.SELECT -> {
                var open by rememberSaveable(key) { mutableStateOf(false) }
                val current = if (otherChosen) "Other" else state.text[key]?.takeIf { it in field.options }
                SelectTrigger(
                    label = field.label,
                    value = current,
                    placeholder = field.placeholder?.takeIf { it.isNotBlank() } ?: "Choose an option",
                    isError = error != null,
                    enabled = enabled,
                    onClick = { open = true },
                )
                if (open) {
                    YuyuBottomSheet(onDismiss = { open = false }, title = field.label) {
                        field.options.forEach { option ->
                            ChoiceRow(option, selected = !otherChosen && state.text[key] == option, multi = false, enabled = enabled, onClick = {
                                onStateChange(FormLogic.setText(fields, state, key, option, rules))
                                open = false
                            })
                        }
                        if (withOther) {
                            ChoiceRow("Other", selected = otherChosen, multi = false, enabled = enabled, onClick = {
                                onStateChange(FormLogic.setOtherChosen(fields, state, key, true, rules))
                                open = false
                            })
                        }
                        if (!field.required && (current != null)) {
                            ChoiceRow("Clear answer", selected = false, multi = false, enabled = enabled, showControl = false, onClick = {
                                onStateChange(FormLogic.setText(fields, state, key, "", rules))
                                open = false
                            })
                        }
                        Spacer(Modifier.padding(bottom = 16.dp))
                    }
                }
            }
            FieldType.RADIO -> {
                field.options.forEach { option ->
                    ChoiceRow(option, selected = !otherChosen && state.text[key] == option, multi = false, enabled = enabled, onClick = {
                        onStateChange(FormLogic.setText(fields, state, key, option, rules))
                    })
                }
                if (withOther) {
                    ChoiceRow("Other", selected = otherChosen, multi = false, enabled = enabled, onClick = {
                        onStateChange(FormLogic.setOtherChosen(fields, state, key, true, rules))
                    })
                }
            }
            else -> {
                val selected = state.multi[key].orEmpty()
                val canMore = FormLogic.canSelectMore(field, state, rules)
                field.options.forEach { option ->
                    val isSelected = option in selected
                    ChoiceRow(option, selected = isSelected, multi = true, enabled = enabled && (isSelected || canMore), onClick = {
                        onStateChange(FormLogic.toggleOption(fields, state, key, option, rules))
                    })
                }
                if (withOther) {
                    ChoiceRow("Other", selected = otherChosen, multi = true, enabled = enabled && (otherChosen || canMore), onClick = {
                        onStateChange(FormLogic.setOtherChosen(fields, state, key, !otherChosen, rules))
                    })
                }
            }
        }
        AnimatedReveal(otherChosen) {
            YuyuTextField(
                value = other.text,
                onValueChange = { onStateChange(FormLogic.setOtherText(fields, state, key, it, rules)) },
                label = "${field.label}: your other answer",
                placeholder = "Type your answer",
                enabled = enabled,
                useAccentFocus = true,
                modifier = Modifier.testTag("other-$key"),
            )
        }
        hint?.let { Caption(it) }
        AnimatedRevealValue(error) { FieldError(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateQuestion(
    field: RegistrationFieldDto,
    value: String,
    help: String?,
    hint: String?,
    error: String?,
    enabled: Boolean,
    modifier: Modifier,
    onValue: (String) -> Unit,
) {
    var open by rememberSaveable(field.key) { mutableStateOf(false) }
    val min = field.settings.minDate?.let(FormLogic::parseIsoDate)
    val max = field.settings.maxDate?.let(FormLogic::parseIsoDate)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        QuestionLabel(field.label, optional = !field.required)
        help?.let { Caption(it) }
        SelectTrigger(
            label = field.label,
            value = value.takeIf { it.isNotBlank() }?.let(FormLogic::formatIsoDate),
            placeholder = field.placeholder?.takeIf { it.isNotBlank() } ?: "Choose a date",
            isError = error != null,
            enabled = enabled,
            icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = YuyuTheme.colors.textMuted) },
            onClick = { open = true },
            onClear = if (value.isNotBlank() && enabled) ({ onValue("") }) else null,
        )
        hint?.let { Caption(it) }
        AnimatedRevealValue(error) { FieldError(it) }
    }
    if (open) {
        val initial = FormLogic.parseIsoDate(value)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val selectable = remember(min, max) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return (min == null || !day.isBefore(min)) && (max == null || !day.isAfter(max))
                }

                override fun isSelectableYear(year: Int): Boolean =
                    (min == null || year >= min.year) && (max == null || year <= max.year)
            }
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial, selectableDates = selectable)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val day: LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onValue(day.toString())
                    }
                    open = false
                }) { Text("Done", color = YuyuTheme.colors.text) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel", color = YuyuTheme.colors.textSecondary) } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SelectTrigger(
    label: String,
    value: String?,
    placeholder: String,
    isError: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit = { Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = YuyuTheme.colors.textMuted) },
    onClear: (() -> Unit)? = null,
) {
    val c = YuyuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget + 4.dp)
            .border(1.dp, if (isError) c.dangerFg else c.borderStrong, YuyuRadius.medium)
            .background(if (enabled) c.bgCanvas else c.bgSubtle, YuyuRadius.medium)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Choose") { onClick() }
            .semantics { contentDescription = "$label: ${value ?: "not answered"}" }
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value ?: placeholder,
            style = YuyuTheme.typography.body,
            color = if (value != null) c.text else c.textMuted,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        if (onClear != null) {
            YuyuIconButton(Icons.Outlined.Close, contentDescription = "Clear $label", onClick = onClear, tint = c.textMuted)
        }
        Spacer(Modifier.width(6.dp))
        icon()
        Spacer(Modifier.width(8.dp))
    }
}

/** A radio or checkbox row; the whole row is the 48dp touch target. */
@Composable
internal fun ChoiceRow(
    text: String,
    selected: Boolean,
    multi: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    trailingOptional: Boolean = false,
    showControl: Boolean = true,
) {
    val c = YuyuTheme.colors
    val a = YuyuTheme.accent
    val rowModifier = if (multi) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onClick() }
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget)
            .then(rowModifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showControl) {
            if (multi) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    enabled = enabled,
                    colors = CheckboxDefaults.colors(checkedColor = a.accent, checkmarkColor = a.onAccent, uncheckedColor = c.borderStrong),
                )
            } else {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    enabled = enabled,
                    colors = RadioButtonDefaults.colors(selectedColor = a.accent, unselectedColor = c.borderStrong),
                )
            }
        }
        Text(text, style = YuyuTheme.typography.bodyUi, color = if (enabled) c.text else c.textDisabled, modifier = Modifier.weight(1f, fill = false))
        if (trailingOptional) Text("Optional", style = YuyuTheme.typography.caption, color = c.textMuted)
    }
}

@Composable
internal fun QuestionLabel(text: String, optional: Boolean) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text, style = YuyuTheme.typography.label, color = YuyuTheme.colors.text, modifier = Modifier.weight(1f, fill = false))
        if (optional) {
            Spacer(Modifier.width(6.dp))
            Text("Optional", style = YuyuTheme.typography.caption, color = YuyuTheme.colors.textMuted)
        }
    }
}

@Composable
internal fun Caption(text: String) {
    Text(text, style = YuyuTheme.typography.caption, color = YuyuTheme.colors.textMuted)
}

@Composable
internal fun FieldError(message: String) {
    val c = YuyuTheme.colors
    Row(
        Modifier.semantics { error(message) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = c.dangerFg, modifier = Modifier.size(16.dp))
        Text(message, style = YuyuTheme.typography.caption, color = c.dangerFg)
    }
}
