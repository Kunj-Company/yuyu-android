package com.twilighty.yuyu.feature.attendee

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.network.model.RegistrationDto
import com.twilighty.yuyu.core.network.model.RegistrationsDto
import com.twilighty.yuyu.feature.attendee.reminders.PlannedReminder
import com.twilighty.yuyu.feature.attendee.reminders.ReminderKind
import com.twilighty.yuyu.feature.attendee.reminders.ReminderPlanner
import com.twilighty.yuyu.feature.attendee.reminders.ReminderScheduler
import com.twilighty.yuyu.feature.attendee.tickets.MyTicketsViewModel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersTest {
    private val fixture = Fixtures.registrations()
    /** Spring Hack Night starts 2030-05-10T17:00Z; it is CONFIRMED. The second registration is WAITLISTED. */
    private val confirmed: RegistrationDto = fixture.upcoming.first()
    private val now = Instant.parse("2030-05-01T12:00:00Z")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun plansTwoRemindersOnlyForConfirmedFutureRegistrations() {
        val plan = ReminderPlanner.plan(fixture.upcoming, now)
        assertEquals(
            listOf(
                PlannedReminder(confirmed.rsvpId, ReminderKind.DayBefore, Instant.parse("2030-05-09T17:00:00Z")),
                PlannedReminder(confirmed.rsvpId, ReminderKind.HourBefore, Instant.parse("2030-05-10T16:00:00Z")),
            ),
            plan,
        )
    }

    @Test
    fun skipsRemindersInThePastAndCheckedInRegistrations() {
        val lateNow = Instant.parse("2030-05-10T10:00:00Z")
        assertEquals(listOf(ReminderKind.HourBefore), ReminderPlanner.plan(fixture.upcoming, lateNow).map { it.kind })
        val checkedIn = confirmed.copy(checkedInAt = "2030-05-10T16:59:00Z")
        assertTrue(ReminderPlanner.plan(listOf(checkedIn), now).isEmpty())
    }

    @Test
    fun syncSchedulesWithDelaysAndIsDeduplicatedByRsvpId() = runTest {
        val work = FakeReminderWork()
        val scheduler = ReminderScheduler(work)
        scheduler.sync(fixture.upcoming, enabled = true, now = now)
        scheduler.sync(fixture.upcoming, enabled = true, now = now)
        assertEquals(
            setOf("yuyu-reminder-${confirmed.rsvpId}-24h", "yuyu-reminder-${confirmed.rsvpId}-1h"),
            work.scheduled.keys,
        )
        val (_, delay) = work.scheduled.getValue("yuyu-reminder-${confirmed.rsvpId}-24h")
        assertEquals(java.time.Duration.ofDays(8).plusHours(5).toMillis(), delay)
    }

    @Test
    fun syncReschedulesWhenStartMovesAndCancelsStaleOnes() = runTest {
        val work = FakeReminderWork()
        val scheduler = ReminderScheduler(work)
        scheduler.sync(fixture.upcoming, enabled = true, now = now)
        val moved = confirmed.copy(event = confirmed.event.copy(startDateTime = "2030-05-11T17:00:00.000Z"))
        scheduler.sync(listOf(moved), enabled = true, now = now)
        val (reminder, _) = work.scheduled.getValue("yuyu-reminder-${confirmed.rsvpId}-24h")
        assertEquals(Instant.parse("2030-05-10T17:00:00Z"), reminder.fireAt)

        // Cancelled registration disappears from upcoming: its reminders go.
        scheduler.sync(emptyList(), enabled = true, now = now)
        assertTrue(work.scheduled.isEmpty())
        assertTrue(work.cancelledNames.contains("yuyu-reminder-${confirmed.rsvpId}-1h"))
    }

    @Test
    fun turningRemindersOffCancelsEverything() = runTest {
        val work = FakeReminderWork()
        val scheduler = ReminderScheduler(work)
        scheduler.sync(fixture.upcoming, enabled = true, now = now)
        val planned = scheduler.sync(fixture.upcoming, enabled = false, now = now)
        assertTrue(planned.isEmpty())
        assertTrue(work.scheduled.isEmpty())
    }

    // ---- My tickets: refresh → reminders + permission rationale --------------------------------

    private fun viewModel(work: FakeReminderWork, prefs: FakeReminderPreferences, permission: FakeNotificationPermission, me: FakeMeRepository) =
        MyTicketsViewModel(me, ReminderScheduler(work), prefs, permission, FakeCertificateStore())

    private fun futureRegistrations(): RegistrationsDto = fixture // events in 2030

    @Test
    fun refreshSchedulesRemindersAndAsksForPermissionOnce() {
        val work = FakeReminderWork()
        val prefs = FakeReminderPreferences()
        val permission = FakeNotificationPermission(missing = true)
        val me = FakeMeRepository(futureRegistrations())
        val vm = viewModel(work, prefs, permission, me)
        assertEquals(2, work.scheduled.size)
        assertTrue(vm.uiState.value.showNotificationRationale)
        vm.onNotificationRationaleDone()
        assertFalse(vm.uiState.value.showNotificationRationale)
        assertTrue(prefs.prompted)

        vm.refresh()
        assertFalse(vm.uiState.value.showNotificationRationale)
        assertEquals(2, work.scheduled.size)
    }

    @Test
    fun noRationaleWhenPermissionGrantedOrRemindersOff() {
        val granted = viewModel(FakeReminderWork(), FakeReminderPreferences(), FakeNotificationPermission(missing = false), FakeMeRepository(futureRegistrations()))
        assertFalse(granted.uiState.value.showNotificationRationale)

        val work = FakeReminderWork()
        val off = viewModel(work, FakeReminderPreferences(enabled = false), FakeNotificationPermission(missing = true), FakeMeRepository(futureRegistrations()))
        assertFalse(off.uiState.value.showNotificationRationale)
        assertTrue(work.scheduled.isEmpty())
    }

    @Test
    fun toggleOffCancelsAndToggleOnReschedules() {
        val work = FakeReminderWork()
        val prefs = FakeReminderPreferences(prompted = true)
        val vm = viewModel(work, prefs, FakeNotificationPermission(missing = false), FakeMeRepository(futureRegistrations()))
        vm.setRemindersEnabled(false)
        assertFalse(vm.uiState.value.remindersEnabled)
        assertTrue(work.scheduled.isEmpty())
        vm.setRemindersEnabled(true)
        assertEquals(2, work.scheduled.size)
    }

    @Test
    fun offlineRefreshKeepsCachedTicketsAndFlagsOffline() {
        val me = FakeMeRepository(futureRegistrations()).apply { remote = AppResult.Failure(AppError.Network()) }
        val vm = viewModel(FakeReminderWork(), FakeReminderPreferences(prompted = true), FakeNotificationPermission(false), me)
        val s = vm.uiState.value
        assertTrue(s.offline)
        assertEquals(2, s.upcoming.size)
        assertEquals(null, s.error)
    }

    @Test
    fun cancelOnlyWhenAllowedAndRefreshesAfterwards() {
        val me = FakeMeRepository(futureRegistrations())
        val vm = viewModel(FakeReminderWork(), FakeReminderPreferences(prompted = true), FakeNotificationPermission(false), me)
        val before = me.registrationCalls
        vm.askCancel(confirmed.copy(canCancel = false))
        assertEquals(null, vm.uiState.value.cancelTarget)
        vm.askCancel(confirmed)
        vm.confirmCancel()
        assertEquals(listOf(confirmed.rsvpId), me.cancelled)
        assertEquals(before + 1, me.registrationCalls)
        assertEquals(null, vm.uiState.value.cancelTarget)
    }
}
