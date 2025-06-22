package io.github.turtlepaw.mindsky.workers

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetFeedQueryParams
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.Post
import io.github.turtlepaw.mindsky.db.EmbeddedPost
import io.github.turtlepaw.mindsky.db.Engagement
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.logic.FeedTuner
import io.github.turtlepaw.mindsky.logic.PostEmbedder
import io.github.turtlepaw.mindsky.logic.ranking.InterestCluster
import io.github.turtlepaw.mindsky.logic.ranking.MultiInterestUserProfile
import io.github.turtlepaw.mindsky.logic.ranking.PostRanker
import io.github.turtlepaw.mindsky.logic.ranking.PostScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
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
        UPDATING_DATABASE("Finalizing and updating database..."),

        // PAUSED_FOR_MEMORY removed as the system is changed
        COMPLETE("Sync complete.")
    }

    companion object {
        const val CHANNEL_ID = "feed_worker_channel"
        const val NOTIFICATION_ID = 1




        // Optimized constants
        private const val MAX_POSTS_PER_FEED = 250 // Increased for more comprehensive fetching
        private const val PROCESSING_BATCH_SIZE = 50 // Increased for DB operations


        // MAX_MEMORY_USAGE_MB can be kept as a general guideline if needed elsewhere, but not for pausing.
        // private const val MAX_MEMORY_USAGE_MB = 150

        // Enqueueing logic with better constraints

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
                            limit = WorkerCommon.API_REQUEST_LIMIT,
                            cursor = followingCursor
                        )
                    ).maybeResponse()
                } catch (e: Exception) {
                    Log.e(
                        "FeedWorker",
                        "Error fetching timeline page ${timelinePagesFetched + 1}",
                        e
                    )
                    delay(WorkerCommon.THERMAL_COOLDOWN_MS)
                    // Consider retry logic or breaking
                    break@timelineLoop // Stop timeline fetch on error for now
                }

                timelinePagesFetched++

                if (response == null) {
                    Log.w("FeedWorker", "Timeline response null, page ${timelinePagesFetched}")
                    delay(WorkerCommon.THERMAL_COOLDOWN_MS) // Short delay before potentially retrying or stopping
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
                delay(WorkerCommon.THERMAL_COOLDOWN_MS) // Cooldown after each page fetch
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
                            limit = WorkerCommon.API_REQUEST_LIMIT,
                            cursor = discoveryCursor
                        )
                    ).maybeResponse()
                } catch (e: Exception) {
                    Log.e(
                        "FeedWorker",
                        "Error fetching discovery feed page ${discoveryPagesFetched + 1}",
                        e
                    )
                    delay(WorkerCommon.THERMAL_COOLDOWN_MS)
                    break@discoveryLoop // Stop discovery fetch on error for now
                }
                discoveryPagesFetched++

                if (response == null) {
                    Log.w("FeedWorker", "Discovery response null, page ${discoveryPagesFetched}")
                    delay(WorkerCommon.THERMAL_COOLDOWN_MS)
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
                delay(WorkerCommon.THERMAL_COOLDOWN_MS) // Cooldown after each page fetch
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
                val session = WorkerCommon.getSession(appContext)
                if (session == null) {
                    Log.e("FeedWorker", "User session not found.")
                    return@coroutineScope Result.failure()
                }
                val api = WorkerCommon.getBlueskyApi(session)
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


                val totalPostsToProcess = familiarFeed.size
                Log.i("FeedWorker", "Total posts to process: $totalPostsToProcess")
                updateProgressNotification(
                    WorkStage.PROCESSING_POSTS,
                    0,
                    indeterminate = totalPostsToProcess == 0
                )

                val postScoreBox = objectBox.boxFor(PostScore::class.java)
                val engagementBox = objectBox.boxFor(Engagement::class.java)
                val interestCluster = objectBox.boxFor(InterestCluster::class.java)
                val allEmbeddings = interestCluster.all
                val allEngagements = engagementBox.all

                postScoreBox.removeAll() // Clear existing posts before adding new ones
                Log.i("FeedWorker", "Cleared existing EmbeddedPost data.")

                val batchToStore = mutableListOf<PostScore>()

                familiarFeed.forEachIndexed { globalIndex, feedViewPost ->
                    if (isStopped) {
                        Log.i("FeedWorker", "doWork: Worker stopped during post processing loop.");
                        // Store any partially filled batch before exiting
                        if (batchToStore.isNotEmpty()) {
                            try {
                                postScoreBox.put(batchToStore.sortedByDescending { it.finalScore!! + (Random.Default.nextFloat() * 0.1f) })
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
                            createdAt = post.createdAt.epochSeconds,
                            score = 0f // Score will be calculated next
                        )

                        val score = PostRanker.scorePost(
                            embeddedPost,
                            MultiInterestUserProfile.fromInterestClusters(allEmbeddings),
                            allEngagements
                        ) // Assuming FeedRanker is available
                        batchToStore.add(score)

                    } catch (e: Exception) {
                        Log.e("FeedWorker", "Error processing post ${feedViewPost.post.uri}", e)
                    }

                    if (batchToStore.size >= PROCESSING_BATCH_SIZE || globalIndex == familiarFeed.size - 1) {
                        if (batchToStore.isNotEmpty()) {
                            try {
                                // Sort the batch by score + jitter before storing
                                val sortedBatch =
                                    batchToStore.sortedByDescending { it.finalScore + (Random.Default.nextFloat() * 0.1f) }
                                postScoreBox.put(sortedBatch)
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
                            delay(WorkerCommon.THERMAL_COOLDOWN_MS) // Cooldown after DB batch write
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