package io.github.turtlepaw.mindsky.components.post

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import app.bsky.feed.PostView
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

/**
 * A post component that shows threading indicators for replies
 */
@Composable
fun ThreadedPostComponent(
    post: PostView,
    navigator: DestinationsNavigator,
    density: PostDensity = PostDensity.Regular,
    depth: Int = 0,
    showThreadLine: Boolean = false,
    isParent: Boolean = false,
    onClick: () -> Unit
) {
    val threadLineColor = if (isParent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }

    val indentationWidth = (depth * 16).dp
    val threadLineWidth = 2.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentationWidth)
    ) {
        // Threading line indicator
        if (depth > 0) {
            Box(
                modifier = Modifier
                    .width(threadLineWidth + 8.dp)
                    .height(if (density == PostDensity.Compact) 80.dp else 120.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showThreadLine) {
                    Canvas(
                        modifier = Modifier
                            .width(threadLineWidth)
                            .fillMaxHeight()
                    ) {
                        val strokeWidth = threadLineWidth.toPx()

                        // Vertical line
                        drawLine(
                            color = threadLineColor,
                            start = Offset(strokeWidth / 2, 0f),
                            end = Offset(strokeWidth / 2, size.height),
                            strokeWidth = strokeWidth,
                            pathEffect = if (isParent) null else PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                        )
                    }
                }

                // Horizontal connection line to post
                Canvas(
                    modifier = Modifier
                        .width(8.dp)
                        .height(threadLineWidth)
                ) {
                    val strokeWidth = threadLineWidth.toPx()

                    drawLine(
                        color = threadLineColor,
                        start = Offset(0f, strokeWidth / 2),
                        end = Offset(size.width, strokeWidth / 2),
                        strokeWidth = strokeWidth,
                        pathEffect = if (isParent) null else PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
                    )
                }
            }
        }

        // The actual post content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            PostComponent(
                post,
                navigator,
                enabled = true,
                density = density,
                onClick = onClick
            )

            // Add some spacing between threaded posts
            if (depth > 0) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
