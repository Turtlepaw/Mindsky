package io.github.turtlepaw.mindsky.di

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.turtlepaw.mindsky.auth.SessionManager
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi

val LocalMindskyApi = staticCompositionLocalOf<AuthenticatedXrpcBlueskyApi> {
    error("XrpcBlueskyApi not provided")
}

val LocalSessionManager = staticCompositionLocalOf<SessionManager> {
    error("SessionManager not provided")
}
