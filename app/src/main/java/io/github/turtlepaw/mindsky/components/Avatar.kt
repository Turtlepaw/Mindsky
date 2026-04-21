package io.github.turtlepaw.mindsky.components

import android.graphics.drawable.VectorDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.TextAutoSizeDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    contentDescription: String?,
    clip: Shape = CircleShape,
    onClick: (() -> Unit)? = null,
) {
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(clip)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            onClick = onClick
                        )
                    } else Modifier
                )
        )
    } else {
        FallbackAvatar(
            modifier = modifier.then(
                if (onClick != null)
                    Modifier.clickable(onClick = onClick)
                else Modifier
            ),
            contentDescription = contentDescription,
            shape = clip,
        )
    }
}

@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    drawable: ImageVector?,
    contentDescription: String?,
    clip: Shape = CircleShape,
    color: Color = MaterialTheme.colorScheme.primary,
    onColor: Color = MaterialTheme.colorScheme.onPrimary,
    onClick: (() -> Unit)? = null,
) {
    if (drawable != null) {
        Box(
            modifier = modifier
                .background(color, clip),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                drawable,
                contentDescription = contentDescription,
                tint = onColor
            )
        }
    } else {
        FallbackAvatar(
            modifier = modifier.then(
                if (onClick != null)
                    Modifier.clickable(onClick = onClick)
                else Modifier
            ),
            contentDescription = contentDescription,
            shape = clip,
        )
    }
}

@Composable
private fun FallbackAvatar(
    modifier: Modifier = Modifier,
    contentDescription: String?,
    shape: Shape = CircleShape,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = contentDescription?.take(1) ?: "?",
            color = MaterialTheme.colorScheme.onPrimary,
            autoSize = TextAutoSize.StepBased(
                maxFontSize = TextAutoSizeDefaults.MaxFontSize,
                minFontSize = TextAutoSizeDefaults.MinFontSize,
            ),
            maxLines = 1,
            modifier = Modifier
                .fillMaxSize(0.8f)
                .wrapContentSize(Alignment.Center)
        )
    }
}

@Composable
private fun LoadingAvatar(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(0.6f), CircleShape),
    )
}

@Preview
@Composable
fun AvatarPreview() {
    Avatar(
        modifier = Modifier.size(40.dp),
        avatarUrl = "https://example.com/avatar.jpg",
        contentDescription = "User Avatar",
    )
}

@Preview
@Composable
fun FallbackAvatarPreview() {
    Avatar(
        modifier = Modifier.size(40.dp),
        avatarUrl = null,
        contentDescription = "User Avatar",
    )
}
