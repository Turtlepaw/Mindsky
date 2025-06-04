package io.github.turtlepaw.mindsky.routes

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.atproto.server.CreateSessionRequest
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FeedDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.auth.UserSession
import io.github.turtlepaw.mindsky.di.LocalAuthTokensFlow
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import io.github.turtlepaw.mindsky.replaceCurrent
import kotlinx.coroutines.launch
import sh.christian.ozone.api.BlueskyAuthPlugin
import sh.christian.ozone.api.response.AtpResponse

@Destination<RootGraph>
@Composable
fun Login(navigator: DestinationsNavigator) {
    var username by remember { mutableStateOf("") }
    var appPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current // For the Intent

    // Access the API, SessionManager and AuthTokensFlow from CompositionLocals
    val api = LocalMindskyApi.current
    val sessionManager = LocalSessionManager.current
    val authTokensFlow = LocalAuthTokensFlow.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Login to Bluesky",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Handle or DID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = appPassword,
            onValueChange = { appPassword = it },
            label = { Text("App Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            val request = CreateSessionRequest(
                                identifier = username,
                                password = appPassword
                            )
                            when (val result = api.createSession(request)) {
                                is AtpResponse.Success -> {
                                    val newSession = UserSession(
                                        did = result.response.did.did,
                                        handle = result.response.handle.handle,
                                        accessToken = result.response.accessJwt,
                                        refreshToken = result.response.refreshJwt
                                    )
                                    // 1. Save the full session to SessionManager
                                    sessionManager.saveSession(newSession)

                                    // 2. Update the shared authTokensFlow
                                    authTokensFlow.value = BlueskyAuthPlugin.Tokens(
                                        auth = newSession.accessToken,
                                        refresh = newSession.refreshToken
                                    )

                                    isLoading = false
                                    navigator.replaceCurrent(
                                        FeedDestination
                                    )
                                }

                                is AtpResponse.Failure -> {
                                    val error = result.error
                                    errorMessage =
                                        error?.message ?: "Login failed: An error occurred."
                                    isLoading = false
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage =
                                "Login failed: ${e.localizedMessage ?: "An unexpected error occurred"}"
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login")
            }

            OutlinedButton(
                onClick = {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        "https://bsky.app/settings/app-passwords".toUri()
                    )
                    context.startActivity(browserIntent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create an app password")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open in new")
            }
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }
    }
}
