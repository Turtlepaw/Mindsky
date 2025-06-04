package io.github.turtlepaw.mindsky.di

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.turtlepaw.mindsky.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.BlueskyAuthPlugin

val LocalMindskyApi = staticCompositionLocalOf<AuthenticatedXrpcBlueskyApi> {
    error("XrpcBlueskyApi not provided")
}

val LocalSessionManager = staticCompositionLocalOf<SessionManager> {
    error("SessionManager not provided")
}

// Added for sharing the auth tokens flow
val LocalAuthTokensFlow = staticCompositionLocalOf<MutableStateFlow<BlueskyAuthPlugin.Tokens?>> {
    error("AuthTokensFlow not provided")
}
