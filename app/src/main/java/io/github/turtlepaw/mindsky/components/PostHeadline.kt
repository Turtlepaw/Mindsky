package io.github.turtlepaw.mindsky.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bsky.actor.ProfileViewBasic
import io.github.turtlepaw.mindsky.components.post.PostDensity
import io.github.turtlepaw.mindsky.utils.toRelativeTimeString
import kotlinx.datetime.Instant

private fun String.nullable(): String? {
    return if (isNullOrBlank()) {
        null
    } else {
        this
    }
}

@Composable
fun PostHeadline(timestamp: Instant, author: ProfileViewBasic, density: PostDensity) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f)) {
            if (density == PostDensity.Expanded) {
                Column {
                    AuthorNameWithVerification(author)
                    Text(
                        text = "@${author.handle.handle}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Row {
                    AuthorNameWithVerification(author)
                    if (author.verification?.verifications?.isNotEmpty() != true) {
                        Spacer(modifier = Modifier.size(5.dp))
                    }
                    Text(
                        text = "@${author.handle.handle}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(5.dp))

        Text(
            text = timestamp.toRelativeTimeString(),
            style = MaterialTheme.typography.titleSmall.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        )
    }
}

@Composable
private fun AuthorNameWithVerification(author: ProfileViewBasic) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = author.displayName?.nullable() ?: author.handle.handle,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (author.verification?.verifications?.isNotEmpty() == true) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Verified",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(23.dp)
                    .padding(horizontal = 2.dp)
            )
        }
    }
}
