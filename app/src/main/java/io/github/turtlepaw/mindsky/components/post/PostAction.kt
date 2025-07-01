package io.github.turtlepaw.mindsky.components.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.turtlepaw.mindsky.utils.Formatters

object PostAction {
    @Composable
    fun Section(
        actions: @Composable (modifier: Modifier) -> Unit,
        discoveryContext: @Composable (modifier: Modifier) -> Unit
    ) {
        actions(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        discoveryContext(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .padding(horizontal = 4.dp)
        )
    }

    @Composable
    fun Button(
        label: Long?,
        icon: ImageVector,
        contentDescription: String,
        isHighlighted: Boolean = false,
        onClick: () -> Unit,
    ) {
        Button(
            label = label?.toFloat(),
            icon = icon,
            contentDescription = contentDescription,
            isHighlighted = isHighlighted,
            onClick = onClick
        )
    }

    @Composable
    fun Button(
        label: Float?,
        icon: ImageVector,
        contentDescription: String,
        isHighlighted: Boolean = false,
        onClick: () -> Unit,
    ) {
        val density = LocalPostDensity.current
        Box(
            modifier = Modifier
                .sizeIn()
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (label != null && label > 0) {
                    Text(
                        text = Formatters.formatNumberForLocale(label.toInt()),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}