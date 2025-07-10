package io.github.turtlepaw.mindsky.di

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.turtlepaw.mindsky.cache.LabelManager

val LocalLabelManager = staticCompositionLocalOf<LabelManager> {
    error("LabelManager not provided")
}