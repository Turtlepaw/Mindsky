package io.github.turtlepaw.mindsky.workers

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetActorLikesQueryParams
import app.bsky.feed.GetActorLikesResponse
import app.bsky.feed.Post
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import io.github.turtlepaw.mindsky.db.Engagement
import io.github.turtlepaw.mindsky.db.EngagementType
import io.github.turtlepaw.mindsky.logic.PostEmbedder
import io.github.turtlepaw.mindsky.logic.ranking.InterestCluster
import io.github.turtlepaw.mindsky.logic.ranking.InterestClusterer
import io.github.turtlepaw.mindsky.workers.WorkerManager.getFeedWorkerRequest
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.Did

class SignalProcessingWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val CHANNEL_ID = "signal_processing_channel"
        const val SHOULD_ENQUEUE_FEED_WORKER = "should_enqueue_feed_worker"

        // Time management constants
        private const val MAX_EXECUTION_TIME_MS = 9 * 60 * 1000L // 9 minutes
        private const val TIME_BUFFER_MS = 30 * 1000L // 30 seconds buffer for cleanup
        private const val EFFECTIVE_MAX_TIME_MS = MAX_EXECUTION_TIME_MS - TIME_BUFFER_MS

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Signal Processing",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress of background signal processing tasks."
                }
                val manager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }
    }

    enum class Stage(val displayName: String) {
        INITIALIZING("Initializing"),
        FETCHING_LIKES("Fetching Likes"),
        PROCESSING_LIKES("Processing Likes"),
        COMPLETED("Completed"),
        TIME_LIMIT_REACHED("Time Limit Reached")
    }

    private val logTag = "SignalProcessingWorker"
    private val startTime = System.currentTimeMillis()

    /**
     * Checks if we're approaching the time limit
     */
    private fun isTimeRunningOut(): Boolean {
        val elapsedTime = System.currentTimeMillis() - startTime
        return elapsedTime >= EFFECTIVE_MAX_TIME_MS
    }

    /**
     * Gets remaining time in milliseconds
     */
    private fun getRemainingTime(): Long {
        val elapsedTime = System.currentTimeMillis() - startTime
        return (EFFECTIVE_MAX_TIME_MS - elapsedTime).coerceAtLeast(0)
    }

    /**
     * Logs time status for debugging
     */
    private fun logTimeStatus(context: String) {
        val elapsedTime = System.currentTimeMillis() - startTime
        val remainingTime = getRemainingTime()
        Log.d(
            logTag,
            "$context - Elapsed: ${elapsedTime / 1000}s, Remaining: ${remainingTime / 1000}s"
        )
    }

    private fun initializeLikeData(box: Box<Engagement>): Pair<MutableList<Engagement>, MutableSet<String>> {
        if (isTimeRunningOut()) {
            Log.w(logTag, "Time running out during initialization")
            return Pair(mutableListOf(), mutableSetOf())
        }

        val allLikes = mutableListOf<Engagement>()
        val existingLikes = box.all
        allLikes.addAll(existingLikes)
        val knownUris = mutableSetOf<String>().apply {
            addAll(allLikes.map { it.uri })
        }
        Log.d(logTag, "Initialized like data. Found ${existingLikes.size} existing likes.")
        logTimeStatus("After initialization")
        return Pair(allLikes, knownUris)
    }

    private suspend fun fetchLikesPageInternal(
        api: AuthenticatedXrpcBlueskyApi,
        userDid: Did,
        cursor: String?
    ): GetActorLikesResponse? {
        if (isTimeRunningOut()) {
            Log.w(logTag, "Time running out, skipping API call")
            return null
        }

        return try {
            api.getActorLikes(
                GetActorLikesQueryParams(
                    actor = userDid,
                    limit = WorkerCommon.API_REQUEST_LIMIT,
                    cursor = cursor
                )
            ).maybeResponse()
        } catch (e: Exception) {
            Log.e(logTag, "Error fetching likes page. Cursor: $cursor", e)
            null
        }
    }

    private suspend fun processLikesBatch(
        likeViewsToProcess: List<FeedViewPost>,
        box: Box<Engagement>,
        knownUris: MutableSet<String>,
        postEmbedder: PostEmbedder = PostEmbedder(appContext),
        onProgress: (Int) -> Unit
    ): List<Engagement> {
        if (likeViewsToProcess.isEmpty() || isTimeRunningOut()) {
            Log.w(logTag, "Skipping batch processing - empty list or time running out")
            return emptyList()
        }

        val likesToEmbedAndStore = mutableListOf<Engagement>()
        val processedEngagements = mutableListOf<Engagement>()

        // Calculate time-based processing limit
        val remainingTimeMs = getRemainingTime()
        val estimatedTimePerPost = 2500L // 2.5 seconds per post (including delay)
        val maxPostsBasedOnTime = (remainingTimeMs / estimatedTimePerPost).toInt()
        val postsToProcess = minOf(likeViewsToProcess.size, 150, maxPostsBasedOnTime)

        Log.d(
            logTag,
            "Processing $postsToProcess posts (time-limited from ${likeViewsToProcess.size})"
        )

        for (i in 0 until postsToProcess) {
            if (isStopped || isTimeRunningOut()) {
                Log.i(logTag, "Stopping batch processing - worker stopped or time limit reached")
                break
            }

            val likeView = likeViewsToProcess[i]

            try {
                val post = likeView.post.record.decodeAs<Post>()
                if (post == null) {
                    Log.w(logTag, "Skipping like, post record is null: ${likeView.post.uri}")
                    continue
                }

                if (post.text.isNullOrBlank()) {
                    continue
                }

                val vector = postEmbedder.encode(post.text)
                val newVector = Engagement(
                    uri = likeView.post.uri.atUri,
                    cid = likeView.post.cid.cid,
                    createdAt = post.createdAt.epochSeconds,
                    embedding = vector,
                    text = post.text,
                    authorDid = likeView.post.author.did.did,
                    type = EngagementType.Like,
                )

                processedEngagements.add(newVector)
                box.put(newVector)
                knownUris.add(newVector.uri)

                onProgress((i * 100) / postsToProcess.coerceAtLeast(1))

                // Adaptive delay based on remaining time
                val adaptiveDelay = if (getRemainingTime() > 120_000L) 2_000L else 1_000L
                delay(adaptiveDelay)

            } catch (e: Exception) {
                Log.e(logTag, "Error processing like for embedding: ${likeView.post.uri}", e)
            }
        }

        logTimeStatus("After processing batch")
        return processedEngagements
    }

    private suspend fun getLikes(
        api: AuthenticatedXrpcBlueskyApi,
        session: UserSession,
        knownUris: MutableSet<String>,
        onProgress: suspend (Int) -> Unit
    ): List<FeedViewPost> = withContext(Dispatchers.IO) {
        var cursor: String? = null
        var pagesFetched = 0
        val allLikes = mutableListOf<FeedViewPost>()
        val fetchedLikes = mutableListOf<FeedViewPost>()

        // Calculate time-based page limit
        val remainingTimeMs = getRemainingTime()
        val estimatedTimePerPage =
            (WorkerCommon.THERMAL_COOLDOWN_MS + 1000L) // Cooldown + API call time
        val maxPagesBasedOnTime = (remainingTimeMs / estimatedTimePerPage).toInt()
        val effectiveMaxPages = minOf(WorkerCommon.MAX_PAGES_TO_FETCH_LIKES, maxPagesBasedOnTime)

        Log.d(
            logTag,
            "Fetching max $effectiveMaxPages pages (time-limited from ${WorkerCommon.MAX_PAGES_TO_FETCH_LIKES})"
        )

        try {
            while (pagesFetched < effectiveMaxPages && !isTimeRunningOut()) {
                if (isStopped) {
                    Log.i(logTag, "Worker stopped, exiting likes fetch loop.")
                    break
                }

                val progress = (pagesFetched * 100) / effectiveMaxPages.coerceAtLeast(1)
                onProgress(progress)

                val response = fetchLikesPageInternal(api, Did(session.did), cursor)
                val pageLikes = response?.feed ?: emptyList()

                if (response == null) {
                    Log.w(logTag, "Failed to fetch likes page ${pagesFetched + 1}. Cooling down...")
                    delay(WorkerCommon.THERMAL_COOLDOWN_MS)
                    pagesFetched++
                    continue
                }

                fetchedLikes.addAll(pageLikes)
                allLikes.addAll(pageLikes.filter { it.post.uri.atUri !in knownUris })

                if (pageLikes.isEmpty() && response.cursor == null) {
                    Log.d(logTag, "No new likes and no more pages.")
                    break
                }

                cursor = response.cursor ?: break
                pagesFetched++

                // Adaptive cooldown based on remaining time
                val adaptiveCooldown = if (getRemainingTime() > 180_000L) {
                    WorkerCommon.THERMAL_COOLDOWN_MS
                } else {
                    WorkerCommon.THERMAL_COOLDOWN_MS / 2
                }
                delay(adaptiveCooldown)
            }
        } catch (e: Exception) {
            Log.e(logTag, "Exception while fetching likes", e)
        } finally {
            val timeoutReason = if (isTimeRunningOut()) " (time limit reached)" else ""
            Log.i(
                logTag,
                "Likes fetch complete$timeoutReason. Total pages: $pagesFetched. Total new likes: ${allLikes.size}. Total fetched likes: ${fetchedLikes.size}."
            )
            onProgress(100)
            logTimeStatus("After fetching likes")
        }
        return@withContext fetchedLikes
    }

    private suspend fun checkIfWorkerAlreadyRunning(): Boolean {
        val workManager = WorkManager.getInstance(appContext)
        val existingWork = workManager.getWorkInfos(
            WorkQuery.fromTags(this::class.java.simpleName)
        ).get()

        val runningWorkers = existingWork.filter { it.state == WorkInfo.State.RUNNING }

        if (runningWorkers.isEmpty()) return false

        // Sort by UUID to get deterministic ordering
        val sortedWorkers = runningWorkers.sortedBy { it.id.toString() }
        val highestPriorityWorker = sortedWorkers.first()

        Log.d(logTag, "Highest priority worker: ${highestPriorityWorker.id}, current: ${this.id}")

        // Only continue if this worker has the highest priority
        return highestPriorityWorker.id != this.id
    }

    override suspend fun doWork(): Result {
        Log.d(
            logTag,
            "SignalProcessingWorker started with ${MAX_EXECUTION_TIME_MS / 1000}s time limit."
        )
        logTimeStatus("Worker start")

        val wakeLock: PowerManager.WakeLock =
            (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SignalProcessingWorker::MainWakeLock")
            }

        wakeLock.apply {
            return try {
                // Acquire wake lock to keep CPU running
                acquire(10 * 60 * 1000L /*10 minutes*/)

                createNotificationChannel(appContext)
                val objectBoxStore = WorkerCommon.safelyGetObjectBox(appContext)
                val sessionManager = SessionManager(appContext)
                val session = sessionManager.getSession()

                if (session == null) {
                    Log.e(logTag, "No active session found. Worker cannot proceed.")
                    return Result.failure()
                }

                val blueskyApi = WorkerCommon.getBlueskyApi(sessionManager)
                if (blueskyApi !is AuthenticatedXrpcBlueskyApi) {
                    Log.e(logTag, "Bluesky API is not authenticated, cannot proceed.")
                    return Result.failure()
                }

                if (isTimeRunningOut()) {
                    Log.w(logTag, "Time limit reached before main processing")
                    return Result.success() // Return success to avoid retry
                }

                val (allLikesFromDb, knownUris) = initializeLikeData(
                    objectBoxStore.boxFor(
                        Engagement::class.java
                    )
                )

                if (isTimeRunningOut()) {
                    Log.w(logTag, "Time limit reached after initialization")
                    return Result.success()
                }

                Log.d(logTag, "Starting to pull likes for user: ${session.did}")
                val likedPosts = getLikes(
                    blueskyApi, session, knownUris, getProgressCallback(Stage.FETCHING_LIKES)
                ).filter { like ->
                    like.post.uri.atUri !in allLikesFromDb.map { it.uri }
                }

                if (checkIfWorkerAlreadyRunning()) {
                    Log.i(logTag, "Another embedding worker is running, skipping...")
                    return Result.success()
                }

                if (isTimeRunningOut()) {
                    Log.w(logTag, "Time limit reached before processing likes")
                    return Result.success()
                }

                Log.d(logTag, "Fetched ${likedPosts.size} new likes for user: ${session.did}")
                val allProcessedLikes = processLikesBatch(
                    likedPosts,
                    objectBoxStore.boxFor(Engagement::class.java),
                    knownUris,
                    onProgress = getProgressCallback(Stage.PROCESSING_LIKES)
                )

                val allLikes = allLikesFromDb + allProcessedLikes

                // Only do clustering if we have time and processed likes
                if (allLikes.isNotEmpty()) {
                    Log.d(logTag, "Creating interest clusters...")
                    val clusterer = InterestClusterer()
                    val userProfile = clusterer.createClusters(allLikes)

                    // Store clusters
                    objectBoxStore.boxFor(InterestCluster::class.java)
                        .apply {
                            removeAll()
                            put(userProfile.first)
                        }

                    Log.d(
                        logTag,
                        "Interest clusters created and stored (${userProfile.first.size} clusters / ${userProfile.second})."
                    )
                } else {
                    Log.w(
                        logTag,
                        "Skipping clustering due to no processed likes"
                    )
                }

                // Final time check before enqueueing next worker
                val shouldEnqueueFeedWorker = inputData.getBoolean(SHOULD_ENQUEUE_FEED_WORKER, true)

                if (shouldEnqueueFeedWorker) {
                    WorkManager.getInstance(applicationContext).apply {
                        val request = getFeedWorkerRequest(8)
                        enqueueUniqueWork(
                            "FeedWorker_Periodic_Chained",
                            ExistingWorkPolicy.REPLACE,
                            request
                        )
                    }
                    Log.i(logTag, "Enqueued FeedWorker to run.")
                }

                val totalElapsedTime = System.currentTimeMillis() - startTime
                Log.d(
                    logTag,
                    "SignalProcessingWorker finished successfully in ${totalElapsedTime / 1000}s."
                )

                Result.success()
            } catch (e: NotImplementedError) {
                Log.e(logTag, "Worker failed due to unimplemented dependency: ${e.message}", e)
                Result.failure()
            } catch (e: Exception) {
                Log.e(logTag, "Error in doWork", e)
                Result.failure()
            } finally {
                // Ensure wake lock is released
                if (wakeLock.isHeld) {
                    release()
                    Log.d(logTag, "Wake lock released.")
                }
            }
        }
    }

    private fun createNotification(
        stage: Stage,
        progress: Int,
        indeterminate: Boolean = false
    ): Notification {
        val title = "Mindsky Signal Processing"
        val remainingTime = getRemainingTime() / 1000
        val contentText = if (indeterminate && stage != Stage.COMPLETED) {
            "${stage.displayName} (${remainingTime}s left)"
        } else if (stage == Stage.COMPLETED) {
            stage.displayName
        } else {
            "${stage.displayName} ($progress%) - ${remainingTime}s left"
        }
        val ongoing = stage != Stage.COMPLETED
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_popup_sync)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate && stage != Stage.COMPLETED)
            .build()
    }

    fun getProgressCallback(stage: Stage): (Int) -> Unit = { progress ->
        createNotification(stage, progress)
        setProgressAsync(
            workDataOf(
                WorkerCommon.PROGRESS to progress,
                WorkerCommon.STAGE to stage.name
            )
        )
        Log.d(logTag, "Progress updated: $progress%")
    }
}