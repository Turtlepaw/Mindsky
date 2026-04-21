package io.github.turtlepaw.mindsky.routes

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import app.bsky.actor.PreferencesUnion
import app.bsky.feed.GeneratorView
import app.bsky.feed.GetFeedGeneratorsQueryParams
import app.bsky.graph.GetListQueryParams
import app.bsky.graph.GetListsQueryParams
import app.bsky.graph.ListView
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.DownloadModelDestination
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.generated.destinations.MyProfileDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.fetch_and_cache.SingleCache
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.components.TopBarBackground
import io.github.turtlepaw.mindsky.components.TopBarInteractiveElements
import io.github.turtlepaw.mindsky.components.post.LoadingPost
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.post.PostInsightsContext
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalProfileModel
import io.github.turtlepaw.mindsky.di.LocalScrollToTop
import io.github.turtlepaw.mindsky.logic.ModelDownloadWorker
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.LocalPreferences
import io.github.turtlepaw.mindsky.preferences.rememberPreference
import io.github.turtlepaw.mindsky.replaceCurrent
import io.github.turtlepaw.mindsky.viewmodels.FeedViewModel
import io.github.turtlepaw.mindsky.viewmodels.ProfileUiState
import io.github.turtlepaw.mindsky.workers.FeedWorker
import io.github.turtlepaw.mindsky.workers.SignalProcessingWorker
import io.github.turtlepaw.mindsky.workers.WorkerManager.enqueueImmediateWorkers
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.api.AtUri
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

class FeedViewModelFactory(private val api: BlueskyApi, private val context: Context) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FeedViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FeedViewModel(api, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${'$'}{modelClass.name}")
    }
}

sealed class FeedDestination(open val title: String) {

    object Following : FeedDestination("Following")
    object ForYou : FeedDestination("For You")

    data class UserFeed(
        val id: String,
        override val title: String
    ) : FeedDestination(title)
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedWorkerProgressDisplay(feedWorkerInfo: WorkInfo?) {
    if (feedWorkerInfo != null && (feedWorkerInfo.state == WorkInfo.State.RUNNING || feedWorkerInfo.state == WorkInfo.State.ENQUEUED)) {
        val progressData = feedWorkerInfo.progress
        val stageNameString = progressData.getString("stage") ?: FeedWorker.WorkStage.STARTING.name
        val currentProgressInt = progressData.getInt("progress", 0)
        // Ensure progressFraction is correctly calculated (progress is 0-100)
        val animatedProgress by animateFloatAsState(
            targetValue = currentProgressInt / 100f,
            label = "feedWorkerProgress"
        )

        val displayStageName = try {
            FeedWorker.WorkStage.valueOf(stageNameString).displayName
        } catch (_: IllegalArgumentException) {
            try {
                SignalProcessingWorker.Stage.valueOf(stageNameString).displayName
            } catch (_: IllegalArgumentException) {
                stageNameString // Fallback if stage name is somehow not in enum
            }
        }

        // Determine if progress is indeterminate
        // It's indeterminate if ENQUEUED, or if RUNNING but no "progress" key yet, or if progress is 0 for a non-STARTING stage
        val isIndeterminate = feedWorkerInfo.state == WorkInfo.State.ENQUEUED ||
                (feedWorkerInfo.state == WorkInfo.State.RUNNING && !progressData.keyValueMap.containsKey(
                    "progress"
                )) ||
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
                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Serializable
sealed class FeedItem {
    @Serializable
    data class FeedGenerator(val generator: GeneratorView) : FeedItem()
    @Serializable
    data class List(val list: ListView) : FeedItem()

    companion object {
        fun getName(feedItem: FeedItem): String {
            return when (feedItem) {
                is FeedGenerator -> feedItem.generator.displayName
                is List -> feedItem.list.name
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>(start = true)
@Composable
fun Feed(nav: DestinationsNavigator) {
    val context = LocalContext.current
    val viewModel = LocalFeedModel.current
    val api = LocalMindskyApi.current
    val listState = rememberLazyListState()
    val scrollToTopHandler = LocalScrollToTop.current
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(listState) {
        val handler: () -> Unit = {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
            }
            Unit
        }
        scrollToTopHandler.value = handler
        onDispose {
            if (scrollToTopHandler.value === handler) {
                scrollToTopHandler.value = null
            }
        }
    }

    var lastFetchTime by remember { mutableStateOf(0L) }

    val followingFeedData = viewModel.followingFeed.value
    val forYouFeedData = viewModel.forYouFeed.value
    val customFeedData = viewModel.customFeed.value

    val isFetchingFromViewModel =
        viewModel.isFetchingFeed.value || viewModel.isFetchingCustomFeed.value // Use ViewModel's state
    val error = viewModel.error.value

    val startDestination by rememberPreference(AppPrefs.DefaultFeed)
    var selectedDestination by rememberSaveable { mutableIntStateOf(startDestination) }

    val workManager = WorkManager.getInstance(context)
    val workInfos by workManager.getWorkInfosLiveData(
        WorkQuery.fromTags(
            FeedWorker::class.java.simpleName,
            SignalProcessingWorker::class.java.simpleName
        )
    ).observeAsState()
    val feedWorkerInfo = remember(workInfos) {
        workInfos?.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    }

    val userFeeds = SingleCache<FeedItem>(
        fetcher = {
            try {
                val prefs = api.getPreferences().requireResponse()
                val savedFeeds = prefs.preferences
                    .filterIsInstance<PreferencesUnion.SavedFeedsPrefV2>()
                    .first()
                    .value.items
                    .filter { it.pinned }

                val fetchable = savedFeeds.filter { it.value != "following" }

                val feedGenerators =
                    fetchable.filter { it.value.contains("app.bsky.feed.generator") }
                        .map { AtUri(it.value) }
                val fetchedFeedGenerators = api.getFeedGenerators(
                    GetFeedGeneratorsQueryParams(
                        feeds = feedGenerators
                    )
                ).requireResponse().feeds.associateBy { it.uri.atUri }

                val lists = fetchable.filter { it.value.contains("app.bsky.graph.list") }
                    .map { AtUri(it.value) }
                val fetchedLists = lists.map {
                    api.getList(
                        GetListQueryParams(
                            list = it,
                            limit = 1
                        )
                    ).requireResponse().list
                }

                val ordered = LinkedHashMap<String, FeedItem>()
                for (item in savedFeeds) {
                    val feed = when {
                        item.value.contains("app.bsky.feed.generator") -> {
                            val item = fetchedFeedGenerators[item.value] ?: continue
                            FeedItem.FeedGenerator(item)
                        }

                        item.value.contains("app.bsky.graph.list") -> {
                            val item =
                                fetchedLists.firstOrNull { it.uri.atUri == item.value } ?: continue
                            FeedItem.List(item)
                        }

                        else -> null
                    }

                    if (feed != null) {
                        ordered[item.value] = feed
                    }
                }

                ordered
            } catch (e: Exception) {
                Log.e("Feed", "Error fetching user feeds", e)
                throw e
            }
        },
        identifier = "user_feeds",
        serializer = FeedItem.serializer()
    ).load()

    val feeds = listOf(
        FeedDestination.Following,
        FeedDestination.ForYou
    ) + (userFeeds.value?.map {
        FeedDestination.UserFeed(
            it.key,
            FeedItem.getName(it.value)
        )
    } ?: emptyList())

    LaunchedEffect(Unit) {
        val files = listOf(
            File(context.filesDir, ModelDownloadWorker.MODEL_FILENAME),
            File(context.filesDir, ModelDownloadWorker.TOKENIZER_FILENAME)
        )
        if (!files.all { it.exists() && it.length() > 0 }) {
            nav.replaceCurrent(DownloadModelDestination)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { /* do something */ }) {
                Icon(Icons.Filled.PostAdd, stringResource(R.string.post))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f), // Base layer for scrolling content
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (error != null) Arrangement.Center else Arrangement.Top
            ) {
                if (error != null) {
                    item {
                        Icon(
                            Icons.Rounded.Error,
                            contentDescription = "Error",
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .size(40.dp)
                        )
                    }
                    item {
                        Text(
                            text = "Failed to fetch your feed",
                            //color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    MaterialTheme.shapes.medium
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(0.2f),
                                    MaterialTheme.shapes.medium
                                )
                                .padding(16.dp)
                        ) {
                            Text(
                                text = error,
                            )
                        }
                    }
                } else {
                    item {
                        Spacer(modifier = Modifier.height(50.dp))
                    }
                    item {
                        val slideDistancePx = with(LocalDensity.current) { 36.dp.toPx().toInt() }

                        AnimatedVisibility(
                            visible = feedWorkerInfo != null &&
                                    feedWorkerInfo.state == WorkInfo.State.RUNNING,
                            enter = fadeIn() + slideInVertically(
                                initialOffsetY = { -slideDistancePx }  // Starts ABOVE and slides down
                            ),
                            exit = fadeOut() + slideOutVertically(
                                targetOffsetY = { slideDistancePx }  // Exits sliding DOWN
                            )
                        ) {
                            FeedWorkerProgressDisplay(feedWorkerInfo = feedWorkerInfo)
                        }
                    }
                    item {
                        PrimaryScrollableTabRow(
                            selectedTabIndex = selectedDestination,
                            edgePadding = 0.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (userFeeds.error == null) {
                                feeds.forEachIndexed { index, destination ->
                                    Tab(
                                        selected = selectedDestination == index,
                                        onClick = {
                                            if (selectedDestination != index) {
                                                selectedDestination = index

                                                if (index < feeds.size) {
                                                    when (val dest = feeds[index]) {
                                                        is FeedDestination.Following, FeedDestination.ForYou -> viewModel.fetchFeed()
                                                        is FeedDestination.UserFeed -> viewModel.fetchCustomFeed(
                                                            AtUri(dest.id),
                                                            isRefresh = true
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (destination is FeedDestination.ForYou) {
                                                    Icon(
                                                        Icons.Rounded.AutoAwesome,
                                                        contentDescription = "Sparkles",
                                                        tint = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                Text(
                                                    destination.title,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }
                            } else {
                                Text(userFeeds.error!!.message ?: "Unknown error")
                            }
                        }
                    }
                    if (selectedDestination == 0) {
                        if (followingFeedData != null) {
                            items(followingFeedData) {
                                PostComponent(it, nav) {
                                    nav.navigate(FullsizePostDestination(it.post.uri.atUri))
                                }
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
                    } else if (selectedDestination == 1) {
                        if (!forYouFeedData.isNullOrEmpty()) {
                            items(forYouFeedData) {
                                PostComponent(it.post.post, nav, discoveryContext = {
                                    PostInsightsContext(
                                        it.feedInfo
                                    )
                                }) {
                                    nav.navigate(FullsizePostDestination(it.post.post.uri.atUri))
                                }
                            }
                            item {
                                LaunchedEffect(Unit) {
                                    viewModel.loadMoreForYouFeeds()
                                }

                                LoadingIndicator(
                                    modifier = Modifier
                                        .padding(16.dp),
                                )
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
                                            .enqueueImmediateWorkers()
                                    }
                                ) {
                                    Text("Generate For You Feed")
                                }
                            }
                        } else {
                            Loading()
                        }
                    } else {
                        // Custom Feed
                        if (selectedDestination < feeds.size) {
                            val currentFeed =
                                feeds[selectedDestination] as? FeedDestination.UserFeed
                            if (currentFeed != null) {
                                if (customFeedData != null) {
                                    items(customFeedData) {
                                        PostComponent(it, nav) {
                                            nav.navigate(FullsizePostDestination(it.post.uri.atUri))
                                        }
                                    }
                                    item {
                                        LaunchedEffect(currentFeed.id) {
                                            viewModel.loadMoreCustomFeed(AtUri(currentFeed.id))
                                        }

                                        LoadingIndicator(
                                            modifier = Modifier.padding(16.dp),
                                        )
                                    }
                                } else {
                                    // Trigger fetch if null (e.g. initial load if logic above failed or just safe guard)
                                    item {
                                        LaunchedEffect(currentFeed.id) {
                                            viewModel.fetchCustomFeed(
                                                AtUri(currentFeed.id),
                                                isRefresh = true
                                            )
                                        }
                                        LoadingIndicator(
                                            modifier = Modifier.padding(16.dp),
                                        )
                                    }
                                }
                            }
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
                            lastFetchTime = currentTime
                            if (selectedDestination < feeds.size) {
                                when (val dest = feeds[selectedDestination]) {
                                    is FeedDestination.Following, FeedDestination.ForYou -> viewModel.fetchFeed(
                                        isRefresh = true
                                    )

                                    is FeedDestination.UserFeed -> viewModel.fetchCustomFeed(
                                        AtUri(dest.id),
                                        isRefresh = true
                                    )
                                }
                            }
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
                                .clip(CircleShape)
                                .clickable {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    navigator.navigate(MyProfileDestination)
                                }
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
                                .clip(CircleShape)
                                .clickable {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    navigator.navigate(MyProfileDestination)
                                }
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
                                    navigator.navigate(MyProfileDestination)
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
        LoadingPost()
    }
}
