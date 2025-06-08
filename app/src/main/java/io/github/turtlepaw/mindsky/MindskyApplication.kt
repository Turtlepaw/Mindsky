package io.github.turtlepaw.mindsky

import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.directory
import coil3.request.crossfade
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.Logger as KtorLogger
import io.ktor.http.takeFrom
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.api.BlueskyAuthPlugin
import kotlinx.coroutines.flow.MutableStateFlow // Added for AuthTokensFlow
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi

class MindskyApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(1024 * 1024 * 100) // 100 MB
                    .build()
            }
            .build()
    }

    // For LocalAuthTokensFlow
    val authTokensFlow = MutableStateFlow<BlueskyAuthPlugin.Tokens?>(null)

    lateinit var blueskyApi: AuthenticatedXrpcBlueskyApi
        private set // External read, internal set

    override fun onCreate() {
        super.onCreate()
        Log.d("MindskyApplication", "onCreate")
        val sessionManager = SessionManager(this)
        val currentSession = sessionManager.getSession()
        if (currentSession != null) {
            Log.d(
                "MindskyApplication",
                "Session found, configuring API for host: ${currentSession.host}"
            )
            updateBlueskyApi(currentSession)
        } else {
            Log.d("MindskyApplication", "No session found, setting up default API client.")
            // Setup a default, unauthenticated client or a client pointing to a default host
            // This instance will be replaced by updateBlueskyApi after a successful login
            blueskyApi = AuthenticatedXrpcBlueskyApi(HttpClient(OkHttp) {
                install(Logging) {
                    logger = object : KtorLogger {
                        override fun log(message: String) {
                            Log.v("Ktor_Default", message)
                        }
                    }
                    level = LogLevel.HEADERS // Or your preferred level for default
                }
                defaultRequest {
                    // It's good to have a default, even if it's just bsky.social
                    // or if some unauthenticated calls are possible
                    url.takeFrom("https://bsky.social")
                }
                expectSuccess = true
            })
        }
    }

    fun updateBlueskyApi(session: UserSession) {
        Log.d(
            "MindskyApplication",
            "Updating Bluesky API. Host: ${session.host}, User: ${session.handle}"
        )
        val newHttpClient = HttpClient(OkHttp) { // Or your existing Ktor HttpClient configuration
            install(Logging) {
                logger = object : KtorLogger {
                    override fun log(message: String) {
                        Log.v("Ktor_Authenticated", message)
                    }
                }
                level = LogLevel.BODY // More detailed logging for authenticated calls
            }
            defaultRequest {
                url.takeFrom(session.host) // Use the host from the session
            }
            expectSuccess = true
        }
        blueskyApi = AuthenticatedXrpcBlueskyApi(
            newHttpClient, initialTokens = BlueskyAuthPlugin.Tokens(
                auth = session.accessToken,
                refresh = session.refreshToken
            )
        )

        // Update the authTokensFlow as well, so UI reacting to it gets updated
        authTokensFlow.value = BlueskyAuthPlugin.Tokens(session.accessToken, session.refreshToken)
        Log.d("MindskyApplication", "Bluesky API updated for host: ${session.host}")
    }
}
