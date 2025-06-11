package io.github.turtlepaw.mindsky.viewmodels

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.PostView
import io.github.turtlepaw.mindsky.EmbeddedPost
import io.github.turtlepaw.mindsky.EmbeddedPost_
import io.github.turtlepaw.mindsky.ObjectBox
import io.github.turtlepaw.mindsky.logic.FeedTuner
import io.github.turtlepaw.mindsky.utils.ApiUtils.fetchChunkedPosts
import io.objectbox.Box
import kotlinx.coroutines.launch
import sh.christian.ozone.BlueskyApi
import kotlin.random.Random

class FeedViewModel(
    private val api: BlueskyApi
) : ViewModel() {

    var followingFeed = mutableStateOf<List<FeedViewPost>?>(null)
        private set
    var isFetchingFeed = mutableStateOf(false)
        private set
    var isFetchingMoreFollowing = mutableStateOf(false)
        private set

    var forYouFeed = mutableStateOf<List<Pair<EmbeddedPost, PostView>>?>(null)
        private set

    var error = mutableStateOf<String?>(null)
        private set

    private var lastFetchTime = 0L
    private var followingFeedCursor: String? = null

    init {
        fetchFeed()
    }

    fun fetchFeed(limit: Long = 100, isRefresh: Boolean = false) {
        if (isFetchingFeed.value && !isRefresh) {
            Log.d("FeedVM", "Already fetching initial feed.")
            return
        }
        if (isFetchingMoreFollowing.value && !isRefresh) {
            // If a "load more" is happening, a general refresh might be too disruptive or redundant
            Log.d("FeedVM", "Already fetching more posts, refresh delayed or ignored.")
            return
        }

        val now = System.currentTimeMillis()
        if (!isRefresh && (now - lastFetchTime < 5000)) { // Cooldown only if not a manual refresh
            Log.d("FeedVM", "Fetch cooldown active.")
            return
        }

        viewModelScope.launch {
            isFetchingFeed.value = true
            if (isRefresh) {
                followingFeedCursor = null // Reset cursor on refresh
                // Optionally clear feed: followingFeed.value = emptyList() for faster UI update
            }
            lastFetchTime = now // Update last fetch time for both initial and refresh
            error.value = null // Clear previous errors

            try {
                Log.d("FeedVM", "Fetching following feed. Refresh: $isRefresh")
                fetchFollowingPostsInternal(
                    limit = limit,
                    isLoadMore = false
                ) // 'false' because it's initial/refresh

                Log.d("FeedVM", "Fetching For You feed.")
                fetchForYouPostsInternal(limit = limit)

            } catch (e: Exception) {
                Log.e("FeedVM", "Error fetching feeds (Exception)", e)
                error.value = "Failed to fetch feeds: ${e.message}"
            } catch (t: Throwable) {
                Log.e("FeedVM", "Error fetching feeds (Throwable)", t)
                error.value = "An unexpected error occurred: ${t.message}"
            } finally {
                isFetchingFeed.value = false
            }
        }
    }

    private suspend fun fetchFollowingPostsInternal(limit: Long, isLoadMore: Boolean) {
        val cursorToUse = if (isLoadMore) followingFeedCursor else null
        if (isLoadMore && cursorToUse == null && followingFeed.value?.isNotEmpty() == true) {
            Log.d("FeedVM", "No more items to load for following feed.")
            // Potentially set a flag like allFollowingPostsLoaded.value = true
            return // Nothing more to load
        }

        Log.d(
            "FeedVM",
            "Fetching following. LoadMore: $isLoadMore, Cursor: $cursorToUse, Limit: $limit"
        )
        val timelineResponse = api.getTimeline(
            GetTimelineQueryParams(
                cursor = cursorToUse,
                limit = limit
            )
        )

        timelineResponse.maybeResponse().let { response ->
            val newPosts = FeedTuner.cleanReplies(response?.feed ?: emptyList())
            followingFeedCursor = response?.cursor

            if (isLoadMore) {
                followingFeed.value = (followingFeed.value ?: emptyList()) + newPosts
            } else {
                followingFeed.value = newPosts // Replace for initial load or refresh
            }
        }
    }

    fun loadMoreFollowingFeed(limit: Long = 50) {
        if (isFetchingFeed.value || isFetchingMoreFollowing.value) {
            Log.d("FeedVM", "Cannot load more: Another fetch operation is in progress.")
            return
        }
        if (followingFeedCursor == null && followingFeed.value?.isNotEmpty() == true) {
            Log.d("FeedVM", "No cursor available, cannot load more (all items loaded).")
            // Optionally set a UI message like "All posts loaded"
            return
        }

        viewModelScope.launch {
            isFetchingMoreFollowing.value = true
            error.value = null // Clear previous errors
            try {
                fetchFollowingPostsInternal(limit = limit, isLoadMore = true)
            } catch (e: Exception) {
                Log.e("FeedVM", "Error loading more following posts (Exception)", e)
                error.value = "Failed to load more posts: ${e.message}"
            } catch (t: Throwable) {
                Log.e("FeedVM", "Error loading more following posts (Throwable)", t)
                error.value = "An unexpected error occurred while loading more: ${t.message}"
            } finally {
                isFetchingMoreFollowing.value = false
            }
        }
    }

    private suspend fun fetchForYouPostsInternal(limit: Long = 100) {
        val box = ObjectBox.store.boxFor(EmbeddedPost::class.java)
        if (box.all.isEmpty()) { // Check if ObjectBox has any posts at all
            Log.d(
                "FeedVM",
                "No posts found in ObjectBox for ForYou feed, fetching from API not possible with this logic."
            )
            forYouFeed.value = emptyList() // Set to empty or handle as an error/empty state
            return
        }

        // Get locally scored posts, take up to the specified limit
        val postsFromDb = getPostsSortedByScore(box).take(limit.toInt())

        if (postsFromDb.isEmpty()) {
            Log.d(
                "FeedVM",
                "No posts from DB to fetch details for ForYou feed after applying limit."
            )
            forYouFeed.value = emptyList()
            return
        }

        forYouFeed.value = api.fetchChunkedPosts(
            postsFromDb.map { Pair(it, it.uri) }
        )
    }

    // This is a helper, remains unchanged
    fun getPostsSortedByScore(box: Box<EmbeddedPost>): List<EmbeddedPost> {
        return box.query()
            .notNull(EmbeddedPost_.score) // Ensure score is not null for sorting
            .build()
            .find()
            // Add a small random jitter to score for variety if scores are identical
            .map { it to (it.score!! + Random.Default.nextFloat() * 0.1f) }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}