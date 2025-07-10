package io.github.turtlepaw.mindsky.components.post.embeds

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.sanghun.compose.video.VideoPlayer
import io.sanghun.compose.video.uri.VideoPlayerMediaItem

@Composable
fun VideoEmbed(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    VideoPlayer(
        mediaItems = listOf(
            VideoPlayerMediaItem.NetworkMediaItem(
                url = videoUrl,
            )
        ),
        handleLifecycle = true,
        autoPlay = false,
        usePlayerController = true,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
    )
}

@Preview
@Composable
fun VideoEmbedPreview() {
    // Replace with a valid video URL for preview
    VideoEmbed(videoUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
}
