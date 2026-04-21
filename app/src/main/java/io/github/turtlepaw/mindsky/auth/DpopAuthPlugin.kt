package io.github.turtlepaw.mindsky.auth

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.util.AttributeKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DpopAuthPlugin private constructor(
    private val sessionManager: SessionManager,
    private val oauthClient: OAuthClient,
    private val dpopManager: DPoPManager,
    private val refreshBufferMs: Long
) {
    private val refreshMutex = Mutex()

    class Config {
        lateinit var sessionManager: SessionManager
        lateinit var oauthClient: OAuthClient
        lateinit var dpopManager: DPoPManager
        var refreshBufferMs: Long = 30_000L
    }

    companion object : HttpClientPlugin<Config, DpopAuthPlugin> {
        override val key: AttributeKey<DpopAuthPlugin> = AttributeKey("DpopAuthPlugin")

        override fun prepare(block: Config.() -> Unit): DpopAuthPlugin {
            val config = Config().apply(block)
            return DpopAuthPlugin(
                sessionManager = config.sessionManager,
                oauthClient = config.oauthClient,
                dpopManager = config.dpopManager,
                refreshBufferMs = config.refreshBufferMs
            )
        }

        override fun install(plugin: DpopAuthPlugin, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { request ->
                val baseRequest = HttpRequestBuilder().takeFrom(request)
                val session = plugin.sessionManager.getSession()
                    ?: return@intercept execute(baseRequest)

                var activeSession = plugin.ensureFreshSession(session)
                    ?: return@intercept execute(baseRequest)

                suspend fun sendWithDpop(
                    sessionToUse: UserSession,
                    nonce: String? = null
                ) = execute(HttpRequestBuilder().takeFrom(baseRequest).apply {
                    plugin.applyAuthHeaders(this, sessionToUse, nonce)
                })

                var call = sendWithDpop(activeSession)

                val nonce = call.response.headers["DPoP-Nonce"]
                if (call.response.status == HttpStatusCode.Unauthorized && nonce != null) {
                    call = sendWithDpop(activeSession, nonce)
                }

                if (call.response.status == HttpStatusCode.Unauthorized && plugin.isInvalidToken(call.response)) {
                    val refreshed = plugin.refreshSession(activeSession)
                    if (refreshed != null) {
                        activeSession = refreshed
                        call = sendWithDpop(activeSession)
                        val retryNonce = call.response.headers["DPoP-Nonce"]
                        if (call.response.status == HttpStatusCode.Unauthorized && retryNonce != null) {
                            call = sendWithDpop(activeSession, retryNonce)
                        }
                    }
                }

                call
            }
        }
    }

    private fun applyAuthHeaders(
        request: HttpRequestBuilder,
        session: UserSession,
        nonce: String?
    ) {
        request.headers.remove("Authorization")
        request.headers.remove("DPoP")
        val dpopProof = dpopManager.generateProof(
            request.method.value,
            request.url.buildString(),
            session.accessToken,
            nonce
        )
        val tokenType = if (session.tokenType.isBlank()) "DPoP" else session.tokenType
        request.headers.append("Authorization", "$tokenType ${session.accessToken}")
        request.headers.append("DPoP", dpopProof)
    }

    private suspend fun ensureFreshSession(session: UserSession): UserSession? {
        if (!isExpiringSoon(session)) {
            return session
        }

        return refreshMutex.withLock {
            val latest = sessionManager.getSession()
            if (latest != null && !isExpiringSoon(latest)) {
                return@withLock latest
            }
            refreshSession(latest ?: session)
        }
    }

    private fun isExpiringSoon(session: UserSession): Boolean {
        val expiresAt = session.expiresAt ?: return false
        return System.currentTimeMillis() >= expiresAt - refreshBufferMs
    }

    private suspend fun refreshSession(session: UserSession): UserSession? {
        return try {
            oauthClient.refreshToken(session)
        } catch (e: Exception) {
            Log.e("DpopAuthPlugin", "Token refresh failed; clearing session.", e)
            sessionManager.clearSession()
            null
        }
    }

    private suspend fun isInvalidToken(response: HttpResponse): Boolean {
        return try {
            val body = response.bodyAsText()
            body.contains("invalid_token", ignoreCase = true) ||
                body.contains("Token has been revoked", ignoreCase = true) ||
                body.contains("Invalid refresh token", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
}
