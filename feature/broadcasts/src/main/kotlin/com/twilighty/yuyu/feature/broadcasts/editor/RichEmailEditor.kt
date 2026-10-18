package com.twilighty.yuyu.feature.broadcasts.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuChip
import com.twilighty.yuyu.core.designsystem.component.YuyuTextField
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.broadcasts.BroadcastCopy

private val PLACEHOLDER_TOKEN = Regex("\\{\\{\\s*[a-zA-Z]+\\s*\\}\\}")

/** Bold, italic and link spans of [this] as an [AnnotatedString]; placeholders get a quiet highlight. */
fun RichText.toAnnotatedString(linkColor: Color, tokenBackground: Color): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    for (run in runs()) {
        if (run.style.bold) builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), run.start, run.end)
        if (run.style.italic) builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), run.start, run.end)
        if (run.style.link != null) builder.addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), run.start, run.end)
    }
    PLACEHOLDER_TOKEN.findAll(text).forEach { builder.addStyle(SpanStyle(background = tokenBackground), it.range.first, it.range.last + 1) }
    return builder.toAnnotatedString()
}

/** Draws the block's spans over the plain field text without changing offsets. */
private class RichTextTransformation(
    private val rich: RichText,
    private val linkColor: Color,
    private val tokenBackground: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text != rich.text) return TransformedText(text, OffsetMapping.Identity)
        return TransformedText(rich.toAnnotatedString(linkColor, tokenBackground), OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean =
        other is RichTextTransformation && other.rich == rich && other.linkColor == linkColor && other.tokenBackground == tokenBackground

    override fun hashCode(): Int = (rich.hashCode() * 31 + linkColor.hashCode()) * 31 + tokenBackground.hashCode()
}

private sealed interface EditorDialog {
    data class Link(val text: String, val href: String, val needsText: Boolean, val canRemove: Boolean) : EditorDialog
    data class Button(val editingId: Long?, val label: String, val href: String) : EditorDialog
}

/**
 * Block-based editor for the broadcast HTML subset: a formatting toolbar
 * (Bold, Italic, Heading, Bullet list, Numbered list, Quote, Link, Button,
 * Insert placeholder) over a column of blocks. Enter starts a new block;
 * Backspace at the start of a block joins it to the one above.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichEmailEditor(
    state: RichEmailEditorState,
    placeholders: List<String>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    val c = YuyuTheme.colors
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    var placeholderMenu by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(YuyuRadius.medium)
            .border(if (isError) 1.5.dp else 1.dp, if (isError) c.dangerSolid else c.borderStrong, YuyuRadius.medium)
            .background(c.bgCanvas),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.bgSubtle)
                .horizontalScroll(rememberScrollState())
                .padding(4.dp)
                .semantics { contentDescription = "Message formatting" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val kind = state.activeKind
            ToolbarToggle(Icons.Outlined.FormatBold, "Bold", state.isBoldActive(), enabled) { state.toggleBold() }
            ToolbarToggle(Icons.Outlined.FormatItalic, "Italic", state.isItalicActive(), enabled) { state.toggleItalic() }
            ToolbarToggle(Icons.Outlined.Title, "Heading", kind?.isHeading == true, enabled) { state.toggleKind(BlockKind.Heading2) }
            ToolbarToggle(Icons.AutoMirrored.Outlined.FormatListBulleted, "Bullet list", kind == BlockKind.Bullet, enabled) { state.toggleKind(BlockKind.Bullet) }
            ToolbarToggle(Icons.Outlined.FormatListNumbered, "Numbered list", kind == BlockKind.Numbered, enabled) { state.toggleKind(BlockKind.Numbered) }
            ToolbarToggle(Icons.Outlined.FormatQuote, "Quote", kind == BlockKind.Quote, enabled) { state.toggleKind(BlockKind.Quote) }
            Box(Modifier.width(1.dp).height(24.dp).background(c.border))
            val currentLink = state.linkAtSelection()
            ToolbarToggle(Icons.Outlined.Link, "Link", currentLink != null, enabled) {
                dialog = EditorDialog.Link(
                    text = state.selectedText(),
                    href = currentLink.orEmpty(),
                    needsText = !state.hasSelection(),
                    canRemove = currentLink != null,
                )
            }
            ToolbarAction(Icons.Outlined.SmartButton, "Button", enabled) {
                dialog = EditorDialog.Button(editingId = null, label = "", href = "")
            }
            Box {
                ToolbarAction(Icons.Outlined.DataObject, "Insert placeholder", enabled, showLabel = true) { placeholderMenu = true }
                DropdownMenu(expanded = placeholderMenu, onDismissRequest = { placeholderMenu = false }) {
                    placeholders.forEach { token ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(BroadcastCopy.placeholderLabel(token), style = YuyuTheme.typography.bodyUiMedium, color = c.text)
                                    Text(token, style = YuyuTheme.typography.mono, color = c.textMuted)
                                }
                            },
                            onClick = {
                                placeholderMenu = false
                                state.insertPlaceholder(token)
                            },
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Column(
            Modifier.fillMaxWidth().heightIn(min = 200.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var number = 0
            state.items.forEachIndexed { index, item ->
                val block = item.block
                number = if (block is TextBlock && block.kind == BlockKind.Numbered) number + 1 else 0
                val itemNumber = number
                androidx.compose.runtime.key(item.id) {
                    when (block) {
                        is TextBlock -> TextBlockRow(
                            state = state,
                            item = item,
                            block = block,
                            index = index,
                            number = itemNumber,
                            enabled = enabled,
                            showHint = state.items.size == 1 && block.text.length == 0,
                        )
                        is ButtonBlock -> ButtonBlockRow(
                            state = state,
                            item = item,
                            block = block,
                            enabled = enabled,
                            onEdit = { dialog = EditorDialog.Button(item.id, block.label, block.href) },
                        )
                    }
                }
            }
        }
    }

    when (val current = dialog) {
        is EditorDialog.Link -> LinkDialog(
            initial = current,
            onDismiss = { dialog = null },
            onRemove = {
                state.removeLink()
                dialog = null
            },
            onApply = { text, href -> state.applyLink(text, href).also { if (it) dialog = null } },
        )
        is EditorDialog.Button -> ButtonDialog(
            initial = current,
            onDismiss = { dialog = null },
            onApply = { label, href ->
                val ok = if (current.editingId != null) state.updateButton(current.editingId, label, href) else state.insertButton(label, href)
                if (ok) dialog = null
                ok
            },
            onRemove = current.editingId?.let { id ->
                {
                    state.removeBlock(id)
                    dialog = null
                }
            },
        )
        null -> Unit
    }
}

@Composable
private fun ToolbarToggle(icon: ImageVector, label: String, active: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val c = YuyuTheme.colors
    Box(
        Modifier
            .size(48.dp)
            .clip(YuyuRadius.small)
            .background(if (active) c.primary else Color.Transparent)
            .toggleable(value = active, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics {
                contentDescription = label
                stateDescription = if (active) "On" else "Off"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (!enabled) c.textDisabled else if (active) c.onPrimary else c.textSecondary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ToolbarAction(icon: ImageVector, label: String, enabled: Boolean, showLabel: Boolean = false, onClick: () -> Unit) {
    val c = YuyuTheme.colors
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(YuyuRadius.small)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = if (showLabel) 10.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) c.textSecondary else c.textDisabled, modifier = Modifier.size(22.dp))
        if (showLabel) Text("Placeholder", style = YuyuTheme.typography.label, color = if (enabled) c.textSecondary else c.textDisabled)
    }
}

private fun kindLabel(kind: BlockKind): String = when (kind) {
    BlockKind.Paragraph -> "Paragraph"
    BlockKind.Heading2, BlockKind.Heading3 -> "Heading"
    BlockKind.Bullet -> "Bullet list item"
    BlockKind.Numbered -> "Numbered list item"
    BlockKind.Quote -> "Quote"
}

@Composable
private fun TextBlockRow(
    state: RichEmailEditorState,
    item: EditorItem,
    block: TextBlock,
    index: Int,
    number: Int,
    enabled: Boolean,
    showHint: Boolean,
) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) {
        if (state.focusRequest == item.id) {
            runCatching { focusRequester.requestFocus() }
            state.focusRequest = null
        }
    }
    val style: TextStyle = when (block.kind) {
        BlockKind.Heading2 -> t.h3
        BlockKind.Heading3 -> t.h4
        BlockKind.Quote -> t.body.copy(fontStyle = FontStyle.Italic)
        else -> t.body
    }.copy(color = c.text)
    val transformation = remember(block.text, c.primary, c.bgMuted) { RichTextTransformation(block.text, c.primary, c.bgMuted) }
    val active = state.activeId == item.id
    Row(verticalAlignment = Alignment.Top) {
        when (block.kind) {
            BlockKind.Bullet -> Text("•", style = style, modifier = Modifier.width(20.dp))
            BlockKind.Numbered -> Text("$number.", style = style, modifier = Modifier.width(28.dp))
            BlockKind.Quote -> Box(Modifier.padding(end = 10.dp).width(3.dp).height(24.dp).background(c.primary))
            else -> Unit
        }
        Box(Modifier.weight(1f)) {
            if (showHint) Text("Write your message…", style = style.copy(color = c.textMuted))
            BasicTextField(
                value = TextFieldValue(block.text.text, item.selection, item.composition),
                onValueChange = { state.onValueChange(item.id, it) },
                enabled = enabled,
                textStyle = style,
                cursorBrush = SolidColor(c.primary),
                visualTransformation = transformation,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) state.onFocus(item.id) }
                    .onPreviewKeyEvent { event ->
                        event.type == KeyEventType.KeyDown && event.key == Key.Backspace && state.onBackspaceAtStart(item.id)
                    }
                    .testTag("editor-block-$index")
                    .semantics { contentDescription = "Message, ${kindLabel(block.kind).lowercase()}" },
            )
        }
        if (active && enabled) BlockMenu(state, item.id)
    }
}

@Composable
private fun BlockMenu(state: RichEmailEditorState, id: Long) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(48.dp).clip(YuyuRadius.pill).clickable(role = Role.Button, onClickLabel = "Block options") { open = true }
                .semantics { contentDescription = "Block options" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = YuyuTheme.colors.textMuted, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Move up") }, onClick = { open = false; state.moveBlock(id, -1) })
            DropdownMenuItem(text = { Text("Move down") }, onClick = { open = false; state.moveBlock(id, 1) })
            DropdownMenuItem(text = { Text("Remove block") }, onClick = { open = false; state.removeBlock(id) })
        }
    }
}

@Composable
private fun ButtonBlockRow(state: RichEmailEditorState, item: EditorItem, block: ButtonBlock, enabled: Boolean, onEdit: () -> Unit) {
    val c = YuyuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clip(YuyuRadius.medium)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Edit button") {
                    state.onFocus(item.id)
                    onEdit()
                }
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier.clip(YuyuRadius.pill).background(c.primary).heightIn(min = 40.dp).padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(block.label, style = YuyuTheme.typography.button, color = c.onPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "Button · ${block.href}",
                style = YuyuTheme.typography.caption,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (enabled) BlockMenu(state, item.id)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkPresets(onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinkValidation.URL_PLACEHOLDERS.forEach { token ->
            Box(Modifier.clip(YuyuRadius.pill).clickable(role = Role.Button, onClickLabel = "Use $token") { onPick(token) }.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                YuyuChip(BroadcastCopy.placeholderLabel(token))
            }
        }
    }
}

@Composable
private fun LinkDialog(
    initial: EditorDialog.Link,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onApply: (String, String) -> Boolean,
) {
    var text by rememberSaveable { mutableStateOf(initial.text) }
    var href by rememberSaveable { mutableStateOf(initial.href) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = YuyuRadius.extraExtraLarge,
        containerColor = YuyuTheme.colors.bgCanvas,
        title = { Text(if (initial.canRemove) "Edit link" else "Add link", style = YuyuTheme.typography.h3, color = YuyuTheme.colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (initial.needsText) YuyuTextField(value = text, onValueChange = { text = it }, label = "Link text")
                YuyuTextField(
                    value = href,
                    onValueChange = {
                        href = it
                        error = null
                    },
                    label = "Web address",
                    placeholder = "https://example.com",
                    helper = "A full https:// address, or a link placeholder.",
                    error = error,
                )
                LinkPresets { href = it; error = null }
            }
        },
        confirmButton = {
            YuyuButton(if (initial.canRemove) "Save link" else "Add link", onClick = {
                error = when {
                    initial.needsText && text.isBlank() -> "Enter the text to show for this link."
                    else -> LinkValidation.error(href)
                }
                if (error == null && !onApply(text, href)) error = LinkValidation.ERROR
            })
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial.canRemove) YuyuButton("Remove link", onRemove, variant = YuyuButtonVariant.Quiet)
                YuyuButton("Cancel", onDismiss, variant = YuyuButtonVariant.Secondary)
            }
        },
    )
}

private val BUTTON_PRESETS = listOf(
    "View your ticket" to "{{ticketLink}}",
    "Open the event page" to "{{eventUrl}}",
    "Share your feedback" to "{{feedbackUrl}}",
    "See the programme" to "{{scheduleUrl}}",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonDialog(
    initial: EditorDialog.Button,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Boolean,
    onRemove: (() -> Unit)?,
) {
    var label by rememberSaveable { mutableStateOf(initial.label) }
    var href by rememberSaveable { mutableStateOf(initial.href) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = YuyuRadius.extraExtraLarge,
        containerColor = YuyuTheme.colors.bgCanvas,
        title = { Text(if (initial.editingId != null) "Edit button" else "Add button", style = YuyuTheme.typography.h3, color = YuyuTheme.colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BUTTON_PRESETS.forEach { (presetLabel, presetHref) ->
                        Box(
                            Modifier.clip(YuyuRadius.pill).heightIn(min = 48.dp).clickable(role = Role.Button) {
                                label = presetLabel
                                href = presetHref
                                error = null
                            },
                            contentAlignment = Alignment.Center,
                        ) { YuyuChip(presetLabel) }
                    }
                }
                YuyuTextField(value = label, onValueChange = { label = it; error = null }, label = "Button text")
                YuyuTextField(
                    value = href,
                    onValueChange = { href = it; error = null },
                    label = "Web address",
                    placeholder = "https://example.com or {{ticketLink}}",
                    error = error,
                )
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            YuyuButton(if (initial.editingId != null) "Save button" else "Add button", onClick = {
                error = if (label.isBlank()) "Enter the button text." else LinkValidation.error(href)
                if (error == null && !onApply(label, href)) error = "The message is too long to add this."
            })
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onRemove != null) YuyuButton("Remove", onRemove, variant = YuyuButtonVariant.DangerQuiet)
                YuyuButton("Cancel", onDismiss, variant = YuyuButtonVariant.Secondary)
            }
        },
    )
}
