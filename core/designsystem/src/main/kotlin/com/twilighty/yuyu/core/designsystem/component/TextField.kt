package com.twilighty.yuyu.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme

/**
 * Bridges a `value`/`onValueChange` API onto a [TextFieldState] (the state-based
 * BasicTextField). Edits flow out through [onValueChange]; programmatic changes
 * to [value] (e.g. a ViewModel trimming input or clearing a field) flow back in.
 */
@Composable
private fun rememberSyncedTextFieldState(value: String, onValueChange: (String) -> Unit): TextFieldState {
    val state = rememberTextFieldState(value)
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            if (text != currentValue) currentOnChange(text)
        }
    }
    LaunchedEffect(value) {
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
    }
    return state
}

private fun imeHandler(onImeAction: (() -> Unit)?): KeyboardActionHandler? =
    onImeAction?.let { action -> KeyboardActionHandler { action() } }

/**
 * DESIGN §4.2: label **above** the field (never floating), 1dp strong border
 * (2dp ink — or accent with [useAccentFocus] — when focused), 12dp radius,
 * 16sp text, helper under the field, error in danger with an icon.
 * Set [isPassword] for an obscured field with an inline show/hide toggle.
 * [onImeAction] runs for the keyboard's action key (Done/Go/Search…).
 *
 * Built on Material 3's OutlinedTextField (restyled with Yuyu tokens), which
 * keeps focus reliably across recompositions on current Android releases.
 */
@Composable
fun YuyuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    error: String? = null,
    optional: Boolean = false,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isPassword: Boolean = false,
    useAccentFocus: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    var revealed by rememberSaveable { mutableStateOf(false) }
    val focusColor = if (useAccentFocus) YuyuTheme.accent.accent else c.primary
    val actions = onImeAction?.let { action ->
        KeyboardActions(onDone = { action() }, onGo = { action() }, onSearch = { action() }, onSend = { action() }, onNext = { action() })
    } ?: KeyboardActions.Default
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = t.label, color = c.text)
            if (optional) {
                Spacer(Modifier.width(6.dp))
                Text("Optional", style = t.caption, color = c.textMuted)
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            enabled = enabled,
            readOnly = readOnly,
            textStyle = t.body,
            placeholder = placeholder?.let { { Text(it, style = t.body, color = c.textMuted) } },
            trailingIcon = when {
                isPassword -> {
                    {
                        YuyuIconButton(
                            icon = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (revealed) "Hide password" else "Show password",
                            onClick = { revealed = !revealed },
                            tint = c.textMuted,
                        )
                    }
                }
                trailing != null -> trailing
                readOnly -> {
                    { Icon(Icons.Outlined.Lock, null, tint = c.textMuted, modifier = Modifier.size(18.dp)) }
                }
                else -> null
            },
            isError = error != null,
            visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (isPassword) keyboardOptions.copy(keyboardType = KeyboardType.Password) else keyboardOptions,
            keyboardActions = actions,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            shape = YuyuRadius.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = c.text,
                unfocusedTextColor = c.text,
                disabledTextColor = c.textMuted,
                errorTextColor = c.text,
                focusedContainerColor = c.bgCanvas,
                unfocusedContainerColor = if (readOnly) c.bgSubtle else c.bgCanvas,
                disabledContainerColor = c.bgSubtle,
                errorContainerColor = c.bgCanvas,
                cursorColor = focusColor,
                errorCursorColor = c.dangerFg,
                focusedBorderColor = if (readOnly) c.border else focusColor,
                unfocusedBorderColor = if (readOnly) c.border else c.borderStrong,
                disabledBorderColor = c.border,
                errorBorderColor = c.dangerFg,
                focusedPlaceholderColor = c.textMuted,
                unfocusedPlaceholderColor = c.textMuted,
                focusedTrailingIconColor = c.textMuted,
                unfocusedTrailingIconColor = c.textMuted,
            ),
        )
        val reduced = LocalReducedMotion.current
        // The outgoing message keeps its own text while it fades, so an error
        // that is fixed doesn't blink to empty before collapsing.
        AnimatedContent(
            targetState = error,
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(YuyuSpecs.quick()) + slideInVertically(YuyuSpecs.quick()) { -it / 3 }) togetherWith
                        fadeOut(tween(90)) using SizeTransform(clip = false)
                }
            },
            contentKey = { it != null },
            label = "fieldMessage",
        ) { message ->
            if (message != null) {
                Row(
                    Modifier.semantics { error(message) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = c.dangerFg, modifier = Modifier.size(16.dp))
                    Text(message, style = t.caption, color = c.dangerFg)
                }
            } else if (helper != null) {
                Text(helper, style = t.caption, color = c.textMuted)
            }
        }
    }
}

/** Pill search field with a leading search icon and a clear button. */
@Composable
fun YuyuSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onSearch: (() -> Unit)? = null,
) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    val state = rememberSyncedTextFieldState(value, onValueChange)
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        state = state,
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = t.body.copy(color = c.text),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = imeHandler(onSearch),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = placeholder },
        decorator = TextFieldDecorator { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .background(c.bgCanvas, YuyuRadius.pill)
                    .border(if (focused) 2.dp else 1.dp, if (focused) c.primary else c.borderStrong, YuyuRadius.pill)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    inner()
                    if (state.text.isEmpty()) Text(placeholder, style = t.body, color = c.textMuted)
                }
                if (state.text.isNotEmpty()) {
                    YuyuIconButton(Icons.Outlined.Close, "Clear search", onClick = { state.setTextAndPlaceCursorAtEnd("") }, tint = c.textMuted)
                } else {
                    Spacer(Modifier.size(12.dp))
                }
            }
        },
    )
}
