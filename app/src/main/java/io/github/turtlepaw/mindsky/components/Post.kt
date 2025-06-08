package io.github.turtlepaw.mindsky.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.Post
import app.bsky.feed.PostEmbedUnion
import app.bsky.embed.External
import app.bsky.embed.Images
import app.bsky.embed.Record
import app.bsky.embed.RecordWithMedia
import app.bsky.feed.PostViewEmbedUnion
import coil3.compose.AsyncImage
import com.atproto.repo.GetRecordQueryParams
import com.ramcosta.composedestinations.generated.destinations.ImageDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.di.LocalMindskyApi

@Composable
fun PostView(feedViewPost: FeedViewPost, navigator: DestinationsNavigator) {
    val postView = feedViewPost.post
    val author = feedViewPost.post.author
    val postRecord = postView.record.decodeAs<Post>()
    val api = LocalMindskyApi.current
    Column(
        modifier = Modifier.fillMaxWidth(), // Changed from fillMaxSize
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            if (feedViewPost.reason is FeedViewPostReasonUnion.ReasonRepost) {
                Row(
                    horizontalArrangement = spacedBy(8.dp),
                    modifier = Modifier
                        .offset((25).dp)
                        .padding(bottom = 8.dp)
                ) {
                    val repostReason =
                        feedViewPost.reason as FeedViewPostReasonUnion.ReasonRepost

                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Reposted by ${repostReason.value.by.displayName ?: repostReason.value.by.handle.handle}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        ),
                    )
                }
            }

            Row(
                horizontalArrangement = spacedBy(12.dp)
            ) {
                Avatar(
                    modifier = Modifier
                        .size(42.dp)
                        .align(Alignment.Top)
                        .offset(y = 2.dp),
                    avatarUrl = author.avatar?.uri,
                    contentDescription = author.displayName ?: author.handle.handle,
                )

                Column(
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    PostHeadline(postRecord.createdAt, author)
                    // is reply
                    if (postRecord.reply != null) {
                        val reply = postRecord.reply
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

                    when (feedViewPost.post.embed) {
                        is PostViewEmbedUnion.ImagesView -> {
                            val imagesEmbed =
                                (feedViewPost.post.embed as PostViewEmbedUnion.ImagesView).value
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
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.25.dp
        )
    }
}
