package com.twilighty.yuyu.feature.broadcasts.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichEmailEditorStateTest {
    private fun RichEmailEditorState.type(index: Int, text: String, cursor: Int = text.length) {
        val item = items[index]
        onFocus(item.id)
        onValueChange(item.id, TextFieldValue(text, TextRange(cursor)))
    }

    private fun RichEmailEditorState.select(index: Int, start: Int, end: Int) {
        val item = items[index]
        onFocus(item.id)
        onValueChange(item.id, TextFieldValue((item.block as TextBlock).text.text, TextRange(start, end)))
    }

    @Test
    fun editKeepsStylesWithTheirText() {
        val rich = RichText("aa", listOf(CharStyle(bold = true), CharStyle.Plain))
        // A plain "a" typed at the start: the caret disambiguates which "a" is new.
        val next = rich.applyEdit("aaa", newCursor = 1, typingStyle = CharStyle.Plain)
        assertEquals(listOf(CharStyle.Plain, CharStyle(bold = true), CharStyle.Plain), next.styles)
        // Typing after bold text continues bold; links don't extend at their end.
        val linked = RichText.plain("go", CharStyle(bold = true, link = "https://x.example"))
        assertEquals(CharStyle(bold = true), linked.applyEdit("go!").styles.last())
    }

    @Test
    fun boldOnASelectionAndThenOff() {
        val state = RichEmailEditorState("<p>Hello world</p>")
        state.select(0, 0, 5)
        state.toggleBold()
        assertEquals("<p><strong>Hello</strong> world</p>", state.html)
        assertTrue(state.isBoldActive())
        state.toggleBold()
        assertEquals("<p>Hello world</p>", state.html)
    }

    @Test
    fun boldWithACaretAppliesToTheNextTypedText() {
        val state = RichEmailEditorState("<p>Hi</p>")
        state.select(0, 2, 2)
        state.toggleBold()
        assertTrue(state.isBoldActive())
        state.type(0, "Hi there")
        assertEquals("<p>Hi<strong> there</strong></p>", state.html)
    }

    @Test
    fun enterSplitsBlocksAndListsContinue() {
        val state = RichEmailEditorState("<ul><li>One</li></ul>")
        state.type(0, "One\n", cursor = 4)
        assertEquals(2, state.items.size)
        state.type(1, "Two")
        assertEquals("<ul><li>One</li><li>Two</li></ul>", state.html)
        // Enter on an empty item leaves the list.
        state.type(1, "Two\n", cursor = 4)
        state.type(2, "\n", cursor = 1)
        assertEquals(BlockKind.Paragraph, (state.items[2].block as TextBlock).kind)
    }

    @Test
    fun headingEnterStartsAParagraph() {
        val state = RichEmailEditorState("<h2>Title</h2>")
        state.type(0, "Title\nBody", cursor = 10)
        assertEquals("<h2>Title</h2><p>Body</p>", state.html)
    }

    @Test
    fun backspaceAtStartMergesWithThePreviousBlock() {
        val state = RichEmailEditorState("<p>One</p><p>Two</p>")
        state.select(1, 0, 0)
        assertTrue(state.onBackspaceAtStart(state.items[1].id))
        assertEquals("<p>OneTwo</p>", state.html)
    }

    @Test
    fun toolbarToggleBlockKinds() {
        val state = RichEmailEditorState("<p>Item</p>")
        state.select(0, 0, 0)
        state.toggleKind(BlockKind.Bullet)
        assertEquals("<ul><li>Item</li></ul>", state.html)
        state.toggleKind(BlockKind.Numbered)
        assertEquals("<ol><li>Item</li></ol>", state.html)
        state.toggleKind(BlockKind.Quote)
        assertEquals("<blockquote><p>Item</p></blockquote>", state.html)
        state.toggleKind(BlockKind.Heading2)
        assertEquals("<h2>Item</h2>", state.html)
        state.toggleKind(BlockKind.Heading2)
        assertEquals("<p>Item</p>", state.html)
    }

    @Test
    fun linksAndButtons() {
        val state = RichEmailEditorState("<p>Read this</p>")
        state.select(0, 5, 9)
        assertFalse(state.applyLink("", "javascript:alert(1)"))
        assertTrue(state.applyLink("", "https://example.com"))
        assertEquals("""<p>Read <a href="https://example.com">this</a></p>""", state.html)
        assertEquals("https://example.com", state.linkAtSelection())
        state.removeLink()
        assertEquals("<p>Read this</p>", state.html)

        assertTrue(state.insertButton("View your ticket", "{{ticketLink}}"))
        assertEquals("""<p>Read this</p><p><a class="cta" href="{{ticketLink}}">View your ticket</a></p>""", state.html)
        assertFalse(state.insertButton("Bad", "ftp://x"))
    }

    @Test
    fun placeholderGoesAtTheCaret() {
        val state = RichEmailEditorState("<p>Hello ,</p>")
        state.select(0, 6, 6)
        state.insertPlaceholder("{{name}}")
        assertEquals("<p>Hello {{name}},</p>", state.html)
        assertEquals(TextRange(14), state.items[0].selection)
    }

    @Test
    fun theBodyLimitIsEnforced() {
        val state = RichEmailEditorState("<p>abc</p>", maxLength = 20)
        state.type(0, "abcdefghij")
        assertEquals("<p>abcdefghij</p>", state.html)
        state.type(0, "abcdefghijklmnopqrstuvwxyz")
        assertEquals("<p>abcdefghij</p>", state.html)
        assertTrue(state.lengthExceeded)
        state.type(0, "abc")
        assertEquals("<p>abc</p>", state.html)
        assertFalse(state.lengthExceeded)
    }

    @Test
    fun formattingOnlyBodyIsEmpty() {
        val state = RichEmailEditorState("<p><strong></strong></p>")
        assertTrue(state.isEmpty)
        state.type(0, "x")
        assertFalse(state.isEmpty)
    }
}
