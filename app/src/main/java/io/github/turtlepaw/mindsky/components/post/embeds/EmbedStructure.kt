package io.github.turtlepaw.mindsky.components.post.embeds

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EmbedStructure(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit),
) {
    OutlinedCard(
        modifier = modifier.padding(vertical = 8.dp),
        border = CardDefaults.outlinedCardBorder().copy(width = 0.5.dp),
        onClick = onClick,
        enabled = enabled,
        content = content
    )
}