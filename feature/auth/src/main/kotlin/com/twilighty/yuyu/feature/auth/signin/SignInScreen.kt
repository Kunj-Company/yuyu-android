package com.twilighty.yuyu.feature.auth.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.designsystem.brand.DotLockup
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonSize
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuTextField
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.designsystem.util.openInBrowser
import com.twilighty.yuyu.feature.auth.google.GoogleSignInResult
import com.twilighty.yuyu.feature.auth.google.requestGoogleIdToken
import kotlinx.coroutines.launch

/** Web paths opened in the browser (the app doesn't create accounts or reset passwords itself). */
internal object WebPaths {
    const val SIGN_UP = "login"
    const val RESET_PASSWORD = "reset-password"
    const val SECURITY = "account/security"
}

@Composable
fun SignInScreen(viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val open: (String) -> Unit = { path -> viewModel.webUrl(path)?.let { openInBrowser(context, it) } }
    SignInContent(
        state = state,
        email = viewModel.email,
        password = viewModel.password,
        code = viewModel.code,
        canSubmit = viewModel.canSubmit(state),
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onCodeChange = viewModel::onCodeChange,
        onToggleRecovery = viewModel::toggleRecoveryCode,
        onSubmit = viewModel::submit,
        onBack = viewModel::backToCredentials,
        onGoogle = {
            val clientId = state.config?.googleSignIn?.webClientId ?: return@SignInContent
            scope.launch {
                when (val result = requestGoogleIdToken(context, clientId)) {
                    is GoogleSignInResult.Token -> viewModel.onGoogleIdToken(result.idToken)
                    is GoogleSignInResult.Failed -> viewModel.onGoogleError(result.message)
                    GoogleSignInResult.Cancelled -> Unit
                }
            }
        },
        onCreateAccount = { open(WebPaths.SIGN_UP) },
        onForgotPassword = { open(WebPaths.RESET_PASSWORD) },
        onChangeServer = viewModel::changeServer,
    )
}

@Composable
internal fun SignInContent(
    state: SignInUiState,
    email: String,
    password: String,
    code: String,
    canSubmit: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onToggleRecovery: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onGoogle: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    onChangeServer: () -> Unit,
) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    Surface(color = c.bgCanvas, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DotLockup()
                Spacer(Modifier.height(12.dp))
                when (state.step) {
                    SignInStep.Credentials -> {
                        Text(
                            "Sign in" + (state.config?.instanceName?.takeIf { it != "Yuyu" }?.let { " to $it" } ?: ""),
                            style = t.h1,
                            color = c.text,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (state.serverLabel.isNotEmpty()) {
                            Text(state.serverLabel.substringAfter("://"), style = t.caption, color = c.textMuted)
                        }
                        ErrorBanner(state.error)
                        YuyuTextField(
                            value = email,
                            onValueChange = onEmailChange,
                            label = "Email",
                            error = state.emailError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                        )
                        YuyuTextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            label = "Password",
                            isPassword = true,
                            error = state.passwordError,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            onImeAction = onSubmit,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            YuyuButton("Forgot password?", onForgotPassword, variant = YuyuButtonVariant.Quiet, size = YuyuButtonSize.Small)
                        }
                        YuyuButton(
                            "Sign in",
                            onSubmit,
                            size = YuyuButtonSize.Large,
                            fullWidth = true,
                            loading = state.submitting,
                            loadingText = "Signing in…",
                            enabled = canSubmit,
                        )
                        if (state.googleEnabled) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                HorizontalDivider(Modifier.weight(1f), color = c.border)
                                Text("or", style = t.caption, color = c.textMuted)
                                HorizontalDivider(Modifier.weight(1f), color = c.border)
                            }
                            YuyuButton(
                                "Continue with Google",
                                onGoogle,
                                variant = YuyuButtonVariant.Secondary,
                                size = YuyuButtonSize.Large,
                                fullWidth = true,
                                loading = state.googleSubmitting,
                                loadingText = "Signing in…",
                            )
                        }
                        if (state.config?.signupEnabled != false) {
                            YuyuButton("New here? Create an account on the website", onCreateAccount, variant = YuyuButtonVariant.Quiet, fullWidth = true)
                        }
                        YuyuButton("Use a different server", onChangeServer, variant = YuyuButtonVariant.Quiet, fullWidth = true)
                    }
                    SignInStep.Mfa -> {
                        Text("Two-step check", style = t.h1, color = c.text, modifier = Modifier.semantics { heading() })
                        Text(
                            if (state.useRecoveryCode) {
                                "Enter one of the recovery codes you saved when you turned on two-step sign-in."
                            } else {
                                "Enter the 6-digit code from your authenticator app."
                            },
                            style = t.body,
                            color = c.textSecondary,
                        )
                        ErrorBanner(state.error)
                        YuyuTextField(
                            value = code,
                            onValueChange = onCodeChange,
                            label = if (state.useRecoveryCode) "Recovery code" else "Authentication code",
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (state.useRecoveryCode) KeyboardType.Ascii else KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done,
                                autoCorrectEnabled = false,
                            ),
                            onImeAction = onSubmit,
                        )
                        YuyuButton(
                            "Verify and sign in",
                            onSubmit,
                            size = YuyuButtonSize.Large,
                            fullWidth = true,
                            loading = state.submitting,
                            loadingText = "Checking…",
                            enabled = canSubmit,
                        )
                        YuyuButton(
                            if (state.useRecoveryCode) "Use an authenticator code instead" else "Use a recovery code instead",
                            onToggleRecovery,
                            variant = YuyuButtonVariant.Quiet,
                            fullWidth = true,
                        )
                        YuyuButton("Back", onBack, variant = YuyuButtonVariant.Quiet, fullWidth = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String?) {
    if (message == null) return
    val c = YuyuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = c.dangerFg)
        Text(message, style = YuyuTheme.typography.bodyUi, color = c.dangerFg)
    }
}
