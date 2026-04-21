package io.github.turtlepaw.mindsky.components.post.embeds

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.bsky.embed.ExternalView
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.PostView
import coil3.compose.AsyncImage
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination.invoke
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.components.post.LoadingPost
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.post.PostDensity
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import kotlinx.coroutines.launch

@Composable
fun LinkEmbed(record: ExternalView) {
    val uriHandler = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current
    EmbedStructure(onClick = {
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        uriHandler.openUri(record.external.uri.toString())
    }) {
        if(record.external.thumb?.uri != null) AsyncImage(
            model = record.external.thumb?.uri,
            contentDescription = "External embed image",
            modifier = Modifier
                .fillMaxWidth()
        )
        Column(
            modifier = Modifier
                .padding(15.dp)
        ) {
            val domain = record.external.uri.uri.toUri().authority
            if(domain != null) Text(domain, style = MaterialTheme.typography.labelMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ))
            Text(record.external.title, style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ))
            Text(record.external.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}