package io.github.turtlepaw.mindsky.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.TextAutoSizeDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage

@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    contentDescription: String?,
) {
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = contentDescription,
            modifier = modifier.clip(
                CircleShape
            ),
        )
    } else {
        FallbackAvatar(
            modifier = modifier,
            contentDescription = contentDescription,
        )
    }
}

@Composable
private fun FallbackAvatar(
    modifier: Modifier = Modifier,
    contentDescription: String?,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, CircleShape),
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