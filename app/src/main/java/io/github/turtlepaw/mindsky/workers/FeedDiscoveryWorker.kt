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
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetFeedQueryParams
import app.bsky.feed.Post
import app.bsky.unspecced.GetPopularFeedGeneratorsQueryParams
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.db.EmbeddedPost
import io.github.turtlepaw.mindsky.db.Engagement
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.github.turtlepaw.mindsky.db.SuggestedFeed
import io.github.turtlepaw.mindsky.logic.PostEmbedder
import io.github.turtlepaw.mindsky.logic.ranking.InterestCluster
import io.github.turtlepaw.mindsky.logic.ranking.MultiInterestUserProfile
import io.github.turtlepaw.mindsky.logic.ranking.PostRanker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.response.AtpResponse
import kotlin.math.sqrt

class FeedDiscoveryWorker(
    val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    enum class WorkStage(val displayName: String) {
        STARTING("Starting feed discovery..."),
        ANALYZING_CLUSTERS("Analyzing your interests..."),
        FETCHING_POPULAR_FEEDS("Fetching popular feeds..."),
        COMPUTING_SIMILARITIES("Computing feed similarities..."),
        SAMPLING_FEED_CONTENT("Sampling feed content..."),
        RANKING_RESULTS("Ranking feed suggestions..."),
        COMPLETE("Feed discovery complete.")
    }

    // Simplified data classes for semantic similarity approach
    data class InterestProfile(
        val clusterId: Long,
        val centerEmbedding: FloatArray,
        val postCount: Int,
        val sampleTexts: List<String>
    )

    data class FeedCandidate(
        val uri: AtUri,
        val name: String,
        val description: String,
        val creator: String,
        val displayName: String? = null
    )

    data class FeedWithSimilarity(
        val candidate: FeedCandidate,
        val similarityScore: Float,
        val matchedClusterId: Long,
        val contentFreshness: Float,
        val postFrequency: Float,
        val samplePosts: List<String> = emptyList()
    )

    data class FeedRecommendation(
        val feed: FeedWithSimilarity,
        val finalScore: Float,
        val explanation: String
    )

    companion object {
        const val CHANNEL_ID = "feed_discovery_channel"
        const val NOTIFICATION_ID = 2
        private const val SAMPLE_POSTS_FOR_ANALYSIS = 3 // Down from 10
        private const val MAX_FEEDS_TO_ANALYZE = 15 // Down from 50
        private const val MAX_FEED_RECOMMENDATIONS = 10 // Down from 10
        private const val MIN_POSTS_PER_CLUSTER = 5
        private const val MIN_SIMILARITY_THRESHOLD = 0.3f

        // Time management constants
        private const val MAX_EXECUTION_TIME_MS = 9 * 60 * 1000L // 9 minutes
        private const val TIME_BUFFER_MS = 30 * 1000L // 30 seconds buffer for cleanup
        private const val EFFECTIVE_MAX_TIME_MS = MAX_EXECUTION_TIME_MS - TIME_BUFFER_MS

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Feed Discovery",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress of feed discovery process."
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

    private var startTime: Long = 0L
    private fun getRemainingTime(): Long = EFFECTIVE_MAX_TIME_MS - (System.currentTimeMillis() - startTime)
    private fun hasTimeRemaining(): Boolean = getRemainingTime() > 0

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
                Log.e("FeedDiscoveryWorker", "Failed to update notification", e)
            }
        }
    }

    private fun createNotification(
        stage: WorkStage,
        progress: Int,
        indeterminate: Boolean = false
    ): Notification {
        val title = "Mindsky Feed Discovery"
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
            .setSmallIcon(R.drawable.ic_popup_sync)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate && stage != WorkStage.COMPLETE)
            .build()
    }

    // Phase 1: Analyze existing clusters and create interest profiles
    private suspend fun analyzeInterestClusters(): List<InterestProfile> = withContext(Dispatchers.IO) {
        Log.i("FeedDiscoveryWorker", "Phase 1: Analyzing interest clusters...")
        updateProgressNotification(WorkStage.ANALYZING_CLUSTERS, 0, indeterminate = true)

        if (!hasTimeRemaining()) return@withContext emptyList()

        val objectBox = if (ObjectBox.store == null) ObjectBox.init(appContext) else ObjectBox.store
        val interestClusterBox = objectBox.boxFor(InterestCluster::class.java)
        val embeddedPostBox = objectBox.boxFor(Engagement::class.java)

        val allClusters = interestClusterBox.all
        val allPosts = embeddedPostBox.all

        Log.i("FeedDiscoveryWorker", "Found ${allClusters.size} existing clusters with ${allPosts.size} posts")

        // Assign posts to clusters using similarity
        val postToClusterMap = assignPostsToClusters(allPosts, allClusters)

        val interestProfiles = mutableListOf<InterestProfile>()

        for ((index, cluster) in allClusters.withIndex()) {
            if (isStopped || !hasTimeRemaining()) break

            val progress = ((index + 1) * 100 / allClusters.size.coerceAtLeast(1))
            updateProgressNotification(WorkStage.ANALYZING_CLUSTERS, progress)

            val clusterPosts = postToClusterMap[cluster.id] ?: emptyList()

            if (clusterPosts.size >= MIN_POSTS_PER_CLUSTER) {
                val sampleTexts = clusterPosts.take(10).map { it.text }

                interestProfiles.add(
                    InterestProfile(
                        clusterId = cluster.id,
                        centerEmbedding = cluster.centerEmbedding,
                        postCount = clusterPosts.size,
                        sampleTexts = sampleTexts
                    )
                )

                Log.d("FeedDiscoveryWorker", "Interest cluster ${cluster.id}: ${clusterPosts.size} posts")
            }
        }

        Log.i("FeedDiscoveryWorker", "Phase 1 complete: Created ${interestProfiles.size} interest profiles")
        return@withContext interestProfiles
    }

    // Assign posts to clusters using similarity
    private fun assignPostsToClusters(posts: List<Engagement>, clusters: List<InterestCluster>): Map<Long, List<Engagement>> {
        val postToClusterMap = mutableMapOf<Long, MutableList<Engagement>>()

        clusters.forEach { cluster ->
            postToClusterMap[cluster.id] = mutableListOf()
        }

        for (post in posts) {
            var bestCluster: InterestCluster? = null
            var bestSimilarity = -1f

            for (cluster in clusters) {
                val similarity = cosineSimilarity(post.embedding, cluster.centerEmbedding)
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestCluster = cluster
                }
            }

            bestCluster?.let { cluster ->
                postToClusterMap[cluster.id]?.add(post)
            }
        }

        return postToClusterMap
    }

    // Phase 2: Fetch popular feeds to analyze
    private suspend fun fetchPopularFeeds(api: AuthenticatedXrpcBlueskyApi): List<FeedCandidate> = withContext(Dispatchers.IO) {
        Log.i("FeedDiscoveryWorker", "Phase 2: Fetching popular feeds...")
        updateProgressNotification(WorkStage.FETCHING_POPULAR_FEEDS, 0, indeterminate = true)

        if (!hasTimeRemaining()) return@withContext emptyList()

        val allFeeds = mutableListOf<FeedCandidate>()
        var currentLimit = 50

        try {
            // Use broad search terms to get diverse feeds
            val searchTerms = listOf("", "art", "music", "tech", "news", "gaming", "food", "sports", "science")

            for ((index, searchTerm) in searchTerms.withIndex()) {
                if (isStopped || !hasTimeRemaining()) break

                val progress = ((index + 1) * 100 / searchTerms.size)
                updateProgressNotification(WorkStage.FETCHING_POPULAR_FEEDS, progress)

                try {
                    val response = api.getPopularFeedGeneratorsUnspecced(
                        GetPopularFeedGeneratorsQueryParams(
                            limit = if (searchTerm.isEmpty()) 30 else 10, // Get more general feeds
                            query = searchTerm.ifEmpty { null }
                        )
                    ).maybeResponse()

                    response?.feeds?.forEach { feedGenerator ->
                        try {
                            allFeeds.add(
                                FeedCandidate(
                                    uri = feedGenerator.uri,
                                    name = feedGenerator.displayName ?: "Unknown Feed",
                                    description = feedGenerator.description ?: "",
                                    creator = feedGenerator.creator.displayName ?: "Unknown",
                                    displayName = feedGenerator.displayName
                                )
                            )
                        } catch (e: Exception) {
                            Log.w("FeedDiscoveryWorker", "Error parsing feed generator", e)
                        }
                    }

                    // Adaptive delay based on remaining time
                    val adaptiveDelay = if (getRemainingTime() > 120_000L) 2_000L else 1_000L
                    delay(adaptiveDelay)

                } catch (e: Exception) {
                    Log.e("FeedDiscoveryWorker", "Error fetching feeds for term '$searchTerm'", e)
                }
            }

        } catch (e: Exception) {
            Log.e("FeedDiscoveryWorker", "Error in fetchPopularFeeds", e)
        }

        val uniqueFeeds = allFeeds.distinctBy { it.uri }.take(MAX_FEEDS_TO_ANALYZE)
        Log.i("FeedDiscoveryWorker", "Phase 2 complete: Fetched ${uniqueFeeds.size} unique feeds")
        return@withContext uniqueFeeds
    }

    // Phase 3: Compute semantic similarities between feeds and user interests
    private suspend fun computeFeedSimilarities(
        api: AuthenticatedXrpcBlueskyApi,
        interestProfiles: List<InterestProfile>,
        feedCandidates: List<FeedCandidate>
    ): List<FeedWithSimilarity> = withContext(Dispatchers.IO) {
        Log.i("FeedDiscoveryWorker", "Phase 3: Computing feed similarities...")
        updateProgressNotification(WorkStage.COMPUTING_SIMILARITIES, 0, indeterminate = true)

        if (!hasTimeRemaining()) return@withContext emptyList()

        val postEmbedder = PostEmbedder(appContext)
        val feedsWithSimilarity = mutableListOf<FeedWithSimilarity>()

        for ((index, feed) in feedCandidates.withIndex()) {
            if (isStopped || !hasTimeRemaining()) break

            val progress = ((index + 1) * 100 / feedCandidates.size)
            updateProgressNotification(WorkStage.SAMPLING_FEED_CONTENT, progress)

            try {
                // Sample posts from the feed
                val samplePosts = sampleFeedPosts(api, feed.uri)

                if (samplePosts.isNotEmpty()) {
                    // Compute embeddings for sample posts
                    val postEmbeddings = mutableListOf<FloatArray>()
                    val validSampleTexts = mutableListOf<String>()

                    for (post in samplePosts.take(SAMPLE_POSTS_FOR_ANALYSIS)) {
                        if (!hasTimeRemaining()) break

                        try {
                            if (post.text.isNotBlank()) {
                                val embedding = postEmbedder.encode(post.text)
                                postEmbeddings.add(embedding)
                                validSampleTexts.add(post.text)

                                // Adaptive delay for ML tasks
                                val adaptiveDelay = if (getRemainingTime() > 120_000L) 2_000L else 1_000L
                                delay(adaptiveDelay)
                            }
                        } catch (e: Exception) {
                            Log.w("FeedDiscoveryWorker", "Failed to embed post from ${feed.name}", e)
                        }
                    }

                    if (postEmbeddings.isNotEmpty()) {
                        // Compute average embedding for the feed
                        val feedEmbedding = computeAverageEmbedding(postEmbeddings)

                        // Find best matching interest cluster
                        var bestSimilarity = -1f
                        var bestClusterId = -1L

                        for (profile in interestProfiles) {
                            val similarity = cosineSimilarity(feedEmbedding, profile.centerEmbedding)
                            if (similarity > bestSimilarity) {
                                bestSimilarity = similarity
                                bestClusterId = profile.clusterId
                            }
                        }

                        // Only include feeds above similarity threshold
                        if (bestSimilarity >= MIN_SIMILARITY_THRESHOLD) {
                            feedsWithSimilarity.add(
                                FeedWithSimilarity(
                                    candidate = feed,
                                    similarityScore = bestSimilarity,
                                    matchedClusterId = bestClusterId,
                                    contentFreshness = calculateContentFreshness(samplePosts),
                                    postFrequency = calculatePostFrequency(samplePosts),
                                    samplePosts = validSampleTexts.take(3)
                                )
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("FeedDiscoveryWorker", "Error analyzing feed ${feed.name}", e)
            }
        }

        Log.i("FeedDiscoveryWorker", "Phase 3 complete: Found ${feedsWithSimilarity.size} similar feeds")
        return@withContext feedsWithSimilarity
    }

    // Phase 4: Rank and create final recommendations
    private suspend fun createRecommendations(feedsWithSimilarity: List<FeedWithSimilarity>): List<FeedRecommendation> = withContext(Dispatchers.IO) {
        Log.i("FeedDiscoveryWorker", "Phase 4: Creating recommendations...")
        updateProgressNotification(WorkStage.RANKING_RESULTS, 0, indeterminate = true)

        val recommendations = feedsWithSimilarity.map { feedWithSim ->
            val finalScore = calculateFinalScore(feedWithSim)
            val explanation = generateExplanation(feedWithSim)

            FeedRecommendation(
                feed = feedWithSim,
                finalScore = finalScore,
                explanation = explanation
            )
        }.sortedByDescending { it.finalScore }
            .take(MAX_FEED_RECOMMENDATIONS)

        Log.i("FeedDiscoveryWorker", "Phase 4 complete: Created ${recommendations.size} recommendations")
        return@withContext recommendations
    }

    // Helper functions
    private suspend fun sampleFeedPosts(api: AuthenticatedXrpcBlueskyApi, feedUri: AtUri): List<PostSample> {
        return try {
            val response = api.getFeed(
                GetFeedQueryParams(
                    feed = feedUri,
                    limit = SAMPLE_POSTS_FOR_ANALYSIS.toLong()
                )
            ).maybeResponse()

            response?.feed?.mapNotNull { feedViewPost ->
                try {
                    val post = feedViewPost.post.record.decodeAs<Post>()
                    PostSample(
                        text = post.text,
                        createdAt = post.createdAt.epochSeconds
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("FeedDiscoveryWorker", "Error sampling posts from feed $feedUri", e)
            emptyList()
        }
    }

    private fun computeAverageEmbedding(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(0)

        val embeddingSize = embeddings.first().size
        val average = FloatArray(embeddingSize) { 0f }

        for (embedding in embeddings) {
            for (i in embedding.indices) {
                average[i] += embedding[i]
            }
        }

        for (i in average.indices) {
            average[i] /= embeddings.size
        }

        return average
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        return if (normA > 0 && normB > 0) {
            dotProduct / (sqrt(normA) * sqrt(normB))
        } else 0f
    }

    private fun calculateContentFreshness(posts: List<PostSample>): Float {
        if (posts.isEmpty()) return 0f

        val now = System.currentTimeMillis() / 1000
        val avgAge = posts.map { now - it.createdAt }.average()
        val maxAge = 7 * 24 * 3600 // 7 days in seconds

        return (1f - (avgAge / maxAge).coerceIn(0.0, 1.0)).toFloat()
    }

    private fun calculatePostFrequency(posts: List<PostSample>): Float {
        if (posts.size < 2) return 0f

        val timeSpan = posts.maxOf { it.createdAt } - posts.minOf { it.createdAt }
        return if (timeSpan > 0) posts.size.toFloat() / timeSpan else 0f
    }

    private fun calculateFinalScore(feedWithSim: FeedWithSimilarity): Float {
        return (feedWithSim.similarityScore * 0.7f) +
                (feedWithSim.contentFreshness * 0.2f) +
                (feedWithSim.postFrequency.coerceAtMost(1f) * 0.1f)
    }

    private fun generateExplanation(feedWithSim: FeedWithSimilarity): String {
        val similarityPercent = (feedWithSim.similarityScore * 100).toInt()
        return "This feed matches your interests with ${similarityPercent}% similarity based on content analysis"
    }

    // Data classes
    data class PostSample(
        val text: String,
        val createdAt: Long
    )

    override suspend fun doWork(): Result {
        val wakeLock = (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FeedDiscoveryWorker::MainWakeLock")

        return wakeLock.run {
            try {
                acquire(MAX_EXECUTION_TIME_MS)
                startTime = System.currentTimeMillis()

                Log.i("FeedDiscoveryWorker", "Starting semantic feed discovery process...")
                updateProgressNotification(WorkStage.STARTING, 0, indeterminate = true)

                val sessionManager = SessionManager(appContext)
                if (sessionManager.getSession() == null) {
                    Log.e("FeedDiscoveryWorker", "No active session")
                    return Result.failure()
                }
                val api = WorkerCommon.getBlueskyApi(appContext)

                // Phase 1: Analyze interest clusters
                val interestProfiles = analyzeInterestClusters()
                if (interestProfiles.isEmpty() || !hasTimeRemaining()) {
                    Log.w("FeedDiscoveryWorker", "No suitable interest profiles found or out of time")
                    updateProgressNotification(WorkStage.COMPLETE, 100)
                    return Result.success()
                }

                // Phase 2: Fetch popular feeds
                val feedCandidates = fetchPopularFeeds(api)
                if (feedCandidates.isEmpty() || !hasTimeRemaining()) {
                    Log.w("FeedDiscoveryWorker", "No feed candidates found or out of time")
                    updateProgressNotification(WorkStage.COMPLETE, 100)
                    return Result.success()
                }

                // Phase 3: Compute similarities
                val feedsWithSimilarity = computeFeedSimilarities(api, interestProfiles, feedCandidates)
                if (!hasTimeRemaining()) {
                    Log.w("FeedDiscoveryWorker", "Out of time during similarity computation")
                    updateProgressNotification(WorkStage.COMPLETE, 100)
                    return Result.success()
                }

                // Phase 4: Create recommendations
                val recommendations = createRecommendations(feedsWithSimilarity)

                // Store recommendations
                storeRecommendations(recommendations)

                updateProgressNotification(WorkStage.COMPLETE, 100)
                Log.i("FeedDiscoveryWorker", "Semantic feed discovery completed successfully. Found ${recommendations.size} recommendations")

                Result.success()
            } catch (e: Exception) {
                Log.e("FeedDiscoveryWorker", "Error in semantic feed discovery", e)
                Result.retry()
            } finally {
                if (wakeLock.isHeld) {
                    release()
                }
            }
        }
    }

    private suspend fun storeRecommendations(recommendations: List<FeedRecommendation>) {
        Log.i("FeedDiscoveryWorker", "Storing ${recommendations.size} feed recommendations")
        val box = ObjectBox.store.boxFor(SuggestedFeed::class.java)

        try {
            box.removeAll()

            val prefs = appContext.getSharedPreferences("feed_discovery", Context.MODE_PRIVATE)
            val currentTime = System.currentTimeMillis()

            prefs.edit()
                .putString("last_recommendation_time", currentTime.toString())
                .putInt("recommendation_count", recommendations.size)
                .apply()

            recommendations.forEachIndexed { index, recommendation ->
                Log.d("FeedDiscoveryWorker",
                    "Recommendation ${index + 1}: ${recommendation.feed.candidate.name} " +
                            "(Similarity: ${(recommendation.feed.similarityScore * 100).toInt()}%, " +
                            "Final Score: ${String.format("%.3f", recommendation.finalScore)})"
                )

                box.put(
                    SuggestedFeed(
                        uri = recommendation.feed.candidate.uri.atUri,
                        explanation = recommendation.explanation,
                        finalScore = recommendation.finalScore,
                        postFrequency = recommendation.feed.postFrequency,
                        similarityScore = recommendation.feed.similarityScore,
                        contentFreshness = recommendation.feed.contentFreshness,
                        matchedClusterId = recommendation.feed.matchedClusterId
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("FeedDiscoveryWorker", "Error storing recommendations", e)
        }
    }
}