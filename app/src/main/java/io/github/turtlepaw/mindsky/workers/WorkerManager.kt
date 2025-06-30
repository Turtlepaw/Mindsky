package io.github.turtlepaw.mindsky.workers

// FeedWorker import is still needed for buildOneTimeWorkRequest in attachWorkRequests
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit

enum class TimedWorkType(val time: LocalTime) {
    EVENING(LocalTime.of(16, 0)),
    MORNING(LocalTime.of(3, 0))
}

object WorkerManager {
    fun WorkManager.enqueuePeriodicFeedWorkers() {
        TimedWorkType.entries.forEach {
            attachTimedWorkRequests(it)
        }
    }

    private fun WorkManager.attachTimedWorkRequests(it: TimedWorkType, existingWorkPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.REPLACE) {
        enqueueUniquePeriodicWork(
            it.name + SignalProcessingWorker::class.java.simpleName, // Unique name for the periodic SignalProcessingWorker
            existingWorkPolicy,
            buildPeriodicWorkRequest<SignalProcessingWorker>(it.time) // Only SignalProcessingWorker is scheduled directly as periodic
        )
        // FeedWorker is no longer enqueued directly as a periodic worker here.
        // It will be enqueued by SignalProcessingWorker upon its successful completion.
    }

    fun WorkManager.enqueueImmediateFeedWorker(
        existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
    ) {
        TimedWorkType.entries.forEach {
            attachWorkRequests(it, existingWorkPolicy)
        }
    }

    private fun WorkManager.attachWorkRequests(it: TimedWorkType, existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val signalProcessingRequest =
            buildOneTimeWorkRequest<SignalProcessingWorker>() // No delay for SignalProcessingWorker
        val feedWorkerRequest =
            buildOneTimeWorkRequest<FeedWorker>(10) // FeedWorker will start 10 minutes AFTER SignalProcessingWorker completes

        this.beginUniqueWork(
            it.name + "_ImmediateDataSyncChain", // A new unique name for this specific chain
            existingWorkPolicy, // This policy applies to the entire chain
            signalProcessingRequest
        )
            .then(feedWorkerRequest)
            .enqueue()
    }

    private inline fun <reified W: ListenableWorker> WorkManager.buildPeriodicWorkRequest(time: LocalTime? = null): PeriodicWorkRequest {
        val delay = if (time != null) {
            if (time.isAfter(LocalTime.now())) {
                Duration.between(LocalTime.now(), time).toMillis()
            } else {
                Duration.between(LocalTime.now(), time.plusHours(24)).toMillis()
            }
        } else 0

        return PeriodicWorkRequestBuilder<W>(
            12, TimeUnit.HOURS,  // Every 12 hours
            2, TimeUnit.HOURS    // 2 hour flex window
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .apply {
                        if (time != null) {
                            setRequiresCharging(true)
                        }
                    }
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(
                W::class.java.simpleName
            )
            .build()
    }

    private inline fun <reified W : ListenableWorker> WorkManager.buildOneTimeWorkRequest(
        delayMinutes: Long = 0
    ): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<W>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES) // Changed parameter name for clarity
            .addTag(
                W::class.java.simpleName
            )
            .build()
    }
}
