package io.github.turtlepaw.mindsky.logic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetFeedQueryParams
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.Post
import io.github.turtlepaw.mindsky.EmbeddedPost
import io.github.turtlepaw.mindsky.ObjectBox
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.MutableStateFlow
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.BlueskyAuthPlugin
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class FeedWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "feed_worker_channel"
        const val NOTIFICATION_ID = 1
        val WORK_NAME = "FeedWorker"

        val TIME_EVENING = LocalTime.of(16, 0)
        val TIME_MORNING = LocalTime.of(4, 0)

        fun WorkManager.enqueueFeedWorkers(
            existingWorkPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
        ) {
            val eveningWorkRequest = buildWorkRequest(TIME_EVENING)
            val morningWorkRequest = buildWorkRequest(TIME_MORNING)

            enqueueUniquePeriodicWork(WORK_NAME, existingWorkPolicy, eveningWorkRequest)
            enqueueUniquePeriodicWork(WORK_NAME, existingWorkPolicy, morningWorkRequest)
        }

        fun buildWorkRequest(time: LocalTime): PeriodicWorkRequest {
            val delay = if (time.isAfter(LocalTime.now())) {
                // Schedule for the next occurrence of the specified time
                java.time.Duration.between(LocalTime.now(), time).toMillis()
            } else {
                // If the time is in the past today, schedule for tomorrow
                java.time.Duration.between(LocalTime.now(), time.plusHours(24)).toMillis()
            }
            return androidx.work.PeriodicWorkRequestBuilder<FeedWorker>(1, TimeUnit.DAYS).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .setRequiresDeviceIdle(true)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
                .setInitialDelay(
                    delay,
                    TimeUnit.MILLISECONDS
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    java.time.Duration.ofMinutes(15).toMillis(),
                    TimeUnit.MILLISECONDS
                )
                .build()
        }

        private fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Feed Worker Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification channel for FeedWorker foreground service"
            }
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    init {
        createNotificationChannel(applicationContext)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = createNotification(0)
        return ForegroundInfo(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun createNotification(progress: Int): Notification {
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Fetching Bluesky feed")
            .setContentText("Progress: $progress%")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
        return builder.build()
    }

    fun getBlueskyApi(): AuthenticatedXrpcBlueskyApi? {
        val sessionManager = SessionManager(applicationContext)
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

    suspend fun getFamiliarFeeds(
        api: AuthenticatedXrpcBlueskyApi,
        limitPerFeed: Long = 100
    ): List<FeedViewPost> {
        val followingFeed = api.getTimeline(
            GetTimelineQueryParams(
                algorithm = "reverse-chronological",
                limit = limitPerFeed
            )
        ).maybeResponse()?.feed ?: emptyList()

        val discoverFeed = api.getFeed(
            GetFeedQueryParams(
                feed = AtUri("at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot"),
                limit = limitPerFeed
            )
        ).maybeResponse()?.feed ?: emptyList()

        return followingFeed + discoverFeed
    }

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo()) // Run as foreground with notification

        setProgress(workDataOf("progress" to 0))

        return try {
            val api = getBlueskyApi()
            if (api == null) {
                Log.e("FeedWorker", "Bluesky API not initialized")
                return Result.failure()
            }

            val postEmbedder = PostEmbedder(applicationContext)
            val familiarFeed = getFamiliarFeeds(api)
            val totalPosts = familiarFeed.size

            val objectBox = if (ObjectBox.store == null) {
                ObjectBox.init(applicationContext)
            } else {
                ObjectBox.store
            }
            val postEmbedderBox = objectBox.boxFor(EmbeddedPost::class.java)

            postEmbedderBox.removeAll()

            for ((index, feedViewPost) in familiarFeed.withIndex()) {
                val progress = (index + 1) * 100 / totalPosts
                val postView = feedViewPost.post
                val post = postView.record.decodeAs<Post>()
                val embedding = postEmbedder.encode(post.text)
                Log.d(
                    "FeedWorker",
                    "${post.text} = ${embedding.joinToString(prefix = "[", postfix = "]")}"
                )
                postEmbedderBox.put(
                    EmbeddedPost(
                        uri = postView.uri.atUri,
                        text = post.text,
                        embedding = embedding,
                        authorDid = postView.author.did.did,
                        timestamp = post.createdAt.epochSeconds,
                        score = 0f
                    )
                )
                setProgress(workDataOf("progress" to progress))
                // Update notification progress
                val notification = createNotification(progress)
                (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, notification)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
