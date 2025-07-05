package io.github.turtlepaw.mindsky.components.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.toJavaInstant
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class PostDensity {
    Compact,
    Regular,
    Expanded
}

@Composable
fun PostStructure(
    headline: @Composable () -> Unit,
    avatar: @Composable (modifier: Modifier) -> Unit,
    metadata: @Composable () -> Unit,
    actions: @Composable (modifier: Modifier) -> Unit,
    discoveryContext: @Composable (modifier: Modifier) -> Unit = {},
    density: PostDensity = PostDensity.Regular,
    onClick: (() -> Unit)? = null,
    createdAt: kotlinx.datetime.Instant,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalPostDensity provides density
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                metadata()

                // Header with avatar and headline
                Row(
                    horizontalArrangement = spacedBy(
                        if (density == PostDensity.Compact) 6.dp else 12.dp
                    ),
                    verticalAlignment = if (density == PostDensity.Compact) Alignment.CenterVertically else Alignment.Top,
                ) {
                    avatar(
                        Modifier
                            .size(if (density == PostDensity.Compact) 20.dp else 42.dp)
                            .let { if (density == PostDensity.Compact) it else it.offset(y = 2.dp) }
                    )

                    if (density == PostDensity.Compact || density == PostDensity.Expanded) {
                        headline()
                    } else {
                        Column(
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.Start
                        ) {
                            headline()
                            content()
                            PostAction.Section(
                                actions,
                                discoveryContext
                            )
                        }
                    }
                }

                // Content section for compact mode
                if (density == PostDensity.Compact || density == PostDensity.Expanded) {
                    if (density == PostDensity.Expanded) Spacer(
                        modifier = Modifier.size(10.dp)
                    )

                    content()
                    if (density != PostDensity.Expanded) PostAction.Section(
                        actions,
                        discoveryContext
                    )
                }
            }
            if (density == PostDensity.Expanded) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 26.dp, vertical = 10.dp)
                ) {
                    Text(
                        createdAt.toJavaInstant().toFormattedString()
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (density == PostDensity.Expanded) Modifier.padding(horizontal = 12.dp) else Modifier
                    ),
                thickness = 0.25.dp
            )
            if (density == PostDensity.Expanded) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 26.dp, vertical = 10.dp)
                ) {
                    PostAction.Section(actions, discoveryContext)
                }
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 0.25.dp
                )
            }
        }
    }
}

fun Instant.toFormattedString(zoneId: ZoneId = ZoneId.systemDefault()): String {
    val formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mma")
    return this.atZone(zoneId)
        .toLocalDateTime()
        .format(formatter)
}
