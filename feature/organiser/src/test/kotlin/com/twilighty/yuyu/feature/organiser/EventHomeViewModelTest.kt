package com.twilighty.yuyu.feature.organiser

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.network.model.EventPermissionsDto
import com.twilighty.yuyu.feature.organiser.event.EventActions
import com.twilighty.yuyu.feature.organiser.event.EventHomeViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventHomeViewModelTest : MainDispatcherTest() {
    private val events = FakeEventsRepository()
    private val schedules = FakeScheduleRepository()

    private fun actionsFor(permissions: EventPermissionsDto): EventActions {
        events.detail = AppResult.Success(eventDetail(permissions = permissions))
        return EventHomeViewModel(events, FakeWebLinks, schedules).also { it.start("civic-tech", "e1") }.uiState.value.actions
    }

    @Test
    fun actionTilesFollowPermissions() = runTest {
        assertEquals(EventActions(checkIn = true, guests = true, emails = true, dayOf = true, schedule = true), actionsFor(AllPermissions))
        // The check-in desk reads the programme too.
        assertEquals(EventActions(checkIn = true, guests = true, emails = false, dayOf = false, schedule = true), actionsFor(CheckInOnly))
        assertEquals(EventActions(checkIn = false, guests = false, emails = false, dayOf = true, schedule = true), actionsFor(EventPermissionsDto(schedule = true)))
        assertEquals(EventActions(schedule = true), actionsFor(EventPermissionsDto()))
    }

    @Test
    fun webLinkPointsAtTheEventDashboard() = runTest {
        val vm = EventHomeViewModel(events, FakeWebLinks, schedules).also { it.start("civic-tech", "e1") }
        assertEquals("https://yuyu.example/dashboard/civic-tech/event/e1", vm.dashboardUrl())
    }

    @Test
    fun offersTheSavedScheduleWhenTheEventCannotLoad() = runTest {
        events.detail = AppResult.Failure(AppError.Network())
        val vm = EventHomeViewModel(events, FakeWebLinks, schedules).also { it.start("civic-tech", "e1") }
        assertFalse(vm.uiState.value.hasSavedSchedule)
        schedules.saved.value = savedSchedule()
        assertTrue(vm.uiState.value.hasSavedSchedule)
        assertEquals(EventActions(), vm.uiState.value.actions)
    }
}
