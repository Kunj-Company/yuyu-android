package com.twilighty.yuyu.feature.broadcasts.editor

import androidx.compose.runtime.Immutable

/** Inline formatting of one character: bold, italic and an optional link target. */
@Immutable
data class CharStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val link: String? = null,
) {
    companion object {
        val Plain = CharStyle()
    }
}

/** A run of characters sharing one [CharStyle], `[start, end)`. */
data class StyleRun(val start: Int, val end: Int, val style: CharStyle)

/**
 * Text with per-character inline styles. Keeping one style per UTF-16 char
 * makes edits trivial and loss-free: the styles list is spliced exactly like
 * the text. Serialisation groups equal neighbours back into runs.
 */
@Immutable
data class RichText(val text: String, val styles: List<CharStyle>) {
    init {
        require(text.length == styles.size) { "styles must match text length" }
    }

    val length: Int get() = text.length

    fun isBlank(): Boolean = text.isBlank()

    fun runs(): List<StyleRun> {
        if (text.isEmpty()) return emptyList()
        val runs = mutableListOf<StyleRun>()
        var start = 0
        for (i in 1..text.length) {
            if (i == text.length || styles[i] != styles[start]) {
                runs += StyleRun(start, i, styles[start])
                start = i
            }
        }
        return runs
    }

    fun slice(start: Int, end: Int): RichText =
        RichText(text.substring(start, end), styles.subList(start, end).toList())

    operator fun plus(other: RichText): RichText = RichText(text + other.text, styles + other.styles)

    /** Replaces `[start, end)` with [inserted]. */
    fun replace(start: Int, end: Int, inserted: RichText): RichText =
        RichText(
            text.substring(0, start) + inserted.text + text.substring(end),
            styles.subList(0, start) + inserted.styles + styles.subList(end, styles.size),
        )

    /** Applies [transform] to the style of every char in `[start, end)`. */
    fun restyle(start: Int, end: Int, transform: (CharStyle) -> CharStyle): RichText {
        if (start >= end) return this
        val next = styles.toMutableList()
        for (i in start until end) next[i] = transform(next[i])
        return copy(styles = next)
    }

    fun allMatch(start: Int, end: Int, predicate: (CharStyle) -> Boolean): Boolean =
        start < end && (start until end).all { predicate(styles[it]) }

    /** The style new text typed at [offset] would get: bold/italic continue, links only strictly inside. */
    fun typingStyleAt(offset: Int): CharStyle {
        if (text.isEmpty()) return CharStyle.Plain
        val before = styles.getOrNull(offset - 1)
        val after = styles.getOrNull(offset)
        val base = before ?: after ?: CharStyle.Plain
        val link = if (before?.link != null && before.link == after?.link) before.link else null
        return base.copy(link = link)
    }

    /** Drops trailing whitespace (with its styles). */
    fun trimEnd(): RichText {
        var end = text.length
        while (end > 0 && text[end - 1].isWhitespace()) end--
        return if (end == text.length) this else slice(0, end)
    }

    /**
     * Applies a text-field edit. The changed region is found by diffing old and
     * new text; [newCursor] (the caret after the edit) disambiguates repeated
     * characters so styles stay with the text they belong to. Inserted chars get
     * [typingStyle], or the style continuing from the insertion point.
     */
    fun applyEdit(newText: String, newCursor: Int = newText.length, typingStyle: CharStyle? = null): RichText {
        if (newText == text) return this
        val range = diffEdit(text, newText, newCursor)
        val inserted = newText.substring(range.prefix, range.newEnd)
        val style = typingStyle ?: typingStyleAt(range.prefix)
        return replace(range.prefix, range.oldEnd, plain(inserted, style))
    }

    companion object {
        val Empty = RichText("", emptyList())

        fun plain(text: String, style: CharStyle = CharStyle.Plain): RichText = RichText(text, List(text.length) { style })
    }
}

/** The region an edit changed, computed the same way as [RichText.applyEdit]. */
internal data class EditRange(val prefix: Int, val oldEnd: Int, val newEnd: Int)

internal fun diffEdit(old: String, new: String, newCursor: Int): EditRange {
    val oldLen = old.length
    val newLen = new.length
    val cursor = newCursor.coerceIn(0, newLen)
    var suffix = 0
    val maxSuffix = minOf(oldLen, newLen - cursor)
    while (suffix < maxSuffix && old[oldLen - 1 - suffix] == new[newLen - 1 - suffix]) suffix++
    var prefix = 0
    val maxPrefix = minOf(oldLen, newLen) - suffix
    while (prefix < maxPrefix && old[prefix] == new[prefix]) prefix++
    return EditRange(prefix, oldLen - suffix, newLen - suffix)
}
