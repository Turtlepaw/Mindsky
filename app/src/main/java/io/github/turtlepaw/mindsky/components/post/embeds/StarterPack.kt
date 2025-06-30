package io.github.turtlepaw.mindsky.components.post.embeds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.bsky.graph.StarterPackViewBasic
import app.bsky.graph.Starterpack

@Composable
fun StarterPack(it: StarterPackViewBasic) {
    val pack = it.record.decodeAs<Starterpack>()
    EmbedStructure(
        onClick = { /* TODO: Handle click action */ },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                pack.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                "Starter pack by @${it.creator.handle}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}