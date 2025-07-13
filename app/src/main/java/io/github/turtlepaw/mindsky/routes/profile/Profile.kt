package io.github.turtlepaw.mindsky.routes.profile

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.bsky.actor.GetProfileQueryParams
import app.bsky.actor.ProfileViewDetailed
import app.bsky.feed.FeedViewPost
import coil3.compose.AsyncImage
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ImageDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.components.post.Checkmark
import io.github.turtlepaw.mindsky.components.post.LabelComponent
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.post.getAvatar
import io.github.turtlepaw.mindsky.components.post.getDisplayName
import io.github.turtlepaw.mindsky.components.post.rememberLoadingColor
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalLabelManager
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.routes.Loading
import io.github.turtlepaw.mindsky.utils.Formatters
import sh.christian.ozone.api.Did

private val startPadding = 10.dp

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun Profile(navigator: DestinationsNavigator, identity: String) {
    var profileData by remember { mutableStateOf<ProfileViewDetailed?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val api = LocalMindskyApi.current
    val posts = rememberPosts(Did(identity))

    LaunchedEffect(identity) {
        profileData = api.getProfile(
            GetProfileQueryParams(
                Did(identity)
            )
        ).maybeResponse()
        isLoading = false
    }

    Scaffold {
        LazyColumn {
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
                                        MaterialTheme.colorScheme.surface,
                                        CircleShape
                                    )
                                    .background(
                                        color,
                                        CircleShape
                                    )
                            )
                        },
                        navigator = navigator,
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
                            AsyncImage(
                                model = profileData!!.banner?.uri,
                                contentDescription = "${profileData!!.displayName}'s banner",
                                contentScale = ContentScale.Crop,
                                modifier = it
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
                        if (profileData!!.verification?.verifications?.isNotEmpty() == true) {
                            Checkmark(
                                Modifier.size(25.dp)
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
                        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)
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
                    if (followedBy != null) {
                        Row(
                            modifier = Modifier.padding(start = startPadding),
                            horizontalArrangement = Arrangement.spacedBy(46.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val trimmedFollowedBy = followedBy.followers.take(3)
                            AvatarStack(
                                items = trimmedFollowedBy,
                            ) {
                                Avatar(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .border(
                                            3.dp,
                                            MaterialTheme.colorScheme.surface,
                                            CircleShape
                                        ),
                                    avatarUrl = it.avatar?.uri,
                                    contentDescription = "${it.displayName ?: it.handle}'s avatar",
                                    clip = CircleShape,
                                )
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
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                item {
                    ProfileLabels(profileData!!)
                }

                ProfilePosts(posts, navigator)
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

@Composable
fun ProfileLabels(author: ProfileViewDetailed) {
    val labelManager = LocalLabelManager.current
    val labelers by labelManager.labelersDefinitionFlow.collectAsState()

    FlowRow(
        modifier = Modifier.padding(start = startPadding),
    ) {
        for (label in author.labels.filter { it.`val` != "!no-unauthenticated" }) {
            val resolvedLabel = labelers[label.src.did]

            if (resolvedLabel != null) {
                LabelComponent(
                    label = resolvedLabel.getDisplayName(label.`val`),
                    resolvedLabel.getAvatar(),
                    true
                )
            } else {
                // Fallback for unresolved labels
                LabelComponent(
                    label = label.`val`,
                    null,
                    true
                )
            }
        }
    }
}

@Composable
fun rememberPosts(account: Did): Pair<MutableState<List<FeedViewPost>>, MutableState<Boolean>> {
    val api = LocalFeedModel.current

    LaunchedEffect(Unit) {
        api.fetchProfilePosts(account, isRefresh = true)
    }

    return api.profilePosts to api.isFetchingProfile
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.ProfilePosts(
    posts: Pair<MutableState<List<FeedViewPost>>, MutableState<Boolean>>,
    navigator: DestinationsNavigator
) {
    if (posts.second.value) {
        Loading()
    } else {
        items(posts.first.value) {
            PostComponent(it, navigator) { }
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