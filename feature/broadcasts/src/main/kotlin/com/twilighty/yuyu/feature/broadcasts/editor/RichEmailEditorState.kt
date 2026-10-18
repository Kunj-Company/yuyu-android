package com.twilighty.yuyu.feature.broadcasts.editor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** One editor row: a block plus its caret/selection and IME composition. */
@Immutable
data class EditorItem(
    val id: Long,
    val block: EmailBlock,
    val selection: TextRange = TextRange.Zero,
    val composition: TextRange? = null,
)

/**
 * Snapshot state for the block-based email editor. All mutations go through
 * here so the toolbar, the text fields and the ViewModel see one model, and
 * every change can be serialised to the allowed HTML subset ([html]).
 *
 * The body limit ([maxLength], measured on the serialised HTML like the
 * server's 20,000-character check) is enforced: an edit that would grow the
 * body past it is refused and [lengthExceeded] is set.
 */
@Stable
class RichEmailEditorState(initialHtml: String = "", private val maxLength: Int = EmailHtml.BODY_MAX) {
    private var nextId = 0L
    val items = mutableStateListOf<EditorItem>()

    /** The block the toolbar acts on: the last focused one (kept while dialogs are open). */
    var activeId by mutableStateOf<Long?>(null)
        private set

    /** Bold/italic toggled with a collapsed caret, applied to the next typed text. */
    var pendingStyle by mutableStateOf<CharStyle?>(null)
        private set

    /** Block that should take focus (after a split, merge or insert); the UI clears it. */
    var focusRequest by mutableStateOf<Long?>(null)

    var lengthExceeded by mutableStateOf(false)
        private set

    /** Called after every content change (not caret moves). */
    var onContentChange: (() -> Unit)? = null

    val blocks: List<EmailBlock> get() = items.map { it.block }

    val html: String by derivedStateOf { EmailHtml.serialize(items.map { it.block }) }

    val isEmpty: Boolean by derivedStateOf { items.map { it.block }.isEmptyBody() }

    init {
        load(initialHtml)
    }

    // ------------------------------------------------------------------ loading

    /** Replaces the whole body (template, previous email, restored draft). */
    fun setHtml(value: String) {
        load(value)
        activeId = null
        pendingStyle = null
        lengthExceeded = false
        onContentChange?.invoke()
    }

    private fun load(value: String) {
        val parsed = EmailHtml.parse(value)
        items.clear()
        items.addAll(parsed.ifEmpty { listOf(TextBlock(BlockKind.Paragraph, RichText.Empty)) }.map { EditorItem(newId(), it) })
    }

    private fun newId(): Long = nextId++

    private fun indexOf(id: Long): Int = items.indexOfFirst { it.id == id }

    val activeItem: EditorItem? get() = activeId?.let { id -> items.firstOrNull { it.id == id } }

    private val activeTextItem: EditorItem? get() = activeItem?.takeIf { it.block is TextBlock }

    val activeKind: BlockKind? get() = (activeItem?.block as? TextBlock)?.kind

    fun onFocus(id: Long) {
        if (activeId != id) pendingStyle = null
        activeId = id
    }

    // ------------------------------------------------------------------ editing

    /** Applies a text-field change to block [id]: typing, deleting, pasting, or Enter (splits the block). */
    fun onValueChange(id: Long, value: TextFieldValue) {
        val index = indexOf(id)
        if (index < 0) return
        val item = items[index]
        val block = item.block as? TextBlock ?: return
        activeId = id
        if (value.text == block.text.text) {
            if (value.selection != item.selection) pendingStyle = null
            items[index] = item.copy(selection = value.selection, composition = value.composition)
            return
        }
        val range = diffEdit(block.text.text, value.text, value.selection.end)
        val inserted = value.text.substring(range.prefix, range.newEnd)
        val typing = pendingStyle ?: block.text.typingStyleAt(range.prefix)
        val rich = block.text.replace(range.prefix, range.oldEnd, RichText.plain(inserted, typing))
        pendingStyle = null

        if ('\n' !in inserted) {
            val next = item.copy(block = block.copy(text = rich), selection = value.selection, composition = value.composition)
            val candidate = items.toMutableList().also { it[index] = next }
            commit(candidate) { items[index] = next }
            return
        }

        // Enter in an empty list item or quote leaves the list/quote, like other editors.
        if (inserted == "\n" && block.text.isBlank() && block.kind != BlockKind.Paragraph) {
            items[index] = item.copy(block = TextBlock(BlockKind.Paragraph, RichText.Empty), selection = TextRange.Zero, composition = null)
            onContentChange?.invoke()
            return
        }

        val breaks = inserted.indices.filter { inserted[it] == '\n' }.map { range.prefix + it }
        val pieces = mutableListOf<RichText>()
        var start = 0
        for (position in breaks) {
            pieces += rich.slice(start, position)
            start = position + 1
        }
        pieces += rich.slice(start, rich.length)
        val nextKind = if (block.kind.isHeading) BlockKind.Paragraph else block.kind
        val lastStart = start
        val newItems = pieces.mapIndexed { i, piece ->
            if (i == 0) {
                item.copy(block = block.copy(text = piece), selection = TextRange(piece.length), composition = null)
            } else {
                val caret = if (i == pieces.lastIndex) (value.selection.end - lastStart).coerceIn(0, piece.length) else 0
                EditorItem(newId(), TextBlock(nextKind, piece), TextRange(caret))
            }
        }
        val candidate = items.toMutableList().also {
            it.removeAt(index)
            it.addAll(index, newItems)
        }
        commit(candidate) {
            replaceAll(candidate)
            activeId = newItems.last().id
            focusRequest = newItems.last().id
        }
    }

    /** Hardware Backspace with the caret at the start of a block. Returns true when handled. */
    fun onBackspaceAtStart(id: Long): Boolean {
        val index = indexOf(id)
        if (index < 0) return false
        val item = items[index]
        val block = item.block as? TextBlock ?: return false
        if (!item.selection.collapsed || item.selection.start != 0) return false
        if (block.kind != BlockKind.Paragraph) {
            items[index] = item.copy(block = block.copy(kind = BlockKind.Paragraph))
            onContentChange?.invoke()
            return true
        }
        if (index == 0) return false
        val previous = items[index - 1]
        when (val prevBlock = previous.block) {
            is TextBlock -> {
                val junction = prevBlock.text.length
                val merged = previous.copy(block = prevBlock.copy(text = prevBlock.text + block.text), selection = TextRange(junction), composition = null)
                items[index - 1] = merged
                items.removeAt(index)
                activeId = merged.id
                focusRequest = merged.id
            }
            is ButtonBlock -> {
                if (!block.text.isBlank()) return true
                items.removeAt(index)
                activeId = previous.id
            }
        }
        onContentChange?.invoke()
        return true
    }

    // ----------------------------------------------------------------- toolbar

    fun isBoldActive(): Boolean = isInlineActive { it.bold }
    fun isItalicActive(): Boolean = isInlineActive { it.italic }

    private fun isInlineActive(get: (CharStyle) -> Boolean): Boolean {
        val item = activeTextItem ?: return false
        val rich = (item.block as TextBlock).text
        val selection = item.selection
        return if (!selection.collapsed) {
            rich.allMatch(selection.min, selection.max, get)
        } else {
            get(pendingStyle ?: rich.typingStyleAt(selection.start))
        }
    }

    fun toggleBold() = toggleInline({ it.bold }) { style, on -> style.copy(bold = on) }
    fun toggleItalic() = toggleInline({ it.italic }) { style, on -> style.copy(italic = on) }

    private fun toggleInline(get: (CharStyle) -> Boolean, set: (CharStyle, Boolean) -> CharStyle) {
        val item = activeTextItem ?: return
        val block = item.block as TextBlock
        val selection = item.selection
        if (selection.collapsed) {
            val base = pendingStyle ?: block.text.typingStyleAt(selection.start)
            pendingStyle = set(base, !get(base))
            focusRequest = item.id
            return
        }
        val on = !block.text.allMatch(selection.min, selection.max, get)
        val next = block.text.restyle(selection.min, selection.max) { set(it, on) }
        updateText(item, next)
        focusRequest = item.id
    }

    /** Heading, list and quote toggles: the block becomes [kind], or back to a paragraph when it already is one. */
    fun toggleKind(kind: BlockKind) {
        val item = activeTextItem ?: return
        val block = item.block as TextBlock
        val same = block.kind == kind || (kind.isHeading && block.kind.isHeading)
        val index = indexOf(item.id)
        items[index] = item.copy(block = block.copy(kind = if (same) BlockKind.Paragraph else kind))
        focusRequest = item.id
        onContentChange?.invoke()
    }

    /** Text currently selected in the active block (prefills the link dialog). */
    fun selectedText(): String {
        val item = activeTextItem ?: return ""
        val selection = item.selection
        return (item.block as TextBlock).text.text.substring(selection.min, selection.max)
    }

    fun hasSelection(): Boolean = activeTextItem?.selection?.collapsed == false

    /** The link at the caret or covering the whole selection, if any. */
    fun linkAtSelection(): String? {
        val item = activeTextItem ?: return null
        val rich = (item.block as TextBlock).text
        val selection = item.selection
        if (!selection.collapsed) {
            val first = rich.styles.getOrNull(selection.min)?.link ?: return null
            return first.takeIf { rich.allMatch(selection.min, selection.max) { it.link == first } }
        }
        return rich.styles.getOrNull(selection.start - 1)?.link?.takeIf { it == rich.styles.getOrNull(selection.start)?.link }
            ?: rich.styles.getOrNull(selection.start)?.link
    }

    /**
     * Links the selection to [href], or inserts [text] as a link at the caret.
     * Returns false (and changes nothing) when [href] is not an accepted link.
     */
    fun applyLink(text: String, href: String): Boolean {
        val target = LinkValidation.normalise(href) ?: return false
        val item = activeTextItem
        if (item == null) {
            val label = text.trim().ifEmpty { return false }
            val block = TextBlock(BlockKind.Paragraph, RichText.plain(label, CharStyle(link = target)))
            val added = EditorItem(newId(), block, TextRange(label.length))
            commit(items.toList() + added) {
                items.add(added)
                activeId = added.id
                focusRequest = added.id
            }
            return true
        }
        val block = item.block as TextBlock
        val selection = item.selection
        if (!selection.collapsed) {
            updateText(item, block.text.restyle(selection.min, selection.max) { it.copy(link = target) })
        } else {
            val label = text.trim().ifEmpty { return false }
            val base = block.text.typingStyleAt(selection.start)
            val next = block.text.replace(selection.start, selection.start, RichText.plain(label, base.copy(link = target)))
            updateText(item, next, TextRange(selection.start + label.length))
        }
        focusRequest = item.id
        return true
    }

    /** Removes the link from the selection, or from the whole link around the caret. */
    fun removeLink() {
        val item = activeTextItem ?: return
        val block = item.block as TextBlock
        val rich = block.text
        val selection = item.selection
        val (start, end) = if (!selection.collapsed) {
            selection.min to selection.max
        } else {
            val link = linkAtSelection() ?: return
            var s = selection.start
            while (s > 0 && rich.styles[s - 1].link == link) s--
            var e = selection.start
            while (e < rich.length && rich.styles[e].link == link) e++
            s to e
        }
        updateText(item, rich.restyle(start, end) { it.copy(link = null) })
        focusRequest = item.id
    }

    /** Inserts a button after the active block (or at the end). Returns false for an invalid link or empty label. */
    fun insertButton(label: String, href: String): Boolean {
        val target = LinkValidation.normalise(href) ?: return false
        val text = label.trim().ifEmpty { return false }
        val added = EditorItem(newId(), ButtonBlock(text, target))
        val index = activeItem?.let { indexOf(it.id) + 1 } ?: items.size
        val candidate = items.toMutableList().also { it.add(index, added) }
        commit(candidate) {
            items.add(index, added)
            activeId = added.id
        }
        return true
    }

    fun updateButton(id: Long, label: String, href: String): Boolean {
        val target = LinkValidation.normalise(href) ?: return false
        val text = label.trim().ifEmpty { return false }
        val index = indexOf(id)
        if (index < 0 || items[index].block !is ButtonBlock) return false
        val next = items[index].copy(block = ButtonBlock(text, target))
        commit(items.toMutableList().also { it[index] = next }) { items[index] = next }
        return true
    }

    /** Inserts a `{{placeholder}}` at the caret of the active text block (or at the end of the body). */
    fun insertPlaceholder(token: String) {
        val item = activeTextItem ?: items.lastOrNull { it.block is TextBlock }?.let { last ->
            last.copy(selection = TextRange((last.block as TextBlock).text.length))
        }
        if (item == null) {
            val added = EditorItem(newId(), TextBlock(BlockKind.Paragraph, RichText.plain(token)), TextRange(token.length))
            commit(items.toList() + added) {
                items.add(added)
                activeId = added.id
                focusRequest = added.id
            }
            return
        }
        val block = item.block as TextBlock
        val selection = item.selection
        val style = (pendingStyle ?: block.text.typingStyleAt(selection.min)).copy(link = null)
        val next = block.text.replace(selection.min, selection.max, RichText.plain(token, style))
        pendingStyle = null
        updateText(item, next, TextRange(selection.min + token.length))
        activeId = item.id
        focusRequest = item.id
    }

    fun removeBlock(id: Long) {
        val index = indexOf(id)
        if (index < 0) return
        items.removeAt(index)
        if (items.isEmpty()) items.add(EditorItem(newId(), TextBlock(BlockKind.Paragraph, RichText.Empty)))
        if (activeId == id) activeId = null
        onContentChange?.invoke()
    }

    fun moveBlock(id: Long, delta: Int) {
        val index = indexOf(id)
        val target = index + delta
        if (index < 0 || target !in items.indices) return
        val item = items.removeAt(index)
        items.add(target, item)
        onContentChange?.invoke()
    }

    // ----------------------------------------------------------------- helpers

    private fun updateText(item: EditorItem, text: RichText, selection: TextRange = item.selection) {
        val index = indexOf(item.id)
        if (index < 0) return
        val block = item.block as TextBlock
        val next = item.copy(block = block.copy(text = text), selection = selection, composition = null)
        commit(items.toMutableList().also { it[index] = next }) { items[index] = next }
    }

    private fun replaceAll(next: List<EditorItem>) {
        items.clear()
        items.addAll(next)
    }

    /** Applies [apply] unless [candidate] grows the body past [maxLength]. Shrinking is always allowed. */
    private inline fun commit(candidate: List<EditorItem>, apply: () -> Unit): Boolean {
        val newLength = EmailHtml.serialize(candidate.map { it.block }).length
        if (newLength > maxLength && newLength > html.length) {
            lengthExceeded = true
            return false
        }
        lengthExceeded = newLength > maxLength
        apply()
        onContentChange?.invoke()
        return true
    }
}
