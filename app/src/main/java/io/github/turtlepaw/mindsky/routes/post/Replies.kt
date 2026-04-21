package io.github.turtlepaw.mindsky.routes.post

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bsky.feed.GetPostThreadQueryParams
import app.bsky.feed.GetPostThreadResponseThreadUnion
import app.bsky.feed.PostView
import app.bsky.feed.ThreadViewPost
import app.bsky.feed.ThreadViewPostReplieUnion
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.generated.destinations.RepliesDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.fetch_and_cache.SingleCache
import io.github.turtlepaw.fetch_and_cache.SingleCacheLoadResult
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.components.post.LoadingPost
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.post.PostDensity
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.routes.settings.TopAppBarCommon
import kotlinx.serialization.PolymorphicSerializer
import sh.christian.ozone.api.AtUri
import kotlin.math.max
import kotlin.math.min

internal const val FullThreadDepth = 10
internal const val ReplyPageSize = 15
internal const val MaxNestedDepth = 2

@Composable
internal fun rememberThreadReplies(postUri: String, depth: Int): SingleCacheLoadResult<ThreadViewPost> {
    val api = LocalMindskyApi.current
    return SingleCache.rememberLoad(
        fetcher = {
            val result = api.getPostThread(
                GetPostThreadQueryParams(
                    uri = AtUri(postUri),
                    depth = depth.toLong()
                )
            ).requireResponse().thread

            val threadPost = result as? GetPostThreadResponseThreadUnion.ThreadViewPost
                ?: throw Exception("Post not found or blocked")

            threadPost.value.replies
                .filterIsInstance<ThreadViewPostReplieUnion.ThreadViewPost>()
                .associate { it.value.post.uri.atUri to it.value }
        },
        serializer = PolymorphicSerializer(ThreadViewPost::class),
        identifier = "${postUri}_replies_depth_$depth",
    )
}

@Destination<RootGraph>
@Composable
fun Replies(navigator: DestinationsNavigator, postUri: String, focusReplyUri: String? = null) {
    val feedModel = LocalFeedModel.current
    val replies = rememberThreadReplies(postUri, depth = FullThreadDepth)
    val post = feedModel.usePost(postUri)
    val replyList = remember(replies.value) { replies.value.values.toList() }
    val listState = rememberLazyListState()

    val resolvedFocusUri = remember(replyList, focusReplyUri) {
        focusReplyUri ?: findLastReplyUri(replyList)
    }
    val focusIndex = remember(replyList, resolvedFocusUri) {
        resolvedFocusUri?.let { findTopLevelIndex(replyList, it) } ?: -1
    }

    val baseCount = remember(replyList.size) {
        min(replyList.size, ReplyPageSize)
    }
    var visibleCount by remember(replyList.size, focusIndex) {
        mutableIntStateOf(
            if (focusIndex >= 0) max(baseCount, focusIndex + 1) else baseCount
        )
    }
    val visibleReplies = remember(replyList, visibleCount) { replyList.take(visibleCount) }
    val shouldLoadMore by remember(listState, visibleCount, replyList.size) {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= visibleCount && visibleCount < replyList.size
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            visibleCount = min(visibleCount + ReplyPageSize, replyList.size)
        }
    }

    LaunchedEffect(focusIndex, visibleCount, replyList.size) {
        if (focusIndex >= 0 && focusIndex < visibleCount) {
            listState.scrollToItem(focusIndex + 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(
                navigator,
                R.string.post, 
                MaterialTheme.typography.titleMedium
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.Top,
            state = listState
        ) {
            item {
                if (post.value != null) {
                    PostComponent(
                        post.value!!,
                        navigator,
                        enabled = false,
                        density = PostDensity.Expanded
                    ) {}
                } else if (post.isLoading) {
                    LoadingPost()
                } else {
                    Text("Failed to load post")
                }
            }

            if (replies.isLoading && replyList.isEmpty()) {
                item {
                    LoadingPost()
                }
            }

            items(visibleReplies, key = { it.post.uri.atUri }) { reply ->
                PostReplies(
                    reply.post,
                    reply.replies.filterIsInstance<ThreadViewPostReplieUnion.ThreadViewPost>(),
                    navigator,
                    rootPostUri = postUri,
                    depthLimit = MaxNestedDepth
                )
            }

            if (visibleCount < replyList.size) {
                item {
                    LoadingPost()
                }
            }
        }
    }
}

@Composable
internal fun MoreRepliesRow(
    replyCount: Int,
    onClick: () -> Unit,
    indentation: Dp = 50.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Spacer(modifier = Modifier.width(indentation))
        Text(
            text = "View all $replyCount replies",
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
internal fun PostReplies(
    post: PostView? = null,
    replies: List<ThreadViewPostReplieUnion.ThreadViewPost>,
    navigator: DestinationsNavigator,
    rootPostUri: String,
    depth: Int = 1,
    depthLimit: Int = MaxNestedDepth
) {
    Column {
        if (post != null) {
            PostComponent(
                postView = post,
                navigator = navigator,
                density = PostDensity.Regular,
                showReply = false,
                showSeparator = replies.isEmpty()
            ) {
                navigator.navigate(
                    FullsizePostDestination(post.uri.atUri)
                )
            }
        }

        replies.forEachIndexed { index, reply ->
            RenderReply(
                reply = reply,
                navigator = navigator,
                rootPostUri = rootPostUri,
                depth = depth,
                depthLimit = depthLimit,
                isLastReply = index == replies.size - 1
            )
        }
    }
}

@Composable
internal fun RenderReply(
    reply: ThreadViewPostReplieUnion.ThreadViewPost,
    navigator: DestinationsNavigator,
    rootPostUri: String,
    depth: Int,
    depthLimit: Int,
    isLastReply: Boolean = false
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Thread indicator drawn BEHIND everything
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            val strokeWidth = 2.dp.toPx()
            val color = Color.Gray.copy(alpha = 0.3f)

            val verticalX = 30.dp.toPx()  // X position of vertical line (align with parent avatar center)
            val elbowY = 40.dp.toPx()      // Y position where elbow bends (align with child avatar center)
            val horizontalEndX = 60.dp.toPx()  // Where horizontal line ends (at child avatar center)

            // Vertical line from top to elbow
            val verticalEndY = if (isLastReply) elbowY else size.height
            drawLine(
                color = color,
                start = Offset(verticalX, 0f),
                end = Offset(verticalX, verticalEndY),
                strokeWidth = strokeWidth
            )

            // Horizontal line (elbow) extending to the avatar
            drawLine(
                color = color,
                start = Offset(verticalX, elbowY),
                end = Offset(horizontalEndX, elbowY),
                strokeWidth = strokeWidth
            )
        }

        // The actual post content on top
        Row {
            Spacer(
                modifier = Modifier
                    .width(50.dp)
            )
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                PostComponent(
                    postView = reply.value.post,
                    navigator = navigator,
                    density = PostDensity.Regular,
                    showReply = false,
                    showSeparator = reply.value.replies.isEmpty()
                ) {
                    navigator.navigate(
                        FullsizePostDestination(reply.value.post.uri.atUri)
                    )
                }

                val childReplies = reply.value.replies
                    .filterIsInstance<ThreadViewPostReplieUnion.ThreadViewPost>()

                if (childReplies.isNotEmpty()) {
                    if (depth >= depthLimit) {
                        val focusUri =
                            findLastReplyUri(childReplies.map { it.value }) ?: reply.value.post.uri.atUri
                        MoreRepliesRow(childReplies.size, onClick = {
                            navigator.navigate(
                                RepliesDestination(
                                    postUri = rootPostUri,
                                    focusReplyUri = focusUri
                                )
                            )
                        })
                    } else {
                        PostReplies(
                            post = null,
                            replies = childReplies,
                            navigator = navigator,
                            rootPostUri = rootPostUri,
                            depth = depth + 1,
                            depthLimit = depthLimit
                        )
                    }
                }
            }
        }
    }
}

private fun findLastReplyUri(replies: List<ThreadViewPost>): String? {
    var lastUri: String? = null
    for (reply in replies) {
        val nested = reply.replies
            .filterIsInstance<ThreadViewPostReplieUnion.ThreadViewPost>()
            .map { it.value }
        val nestedUri = findLastReplyUri(nested)
        lastUri = nestedUri ?: reply.post.uri.atUri
    }
    return lastUri
}

private fun findTopLevelIndex(replies: List<ThreadViewPost>, targetUri: String): Int {
    replies.forEachIndexed { index, reply ->
        if (containsReply(reply, targetUri)) {
            return index
        }
    }
    return replies.lastIndex
}

private fun containsReply(reply: ThreadViewPost, targetUri: String): Boolean {
    if (reply.post.uri.atUri == targetUri) {
        return true
    }
    return reply.replies
        .filterIsInstance<ThreadViewPostReplieUnion.ThreadViewPost>()
        .any { containsReply(it.value, targetUri) }
}
