package io.github.turtlepaw.mindsky.workers

import android.app.Application
import android.content.Context
import android.util.Log
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.takeFrom
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.MutableStateFlow
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.BlueskyAuthPlugin

object WorkerCommon {
    const val API_REQUEST_LIMIT = 100L // Standard API limit for pagination
    const val THERMAL_COOLDOWN_MS = 200L // Short delay for CPU cooling
    const val MAX_PAGES_TO_FETCH_LIKES = 10 // Limit to prevent excessive API calls
    const val PROGRESS = "progress"
    const val STAGE = "stage"

    fun getSession(appContext: Context): UserSession? {
        val sessionManager = SessionManager(appContext)
        return sessionManager.getSession()
    }

    fun getBlueskyApi(currentSession: UserSession): AuthenticatedXrpcBlueskyApi? {
        return try {
            val initialTokens = BlueskyAuthPlugin.Tokens(currentSession.accessToken, currentSession.refreshToken)
            val authTokensFlow = MutableStateFlow(initialTokens)

            val httpClient = HttpClient(OkHttp) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            Log.v("Ktor_Default", message)
                        }
                    }
                    level = LogLevel.HEADERS // Or LogLevel.NONE for less verbosity in production
                }
                defaultRequest {
                    url.takeFrom(currentSession.host ?: "https://bsky.social")
                }
                expectSuccess = true
            }

            AuthenticatedXrpcBlueskyApi(
                initialTokens = authTokensFlow.value,
                httpClient = httpClient,
            )
        } catch (e: Exception) {
            Log.e("FeedWorker", "Failed to initialize Bluesky API", e)
            null
        }
    }

    fun safelyGetObjectBox(appContext: Context): BoxStore {
        return if (ObjectBox.store == null) {
            ObjectBox.init(appContext)
        } else {
            ObjectBox.store
        }
    }
}