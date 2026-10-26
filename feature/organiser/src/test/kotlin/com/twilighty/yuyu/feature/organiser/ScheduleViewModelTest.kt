package com.twilighty.yuyu.feature.organiser

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.network.model.OrgScheduleDto
import com.twilighty.yuyu.core.network.model.OrgSessionDto
import com.twilighty.yuyu.core.network.model.OrgTrackDto
import com.twilighty.yuyu.core.network.model.ScheduleSpeakerDto
import com.twilighty.yuyu.feature.organiser.schedule.ScheduleViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleViewModelTest : MainDispatcherTest() {
    private val repository = FakeScheduleRepository()
    private val programme = OrgScheduleDto(
        listOf(
            OrgTrackDto(
                "t1",
                "Main stage",
                listOf(
                    OrgSessionDto(
                        "s1", "Keynote", "2030-05-10T10:00:00Z", "2030-05-10T10:45:00Z",
                        speakers = listOf(ScheduleSpeakerDto("sp1", "Ada Lovelace", headline = "Founder")),
                    ),
                ),
            ),
        ),
    )

    private fun started() = ScheduleViewModel(repository).also { it.start("civic-tech", "e1") }

    @Test
    fun syncsOnOpenAndShowsSpeakers() = runTest {
        repository.syncResult = { AppResult.Success(savedSchedule(programme)) }
        val state = started().uiState.value
        assertEquals(1, repository.syncCalls)
        assertFalse(state.syncing)
        assertNull(state.syncError)
        assertEquals("Ada Lovelace", state.tracks[0].sessions[0].session.speakers.single().name)
        // Opening the screen syncs quietly; only the button confirms.
        assertNull(state.message)
    }

    @Test
    fun offlineKeepsTheSavedCopy() = runTest {
        repository.saved.value = savedSchedule(programme)
        val vm = started()
        val state = vm.uiState.value
        assertTrue(state.offline)
        assertEquals("Keynote", state.tracks[0].sessions[0].session.title)
        assertNull(state.message)

        vm.sync()
        assertEquals(2, repository.syncCalls)
        assertEquals(AppError.Network().message, vm.uiState.value.message)
    }

    @Test
    fun noSavedCopyAndOfflineIsAnError() = runTest {
        val state = started().uiState.value
        assertFalse(state.loading)
        assertNull(state.saved)
        assertTrue(state.offline)
        assertEquals(AppError.Network().message, state.syncError)
    }

    @Test
    fun syncButtonConfirmsAndClearsTheError() = runTest {
        val vm = started()
        repository.syncResult = { AppResult.Success(savedSchedule(programme)) }
        vm.sync()
        val state = vm.uiState.value
        assertNull(state.syncError)
        assertFalse(state.offline)
        assertEquals("Schedule synced", state.message)
        vm.messageShown()
        assertNull(vm.uiState.value.message)
    }
}
