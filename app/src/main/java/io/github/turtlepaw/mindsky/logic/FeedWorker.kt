package io.github.turtlepaw.mindsky.logic

import android.content.Context
import android.util.Log
import androidx.work.*
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetFeedQueryParams
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.Post
import io.github.turtlepaw.mindsky.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.BlueskyAuthPlugin

class FeedWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        fun buildWorkRequest(stage: DownloadStage): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<FeedWorker>()
                .build()
        }
    }

    fun getBlueskyApi(): AuthenticatedXrpcBlueskyApi? {
        val sessionManager = SessionManager(applicationContext)
        val currentSession = sessionManager.getSession()

        val initialTokens = if (currentSession != null) {
            BlueskyAuthPlugin.Tokens(
                currentSession.accessToken,
                currentSession.refreshToken
            )
        } else {
            return null
        }

        val authTokensFlow = MutableStateFlow(initialTokens)

        return AuthenticatedXrpcBlueskyApi(
            initialTokens = authTokensFlow.value
        )
    }

    suspend fun getFamiliarFeeds(api: AuthenticatedXrpcBlueskyApi, limitPerFeed: Long = 100): List<FeedViewPost> {
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

            var embeddings = mutableListOf<FloatArray>()

            for ((index, feedViewPost) in familiarFeed.withIndex()) {
                val progress = (index + 1) * 100 / totalPosts
                val postView = feedViewPost.post
                val post = postView.record.decodeAs<Post>()
                val embedding = postEmbedder.encode(post.text)
                Log.d("FeedWorker", "${post.text} = ${embedding}")

                embeddings.add(embedding)
                setProgress(workDataOf("progress" to progress))
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
