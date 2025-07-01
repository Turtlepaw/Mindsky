package io.github.turtlepaw.mindsky.di

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.turtlepaw.mindsky.viewmodels.FeedViewModel

val LocalFeedModel = staticCompositionLocalOf<FeedViewModel> {
    error("FeedViewModel not provided")
}