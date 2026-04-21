package io.github.turtlepaw.mindsky

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.directory
import coil3.request.crossfade
import io.github.turtlepaw.mindsky.auth.DPoPManager
import io.github.turtlepaw.mindsky.auth.DpopAuthPlugin
import io.github.turtlepaw.mindsky.auth.OAuthClient
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import io.github.turtlepaw.mindsky.cache.LabelManager
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.takeFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import io.ktor.client.plugins.logging.Logger as KtorLogger

class MindskyApplication : Application(), SingletonImageLoader.Factory/*, Configuration.Provider*/ {
//    override val workManagerConfiguration: Configuration
//        get() = Configuration.Builder()
//            .setMinimumLoggingLevel(Log.DEBUG)
//            .build()

    private lateinit var sessionManager: SessionManager
    lateinit var labelManager: LabelManager
        private set

    var hostUrl = MutableStateFlow<String>("https://bsky.social")
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
        labelManager = LabelManager(this)
        // postThreadRepository will be initialized in configureAuthenticatedApi

        val currentSession = sessionManager.getSession()
        configureAuthenticatedApi(currentSession)
    }

    fun configureAuthenticatedApi(session: UserSession?){
        configureAuthenticatedApi(
            session?.pdsUrl?.takeIf { it.isNotBlank() } ?: "https://bsky.social",
            session
        )
    }

    fun configureAuthenticatedApi(newHostUrl: String, session: UserSession? = null): AuthenticatedXrpcBlueskyApi {
        hostUrl.value = newHostUrl
        Log.i(
            "MindskyApplication",
            "Configuring authenticated API for host: ${session?.pdsUrl}, user: ${session?.handle}"
        )

        // Now, create the main client with the correct labelers
        val httpClient = HttpClient(OkHttp) {
            expectSuccess = false
            install(Logging) {
                logger = object : KtorLogger {
                    override fun log(message: String) {
                        Log.v("Ktor_Authenticated", message)
                    }
                }
                level = LogLevel.BODY
            }
            install(DpopAuthPlugin) {
                sessionManager = this@MindskyApplication.sessionManager
                oauthClient = OAuthClient(this@MindskyApplication, this@MindskyApplication.sessionManager)
                dpopManager = DPoPManager.getInstance(this@MindskyApplication)
            }

            defaultRequest {
                url.takeFrom(hostUrl.value)
                val cachedLabelers = labelManager.labelersFlow.value.joinToString(",")
                headers.append("atproto-accept-labelers", cachedLabelers)
                headers.append("atproto-proxy", "did:web:api.bsky.app#bsky_appview")
            }
        }

        val client = AuthenticatedXrpcBlueskyApi(
            httpClient,
            null,
        )

        this.blueskyApi = client

        CoroutineScope(Dispatchers.IO).launch {
            labelManager.preWarmDefinitions(blueskyApi)
            labelManager.revalidateLabelers(blueskyApi)
        }

        Log.i("MindskyApplication", "Authenticated API client configured for ${session?.handle}.")

        return client
    }
}
