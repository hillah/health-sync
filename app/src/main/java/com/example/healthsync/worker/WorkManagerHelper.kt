package com.example.healthsync.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkManagerHelper {

    private const val UNIQUE_PERIODIC_WORK_NAME = "HEALTH_SYNC_PERIODIC_WORK"
    private const val UNIQUE_ONE_TIME_WORK_NAME = "HEALTH_SYNC_ONE_TIME_WORK"

    fun updatePeriodicSync(context: Context, intervalHours: Long, isEnabled: Boolean) {
        val workManager = WorkManager.getInstance(context)

        if (!isEnabled) {
            Log.d("WorkManagerHelper", "Cancelling periodic health sync")
            workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
            return
        }

        Log.d("WorkManagerHelper", "Scheduling periodic health sync every $intervalHours hours")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<HealthSyncWorker>(
            intervalHours,
            TimeUnit.HOURS,
            15, // Flex period (15 minutes flex)
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    fun triggerOneTimeSync(context: Context) {
        Log.d("WorkManagerHelper", "Enqueueing one-time immediate sync")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeRequest = OneTimeWorkRequestBuilder<HealthSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )
    }
}
