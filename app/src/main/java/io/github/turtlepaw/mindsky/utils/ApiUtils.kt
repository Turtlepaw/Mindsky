package io.github.turtlepaw.mindsky.utils

import android.util.Log
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.PostView
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.api.AtUri

object ApiUtils {
    suspend fun <T> BlueskyApi.fetchChunkedPosts(
        itemsWithUris: List<Pair<T, String>>,
    ): List<Pair<T, PostView>> {
        // Convert URI strings to AtUri objects
        val urisWithItems = itemsWithUris.map { Pair(it.first, AtUri(it.second)) }

        // Map for quick lookup: atUri -> original T
        val itemMap = urisWithItems.associate { it.second.atUri to it.first }

        val chunkSize = 25 // API allows max 25 URIs per request
        val allFetchedPostViews = mutableListOf<Pair<T, PostView>>()

        // Process in chunks
        urisWithItems.map { it.second }.chunked(chunkSize).forEach { chunkOfUris ->
            Log.d("ApiUtils", "Fetching chunk with ${chunkOfUris.size} URIs.")
            val response = getPosts(GetPostsQueryParams(uris = chunkOfUris))
            response.maybeResponse()?.posts?.forEach { postView ->
                val originalItem = itemMap[postView.uri.atUri]
                if (originalItem != null) {
                    allFetchedPostViews.add(Pair(originalItem, postView))
                } else {
                    Log.w("ApiUtils", "Original item not found for URI: ${postView.uri.atUri}")
                }
            }
        }

        return allFetchedPostViews
    }
}