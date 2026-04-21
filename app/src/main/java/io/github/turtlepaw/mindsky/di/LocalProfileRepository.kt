package io.github.turtlepaw.mindsky.di

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.turtlepaw.mindsky.repositories.ProfileRepository

val LocalProfileRepository = staticCompositionLocalOf<ProfileRepository> {
    error("ProfileRepository not provided")
}
