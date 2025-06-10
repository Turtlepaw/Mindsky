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
import kotlinx.coroutines.flow.MutableStateFlow
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
        COMPLETE("Sync complete.")
    }

    companion object {
        const val CHANNEL_ID = "feed_worker_channel"
        const val NOTIFICATION_ID = 1
        val WORK_NAME = "FeedWorker"
        val IMMEDIATE_WORK_NAME = "FeedWorkerImmediate"

        val TIME_EVENING = LocalTime.of(16, 0)
        val TIME_MORNING = LocalTime.of(4, 0)

        // Enqueueing logic remains the same
        fun WorkManager.enqueueFeedWorkers(
            existingWorkPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
        ) {
            val eveningWorkRequest = buildWorkRequest(TIME_EVENING)
            val morningWorkRequest = buildWorkRequest(TIME_MORNING)

            enqueueUniquePeriodicWork(WORK_NAME, existingWorkPolicy, eveningWorkRequest)
            enqueueUniquePeriodicWork(WORK_NAME, existingWorkPolicy, morningWorkRequest)
        }

        fun WorkManager.enqueueImmediateFeedWorker(
            existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP
        ) {
            enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                existingWorkPolicy,
                buildImmediateWorkRequest()
            )
        }

        fun buildWorkRequest(time: LocalTime, requireIdle: Boolean = true): PeriodicWorkRequest {
            val delay = if (time.isAfter(LocalTime.now())) {
                Duration.between(LocalTime.now(), time).toMillis()
            } else {
                Duration.between(LocalTime.now(), time.plusHours(24)).toMillis()
            }
            return PeriodicWorkRequestBuilder<FeedWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresDeviceIdle(requireIdle)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
        }

        fun buildImmediateWorkRequest(): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<FeedWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
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
        if (now - lastProgressUpdate > 500) { // Update at most every 500ms
            val notification = createNotification(stage, progress, indeterminate)
            setForeground(getForegroundInfo(stage, progress, indeterminate))
            setProgress(workDataOf("stage" to stage.name, "progress" to progress))
            lastProgressUpdate = now
        }

//        // Also update the notification manager directly for immediate feedback
//        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        notificationManager.notify(NOTIFICATION_ID, notification)
    }


    private fun getForegroundInfo(
        stage: WorkStage,
        progress: Int,
        indeterminate: Boolean = false
    ): ForegroundInfo {
        val notification = createNotification(stage, progress, indeterminate)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Q for foregroundServiceType
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    // Keep this for the initial foreground info call from doWork()
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return getForegroundInfo(WorkStage.STARTING, 0)
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

        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate && stage != WorkStage.COMPLETE)
            .build()
    }

    fun getBlueskyApi(): AuthenticatedXrpcBlueskyApi? {
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
                url.takeFrom("https://bsky.social")
            }
            expectSuccess = true
        }

        return AuthenticatedXrpcBlueskyApi(
            initialTokens = authTokensFlow.value,
            httpClient = httpClient,
        )
    }

    suspend fun pullLikes(
        objectBox: BoxStore,
        api: AuthenticatedXrpcBlueskyApi,
        session: UserSession,
        postEmbedder: PostEmbedder,
        onProgress: suspend (Int) -> Unit // Progress 0-100 (approximate)
    ): List<LikeVector> {
        val box = objectBox.boxFor(LikeVector::class.java)
        val allLikes = mutableListOf<LikeVector>().apply { addAll(box.all) }
        val knownUris = mutableSetOf<String>().apply {
            addAll(allLikes.map { it.uri })
        }
        var cursor: String? = null
        var pagesFetched = 0
        val estimatedTotalPages = 5 // A rough estimate, can be adjusted or made smarter

        while (true) {
            onProgress((pagesFetched * 100) / estimatedTotalPages) // Report progress

            val response = api.getActorLikes(
                GetActorLikesQueryParams(
                    actor = Did(session.did),
                    limit = 100, // Max limit
                    cursor = cursor
                )
            ).maybeResponse()

            pagesFetched++

            if (response == null || response.feed.isEmpty()) {
                Log.d("FeedWorker", "No more likes to process or API error (page $pagesFetched)")
                break
            }

            val newLikes = response.feed.filter { it.post.uri.atUri !in knownUris }
            // If newLikes is empty, it means we've caught up, but we might have more pages if response.cursor is not null
            if (newLikes.isEmpty() && response.cursor == null) { // More specific condition to break early
                Log.d("FeedWorker", "No new likes and no more pages.")
                break
            }

            for (like in newLikes) {
                val post = like.post.record.decodeAs<Post>()
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
            }

            cursor = response.cursor ?: break // Break if no cursor
        }
        onProgress(100) // Ensure 100% at the end
        return allLikes
    }

    suspend fun getFamiliarFeeds(
        api: AuthenticatedXrpcBlueskyApi,
        limitPerFeed: Long = 100,
        onTimelineFetched: suspend () -> Unit,
        onDiscoveryFetched: suspend () -> Unit
    ): List<FeedViewPost> {
        val followingFeed = api.getTimeline(
            GetTimelineQueryParams(
                algorithm = "reverse-chronological",
                limit = limitPerFeed
            )
        ).maybeResponse()?.feed ?: emptyList()
        onTimelineFetched()

        val discoverFeed = api.getFeed(
            GetFeedQueryParams(
                feed = AtUri("at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot"),
                limit = limitPerFeed
            )
        ).maybeResponse()?.feed ?: emptyList()
        onDiscoveryFetched()

        return followingFeed + discoverFeed
    }

    override suspend fun doWork(): Result {
        // Initial foreground notification
        setForeground(getForegroundInfo(WorkStage.STARTING, 0))
        updateForegroundNotification(WorkStage.STARTING, 0)

        return try {
            updateForegroundNotification(WorkStage.CONNECTING_API, 0, indeterminate = true)
            val api = getBlueskyApi()
            if (api == null) {
                Log.e("FeedWorker", "Bluesky API not initialized")
                return Result.failure() // Or Result.retry()
            }
            // Assume API connection is quick, move to next stage

            val postEmbedder = PostEmbedder(appContext)

            val familiarFeed = getFamiliarFeeds(
                api,
                onTimelineFetched = {
                    updateForegroundNotification(
                        WorkStage.FETCHING_TIMELINE,
                        100,
                        indeterminate = false
                    )
                },
                onDiscoveryFetched = {
                    updateForegroundNotification(
                        WorkStage.FETCHING_DISCOVERY,
                        100,
                        indeterminate = false
                    )
                }
            )

            val totalPostsToProcess = familiarFeed.size

            if (totalPostsToProcess > 0) {
                updateForegroundNotification(WorkStage.PROCESSING_POSTS, 0)
            }

            val objectBox = if (ObjectBox.store == null) {
                ObjectBox.init(appContext)
            } else {
                ObjectBox.store
            }

            val currentSession = SessionManager(appContext).getSession()
            if (currentSession == null) {
                Log.e("FeedWorker", "User session not found.")
                return Result.failure()
            }

            updateForegroundNotification(
                WorkStage.PULLING_LIKES,
                0,
                indeterminate = true
            ) // Start as indeterminate
            val likes = pullLikes(objectBox, api, currentSession, postEmbedder) { likeProgress ->
                // As pullLikes progresses, this lambda is called.
                // We keep it indeterminate for now unless we can get a total count of likes.
                updateForegroundNotification(
                    WorkStage.PULLING_LIKES,
                    likeProgress,
                    indeterminate = false
                )
            }
            updateForegroundNotification(WorkStage.PULLING_LIKES, 100, indeterminate = false)

            val postEmbedderBox = objectBox.boxFor(EmbeddedPost::class.java)
            postEmbedderBox.removeAll() // Clear existing data

            val batchSize = 10 // Process posts in small batches

            familiarFeed.chunked(batchSize).forEachIndexed { batchIndex, batch ->
                val batchEmbeddings = mutableListOf<EmbeddedPost>()

                batch.forEachIndexed { indexInBatch, feedViewPost ->
                    val globalIndex = (batchIndex * batchSize) + indexInBatch
                    val progress = if (totalPostsToProcess > 0) (globalIndex + 1) * 100 / totalPostsToProcess else 0
                    updateForegroundNotification(WorkStage.PROCESSING_POSTS, progress)

                    val postView = feedViewPost.post
                    val post = postView.record.decodeAs<Post>()
                    val embedding = postEmbedder.encode(post.text)

                    Log.d("FeedWorker", "${post.text} = ${embedding.joinToString(prefix = "[", postfix = "]")}")

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
                }

                // Calculate scores and save this batch immediately
                updateForegroundNotification(WorkStage.UPDATING_DATABASE,
                    (batchIndex * 100) / ((totalPostsToProcess + batchSize - 1) / batchSize),
                    indeterminate = false)

                val scoredBatch = batchEmbeddings.map { post ->
                    val score = FeedRanker.calculatePostScore(post, likes)
                    post.copy(score = score)
                }

                // Save batch to database and clear from memory
                scoredBatch.filter { it.score != null }
                    .sortedByDescending { it.score!! + (Random.nextFloat() * 0.1f) }
                    .forEach { postEmbedderBox.put(it) }

                // Clear batch from memory
                batchEmbeddings.clear()

                // Force garbage collection hint (optional)
                System.gc()
            }

            updateForegroundNotification(WorkStage.COMPLETE, 100)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Consider more specific error handling or Result.failure()
            Result.retry()
        }
    }
}
