package io.github.turtlepaw.mindsky


import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.PostView
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.launch
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.api.AtUri
import kotlin.random.Random

class FeedViewModel(
    private val api: BlueskyApi
) : ViewModel() {

    var followingFeed = mutableStateOf<List<FeedViewPost>?>(null)
        private set

    var isFetchingFeed = mutableStateOf(false)
        private set

    var forYouFeed = mutableStateOf<List<PostView>?>(null)
        private set

    var error = mutableStateOf<String?>(null)
        private set

    private var lastFetchTime = 0L

    init {
        fetchFeed()
    }

    fun fetchFeed(limit: Long = 100) {
        // 1. If feed data already exists, don't re-fetch
//        if (feed.value != null) {
//            Log.d("FeedVM", "Feed already loaded, skipping fetch.")
//            return
//        }

        // 2. If already fetching, don't start another fetch
        if (isFetchingFeed.value) {
            Log.d("FeedVM", "Already fetching feed.")
            return
        }

        // 3. Cooldown to prevent rapid successive fetches
        val now = System.currentTimeMillis()
        if (now - lastFetchTime < 5000) {
            Log.d("FeedVM", "Fetch cooldown active.")
            return
        }

        viewModelScope.launch {
            try {
                isFetchingFeed.value = true
                lastFetchTime = now
                Log.d("FeedVM", "Fetching following feed...")
                fetchFollowing()
                fetchForYou()
            } catch (e: Exception) {
                Log.e("FeedVM", "Error fetching feed in ViewModel (Exception)", e)
                error.value = "Failed to fetch feed: ${e.message}" // Set error state
            } catch (t: Throwable) { // Catching Throwable
                Log.e("FeedVM", "Error fetching feed in ViewModel (Throwable)", t)
                error.value = "An unexpected error occurred: ${t.message}" // Set error state
            } finally {
                Log.d("FeedVM", "Finally block: setting isFetchingFeed to false.")
                isFetchingFeed.value = false
            }
        }
    }

    suspend fun fetchFollowing(limit: Long = 100) {
        val timelineResponse = api.getTimeline(GetTimelineQueryParams(limit = limit))
        val maybeResult = timelineResponse.maybeResponse()
        val result = maybeResult?.feed
        followingFeed.value = result
    }

    fun getPostsSortedByScore(box: Box<EmbeddedPost>): List<EmbeddedPost> {
        return box.query()
            .notNull(EmbeddedPost_.score)
            .build()
            .find()
            .map { it to (it.score!! + Random.nextFloat() * 0.1f) }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    suspend fun fetchForYou(limit: Long = 100) {
        val box = ObjectBox.store.boxFor(EmbeddedPost::class.java)
        if (box.all.isEmpty()) {
            Log.d("FeedVM", "No posts found in ObjectBox, fetching from API.")
            forYouFeed.value = emptyList()
            return
        }

        val posts = getPostsSortedByScore(box)

        val uris = posts.map { AtUri(it.uri) }
        val chunkSize = 25
        val allPosts = mutableListOf<PostView>()

        // Split uris into chunks of max 25
        uris.chunked(chunkSize).forEach { chunk ->
            val postRecords = api.getPosts(
                GetPostsQueryParams(
                    uris = chunk
                )
            ).maybeResponse()
            postRecords?.posts?.let { allPosts.addAll(it) }
        }

        forYouFeed.value = allPosts
    }
}
