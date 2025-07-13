package io.github.turtlepaw.mindsky.routes.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.LocalPreferences
import io.github.turtlepaw.mindsky.utils.StringComposable
import me.zhanghai.compose.preference.switchPreference

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun Appearance(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val preferences = LocalPreferences.current

    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(navigator, R.string.appearance)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            switchPreference(
                key = preferences.getKey(AppPrefs.DarkTheme),
                title = {
                    R.string.dark_theme_dark.StringComposable()
                },
                summary = {
                    R.string.dark_theme_dark_description.StringComposable()
                },
                defaultValue = AppPrefs.DarkTheme.defaultValue
            )
            switchPreference(
                key = preferences.getKey(AppPrefs.ShowLabelerAvatars),
                title = {
                    R.string.show_avatar_labelers.StringComposable()
                },
                summary = {
                    R.string.show_avatar_labelers_description.StringComposable()
                },
                defaultValue = AppPrefs.ShowLabelerAvatars.defaultValue
            )
        }
    }
}
