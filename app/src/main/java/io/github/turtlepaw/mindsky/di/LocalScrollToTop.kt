package io.github.turtlepaw.mindsky.di

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf

val LocalScrollToTop = staticCompositionLocalOf<MutableState<(() -> Unit)?>> {
    error("Scroll-to-top handler not provided")
}
