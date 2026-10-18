package com.twilighty.yuyu.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.twilighty.yuyu.core.designsystem.component.YuyuTextField
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextFieldFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tappingTheFieldFocusesItAndTypingKeepsFocus() {
        compose.setContent {
            YuyuTheme(darkTheme = false) {
                var value by remember { mutableStateOf("") }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    YuyuTextField(value = value, onValueChange = { value = it }, label = "Server address", placeholder = "events.example.org")
                }
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.assertIsFocused()
        field.performTextInput("http://10.0.2.2:3000")
        field.assertIsFocused()
        field.assertTextContains("http://10.0.2.2:3000")
    }
}
