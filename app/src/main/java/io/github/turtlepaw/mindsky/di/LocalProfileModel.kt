package io.github.turtlepaw.mindsky.di

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.turtlepaw.mindsky.viewmodels.ProfileViewModel

val LocalProfileModel = staticCompositionLocalOf<ProfileViewModel> {
    error("ProfileViewModel not provided")
}