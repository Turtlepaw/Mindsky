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

    private var lastFetchTime = 0L

    fun fetchFeed(limit: Long = 100) {
        if (isFetchingFeed.value) return
        val now = System.currentTimeMillis()
        if (now - lastFetchTime < 5000) {
            Log.d("FeedVM", "Fetch cooldown")
            return
        }

        viewModelScope.launch {
            try {
                isFetchingFeed.value = true
                lastFetchTime = now
                Log.d("FeedVM", "Fetching feed")
                val result = api.getTimeline(GetTimelineQueryParams(limit = limit)).maybeResponse()?.feed
                feed.value = result
                Log.d("FeedVM", "Feed fetched: ${result?.size}")
            } catch (e: Exception) {
                Log.e("FeedVM", "Error fetching feed", e)
            } finally {
                isFetchingFeed.value = false
            }
        }
    }
}
