package io.github.turtlepaw.mindsky.routes.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.bsky.feed.PostView
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.components.post.LoadingPost
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.post.PostDensity
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.routes.TopAppBarCommon
import kotlinx.coroutines.launch

@Destination<RootGraph>
@Composable
fun FullsizePost(navigator: DestinationsNavigator, postUri: String) {
    val coroutineScope = rememberCoroutineScope()
    val feedModel = LocalFeedModel.current
    var post by remember { mutableStateOf<PostView?>(null) }

    LaunchedEffect(postUri) {
        coroutineScope.launch {
            post = feedModel.getPost(postUri)
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
            verticalArrangement = Arrangement.Top
        ) {
            item {
                if (post != null) {
                    PostComponent(
                        post!!,
                        navigator,
                        enabled = false,
                        density = PostDensity.Expanded
                    ) {}
                } else {
                    // Optionally show a loading state or error message
                    LoadingPost()
                }
            }
        }
    }
}