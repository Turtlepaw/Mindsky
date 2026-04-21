package io.github.turtlepaw.mindsky.components.post

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.Like
import app.bsky.feed.Post
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.richtext.FacetFeatureUnion
import coil3.compose.AsyncImage
import com.atproto.label.Label
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.DeleteRecordRequest
import com.atproto.repo.StrongRef
import com.ramcosta.composedestinations.generated.destinations.ImageDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.components.post.embeds.FeedGraphDisplay
import io.github.turtlepaw.mindsky.components.post.embeds.InformationalEmbed
import io.github.turtlepaw.mindsky.components.post.embeds.LinkEmbed
import io.github.turtlepaw.mindsky.components.post.embeds.QuotePost
import io.github.turtlepaw.mindsky.components.post.embeds.StarterPack
import io.github.turtlepaw.mindsky.components.post.embeds.VideoEmbed
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import sh.christian.ozone.api.model.Timestamp
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun PostComponent(
    postView: FeedViewPost,
    navigator: DestinationsNavigator,
    density: PostDensity = PostDensity.Regular,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    PostComponent(
        postView.post,
        navigator,
        postView.reason,
        onClick = onClick,
        enabled = enabled,
        density = density
    )
}

@Serializable
public data class AppBskyFeedLike(
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @EncodeDefault
    public val `$type`: String = "app.bsky.feed.like",
    public val subject: StrongRef,
    public val createdAt: Timestamp,
) {
    companion object {
        public fun fromSubject(subject: StrongRef): AppBskyFeedLike {
            return AppBskyFeedLike(
                subject = subject,
                createdAt = Clock.System.now()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepostSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    if (isOpen) {
        ModalBottomSheet(
            onDismissRequest = {
                onDismiss()
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismiss()
                                onClick()
                            }
                        }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Text("Repost")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismiss()
                                onClick()
                            }
                        }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = "Chat bubble",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Text("Quote post")
                }
            }
        }
    }
}

@Composable
fun PostComponent(
    postView: PostView,
    navigator: DestinationsNavigator,
    reason: FeedViewPostReasonUnion? = null,
    discoveryContext: @Composable () -> Unit = {},
    showActions: Boolean = true,
    density: PostDensity = PostDensity.Regular,
    enabled: Boolean = true,
    showReply: Boolean = true,
    showSeparator: Boolean = true,
    showLabels: Boolean = true,
    onClick: () -> Unit,
) {
    val author = postView.author
    val postRecord = try {
        Log.d("PostDebug", "Raw record JSON: ${postView.record}")
        postView.record.decodeAs<Post>()
    } catch (e: Exception) {
        Log.e("PostDebug", "Failed to decode JSON: ${postView.record}", e)
        null
    }

    if (postRecord == null) {
        return Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Failed to decode post")
        }
    }
    val session = SessionManager(LocalContext.current).getSession()
    val api = LocalMindskyApi.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var isRepostSheetOpen by remember { mutableStateOf(false) }

    RepostSheet(
        isOpen = isRepostSheetOpen,
        onDismiss = { isRepostSheetOpen = false },
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)

        }
    )

    PostStructure(
        metadata = {
            Column(
                modifier = Modifier.padding()
            ) {
                discoveryContext()

                if (reason is FeedViewPostReasonUnion.ReasonRepost) {
                    Row(
                        horizontalArrangement = spacedBy(8.dp),
                        modifier = Modifier
                            .padding(bottom = 5.dp)
                            .padding(start = 25.dp)
                            .run {
                                if (session?.did == reason.value.by.did.did) this
                                else clickable {
                                    navigator.navigate(
                                        ProfileDestination(reason.value.by.did.did)
                                    )
                                }
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Repeat,
                            contentDescription = "Repeat",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "Reposted by ${if (session?.did == reason.value.by.did.did) "you" else reason.value.by.displayName ?: reason.value.by.handle.handle}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.W600,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            ),
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        avatar = {
            Avatar(
                modifier = it,
                avatarUrl = author.avatar?.uri,
                contentDescription = author.displayName ?: author.handle.handle,
            ) {
                navigator.navigate(
                    ProfileDestination(author.did.did)
                )
            }
        },
        headline = {
            PostHeadline(postRecord.createdAt, author, density, showLabels)
        },
        actions = {
            if (showActions) {
                FlowRow(it, horizontalArrangement = spacedBy(24.dp)) {
                    var isLiked by remember {
                        mutableStateOf(postView.viewer?.like != null)
                    }
                    var likeUri by remember {
                        mutableStateOf(postView.viewer?.like)
                    }
                    val isReposted = postView.viewer?.repost != null
                    PostAction.Button(
                        label = postView.replyCount,
                        icon = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = "Chat Bubble",
                    ) { }
                    PostAction.Button(
                        label = postView.repostCount,
                        icon = Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        isHighlighted = isReposted,
                    ) {
                        isRepostSheetOpen = true
                    }
                    PostAction.Button(
                        label = (postView.likeCount ?: 0) + if (isLiked) 1 else 0,
                        icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Heart",
                        isHighlighted = isLiked,
                    ) {
                        coroutineScope.launch {
                            val session = SessionManager(context).getSession()
                            val collection = Nsid("app.bsky.feed.like")
                            val vibrator =
                                ContextCompat.getSystemService(context, Vibrator::class.java)!!
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                vibrator.vibrate(
                                    VibrationEffect.createPredefined(
                                        if (isLiked) VibrationEffect.EFFECT_TICK
                                        else VibrationEffect.EFFECT_DOUBLE_CLICK
                                    )
                                )
                            } else {
                                vibrator.vibrate(50)
                            }
                            if (isLiked) {
                                isLiked = false
                                api.deleteRecord(
                                    DeleteRecordRequest(
                                        collection = collection,
                                        repo = Did(session!!.did),
                                        rkey = likeUri!!.getRkey(),
                                    ),
                                ).requireResponse()
                                likeUri = null
                            } else {
                                isLiked = true
                                val data = api.createRecord(
                                    CreateRecordRequest(
                                        record = Json.encodeAsJsonContent(
                                            AppBskyFeedLike.fromSubject(
                                                StrongRef(postView.uri, postView.cid)
                                            )
                                        ),
                                        repo = Did(session!!.did),
                                        collection = collection,
                                    )
                                ).requireResponse()
                                likeUri = data.uri
                            }
                        }
                    }
                }
            }
        },
        density = density,
        onClick = if (enabled) onClick else null,
        createdAt = postRecord.createdAt,
        showSeparator = showSeparator
    ) {
        if (density == PostDensity.Expanded) Labelers(author.labels)
        if (postRecord.reply != null && showReply) {
            Row {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Reply,
                    contentDescription = "Reply",
                    modifier = Modifier
                        .size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                )
                Text(
                    text = "Reply",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val uriHandler = LocalUriHandler.current
        val annotatedText = buildAnnotatedString {
            append(postRecord.text)
            val textBytes = postRecord.text.toByteArray(StandardCharsets.UTF_8)

            val textByteLength = textBytes.size

            postRecord.facets?.forEach { facet ->
                val byteStart = facet.index.byteStart.toInt().coerceIn(0, textByteLength)
                val byteEnd = facet.index.byteEnd.toInt().coerceIn(byteStart, textByteLength)

                // Convert byte offsets to char offsets correctly
                val charStart = String(textBytes, 0, byteStart, StandardCharsets.UTF_8).length
                val charEnd = String(textBytes, 0, byteEnd, StandardCharsets.UTF_8).length

                if (charStart >= charEnd) {
                    Log.w(
                        "PostComponent",
                        "Skipping invalid facet range: charStart=$charStart, charEnd=$charEnd from byteStart=$byteStart, byteEnd=$byteEnd. Text: ${postRecord.text}"
                    )
                    return@forEach // Skip invalid or empty ranges
                }

                facet.features.forEach { feature ->
                    when (feature) {
                        is FacetFeatureUnion.Link -> {
                            addStyle(
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline
                                ).toSpanStyle(),
                                start = charStart,
                                end = charEnd
                            )
                            addStringAnnotation(
                                tag = "url",
                                annotation = feature.value.uri.uri,
                                start = charStart,
                                end = charEnd
                            )
                        }

                        is FacetFeatureUnion.Mention -> {
                            addStyle(
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                ).toSpanStyle(),
                                start = charStart,
                                end = charEnd
                            )
                            addStringAnnotation(
                                tag = "did",
                                annotation = feature.value.did.did,
                                start = charStart,
                                end = charEnd
                            )
                        }

                        is FacetFeatureUnion.Tag -> {
                            addStyle(
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                ).toSpanStyle(),
                                start = charStart,
                                end = charEnd
                            )
                            addStringAnnotation(
                                tag = "tag",
                                annotation = feature.value.tag,
                                start = charStart,
                                end = charEnd
                            )
                        }

                        else -> {
                            // Handle Unknown or other types if necessary
                        }
                    }
                }
            }
        }

        ClickableText(
            text = annotatedText,
            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            onClick = { offset ->
                annotatedText.getStringAnnotations("url", offset, offset)
                    .firstOrNull()
                    ?.let { annotation ->
                        uriHandler.openUri(annotation.item)
                        return@ClickableText
                    }

                annotatedText.getStringAnnotations("did", offset, offset)
                    .firstOrNull()
                    ?.let { annotation ->
                        navigator.navigate(ProfileDestination(annotation.item))
                        return@ClickableText
                    }

                annotatedText.getStringAnnotations("tag", offset, offset)
                    .firstOrNull()
                    ?.let { annotation ->
                        val queryTag =
                            if (annotation.item.startsWith("#")) annotation.item.substring(1) else annotation.item
                        val encodedTag =
                            URLEncoder.encode(queryTag, StandardCharsets.UTF_8.name())
                        uriHandler.openUri("https://bsky.app/search?q=%23$encodedTag")
                    }
            }
        )

        when (postView.embed) {
            is PostViewEmbedUnion.ImagesView -> {
                val imagesEmbed =
                    (postView.embed as PostViewEmbedUnion.ImagesView).value
                val images = imagesEmbed.images // Store for easier access
                if (images.isNotEmpty()) {
                    val columnCount = if (images.size == 1) 1 else 2
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnCount),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 800.dp),
                        horizontalArrangement = spacedBy(8.dp),
                        verticalArrangement = spacedBy(8.dp)
                    ) {
                        items(images) { image ->
                            val imageModifier = if (images.size == 1) {
                                Modifier
                                    .fillMaxSize() // Fill the 200dp high cell for a single image
                                    .padding(4.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            } else {
                                Modifier
                                    .aspectRatio(1f) // Square for multiple images in 2 columns
                                    .clip(MaterialTheme.shapes.medium)
                            }
                            AsyncImage(
                                model = image.thumb.uri,
                                contentDescription = image.alt
                                    ?: "Post image", // Use alt text
                                contentScale = ContentScale.Crop,
                                modifier = imageModifier
                                    .background(MaterialTheme.colorScheme.error)
                                    .clickable {
                                        navigator.navigate(
                                            ImageDestination(
                                                imageUrl = image.fullsize.uri,
                                                alt = image.alt
                                            )
                                        )
                                    }
                            )
                        }
                    }
                }
            }

            is PostViewEmbedUnion.RecordView -> {
                val recordEmbed = (postView.embed as PostViewEmbedUnion.RecordView).value
                val record = recordEmbed.record

                when (record) {
                    is RecordViewRecordUnion.ViewRecord -> {
                        QuotePost(record, navigator)
                    }

                    is RecordViewRecordUnion.FeedGeneratorView -> {
                        FeedGraphDisplay(record.value)
                    }

                    is RecordViewRecordUnion.GraphListView -> {
                        FeedGraphDisplay(record.value)
                    }

                    is RecordViewRecordUnion.GraphStarterPackViewBasic -> {
                        StarterPack(record.value)
                    }
//                    is RecordViewRecordUnion.LabelerLabelerView -> TODO()
                    is RecordViewRecordUnion.Unknown -> {
                        InformationalEmbed(R.string.unknown_embed)
                    }

                    is RecordViewRecordUnion.ViewBlocked -> {
                        InformationalEmbed(R.string.blocked)
                    }

                    is RecordViewRecordUnion.ViewDetached -> {
                        InformationalEmbed(R.string.detached_by_author)
                    }

                    is RecordViewRecordUnion.ViewNotFound -> {
                        InformationalEmbed(R.string.embed_not_found)
                    }

                    else -> {}
                }
            }

            // This is an assumed structure for video embeds.
            // Replace with the actual structure if different.
            is PostViewEmbedUnion.VideoView -> {
                val externalEmbed = (postView.embed as PostViewEmbedUnion.VideoView)
                // Assuming the video URL is in externalEmbed.external.uri
                // You might need to adjust this based on the actual data structure
                VideoEmbed(externalEmbed.value.playlist.uri, externalEmbed.value.thumbnail?.uri)
            }

            is PostViewEmbedUnion.ExternalView -> {
                val externalEmbed = (postView.embed as PostViewEmbedUnion.ExternalView).value

                LinkEmbed(externalEmbed)
            }

            else -> {}
        }
    }
}

fun AtUri.getRkey(): RKey {
    return RKey(this.atUri.substringAfterLast("/"))
}

@Composable
fun rememberLoadingColor(): Color {
    val infiniteTransition = rememberInfiniteTransition()

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    return MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
}

@Composable
fun LoadingPost() {
    val color = rememberLoadingColor()

    PostStructure(
        avatar = {
            Box(
                modifier = it
                    .background(
                        color,
                        CircleShape
                    )
            )
        },
        headline = {
            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth(0.5f)
                    .height(10.dp)
                    .background(
                        color,
                        MaterialTheme.shapes.small
                    )
            )
        },
        metadata = {},
        actions = {},
        createdAt = Clock.System.now()
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .fillMaxWidth(if (it == 2) 0.8f else 0.9f)
                    .height(9.dp)
                    .background(
                        color,
                        MaterialTheme.shapes.small
                    )
            )
        }
    }
}
