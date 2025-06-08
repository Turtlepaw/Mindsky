package io.github.turtlepaw.mindsky.logic

import android.content.Context
import android.util.Log
import androidx.work.*
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

class FeedWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        val WORK_NAME = "FeedWorker"
        fun buildWorkRequest(): OneTimeWorkRequest {
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

        val httpClient = HttpClient(OkHttp) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.v("Ktor_Default", message)
                    }
                }
                level = LogLevel.HEADERS // Or your preferred level for default
            }
            defaultRequest {
                // It's good to have a default, even if it's just bsky.social
                // or if some unauthenticated calls are possible
                url.takeFrom("https://bsky.social")
            }
            expectSuccess = true
        }

        return AuthenticatedXrpcBlueskyApi(
            initialTokens = authTokensFlow.value,
            httpClient = httpClient,
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
            val objectBox = if (ObjectBox.store == null) {
                ObjectBox.init(applicationContext)
            } else {
                ObjectBox.store
            }
            val postEmbedderBox = objectBox.boxFor(EmbeddedPost::class.java)

            // clear existing posts
            postEmbedderBox.removeAll()

            for ((index, feedViewPost) in familiarFeed.withIndex()) {
                val progress = (index + 1) * 100 / totalPosts
                val postView = feedViewPost.post
                val post = postView.record.decodeAs<Post>()
                val embedding = postEmbedder.encode(post.text)
                Log.d("FeedWorker", "${post.text} = ${embedding.joinToString(prefix = "[", postfix = "]")}")
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
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
