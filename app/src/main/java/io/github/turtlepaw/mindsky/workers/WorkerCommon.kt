package io.github.turtlepaw.mindsky.workers

import android.content.Context
import android.util.Log
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.takeFrom
import io.objectbox.BoxStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.BlueskyAuthPlugin

object WorkerCommon {
    const val API_REQUEST_LIMIT = 100L // Standard API limit for pagination
    const val THERMAL_COOLDOWN_MS = 200L // Short delay for CPU cooling
    const val MAX_PAGES_TO_FETCH_LIKES = 10 // Limit to prevent excessive API calls
    const val PROGRESS = "progress"
    const val STAGE = "stage"

    private val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getSession(appContext: Context): UserSession? {
        val sessionManager = SessionManager(appContext)
        return sessionManager.getSession()
    }

    fun getBlueskyApi(sessionManager: SessionManager): AuthenticatedXrpcBlueskyApi? {
        val currentSession = sessionManager.sessionFlow.value ?: return null
        return try {
            val httpClient = HttpClient(OkHttp) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            Log.v("Ktor_Default", message)
                        }
                    }
                    level = LogLevel.HEADERS
                }
                install(BlueskyAuthPlugin) {
                    // observe session changes
                    workerScope.launch {
                        authTokens.collect {
                            Log.d("BlueskyAuthPlugin", "Auth tokens updated: $it")
                            val session = sessionManager.getSession()
                            if (it != null && session != null) {
                                sessionManager.saveSession(
                                    session.copy(
                                        accessToken = it.auth,
                                        refreshToken = it.refresh,
                                    )
                                )
                            }
                        }
                    }
                }
                defaultRequest {
                    url.takeFrom(currentSession.host)
                }
                expectSuccess = true
            }

            AuthenticatedXrpcBlueskyApi(
                httpClient,
                BlueskyAuthPlugin.Tokens(currentSession.accessToken, currentSession.refreshToken)
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
            ObjectBox.store!!
        }
    }
}