package com.twilighty.yuyu.feature.broadcasts.editor

import com.twilighty.yuyu.feature.broadcasts.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailHtmlTest {
    private val tagPattern = Regex("</?([a-zA-Z0-9]+)([^>]*)>")

    private fun assertOnlyAllowedMarkup(html: String) {
        tagPattern.findAll(html).forEach { match ->
            val tag = match.groupValues[1]
            val attributes = match.groupValues[2]
            assertTrue("unexpected <$tag> in $html", tag in EmailHtml.ALLOWED_TAGS)
            if (tag != "a") {
                assertTrue("unexpected attributes on <$tag> in $html", attributes.isBlank())
            } else if (!match.value.startsWith("</")) {
                val ok = Regex("""^ (class="cta" )?href="[^"]*"$""").matches(attributes)
                assertTrue("unexpected <a$attributes> in $html", ok)
                val href = Regex("""href="([^"]*)"""").find(attributes)!!.groupValues[1]
                assertTrue("unsafe href $href", LinkValidation.isValid(EmailHtml.decodeEntities(href)))
            }
        }
    }

    @Test
    fun everyTemplateBodyRoundTripsExactly() {
        val templates = TestFixtures.templates()
        assertEquals(13, templates.size)
        templates.forEach { template ->
            val blocks = EmailHtml.parse(template.bodyHtml)
            assertEquals("template ${template.id}", template.bodyHtml, EmailHtml.serialize(blocks))
            assertOnlyAllowedMarkup(EmailHtml.serialize(blocks))
        }
    }

    @Test
    fun templateBodiesParseIntoTheExpectedBlocks() {
        val reminder = TestFixtures.templates().first { it.id == "reminder" }
        val blocks = EmailHtml.parse(reminder.bodyHtml)
        val kinds = blocks.map { (it as? TextBlock)?.kind ?: "button" }
        assertEquals(
            listOf(BlockKind.Paragraph, BlockKind.Paragraph, BlockKind.Bullet, BlockKind.Bullet, BlockKind.Paragraph, "button", BlockKind.Paragraph, BlockKind.Paragraph),
            kinds,
        )
        assertEquals(ButtonBlock("View your ticket", "{{ticketLink}}"), blocks[5])
        val signature = blocks.last() as TextBlock
        assertEquals("See you there,\n{{organisation}}", signature.text.text)
        val when_ = blocks[2] as TextBlock
        assertTrue(when_.text.styles.take(5).all { it.bold })
        assertFalse(when_.text.styles.last().bold)
    }

    @Test
    fun editedTemplatesStayCanonicalAfterEditing() {
        TestFixtures.templates().forEach { template ->
            val state = RichEmailEditorState(template.bodyHtml)
            val first = state.items.first { it.block is TextBlock }
            val text = (first.block as TextBlock).text.text
            state.onFocus(first.id)
            state.onValueChange(first.id, androidx.compose.ui.text.input.TextFieldValue("$text extra", androidx.compose.ui.text.TextRange(text.length + 6)))
            val html = state.html
            assertOnlyAllowedMarkup(html)
            assertEquals(html, EmailHtml.serialize(EmailHtml.parse(html)))
        }
    }

    @Test
    fun serialisationNeverEmitsTagsOutsideTheSubset() {
        val hostile = listOf(
            """<script>alert(1)</script><p onclick="x()">Hi <b>there</b> <i>you</i></p>""",
            """<div><span style="color:red">Red</span><img src="x" onerror="y"><iframe src="evil"></iframe></div>""",
            """<h1>Big</h1><h4>Small</h4><table><tr><td>Cell</td></tr></table>""",
            """<a href="javascript:alert(1)">bad link</a><a href="data:text/html,hi">data</a><a href="/relative">rel</a>""",
            """<p><a class="cta evil" href="https://ok.example/x?a=1&amp;b=2" target="_blank" style="x">Go</a></p>""",
            """<ul><li>One<ul><li>Nested</li></ul>tail</li></ul><ol><li>A</li></ol>""",
            """<blockquote>Bare quote<h2>inside</h2></blockquote>""",
            """<p>5 &lt; 6 &amp; "quotes" &#x1F600; &unknown;</p>""",
            """<p>Unclosed <strong>bold <em>both""",
            """<style>p{}</style><!-- comment --><p>after</p><u>under</u><s>strike</s>""",
            """<a href="{{ticketLink}}">ticket</a><a href="{{nope}}">nope</a><a href="mailto:a@b.c">mail</a>""",
            "<p>a < b and c > d</p>",
            """<a href="https://x.example/" class="cta">Inline button</a> then text""",
        )
        hostile.forEach { html ->
            val out = EmailHtml.serialize(EmailHtml.parse(html))
            assertOnlyAllowedMarkup(out)
            assertFalse(out, out.contains("script") || out.contains("alert") || out.contains("javascript"))
            // Idempotent once canonical.
            assertEquals(out, EmailHtml.serialize(EmailHtml.parse(out)))
        }
    }

    @Test
    fun unsafeLinksKeepTheirTextButLoseTheAnchor() {
        val out = EmailHtml.serialize(EmailHtml.parse("""<p><a href="javascript:alert(1)">click</a> and <a href="https://ok.example">ok</a></p>"""))
        assertEquals("""<p>click and <a href="https://ok.example">ok</a></p>""", out)
    }

    @Test
    fun overlappingStylesNestDeterministically() {
        val text = RichText("abc", listOf(CharStyle(bold = true), CharStyle(bold = true, italic = true, link = "https://x.example"), CharStyle(italic = true)))
        val html = EmailHtml.serialize(listOf(TextBlock(BlockKind.Paragraph, text)))
        assertEquals("""<p><strong>a</strong><a href="https://x.example"><strong><em>b</em></strong></a><em>c</em></p>""", html)
        assertEquals(html, EmailHtml.serialize(EmailHtml.parse(html)))
    }

    @Test
    fun textIsEscapedAndEntitiesDecoded() {
        val blocks = EmailHtml.parse("<p>Fish &amp; chips &lt;3 &rsquo;</p>")
        assertEquals("Fish & chips <3 ’", (blocks.single() as TextBlock).text.text)
        assertEquals("<p>Fish &amp; chips &lt;3 ’</p>", EmailHtml.serialize(blocks))
    }

    @Test
    fun listsAndQuotesGroupConsecutiveBlocks() {
        val blocks = listOf(
            TextBlock(BlockKind.Bullet, RichText.plain("a")),
            TextBlock(BlockKind.Bullet, RichText.plain("b")),
            TextBlock(BlockKind.Numbered, RichText.plain("c")),
            TextBlock(BlockKind.Quote, RichText.plain("q1")),
            TextBlock(BlockKind.Quote, RichText.plain("q2")),
            TextBlock(BlockKind.Heading3, RichText.plain("h")),
        )
        assertEquals(
            "<ul><li>a</li><li>b</li></ul><ol><li>c</li></ol><blockquote><p>q1</p><p>q2</p></blockquote><h3>h</h3>",
            EmailHtml.serialize(blocks),
        )
    }

    @Test
    fun emptyBodyDetection() {
        assertTrue(EmailHtml.isBlank("<p></p><p><strong> </strong></p><ul><li></li></ul>"))
        assertTrue(EmailHtml.isBlank(""))
        assertFalse(EmailHtml.isBlank("<p>{{name}}</p>"))
        assertFalse(EmailHtml.isBlank("""<p><a class="cta" href="{{eventUrl}}">Open</a></p>"""))
    }

    @Test
    fun plainTextForHistoryPreviews() {
        assertEquals("See you tomorrow!", EmailHtml.toPlainText("<p>See you tomorrow!</p>"))
    }
}
