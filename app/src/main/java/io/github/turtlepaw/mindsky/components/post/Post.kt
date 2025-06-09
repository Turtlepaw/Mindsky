package io.github.turtlepaw.mindsky.components.post

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.Like
import app.bsky.feed.Post
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import coil3.compose.AsyncImage
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.DeleteRecordRequest
import com.atproto.repo.StrongRef
import com.ramcosta.composedestinations.generated.destinations.ImageDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.components.PostHeadline
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import sh.christian.ozone.api.AtIdentifier
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent

@Composable
fun PostView(
    postView: FeedViewPost,
    navigator: DestinationsNavigator,
) {
    PostView(postView.post, navigator, postView.reason)
}

@Composable
fun PostView(
    postView: PostView,
    navigator: DestinationsNavigator,
    reason: FeedViewPostReasonUnion? = null
) {
    val author = postView.author
    val postRecord = postView.record.decodeAs<Post>()
    val api = LocalMindskyApi.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    PostStructure(
        context = {
            if (reason is FeedViewPostReasonUnion.ReasonRepost) {
                Row(
                    horizontalArrangement = spacedBy(8.dp),
                    modifier = Modifier
                        .offset((25).dp)
                        .padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Reposted by ${reason.value.by.displayName ?: reason.value.by.handle.handle}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        ),
                    )
                }
            }
        },
        avatar = {
            Avatar(
                modifier = it,
                avatarUrl = author.avatar?.uri,
                contentDescription = author.displayName ?: author.handle.handle,
            )
        },
        headline = {
            PostHeadline(postRecord.createdAt, author)
        },
        actions = {
            FlowRow(it, horizontalArrangement = spacedBy(24.dp)) {
                var isLiked by remember {
                    mutableStateOf(postView.viewer?.like != null)
                }
                var likeUri by remember {
                    mutableStateOf(postView.viewer?.like)
                }
                val isReposted = postView.viewer?.repost != null
                PostAction(
                    label = postView.replyCount,
                    icon = Icons.Rounded.ChatBubbleOutline,
                    contentDescription = "Chat Bubble",
                ) { }
                PostAction(
                    label = postView.repostCount,
                    icon = Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    isHighlighted = isReposted,
                ) { }
                PostAction(
                    label = (postView.likeCount ?: 0) + if (isLiked) 1 else 0,
                    icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Heart",
                    isHighlighted = isLiked,
                ) {
                    Log.d("PostView", "PostView: $isLiked")
                    coroutineScope.launch {
                        val session = SessionManager(context).getSession()
                        val collection = Nsid("app.bsky.feed.like")
                        if (isLiked) {
                            api.deleteRecord(
                                DeleteRecordRequest(
                                    collection = collection,
                                    repo = Did(session!!.did),
                                    rkey = likeUri!!.getRkey(),
                                ),
                            ).requireResponse()
                            isLiked = false
                            likeUri = null
                        } else {
                            val data = api.createRecord(
                                CreateRecordRequest(
                                    record = Json.encodeAsJsonContent(
                                        Like(
                                            StrongRef(postView.uri, postView.cid),
                                            Clock.System.now()
                                        )
                                    ),
                                    repo = Did(session!!.did),
                                    collection = collection,
                                )
                            ).requireResponse()
                            likeUri = data.uri
                            isLiked = true
                        }
                    }
                }
            }
        }
    ) {
        if (postRecord.reply != null) {
            Row {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Reply,
                    contentDescription = "Reply",
                    modifier = Modifier
                        .size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                )
                Text(
                    text = "Reply",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = postRecord.text,
            style = MaterialTheme.typography.bodyLarge
        )

        when (postView.embed) {
            is PostViewEmbedUnion.ImagesView -> {
                val imagesEmbed =
                    (postView.embed as PostViewEmbedUnion.ImagesView).value
                val images = imagesEmbed.images // Store for easier access
                if (images.isNotEmpty()) {
                    val columnCount = if (images.size == 1) 1 else 2
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnCount),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 800.dp),
                        horizontalArrangement = spacedBy(8.dp),
                        verticalArrangement = spacedBy(8.dp)
                    ) {
                        items(images) { image ->
                            val imageModifier = if (images.size == 1) {
                                Modifier
                                    .fillMaxSize() // Fill the 200dp high cell for a single image
                                    .padding(4.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            } else {
                                Modifier
                                    .aspectRatio(1f) // Square for multiple images in 2 columns
                                    .clip(MaterialTheme.shapes.medium)
                            }
                            AsyncImage(
                                model = image.thumb.uri,
                                contentDescription = image.alt
                                    ?: "Post image", // Use alt text
                                contentScale = ContentScale.Crop,
                                modifier = imageModifier
                                    .background(MaterialTheme.colorScheme.error)
                                    .clickable {
                                        navigator.navigate(
                                            ImageDestination(
                                                imageUrl = image.fullsize.uri,
                                                alt = image.alt
                                            )
                                        )
                                    }
                            )
                        }
                    }
                }
            }

            else -> {}
        }
    }
}

fun AtUri.getRkey(): RKey {
    return RKey(this.atUri.substringAfterLast("/"))
}
