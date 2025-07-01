package io.github.turtlepaw.mindsky.workers

// import io.github.turtlepaw.mindsky.workers.FeedWorker.Companion.API_REQUEST_LIMIT // Replaced with WorkerCommon
// import io.github.turtlepaw.mindsky.workers.FeedWorker.Companion.THERMAL_COOLDOWN_MS // Replaced with WorkerCommon
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetActorLikesQueryParams
import app.bsky.feed.GetActorLikesResponse
import app.bsky.feed.Post
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import io.github.turtlepaw.mindsky.db.Engagement
import io.github.turtlepaw.mindsky.db.EngagementType
import io.github.turtlepaw.mindsky.logic.PostEmbedder
import io.github.turtlepaw.mindsky.logic.ranking.InterestCluster
import io.github.turtlepaw.mindsky.logic.ranking.InterestClusterer
import io.github.turtlepaw.mindsky.workers.WorkerManager.enqueueImmediateWorkers
import io.github.turtlepaw.mindsky.workers.WorkerManager.getFeedWorkerRequest
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.Did

class SignalProcessingWorker(
    private val appContext: Context, // Made private as it's used internally
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    companion object {
        /**
         * Flag to indicate if this worker should enqueue the [FeedWorker] after processing.
         */
        const val SHOULD_ENQUEUE_FEED_WORKER = "should_enqueue_feed_worker"
    }
    enum class Stage(val displayName: String) {
        INITIALIZING("Initializing"),
        FETCHING_LIKES("Fetching Likes"),
        PROCESSING_LIKES("Processing Likes"),
        COMPLETED("Completed")
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
        onProgress: (Int) -> Unit
    ): List<Engagement> {
        if (likeViewsToProcess.isEmpty()) return emptyList()
        val likesToEmbedAndStore = mutableListOf<Engagement>()
        val processedEngagements = mutableListOf<Engagement>() // To store all processed engagements

        for (likeView in (likeViewsToProcess.takeLast(150))) {
            if (isStopped) {
                Log.i(logTag, "Worker stopped during like batch processing.")
                break // Exit loop if worker is stopped
            }
            try {
                // Ensure post and post.record are not null
                val post = likeView.post.record.decodeAs<Post>()
                if (post == null) {
                    Log.w(logTag, "Skipping like, post record is null: ${likeView.post.uri}")
                    continue // This continue is now directly in the for loop
                }

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

                onProgress(
                    (processedEngagements.size * 100) / likeViewsToProcess.size.coerceAtLeast(1)
                )
                delay(
                    2_000L // Delay to prevent overwhelming the API and allow for thermal cooldown
                )
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

    private suspend fun checkIfWorkerAlreadyRunning(): Boolean {
        val workManager = WorkManager.getInstance(appContext)
        val existingWork = workManager.getWorkInfos(
            WorkQuery.fromTags(
                this::class.java.simpleName
            )
        ).get()
        return existingWork.isNotEmpty() && existingWork[0].state == WorkInfo.State.RUNNING
    }

    override suspend fun doWork(): Result {
        Log.d(logTag, "SignalProcessingWorker started.")
        return try {

            val objectBoxStore = WorkerCommon.safelyGetObjectBox(appContext)
            val sessionManager = SessionManager(appContext)
            val session = sessionManager.getSession()

            if (session == null) {
                Log.e(logTag, "No active session found. Worker cannot proceed.")
                return Result.failure() // Fail if no session is available
            }

            val blueskyApi = WorkerCommon.getBlueskyApi(sessionManager)
            if (blueskyApi !is AuthenticatedXrpcBlueskyApi) {
                Log.e(logTag, "Bluesky API is not authenticated, cannot proceed.")
                return Result.failure() // Fail if API is not authenticated
            }

            val (allLikesFromDb, knownUris) = initializeLikeData(objectBoxStore.boxFor(Engagement::class.java)) 

            Log.d(logTag, "Starting to pull likes for user: ${session.did}")
            val likedPosts = getLikes(
                blueskyApi, session, knownUris, getProgressCallback(
                    Stage.FETCHING_LIKES
                )
            ).filter { like ->
                like.post.uri.atUri !in allLikesFromDb.map { it.uri }
            }

            if (checkIfWorkerAlreadyRunning()) {
                Log.i(logTag, "Another embedding worker is running, skipping...")
                WorkManager.getInstance(appContext).apply {
                    enqueueImmediateWorkers()
                    cancelAllWorkByTag(this::class.java.simpleName)
                }
                return Result.success()
            }

            Log.d(logTag, "Fetched ${likedPosts.size} new likes for user: ${session.did}")
            val allProcessedLikes = processLikesBatch(
                likedPosts,
                objectBoxStore.boxFor(Engagement::class.java),
                knownUris,
                onProgress = getProgressCallback(
                    Stage.PROCESSING_LIKES
                )
            )

            val clusterer = InterestClusterer()
            val userProfile =
                clusterer.createClusters(allProcessedLikes) // Ensure this has all data needed

            // store clusters
            objectBoxStore.boxFor(InterestCluster::class.java).apply {
                removeAll()
                put(
                    userProfile.first
                )
            }

            Log.d(logTag, "SignalProcessingWorker finished successfully.")

            // Enqueue FeedWorker to run after a delay
            val shouldEnqueueFeedWorker = inputData.getBoolean(
                SHOULD_ENQUEUE_FEED_WORKER, true
            )

            if (shouldEnqueueFeedWorker) {
                WorkManager.getInstance(applicationContext).apply {
                    val request = getFeedWorkerRequest(8)
                    enqueueUniqueWork(
                        "FeedWorker_Periodic_Chained",
                        ExistingWorkPolicy.REPLACE,
                        request
                    )
                }
            }

            Log.i(logTag, "Enqueued FeedWorker to run.")
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
