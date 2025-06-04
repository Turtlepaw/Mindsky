package io.github.turtlepaw.mindsky.components

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bsky.feed.FeedViewPost
import app.bsky.feed.Post

@Composable
fun PostView(feedViewPost: FeedViewPost) {
    val postView = feedViewPost.post
    val author = feedViewPost.post.author
    val postRecord = postView.record.decodeAs<Post>()
    Card {
        Row(horizontalArrangement = spacedBy(8.dp)) {
            Avatar(
                modifier = Modifier.size(16.dp).align(Alignment.CenterVertically),
                avatarUrl = author.avatar?.uri,
                contentDescription = author.displayName ?: author.handle.handle,
                fallbackColor = MaterialTheme.colorScheme.primary,
            )

            //PostHeadline(now, post.createdAt, author)
        }
        Text(
            text = postRecord.text,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
    }
}