package io.github.turtlepaw.mindsky.routes

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.DownloadModelDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.components.TopBarBackground
import io.github.turtlepaw.mindsky.components.TopBarInteractiveElements
import io.github.turtlepaw.mindsky.components.post.InsightType
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.post.PostInsightsContext
import io.github.turtlepaw.mindsky.components.post.PostStructure
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalProfileModel
import io.github.turtlepaw.mindsky.logic.FeedWorker
import io.github.turtlepaw.mindsky.logic.FeedWorker.Companion.enqueueImmediateFeedWorker
import io.github.turtlepaw.mindsky.logic.ModelDownloadWorker
import io.github.turtlepaw.mindsky.replaceCurrent
import io.github.turtlepaw.mindsky.viewmodels.FeedViewModel
import io.github.turtlepaw.mindsky.viewmodels.ProfileUiState
import sh.christian.ozone.BlueskyApi
import java.io.File

class FeedViewModelFactory(private val api: BlueskyApi) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FeedViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FeedViewModel(api) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${'$'}{modelClass.name}")
    }
}

enum class FeedDestination(val title: String? = null) {
    Following, ForYou("For You")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedWorkerProgressDisplay(feedWorkerInfo: WorkInfo?) {
    if (feedWorkerInfo != null && (feedWorkerInfo.state == WorkInfo.State.RUNNING || feedWorkerInfo.state == WorkInfo.State.ENQUEUED)) {
        val progressData = feedWorkerInfo.progress
        val stageNameString = progressData.getString("stage") ?: FeedWorker.WorkStage.STARTING.name
        val currentProgressInt = progressData.getInt("progress", 0)
        // Ensure progressFraction is correctly calculated (progress is 0-100)
        val progressFraction = currentProgressInt / 100f

        val displayStageName = try {
            FeedWorker.WorkStage.valueOf(stageNameString).displayName
        } catch (e: IllegalArgumentException) {
            stageNameString // Fallback if stage name is somehow not in enum
        }

        // Determine if progress is indeterminate
        // It's indeterminate if ENQUEUED, or if RUNNING but no "progress" key yet, or if progress is 0 for a non-STARTING stage
        val isIndeterminate = feedWorkerInfo.state == WorkInfo.State.ENQUEUED ||
                (feedWorkerInfo.state == WorkInfo.State.RUNNING && !progressData.keyValueMap.containsKey("progress")) ||
                (feedWorkerInfo.state == WorkInfo.State.RUNNING && currentProgressInt == 0 && stageNameString != FeedWorker.WorkStage.STARTING.name && stageNameString != FeedWorker.WorkStage.COMPLETE.name)


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp) // Padding around the column
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.shapes.small
                )
                .padding(horizontal = 14.dp, vertical = 12.dp) // Padding inside the background
        ) {
            Text(
                text = if (feedWorkerInfo.state == WorkInfo.State.ENQUEUED) "Sync starting..." else "Sync: $displayStageName ${if (!isIndeterminate) "($currentProgressInt%)" else ""}",
                style = MaterialTheme.typography.bodySmall, // Slightly smaller text
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (isIndeterminate) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearWavyProgressIndicator(progress = { progressFraction }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview
@Composable
fun FeedProgressDisplayPreview(){
    FeedWorkerProgressDisplay(
        feedWorkerInfo = WorkInfo(
            id = java.util.UUID.randomUUID(),
            state = WorkInfo.State.RUNNING,
            tags = setOf(FeedWorker.IMMEDIATE_WORK_NAME),
            progress = androidx.work.Data.Builder()
                .putString("stage", FeedWorker.WorkStage.PROCESSING_POSTS.name)
                .putInt("progress", 50)
                .build()
        )
    )
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>(start = true)
@Composable
fun Feed(nav: DestinationsNavigator) {
    val context = LocalContext.current
    val api = LocalMindskyApi.current
    val listState = rememberLazyListState()

    var lastFetchTime by remember { mutableStateOf(0L) }

    val viewModel: FeedViewModel = viewModel(factory = FeedViewModelFactory(api))

    val followingFeedData = viewModel.followingFeed.value
    val forYouFeedData = viewModel.forYouFeed.value

    val isFetchingFromViewModel = viewModel.isFetchingFeed.value // Use ViewModel's state
    val error = viewModel.error.value

    val startDestination = FeedDestination.Following
    var selectedDestination by rememberSaveable { mutableIntStateOf(startDestination.ordinal) }

    val workManager = WorkManager.getInstance(context)
    val workInfos by workManager.getWorkInfosForUniqueWorkLiveData(FeedWorker.IMMEDIATE_WORK_NAME).observeAsState()
    val feedWorkerInfo = remember(workInfos) {
        workInfos?.find { it.tags.contains(FeedWorker.IMMEDIATE_WORK_NAME) || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    }


    // Updated LaunchedEffect to react to selectedDestination changes
    LaunchedEffect(selectedDestination, viewModel) {
        Log.d(
            "Feed",
            "LaunchedEffect for selectedDestination: ${'$'}{FeedDestination.values()[selectedDestination]}. Requesting fetch."
        )
        // USER ACTION REQUIRED in ViewModel:
        // viewModel.fetchFeed() must be adapted to fetch data based on the current
        // FeedDestination.values()[selectedDestination].
        viewModel.fetchFeed()
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
            // Error and Loading states for the feed
            if (error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Error fetching feed: $error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                // Feed content (LazyColumn)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f), // Base layer for scrolling content
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        // Spacer for fixed TopBarInteractiveElements + ProgressIndicator (approx. 56dp + progress height)
                        // Adjust this spacer if the progress indicator height changes significantly
                        Spacer(modifier = Modifier.height(50.dp)) // Increased to accommodate progress display roughly
                    }
                    item {
                        AnimatedVisibility(
                            visible = feedWorkerInfo != null && (feedWorkerInfo.state == WorkInfo.State.RUNNING || feedWorkerInfo.state == WorkInfo.State.ENQUEUED),
                        ) {
                            FeedWorkerProgressDisplay(feedWorkerInfo = feedWorkerInfo)
                        }
                    }
                    item { // PrimaryTabRow is now an item in LazyColumn
                        val tabTitles =
                            remember { FeedDestination.entries.map { it.title ?: it.name } }
                        PrimaryTabRow(
                            selectedTabIndex = selectedDestination,
                        ) {
                            Tab(
                                selected = selectedDestination == 0,
                                onClick = {
                                    if (selectedDestination != 0) {
                                        selectedDestination = 0
                                        viewModel.fetchFeed()
                                    }
                                },
                                text = { Text(FeedDestination.Following.name) }
                            )
                            Tab(
                                selected = selectedDestination == 1,
                                onClick = {
                                    if (selectedDestination != 1) {
                                        selectedDestination = 1
                                        //viewModel.fetchForYou()
                                    }
                                },
                                text = {
                                    Text(
                                        FeedDestination.ForYou.title ?: FeedDestination.ForYou.name
                                    )
                                }
                            )
                        }
                    }
                    if (selectedDestination == 0) {
                        if (followingFeedData != null) {
                            items(followingFeedData) {
                                PostComponent(it, nav)
                            }
                            item {
                                LaunchedEffect(Unit) {
                                    viewModel.loadMoreFollowingFeed()
                                }

                                LoadingIndicator(
                                    modifier = Modifier
                                        .padding(16.dp),
                                )
                            }
                        } else {
                            Loading()
                        }
                    } else {
                        if (forYouFeedData != null && forYouFeedData.isNotEmpty()) {
                            items(forYouFeedData) {
                                PostComponent(it.second, nav, discoveryContext = { modifier ->
                                    PostInsightsContext(
                                        it.first.score ?: 0f,
                                        InsightType.Score,
                                        modifier
                                    )
                                })
                            }
                        } else if (forYouFeedData != null && forYouFeedData.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No posts available",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                            item {
                                Button(
                                    onClick = {
                                        WorkManager.getInstance(context)
                                            .enqueueImmediateFeedWorker()
                                    }
                                ) {
                                    Text("Generate For You Feed")
                                }
                            }
                        } else {
                            Loading()
                        }
                    }
                }
            }

            TopBarBackground(
                modifier = Modifier.zIndex(2f)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(3f) // Above TopBarBackground
            ) {
                TopBarInteractiveElements(
                    listState = listState,
                    onIconClick = {
                        val currentTime = System.currentTimeMillis()
                        if (isFetchingFromViewModel) {
                            Log.d("Feed", "Already fetching feed, click ignored.")
                        } else if (currentTime - lastFetchTime < 5000) {
                            Log.d("Feed", "Fetched too recently, click ignored. Cooldown active.")
                        } else {
                            Log.d(
                                "Feed",
                                "Requesting feed refresh for: ${'$'}{FeedDestination.values()[selectedDestination]}"
                            )
                            lastFetchTime = currentTime
                            viewModel.fetchFeed()
                        }
                    },
                    modifier = Modifier
                )
            }

            // TopBarButtons (settings icon) uses internal zIndex(5f)
            TopBarButtons(listState, nav)
        }
    }
}

@Composable
fun TopBarButtons(listState: LazyListState, navigator: DestinationsNavigator) {
    val scrollOffset = remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }
    val firstVisibleItemIndex = remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val shouldShow = firstVisibleItemIndex.value == 0 && scrollOffset.value < 150
    val hapticFeedback = LocalHapticFeedback.current

    val offsetY by animateFloatAsState(
        targetValue = if (shouldShow) 0f else -80f, // moves it up
        label = "iconRowOffset"
    )

    val alpha by animateFloatAsState(
        targetValue = if (shouldShow) 1f else 0f,
        label = "iconRowAlpha"
    )

    val profileModel = LocalProfileModel.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .offset { IntOffset(x = 0, y = offsetY.toInt()) }
            .graphicsLayer { this.alpha = alpha }
            .zIndex(5f) // above top bar background
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
            ) {
                val profileUiState by profileModel.uiState.collectAsState()
                when (profileUiState) {
                    ProfileUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                    CircleShape
                                )
                        )
                    }

                    is ProfileUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                    CircleShape
                                )
                        )
                    }

                    is ProfileUiState.Success -> {
                        val avatarUrl =
                            (profileUiState as ProfileUiState.Success).profile.avatar?.uri
                        Avatar(
                            avatarUrl = avatarUrl,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    navigator.navigate(ProfileDestination)
                                },
                            contentDescription = "Avatar"
                        )
                    }
                }
            }
            IconButton(onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                navigator.navigate(SettingsDestination)
            }) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(25.dp)
                )
            }
        }
    }
}

fun LazyListScope.Loading() {
    items(8, key = { it }) {
        val infiniteTransition = rememberInfiniteTransition()

        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        PostStructure(
            avatar = {
                Box(
                    modifier = it
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                            CircleShape
                        )
                )
            },
            headline = {
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .fillMaxWidth(0.5f)
                        .height(10.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                            MaterialTheme.shapes.small
                        )
                )
            },
            metadata = {},
            actions = {}
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth(if (it == 2) 0.8f else 0.9f)
                        .height(9.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                            MaterialTheme.shapes.small
                        )
                )
            }
        }
    }
}
