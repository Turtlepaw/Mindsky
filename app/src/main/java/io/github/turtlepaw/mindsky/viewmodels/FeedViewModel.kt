package io.github.turtlepaw.mindsky.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bsky.actor.PreferencesUnion
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.GetAuthorFeedQueryParams
import app.bsky.feed.GetFeedGeneratorQueryParams
import app.bsky.feed.GetFeedGeneratorResponse
import app.bsky.feed.GetFeedQueryParams
import app.bsky.feed.GetFeedResponse
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.PostView
import io.github.turtlepaw.fetch_and_cache.LoadResult
import io.github.turtlepaw.mindsky.cache.PostCache
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.github.turtlepaw.mindsky.db.SuggestedFeed
import io.github.turtlepaw.mindsky.logic.FeedTuner
import io.github.turtlepaw.mindsky.logic.ranking.PostScore
import io.github.turtlepaw.mindsky.utils.ApiUtils.fetchChunkedPosts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did

data class ForYouPost(
    val post: FeedViewPost,
    val suggestedFeed: SuggestedFeed,
    val feedInfo: GetFeedGeneratorResponse
)

class FeedViewModel(
    private val api: BlueskyApi,
    context: Context
) : ViewModel() {

    private val postCache = PostCache(context)
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    var followingFeed = mutableStateOf<List<FeedViewPost>?>(null)
        private set
    var isFetchingFeed = mutableStateOf(false)
        private set
    var isFetchingMoreFollowing = mutableStateOf(false)
        private set

    var isFetchingMoreForYou = mutableStateOf(false)
        private set
    var isFetchingForYou = mutableStateOf(false)
        private set

    var forYouFeed = mutableStateOf<List<ForYouPost>?>(null)
        private set

    var highlightsFeed = mutableStateOf<List<Pair<PostScore, PostView>>?>(null)
        private set

    var customFeed = mutableStateOf<List<FeedViewPost>?>(null)
        private set
    var isFetchingCustomFeed = mutableStateOf(false)
        private set
    var isFetchingMoreCustomFeed = mutableStateOf(false)
        private set

    var error = mutableStateOf<String?>(null)
        private set

    var userFeedCursors = mutableMapOf<AtUri, String?>()

    suspend fun fetchUserFeed(uri: AtUri): GetFeedResponse {
        val feed = api.getFeed(
            GetFeedQueryParams(
                feed = uri,
                limit = 50,
                cursor = userFeedCursors[uri]
            )
        ).requireResponse()

        userFeedCursors[uri] = feed.cursor
        return feed
    }

    fun fetchCustomFeed(uri: AtUri, isRefresh: Boolean = false) {
        if (isFetchingCustomFeed.value && !isRefresh) return
        if (isFetchingMoreCustomFeed.value && !isRefresh) return

        viewModelScope.launch {
            isFetchingCustomFeed.value = true
            if (isRefresh) {
                userFeedCursors[uri] = null
                customFeed.value = emptyList()
            }
            error.value = null

            try {
                val feedResponse = fetchUserFeed(uri)
                val newPosts = FeedTuner.cleanReplies(feedResponse.feed)

                // Cache all posts
                newPosts.forEach { addCachedPost(it.post) }

                if (isRefresh) {
                    customFeed.value = newPosts
                } else {
                    customFeed.value = (customFeed.value ?: emptyList()) + newPosts
                }
            } catch (e: Exception) {
                Log.e("FeedVM", "Error fetching custom feed", e)
                error.value = "Failed to fetch custom feed: ${e.message}"
            } finally {
                isFetchingCustomFeed.value = false
            }
        }
    }

    fun loadMoreCustomFeed(uri: AtUri) {
        if (isFetchingCustomFeed.value || isFetchingMoreCustomFeed.value) return
        if (userFeedCursors[uri] == null && customFeed.value?.isNotEmpty() == true) return

        viewModelScope.launch {
            isFetchingMoreCustomFeed.value = true
            error.value = null
            try {
                val feedResponse = fetchUserFeed(uri)
                val newPosts = FeedTuner.cleanReplies(feedResponse.feed)

                // Cache all posts
                newPosts.forEach { addCachedPost(it.post) }

                customFeed.value = (customFeed.value ?: emptyList()) + newPosts
            } catch (e: Exception) {
                Log.e("FeedVM", "Error loading more custom feed posts", e)
                error.value = "Failed to load more posts: ${e.message}"
            } finally {
                isFetchingMoreCustomFeed.value = false
            }
        }
    }

    // Keep in-memory cache for immediate access, but also persist to disk
    var cachedPosts = mutableMapOf<AtUri, PostView>()
        private set

    fun addCachedPost(post: PostView) {
        cachedPosts[post.uri] = post
        // Persist to disk cache asynchronously
        viewModelScope.launch {
            postCache.cachePost(post)
        }
    }

    suspend fun getCachedPost(uri: AtUri): PostView? {
        // First check memory cache
        cachedPosts[uri]?.let { return it }

        // Then check persistent cache
        val cachedPost = postCache.getCachedPost(uri)
        if (cachedPost != null) {
            // Add back to memory cache for faster access
            cachedPosts[uri] = cachedPost
            return cachedPost
        }

        return null
    }

    // Convenience method for non-suspend contexts
    fun getCachedPostSync(uri: AtUri): PostView? {
        return cachedPosts[uri]
    }

    fun removeCachedPost(uri: AtUri) {
        cachedPosts.remove(uri)
        viewModelScope.launch {
            postCache.removeCachedPost(uri)
        }
    }

    fun clearCache() {
        cachedPosts.clear()
        viewModelScope.launch {
            postCache.clearCache()
        }
    }

    fun getCacheSize(callback: (Long) -> Unit) {
        viewModelScope.launch {
            val size = postCache.getCacheSize()
            callback(size)
        }
    }

    var profilePosts = mutableStateOf<List<FeedViewPost>>(emptyList())
        private set

    private var profileCursor: String? = null
    var isFetchingProfile = mutableStateOf(false)
        private set

    fun fetchProfilePosts(
        actor: Did,
        limit: Int = 30,
        isRefresh: Boolean = false
    ) {
        if (isFetchingProfile.value) return

        viewModelScope.launch {
            if (isRefresh) isFetchingProfile.value = true
            error.value = null
            if (isRefresh) {
                profileCursor = null
                profilePosts.value = emptyList() // Optional: clear UI fast
            }

            try {
                val response = api.getAuthorFeed(
                    GetAuthorFeedQueryParams(
                        actor = actor,
                        limit = limit.toLong(),
                        cursor = profileCursor
                    )
                ).maybeResponse()

                val newPosts = FeedTuner.cleanReplies(response?.feed ?: emptyList())
                profileCursor = response?.cursor

                // Cache them
                newPosts.forEach { addCachedPost(it.post) }

                // Merge or set
                profilePosts.value = if (isRefresh) {
                    newPosts
                } else {
                    profilePosts.value + newPosts
                }

            } catch (e: Exception) {
                Log.e("FeedVM", "Error fetching profile posts", e)
                error.value = "Failed to load profile: ${e.message}"
            } finally {
                isFetchingProfile.value = false
            }
        }
    }

    fun loadMoreProfilePosts(limit: Int = 30) {
        val assumedActor =
            profilePosts.value.firstOrNull { it.reason !is FeedViewPostReasonUnion.ReasonRepost }?.post?.author?.did
        if (isFetchingProfile.value || profileCursor == null || assumedActor == null) return

        fetchProfilePosts(assumedActor, limit, isRefresh = false)
    }

    private var lastFetchTime = 0L
    private var followingFeedCursor: String? = null

    init {
        fetchFeed()
    }

    suspend fun getPost(uri: String): PostView {
        val followingMatch = followingFeed.value?.find { it.post.uri.atUri == uri }
        val forYouMatch = forYouFeed.value?.find { it.post.post.uri.atUri == uri }
        val highlightsMatch = highlightsFeed.value?.find { it.first.postUri == uri }

        return if (followingMatch != null) {
            followingMatch.post
        } else if (forYouMatch != null) {
            forYouMatch.post.post
        } else if (highlightsMatch != null) {
            highlightsMatch.second
        } else {
            // Check cache first
            val cachedPost = getCachedPost(AtUri(uri))
            if (cachedPost != null) {
                return cachedPost
            }

            // If not in cache, fetch from API
            val fetchedPost = api.getPosts(
                GetPostsQueryParams(
                    uris = listOf(AtUri(uri))
                )
            ).requireResponse().posts.first()

            // Cache the fetched post
            addCachedPost(fetchedPost)
            fetchedPost
        }
    }

    @Composable
    fun usePost(uri: String): LoadResult<PostView> {
        var post by remember { mutableStateOf<PostView?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(uri) {
            val cachedPost = getCachedPost(AtUri(uri))
            if (post == null) {
                post = cachedPost
                isLoading = false
            }
        }

        LaunchedEffect(post) {
            scope.launch {
                try {
                    val fetchedPost = api.getPosts(
                        GetPostsQueryParams(
                            uris = listOf(AtUri(uri))
                        )
                    ).requireResponse().posts.first()
                } catch (e: Exception) {
                    error = e.message
                }
                isLoading = false
            }
        }

        return LoadResult<PostView>(
            value = post,
            isLoading = isLoading,
            error = error
        )
    }

    fun fetchFeed(limit: Long = 100, isRefresh: Boolean = false) {
        if (isFetchingFeed.value && !isRefresh) {
            Log.d("FeedVM", "Already fetching initial feed.")
            return
        }
        if (isFetchingMoreFollowing.value && !isRefresh) {
            Log.d("FeedVM", "Already fetching more posts, refresh delayed or ignored.")
            return
        }

        val now = System.currentTimeMillis()
        if (!isRefresh && (now - lastFetchTime < 5000)) {
            Log.d("FeedVM", "Fetch cooldown active.")
            return
        }

        viewModelScope.launch {
            isFetchingFeed.value = true
            if (isRefresh) {
                followingFeedCursor = null
            }
            lastFetchTime = now
            error.value = null

            try {
                Log.d("FeedVM", "Fetching following feed. Refresh: $isRefresh")
                fetchFollowingPostsInternal(
                    limit = limit,
                    isLoadMore = false
                )

                Log.d("FeedVM", "Fetching For You feed.")
                fetchForYou()
                fetchHighlightsPostsInternal()
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
            return
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

            // Cache all posts
            newPosts.forEach { addCachedPost(it.post) }

            if (isLoadMore) {
                followingFeed.value = (followingFeed.value ?: emptyList()) + newPosts
            } else {
                followingFeed.value = newPosts
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
            return
        }

        viewModelScope.launch {
            isFetchingMoreFollowing.value = true
            error.value = null
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

    var feeds = emptyList<Pair<SuggestedFeed, GetFeedGeneratorResponse>>()
    var cursors = mutableMapOf<AtUri, String>()

    private suspend fun fetchForYou(limit: Long = 5) {
        val resolvedFeeds = feeds.ifEmpty {
            val box = ObjectBox.store.boxFor(SuggestedFeed::class.java)
            box.all.mapNotNull {
                val generator = api.getFeedGenerator(
                    GetFeedGeneratorQueryParams(
                        feed = AtUri(it.uri)
                    )
                ).maybeResponse()
                if (generator == null) return@mapNotNull null
                else it to generator
            }
        }

        feeds = resolvedFeeds
        if (resolvedFeeds.isEmpty()) {
            Log.d("FeedVM", "No posts found in ObjectBox for ForYou feed.")
            forYouFeed.value = emptyList()
            return
        }

        val posts = resolvedFeeds.flatMap { (suggestedFeed, generatorResponse) ->
            val feedResp = api.getFeed(
                GetFeedQueryParams(
                    feed = generatorResponse.view.uri,
                    limit = limit,
                    cursor = cursors[generatorResponse.view.uri]
                )
            ).maybeResponse()

            if (feedResp?.cursor != null) cursors.set(generatorResponse.view.uri, feedResp.cursor!!)

            val forYouPosts = feedResp?.feed?.map { feedViewPost ->
                addCachedPost(feedViewPost.post)
                ForYouPost(
                    post = feedViewPost,
                    suggestedFeed = suggestedFeed,
                    feedInfo = generatorResponse
                )
            } ?: emptyList()

            forYouPosts
        }.shuffled()

        forYouFeed.value = posts
    }

    fun loadMoreForYouFeeds() {
        if (isFetchingForYou.value || isFetchingMoreForYou.value) {
            Log.d("FeedVM", "Cannot load more: Another fetch operation is in progress.")
            return
        }
        if (cursors.isEmpty()) {
            Log.d("FeedVM", "No cursor available, cannot load more (all items loaded).")
            return
        }

        viewModelScope.launch {
            isFetchingMoreForYou.value = true
            error.value = null
            try {
                fetchForYou()
            } catch (e: Exception) {
                Log.e("FeedVM", "Error loading more for you posts (Exception)", e)
                error.value = "Failed to load more posts: ${e.message}"
            } catch (t: Throwable) {
                Log.e("FeedVM", "Error loading more for you posts (Throwable)", t)
                error.value = "An unexpected error occurred while loading more: ${t.message}"
            } finally {
                isFetchingMoreForYou.value = false
            }
        }
    }

    private suspend fun fetchHighlightsPostsInternal() {
        val box = ObjectBox.store.boxFor(PostScore::class.java)
        val posts = box.all
        if (posts.isEmpty()) {
            Log.d(
                "FeedVM",
                "No posts found in ObjectBox for ForYou feed, fetching from API not possible with this logic."
            )
            highlightsFeed.value = emptyList()
            return
        }

        val chunkedPosts = api.fetchChunkedPosts(
            posts.map { Pair(it, it.postUri) }
        )

        // Cache all fetched posts
        chunkedPosts.forEach { (_, post) ->
            addCachedPost(post)
        }

        highlightsFeed.value = chunkedPosts
    }

    override fun onCleared() {
        super.onCleared()
        // Optional: Clear memory cache when ViewModel is destroyed
        cachedPosts.clear()
    }
}