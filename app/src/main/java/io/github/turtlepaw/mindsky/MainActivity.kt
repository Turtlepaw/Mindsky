package io.github.turtlepaw.mindsky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.FeedDestination
import com.ramcosta.composedestinations.generated.destinations.LoginDestination
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import io.github.turtlepaw.mindsky.di.LocalAuthTokensFlow
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import io.github.turtlepaw.mindsky.ui.theme.MindskyTheme
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.Json
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi.Companion.authenticated
import sh.christian.ozone.api.BlueskyAuthPlugin

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionManager = SessionManager(applicationContext)

        val startRoute = if (sessionManager.getSession() != null) {
            FeedDestination
        } else {
            LoginDestination
        }

        setContent {
            val rememberedSessionManager = remember { sessionManager }

            val authTokensFlow = remember {
                val currentSession = rememberedSessionManager.getSession()
                val initialTokens = if (currentSession != null) {
                    BlueskyAuthPlugin.Tokens(
                        currentSession.accessToken,
                        currentSession.refreshToken
                    )
                } else {
                    null
                }
                MutableStateFlow(initialTokens)
            }

            val api = remember {
                AuthenticatedXrpcBlueskyApi(
                    initialTokens = authTokensFlow.value
                )
            }

            CompositionLocalProvider(
                LocalMindskyApi provides api,
                LocalSessionManager provides rememberedSessionManager,
                LocalAuthTokensFlow provides authTokensFlow // Provide the flow
            ) {
                MindskyTheme {
                    DestinationsNavHost(
                        navGraph = NavGraphs.root,
                        start = startRoute
                    )
                }
            }
        }
    }
}
