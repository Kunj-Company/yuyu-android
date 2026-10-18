package com.twilighty.yuyu.feature.organiser

import com.twilighty.yuyu.core.network.model.CollaborationDto
import com.twilighty.yuyu.core.network.model.MeDto
import com.twilighty.yuyu.core.network.model.OrgRefDto
import com.twilighty.yuyu.core.network.model.OrgSummaryDto
import com.twilighty.yuyu.core.network.model.UserDto
import com.twilighty.yuyu.feature.organiser.home.EventsFilter
import com.twilighty.yuyu.feature.organiser.home.OrganiserHomeViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganiserHomeViewModelTest : MainDispatcherTest() {
    private val me = MeDto(
        user = UserDto("u1", "Ada"),
        organisations = listOf(
            OrgSummaryDto("o1", "civic-tech", "Civic Tech", role = "OWNER"),
            OrgSummaryDto("o2", "book-club", "Book Club", role = "MEMBER"),
        ),
        collaborations = listOf(
            CollaborationDto(OrgRefDto("design-club", "Design Club"), "e9", "Design Jam", listOf("CHECK_IN")),
            CollaborationDto(OrgRefDto("design-club", "Design Club"), "e10", "Poster Night", listOf("CHECK_IN")),
            // A grant inside an organisation the person already belongs to is not listed twice.
            CollaborationDto(OrgRefDto("civic-tech", "Civic Tech"), "e11", "Hack", listOf("CHECK_IN")),
        ),
    )
    private val meRepository = FakeMeRepository(me)
    private val events = FakeEventsRepository()

    private fun vm(prefs: FakePreferences) = OrganiserHomeViewModel(meRepository, events, prefs, FakeWebLinks)

    @Test
    fun firstOrganisationIsChosenWhenNothingWasSaved() = runTest {
        val viewModel = vm(FakePreferences())
        viewModel.start(null)
        val state = viewModel.uiState.value
        assertFalse(state.loadingOrgs)
        assertEquals("civic-tech", state.selectedOrg?.slug)
        assertEquals(listOf("civic-tech" to "upcoming"), events.eventCalls)
        assertEquals(listOf("civic-tech-1"), state.events.map { it.id })
    }

    @Test
    fun collaboratorOnlyOrganisationsAreGroupedWithTheirEvents() = runTest {
        val viewModel = vm(FakePreferences())
        viewModel.start(null)
        val state = viewModel.uiState.value
        assertEquals(listOf("civic-tech", "book-club"), state.memberships.map { it.slug })
        assertEquals(listOf("design-club"), state.collaborations.map { it.slug })
        assertEquals(listOf("Design Jam", "Poster Night"), state.collaborations.single().collaboratedEvents)
        assertEquals("Collaborator", state.collaborations.single().roleLabel)
    }

    @Test
    fun switchingOrganisationIsRememberedForNextTime() = runTest {
        val prefs = FakePreferences()
        val first = vm(prefs)
        first.start(null)
        first.selectOrg("design-club")
        assertEquals(listOf("design-club"), prefs.writes)
        assertEquals("design-club", first.uiState.value.selectedOrg?.slug)
        assertEquals("design-club" to "upcoming", events.eventCalls.last())

        // A fresh screen (e.g. after the app restarts) opens the remembered organisation.
        events.eventCalls.clear()
        val second = vm(prefs)
        second.start(null)
        assertEquals("design-club", second.uiState.value.selectedOrg?.slug)
        assertEquals(listOf("design-club" to "upcoming"), events.eventCalls)
    }

    @Test
    fun savedOrganisationThatNoLongerExistsFallsBackToTheFirst() = runTest {
        val viewModel = vm(FakePreferences("left-this-org"))
        viewModel.start(null)
        assertEquals("civic-tech", viewModel.uiState.value.selectedOrg?.slug)
    }

    @Test
    fun routeOrganisationWinsOverTheSavedOne() = runTest {
        val viewModel = vm(FakePreferences("book-club"))
        viewModel.start("design-club")
        assertEquals("design-club", viewModel.uiState.value.selectedOrg?.slug)
    }

    @Test
    fun filterChangeReloadsWithTheApiValue() = runTest {
        val viewModel = vm(FakePreferences())
        viewModel.start(null)
        viewModel.selectFilter(EventsFilter.Past)
        viewModel.selectFilter(EventsFilter.All)
        viewModel.selectFilter(EventsFilter.All) // no-op
        assertEquals(
            listOf("civic-tech" to "upcoming", "civic-tech" to "past", "civic-tech" to "all"),
            events.eventCalls,
        )
        assertEquals(EventsFilter.All, viewModel.uiState.value.filter)
    }

    @Test
    fun emptyListKeepsTheDashboardLink() = runTest {
        events.eventsResult = { _, _ -> com.twilighty.yuyu.core.common.result.AppResult.Success(com.twilighty.yuyu.core.network.model.OrgEventsDto()) }
        val viewModel = vm(FakePreferences())
        viewModel.start(null)
        assertTrue(viewModel.uiState.value.events.isEmpty())
        assertEquals("https://yuyu.example/dashboard/civic-tech", viewModel.dashboardUrl())
    }

    @Test
    fun loadErrorIsShownAndRetryRecovers() = runTest {
        events.eventsResult = { _, _ -> com.twilighty.yuyu.core.common.result.AppResult.Failure(com.twilighty.yuyu.core.common.result.AppError.Network()) }
        val viewModel = vm(FakePreferences())
        viewModel.start(null)
        assertEquals("You're offline or the server can't be reached.", viewModel.uiState.value.error)
        events.eventsResult = { org, _ -> com.twilighty.yuyu.core.common.result.AppResult.Success(com.twilighty.yuyu.core.network.model.OrgEventsDto(listOf(orgEvent("x", org)))) }
        viewModel.retry()
        assertEquals(null, viewModel.uiState.value.error)
        assertEquals(listOf("x"), viewModel.uiState.value.events.map { it.id })
    }
}
