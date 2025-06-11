package io.github.turtlepaw.mindsky

// import io.github.turtlepaw.mindsky.auth.UserSession // Not directly used here anymore
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.work.WorkManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.FeedDestination
import com.ramcosta.composedestinations.generated.destinations.OnboardingDestination
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.di.LocalAuthTokensFlow
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalProfileModel
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import io.github.turtlepaw.mindsky.logic.FeedWorker.Companion.enqueueFeedWorkers
import io.github.turtlepaw.mindsky.repositories.ProfileRepository
import io.github.turtlepaw.mindsky.ui.theme.MindskyTheme
import io.github.turtlepaw.mindsky.viewmodels.ProfileViewModel
import io.github.turtlepaw.mindsky.viewmodels.ProfileViewModelFactory

object DefaultSlideFadeTransitions : NavHostAnimatedDestinationStyle() {
    private val fastOutExtraSlowIn = CubicBezierEasing(0.05f, 0f, 0.133333f, 1f)
    private val slideDistancePx = 96

    private val enterDuration = 340
    private val exitDuration = 200

    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(
            animationSpec = tween(durationMillis = enterDuration, easing = LinearEasing)
        ) + slideInHorizontally(
            initialOffsetX = { slideDistancePx },
            animationSpec = tween(durationMillis = enterDuration, easing = fastOutExtraSlowIn)
        )
    }

    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(
            animationSpec = tween(durationMillis = exitDuration, easing = LinearEasing)
        ) + slideOutHorizontally(
            targetOffsetX = { -slideDistancePx },
            animationSpec = tween(durationMillis = exitDuration, easing = fastOutExtraSlowIn)
        )
    }

    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(
            animationSpec = tween(durationMillis = enterDuration, easing = LinearEasing)
        ) + slideInHorizontally(
            initialOffsetX = { -slideDistancePx },
            animationSpec = tween(durationMillis = enterDuration, easing = fastOutExtraSlowIn)
        )
    }

    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(
            animationSpec = tween(durationMillis = exitDuration, easing = LinearEasing)
        ) + slideOutHorizontally(
            targetOffsetX = { slideDistancePx },
            animationSpec = tween(durationMillis = exitDuration, easing = fastOutExtraSlowIn)
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

            val profileRepository = ProfileRepository(blueskyApi)
            val viewModel: ProfileViewModel = viewModel(
                factory = ProfileViewModelFactory(
                    profileRepository,
                    rememberedSessionManager
                )
            )

            CompositionLocalProvider(
                LocalMindskyApi provides blueskyApi, // Use API from Application
                LocalSessionManager provides rememberedSessionManager,
                LocalAuthTokensFlow provides authTokensFlow,
                LocalProfileModel provides viewModel,
            ) {
                MindskyTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
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

}
