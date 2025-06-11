package io.github.turtlepaw.mindsky.components.post

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PostInsightsContext(
    score: Float,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        Icon(
            imageVector = Icons.Rounded.Insights,
            contentDescription = "Insights",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Text(
            text = "Post score was $score",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
