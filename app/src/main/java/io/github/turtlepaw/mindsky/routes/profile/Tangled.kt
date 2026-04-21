package io.github.turtlepaw.mindsky.routes.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atproto.repo.ListRecordsQueryParams
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.fetch_and_cache.SingleCache
import io.github.turtlepaw.fetch_and_cache.SingleCacheLoadResult
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import kotlinx.serialization.Serializable
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Nsid
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import io.github.turtlepaw.fetch_and_cache.Cache
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.takeFrom
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import sh.christian.ozone.XrpcBlueskyApi

@Serializable
data class TangledRepo(
    val name: String,
    val knot: String,
    val spindle: String? = null,
    val description: String? = null,
    val website: String? = null,
    val topics: List<String>? = null,
    val source: String? = null,
    val labels: List<String>? = null,
    val repoDid: String? = null,
    val createdAt: String
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.tangled(
    identity: String,
    navigator: DestinationsNavigator
) {
    item {
        Spacer(
            modifier = Modifier.height(16.dp)
        )
    }
    item {
        val repos = rememberTangledRepos(identity)
        val repoList = remember(repos.value) { repos.value.values.toList() }

        val state = when {
            repos.isLoading && repoList.isEmpty() -> "loading"
            repos.error != null && repoList.isEmpty() -> "error"
            repoList.isEmpty() -> "empty"
            else -> "content"
        }

        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) + slideInVertically { it / 4 } togetherWith
                        fadeOut(animationSpec = tween(150))
            },
            label = "repo_state_animation"
        ) { target ->
            when (target) {
                "loading" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }

                "error" -> {
                    Text("Failed to load Tangled repos")
                }

                "empty" -> {
                    Text(
                        "No Tangled repositories found",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                "content" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        repoList.forEach { repo ->
                            TangledRepoRow(identity, repo)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

fun openUrl(uri: Uri, context: Context){
    val customTabsIntent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
    customTabsIntent.intent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    )
    customTabsIntent.launchUrl(context, uri)
}

@Composable
private fun TangledRepoRow(identifier: String, repo: TangledRepo) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium
            )
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.medium
            )
            .clip(
                MaterialTheme.shapes.medium
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                openUrl("https://tangled.org/${identifier}/${repo.name}".toUri(), context)
            }

    ) {
        Column {
            SubcomposeAsyncImage(
                model = "https://tangled.org/${repo.repoDid ?: "${identifier}/${repo.name}"}/opengraph",
                contentDescription = "${repo.name} opengraph image",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                val state by painter.state.collectAsState()
                if (state is AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
            Spacer(
                modifier = Modifier.height(8.dp)
            )
            Column(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
            ) {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                if (!repo.description.isNullOrBlank()) {
                    Text(
                        text = repo.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Serializable
data class ResolveResponse(
    val endpoint: String
)

@Composable
internal fun rememberTangledRepos(identifier: String): SingleCacheLoadResult<TangledRepo> {
    val context = LocalContext.current
    val client = HttpClient(OkHttp) {
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.v("Ktor_Authenticated", message)
                }
            }
            level = LogLevel.BODY
        }
    }
    val pdsCache = Cache<String>(
        identifier = "pds_cache",
        serializer = String.serializer(),
        context = context,
        fetcher = {
            val response = client.get(
                "https://slingshot.microcosm.blue/xrpc/com.bad-example.identity.resolveService?did=${identifier.encodeURLQueryComponent()}&id=%23atproto_pds"
            )
            val json = response.bodyAsText()

            Json.decodeFromString<ResolveResponse>(json).endpoint
        }
    )

    return SingleCache.rememberLoad(
        fetcher = {
            val user = pdsCache.loadAsync(identifier, context)
            if (user.value == null) throw Exception("Failed to resolve PDS for $identifier")
            val api = XrpcBlueskyApi(
                HttpClient(OkHttp) {
                    install(Logging) {
                        logger = object : Logger {
                            override fun log(message: String) {
                                Log.v("Ktor_Authenticated", message)
                            }
                        }
                        level = LogLevel.BODY
                    }
                    defaultRequest {
                        url.takeFrom(
                            Url(user.value!!)
                        )
                    }
                }
            )

            val result = api.listRecords(
                ListRecordsQueryParams(
                    repo = Did(identifier),
                    collection = Nsid("sh.tangled.repo"),
                )
            ).requireResponse()

            result.records.associate { record ->
                val repo = record.value.decodeAs<TangledRepo>()
                record.uri.atUri to repo
            }
        },
        serializer = TangledRepo.serializer(),
        identifier = "${identifier}_tangled_repos",
    )
}
