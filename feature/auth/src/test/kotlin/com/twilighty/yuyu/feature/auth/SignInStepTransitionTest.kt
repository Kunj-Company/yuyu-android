package com.twilighty.yuyu.feature.auth

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.feature.auth.signin.SignInContent
import com.twilighty.yuyu.feature.auth.signin.SignInStep
import com.twilighty.yuyu.feature.auth.signin.SignInUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sign-in steps animate between each other but always land on exactly one step. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SignInStepTransitionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(reducedMotion: Boolean): (SignInUiState) -> Unit {
        var state by mutableStateOf(SignInUiState(configLoading = false))
        compose.setContent {
            YuyuTheme(darkTheme = false) {
                CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                    SignInContent(
                        state = state,
                        email = "ada@example.org",
                        password = "secret",
                        code = "",
                        canSubmit = true,
                        onEmailChange = {},
                        onPasswordChange = {},
                        onCodeChange = {},
                        onToggleRecovery = {},
                        onSubmit = {},
                        onBack = {},
                        onGoogle = {},
                        onCreateAccount = {},
                        onForgotPassword = {},
                        onChangeServer = {},
                    )
                }
            }
        }
        return { state = it }
    }

    private fun assertOnlyStep(heading: String, other: String) {
        compose.onNodeWithText(heading).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText(other).fetchSemanticsNodes().size)
    }

    @Test
    fun stepsSlideForwardAndBack() {
        val update = show(reducedMotion = false)
        assertOnlyStep("Forgot password?", "Two-step check")
        update(SignInUiState(configLoading = false, step = SignInStep.Mfa, error = "That code didn't work."))
        compose.waitForIdle()
        assertOnlyStep("Two-step check", "Forgot password?")
        compose.onNodeWithText("That code didn't work.").assertIsDisplayed()
        update(SignInUiState(configLoading = false))
        compose.waitForIdle()
        assertOnlyStep("Forgot password?", "Two-step check")
        assertEquals(0, compose.onAllNodesWithText("That code didn't work.").fetchSemanticsNodes().size)
    }

    @Test
    fun reducedMotionStillLandsOnTheNewStepQuickly() {
        val update = show(reducedMotion = true)
        compose.mainClock.autoAdvance = false
        update(SignInUiState(configLoading = false, step = SignInStep.Mfa))
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(200)
        assertOnlyStep("Two-step check", "Forgot password?")
    }
}
