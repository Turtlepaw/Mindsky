package io.github.turtlepaw.mindsky.routes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FeedDestination
import com.ramcosta.composedestinations.generated.destinations.LoginDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import kotlinx.coroutines.launch
import sh.christian.ozone.BlueskyApi
import com.atproto.server.CreateSessionRequest // Assuming this might be used or adapted
import com.atproto.server.CreateSessionResponse // Expected response type for session
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.response.AtpResponse
import java.security.MessageDigest
import java.security.SecureRandom

// --- OAuth & PKCE Configuration ---
// TODO: Replace with your actual client ID from Bluesky app registration
private const val YOUR_CLIENT_ID = "YOUR_APP_CLIENT_ID"
private const val REDIRECT_URI = "mindsky://callback"
// TODO: Define the scopes your application needs. Example:
private const val REQUESTED_SCOPES = "com.atproto.identity com.atproto.repo" // Verify actual scopes
private const val BLUESKY_AUTH_BASE_URL = "https://bsky.app/oauth/authorize" // Verify official base URL
private const val TAG = "LoginRoute"

// --- Helper Functions for PKCE and CSRF ---
private fun generatePkceCodeVerifier(): String {
    val sr = SecureRandom()
    val code = ByteArray(32)
    sr.nextBytes(code)
    return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

private fun generatePkceCodeChallenge(verifier: String): String {
    val bytes = verifier.toByteArray(Charsets.US_ASCII)
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

private fun generateCsrfState(): String {
    val sr = SecureRandom()
    val randomBytes = ByteArray(16)
    sr.nextBytes(randomBytes)
    return Base64.encodeToString(randomBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

@Destination<RootGraph>(
    deepLinks = [
        DeepLink(uriPattern = "$REDIRECT_URI?code={code}&state={state}")
    ]
)
@Composable
fun Login(
    navigator: DestinationsNavigator,
    code: String? = null, // Authorization code from Bluesky callback
    state: String? = null  // State parameter from Bluesky callback (for CSRF verification)
) {
    val context = LocalContext.current

}
