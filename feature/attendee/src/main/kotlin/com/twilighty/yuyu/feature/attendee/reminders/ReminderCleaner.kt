package com.twilighty.yuyu.feature.attendee.reminders

import com.twilighty.yuyu.core.data.session.LocalDataCleaner
import javax.inject.Inject

/**
 * Reminders belong to the signed-in person's registrations, so a sign-out or
 * server switch cancels them all. The on/off preference is a device setting
 * and stays.
 */
class ReminderCleaner @Inject constructor(private val work: ReminderWork) : LocalDataCleaner {
    override suspend fun clear() {
        work.scheduledNames().forEach(work::cancel)
    }
}
