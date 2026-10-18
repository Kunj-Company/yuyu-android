package com.twilighty.yuyu.feature.organiser

import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.network.model.EventPermissionsDto
import com.twilighty.yuyu.feature.organiser.event.EventActions
import com.twilighty.yuyu.feature.organiser.event.EventHomeViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EventHomeViewModelTest : MainDispatcherTest() {
    private val events = FakeEventsRepository()

    private fun actionsFor(permissions: EventPermissionsDto): EventActions {
        events.detail = AppResult.Success(eventDetail(permissions = permissions))
        return EventHomeViewModel(events, FakeWebLinks).also { it.start("civic-tech", "e1") }.uiState.value.actions
    }

    @Test
    fun actionTilesFollowPermissions() = runTest {
        assertEquals(EventActions(checkIn = true, guests = true, emails = true, dayOf = true), actionsFor(AllPermissions))
        assertEquals(EventActions(checkIn = true, guests = true, emails = false, dayOf = false), actionsFor(CheckInOnly))
        assertEquals(EventActions(checkIn = false, guests = false, emails = false, dayOf = true), actionsFor(EventPermissionsDto(schedule = true)))
        assertEquals(EventActions(), actionsFor(EventPermissionsDto()))
    }

    @Test
    fun webLinkPointsAtTheEventDashboard() = runTest {
        val vm = EventHomeViewModel(events, FakeWebLinks).also { it.start("civic-tech", "e1") }
        assertEquals("https://yuyu.example/dashboard/civic-tech/event/e1", vm.dashboardUrl())
    }
}
