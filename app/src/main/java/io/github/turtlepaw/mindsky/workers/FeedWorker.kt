package io.github.turtlepaw.mindsky.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetFeedQueryParams
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.Post
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.db.EmbeddedPost
import io.github.turtlepaw.mindsky.db.Engagement
import io.github.turtlepaw.mindsky.db.ObjectBox
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
        COMPLETE("Sync complete.")
    }

    companion object {
        const val CHANNEL_ID = "feed_worker_channel"
        const val NOTIFICATION_ID = 1

        private const val MAX_POSTS_PER_FEED = 250
        private const val PROCESSING_BATCH_SIZE = 50

        private const val TEN_MINUTES_MS = 10 * 60 * 1000L // 10 minutes in milliseconds
        private const val MIN_INTER_EMBEDDING_DELAY_MS = 3000L // Just over 2 seconds

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
        val ongoing = stage != WorkStage.COMPLETE
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
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
        onTimelineProgress: suspend (fetchedCount: Int, targetCount: Int) -> Unit,
        onDiscoveryProgress: suspend (fetchedCount: Int, targetCount: Int) -> Unit
    ): List<FeedViewPost> = withContext(Dispatchers.IO) {
        val MIN_DISCOVERY_POSTS_TARGET = MAX_POSTS_PER_FEED
        val MIN_TIMELINE_POSTS_TARGET = MAX_POSTS_PER_FEED

        val allFollowingPosts = mutableListOf<FeedViewPost>()
        var followingCursor: String? = null
        var timelineFetchComplete = false
        var timelinePagesFetched = 0
        val maxTimelinePages = 5

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
                    break@timelineLoop
                }
                timelinePagesFetched++
                if (response == null) {
                    Log.w("FeedWorker", "Timeline response null, page ${timelinePagesFetched}")
                    delay(WorkerCommon.THERMAL_COOLDOWN_MS)
                    break@timelineLoop
                }
                if (response.feed.isEmpty()) {
                    Log.d("FeedWorker", "Timeline feed empty at page ${timelinePagesFetched}")
                    timelineFetchComplete = true
                } else {
                    allFollowingPosts.addAll(response.feed)
                    followingCursor = response.cursor
                    if (followingCursor == null) timelineFetchComplete = true
                    Log.d(
                        "FeedWorker",
                        "Fetched ${response.feed.size} timeline posts. Total: ${allFollowingPosts.size}"
                    )
                }
                delay(WorkerCommon.THERMAL_COOLDOWN_MS)
            }
        } catch (e: Exception) {
            Log.e("FeedWorker", "Error in timeline fetch loop", e)
        }
        onTimelineProgress(allFollowingPosts.size, MIN_TIMELINE_POSTS_TARGET)
        Log.i("FeedWorker", "Timeline fetch complete. Total posts: ${allFollowingPosts.size}")

        val cleanedFollowingFeed = try {
            FeedTuner.cleanReplies(allFollowingPosts)
        } catch (e: Exception) {
            Log.e("FeedWorker", "Error cleaning replies from timeline", e)
            allFollowingPosts
        }

        val allDiscoveryPosts = mutableListOf<FeedViewPost>()
        var discoveryCursor: String? = null
        var discoveryFetchComplete = false
        var discoveryPagesFetched = 0
        val maxDiscoveryPages = 5
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
                    break@discoveryLoop
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
                    if (discoveryCursor == null) discoveryFetchComplete = true
                    Log.d(
                        "FeedWorker",
                        "Fetched ${response.feed.size} discovery posts. Total: ${allDiscoveryPosts.size}"
                    )
                }
                delay(WorkerCommon.THERMAL_COOLDOWN_MS)
            }
        } catch (e: Exception) {
            Log.e("FeedWorker", "Error in discovery fetch loop", e)
        }
        onDiscoveryProgress(allDiscoveryPosts.size, MIN_DISCOVERY_POSTS_TARGET)
        Log.i("FeedWorker", "Discovery fetch complete. Total posts: ${allDiscoveryPosts.size}")

        val combinedFeed = (cleanedFollowingFeed + allDiscoveryPosts).distinctBy { it.post.uri }
        Log.i("FeedWorker", "Combined feed size after de-duplication: ${combinedFeed.size}")
        return@withContext combinedFeed
    }

    override suspend fun doWork(): Result = coroutineScope {
        val startTimeMillis = System.currentTimeMillis()

        val wakeLock: PowerManager.WakeLock =
            (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FeedWorker::MainWakeLock")
            }

        wakeLock.run {
            try {
                acquire(10 * 60 * 1000L /*10 minutes*/)

                Log.i(
                    "FeedWorker",
                    "doWork: Starting FeedWorker execution. Max duration: ${TEN_MINUTES_MS / 60000} min."
                )
                updateProgressNotification(WorkStage.STARTING, 0, indeterminate = true)

                if (isStopped) {
                    Log.i("FeedWorker", "doWork: Worker stopped at start.")
                    return@coroutineScope Result.failure()
                }
                if (System.currentTimeMillis() - startTimeMillis >= TEN_MINUTES_MS) {
                    Log.i("FeedWorker", "doWork: Worker timed out (10 min) at start.")
                    updateProgressNotification(WorkStage.COMPLETE, 100)
                    return@coroutineScope Result.success()
                }

                updateProgressNotification(WorkStage.CONNECTING_API, 0, indeterminate = true)
                val sessionManager = SessionManager(appContext)
                if (sessionManager.getSession() == null) {
                    Log.e("FeedWorker", "No active session")
                    return@coroutineScope Result.failure()
                }
                val api = WorkerCommon.getBlueskyApi(appContext)
                updateProgressNotification(WorkStage.CONNECTING_API, 100, indeterminate = false)

                if (isStopped) {
                    Log.i("FeedWorker", "doWork: Worker stopped after API init.")
                    return@coroutineScope Result.failure()
                }
                if (System.currentTimeMillis() - startTimeMillis >= TEN_MINUTES_MS) {
                    Log.i("FeedWorker", "doWork: Worker timed out (10 min) after API init.")
                    updateProgressNotification(WorkStage.COMPLETE, 100)
                    return@coroutineScope Result.success()
                }


                val postEmbedder = try {
                    PostEmbedder(appContext)
                } catch (e: Exception) {
                    Log.e("FeedWorker", "Failed to initialize PostEmbedder", e)
                    return@coroutineScope Result.failure()
                }

                if (isStopped) {
                    Log.i("FeedWorker", "doWork: Worker stopped after PostEmbedder init.")
                    return@coroutineScope Result.failure()
                }
                if (System.currentTimeMillis() - startTimeMillis >= TEN_MINUTES_MS) {
                    Log.i(
                        "FeedWorker",
                        "doWork: Worker timed out (10 min) after PostEmbedder init."
                    )
                    updateProgressNotification(WorkStage.COMPLETE, 100)
                    return@coroutineScope Result.success()
                }

                val objectBox =
                    if (ObjectBox.store == null) ObjectBox.init(appContext) else ObjectBox.store
                val currentSession = SessionManager(appContext).getSession()
                if (currentSession == null) {
                    Log.e("FeedWorker", "User session not found.")
                    return@coroutineScope Result.failure()
                }

                updateProgressNotification(WorkStage.FETCHING_TIMELINE, 0, indeterminate = true)
                val familiarFeedDeferred = async(Dispatchers.IO) {
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

                val familiarFeed = familiarFeedDeferred.await()
                if (isStopped) {
                    Log.i("FeedWorker", "doWork: Worker stopped after familiarFeed.await().")
                    return@coroutineScope Result.failure()
                }
                if (System.currentTimeMillis() - startTimeMillis >= TEN_MINUTES_MS) {
                    Log.i(
                        "FeedWorker",
                        "doWork: Worker timed out (10 min) after familiarFeed.await()."
                    )
                    updateProgressNotification(WorkStage.FETCHING_DISCOVERY, 100, false)
                    updateProgressNotification(WorkStage.COMPLETE, 100)
                    return@coroutineScope Result.success()
                }
                updateProgressNotification(WorkStage.FETCHING_DISCOVERY, 100, indeterminate = false)

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
                val allInterestClusters = interestCluster.all
                val allEngagements = engagementBox.all

                postScoreBox.removeAll()
                Log.i("FeedWorker", "Cleared existing PostScore data.")

                val batchToStore = mutableListOf<PostScore>()

                for ((globalIndex, feedViewPost) in familiarFeed.withIndex()) {
                    if (isStopped) {
                        Log.i("FeedWorker", "doWork: Worker stopped during post processing loop.")
                        if (batchToStore.isNotEmpty()) {
                            try {
                                postScoreBox.put(batchToStore.sortedByDescending { it.finalScore + (Random.Default.nextFloat() * 0.1f) })
                            } catch (e: Exception) {
                                Log.e("FeedWorker", "Error storing final partial batch on stop", e)
                            }
                        }
                        return@coroutineScope Result.failure()
                    }

                    if (System.currentTimeMillis() - startTimeMillis >= TEN_MINUTES_MS) {
                        Log.i(
                            "FeedWorker",
                            "doWork: Worker timed out (10 min) during post processing."
                        )
                        if (batchToStore.isNotEmpty()) {
                            try {
                                postScoreBox.put(batchToStore.sortedByDescending { it.finalScore + (Random.Default.nextFloat() * 0.1f) })
                            } catch (e: Exception) {
                                Log.e(
                                    "FeedWorker",
                                    "Error storing final partial batch on timeout",
                                    e
                                )
                            }
                        }
                        updateProgressNotification(WorkStage.UPDATING_DATABASE, 100, false)
                        updateProgressNotification(WorkStage.COMPLETE, 100)
                        Log.i("FeedWorker", "doWork: Sync ended due to timeout.")
                        return@coroutineScope Result.success()
                    }

                    val currentPostProgress =
                        if (totalPostsToProcess > 0) ((globalIndex + 1) * 100 / totalPostsToProcess) else 0
                    updateProgressNotification(WorkStage.PROCESSING_POSTS, currentPostProgress)

                    var performedEmbeddingCycle = false
                    try {
                        val postView = feedViewPost.post
                        val post = postView.record.decodeAs<Post>()
                        if (post.text.isNotBlank() && post.text.isNotBlank()) {
                            performedEmbeddingCycle = true
                            val embedding = postEmbedder.encode(post.text)
                            val embeddedPost = EmbeddedPost(
                                uri = postView.uri.atUri,
                                text = post.text,
                                embedding = embedding,
                                authorDid = postView.author.did.did,
                                createdAt = post.createdAt.epochSeconds,
                                score = 0f
                            )
                            val score = PostRanker.scorePost(
                                embeddedPost,
                                MultiInterestUserProfile.fromInterestClusters(allInterestClusters),
                                allEngagements
                            )
                            batchToStore.add(score)
                        }
                    } catch (e: Exception) {
                        Log.e("FeedWorker", "Error processing post ${feedViewPost.post.uri}", e)
                    }

                    if (batchToStore.size >= PROCESSING_BATCH_SIZE || globalIndex == familiarFeed.size - 1) {
                        if (batchToStore.isNotEmpty()) {
                            try {
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
                            }
                            if (!isStopped && (System.currentTimeMillis() - startTimeMillis < TEN_MINUTES_MS)) {
                                delay(WorkerCommon.THERMAL_COOLDOWN_MS)
                            }
                        }
                    }

                    if (performedEmbeddingCycle && globalIndex < familiarFeed.size - 1) {
                        if (!isStopped && (System.currentTimeMillis() - startTimeMillis < TEN_MINUTES_MS)) {
                            delay(MIN_INTER_EMBEDDING_DELAY_MS)
                        }
                    }
                }

                updateProgressNotification(WorkStage.UPDATING_DATABASE, 100, indeterminate = false)
                updateProgressNotification(WorkStage.COMPLETE, 100)
                Log.i("FeedWorker", "doWork: Sync completed successfully.")
                Result.success()
            } catch (e: Exception) {
                Log.e("FeedWorker", "Fatal error in doWork", e)
                if (isStopped) {
                    Log.i("FeedWorker", "doWork: Worker stopped during fatal error handling.")
                    return@coroutineScope Result.failure()
                }
                if (System.currentTimeMillis() - startTimeMillis >= TEN_MINUTES_MS && !isStopped) { // Check timeout only if not already stopped
                    Log.i(
                        "FeedWorker",
                        "doWork: Worker timed out (10 min) during fatal error handling."
                    )
                    updateProgressNotification(
                        WorkStage.COMPLETE,
                        100
                    ) // Mark complete on timeout during error
                    return@coroutineScope Result.success() // Or failure, depends on policy
                }
                Result.retry()
            } finally {
                if (wakeLock.isHeld) {
                    release()
                    Log.i("FeedWorker", "doWork: Wake lock released.")
                }
            }
        }
    }
}
