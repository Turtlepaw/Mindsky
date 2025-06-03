package io.github.turtlepaw.mindsky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs // Assuming this is your generated NavGraphs
import com.ramcosta.composedestinations.generated.destinations.FeedDestination // Assuming this is your feed destination
import com.ramcosta.composedestinations.generated.destinations.LoginDestination // Assuming this is your login destination
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.ui.theme.MindskyTheme

class MainActivity : ComponentActivity() {
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(applicationContext)

        val startRoute = if (sessionManager.getSession() != null) {
            FeedDestination
        } else {
            LoginDestination
        }

        setContent {
            MindskyTheme {
                DestinationsNavHost(
                    navGraph = NavGraphs.root,
                    //start = startRoute
                )
            }
        }
    }
}
