package io.github.turtlepaw.mindsky.di

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("NavController not provided")
}
