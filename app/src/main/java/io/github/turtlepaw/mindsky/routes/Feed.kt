package io.github.turtlepaw.mindsky.routes

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.zIndex
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.DownloadModelDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.FeedViewModel
import io.github.turtlepaw.mindsky.components.PostView
import io.github.turtlepaw.mindsky.components.TopBarBackground
import io.github.turtlepaw.mindsky.components.TopBarInteractiveElements
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.logic.ModelDownloadWorker
import io.github.turtlepaw.mindsky.replaceCurrent
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>(start = true)
@Composable
fun Feed(nav: DestinationsNavigator) {
    val context = LocalContext.current
    val api = LocalMindskyApi.current
    val listState = rememberLazyListState()

    var lastFetchTime by remember { mutableStateOf(0L) }
    // isFetchingFeed state is now managed by the ViewModel
    // var isFetchingFeed by remember { mutableStateOf(false) }

    val viewModel = remember { FeedViewModel(api) }

    val feed = viewModel.feed.value
    val isFetchingFromViewModel = viewModel.isFetchingFeed.value // Use ViewModel's state

    LaunchedEffect(Unit) {
        viewModel.fetchFeed() // Initial fetch
    }

    LaunchedEffect(Unit) {
        val files = listOf(
            File(context.filesDir, ModelDownloadWorker.MODEL_FILENAME),
            File(context.filesDir, ModelDownloadWorker.TOKENIZER_FILENAME)
        )
        if (!files.all { it.exists() && it.length() > 0 }) {
            nav.replaceCurrent(DownloadModelDestination)
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Layer 1: TopBar Background
            TopBarBackground(
                modifier = Modifier.zIndex(1f)
            )

            // Layer 2: Scrolling Content (including settings)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .animateContentSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { /* TODO: Settings Left */ }
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = "Settings Left",
                            )
                        }
                        IconButton(
                            onClick = { /* TODO: Settings Right */ }
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = "Settings Right",
                            )
                        }
                    }
                }

                if (feed == null && !isFetchingFromViewModel) { // Show loading only if not fetching and feed is null
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(), // Changed from fillMaxSize()
                            contentAlignment = Alignment.Center
                        ) {
                            LinearWavyProgressIndicator()
                        }
                    }
                } else if (feed != null) {
                    items(feed) {
                        PostView(
                            feedViewPost = it,
                            // pdsHost = api.pdsUrl.toString() // Removed as per user request
                        )
                    }
                }
            }

            // Layer 3: TopBar Interactive Elements
            TopBarInteractiveElements(
                listState = listState,
                onIconClick = {
                    val currentTime = System.currentTimeMillis()
                    if (isFetchingFromViewModel) {
                        Log.d("Feed", "Already fetching feed, click ignored.")
                    } else if (currentTime - lastFetchTime < 5000) {
                        Log.d("Feed", "Fetched too recently, click ignored. Cooldown active.")
                    } else {
                        Log.d("Feed", "Requesting feed fetch.")
                        lastFetchTime = currentTime
                        // Fetch logic now directly calls ViewModel
                        try {
                            viewModel.fetchFeed()
                        } catch (e: Exception) {
                            Log.e("Feed", "Error starting feed fetch", e)
                        }
                    }
                },
                modifier = Modifier.zIndex(3f)
            )
        }
    }
}
