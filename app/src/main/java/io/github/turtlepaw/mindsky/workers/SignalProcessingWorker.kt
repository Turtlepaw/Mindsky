package io.github.turtlepaw.mindsky.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetActorLikesQueryParams
import app.bsky.feed.GetActorLikesResponse
import app.bsky.feed.Post
import io.github.turtlepaw.mindsky.db.Engagement
import io.github.turtlepaw.mindsky.db.EngagementType
import io.github.turtlepaw.mindsky.auth.UserSession
// import io.github.turtlepaw.mindsky.workers.FeedWorker.Companion.API_REQUEST_LIMIT // Replaced with WorkerCommon
// import io.github.turtlepaw.mindsky.workers.FeedWorker.Companion.THERMAL_COOLDOWN_MS // Replaced with WorkerCommon
import io.github.turtlepaw.mindsky.logic.PostEmbedder
import io.github.turtlepaw.mindsky.logic.ranking.InterestCluster
import io.github.turtlepaw.mindsky.logic.ranking.InterestClusterer
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.Did
import java.lang.Exception // More specific exceptions can be used if needed

class SignalProcessingWorker(
    private val appContext: Context, // Made private as it'''s used internally
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    enum class Stage {
        INITIALIZING,
        FETCHING_LIKES,
        PROCESSING_LIKES,
        COMPLETED
    }

    private val logTag = "SignalProcessingWorker" // Consistent log tag

    /**
     * Initializes the like processing by fetching existing likes from the database.
     */
    private fun initializeLikeData(box: Box<Engagement>): Pair<MutableList<Engagement>, MutableSet<String>> {
        val allLikes = mutableListOf<Engagement>()
        val existingLikes = box.all
        allLikes.addAll(existingLikes)
        val knownUris = mutableSetOf<String>().apply {
            addAll(allLikes.map { it.uri })
        } // Initially, all stored likes are known.
        Log.d(logTag, "Initialized like data. Found ${existingLikes.size} existing likes.")
        return Pair(allLikes, knownUris)
    }

    /**
     * Fetches a single page of likes from the API.
     */
    private suspend fun fetchLikesPageInternal(
        api: AuthenticatedXrpcBlueskyApi,
        userDid: Did,
        cursor: String?
    ): GetActorLikesResponse? {
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
            null // Return null on error to be handled by the caller
        }
    }

    /**
     * Processes a batch of fetched likes, embeds them, and stores them in the database.
     * Returns all processed likes, including those already known.
     */
    private suspend fun processLikesBatch(
        likeViewsToProcess: List<FeedViewPost>, // All likes to be processed
        box: Box<Engagement>,
        knownUris: MutableSet<String>,
        postEmbedder: PostEmbedder = PostEmbedder(appContext),
    ): List<Engagement> {
        if (likeViewsToProcess.isEmpty()) return emptyList()
        val likesToEmbedAndStore = mutableListOf<Engagement>()
        val processedEngagements = mutableListOf<Engagement>() // To store all processed engagements

        for (likeView in likeViewsToProcess) {
            if (isStopped) {
                Log.i(logTag, "Worker stopped during like batch processing.")
                break // Exit loop if worker is stopped
            }
            try {
                // Ensure post and post.record are not null
                val postRecordValue = likeView.post.record?.value
                if (postRecordValue == null) {
                    Log.w(logTag, "Skipping like, post record is null: ${likeView.post.uri}")
                    continue // This continue is now directly in the for loop
                }
                val postRecord = postRecordValue // Use the non-null value
                // Ensure it'''s actually a Post object
                if (postRecord !is Post) {
                    Log.w(
                        logTag,
                        "Skipping like, record is not of type Post: ${likeView.post.uri}, type was ${postRecord::class.simpleName}"
                    )
                    continue
                }

                val post = postRecord // Now smart-cast to Post

                if (post.text.isNullOrBlank()) {
                    // Log.w(logTag, "Skipping like with empty text: ${likeView.post.uri}")
                    continue
                }
                val vector =
                    postEmbedder.encode(post.text) // Assuming encode handles potential errors
                val newVector = Engagement(
                    uri = likeView.post.uri.atUri,
                    cid = likeView.post.cid.cid,
                    createdAt = post.createdAt.epochSeconds,
                    embedding = vector,
                    text = post.text,
                    authorDid = likeView.post.author.did.did,
                    type = EngagementType.Like,
                )

                // Add to processed list regardless of whether it's new or known
                processedEngagements.add(newVector)

                // Only add to likesToEmbedAndStore if it's a new like
                if (likeView.post.uri.atUri !in knownUris) {
                    likesToEmbedAndStore.add(newVector)
                }
            } catch (e: Exception) { // Catching more specific exceptions like SerializationException might be better
                Log.e(logTag, "Error processing like for embedding: ${likeView.post.uri}", e)
            }
        }

        // Store only the new likes
        if (likesToEmbedAndStore.isNotEmpty()) {
            try {
                box.put(likesToEmbedAndStore)
                knownUris.addAll(likesToEmbedAndStore.map { it.uri })
                Log.d(logTag, "Stored batch of ${likesToEmbedAndStore.size} new likes.")
            } catch (dbE: Exception) {
                Log.e(logTag, "Error storing batch of new likes", dbE)
            }
        }
        return processedEngagements // Return all processed likes
    }

    private suspend fun getLikes(
        api: AuthenticatedXrpcBlueskyApi,
        session: UserSession,
        knownUris: MutableSet<String>,
        onProgress: suspend (Int) -> Unit
    ): List<FeedViewPost> = withContext(Dispatchers.IO) {
        var cursor: String? = null
        var pagesFetched = 0 // Initialize pagesFetched
        val allLikes = mutableListOf<FeedViewPost>()
        val fetchedLikes = mutableListOf<FeedViewPost>() // Store all fetched likes, not just new ones
        try {
            while (pagesFetched < WorkerCommon.MAX_PAGES_TO_FETCH_LIKES) {
                if (isStopped) {
                    Log.i(logTag, "Worker stopped, exiting likes fetch loop.")
                    break
                }

                val progress = (pagesFetched * 100) / WorkerCommon.MAX_PAGES_TO_FETCH_LIKES.coerceAtLeast(1)
                onProgress(progress)

                val response = fetchLikesPageInternal(api, Did(session.did), cursor)
                val pageLikes = response?.feed ?: emptyList()

                if (response == null) {
                    Log.w(logTag, "Failed to fetch likes page ${pagesFetched + 1}. Cooling down...")
                    delay(WorkerCommon.THERMAL_COOLDOWN_MS)
                    pagesFetched++
                    continue
                }

                fetchedLikes.addAll(pageLikes) // Add all likes from the page
                allLikes.addAll(pageLikes.filter { it.post.uri.atUri !in knownUris }) // Only new likes for processing

                if (pageLikes.isEmpty() && response.cursor == null) {
                    Log.d(logTag, "No new likes and no more pages.")
                    break
                }

                cursor = response.cursor ?: break
                pagesFetched++
                delay(WorkerCommon.THERMAL_COOLDOWN_MS)
            }
        } catch (e: Exception) {
            Log.e(logTag, "Exception while fetching likes", e)
        } finally {
            Log.i(
                logTag,
                "Likes fetch complete. Total pages: $pagesFetched. Total new likes: ${allLikes.size}. Total fetched likes: ${fetchedLikes.size}."
            )
            onProgress(100)
        }
        return@withContext fetchedLikes
    }


    override suspend fun doWork(): Result {
        Log.d(logTag, "SignalProcessingWorker started.")
        return try {
            val objectBoxStore = WorkerCommon.safelyGetObjectBox(appContext)
            val session = WorkerCommon.getSession(appContext)

            if (session == null) {
                Log.e(logTag, "No active session found. Worker cannot proceed.")
                return Result.failure() // Fail if no session is available
            }

            val blueskyApi = WorkerCommon.getBlueskyApi(session)
            if (blueskyApi !is AuthenticatedXrpcBlueskyApi) {
                Log.e(logTag, "Bluesky API is not authenticated, cannot proceed.")
                return Result.failure() // Fail if API is not authenticated
            }

            val (allLikes, knownUris) = initializeLikeData(objectBoxStore.boxFor(Engagement::class.java))

            Log.d(logTag, "Starting to pull likes for user: ${session.did}")
            val likedPosts = getLikes(
                blueskyApi, session, knownUris, getProgressCallback(
                    Stage.FETCHING_LIKES
                )
            ) // This now returns all fetched FeedViewPost objects

            // Process all fetched likes. The function will handle storing only new ones.
            val allProcessedLikes = processLikesBatch(
                likedPosts,
                objectBoxStore.boxFor(Engagement::class.java),
                knownUris,
            ) // This now returns a List<Engagement> of all processed likes
            // `allLikes` (from initializeLikeData) is updated within processLikesBatch via knownUris and box.put
            val clusterer = InterestClusterer()
            val userProfile = clusterer.createClusters(allProcessedLikes)

            // store clusters
            objectBoxStore.boxFor(InterestCluster::class.java)

            Log.d(logTag, "SignalProcessingWorker finished successfully.")
            Result.success()
        } catch (e: NotImplementedError) {
            Log.e(logTag, "Worker failed due to unimplemented dependency: ${e.message}", e)
            Result.failure() // Fail if essential components are not implemented
        } catch (e: Exception) {
            Log.e(logTag, "Error in doWork", e)
            Result.failure()
        }
    }

    fun getProgressCallback(stage: Stage): (Int) -> Unit = { progress ->
        setProgressAsync(
            workDataOf(
                WorkerCommon.PROGRESS to progress,
                WorkerCommon.STAGE to stage.name
            )
        )
        Log.d(logTag, "Progress updated: $progress%")
    }
}
