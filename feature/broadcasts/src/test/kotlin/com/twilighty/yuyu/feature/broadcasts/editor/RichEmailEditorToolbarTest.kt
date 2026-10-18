package com.twilighty.yuyu.feature.broadcasts.editor

import android.net.Uri
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.broadcasts.preview.PreviewWidth
import com.twilighty.yuyu.feature.broadcasts.preview.isAllowedSubresource
import com.twilighty.yuyu.feature.broadcasts.preview.withViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RichEmailEditorToolbarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun toolbarAppliesBoldAndInsertsAPlaceholder() {
        val state = RichEmailEditorState("<p>Hello world</p>")
        compose.setContent {
            YuyuTheme(darkTheme = false) {
                RichEmailEditor(state = state, placeholders = listOf("{{name}}", "{{event}}"))
            }
        }
        val block = compose.onNodeWithTag("editor-block-0")
        block.performClick()
        block.performTextInputSelection(TextRange(0, 5))
        compose.onNodeWithContentDescription("Bold").assertIsOff().performClick()
        compose.onNodeWithContentDescription("Bold").assertIsOn()
        assertEquals("<p><strong>Hello</strong> world</p>", state.html)

        block.performTextInputSelection(TextRange(11))
        compose.onNodeWithContentDescription("Insert placeholder").performScrollTo().performClick()
        compose.onNodeWithText("Attendee name").performClick()
        compose.waitForIdle()
        assertEquals("<p><strong>Hello</strong> world{{name}}</p>", state.html)
    }

    @Test
    fun headingAndListTogglesReportTheirState() {
        val state = RichEmailEditorState("<p>Agenda</p>")
        compose.setContent {
            YuyuTheme(darkTheme = false) { RichEmailEditor(state = state, placeholders = emptyList()) }
        }
        compose.onNodeWithTag("editor-block-0").performClick()
        compose.onNodeWithContentDescription("Heading").assertIsOff().performClick()
        compose.onNodeWithContentDescription("Heading").assertIsOn()
        assertEquals("<h2>Agenda</h2>", state.html)
        compose.onNodeWithContentDescription("Bullet list").performClick()
        compose.onNodeWithContentDescription("Bullet list").assertIsOn()
        compose.onNodeWithContentDescription("Heading").assertIsOff()
        assertEquals("<ul><li>Agenda</li></ul>", state.html)
    }

    @Test
    fun previewOnlyLoadsHttpsImages() {
        assertTrue(isAllowedSubresource(Uri.parse("https://cdn.example/a/cover.jpg"), null))
        assertTrue(isAllowedSubresource(Uri.parse("https://cdn.example/api/uploads/x"), "image/avif,image/webp,*/*"))
        assertFalse(isAllowedSubresource(Uri.parse("http://cdn.example/cover.jpg"), "image/*"))
        assertFalse(isAllowedSubresource(Uri.parse("https://cdn.example/app.js"), "*/*"))
        assertFalse(isAllowedSubresource(Uri.parse("https://cdn.example/style.css"), "text/css"))
        assertFalse(isAllowedSubresource(Uri.parse("file:///sdcard/a.png"), "image/png"))
    }

    @Test
    fun previewViewportFollowsTheWidthToggle() {
        val html = "<html><head><meta name=\"viewport\" content=\"width=600\"></head><body>Hi</body></html>"
        val desktop = withViewport(html, PreviewWidth.Desktop)
        assertTrue(desktop.contains("content=\"width=800\""))
        assertFalse(desktop.contains("width=600"))
        assertTrue(withViewport("<p>Hi</p>", PreviewWidth.Mobile).startsWith("<meta name=\"viewport\" content=\"width=device-width"))
    }
}
