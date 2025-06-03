package io.github.turtlepaw.atproto.authentication

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
// It's good practice to add Log imports if you use Log.d, Log.e etc.
// import android.util.Log

class AtpAuthentication(private val context: Context) {

    private val authService: AuthorizationService = AuthorizationService(context)

    // TODO: Replace with your actual client ID and redirect URI
    // TODO: Consider storing these in a more secure or configurable location
    private val oauthClientId = "YOUR_CLIENT_ID"
    private val oauthRedirectUri = "YOUR_REDIRECT_URI" // e.g., "io.github.turtlepaw.mindsky:/oauth2redirect"

    // TODO: Define the scopes your application needs
    private val oauthScopes = listOf("email", "profile", "openid") // Example scopes

    // TODO: Make PDS_HOST configurable if necessary
    private val pdsHost = "bsky.social" // Default PDS for Bluesky

    private fun getAuthorizationServiceConfiguration(): AuthorizationServiceConfiguration {
        val authEndpoint = "https://${pdsHost}/xrpc/com.atproto.server.oauthGetAuthorize".toUri()
        val tokenEndpoint = "https://${pdsHost}/xrpc/com.atproto.server.oauthRequestToken".toUri()
        return AuthorizationServiceConfiguration(authEndpoint, tokenEndpoint)
    }

    fun createAuthRequestIntent(): Intent {
        val serviceConfig = getAuthorizationServiceConfiguration()
        val authRequestBuilder = AuthorizationRequest.Builder(
            serviceConfig,
            oauthClientId,
            ResponseTypeValues.CODE,
            oauthRedirectUri.toUri()
        )

        // TODO: Implement PKCE for enhanced security
        // val codeVerifier = CodeVerifierUtil.generateRandomCodeVerifier()
        // val codeChallenge = CodeVerifierUtil.deriveCodeChallenge(codeVerifier)
        // authRequestBuilder.setCodeVerifier(codeVerifier, codeChallenge, CodeVerifierUtil.CODE_CHALLENGE_METHOD_S256)

        val authRequest = authRequestBuilder
            .setScope(oauthScopes.joinToString(" "))
            .build()

        return authService.getAuthorizationRequestIntent(authRequest)
    }

    suspend fun handleAuthorizationResponse(intent: Intent): AuthState {
        val resp = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        return suspendCancellableCoroutine { continuation ->
            if (resp != null) {
                // Authorization successful, exchange for tokens
                authService.performTokenRequest(
                    resp.createTokenExchangeRequest(),
                    ClientSecretBasicAuthentication("YOUR_CLIENT_SECRET") // If client secret is required
                ) { tokenResponse, tokenEx ->
                    val authState = AuthState(resp, tokenResponse, tokenEx)
                    if (tokenResponse != null) {
                        // Token exchange successful
                        continuation.resume(authState)
                    } else {
                        continuation.resumeWithException(tokenEx ?: AuthorizationException.GeneralErrors.ID_TOKEN_VALIDATION_ERROR)
                    }
                }
            } else {
                // Authorization failed
                continuation.resumeWithException(ex ?: AuthorizationException.GeneralErrors.INVALID_DISCOVERY_DOCUMENT)
            }
        }
    }

    suspend fun refreshAccessToken(authState: AuthState): AuthState {
        return if (authState.needsTokenRefresh) {
            suspendCancellableCoroutine { continuation ->
                authState.performActionWithFreshTokens(authService) { _, _, ex ->
                    if (ex != null) {
                        // Refresh failed
                        continuation.resumeWithException(ex)
                    } else {
                        // Tokens refreshed successfully
                        continuation.resume(authState) // authState is updated automatically
                    }
                }
            }
        } else {
            // No refresh needed
            authState
        }
    }

    fun dispose() {
        authService.dispose()
    }

    companion object {
        private val HANDLE_REGEX = Regex("^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\\\.)+[a-zA-Z]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\$")
        private val DID_REGEX = Regex("^did:[a-z]+:[a-zA-Z0-9._:%-]*[a-zA-Z0-9._-]\\$")

        fun isValidHandle(handle: String): Boolean {
            return HANDLE_REGEX.matches(handle)
        }

        fun isValidDid(did: String): Boolean {
            return DID_REGEX.matches(did)
        }
    }
}

// Helper class if you are using client_secret_basic authentication method
class ClientSecretBasicAuthentication(private val clientSecret: String) : net.openid.appauth.ClientAuthentication {
    override fun getRequestHeaders(clientId: String): MutableMap<String, String>? {
        return null // Not used for client_secret_basic
    }

    override fun getRequestParameters(clientId: String): MutableMap<String, String>? {
        val params = mutableMapOf<String, String>()
        params["client_id"] = clientId
        params["client_secret"] = clientSecret
        return params
    }
}
