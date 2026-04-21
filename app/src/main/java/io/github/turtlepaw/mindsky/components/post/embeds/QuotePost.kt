package io.github.turtlepaw.mindsky.components.post.embeds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.bsky.embed.RecordViewRecord
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.PostView
import com.atproto.repo.GetRecordQueryParams
import com.atproto.repo.StrongRef
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.components.ErrorMessage
import io.github.turtlepaw.mindsky.components.post.LoadingPost
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.post.PostDensity
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import kotlinx.coroutines.launch
import sh.christian.ozone.api.AtIdentifier
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey

@Composable
fun QuotePost(record: RecordViewRecordUnion.ViewRecord, navigator: DestinationsNavigator) {
    val api = LocalMindskyApi.current
    val viewModel = LocalFeedModel.current
    var isLoading by remember { mutableStateOf(true) }
    var postRecord by remember { mutableStateOf<PostView?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(record) {
        coroutineScope.launch {
            isLoading = true
            try {
                val recordData = record.value.toStrongRef()
                val cached = viewModel.getCachedPost(record.value.uri)
                if (cached != null) {
                    postRecord = cached
                } else {
                    val data = api.getPosts(
                        GetPostsQueryParams(
                            listOf(
                                record.value.uri
                            )
                        )
                    ).maybeResponse()
                    if (data != null) {
                        postRecord = data.posts.first()
                        if(data.posts.firstOrNull() != null) viewModel.addCachedPost(data.posts.first())
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }

    EmbedStructure(onClick = {}) {
        if (isLoading) {
            LoadingPost()
        } else if (postRecord != null) {
            PostComponent(
                postRecord!!,
                navigator,
                showActions = false,
                density = PostDensity.Compact,
                showLabels = false
            ) {
                navigator.navigate(FullsizePostDestination(postRecord!!.uri.atUri))
            }
        } else {
            ErrorMessage()
        }
    }
}

fun RecordViewRecord.toStrongRef(): StrongRef {
    return StrongRef(
        uri = this.uri,
        cid = this.cid,
    )
}