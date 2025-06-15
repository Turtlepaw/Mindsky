package io.github.turtlepaw.mindsky.routes

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bsky.feed.PostView
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.LikeVector
import io.github.turtlepaw.mindsky.ObjectBox
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.components.post.InsightType
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.post.PostInsightsContext
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalProfileModel
import io.github.turtlepaw.mindsky.utils.ApiUtils.fetchChunkedPosts
import io.github.turtlepaw.mindsky.viewmodels.ProfileUiState

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun Profile(navigator: DestinationsNavigator) {
    var likes by remember { mutableStateOf<List<Pair<LikeVector?, PostView>>?>(null) }
    val api = LocalMindskyApi.current

    LaunchedEffect(Unit) {
        val box = ObjectBox.store.boxFor(LikeVector::class.java)
        likes = api.fetchChunkedPosts(
            box.all.map { it to it.uri }
        )
    }

    val profileModel = LocalProfileModel.current
    val profileUiState by profileModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            item {
                when (profileUiState) {
                    is ProfileUiState.Loading -> {
                        LinearWavyProgressIndicator()
                    }

                    is ProfileUiState.Error -> {
                        Text("Error loading profile")
                    }

                    is ProfileUiState.Success -> {
                        val profile = (profileUiState as ProfileUiState.Success).profile
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                15.dp,
                                Alignment.CenterHorizontally
                            )
                        ) {
                            Avatar(
                                modifier = Modifier.size(50.dp),
                                profile.avatar?.uri,
                                "${profile.handle} avatar",
                            )

                            Text(
                                text = profile.displayName ?: profile.handle.handle,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.medium
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Lightbulb,
                                contentDescription = "Favorite",
                            )
                            Text("Likes", style = MaterialTheme.typography.titleMedium)
                        }

                        Text(
                            "We're using your likes to generate a personalized feed for you. " + "\n\n" +
                                    "These posts below are liked posts and the numbers represent the relevancy of the post, and do not represent the quality of the post.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (likes != null) {
                items(likes!!) {
                    PostComponent(
                        it.second,
                        navigator,
                        reason = null,
                        discoveryContext = { modifier ->
                            PostInsightsContext(
                                it.first?.vector?.first() ?: 0f,
                                InsightType.Vector,
                                modifier
                            )
                        }
                    )
                }
            } else {
                Loading()
            }
        }
    }
}