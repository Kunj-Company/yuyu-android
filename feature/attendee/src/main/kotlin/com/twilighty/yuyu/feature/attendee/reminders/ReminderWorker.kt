package com.twilighty.yuyu.feature.attendee.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.twilighty.yuyu.core.common.time.EventTimeFormatter
import com.twilighty.yuyu.core.data.repository.MeRepository
import com.twilighty.yuyu.core.network.model.RsvpStatus
import com.twilighty.yuyu.feature.attendee.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** [ReminderWork] on WorkManager. Work data holds only the rsvpId and kind (never the ticket token). */
@Singleton
class WorkManagerReminderWork @Inject constructor(
    private val workManager: Provider<WorkManager>,
) : ReminderWork {
    override suspend fun scheduledNames(): Set<String> =
        workManager.get().getWorkInfosByTagFlow(TAG).first()
            .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
            .flatMap { info -> info.tags.filter { it.startsWith(PlannedReminder.PREFIX) } }
            .toSet()

    override fun schedule(reminder: PlannedReminder, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_RSVP_ID to reminder.rsvpId, ReminderWorker.KEY_KIND to reminder.kind.name))
            .addTag(TAG)
            .addTag(reminder.workName)
            .build()
        workManager.get().enqueueUniqueWork(reminder.workName, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(workName: String) {
        workManager.get().cancelUniqueWork(workName)
    }

    companion object {
        const val TAG = "yuyu-reminder"
    }
}

/**
 * Posts one event reminder. Reads the registration from the encrypted ticket
 * cache when it fires, so a cancelled or checked-in registration (or turning
 * reminders off) stays quiet. Tapping opens the ticket.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val meRepository: MeRepository,
    private val preferences: ReminderPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val rsvpId = inputData.getString(KEY_RSVP_ID) ?: return Result.success()
        val kind = inputData.getString(KEY_KIND)?.let { runCatching { ReminderKind.valueOf(it) }.getOrNull() } ?: return Result.success()
        if (!preferences.remindersEnabled.first()) return Result.success()
        val reg = meRepository.cachedRegistration(rsvpId) ?: return Result.success()
        if (reg.status != RsvpStatus.CONFIRMED || reg.checkedInAt != null) return Result.success()
        if (!canNotify(applicationContext)) return Result.success()

        val event = reg.event
        val start = EventTimeFormatter.parseInstant(event.startDateTime) ?: return Result.success()
        val title = when (kind) {
            ReminderKind.DayBefore -> "Tomorrow: ${event.title}"
            ReminderKind.HourBefore -> "Starting soon: ${event.title}"
        }
        val time = "${EventTimeFormatter.time(start, event.timezone)} ${EventTimeFormatter.zoneLabel(start, event.timezone)}"
        val body = listOfNotNull(time, event.location?.takeIf { it.isNotBlank() } ?: if (event.isOnline) "Online" else null)
            .joinToString(" · ") + ". Tap to open your ticket."

        // The ticket token only lives in this in-memory intent, never in work data.
        val link = reg.ticketToken?.let { "yuyu://ticket/$it" } ?: "yuyu://${event.organisation.slug}/${event.slug}"
        val intent = Intent(Intent.ACTION_VIEW, link.toUri())
            .setPackage(applicationContext.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            applicationContext,
            (rsvpId + kind.name).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ticket)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(PlannedReminder.workName(rsvpId, kind).hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post: nothing to do.
        }
        return Result.success()
    }

    companion object {
        const val KEY_RSVP_ID = "rsvpId"
        const val KEY_KIND = "kind"
        const val CHANNEL_ID = "event_reminders"

        fun canNotify(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Event reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A reminder the day before and an hour before events you're going to."
                },
            )
        }
    }
}
