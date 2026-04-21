package io.github.turtlepaw.mindsky.routes.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.LocalPreferences
import io.github.turtlepaw.mindsky.preferences.rememberPreference
import io.github.turtlepaw.mindsky.utils.StringComposable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.switchPreference

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun Appearance(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val preferences = LocalPreferences.current
    val useCatppuccin by rememberPreference(AppPrefs.UseCatppuccinTheme)
    val catppuccinVariant by rememberPreference(AppPrefs.CatppuccinVariant)
    val catppuccinAccent by rememberPreference(AppPrefs.CatppuccinAccent)
    var showAccentDialog by remember { mutableStateOf(false) }

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
            switchPreference(
                key = preferences.getKey(AppPrefs.UseCatppuccinTheme),
                title = {
                    R.string.catppuccin_theme.StringComposable()
                },
                summary = {
                    R.string.catppuccin_theme_description.StringComposable()
                },
                defaultValue = AppPrefs.UseCatppuccinTheme.defaultValue
            )

            item {
                AnimatedVisibility(
                    visible = useCatppuccin,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.catppuccin_variant),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val variants = listOf("Mocha", "Frappé", "Macchiato")
                                variants.forEachIndexed { index, variant ->
                                    SegmentedButton(
                                        selected = (catppuccinVariant == variant),
                                        onClick = { preferences.set(AppPrefs.CatppuccinVariant, variant) },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = variants.size)
                                    ) {
                                        Text(variant)
                                    }
                                }
                            }
                        }
                        Preference(
                            title = {
                                R.string.catppuccin_accent.StringComposable()
                            },
                            summary = {
                                Text(text = catppuccinAccent)
                            },
                            onClick = {
                                showAccentDialog = true
                            }
                        )
                    }
                }
            }
        }

        if (showAccentDialog) {
            AlertDialog(
                onDismissRequest = { showAccentDialog = false },
                title = { Text(stringResource(R.string.catppuccin_accent)) },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val accents = listOf(
                            "Rosewater", "Flamingo", "Pink", "Mauve", "Red", "Maroon", 
                            "Peach", "Yellow", "Green", "Teal", "Sky", "Sapphire", 
                            "Blue", "Lavender"
                        )
                        items(accents.size) { index ->
                            val accent = accents[index]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        preferences.set(AppPrefs.CatppuccinAccent, accent)
                                        showAccentDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (accent == catppuccinAccent),
                                    onClick = null
                                )
                                Text(
                                    text = accent,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAccentDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
