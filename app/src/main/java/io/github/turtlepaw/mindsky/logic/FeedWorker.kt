package io.github.turtlepaw.mindsky.logic

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetActorLikesQueryParams
import app.bsky.feed.GetFeedQueryParams
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.Post
import io.github.turtlepaw.mindsky.EmbeddedPost
import io.github.turtlepaw.mindsky.LikeVector
import io.github.turtlepaw.mindsky.ObjectBox
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.takeFrom
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.BlueskyAuthPlugin
import sh.christian.ozone.api.Did
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class FeedWorker(
    val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    enum class WorkStage(val displayName: String) {
        STARTING("Starting sync..."),
        CONNECTING_API("Connecting to Bluesky..."),
        FETCHING_TIMELINE("Fetching timeline feed..."),
        FETCHING_DISCOVERY("Fetching discovery feed..."),
        PROCESSING_POSTS("Processing posts..."),
        PULLING_LIKES("Syncing your likes..."),
        UPDATING_DATABASE("Finalizing and updating database..."),
        PAUSED_FOR_MEMORY("Paused (High Memory Usage)..."), // New stage
        COMPLETE("Sync complete.")
    }

    companion object {
        const val CHANNEL_ID = "feed_worker_channel"
        const val NOTIFICATION_ID = 1
        val WORK_NAME = "FeedWorker"
        val IMMEDIATE_WORK_NAME = "FeedWorkerImmediate"

        val TIME_EVENING = LocalTime.of(16, 0)
        val TIME_MORNING = LocalTime.of(4, 0)

        // Reduced limits for mobile devices
        private const val MAX_POSTS_PER_FEED = 100  // Reduced from 250
        private const val PROCESSING_BATCH_SIZE = 5  // Reduced from 10
        private const val MEMORY_CHECK_INTERVAL = 20 // Check memory every 20 items
        private const val MAX_MEMORY_USAGE_MB = 150  // Trigger GC if above this

        // New constants for pausing and cooldown
        private const val MEMORY_PAUSE_DURATION_MINUTES = 5L // Pause for 5 minutes
        private const val INTER_STAGE_COOLDOWN_MS =
            15_000L // 15 seconds cooldown between major stages
        private const val INTRA_LOOP_COOLDOWN_MS = 500L    // 0.5 seconds cooldown within loops


        // Enqueueing logic with better constraints
        fun WorkManager.enqueueFeedWorkers(
            existingWorkPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.REPLACE
        ) {
            val eveningWorkRequest = buildWorkRequest(TIME_EVENING)
            val morningWorkRequest = buildWorkRequest(TIME_MORNING)

            enqueueUniquePeriodicWork(WORK_NAME, existingWorkPolicy, eveningWorkRequest)
            enqueueUniquePeriodicWork(WORK_NAME, existingWorkPolicy, morningWorkRequest)
        }

        fun WorkManager.enqueueImmediateFeedWorker(
            existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
        ) {
            enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                existingWorkPolicy,
                buildImmediateWorkRequest()
            )
        }

        fun buildWorkRequest(time: LocalTime): PeriodicWorkRequest {
            val delay = if (time.isAfter(LocalTime.now())) {
                Duration.between(LocalTime.now(), time).toMillis()
            } else {
                Duration.between(LocalTime.now(), time.plusHours(24)).toMillis()
            }
            return PeriodicWorkRequestBuilder<FeedWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresCharging(true)
                        .build()
                )
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                //.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
        }

        fun buildImmediateWorkRequest(): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<FeedWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
        }

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Feed Sync",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress of background feed synchronization."
                }
                val manager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }

        // Memory monitoring helper
        private fun getCurrentMemoryUsageMB(): Long {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            return usedMemory / (1024 * 1024)
        }

        private suspend fun forceGarbageCollection() {
            withContext(Dispatchers.IO) {
                System.gc()
                delay(100) // Give GC time to work
            }
        }
    }

    init {
        createNotificationChannel(appContext)
    }

    private var lastProgressUpdate = 0L
    private suspend fun updateForegroundNotification(
        stage: WorkStage,
        progress: Int,
        indeterminate: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (now - lastProgressUpdate > 1000 || stage == WorkStage.PAUSED_FOR_MEMORY || stage == WorkStage.COMPLETE) { // Update immediately for pause/complete
            try {
                val notification = createNotification(stage, progress, indeterminate)
                setForeground(getForegroundInfo(stage, progress, indeterminate))
                setProgress(workDataOf("stage" to stage.name, "progress" to progress))
                lastProgressUpdate = now
            } catch (e: Exception) {
                Log.e("FeedWorker", "Failed to update notification", e)
            }
        }
    }

    private fun getForegroundInfo(
        stage: WorkStage,
        progress: Int,
        indeterminate: Boolean = false
    ): ForegroundInfo {
        val notification = createNotification(stage, progress, indeterminate)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return getForegroundInfo(WorkStage.STARTING, 0)
    }

    private fun createNotification(
        stage: WorkStage,
        progress: Int,
        indeterminate: Boolean = false
    ): Notification {
        val title = "Mindsky Sync"
        val contentText = if (stage == WorkStage.PAUSED_FOR_MEMORY) {
            stage.displayName
        } else if (indeterminate && stage != WorkStage.COMPLETE) {
            stage.displayName
        } else if (stage == WorkStage.COMPLETE) {
            stage.displayName
        } else {
            "${stage.displayName} ($progress%)"
        }

        val ongoing = stage != WorkStage.COMPLETE && stage != WorkStage.PAUSED_FOR_MEMORY

        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_popup_sync)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(
                100,
                progress,
                indeterminate && stage != WorkStage.COMPLETE && stage != WorkStage.PAUSED_FOR_MEMORY
            )
            .build()
    }

    // Helper to determine if a stage can be indeterminate for notification restoration
    private fun currentStageMayBeIndeterminate(stage: WorkStage): Boolean {
        return when (stage) {
            WorkStage.CONNECTING_API,
            WorkStage.PULLING_LIKES, // If progress is 0, it might have been indeterminate
            WorkStage.STARTING -> true

            WorkStage.PROCESSING_POSTS -> true // if totalPostsToProcess was 0
            else -> false
        }
    }

    private suspend fun checkMemoryAndPauseIfNeeded(
        currentStage: WorkStage,
        currentProgress: Int
    ): Boolean {
        if (getCurrentMemoryUsageMB() > MAX_MEMORY_USAGE_MB) {
            Log.w(
                "FeedWorker",
                "High memory usage (${getCurrentMemoryUsageMB()}MB). Pausing for $MEMORY_PAUSE_DURATION_MINUTES minutes."
            )
            // Ensure notification is updated immediately for pause
            val oldLastProgressUpdate = lastProgressUpdate
            lastProgressUpdate = 0L // Force update
            updateForegroundNotification(WorkStage.PAUSED_FOR_MEMORY, 0, indeterminate = true)
            lastProgressUpdate = oldLastProgressUpdate // Restore

            forceGarbageCollection() // Attempt to free memory before pause
            delay(TimeUnit.MINUTES.toMillis(MEMORY_PAUSE_DURATION_MINUTES))
            forceGarbageCollection() // Attempt to free memory after pause
            Log.i(
                "FeedWorker",
                "Resuming work after memory pause. Current usage: ${getCurrentMemoryUsageMB()}MB"
            )
            // Restore notification to the actual current stage
            val oldLastProgressUpdate2 = lastProgressUpdate
            lastProgressUpdate = 0L // Force update
            updateForegroundNotification(
                currentStage,
                currentProgress,
                currentStageMayBeIndeterminate(currentStage)
            )
            lastProgressUpdate = oldLastProgressUpdate2 // Restore
            return true // Indicates that a pause occurred
        }
        return false // No pause occurred
    }


    fun getBlueskyApi(): AuthenticatedXrpcBlueskyApi? {
        return try {
            val sessionManager = SessionManager(appContext)
            val currentSession = sessionManager.getSession()

            val initialTokens = currentSession?.let {
                BlueskyAuthPlugin.Tokens(it.accessToken, it.refreshToken)
            } ?: return null

            val authTokensFlow = MutableStateFlow(initialTokens)

            val httpClient = HttpClient(OkHttp) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            Log.v("Ktor_Default", message)
                        }
                    }
                    level = LogLevel.HEADERS
                }
                defaultRequest {
                    url.takeFrom(currentSession.host ?: "https://bsky.social")
                }
                expectSuccess = true
            }

            AuthenticatedXrpcBlueskyApi(
                initialTokens = authTokensFlow.value,
                httpClient = httpClient,
            )
        } catch (e: Exception) {
            Log.e("FeedWorker", "Failed to initialize Bluesky API", e)
            null
        }
    }

    suspend fun pullLikes(
        objectBox: BoxStore,
        api: AuthenticatedXrpcBlueskyApi,
        session: UserSession,
        postEmbedder: PostEmbedder,
        onProgress: suspend (Int) -> Unit
    ): List<LikeVector> = withContext(Dispatchers.IO) {
        val box = objectBox.boxFor(LikeVector::class.java)
        val allLikes = mutableListOf<LikeVector>()
        val existingLikes = box.all
        allLikes.addAll(existingLikes)

        val knownUris = mutableSetOf<String>().apply {
            addAll(allLikes.map { it.uri })
        }

        var cursor: String? = null
        var pagesFetched = 0
        val maxPages = 3 // Limit pages to prevent memory issues
        val estimatedTotalPages = if (maxPages > 0) maxPages else 1 // Avoid division by zero

        try {
            pageFetchLoop@ while (pagesFetched < maxPages) {
                if (isStopped) {
                    Log.i("FeedWorker", "pullLikes: Worker stopped, exiting.")
                    return@withContext allLikes // Return what we have so far
                }

                // Check memory and pause if needed before fetching a new page
                checkMemoryAndPauseIfNeeded(
                    WorkStage.PULLING_LIKES,
                    (pagesFetched * 100) / estimatedTotalPages
                )

                onProgress((pagesFetched * 100) / estimatedTotalPages)

                val response = try {
                    api.getActorLikes(
                        GetActorLikesQueryParams(
                            actor = Did(session.did),
                            limit = 50, // Reduced from 100
                            cursor = cursor
                        )
                    ).maybeResponse()
                } catch (e: Exception) {
                    Log.e("FeedWorker", "Error fetching likes", e)
                    delay(INTRA_LOOP_COOLDOWN_MS) // Cooldown on error
                    continue@pageFetchLoop // Try next page or retry after cooldown
                }

                pagesFetched++

                if (response == null || response.feed.isEmpty()) {
                    Log.d("FeedWorker", "No more likes to process (page $pagesFetched)")
                    break@pageFetchLoop
                }

                val newLikes = response.feed.filter { it.post.uri.atUri !in knownUris }
                if (newLikes.isEmpty() && response.cursor == null) {
                    Log.d("FeedWorker", "No new likes and no more pages.")
                    break@pageFetchLoop
                }

                // Process likes in smaller batches
                newLikes.chunked(5).forEach { likeBatch ->
                    if (isStopped) {
                        Log.i("FeedWorker", "pullLikes: Worker stopped during batch processing.")
                        return@withContext allLikes
                    }
                    yield() // Allow other coroutines to run

                    for (like in likeBatch) {
                        if (isStopped) {
                            Log.i("FeedWorker", "pullLikes: Worker stopped during like processing.")
                            return@withContext allLikes
                        }
                        try {
                            val post = like.post.record.decodeAs<Post>()
                            if (post.text == null || post.text.isBlank()) {
                                Log.w(
                                    "FeedWorker",
                                    "Skipping like with empty text: ${like.post.uri}"
                                )
                                continue
                            }
                            val vector = postEmbedder.encode(post.text)
                            val newVector = LikeVector(
                                uri = like.post.uri.atUri,
                                cid = like.post.cid.cid,
                                createdAt = post.createdAt.epochSeconds,
                                vector = vector,
                            )
                            box.put(newVector)
                            knownUris += newVector.uri
                            allLikes.add(newVector)
                        } catch (e: Exception) {
                            Log.e("FeedWorker", "Error processing like", e)
                        }
                    }
                    delay(INTRA_LOOP_COOLDOWN_MS) // Cooldown after each small batch
                }

                cursor = response.cursor ?: break@pageFetchLoop

                // Memory management (proactive GC)
                if (pagesFetched % 2 == 0 && getCurrentMemoryUsageMB() > MAX_MEMORY_USAGE_MB / 2) { // More proactive GC
                    forceGarbageCollection()
                }
                delay(INTRA_LOOP_COOLDOWN_MS) // Cooldown after processing a page
            }
        } catch (e: Exception) {
            Log.e("FeedWorker", "Error in pullLikes", e)
        }

        onProgress(100)
        allLikes
    }

    suspend fun getFamiliarFeeds(
        api: AuthenticatedXrpcBlueskyApi,
        minPostsPerFeed: Int = MAX_POSTS_PER_FEED,
        onTimelineProgress: suspend (fetchedCount: Int, targetCount: Int) -> Unit,
        onDiscoveryProgress: suspend (fetchedCount: Int, targetCount: Int) -> Unit
    ): List<FeedViewPost> = withContext(Dispatchers.IO) {
        val MIN_DISCOVERY_POSTS = 150
        val MIN_TIMELINE_POSTS = 50
        val postsPerRequest = 100L // Maximum posts per request

        // Fetch Following Feed (Timeline)
        val allFollowingPosts = mutableListOf<FeedViewPost>()
        var followingCursor: String? = null
        var timelineFetchComplete = false

        try {
            timelineLoop@ while (allFollowingPosts.size < MIN_TIMELINE_POSTS && !timelineFetchComplete) {
                if (isStopped) {
                    Log.i("FeedWorker", "getFamiliarFeeds (Timeline): Worker stopped.")
                    break@timelineLoop
                }
                checkMemoryAndPauseIfNeeded(
                    WorkStage.FETCHING_TIMELINE,
                    (allFollowingPosts.size * 100) / MIN_TIMELINE_POSTS.coerceAtLeast(1)
                )

                // Existing memory check
                if (getCurrentMemoryUsageMB() > MAX_MEMORY_USAGE_MB) {
                    Log.w("FeedWorker", "Memory pressure, stopping timeline fetch")
                    break@timelineLoop
                }

                val response = try {
                    api.getTimeline(
                        GetTimelineQueryParams(
                            algorithm = "reverse-chronological",
                            limit = postsPerRequest,
                            cursor = followingCursor
                        )
                    ).maybeResponse()
                } catch (e: Exception) {
                    Log.e("FeedWorker", "Error fetching timeline", e)
                    delay(1000 + INTRA_LOOP_COOLDOWN_MS) // Add retry delay + cooldown
                    continue@timelineLoop // Retry on error
                }

                if (response == null) {
                    delay(1000 + INTRA_LOOP_COOLDOWN_MS) // Add retry delay on null response + cooldown
                    continue@timelineLoop
                }

                if (response.feed.isEmpty()) {
                    timelineFetchComplete = true
                } else {
                    allFollowingPosts.addAll(response.feed)
                    followingCursor = response.cursor
                    if (followingCursor == null) {
                        timelineFetchComplete = true
                    }
                }
                onTimelineProgress(allFollowingPosts.size, MIN_TIMELINE_POSTS)
                yield() // Allow other coroutines to run
                delay(INTRA_LOOP_COOLDOWN_MS) // Cooldown after each page fetch
            }
        } catch (e: Exception) {
            Log.e("FeedWorker", "Error in timeline fetch", e)
        }

        onTimelineProgress(allFollowingPosts.size, MIN_TIMELINE_POSTS) // Final progress update
        val cleanedFollowingFeed = try {
            FeedTuner.cleanReplies(allFollowingPosts)
        } catch (e: Exception) {
            Log.e("FeedWorker", "Error cleaning replies", e)
            allFollowingPosts
        }

        // Fetch Discover Feed
        val allDiscoveryPosts = mutableListOf<FeedViewPost>()
        var discoveryCursor: String? = null
        var discoveryFetchComplete = false
        var retryCount = 0
        val maxRetries = 3
        val discoverFeedUri =
            AtUri("at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot")

        try {
            discoveryLoop@ while (allDiscoveryPosts.size < MIN_DISCOVERY_POSTS && !discoveryFetchComplete && retryCount < maxRetries) {
                if (isStopped) {
                    Log.i("FeedWorker", "getFamiliarFeeds (Discovery): Worker stopped.")
                    break@discoveryLoop
                }
                checkMemoryAndPauseIfNeeded(
                    WorkStage.FETCHING_DISCOVERY,
                    (allDiscoveryPosts.size * 100) / MIN_DISCOVERY_POSTS.coerceAtLeast(1)
                )
                
                if (getCurrentMemoryUsageMB() > MAX_MEMORY_USAGE_MB) {
                    Log.w(
                        "FeedWorker",
                        "Memory pressure at ${allDiscoveryPosts.size}/${MIN_DISCOVERY_POSTS} posts"
                    )
                    break@discoveryLoop
                }

                val remainingPosts = MIN_DISCOVERY_POSTS - allDiscoveryPosts.size
                val requestLimit = minOf(remainingPosts.toLong(), postsPerRequest)

                val response = try {
                    api.getFeed(
                        GetFeedQueryParams(
                            feed = discoverFeedUri,
                            limit = requestLimit,
                            cursor = discoveryCursor
                        )
                    ).maybeResponse()
                } catch (e: Exception) {
                    Log.e(
                        "FeedWorker",
                        "Error fetching discovery feed, attempt ${retryCount + 1}/$maxRetries",
                        e
                    )
                    delay(1000 + INTRA_LOOP_COOLDOWN_MS) // Add retry delay + cooldown
                    retryCount++
                    continue@discoveryLoop
                }

                if (response == null) {
                    retryCount++
                    delay(1000 + INTRA_LOOP_COOLDOWN_MS)
                    continue@discoveryLoop
                }

                if (response.feed.isEmpty()) {
                    if (allDiscoveryPosts.size < MIN_DISCOVERY_POSTS && discoveryCursor != null) { // only retry if there was a cursor, meaning more pages might exist
                        retryCount++
                        delay(1000 + INTRA_LOOP_COOLDOWN_MS)
                        continue@discoveryLoop
                    }
                    discoveryFetchComplete = true
                } else {
                    allDiscoveryPosts.addAll(response.feed)
                    discoveryCursor = response.cursor
                    if (discoveryCursor == null) {
                        if (allDiscoveryPosts.size < MIN_DISCOVERY_POSTS) { // if no cursor and not enough posts, try again
                            retryCount++
                            delay(1000 + INTRA_LOOP_COOLDOWN_MS)
                            continue@discoveryLoop
                        }
                        discoveryFetchComplete = true
                    }
                    // Reset retry count on successful fetch
                    retryCount = 0
                }
                onDiscoveryProgress(allDiscoveryPosts.size, MIN_DISCOVERY_POSTS)
                yield() // Allow other coroutines to run
                delay(INTRA_LOOP_COOLDOWN_MS) // Cooldown after each page fetch
            }
        } catch (e: Exception) {
            Log.e("FeedWorker", "Error in discovery fetch", e)
        }

        if (allDiscoveryPosts.size < MIN_DISCOVERY_POSTS) {
            Log.w(
                "FeedWorker",
                "Failed to meet minimum discovery posts requirement: ${allDiscoveryPosts.size}/${MIN_DISCOVERY_POSTS}"
            )
        }

        onDiscoveryProgress(allDiscoveryPosts.size, MIN_DISCOVERY_POSTS) // Final progress update

        cleanedFollowingFeed + allDiscoveryPosts
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            setForeground(getForegroundInfo(WorkStage.STARTING, 0))
            updateForegroundNotification(WorkStage.STARTING, 0)

            if (isStopped) {
                Log.i(
                    "FeedWorker",
                    "doWork: Worker stopped at start."
                ); return@withContext Result.failure()
            }
            checkMemoryAndPauseIfNeeded(WorkStage.STARTING, 0)
            delay(INTER_STAGE_COOLDOWN_MS)


            updateForegroundNotification(WorkStage.CONNECTING_API, 0, indeterminate = true)
            val api = getBlueskyApi()
            if (api == null) {
                Log.e("FeedWorker", "Bluesky API not initialized")
                return@withContext Result.failure()
            }
            updateForegroundNotification(WorkStage.CONNECTING_API, 100, indeterminate = false)

            if (isStopped) {
                Log.i(
                    "FeedWorker",
                    "doWork: Worker stopped after API init."
                ); return@withContext Result.failure()
            }
            checkMemoryAndPauseIfNeeded(WorkStage.CONNECTING_API, 100)
            delay(INTER_STAGE_COOLDOWN_MS)


            val postEmbedder = try {
                PostEmbedder(appContext)
            } catch (e: Exception) {
                Log.e("FeedWorker", "Failed to initialize PostEmbedder", e)
                return@withContext Result.failure()
            }

            if (isStopped) {
                Log.i(
                    "FeedWorker",
                    "doWork: Worker stopped after PostEmbedder init."
                ); return@withContext Result.failure()
            }
            checkMemoryAndPauseIfNeeded(
                WorkStage.PROCESSING_POSTS,
                0
            ) // Placeholder progress before fetching
            delay(INTER_STAGE_COOLDOWN_MS)

            // Fetch feeds with reduced limits
            val familiarFeed = getFamiliarFeeds(
                api = api,
                minPostsPerFeed = MAX_POSTS_PER_FEED,
                onTimelineProgress = { fetchedCount, targetCount ->
                    val progress =
                        if (targetCount > 0) (fetchedCount * 100 / targetCount).coerceAtMost(100) else 100
                    updateForegroundNotification(
                        WorkStage.FETCHING_TIMELINE,
                        progress,
                        indeterminate = false
                    )
                },
                onDiscoveryProgress = { fetchedCount, targetCount ->
                    val progress =
                        if (targetCount > 0) (fetchedCount * 100 / targetCount).coerceAtMost(100) else 100
                    updateForegroundNotification(
                        WorkStage.FETCHING_DISCOVERY,
                        progress,
                        indeterminate = false
                    )
                }
            )
            if (isStopped) {
                Log.i(
                    "FeedWorker",
                    "doWork: Worker stopped after getFamiliarFeeds."
                ); return@withContext Result.failure()
            }
            checkMemoryAndPauseIfNeeded(
                WorkStage.FETCHING_DISCOVERY,
                100
            ) // Assuming discovery is last part of getFamiliarFeeds
            delay(INTER_STAGE_COOLDOWN_MS)


            val totalPostsToProcess = familiarFeed.size
            updateForegroundNotification(
                WorkStage.PROCESSING_POSTS,
                0,
                indeterminate = totalPostsToProcess == 0
            )

            val objectBox = if (ObjectBox.store == null) {
                ObjectBox.init(appContext)
            } else {
                ObjectBox.store
            }

            val currentSession = SessionManager(appContext).getSession()
            if (currentSession == null) {
                Log.e("FeedWorker", "User session not found.")
                return@withContext Result.failure()
            }

            if (isStopped) {
                Log.i(
                    "FeedWorker",
                    "doWork: Worker stopped before pullLikes."
                ); return@withContext Result.failure()
            }
            checkMemoryAndPauseIfNeeded(
                WorkStage.PULLING_LIKES,
                0
            ) // Progress for PULLING_LIKES starts at 0

            updateForegroundNotification(WorkStage.PULLING_LIKES, 0, indeterminate = true)
            val likes = pullLikes(objectBox, api, currentSession, postEmbedder) { likeProgress ->
                updateForegroundNotification(
                    WorkStage.PULLING_LIKES,
                    likeProgress,
                    indeterminate = false
                )
            }
            updateForegroundNotification(WorkStage.PULLING_LIKES, 100, indeterminate = false)

            if (isStopped) {
                Log.i(
                    "FeedWorker",
                    "doWork: Worker stopped after pullLikes."
                ); return@withContext Result.failure()
            }
            checkMemoryAndPauseIfNeeded(WorkStage.PULLING_LIKES, 100)
            delay(INTER_STAGE_COOLDOWN_MS)


            // Clear existing posts
            val postEmbedderBox = objectBox.boxFor(EmbeddedPost::class.java)
            postEmbedderBox.removeAll()

            // Process posts in smaller batches with memory management
            familiarFeed.chunked(PROCESSING_BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                if (isStopped) {
                    Log.i(
                        "FeedWorker",
                        "doWork: Worker stopped during batch processing."
                    ); return@withContext Result.failure()
                } // Needs to return from doWork

                checkMemoryAndPauseIfNeeded(
                    WorkStage.PROCESSING_POSTS,
                    (batchIndex * PROCESSING_BATCH_SIZE * 100) / totalPostsToProcess.coerceAtLeast(1)
                )

                try {
                    // Check memory before processing batch - redundant if checkMemoryAndPauseIfNeeded is called above
                    // if (getCurrentMemoryUsageMB() > MAX_MEMORY_USAGE_MB) {
                    //     forceGarbageCollection()
                    // }

                    val batchEmbeddings = mutableListOf<EmbeddedPost>()

                    batch.forEachIndexed { indexInBatch, feedViewPost ->
                        if (isStopped) { /* Inner loop stop, handled by outer isStopped check or let it finish batch */
                        }

                        try {
                            val globalIndex = (batchIndex * PROCESSING_BATCH_SIZE) + indexInBatch
                            val progress =
                                if (totalPostsToProcess > 0) (globalIndex + 1) * 100 / totalPostsToProcess else 0
                            updateForegroundNotification(WorkStage.PROCESSING_POSTS, progress)

                            val postView = feedViewPost.post
                            val post = postView.record.decodeAs<Post>()

                            if (post.text == null || post.text.isBlank()) {
                                Log.w(
                                    "FeedWorker",
                                    "Skipping post with empty text: ${postView.uri}"
                                )
                                return@forEachIndexed
                            }

                            val embedding = postEmbedder.encode(post.text)

                            batchEmbeddings.add(
                                EmbeddedPost(
                                    uri = postView.uri.atUri,
                                    text = post.text,
                                    embedding = embedding,
                                    authorDid = postView.author.did.did,
                                    timestamp = post.createdAt.epochSeconds,
                                    score = 0f
                                )
                            )

                            // Yield control periodically
                            if (indexInBatch % 2 == 0) {
                                yield()
                            }
                        } catch (e: Exception) {
                            Log.e("FeedWorker", "Error processing post", e)
                        }
                    }

                    if (isStopped) {
                        Log.i(
                            "FeedWorker",
                            "doWork: Worker stopped before DB update in batch."
                        ); return@withContext Result.failure()
                    }

                    // Database update
                    updateForegroundNotification(
                        WorkStage.UPDATING_DATABASE,
                        (batchIndex * 100) / ((totalPostsToProcess + PROCESSING_BATCH_SIZE - 1) / PROCESSING_BATCH_SIZE).coerceAtLeast(
                            1
                        ),
                        indeterminate = false
                    )

                    val scoredBatch = batchEmbeddings.mapNotNull { post ->
                        try {
                            val score = FeedRanker.calculatePostScore(post, likes)
                            post.copy(score = score)
                        } catch (e: Exception) {
                            Log.e("FeedWorker", "Error calculating score", e)
                            null
                        }
                    }

                    scoredBatch.filter { it.score != null }
                        .sortedByDescending { it.score!! + (Random.nextFloat() * 0.1f) } // Add jitter
                        .forEach {
                            if (isStopped) { /* Inner loop stop, let it finish batch or handled by outer isStopped */
                            }
                            try {
                                postEmbedderBox.put(it)
                            } catch (e: Exception) {
                                Log.e("FeedWorker", "Error saving post", e)
                            }
                        }

                    batchEmbeddings.clear()

                    // Periodic garbage collection - redundant if checkMemoryAndPauseIfNeeded is active
                    // if (batchIndex % MEMORY_CHECK_INTERVAL == 0) {
                    //     forceGarbageCollection()
                    // }
                    delay(INTRA_LOOP_COOLDOWN_MS) // Cooldown after each batch processing

                } catch (e: Exception) {
                    Log.e("FeedWorker", "Error processing batch $batchIndex", e)
                }
                if (isStopped) {
                    Log.i(
                        "FeedWorker",
                        "doWork: Worker stopped after batch $batchIndex."
                    ); return@withContext Result.failure()
                }
            }

            updateForegroundNotification(WorkStage.UPDATING_DATABASE, 100, indeterminate = false)
            updateForegroundNotification(WorkStage.COMPLETE, 100)

            // Final cleanup
            forceGarbageCollection()

            Log.i("FeedWorker", "doWork: Sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e("FeedWorker", "Fatal error in doWork", e)
            // Consider if retry is appropriate or if error is due to bad state/data
            if (isStopped) { // If stopped due to external request while in fatal error block
                Log.i("FeedWorker", "doWork: Worker stopped during fatal error handling.")
                return@withContext Result.failure()
            }
            Result.retry() // Or Result.failure() depending on error type
        } finally {
            // Ensure any resources like PostEmbedder are closed if necessary,
            // though PostEmbedder might not have a close() method if it's context-bound for embeddings.
            // postEmbedder.close() // If applicable
        }
    }
}
