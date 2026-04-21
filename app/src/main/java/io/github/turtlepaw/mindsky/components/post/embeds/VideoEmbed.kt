package io.github.turtlepaw.mindsky.components.post.embeds

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlin.OptIn

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("ExperimentalMaterial3Api")
@Composable
fun VideoEmbed(
    videoUrl: String,
    thumbnail: String? = null,
    isCompact: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    val videoSize = remember { mutableStateOf<VideoSize?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var hasStarted by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var thumbnailHeight by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(hasStarted) {
        if (hasStarted && !isPrepared) {
            isLoading = true
            val mediaItem = MediaItem.fromUri(videoUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    // Update position and duration
    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition
            duration = player.duration.takeIf { it > 0 } ?: 0L
            kotlinx.coroutines.delay(1000)
        }
    }

    // Listen for player state changes
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize.value = size
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isLoading = false
                        isPrepared = true
                    }

                    Player.STATE_BUFFERING -> {
                        isLoading = true
                    }

                    Player.STATE_ENDED -> {
                        isPlaying = false
                    }
                }
            }

            override fun onIsLoadingChanged(_isLoading: Boolean) {
                // We handle loading state in onPlaybackStateChanged
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
        }
    }

    // Manage lifecycle events
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    // Use detected video dimensions if available, otherwise default
    val aspectRatio =
        if (videoSize.value != null && videoSize.value!!.width > 0 && videoSize.value!!.height > 0) {
            videoSize.value!!.width.toFloat() / videoSize.value!!.height.toFloat()
        } else {
            null
        }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier.heightIn(
                    min = 200.dp
                )
            )
            .then(
                if (!hasStarted) Modifier.clickable { hasStarted = true } else Modifier
            )
            .clip(RoundedCornerShape(16.dp))
    ) {
        // Thumbnail
        AsyncImage(
            model = thumbnail,
            contentDescription = "Video thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier.heightIn(min = 200.dp)
                )
                .onSizeChanged { size ->
                    thumbnailHeight = size.height
                }
        )

        // Video player without controls
        if (!hasStarted) {
            // Thumbnail state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { thumbnailHeight?.toDp() ?: 200.dp }),
                contentAlignment = Alignment.Center
            ) {

                // Play button overlay
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                        .padding(12.dp)
                        .size(48.dp)
                )
            }
        } else if (isLoading && !isPrepared) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { thumbnailHeight?.toDp() ?: 200.dp })
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(40.dp),
                    color = Color.White
                )
            }
        } else {
            // Video player state
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Only show controls after video has started and is prepared
        if (hasStarted && isPrepared) {
            if (isCompact) {
                // Compact modern controls with gradient
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 5.dp)
                            .padding(bottom = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val size = 20.dp
                        // Play/Pause button
                        IconButton(
                            onClick = {
                                if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier
                                    .background(
                                        Color.Black.copy(alpha = 0.6f),
                                        CircleShape
                                    )
                                    .padding(5.dp)
                                    .size(size)
                            )
                        }

                        // Mute/Unmute button
                        IconButton(
                            onClick = {
                                val newMuteState = !isMuted
                                player.volume = if (newMuteState) 0f else 1f
                                isMuted = newMuteState
                            },
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = Color.White,
                                modifier = Modifier
                                    .background(
                                        Color.Black.copy(alpha = 0.6f),
                                        CircleShape
                                    )
                                    .padding(5.dp)
                                    .size(size)
                            )
                        }
                    }
                }
            } else {
                // Expanded controls
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.9f)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        // Progress bar
                        LinearProgressIndicator(
                            progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Controls row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Play/Pause
                            IconButton(
                                onClick = {
                                    if (player.isPlaying) {
                                        player.pause()
                                    } else {
                                        player.play()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Time display
                            Text(
                                text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                                color = Color.White,
                                fontSize = 14.sp
                            )

                            // Mute button
                            IconButton(
                                onClick = {
                                    val newMuteState = !isMuted
                                    player.volume = if (newMuteState) 0f else 1f
                                    isMuted = newMuteState
                                }
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}