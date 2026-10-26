package com.twilighty.yuyu.feature.organiser.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.MeRepository
import com.twilighty.yuyu.core.data.repository.OrganiserEventsRepository
import com.twilighty.yuyu.core.network.model.MeDto
import com.twilighty.yuyu.core.network.model.OrgEventDto
import com.twilighty.yuyu.core.network.model.OrgEventsFilter
import com.twilighty.yuyu.feature.organiser.common.OrganiserPreferences
import com.twilighty.yuyu.feature.organiser.common.WebLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One organisation in the switcher: a membership, or an organisation where the person only collaborates on events. */
data class OrgOption(
    val slug: String,
    val name: String,
    val logoUrl: String?,
    /** OWNER | ADMIN | MEMBER, or null for collaborator-only organisations. */
    val role: String?,
    /** Titles of collaborated events (collaborator-only organisations). */
    val collaboratedEvents: List<String> = emptyList(),
) {
    val isCollaboration: Boolean get() = role == null
    val roleLabel: String get() = when (role) {
        "OWNER" -> "Owner"
        "ADMIN" -> "Admin"
        "MEMBER" -> "Member"
        else -> "Collaborator"
    }
}

enum class EventsFilter(val label: String, val apiValue: String) {
    Upcoming("Upcoming", OrgEventsFilter.UPCOMING),
    Past("Past", OrgEventsFilter.PAST),
    All("All", OrgEventsFilter.ALL),
}

data class OrganiserHomeUiState(
    val loadingOrgs: Boolean = true,
    val orgs: List<OrgOption> = emptyList(),
    val selectedOrg: OrgOption? = null,
    val filter: EventsFilter = EventsFilter.Upcoming,
    val events: List<OrgEventDto> = emptyList(),
    val loadingEvents: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val switcherOpen: Boolean = false,
) {
    val memberships: List<OrgOption> get() = orgs.filterNot { it.isCollaboration }
    val collaborations: List<OrgOption> get() = orgs.filter { it.isCollaboration }
}

/** Builds the switcher list: memberships first, then organisations reached only through event collaborations. */
internal fun MeDto.toOrgOptions(): List<OrgOption> {
    val members = organisations.map { OrgOption(it.slug, it.name, it.logoUrl, it.role) }
    val memberSlugs = members.map { it.slug }.toSet()
    val collabs = collaborations
        .filter { it.organisation.slug !in memberSlugs }
        .groupBy { it.organisation.slug }
        .map { (slug, grants) ->
            val org = grants.first().organisation
            OrgOption(slug, org.name, org.logoUrl, role = null, collaboratedEvents = grants.map { it.eventTitle })
        }
    return members + collabs
}

@HiltViewModel
class OrganiserHomeViewModel @Inject constructor(
    private val meRepository: MeRepository,
    private val eventsRepository: OrganiserEventsRepository,
    private val preferences: OrganiserPreferences,
    private val webLinks: WebLinks,
) : ViewModel() {
    private val state = MutableStateFlow(OrganiserHomeUiState())
    val uiState: StateFlow<OrganiserHomeUiState> = state.asStateFlow()

    private var started = false
    private var eventsJob: Job? = null

    /** [requestedOrg]: an organisation from the route (deep link), else the last used one. */
    fun start(requestedOrg: String?) {
        if (started) return
        started = true
        viewModelScope.launch {
            meRepository.me.value?.let { applyMe(it, requestedOrg) }
            when (val result = meRepository.refreshMe()) {
                is AppResult.Success -> applyMe(result.data, requestedOrg)
                is AppResult.Failure -> if (state.value.orgs.isEmpty()) {
                    state.update { it.copy(loadingOrgs = false, error = result.error.message) }
                }
            }
        }
    }

    private suspend fun applyMe(me: MeDto, requestedOrg: String?) {
        val orgs = me.toOrgOptions()
        val current = state.value.selectedOrg?.slug
        val saved = if (current == null && requestedOrg == null) preferences.lastOrgSlug() else null
        val pick = listOfNotNull(current, requestedOrg, saved).firstNotNullOfOrNull { slug -> orgs.firstOrNull { it.slug == slug } }
            ?: orgs.firstOrNull()
        val changed = pick?.slug != current
        state.update { it.copy(loadingOrgs = false, orgs = orgs, selectedOrg = pick, error = null) }
        if (changed && pick != null) loadEvents()
        if (pick == null) state.update { it.copy(events = emptyList()) }
    }

    fun openSwitcher(open: Boolean) = state.update { it.copy(switcherOpen = open) }

    fun selectOrg(slug: String) {
        val org = state.value.orgs.firstOrNull { it.slug == slug } ?: return
        state.update { it.copy(selectedOrg = org, switcherOpen = false, events = emptyList()) }
        viewModelScope.launch { preferences.setLastOrgSlug(slug) }
        loadEvents()
    }

    fun selectFilter(filter: EventsFilter) {
        if (filter == state.value.filter) return
        state.update { it.copy(filter = filter, events = emptyList()) }
        loadEvents()
    }

    fun refresh() {
        val org = state.value.selectedOrg
        state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val me = meRepository.refreshMe()
            if (me is AppResult.Success) applyMe(me.data, null)
            if (org == null || org.slug == state.value.selectedOrg?.slug) {
                loadEvents(refreshing = true)?.join()
            }
            state.update { it.copy(refreshing = false) }
        }
    }

    fun retry() {
        if (state.value.orgs.isEmpty()) {
            started = false
            state.update { it.copy(loadingOrgs = true, error = null) }
            start(null)
        } else {
            loadEvents()
        }
    }

    fun dashboardUrl(): String? = state.value.selectedOrg?.let { webLinks.orgDashboard(it.slug) }

    private fun loadEvents(refreshing: Boolean = false): Job? {
        val org = state.value.selectedOrg ?: return null
        val filter = state.value.filter
        eventsJob?.cancel()
        if (!refreshing) state.update { it.copy(loadingEvents = true, error = null) }
        return viewModelScope.launch {
            val result = eventsRepository.events(org.slug, filter.apiValue)
            // Ignore late results for an organisation or filter the person has since left.
            if (state.value.selectedOrg?.slug != org.slug || state.value.filter != filter) return@launch
            state.update {
                when (result) {
                    is AppResult.Success -> it.copy(events = result.data.events.distinctBy { e -> e.id }, loadingEvents = false, error = null)
                    is AppResult.Failure -> it.copy(loadingEvents = false, error = result.error.message)
                }
            }
        }.also { eventsJob = it }
    }
}
