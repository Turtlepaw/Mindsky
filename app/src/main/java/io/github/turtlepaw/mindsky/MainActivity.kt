package io.github.turtlepaw.mindsky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember // Keep this
import androidx.compose.ui.platform.LocalContext // Added
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.work.WorkManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.animations.defaults.DefaultFadingTransitions
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.FeedDestination
import com.ramcosta.composedestinations.generated.destinations.LoginDestination
import com.ramcosta.composedestinations.generated.destinations.OnboardingDestination
import com.ramcosta.composedestinations.rememberNavHostEngine
import com.ramcosta.composedestinations.spec.DestinationStyle
import io.github.turtlepaw.mindsky.auth.SessionManager
// import io.github.turtlepaw.mindsky.auth.UserSession // Not directly used here anymore
import io.github.turtlepaw.mindsky.di.LocalAuthTokensFlow
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import io.github.turtlepaw.mindsky.logic.FeedWorker.Companion.enqueueFeedWorkers
import io.github.turtlepaw.mindsky.ui.theme.MindskyTheme

object DefaultSlideFadeTransitions : NavHostAnimatedDestinationStyle() {
    private val fastOutExtraSlowIn = CubicBezierEasing(0.05f, 0f, 0.133333f, 1f)
    private val slideDistancePx = 96

    private val enterDuration = 240
    private val exitDuration = 210

    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { slideDistancePx },
            animationSpec = tween(durationMillis = enterDuration, easing = fastOutExtraSlowIn)
        ) + fadeIn(
            animationSpec = tween(durationMillis = enterDuration, easing = LinearEasing)
        )
    }

    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { -slideDistancePx },
            animationSpec = tween(durationMillis = exitDuration, easing = fastOutExtraSlowIn)
        ) + fadeOut(
            animationSpec = tween(durationMillis = exitDuration, easing = LinearEasing)
        )
    }

    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { -slideDistancePx },
            animationSpec = tween(durationMillis = enterDuration, easing = fastOutExtraSlowIn)
        ) + fadeIn(
            animationSpec = tween(durationMillis = enterDuration, easing = LinearEasing)
        )
    }

    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { slideDistancePx },
            animationSpec = tween(durationMillis = exitDuration, easing = fastOutExtraSlowIn)
        ) + fadeOut(
            animationSpec = tween(durationMillis = exitDuration, easing = LinearEasing)
        )
    }
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get MindskyApplication instance
        val mindskyApplication = applicationContext as MindskyApplication

        val sessionManager =
            SessionManager(applicationContext) // SessionManager can still be local if preferred

        val startRoute = if (sessionManager.getSession() != null) {
            FeedDestination
        } else {
            OnboardingDestination
        }

        setContent {
            val rememberedSessionManager = remember { sessionManager }
            val notifications = rememberPermissionState(
                android.Manifest.permission.POST_NOTIFICATIONS
            )

            LaunchedEffect(Unit) {
                if (!notifications.status.isGranted) {
                    notifications.launchPermissionRequest()
                }

                WorkManager.getInstance(this@MainActivity).enqueueFeedWorkers()
            }

            // Get API and authTokensFlow from MindskyApplication
            val blueskyApi = mindskyApplication.blueskyApi
            val authTokensFlow = mindskyApplication.authTokensFlow

            CompositionLocalProvider(
                LocalMindskyApi provides blueskyApi, // Use API from Application
                LocalSessionManager provides rememberedSessionManager,
                LocalAuthTokensFlow provides authTokensFlow // Use flow from Application
            ) {
                MindskyTheme {
                    DestinationsNavHost(
                        navGraph = NavGraphs.root,
                        start = startRoute,
                        defaultTransitions = DefaultSlideFadeTransitions
                    )
                }
            }
        }
    }

}
