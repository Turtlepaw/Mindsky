package io.github.turtlepaw.mindsky.preferences

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

val LocalPreferences = compositionLocalOf<PreferenceManager> {
    error("No PreferenceProvider found")
}

@Composable
fun PreferenceProvider(
    context: Context = LocalContext.current,
    content: @Composable () -> Unit
) {
    val preferenceManager = remember { PreferenceManager(context) }

    DisposableEffect(preferenceManager) {
        onDispose { preferenceManager.cleanup() }
    }

    CompositionLocalProvider(
        LocalPreferences provides preferenceManager
    ) {
        content()
    }
}

// Composable helpers
@Composable
fun <T> rememberPreference(pref: Preference<T>): State<T> {
    val manager = LocalPreferences.current
    return manager.getStateFlow(pref).collectAsState()
}

@Composable
fun <T> rememberPreferenceValue(pref: Preference<T>): T {
    val manager = LocalPreferences.current
    val state by manager.getStateFlow(pref).collectAsState()
    return state
}