package io.github.turtlepaw.mindsky.components.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
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

@Composable
fun PostStructure(
    headline: @Composable () -> Unit,
    avatar: @Composable (modifier: Modifier) -> Unit,
    metadata: @Composable () -> Unit,
    actions: @Composable (modifier: Modifier) -> Unit,
    discoveryContext: @Composable (modifier: Modifier) -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            metadata()

            Row(
                horizontalArrangement = spacedBy(12.dp)
            ) {
                avatar(
                    Modifier
                        .size(42.dp)
                        .align(Alignment.Top)
                        .offset(y = 2.dp),
                )

                Column(
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    headline()
                    content()
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
            }
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.25.dp
        )
    }
}

@Composable
fun PostAction(
    label: Long?,
    icon: ImageVector,
    contentDescription: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
) {
    PostAction(
        label = label?.toFloat(),
        icon = icon,
        contentDescription = contentDescription,
        isHighlighted = isHighlighted,
        onClick = onClick
    )
}

@Composable
fun PostAction(
    label: Float?,
    icon: ImageVector,
    contentDescription: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
){
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
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )
            if (label != null && label > 0) {
                Text(
                    text = Formatters.formatNumberForLocale(label.toInt()),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}