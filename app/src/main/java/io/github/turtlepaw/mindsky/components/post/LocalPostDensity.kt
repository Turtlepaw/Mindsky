package io.github.turtlepaw.mindsky.components.post

import androidx.compose.runtime.staticCompositionLocalOf

val LocalPostDensity = staticCompositionLocalOf<PostDensity> {
    error("PostDensity not provided")
}