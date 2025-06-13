package io.github.turtlepaw.mindsky.logic

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
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

        // PAUSED_FOR_MEMORY removed as the system is changed
        COMPLETE("Sync complete.")
    }

    companion object {
        const val CHANNEL_ID = "feed_worker_channel"
        const val NOTIFICATION_ID = 1
        val WORK_NAME = "FeedWorker"
        val IMMEDIATE_WORK_NAME = "FeedWorkerImmediate"

        val TIME_EVENING = LocalTime.of(16, 0)
        val TIME_MORNING = LocalTime.of(4, 0)

        // Optimized constants
        private const val MAX_POSTS_PER_FEED = 250 // Increased for more comprehensive fetching
        private const val PROCESSING_BATCH_SIZE = 50 // Increased for DB operations
        private const val API_REQUEST_LIMIT = 100L // Standard API limit for pagination
        private const val THERMAL_COOLDOWN_MS = 200L // Short delay for CPU cooling

        // MAX_MEMORY_USAGE_MB can be kept as a general guideline if needed elsewhere, but not for pausing.
        // private const val MAX_MEMORY_USAGE_MB = 150

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

        // getCurrentMemoryUsageMB can be kept if needed for logging or very specific checks,
        // but it's no longer driving the pausing logic.
        /*
        private fun getCurrentMemoryUsageMB(): Long {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            return usedMemory / (1024 * 1024)
        }
        */

        private suspend fun forceGarbageCollection() {
            // Use sparingly, if at all. Modern GCs are generally good.
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
    private suspend fun updateProgressNotification(
        stage: WorkStage,
        progress: Int,
        indeterminate: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        // Update more frequently if needed, or stick to ~1 second interval unless it's a final stage
        if (now - lastProgressUpdate > 1000 || stage == WorkStage.COMPLETE) {
            try {
                val notification = createNotification(stage, progress, indeterminate)
                val notificationManager =
                    appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
                setProgress(workDataOf("stage" to stage.name, "progress" to progress))
                lastProgressUpdate = now
            } catch (e: Exception) {
                Log.e("FeedWorker", "Failed to update notification", e)
            }
        }
    }

    private fun createNotification(
        stage: WorkStage,
        progress: Int,
        indeterminate: Boolean = false
    ): Notification {
        val title = "Mindsky Sync"
        val contentText = if (indeterminate && stage != WorkStage.COMPLETE) {
            stage.displayName
        } else if (stage == WorkStage.COMPLETE) {
            stage.displayName
        } else {
            "${stage.displayName} ($progress%)"
        }

        val ongoing = stage != WorkStage.COMPLETE // Notification is ongoing until complete

        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_popup_sync)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(
                100,
                progress,
                indeterminate && stage != WorkStage.COMPLETE
            )
            .build()
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
                    level = LogLevel.HEADERS // Or LogLevel.NONE for less verbosity in production
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
        onProgress: suspend (Int) -> Unit // Progress callback can be simplified or removed if not granularly needed
    ): List<LikeVector> = withContext(Dispatchers.IO) {
        val box = objectBox.boxFor(LikeVector::class.java)
        val allLikes = mutableListOf<LikeVector>()
        // Consider not loading all existing likes into memory if the list can be very large.
        // Instead, check for existence before putting new ones.
        // For now, keeping existing logic for simplicity.
        val existingLikes = box.all
        allLikes.addAll(existingLikes)

        val knownUris = mutableSetOf<String>().apply {
            addAll(allLikes.map { it.uri })
        }

        var cursor: String? = null
        var pagesFetched = 0
        // Limit total pages to fetch to prevent runaway loops, adjust as needed.
        // Could be based on total likes count from user profile if available.
        val maxPagesToFetch = 10 // Example: Fetch up to 1000 likes if limit is 100
        var totalLikesFetchedThisRun = 0

        try {
            pageFetchLoop@ while (pagesFetched < maxPagesToFetch) {
                if (isStopped) {
                    Log.i("FeedWorker", "pullLikes: Worker stopped, exiting.")
                    return@withContext allLikes
                }

                onProgress((pagesFetched * 100) / maxPagesToFetch.coerceAtLeast(1))

                val likesResponse = try {
                    api.getActorLikes(
                        GetActorLikesQueryParams(
                            actor = Did(session.did),
                            limit = API_REQUEST_LIMIT,
                            cursor = cursor
                        )
                    ).maybeResponse()
                } catch (e: Exception) {
                    Log.e("FeedWorker", "Error fetching likes page ${pagesFetched + 1}", e)
                    delay(THERMAL_COOLDOWN_MS) // Cooldown on error before potentially retrying or stopping
                    // Consider more robust retry logic here or failing if multiple attempts fail
                    break@pageFetchLoop // Stop fetching likes on error for now
                }

                pagesFetched++

                if (likesResponse == null || likesResponse.feed.isEmpty()) {
                    Log.d("FeedWorker", "No more likes to process (page $pagesFetched)")
                    break@pageFetchLoop
                }

                val newLikeViews = likesResponse.feed.filter { it.post.uri.atUri !in knownUris }
                if (newLikeViews.isEmpty() && likesResponse.cursor == null) {
                    Log.d("FeedWorker", "No new likes and no more pages.")
                    break@pageFetchLoop
                }

                val likesToEmbedAndStore = mutableListOf<LikeVector>()
                for (likeView in newLikeViews) {
                    if (isStopped) {
                        Log.i("FeedWorker", "pullLikes: Worker stopped during like processing.")
                        // Store what we have processed so far in this batch before exiting
                        if (likesToEmbedAndStore.isNotEmpty()) {
                            try {
                                box.put(likesToEmbedAndStore)
                                allLikes.addAll(likesToEmbedAndStore)
                                Log.d(
                                    "FeedWorker",
                                    "Stored partial batch of ${likesToEmbedAndStore.size} likes before stopping."
                                )
                            } catch (dbE: Exception) {
                                Log.e(
                                    "FeedWorker",
                                    "Error storing partial like batch before stopping",
                                    dbE
                                )
                            }
                        }
                        return@withContext allLikes
                    }
                    try {
                        val post = likeView.post.record.decodeAs<Post>()
                        if (post.text == null || post.text.isBlank()) {
                            // Log.w("FeedWorker", "Skipping like with empty text: ${likeView.post.uri}")
                            continue
                        }
                        val vector = postEmbedder.encode(post.text)
                        val newVector = LikeVector(
                            uri = likeView.post.uri.atUri,
                            cid = likeView.post.cid.cid,
                            createdAt = post.createdAt.epochSeconds,
                            vector = vector,
                        )
                        likesToEmbedAndStore.add(newVector)
                        knownUris.add(newVector.uri) // Add to known URIs immediately
                    } catch (e: Exception) {
                        Log.e(
                            "FeedWorker",
                            "Error processing like for embedding: ${likeView.post.uri}",
                            e
                        )
                    }
                }

                if (likesToEmbedAndStore.isNotEmpty()) {
                    try {
                        box.put(likesToEmbedAndStore)
                        allLikes.addAll(likesToEmbedAndStore)
                        totalLikesFetchedThisRun += likesToEmbedAndStore.size
                        Log.d(
                            "FeedWorker",
                            "Stored batch of ${likesToEmbedAndStore.size} new likes."
                        )
                    } catch (dbE: Exception) {
                        Log.e("FeedWorker", "Error storing batch of new likes", dbE)
                        // Decide on error handling: skip batch, retry, or fail
                    }
                }

                cursor = likesResponse.cursor ?: break@pageFetchLoop
                delay(THERMAL_COOLDOWN_MS) // Cooldown after processing a page
            }
        } catch (e: Exception) {
            Log.e("FeedWorker", "Outer error in pullLikes loop", e)
        }

        Log.i(
            "FeedWorker",
            "pullLikes finished. Fetched $totalLikesFetchedThisRun new likes this run. Total likes in DB: ${allLikes.size}"
        )
        onProgress(100)
        allLikes
    }

    suspend fun getFamiliarFeeds(
        api: AuthenticatedXrpcBlueskyApi,
        // minPostsPerFeed: Int = MAX_POSTS_PER_FEED, // This param might be re-evaluated for its purpose
        onTimelineProgress: suspend (fetchedCount: Int, targetCount: Int) -> Unit, // Callbacks for granular progress
        onDiscoveryProgress: suspend (fetchedCount: Int, targetCount: Int) -> Unit
    ): List<FeedViewPost> = withContext(Dispatchers.IO) {
        val MIN_DISCOVERY_POSTS_TARGET =
            MAX_POSTS_PER_FEED // Aim for MAX_POSTS_PER_FEED from discovery
        val MIN_TIMELINE_POSTS_TARGET =
            MAX_POSTS_PER_FEED  // Aim for MAX_POSTS_PER_FEED from timeline

        val allFollowingPosts = mutableListOf<FeedViewPost>()
        var followingCursor: String? = null
        var timelineFetchComplete = false
        var timelinePagesFetched = 0
        val maxTimelinePages = 5 // Limit pages for timeline (e.g., 5 * 100 = 500 posts max)

        Log.i("FeedWorker", "Fetching timeline feed...")
        try {
            timelineLoop@ while (allFollowingPosts.size < MIN_TIMELINE_POSTS_TARGET && !timelineFetchComplete && timelinePagesFetched < maxTimelinePages) {
                if (isStopped) {
                    Log.i("FeedWorker", "getFamiliarFeeds (Timeline): Worker stopped.")
                    break@timelineLoop
                }
                onTimelineProgress(allFollowingPosts.size, MIN_TIMELINE_POSTS_TARGET)

                val response = try {
                    api.getTimeline(
                        GetTimelineQueryParams(
                            algorithm = "reverse-chronological", // or other algorithm if preferred
                            limit = API_REQUEST_LIMIT,
                            cursor = followingCursor
                        )
                    ).maybeResponse()
                } catch (e: Exception) {
                    Log.e(
                        "FeedWorker",
                        "Error fetching timeline page ${timelinePagesFetched + 1}",
                        e
                    )
                    delay(THERMAL_COOLDOWN_MS)
                    // Consider retry logic or breaking
                    break@timelineLoop // Stop timeline fetch on error for now
                }

                timelinePagesFetched++

                if (response == null) {
                    Log.w("FeedWorker", "Timeline response null, page ${timelinePagesFetched}")
                    delay(THERMAL_COOLDOWN_MS) // Short delay before potentially retrying or stopping
                    break@timelineLoop // Stop if API behaves unexpectedly
                }

                if (response.feed.isEmpty()) {
                    Log.d("FeedWorker", "Timeline feed empty at page ${timelinePagesFetched}")
                    timelineFetchComplete = true
                } else {
                    allFollowingPosts.addAll(response.feed)
                    followingCursor = response.cursor
                    if (followingCursor == null) {
                        timelineFetchComplete = true
                    }
                    Log.d(
                        "FeedWorker",
                        "Fetched ${response.feed.size} timeline posts. Total: ${allFollowingPosts.size}"
                    )
                }
                delay(THERMAL_COOLDOWN_MS) // Cooldown after each page fetch
            }
        } catch (e: Exception) {
            Log.e("FeedWorker", "Error in timeline fetch loop", e)
        }
        onTimelineProgress(allFollowingPosts.size, MIN_TIMELINE_POSTS_TARGET)
        Log.i("FeedWorker", "Timeline fetch complete. Total posts: ${allFollowingPosts.size}")

        val cleanedFollowingFeed = try {
            FeedTuner.cleanReplies(allFollowingPosts) // Assuming FeedTuner.cleanReplies is efficient
        } catch (e: Exception) {
            Log.e("FeedWorker", "Error cleaning replies from timeline", e)
            allFollowingPosts // Fallback to uncleaned list
        }

        // Fetch Discover Feed
        val allDiscoveryPosts = mutableListOf<FeedViewPost>()
        var discoveryCursor: String? = null
        var discoveryFetchComplete = false
        var discoveryPagesFetched = 0
        val maxDiscoveryPages = 5 // Limit pages for discovery (e.g., 5 * 100 = 500 posts max)
        // A common "what's hot" or trending feed
        val discoverFeedUri =
            AtUri("at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot")

        Log.i("FeedWorker", "Fetching discovery feed...")
        try {
            discoveryLoop@ while (allDiscoveryPosts.size < MIN_DISCOVERY_POSTS_TARGET && !discoveryFetchComplete && discoveryPagesFetched < maxDiscoveryPages) {
                if (isStopped) {
                    Log.i("FeedWorker", "getFamiliarFeeds (Discovery): Worker stopped.")
                    break@discoveryLoop
                }
                onDiscoveryProgress(allDiscoveryPosts.size, MIN_DISCOVERY_POSTS_TARGET)
                
                val response = try {
                    api.getFeed(
                        GetFeedQueryParams(
                            feed = discoverFeedUri,
                            limit = API_REQUEST_LIMIT,
                            cursor = discoveryCursor
                        )
                    ).maybeResponse()
                } catch (e: Exception) {
                    Log.e(
                        "FeedWorker",
                        "Error fetching discovery feed page ${discoveryPagesFetched + 1}",
                        e
                    )
                    delay(THERMAL_COOLDOWN_MS)
                    break@discoveryLoop // Stop discovery fetch on error for now
                }
                discoveryPagesFetched++

                if (response == null) {
                    Log.w("FeedWorker", "Discovery response null, page ${discoveryPagesFetched}")
                    delay(THERMAL_COOLDOWN_MS)
                    break@discoveryLoop
                }

                if (response.feed.isEmpty()) {
                    Log.d("FeedWorker", "Discovery feed empty at page ${discoveryPagesFetched}")
                    discoveryFetchComplete = true
                } else {
                    allDiscoveryPosts.addAll(response.feed)
                    discoveryCursor = response.cursor
                    if (discoveryCursor == null) {
                        discoveryFetchComplete = true
                    }
                    Log.d(
                        "FeedWorker",
                        "Fetched ${response.feed.size} discovery posts. Total: ${allDiscoveryPosts.size}"
                    )
                }
                delay(THERMAL_COOLDOWN_MS) // Cooldown after each page fetch
            }
        } catch (e: Exception) {
            Log.e("FeedWorker", "Error in discovery fetch loop", e)
        }
        onDiscoveryProgress(allDiscoveryPosts.size, MIN_DISCOVERY_POSTS_TARGET)
        Log.i("FeedWorker", "Discovery fetch complete. Total posts: ${allDiscoveryPosts.size}")

        // Combine feeds, potentially with de-duplication if posts can appear in both
        val combinedFeed = (cleanedFollowingFeed + allDiscoveryPosts).distinctBy { it.post.uri }
        Log.i("FeedWorker", "Combined feed size after de-duplication: ${combinedFeed.size}")
        return@withContext combinedFeed
    }

    override suspend fun doWork(): Result =
        coroutineScope { // Use coroutineScope for top-level structure
        try {
            Log.i("FeedWorker", "doWork: Starting FeedWorker execution.")
            updateProgressNotification(WorkStage.STARTING, 0, indeterminate = true)

            if (isStopped) {
                Log.i(
                    "FeedWorker",
                    "doWork: Worker stopped at start."
                ); return@coroutineScope Result.failure()
            }

            updateProgressNotification(WorkStage.CONNECTING_API, 0, indeterminate = true)
            val api = getBlueskyApi()
            if (api == null) {
                Log.e("FeedWorker", "Bluesky API not initialized")
                return@coroutineScope Result.failure()
            }
            updateProgressNotification(WorkStage.CONNECTING_API, 100, indeterminate = false)

            if (isStopped) {
                Log.i(
                    "FeedWorker",
                    "doWork: Worker stopped after API init."
                ); return@coroutineScope Result.failure()
            }

            val postEmbedder = try {
                PostEmbedder(appContext) // Assuming PostEmbedder is lightweight to initialize
            } catch (e: Exception) {
                Log.e("FeedWorker", "Failed to initialize PostEmbedder", e)
                return@coroutineScope Result.failure()
            }

            if (isStopped) {
                Log.i(
                    "FeedWorker",
                    "doWork: Worker stopped after PostEmbedder init."
                ); return@coroutineScope Result.failure()
            }
            
            val objectBox = if (ObjectBox.store == null) {
                ObjectBox.init(appContext)
            } else {
                ObjectBox.store
            }

            val currentSession = SessionManager(appContext).getSession()
            if (currentSession == null) {
                Log.e("FeedWorker", "User session not found.")
                return@coroutineScope Result.failure()
            }

            // Launch fetching operations concurrently
            updateProgressNotification(
                WorkStage.FETCHING_TIMELINE,
                0,
                indeterminate = true
            ) // General fetching stage
            val familiarFeedDeferred =
                async(Dispatchers.IO) { // Explicitly use Dispatchers.IO for network/db
                    Log.i("FeedWorker", "Starting getFamiliarFeeds async block...")
                    getFamiliarFeeds(
                        api = api,
                        onTimelineProgress = { fetchedCount, targetCount ->
                            val progress =
                                if (targetCount > 0) (fetchedCount * 100 / targetCount).coerceAtMost(
                                    100
                                ) else 0
                            updateProgressNotification(
                                WorkStage.FETCHING_TIMELINE,
                                progress,
                                indeterminate = false
                            )
                        },
                        onDiscoveryProgress = { fetchedCount, targetCount ->
                            val progress =
                                if (targetCount > 0) (fetchedCount * 100 / targetCount).coerceAtMost(
                                    100
                                ) else 0
                            updateProgressNotification(
                                WorkStage.FETCHING_DISCOVERY,
                                progress,
                                indeterminate = false
                            )
                        }
                    ).also {
                        Log.i(
                            "FeedWorker",
                            "getFamiliarFeeds completed. Fetched ${it.size} posts."
                        )
                    }
                }

            updateProgressNotification(
                WorkStage.PULLING_LIKES,
                0,
                indeterminate = true
            ) // Switch to likes fetching stage
            val likesDeferred = async(Dispatchers.IO) { // Explicitly use Dispatchers.IO
                Log.i("FeedWorker", "Starting pullLikes async block...")
                pullLikes(objectBox, api, currentSession, postEmbedder) { likeProgress ->
                    updateProgressNotification(
                        WorkStage.PULLING_LIKES,
                        likeProgress,
                        indeterminate = false
                    )
                }.also { Log.i("FeedWorker", "pullLikes completed. Found ${it.size} likes.") }
            }

            // Await results
            val familiarFeed = familiarFeedDeferred.await()
            if (isStopped) {
                Log.i(
                    "FeedWorker",
                    "doWork: Worker stopped after familiarFeed.await()."
                ); return@coroutineScope Result.failure()
            }
            updateProgressNotification(
                WorkStage.FETCHING_DISCOVERY,
                100,
                indeterminate = false
            ) // Mark discovery/timeline fetch part as complete

            val likes = likesDeferred.await()
            if (isStopped) {
                Log.i(
                    "FeedWorker",
                    "doWork: Worker stopped after likes.await()."
                ); return@coroutineScope Result.failure()
            }
            updateProgressNotification(WorkStage.PULLING_LIKES, 100, indeterminate = false)


            val totalPostsToProcess = familiarFeed.size
            Log.i("FeedWorker", "Total posts to process: $totalPostsToProcess")
            updateProgressNotification(
                WorkStage.PROCESSING_POSTS,
                0,
                indeterminate = totalPostsToProcess == 0
            )

            val postEmbedderBox = objectBox.boxFor(EmbeddedPost::class.java)
            postEmbedderBox.removeAll() // Clear existing posts before adding new ones
            Log.i("FeedWorker", "Cleared existing EmbeddedPost data.")

            val batchToStore = mutableListOf<EmbeddedPost>()

            familiarFeed.forEachIndexed { globalIndex, feedViewPost ->
                if (isStopped) {
                    Log.i("FeedWorker", "doWork: Worker stopped during post processing loop.");
                    // Store any partially filled batch before exiting
                    if (batchToStore.isNotEmpty()) {
                        try {
                            postEmbedderBox.put(batchToStore.sortedByDescending { it.score!! + (Random.nextFloat() * 0.1f) })
                        } catch (e: Exception) {
                            Log.e("FeedWorker", "Error storing final partial batch", e)
                        }
                    }
                    return@coroutineScope Result.failure()
                }

                val progress =
                    if (totalPostsToProcess > 0) ((globalIndex + 1) * 100 / totalPostsToProcess) else 0
                updateProgressNotification(WorkStage.PROCESSING_POSTS, progress)

                try {
                    val postView = feedViewPost.post
                    val post = postView.record.decodeAs<Post>()

                    if (post.text == null || post.text.isBlank()) {
                        // Log.w("FeedWorker", "Skipping post with empty text: ${postView.uri}")
                        return@forEachIndexed // continue to next item in forEachIndexed
                    }

                    val embedding = postEmbedder.encode(post.text)
                    val embeddedPost = EmbeddedPost(
                        uri = postView.uri.atUri,
                        text = post.text,
                        embedding = embedding,
                        authorDid = postView.author.did.did,
                        timestamp = post.createdAt.epochSeconds,
                        score = 0f // Score will be calculated next
                    )

                    val score = FeedRanker.calculatePostScore(
                        embeddedPost,
                        likes
                    ) // Assuming FeedRanker is available
                    batchToStore.add(embeddedPost.copy(score = score))

                } catch (e: Exception) {
                    Log.e("FeedWorker", "Error processing post ${feedViewPost.post.uri}", e)
                }

                if (batchToStore.size >= PROCESSING_BATCH_SIZE || globalIndex == familiarFeed.size - 1) {
                    if (batchToStore.isNotEmpty()) {
                        try {
                            // Sort the batch by score + jitter before storing
                            val sortedBatch =
                                batchToStore.sortedByDescending { it.score!! + (Random.nextFloat() * 0.1f) }
                            postEmbedderBox.put(sortedBatch)
                            Log.d(
                                "FeedWorker",
                                "Stored batch of ${batchToStore.size} posts to ObjectBox."
                            )
                            batchToStore.clear()

                            val dbProgress =
                                if (totalPostsToProcess > 0) ((globalIndex + 1) * 100 / totalPostsToProcess.coerceAtLeast(
                                    1
                                )) else 100
                            updateProgressNotification(
                                WorkStage.UPDATING_DATABASE,
                                dbProgress,
                                indeterminate = false
                            )

                        } catch (e: Exception) {
                            Log.e("FeedWorker", "Error storing batch of posts to ObjectBox", e)
                            // Consider how to handle batch store failure
                        }
                        delay(THERMAL_COOLDOWN_MS) // Cooldown after DB batch write
                    }
                }
            }

            updateProgressNotification(WorkStage.UPDATING_DATABASE, 100, indeterminate = false)
            updateProgressNotification(WorkStage.COMPLETE, 100)

            // Optional: A single GC at the very end if you suspect large transient objects were created.
            // forceGarbageCollection()

            Log.i("FeedWorker", "doWork: Sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e("FeedWorker", "Fatal error in doWork", e)
            if (isStopped) {
                Log.i("FeedWorker", "doWork: Worker stopped during fatal error handling.")
                return@coroutineScope Result.failure()
            }
            // For many errors, retry might be appropriate. For others (like bad data), failure.
            Result.retry() // Or Result.failure()
        }
            // Removed finally block as PostEmbedder does not have a close() method
    }
}
