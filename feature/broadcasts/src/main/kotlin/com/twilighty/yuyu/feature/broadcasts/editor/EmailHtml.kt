package com.twilighty.yuyu.feature.broadcasts.editor

/**
 * Converts between editor blocks and the broadcast HTML subset the server keeps
 * (`sanitizeBroadcastHtml`): p, br, strong, em, h2, h3, ul, ol, li, blockquote,
 * a and a.cta.
 *
 * [serialize] is deterministic and only ever emits that subset, with
 * http(s)/placeholder hrefs. [parse] is tolerant: it accepts any HTML (template
 * bodies, previous emails, web-editor output), keeps the text of unknown tags,
 * drops scripts and styles, and flattens nesting into blocks. For HTML that is
 * already in canonical form, `serialize(parse(html)) == html`.
 */
object EmailHtml {
    const val BODY_MAX = 20_000

    /** Tags [serialize] may emit. */
    val ALLOWED_TAGS = setOf("p", "br", "strong", "em", "h2", "h3", "ul", "ol", "li", "blockquote", "a")

    // ---------------------------------------------------------------- serialise

    fun serialize(blocks: List<EmailBlock>): String {
        val out = StringBuilder()
        var i = 0
        while (i < blocks.size) {
            when (val block = blocks[i]) {
                is ButtonBlock -> {
                    val href = LinkValidation.normalise(block.href)
                    val label = escapeText(block.label.replace('\n', ' '))
                    if (href != null) {
                        out.append("<p><a class=\"cta\" href=\"").append(escapeAttribute(href)).append("\">").append(label).append("</a></p>")
                    } else {
                        out.append("<p>").append(label).append("</p>")
                    }
                    i++
                }
                is TextBlock -> when (block.kind) {
                    BlockKind.Paragraph -> {
                        out.append("<p>").append(inline(block.text)).append("</p>")
                        i++
                    }
                    BlockKind.Heading2, BlockKind.Heading3 -> {
                        val tag = if (block.kind == BlockKind.Heading2) "h2" else "h3"
                        out.append('<').append(tag).append('>').append(inline(block.text)).append("</").append(tag).append('>')
                        i++
                    }
                    BlockKind.Bullet, BlockKind.Numbered -> {
                        val tag = if (block.kind == BlockKind.Bullet) "ul" else "ol"
                        out.append('<').append(tag).append('>')
                        while (i < blocks.size) {
                            val item = blocks[i] as? TextBlock ?: break
                            if (item.kind != block.kind) break
                            out.append("<li>").append(inline(item.text)).append("</li>")
                            i++
                        }
                        out.append("</").append(tag).append('>')
                    }
                    BlockKind.Quote -> {
                        out.append("<blockquote>")
                        while (i < blocks.size) {
                            val item = blocks[i] as? TextBlock ?: break
                            if (item.kind != BlockKind.Quote) break
                            out.append("<p>").append(inline(item.text)).append("</p>")
                            i++
                        }
                        out.append("</blockquote>")
                    }
                }
            }
        }
        return out.toString()
    }

    /** Inline content: runs nested as `a > strong > em`; newlines become `<br>`. */
    private fun inline(text: RichText): String {
        val out = StringBuilder()
        val runs = text.runs().map { run ->
            run.copy(style = run.style.copy(link = run.style.link?.let(LinkValidation::normalise)))
        }
        var i = 0
        while (i < runs.size) {
            val link = runs[i].style.link
            var j = i
            while (j < runs.size && runs[j].style.link == link) j++
            if (link != null) out.append("<a href=\"").append(escapeAttribute(link)).append("\">")
            var k = i
            while (k < j) {
                val bold = runs[k].style.bold
                var m = k
                while (m < j && runs[m].style.bold == bold) m++
                if (bold) out.append("<strong>")
                var n = k
                while (n < m) {
                    val italic = runs[n].style.italic
                    var p = n
                    while (p < m && runs[p].style.italic == italic) p++
                    if (italic) out.append("<em>")
                    for (q in n until p) out.append(escapeText(text.text.substring(runs[q].start, runs[q].end)))
                    if (italic) out.append("</em>")
                    n = p
                }
                if (bold) out.append("</strong>")
                k = m
            }
            if (link != null) out.append("</a>")
            i = j
        }
        return out.toString()
    }

    private fun escapeText(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '\n' -> append("<br>")
                '\r' -> Unit
                else -> append(ch)
            }
        }
    }

    private fun escapeAttribute(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }

    // -------------------------------------------------------------------- parse

    fun parse(html: String): List<EmailBlock> = Parser(tokenize(html)).run()

    /** Plain text of an HTML body (history previews). */
    fun toPlainText(html: String): String = parse(html).plainText().trim()

    /** True when [html] has no visible text. */
    fun isBlank(html: String): Boolean = parse(html).isEmptyBody()

    private sealed interface Token {
        data class Open(val name: String, val attributes: Map<String, String>) : Token
        data class Close(val name: String) : Token
        data class Text(val value: String) : Token
    }

    private val RAW_TEXT_TAGS = setOf("script", "style", "title", "textarea", "template", "noscript", "head", "iframe", "object", "svg")

    private fun tokenize(html: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val text = StringBuilder()
        fun flushText() {
            if (text.isNotEmpty()) {
                tokens += Token.Text(decodeEntities(text.toString()))
                text.clear()
            }
        }
        var i = 0
        val n = html.length
        while (i < n) {
            val ch = html[i]
            if (ch != '<') {
                text.append(ch)
                i++
                continue
            }
            if (html.startsWith("<!--", i)) {
                flushText()
                val end = html.indexOf("-->", i + 4)
                i = if (end < 0) n else end + 3
                continue
            }
            if (html.startsWith("<!", i) || html.startsWith("<?", i)) {
                flushText()
                val end = html.indexOf('>', i)
                i = if (end < 0) n else end + 1
                continue
            }
            val closing = i + 1 < n && html[i + 1] == '/'
            val nameStart = if (closing) i + 2 else i + 1
            if (nameStart >= n || !html[nameStart].isLetter()) {
                text.append(ch)
                i++
                continue
            }
            flushText()
            var j = nameStart
            while (j < n && (html[j].isLetterOrDigit() || html[j] == '-')) j++
            val name = html.substring(nameStart, j).lowercase()
            val attributes = mutableMapOf<String, String>()
            // Attributes, honouring quotes so a '>' inside a value doesn't end the tag.
            while (j < n && html[j] != '>') {
                val c = html[j]
                if (c.isWhitespace() || c == '/') {
                    j++
                    continue
                }
                val attrStart = j
                while (j < n && !html[j].isWhitespace() && html[j] != '=' && html[j] != '>' && html[j] != '/') j++
                val attrName = html.substring(attrStart, j).lowercase()
                while (j < n && html[j].isWhitespace()) j++
                var value = ""
                if (j < n && html[j] == '=') {
                    j++
                    while (j < n && html[j].isWhitespace()) j++
                    if (j < n && (html[j] == '"' || html[j] == '\'')) {
                        val quote = html[j]
                        val end = html.indexOf(quote, j + 1).let { if (it < 0) n else it }
                        value = html.substring(j + 1, end)
                        j = (end + 1).coerceAtMost(n)
                    } else {
                        val valueStart = j
                        while (j < n && !html[j].isWhitespace() && html[j] != '>') j++
                        value = html.substring(valueStart, j)
                    }
                }
                if (attrName.isNotEmpty() && attrName !in attributes) attributes[attrName] = decodeEntities(value)
                if (j == attrStart) j++
            }
            i = (j + 1).coerceAtMost(n)
            if (closing) {
                tokens += Token.Close(name)
            } else {
                tokens += Token.Open(name, attributes)
                if (name in RAW_TEXT_TAGS) {
                    // Skip everything up to the matching close tag.
                    val end = html.indexOf("</$name", i, ignoreCase = true)
                    i = if (end < 0) n else html.indexOf('>', end).let { if (it < 0) n else it + 1 }
                    tokens.removeAt(tokens.lastIndex)
                }
            }
        }
        flushText()
        return tokens
    }

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "hellip" to "…", "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“",
        "ndash" to "–", "mdash" to "—", "copy" to "©", "middot" to "·", "bull" to "•",
    )
    private val ENTITY = Regex("&(#[xX][0-9a-fA-F]+|#\\d+|[a-zA-Z]+);")

    internal fun decodeEntities(value: String): String {
        if ('&' !in value) return value
        return ENTITY.replace(value) { match ->
            val entity = match.groupValues[1]
            if (entity.startsWith("#")) {
                val code = if (entity.length > 1 && (entity[1] == 'x' || entity[1] == 'X')) {
                    entity.substring(2).toIntOrNull(16)
                } else {
                    entity.substring(1).toIntOrNull()
                }
                if (code != null && code in 1..0x10FFFF && code !in 0xD800..0xDFFF) String(Character.toChars(code)) else match.value
            } else {
                NAMED_ENTITIES[entity.lowercase()] ?: match.value
            }
        }
    }

    private val BLOCK_TAGS = setOf(
        "p", "div", "section", "article", "header", "footer", "main", "aside", "nav", "figure", "figcaption",
        "pre", "table", "thead", "tbody", "tfoot", "tr", "td", "th", "dl", "dt", "dd", "address", "center",
        "html", "body", "form", "fieldset",
    )
    private val HEADING_TAGS = mapOf(
        "h1" to BlockKind.Heading2, "h2" to BlockKind.Heading2, "h3" to BlockKind.Heading3,
        "h4" to BlockKind.Heading3, "h5" to BlockKind.Heading3, "h6" to BlockKind.Heading3,
    )
    private val WHITESPACE = Regex("[ \\t\\n\\r\\u000C]+")

    private class Parser(private val tokens: List<Token>) {
        private val blocks = mutableListOf<EmailBlock>()
        private val text = StringBuilder()
        private val styles = mutableListOf<CharStyle>()
        private var explicit = false
        private var bold = 0
        private var italic = 0
        private val links = mutableListOf<Pair<String?, Boolean>>()
        private val lists = mutableListOf<Boolean>()
        private var listItemDepth = 0
        private var quoteDepth = 0
        private var heading: BlockKind? = null
        private var button: String? = null
        private var buttonOpen = false

        fun run(): List<EmailBlock> {
            for (token in tokens) {
                when (token) {
                    is Token.Text -> onText(token.value)
                    is Token.Open -> onOpen(token.name, token.attributes)
                    is Token.Close -> onClose(token.name)
                }
            }
            flush()
            return blocks
        }

        private fun contextKind(): BlockKind = when {
            listItemDepth > 0 -> if (lists.lastOrNull() == true) BlockKind.Numbered else BlockKind.Bullet
            heading != null -> heading!!
            quoteDepth > 0 -> BlockKind.Quote
            else -> BlockKind.Paragraph
        }

        private fun currentStyle(): CharStyle {
            val link = if (buttonOpen) null else links.lastOrNull { it.first != null }?.first
            return CharStyle(bold = bold > 0, italic = italic > 0, link = link)
        }

        private fun append(value: String) {
            text.append(value)
            val style = currentStyle()
            repeat(value.length) { styles += style }
        }

        private fun onText(raw: String) {
            var value = raw.replace(WHITESPACE, " ")
            if (text.isEmpty() || text.last() == '\n') value = value.trimStart()
            if (value.isEmpty()) return
            append(value)
        }

        private fun flush() {
            val rich = RichText(text.toString(), styles.toList()).trimEnd()
            if (rich.length > 0 || explicit) blocks += TextBlock(contextKind(), rich)
            text.clear()
            styles.clear()
            explicit = false
        }

        private fun onOpen(name: String, attributes: Map<String, String>) {
            when {
                name == "br" -> append("\n")
                name in BLOCK_TAGS -> {
                    flush()
                    explicit = name == "p"
                }
                name in HEADING_TAGS -> {
                    flush()
                    heading = HEADING_TAGS.getValue(name)
                    explicit = true
                }
                name == "ul" || name == "ol" -> {
                    flush()
                    lists += name == "ol"
                }
                name == "li" -> {
                    flush()
                    listItemDepth++
                    explicit = true
                }
                name == "blockquote" -> {
                    flush()
                    quoteDepth++
                }
                name == "hr" -> flush()
                name == "strong" || name == "b" -> bold++
                name == "em" || name == "i" -> italic++
                name == "a" -> {
                    val href = attributes["href"]?.trim()?.takeIf { it.isNotEmpty() }
                    val cta = attributes["class"]?.split(' ')?.contains("cta") == true
                    links += href to cta
                    if (cta && contextKind() == BlockKind.Paragraph && !buttonOpen) {
                        // A button stands alone: text before it becomes its own paragraph.
                        if (text.isNotBlank()) {
                            flush()
                        } else {
                            text.clear()
                            styles.clear()
                            explicit = false
                        }
                        button = href
                        buttonOpen = true
                    }
                }
            }
        }

        private fun onClose(name: String) {
            when {
                name in BLOCK_TAGS -> flush()
                name in HEADING_TAGS -> {
                    flush()
                    heading = null
                }
                name == "ul" || name == "ol" -> {
                    flush()
                    if (lists.isNotEmpty()) lists.removeAt(lists.lastIndex)
                }
                name == "li" -> {
                    flush()
                    if (listItemDepth > 0) listItemDepth--
                }
                name == "blockquote" -> {
                    flush()
                    if (quoteDepth > 0) quoteDepth--
                }
                name == "strong" || name == "b" -> if (bold > 0) bold--
                name == "em" || name == "i" -> if (italic > 0) italic--
                name == "a" -> {
                    val closed = links.removeLastOrNull()
                    if (buttonOpen && closed?.second == true) {
                        val label = text.toString().replace('\n', ' ').trim()
                        val href = button
                        if (href != null && label.isNotEmpty()) {
                            blocks += ButtonBlock(label, href)
                        } else if (label.isNotEmpty()) {
                            blocks += TextBlock(BlockKind.Paragraph, RichText.plain(label))
                        }
                        text.clear()
                        styles.clear()
                        explicit = false
                        button = null
                        buttonOpen = false
                    }
                }
            }
        }
    }
}
