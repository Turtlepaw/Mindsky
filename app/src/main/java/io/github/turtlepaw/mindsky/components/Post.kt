package io.github.turtlepaw.mindsky.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun PostView(feedViewPost: FeedViewPost) {
    val postView = feedViewPost.post
    val author = feedViewPost.post.author
    val postRecord = postView.record.decodeAs<Post>()
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp), // Changed from fillMaxSize
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                horizontalArrangement = spacedBy(8.dp),
            ) {
                if(feedViewPost.reason is FeedViewPostReasonUnion.ReasonRepost){
                    val repostReason = feedViewPost.reason as FeedViewPostReasonUnion.ReasonRepost

                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Reposted by ${repostReason.value.by.displayName ?: repostReason.value.by.handle.handle}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(horizontalArrangement = spacedBy(8.dp)) {
                Avatar(
                    modifier = Modifier.size(24.dp).align(Alignment.CenterVertically),
                    avatarUrl = author.avatar?.uri,
                    contentDescription = author.displayName ?: author.handle.handle,
                )

                PostHeadline(postRecord.createdAt, author)
            }

            Text(
                text = postRecord.text,
                style = MaterialTheme.typography.bodyLarge
            )

            when(feedViewPost.post.embed){
                is PostViewEmbedUnion.ImagesView -> {
                    val imagesEmbed = (feedViewPost.post.embed as PostViewEmbedUnion.ImagesView).value
                    val images = imagesEmbed.images // Store for easier access
                    if (images.isNotEmpty()) {
                        val columnCount = if (images.size == 1) 1 else 2
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columnCount),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp), // Existing height constraint
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
                                    contentDescription = image.alt ?: "Post image", // Use alt text
                                    contentScale = ContentScale.Crop,
                                    modifier = imageModifier
                                        .background(MaterialTheme.colorScheme.error) // Debug background
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
