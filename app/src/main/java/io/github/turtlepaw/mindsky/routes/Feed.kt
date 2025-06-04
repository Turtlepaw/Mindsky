package io.github.turtlepaw.mindsky.routes

import android.util.Log
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetTimelineQueryParams
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.DownloadModelDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.components.PostView
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.ml.ModelDownloadWorker
import io.github.turtlepaw.mindsky.replaceCurrent
import java.io.File

@Destination<RootGraph>(start = true)
@Composable
fun Feed(nav: DestinationsNavigator) {
    val context = LocalContext.current
    val api = LocalMindskyApi.current

    var feed by remember { mutableStateOf<List<FeedViewPost>?>(null) }

    LaunchedEffect(Unit) {
        val files = listOf(
            File(context.filesDir, ModelDownloadWorker.MODEL_FILENAME),
            File(context.filesDir, ModelDownloadWorker.TOKENIZER_FILENAME)
        )
        if (!files.all { it.exists() && it.length() > 0 }) {
            nav.replaceCurrent(DownloadModelDestination)
        }
    }

    LaunchedEffect(
        Unit
    ) {
        feed = api.getTimeline(
            params = GetTimelineQueryParams(
                limit = 100
            )
        ).maybeResponse()?.feed
    }

    if (feed == null) {
        // Show loading state or error
        Log.d("Feed", "Loading feed...")
    } else {
        LazyColumn {
            items(feed!!){
                PostView(
                    it
                )
            }
        }
    }
}