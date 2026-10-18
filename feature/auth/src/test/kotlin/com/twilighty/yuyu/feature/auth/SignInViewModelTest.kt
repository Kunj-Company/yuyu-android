package com.twilighty.yuyu.feature.auth

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.AuthRepository
import com.twilighty.yuyu.core.data.repository.ServerInfo
import com.twilighty.yuyu.core.data.repository.SessionState
import com.twilighty.yuyu.core.network.model.AuthConfigDto
import com.twilighty.yuyu.core.network.model.GoogleSignInConfigDto
import com.twilighty.yuyu.core.network.model.UserDto
import com.twilighty.yuyu.feature.auth.signin.SignInStep
import com.twilighty.yuyu.feature.auth.signin.SignInViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    private class FakeAuth : AuthRepository {
        val calls = mutableListOf<Triple<String, String, String?>>()
        var next: (String?) -> AppResult<UserDto> = { AppResult.Success(UserDto("u1")) }
        var config = AuthConfigDto(googleSignIn = GoogleSignInConfigDto(enabled = true, webClientId = "web.apps.googleusercontent.com"))
        override val sessionState: Flow<SessionState> = flowOf(SessionState.Loading)
        override val forcedSignOuts: SharedFlow<Unit> = MutableSharedFlow()
        override suspend fun initialize() = Unit
        override suspend fun probeServer(url: HttpUrl): AppResult<ServerInfo> = error("unused")
        override suspend fun selectServer(url: HttpUrl) = Unit
        override suspend fun clearServer() = Unit
        override suspend fun authConfig(): AppResult<AuthConfigDto> = AppResult.Success(config)
        override suspend fun signIn(email: String, password: String, totp: String?): AppResult<UserDto> {
            calls += Triple(email, password, totp)
            return next(totp)
        }
        override suspend fun signInWithGoogle(idToken: String): AppResult<UserDto> = AppResult.Success(UserDto("u1"))
        override suspend fun signOut() = Unit
        override suspend fun signOutEverywhere(): AppResult<Unit> = AppResult.Success(Unit)
        override fun webUrl(path: String): String = "https://yuyu.example/$path"
    }

    private val auth = FakeAuth()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun mfaRequiredMovesToCodeStepThenSendsTheCode() = runTest {
        auth.next = { totp -> if (totp == null) AppResult.Failure(AppError.MfaRequired()) else AppResult.Success(UserDto("u1")) }
        val vm = SignInViewModel(auth)
        assertTrue(vm.uiState.value.googleEnabled)

        vm.onEmailChange("ada@example.test")
        vm.onPasswordChange("correct horse")
        vm.submit()
        assertEquals(SignInStep.Mfa, vm.uiState.value.step)
        assertNull(vm.uiState.value.error)

        vm.onCodeChange("123456")
        vm.submit()
        assertEquals(Triple("ada@example.test", "correct horse", "123456"), auth.calls.last())
        assertFalse(vm.uiState.value.submitting)
    }

    @Test
    fun rateLimitShowsRetryTime() = runTest {
        auth.next = { AppResult.Failure(AppError.RateLimited(retryAfterSeconds = 120)) }
        val vm = SignInViewModel(auth)
        vm.onEmailChange("a@b.c")
        vm.onPasswordChange("x")
        vm.submit()
        assertEquals("Too many attempts. Try again in 2 min.", vm.uiState.value.error)
    }

    @Test
    fun googleHiddenWithoutClientId() = runTest {
        auth.config = AuthConfigDto(googleSignIn = GoogleSignInConfigDto(enabled = true, webClientId = null))
        assertFalse(SignInViewModel(auth).uiState.value.googleEnabled)
    }
}
