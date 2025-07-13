package io.github.turtlepaw.mindsky.preferences

enum class DarkModePreference {
    Dim,
    Dark
}

object AppPrefs {
    val DarkTheme = BooleanPreference(
        key = "dark_theme",
        defaultValue = false,
    )

    val ShowLabelerAvatars = BooleanPreference(
        key = "show_labeler_avatars",
        defaultValue = true
    )

    val ALL = listOf(DarkTheme, ShowLabelerAvatars)
}