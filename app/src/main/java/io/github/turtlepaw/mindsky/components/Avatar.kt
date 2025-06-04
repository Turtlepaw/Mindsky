package io.github.turtlepaw.mindsky.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil3.compose.AsyncImage

@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    contentDescription: String?,
    fallbackColor: Color
) {
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    } else {
        FallbackAvatar(
            modifier = modifier,
            contentDescription = contentDescription,
            fallbackColor = fallbackColor
        )
    }
}

@Composable
private fun FallbackAvatar(
    modifier: Modifier = Modifier,
    contentDescription: String?,
    fallbackColor: Color
) {
    Box(modifier.background(fallbackColor, CircleShape)) {
        Text(
            text = contentDescription?.take(1) ?: "?",
            modifier = modifier,
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            maxLines = 1
        )
    }
}