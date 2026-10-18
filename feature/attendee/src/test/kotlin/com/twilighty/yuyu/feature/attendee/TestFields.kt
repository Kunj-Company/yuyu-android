package com.twilighty.yuyu.feature.attendee

import com.twilighty.yuyu.core.network.model.FieldSettingsDto
import com.twilighty.yuyu.core.network.model.FieldType
import com.twilighty.yuyu.core.network.model.RegistrationFieldDto
import com.twilighty.yuyu.core.network.model.ShowIfDto

fun field(
    key: String,
    type: String,
    label: String = key.replaceFirstChar { it.uppercase() }.replace('_', ' '),
    required: Boolean = false,
    options: List<String> = emptyList(),
    settings: FieldSettingsDto = FieldSettingsDto(),
    showIf: ShowIfDto? = null,
    helpText: String? = null,
    placeholder: String? = null,
) = RegistrationFieldDto(
    id = "f-$key",
    key = key,
    label = label,
    type = type,
    required = required,
    helpText = helpText,
    placeholder = placeholder,
    options = options,
    settings = settings,
    showIf = showIf,
)

/** One question of every type, with the dietary question shown only when dinner is "Yes". */
val allTypeFields: List<RegistrationFieldDto> = listOf(
    field("company", FieldType.TEXT, label = "Company", placeholder = "Where you work", settings = FieldSettingsDto(minLength = 2, maxLength = 40)),
    field("bio", FieldType.TEXTAREA, label = "About you", helpText = "A line or two."),
    field("work_email", FieldType.EMAIL, label = "Work email"),
    field("phone", FieldType.PHONE, label = "Phone"),
    field("tshirt", FieldType.SELECT, label = "T-shirt size", options = listOf("S", "M", "L")),
    field("dinner", FieldType.CHECKBOX, label = "Staying for dinner?"),
    field(
        "dietary",
        FieldType.MULTI_SELECT,
        label = "Dietary needs",
        options = listOf("Vegetarian", "Vegan", "Gluten-free"),
        settings = FieldSettingsDto(allowOther = true, maxSelections = 2),
        showIf = ShowIfDto("dinner", listOf("Yes")),
    ),
    field("heard", FieldType.RADIO, label = "How did you hear?", options = listOf("Friend", "Social media"), settings = FieldSettingsDto(allowOther = true)),
    field("guests", FieldType.NUMBER, label = "Guests", settings = FieldSettingsDto(min = 0.0, max = 3.0)),
    field("birthday", FieldType.DATE, label = "Birthday", settings = FieldSettingsDto(minDate = "1900-01-01", maxDate = "2020-12-31")),
)
