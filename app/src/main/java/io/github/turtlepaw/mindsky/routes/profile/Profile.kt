package io.github.turtlepaw.mindsky.routes.profile

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.bsky.actor.GetProfileQueryParams
import app.bsky.actor.GetProfilesQueryParams
import app.bsky.actor.ProfileViewDetailed
import app.bsky.feed.FeedViewPost
import coil3.compose.AsyncImage
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.generated.destinations.ImageDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.fetch_and_cache.SingleCache
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.components.post.Checkmark
import io.github.turtlepaw.mindsky.components.post.LabelComponent
import io.github.turtlepaw.mindsky.components.post.Labelers
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.post.Robot
import io.github.turtlepaw.mindsky.components.post.getAvatar
import io.github.turtlepaw.mindsky.components.post.getDisplayName
import io.github.turtlepaw.mindsky.components.post.hiddenLabels
import io.github.turtlepaw.mindsky.components.post.rememberLoadingColor
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalLabelManager
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalNavController
import io.github.turtlepaw.mindsky.di.LocalProfileRepository
import io.github.turtlepaw.mindsky.di.LocalScrollToTop
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import io.github.turtlepaw.mindsky.routes.Loading
import io.github.turtlepaw.mindsky.utils.Formatters
import kotlinx.coroutines.launch
import sh.christian.ozone.api.Did
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val startPadding = 10.dp

private data class TabDestination(
    val label: String,
    val icon: Int? = null,
)

enum class BadgeInfoType {
    Bot,
    Verified
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BadgeInformationSheet(
    badge: BadgeInfoType?,
    profileData: ProfileViewDetailed,
    onDismiss: () -> Unit,
    navigator: DestinationsNavigator
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    if (badge != null) {
        ModalBottomSheet(
            onDismissRequest = {
                onDismiss()
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (badge == BadgeInfoType.Bot) {
                    Text("This account is labeled as automated.")
                } else if (badge == BadgeInfoType.Verified) {
                    val api = LocalMindskyApi.current

                    val accounts = SingleCache(
                        identifier = "verification_accounts_${profileData.did.did}",
                        serializer = ProfileViewDetailed.serializer(),
                        fetcher = {
                            val verifications = profileData.verification?.verifications
                            if (verifications.isNullOrEmpty()) {
                                emptyMap()
                            } else {
                                val verificationData = api.getProfiles(
                                    GetProfilesQueryParams(
                                        verifications.map { it.issuer }
                                    )
                                ).requireResponse()
                                verificationData.profiles.associateBy { it.did.did }
                            }
                        },
                    ).load()

                        Text("This account has been verified by trusted sources.")
                        if (accounts.isLoading) {
                            repeat(2){
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    ) {
                                        val color = rememberLoadingColor()
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    color,
                                                    CircleShape
                                                )
                                        )
                                        Box(
                                            modifier = Modifier
                                                .height(20.dp)
                                                .fillMaxWidth(0.5f)
                                                .background(
                                                    color,
                                                    RoundedCornerShape(8.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        } else if (accounts.error != null) {
                            Text("Failed to load verification data: ${accounts.error!!.message}")
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                accounts.value.values.forEach { account ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(
                                                MaterialTheme.shapes.medium
                                            )
                                            .clickable {
                                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                                    onDismiss()
                                                    navigator.navigate(
                                                        ProfileDestination(account.did.did)
                                                    )
                                                }
                                            }
                                            .padding(10.dp)
                                    ) {
                                        Avatar(
                                            avatarUrl = account.avatar?.uri,
                                            contentDescription = "${account.displayName}'s avatar",
                                            clip = CircleShape,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Column() {
                                            Text(account.displayName ?: account.handle.handle, style = MaterialTheme.typography.bodyMedium)
                                            val verification = profileData.verification?.verifications?.find { it.issuer == account.did }
                                            if(verification != null){
                                                val formatter = DateTimeFormatter
                                                    .ofPattern("MMMM d, yyyy")
                                                    .withZone(ZoneId.systemDefault())
                                                Text(
                                                    formatter.format(
                                                        Instant.ofEpochMilli(verification.createdAt.epochSeconds)
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }
                    Button(
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.elevatedButtonColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Okay")
                    }
                }
            }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun Profile(navigator: DestinationsNavigator, identity: String) {
    var profileData by remember { mutableStateOf<ProfileViewDetailed?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val repository = LocalProfileRepository.current
    val api = LocalMindskyApi.current
    val posts = rememberPosts(Did(identity))
    val hasBackStack = LocalNavController.current.previousBackStackEntry != null
    val listState = rememberLazyListState()
    val scrollToTopHandler = LocalScrollToTop.current
    val coroutineScope = rememberCoroutineScope()
    var badgeShown by remember { mutableStateOf<BadgeInfoType?>(null) }

    DisposableEffect(listState) {
        val handler: () -> Unit = {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
                posts.refresh()
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
    val userDid = LocalSessionManager.current.getSession()?.did

    LaunchedEffect(identity) {
        if (profileData == null || profileData?.did?.did != identity) {
            isLoading = true
            profileData = repository.getProfile(Did(identity))
            isLoading = false
        }
    }

    var selectedDestination by remember { mutableStateOf(0) }
    val destinations = listOf(
        TabDestination(
            label = stringResource(R.string.posts)
        ),
        TabDestination(
            label = "Tangled",
            icon = R.drawable.ic_tangled
        ),
    )

    if (!isLoading && profileData != null) {
        BadgeInformationSheet(
            badge = badgeShown,
            profileData = profileData!!,
            onDismiss = { badgeShown = null },
            navigator = navigator
        )
    }

    Scaffold {
        LazyColumn(state = listState) {
            if (isLoading) {
                item {
                    val color = rememberLoadingColor()
                    ProfileStructure(
                        banner = {
                            Box(
                                modifier = it.background(
                                    color
                                )
                            )
                        },
                        avatar = {
                            Box(
                                modifier = it
                                    .background(
                                        color,
                                        CircleShape
                                    )
                            )
                        },
                        navigator = navigator,
                        showBackButton = hasBackStack,
                        avatarOnClick = null
                    )
                }
            } else if (profileData == null) {
                item {
                    Text("Failed to load profile")
                }
            } else {
                item {
                    ProfileStructure(
                        banner = {
                            if (profileData!!.banner?.uri != null)
                                AsyncImage(
                                    model = profileData!!.banner?.uri,
                                    contentDescription = "${profileData!!.displayName}'s banner",
                                    contentScale = ContentScale.Crop,
                                    modifier = it
                                )
                            else AsyncImage(
                                model = profileData!!.avatar?.uri,
                                contentDescription = "${profileData!!.displayName}'s avatar",
                                contentScale = ContentScale.Crop,
                                modifier = it
                                    .blur(100.dp)
                                    .graphicsLayer {
                                        alpha = 0.3f
                                    }
                            )
                        },
                        avatar = {
                            Avatar(
                                avatarUrl = profileData!!.avatar?.uri,
                                contentDescription = profileData!!.displayName,
                                clip = CircleShape,
                                modifier = it
                            )
                        },
                        showBackButton = hasBackStack,
                        navigator = navigator,
                    ) {
                        val avatar = profileData!!.avatar?.uri
                        if (avatar != null) {
                            navigator.navigate(
                                ImageDestination(avatar, null)
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Row(
                        modifier = Modifier.padding(start = startPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            profileData!!.displayName ?: profileData!!.handle.handle,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        if (profileData!!.labels.find { it.`val` == "bot" } != null) {
                            Robot(Modifier
                                .size(25.dp)
                                .clickable {
                                    badgeShown = BadgeInfoType.Bot
                                })
                        }
                        if (profileData!!.verification?.verifications?.isNotEmpty() == true) {
                            Checkmark(
                                Modifier
                                    .size(25.dp)
                                    .clickable {
                                        badgeShown = BadgeInfoType.Verified
                                    }
                            )
                        }
                    }
                }
                item {
                    FlowRow(
                        modifier = Modifier.padding(start = startPadding)
                    ) {
                        if (profileData!!.viewer?.followedBy != null) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 5.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Follows you", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Text(
                            "@${profileData!!.handle.handle}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                item {
                    FlowRow(
                        modifier = Modifier.padding(start = startPadding),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(
                            5.dp,
                            Alignment.CenterVertically
                        )
                    ) {
                        val followingText = stringResource(R.string.following)
                        val followersText = stringResource(R.string.followers)
                        val boldText =
                            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        val accessoryText =
                            MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            buildAnnotatedString {
                                withStyle(boldText.toSpanStyle()) {
                                    append(Formatters.formatCompactNumber(profileData!!.followersCount!!.toInt()))
                                }
                                withStyle(style = accessoryText.toSpanStyle()) {
                                    append(" ")
                                    append(followersText.lowercase())
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            buildAnnotatedString {
                                withStyle(boldText.toSpanStyle()) {
                                    append(Formatters.formatCompactNumber(profileData!!.followsCount!!.toInt()))
                                }
                                withStyle(style = accessoryText.toSpanStyle()) {
                                    append(" ")
                                    append(followingText.lowercase())
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Text(
                        profileData!!.description ?: "",
                        modifier = Modifier.padding(start = startPadding),
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    val followedBy = profileData!!.viewer?.knownFollowers
                    if (followedBy != null && identity != userDid) {
                        Row(
                            modifier = Modifier.padding(start = startPadding),
                            horizontalArrangement = Arrangement.spacedBy(46.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val trimmedFollowedBy = followedBy.followers.take(3)
                            AvatarStack(
                                items = trimmedFollowedBy,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(3.dp)
                                ) {
                                    Avatar(
                                        modifier = Modifier.fillMaxSize(),
                                        avatarUrl = it.avatar?.uri,
                                        contentDescription = "${it.displayName ?: it.handle}'s avatar",
                                        clip = CircleShape,
                                    )
                                }
                            }
                            val names = followedBy.followers.take(2)
                                .joinToString(", ") { it.displayName ?: it.handle }
                            val text =
                                if (followedBy.followers.size > 2) "$names, and ${followedBy.followers.size} others" else names

                            Text(
                                "Followed by $text",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.9f)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                item {
                    Box(
                        modifier = Modifier.padding(start = startPadding)
                    ) {
                        Labelers(profileData!!.labels, true)
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(15.dp))
//                    HorizontalDivider(
//                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
//                        thickness = 0.5.dp
//                    )
                    PrimaryTabRow(selectedTabIndex = selectedDestination, modifier = Modifier) {
                        destinations.forEachIndexed { index, destination ->
                            Tab(
                                selected = selectedDestination == index,
                                onClick = {
                                    selectedDestination = index
                                },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (destination.icon != null) {
                                            Icon(
                                                painter = painterResource(destination.icon),
                                                contentDescription = destination.label,
                                                modifier = Modifier.size(18.dp),
                                                tint = LocalContentColor.current
                                            )
                                        }
                                        Text(
                                            text = destination.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                if (selectedDestination == 0) {
                    ProfilePosts(posts, navigator)
                } else {
                    tangled(identity, navigator)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                )
            )
    )
}

private data class RememberedPosts(
    val posts: MutableState<List<FeedViewPost>>,
    val isLoading: MutableState<Boolean>,
    val refresh: () -> Unit
)

@Composable
private fun rememberPosts(account: Did): RememberedPosts {
    val api = LocalFeedModel.current

    LaunchedEffect(account) {
        if (api.profilePosts.value.isEmpty() || api.profilePosts.value.firstOrNull()?.post?.author?.did != account) {
            api.fetchProfilePosts(account, isRefresh = true)
        }
    }

    return RememberedPosts(
        posts = api.profilePosts,
        isLoading = api.isFetchingProfile,
        refresh = {
            api.fetchProfilePosts(account, isRefresh = true)
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.ProfilePosts(
    posts: RememberedPosts,
    navigator: DestinationsNavigator
) {
    if (posts.isLoading.value) {
        Loading()
    } else {
        items(posts.posts.value) {
            PostComponent(it, navigator) {
                navigator.navigate(
                    FullsizePostDestination(postUri = it.post.uri.atUri)
                )
            }
        }
        item {
            val api = LocalFeedModel.current
            LaunchedEffect(Unit) {
                api.loadMoreProfilePosts()
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(
                    modifier = Modifier
                        .padding(16.dp),
                )
            }
        }
    }
}
