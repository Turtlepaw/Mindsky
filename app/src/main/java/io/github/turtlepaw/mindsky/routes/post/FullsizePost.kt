package io.github.turtlepaw.mindsky.routes.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import app.bsky.feed.ThreadViewPostReplieUnion
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.components.post.LoadingPost
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.post.PostDensity
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.routes.settings.TopAppBarCommon
import kotlin.math.min

@Destination<RootGraph>
@Composable
fun FullsizePost(navigator: DestinationsNavigator, postUri: String) {
    val feedModel = LocalFeedModel.current
    val replies = rememberThreadReplies(postUri, depth = FullThreadDepth)
    val post = feedModel.usePost(postUri)
    val replyList = remember(replies.value) { replies.value.values.toList() }
    val listState = rememberLazyListState()
    var visibleCount by remember(replyList.size) {
        mutableIntStateOf(min(replyList.size, ReplyPageSize))
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
                    // Optionally show a loading state or error message
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

