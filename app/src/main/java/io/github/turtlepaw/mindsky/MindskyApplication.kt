package io.github.turtlepaw.mindsky

import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.directory
import coil3.request.crossfade
import com.ramcosta.composedestinations.annotation.Destination
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.Logger as KtorLogger
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.takeFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
// Using Dispatchers.Main for applicationScope, but ensure long-running init isn't on main thread
// For most of this, it's fine.
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.BlueskyAuthPlugin

class MindskyApplication : Application(), SingletonImageLoader.Factory {

    private lateinit var sessionManager: SessionManager

    val authTokensFlow = MutableStateFlow<BlueskyAuthPlugin.Tokens?>(null)
    lateinit var blueskyApi: AuthenticatedXrpcBlueskyApi
        private set

    override fun newImageLoader(context: Context): ImageLoader { // Coil setup
        return ImageLoader.Builder(context)
            .crossfade(true)
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(1024 * 1024 * 100)
                    .build()
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MindskyApplication", "onCreate")
        ObjectBox.init(this) // Your ObjectBox initialization
        sessionManager = SessionManager(this)

        val currentSession = sessionManager.getSession()
        if (currentSession != null) {
            Log.d(
                "MindskyApplication",
                "Active session found for ${currentSession.handle}. Configuring API."
            )
            configureAuthenticatedApi(currentSession)
        } else {
            Log.d("MindskyApplication", "No active session. User needs to login.")
            configureAuthenticatedApi()
        }
    }

    fun configureAuthenticatedApi(session: UserSession? = null) {
        Log.i(
            "MindskyApplication",
            "Configuring authenticated API for host: ${session?.host}, user: ${session?.handle}"
        )

        val httpClient = HttpClient(OkHttp) {
            // OkHttp specific configurations can go in an engine { ... } block if needed

            install(Logging) {
                logger = object : KtorLogger {
                    override fun log(message: String) {
                        Log.v("Ktor_Authenticated", message)
                    }
                }
                level =
                    LogLevel.BODY // Log BODY for auth debugging, consider HEADERS for production
            }

            defaultRequest { // Set the base URL for all requests made by this client
                url.takeFrom(session?.host ?: "https://bsky.social")
            }

            HttpResponseValidator {
                handleResponseExceptionWithRequest { exception, request ->
                    val clientException = exception as? ClientRequestException
                        ?: return@handleResponseExceptionWithRequest
                    // Check if the failed request was to the refresh session endpoint
                    if (clientException.response.status == HttpStatusCode.BadRequest &&
                        request.url.encodedPath.endsWith("com.atproto.server.refreshSession")
                    ) {
                        try {
                            val errorBody = clientException.response.bodyAsText()
                            if (errorBody.contains("\"ExpiredToken\"", ignoreCase = true) ||
                                errorBody.contains("\"Token has been revoked\"", ignoreCase = true)
                            ) {
                                Log.e(
                                    "MindskyApplicationAuth",
                                    "Unrecoverable refresh token failure: $errorBody. Clearing session."
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(
                                "MindskyApplicationAuth",
                                "Failed to read error body from refresh session response: $e"
                            )
                        }
                    }
                }
            }
        }

        this.blueskyApi = AuthenticatedXrpcBlueskyApi(
            httpClient,
            session?.toTokens(),
        )

        CoroutineScope(Dispatchers.IO).launch {
            blueskyApi?.authTokens?.collect { tokens ->
                if (tokens != null && session != null) {
                    sessionManager.saveSession(
                        UserSession(
                            did = session.did,
                            handle = session.handle,
                            accessToken = tokens.auth,
                            refreshToken = tokens.refresh,
                            host = session.host
                        )
                    )
                } else {
                    Log.w(
                        "MindskyApplicationAuth",
                        "Auth tokens flow emitted null, clearing session."
                    )
                    sessionManager.clearSession()
                }
            }
        }
        // Initialize the flow with the current session's tokens
        authTokensFlow.value = session?.toTokens()
        Log.i("MindskyApplication", "Authenticated API client configured for ${session?.handle}.")
    }
}

fun UserSession.toTokens(): BlueskyAuthPlugin.Tokens {
    return BlueskyAuthPlugin.Tokens(
        auth = this.accessToken,
        refresh = this.refreshToken
    )
}
