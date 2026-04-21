package io.github.turtlepaw.mindsky.components.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bsky.feed.GetFeedGeneratorResponse
import io.github.turtlepaw.mindsky.components.Avatar

@Composable
fun PostInsightsContext(
    feed: GetFeedGeneratorResponse,
    modifier: Modifier = Modifier,
) {
    Column {
        Row(
            modifier = modifier.padding(
                horizontal = 5.dp,
            ).padding(start = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Avatar(
                contentDescription = feed.view.displayName,
                avatarUrl = feed.view.avatar?.uri,
                clip = MaterialTheme.shapes.small,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Post from ${feed.view.displayName}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
