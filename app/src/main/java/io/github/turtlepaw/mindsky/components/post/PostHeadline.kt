package io.github.turtlepaw.mindsky.components.post

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bsky.actor.ProfileViewBasic
import app.bsky.labeler.GetServicesResponseViewUnion
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.di.LocalLabelManager
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.rememberPreference
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
    val labelManager = LocalLabelManager.current
    val labelers by labelManager.labelersDefinitionFlow.collectAsState()

    LaunchedEffect(labelers) {
        Log.d("PostHeadline", "Labels updated: ${labelers.size}")
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f)) {
            Column {
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
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                FlowRow {
                    for (label in author.labels.filter { it.`val` != "!no-unauthenticated" }) {
                        val resolvedLabel = labelers[label.src.did]

                        if (resolvedLabel != null) {
                            LabelComponent(
                                label = resolvedLabel.getDisplayName(label.`val`),
                                avatar = resolvedLabel.getAvatar()
                            )
                        } else {
                            // Fallback for unresolved labels
                            LabelComponent(
                                label = label.`val`,
                                null
                            )
                        }
                    }
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
            Checkmark(Modifier.size(23.dp))
        }
    }
}

@Composable
fun LabelComponent(
    label: String,
    avatar: String?,
    isLarge: Boolean = false
) {
    val showAvatar by rememberPreference(AppPrefs.ShowLabelerAvatars)
    Row(
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .background(
                color = if (isLarge) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            )
            .run {
                if (isLarge) padding(horizontal = 5.dp, vertical = 2.dp) else this
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            2.dp,
            Alignment.CenterHorizontally
        )
    ) {
        if (showAvatar && avatar != null) {
            Avatar(
                modifier = Modifier.size(
                    if (isLarge) 20.dp else 12.dp
                ),
                avatarUrl = avatar,
                contentDescription = "Labeler Avatar",
                clip = CircleShape,
            )
        }
        Text(
            text = label,
            style = (if (isLarge) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall).copy(
                color = MaterialTheme.colorScheme.onSurface.copy(0.9f)
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun GetServicesResponseViewUnion.getDisplayName(identifier: String): String {
    return when (this) {
        is GetServicesResponseViewUnion.LabelerView -> null
        is GetServicesResponseViewUnion.LabelerViewDetailed -> this.value.policies.labelValueDefinitions.find { it.identifier == identifier }
            ?.locales?.find { it.lang.tag == "en" }?.name

        is GetServicesResponseViewUnion.Unknown -> null
    } ?: identifier
}

fun GetServicesResponseViewUnion.getAvatar(): String? {
    return when (this) {
        is GetServicesResponseViewUnion.LabelerView -> this.value.creator.avatar?.uri
        is GetServicesResponseViewUnion.LabelerViewDetailed -> this.value.creator.avatar?.uri

        is GetServicesResponseViewUnion.Unknown -> null
    }
}

@Composable
fun Checkmark(modifier: Modifier) {
    Icon(
        painterResource(R.drawable.check_circle),
        contentDescription = "Verified",
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(horizontal = 2.dp)
    )
}