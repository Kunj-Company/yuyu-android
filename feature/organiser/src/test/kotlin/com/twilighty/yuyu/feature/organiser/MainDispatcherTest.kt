package com.twilighty.yuyu.feature.organiser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/** Runs `viewModelScope` on a test dispatcher whose virtual clock `runTest` controls. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {
    protected val dispatcher: TestDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMainDispatcher() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMainDispatcher() = Dispatchers.resetMain()
}
