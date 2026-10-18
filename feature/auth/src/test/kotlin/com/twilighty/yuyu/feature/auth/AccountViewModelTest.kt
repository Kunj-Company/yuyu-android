package com.twilighty.yuyu.feature.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.twilighty.yuyu.core.common.AppBuildInfo
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.prefs.AppPreferences
import com.twilighty.yuyu.core.data.repository.AuthRepository
import com.twilighty.yuyu.core.data.repository.CachedRegistrations
import com.twilighty.yuyu.core.data.repository.MeRepository
import com.twilighty.yuyu.core.data.repository.ServerInfo
import com.twilighty.yuyu.core.data.repository.SessionState
import com.twilighty.yuyu.core.data.sync.UnsyncedCheckIns
import com.twilighty.yuyu.core.network.model.AuthConfigDto
import com.twilighty.yuyu.core.network.model.MeDto
import com.twilighty.yuyu.core.network.model.RegistrationDto
import com.twilighty.yuyu.core.network.model.RegistrationsDto
import com.twilighty.yuyu.core.network.model.UserDto
import com.twilighty.yuyu.feature.auth.account.AccountViewModel
import com.twilighty.yuyu.feature.auth.account.SignOutAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private class FakeAuth : AuthRepository {
        val actions = mutableListOf<String>()
        var signOutEverywhereResult: AppResult<Unit> = AppResult.Success(Unit)
        override val sessionState: Flow<SessionState> = flowOf(SessionState.Loading)
        override val forcedSignOuts: SharedFlow<Unit> = MutableSharedFlow()
        override suspend fun initialize() = Unit
        override suspend fun probeServer(url: HttpUrl): AppResult<ServerInfo> = error("unused")
        override suspend fun selectServer(url: HttpUrl) = Unit
        override suspend fun clearServer() { actions += "clearServer" }
        override suspend fun authConfig(): AppResult<AuthConfigDto> = error("unused")
        override suspend fun signIn(email: String, password: String, totp: String?): AppResult<UserDto> = error("unused")
        override suspend fun signInWithGoogle(idToken: String): AppResult<UserDto> = error("unused")
        override suspend fun signOut() { actions += "signOut" }
        override suspend fun signOutEverywhere(): AppResult<Unit> { actions += "signOutEverywhere"; return signOutEverywhereResult }
        override fun webUrl(path: String): String = "https://yuyu.example/$path"
    }

    private class FakeMe : MeRepository {
        override val me: StateFlow<MeDto?> = MutableStateFlow(null)
        override suspend fun refreshMe(): AppResult<MeDto> = AppResult.Failure(AppError.Unknown())
        override suspend fun updateName(name: String): AppResult<UserDto> = AppResult.Success(UserDto("u1", name = name))
        override suspend fun registrations(): AppResult<RegistrationsDto> = error("unused")
        override fun cachedRegistrations(): Flow<CachedRegistrations> = emptyFlow()
        override suspend fun cachedRegistration(rsvpId: String): RegistrationDto? = null
        override suspend fun cancelRegistration(rsvpId: String): AppResult<Unit> = error("unused")
    }

    private class FakeUnsynced(var waiting: Int, var leftAfterSync: Int = 0) : UnsyncedCheckIns {
        var syncCalls = 0
        override val count: Flow<Int> get() = flowOf(waiting)
        override suspend fun current(): Int = waiting
        override suspend fun trySyncNow(): Int {
            syncCalls++
            waiting = leftAfterSync
            return waiting
        }
    }

    private object EmptyPrefs : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = transform(emptyPreferences())
    }

    private val auth = FakeAuth()

    private fun viewModel(unsynced: UnsyncedCheckIns) = AccountViewModel(
        authRepository = auth,
        meRepository = FakeMe(),
        preferences = AppPreferences(EmptyPrefs),
        unsyncedCheckIns = unsynced,
        buildInfo = AppBuildInfo(isDebug = false, versionName = "test", applicationId = "com.twilighty.yuyu"),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `signs out straight away when nothing is waiting to sync`() = runTest {
        val vm = viewModel(FakeUnsynced(waiting = 0))
        vm.signOut()
        assertEquals(listOf("signOut"), auth.actions)
        assertNull(vm.uiState.value.unsyncedWarning)
    }

    @Test
    fun `warns instead of signing out when check-ins are waiting`() = runTest {
        val vm = viewModel(FakeUnsynced(waiting = 3))
        val collected = backgroundCollect(vm)
        vm.signOut()
        assertEquals(emptyList<String>(), auth.actions)
        val warning = vm.uiState.value.unsyncedWarning!!
        assertEquals(SignOutAction.SignOut, warning.action)
        assertEquals(3, warning.count)
        collected.cancel()
    }

    @Test
    fun `sync now continues with the original action once everything synced`() = runTest {
        val unsynced = FakeUnsynced(waiting = 2, leftAfterSync = 0)
        val vm = viewModel(unsynced)
        val collected = backgroundCollect(vm)
        vm.changeServer()
        vm.syncNowAndContinue()
        assertEquals(1, unsynced.syncCalls)
        assertEquals(listOf("signOut", "clearServer"), auth.actions)
        assertNull(vm.uiState.value.unsyncedWarning)
        collected.cancel()
    }

    @Test
    fun `sync now keeps the warning when some check-ins still fail`() = runTest {
        val vm = viewModel(FakeUnsynced(waiting = 4, leftAfterSync = 1))
        val collected = backgroundCollect(vm)
        vm.signOutEverywhere()
        vm.syncNowAndContinue()
        assertEquals(emptyList<String>(), auth.actions)
        val warning = vm.uiState.value.unsyncedWarning!!
        assertEquals(1, warning.count)
        assertEquals(false, warning.syncing)
        assertEquals(SignOutAction.SignOutEverywhere, warning.action)
        collected.cancel()
    }

    @Test
    fun `continuing without syncing runs the action and clears the warning`() = runTest {
        val vm = viewModel(FakeUnsynced(waiting = 1))
        val collected = backgroundCollect(vm)
        vm.signOutEverywhere()
        vm.continueWithoutSyncing()
        assertEquals(listOf("signOutEverywhere"), auth.actions)
        assertNull(vm.uiState.value.unsyncedWarning)
        collected.cancel()
    }

    @Test
    fun `cancelling the warning does nothing`() = runTest {
        val vm = viewModel(FakeUnsynced(waiting = 1))
        val collected = backgroundCollect(vm)
        vm.signOut()
        vm.dismissUnsyncedWarning()
        assertEquals(emptyList<String>(), auth.actions)
        assertNull(vm.uiState.value.unsyncedWarning)
        collected.cancel()
    }

    @Test
    fun `failed sign out everywhere surfaces the error`() = runTest {
        auth.signOutEverywhereResult = AppResult.Failure(AppError.Network("No connection."))
        val vm = viewModel(FakeUnsynced(waiting = 0))
        val collected = backgroundCollect(vm)
        vm.signOutEverywhere()
        assertEquals("No connection.", vm.uiState.value.message)
        assertEquals(false, vm.uiState.value.busy)
        collected.cancel()
    }

    /** uiState is WhileSubscribed, so keep a subscriber alive while asserting. */
    private fun kotlinx.coroutines.test.TestScope.backgroundCollect(vm: AccountViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
}
