package io.github.turtlepaw.mindsky


import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetTimelineQueryParams
import kotlinx.coroutines.launch
import sh.christian.ozone.BlueskyApi

class FeedViewModel(
    private val api: BlueskyApi
) : ViewModel() {

    var feed = mutableStateOf<List<FeedViewPost>?>(null)
        private set

    var isFetchingFeed = mutableStateOf(false)
        private set

    var error = mutableStateOf<String?>(null)
        private set

    private var lastFetchTime = 0L

    init {
        fetchFeed() // Fetch feed when ViewModel is created
    }

    fun fetchFeed(limit: Long = 100) {
        // 1. If feed data already exists, don't re-fetch
        if (feed.value != null) {
            Log.d("FeedVM", "Feed already loaded, skipping fetch.")
            return
        }

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
                Log.d("FeedVM", "Fetching feed...")
                Log.d("FeedVM", "Calling api.getTimeline with limit: $limit")
                val timelineResponse = api.getTimeline(GetTimelineQueryParams(limit = limit))
                Log.d("FeedVM", "Got timelineResponse, calling .maybeResponse()")
                val maybeResult = timelineResponse.maybeResponse()
                Log.d("FeedVM", "Got maybeResult, accessing .feed")
                val result = maybeResult?.feed
                Log.d("FeedVM", "Accessed .feed, result is: ${if (result == null) "null" else "not null, size: " + result.size}")
                feed.value = result
                Log.d("FeedVM", "Feed value set. Feed fetched size: ${result?.size}")
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
}
