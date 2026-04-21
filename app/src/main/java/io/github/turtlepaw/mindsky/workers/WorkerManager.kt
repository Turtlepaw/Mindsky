package io.github.turtlepaw.mindsky.workers

// FeedWorker import is still needed for buildOneTimeWorkRequest in attachWorkRequests
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
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
            scheduleSignalProcessingWorker(it)
        }
    }

    private fun WorkManager.scheduleSignalProcessingWorker(
        it: TimedWorkType,
        existingWorkPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.REPLACE
    ) {
        enqueueUniquePeriodicWork(
            it.name + SignalProcessingWorker::class.java.simpleName,
            existingWorkPolicy,
            buildPeriodicWorkRequest<SignalProcessingWorker>(it.time)
                .setInputData(
                    Data.Builder()
                        .putBoolean(SignalProcessingWorker.SHOULD_ENQUEUE_FEED_WORKER, true)
                        .build()
                )
                .build()// Only SignalProcessingWorker is scheduled directly as periodic
        )
    }

    fun WorkManager.enqueueImmediateWorkers(
        existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
    ) {
//        TimedWorkType.entries.forEach {
//
//        }
        attachWorkRequests(existingWorkPolicy)
    }

    private fun WorkManager.attachWorkRequests(existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val signalProcessingRequest =
            buildOneTimeWorkRequest<SignalProcessingWorker>()
                .setInputData(
                    Data.Builder()
                        .putBoolean(SignalProcessingWorker.SHOULD_ENQUEUE_FEED_WORKER, false)
                        .build()
                )
                .build() // No delay for SignalProcessingWorker
        val feedWorkerRequest =
            getFeedWorkerRequest(0) // FeedWorker will start 10 minutes AFTER SignalProcessingWorker completes

        this.beginUniqueWork(
            "ImmediateDataSyncChain", // A new unique name for this specific chain
            existingWorkPolicy, // This policy applies to the entire chain
            signalProcessingRequest
        )
            .then(feedWorkerRequest)
            .enqueue()
    }

    private inline fun <reified W : ListenableWorker> WorkManager.buildPeriodicWorkRequest(time: LocalTime? = null): PeriodicWorkRequest.Builder {
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
    }

    fun WorkManager.getFeedWorkerRequest(
        delayMinutes: Long = 0
    ): OneTimeWorkRequest {
        return buildOneTimeWorkRequest<FeedDiscoveryWorker>(delayMinutes)
            .build()
    }

    private inline fun <reified W : ListenableWorker> WorkManager.buildOneTimeWorkRequest(
        delayMinutes: Long = 0
    ): OneTimeWorkRequest.Builder {
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
    }
}
