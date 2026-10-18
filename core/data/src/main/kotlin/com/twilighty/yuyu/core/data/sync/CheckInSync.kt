package com.twilighty.yuyu.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.CheckInRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Enqueues the offline check-in sync whenever the device is online. */
@Singleton
class CheckInSyncScheduler @Inject constructor(
    private val workManager: Provider<WorkManager>,
) {
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<CheckInSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.get().enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME = "offline-check-in-sync"
    }
}

@HiltWorker
class CheckInSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: CheckInRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val result = repository.syncAllPending()) {
        is AppResult.Success -> Result.success()
        is AppResult.Failure -> when (result.error) {
            is AppError.Network, is AppError.Server, is AppError.RateLimited -> Result.retry()
            // Signed out / permission lost: keep the queue for the organiser to see, stop retrying.
            else -> Result.failure()
        }
    }
}
