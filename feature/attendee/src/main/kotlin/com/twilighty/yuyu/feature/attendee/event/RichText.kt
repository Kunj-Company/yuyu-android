package com.twilighty.yuyu.feature.attendee.event

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit

/**
 * Converts the event description's sanitised rich text (the web allows only
 * p, br, strong, em, s, h2, h3, ul, ol, li, blockquote and a with
 * http/https/mailto links — `lib/richText.ts`) into an [AnnotatedString].
 * No WebView and no script: unknown tags are dropped and their text kept, and
 * links other than http(s)/mailto become plain text.
 */
object RichText {
    private val TAG = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>")
    private val HREF = Regex("href\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE)

    data class Styles(
        val linkColor: Color,
        val headingSize: TextUnit = TextUnit.Unspecified,
        val quoteColor: Color = Color.Unspecified,
    )

    fun toAnnotatedString(html: String, styles: Styles, onLink: LinkInteractionListener? = null): AnnotatedString {
        if (!html.contains('<')) return AnnotatedString(decodeEntities(html).trim())
        return buildAnnotatedString {
            val lists = ArrayDeque<IntArray>() // [ordered(1/0), counter]
            var pendingBreak = 0
            var linkOpen = 0
            val styleStack = ArrayDeque<String>()

            fun breakLines(n: Int) {
                if (length > 0) pendingBreak = maxOf(pendingBreak, n)
            }

            fun appendText(text: String) {
                if (text.isEmpty()) return
                if (pendingBreak > 0) {
                    append("\n".repeat(pendingBreak))
                    pendingBreak = 0
                }
                append(text)
            }

            var index = 0
            for (match in TAG.findAll(html)) {
                val raw = html.substring(index, match.range.first)
                appendText(decodeEntities(raw.replace(Regex("\\s+"), " ")).let { if (length == 0 || pendingBreak > 0) it.trimStart() else it })
                index = match.range.last + 1
                val closing = match.groupValues[1] == "/"
                val tag = match.groupValues[2].lowercase()
                val attrs = match.groupValues[3]
                if (!closing) {
                    when (tag) {
                        "br" -> { append("\n"); pendingBreak = 0 }
                        "p", "div" -> breakLines(2)
                        "h2", "h3" -> {
                            breakLines(2)
                            pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = styles.headingSize))
                            styleStack.addLast(tag)
                        }
                        "blockquote" -> {
                            breakLines(2)
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = styles.quoteColor))
                            styleStack.addLast(tag)
                        }
                        "ul", "ol" -> {
                            breakLines(if (lists.isEmpty()) 2 else 1)
                            lists.addLast(intArrayOf(if (tag == "ol") 1 else 0, 0))
                        }
                        "li" -> {
                            breakLines(1)
                            val list = lists.lastOrNull()
                            val indent = "    ".repeat((lists.size - 1).coerceAtLeast(0))
                            val bullet = if (list != null && list[0] == 1) { list[1] += 1; "${list[1]}. " } else "• "
                            appendText(indent + bullet)
                        }
                        "strong", "b" -> { pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); styleStack.addLast(tag) }
                        "em", "i" -> { pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); styleStack.addLast(tag) }
                        "s", "del", "strike" -> { pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)); styleStack.addLast(tag) }
                        "a" -> {
                            val href = HREF.find(attrs)?.let { m -> m.groupValues.drop(2).firstOrNull { it.isNotEmpty() } }
                                ?.let(::decodeEntities)?.trim()
                            if (href != null && isAllowedLink(href)) {
                                if (pendingBreak > 0) { append("\n".repeat(pendingBreak)); pendingBreak = 0 }
                                pushLink(
                                    LinkAnnotation.Url(
                                        href,
                                        TextLinkStyles(SpanStyle(color = styles.linkColor, textDecoration = TextDecoration.Underline)),
                                        onLink,
                                    ),
                                )
                                linkOpen++
                            }
                        }
                    }
                } else {
                    when (tag) {
                        "p", "div", "h2", "h3", "blockquote" -> {
                            if (tag in setOf("h2", "h3", "blockquote") && styleStack.lastOrNull() == tag) { pop(); styleStack.removeLast() }
                            breakLines(2)
                        }
                        "ul", "ol" -> { lists.removeLastOrNull(); breakLines(2) }
                        "li" -> breakLines(1)
                        "strong", "b", "em", "i", "s", "del", "strike" -> if (styleStack.lastOrNull() == tag) { pop(); styleStack.removeLast() }
                        "a" -> if (linkOpen > 0) { pop(); linkOpen-- }
                    }
                }
            }
            appendText(decodeEntities(html.substring(index).replace(Regex("\\s+"), " ")).let { if (pendingBreak > 0) it.trimStart() else it }.trimEnd())
        }.let { trimTrailing(it) }
    }

    fun isAllowedLink(href: String): Boolean {
        val lower = href.lowercase()
        return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:")
    }

    private fun trimTrailing(text: AnnotatedString): AnnotatedString {
        var end = text.length
        while (end > 0 && text[end - 1].isWhitespace()) end--
        return if (end == text.length) text else text.subSequence(0, end)
    }

    fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        return Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(s) { m ->
            val v = m.groupValues[1]
            when {
                v.startsWith("#x") || v.startsWith("#X") -> v.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                v.startsWith("#") -> v.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> when (v.lowercase()) {
                    "amp" -> "&"
                    "lt" -> "<"
                    "gt" -> ">"
                    "quot" -> "\""
                    "apos" -> "'"
                    "nbsp" -> "\u00A0"
                    "ndash" -> "–"
                    "mdash" -> "—"
                    "hellip" -> "…"
                    "rsquo" -> "’"
                    "lsquo" -> "‘"
                    "rdquo" -> "”"
                    "ldquo" -> "“"
                    else -> m.value
                }
            }
        }
    }
}
