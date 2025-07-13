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
                        viewModel.addCachedPost(postRecord!!)
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
                density = PostDensity.Compact
            ) {
                navigator.navigate(FullsizePostDestination(postRecord!!.uri.atUri))
            }
        } else {
            //TODO: show failed
        }
    }
}

fun StrongRef.toGetRecordQueryParams(): GetRecordQueryParams {
    val uriString = uri.toString().removePrefix("at://")

    val firstSlashIndex = uriString.indexOf('/')
    require(firstSlashIndex > 0) {
        "Invalid AtUri: expected authority + path, but got: $uriString"
    }

    val authority = uriString.substring(0, firstSlashIndex) // repo (handle or DID)
    val path = uriString.substring(firstSlashIndex + 1) // collection/rkey

    val pathSegments = path.split('/')
    require(pathSegments.size >= 2) {
        "Invalid AtUri: expected at least collection and rkey, but got: $path"
    }

    val repo = parseAtIdentifier(authority)
    val collection = Nsid(pathSegments[0])
    val rkey = RKey(pathSegments[1])

    return GetRecordQueryParams(
        repo = repo,
        collection = collection,
        rkey = rkey,
        cid = cid,
    )
}

fun RecordViewRecord.toStrongRef(): StrongRef {
    return StrongRef(
        uri = this.uri,
        cid = this.cid,
    )
}

fun parseAtIdentifier(value: String): AtIdentifier =
    if (value.startsWith("did:")) {
        Did(value) // assuming Did is a data class implementing AtIdentifier
    } else {
        Handle(value) // assuming Handle is a data class implementing AtIdentifier
    }
