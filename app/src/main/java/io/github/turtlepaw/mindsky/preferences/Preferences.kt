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

    val UseCatppuccinTheme = BooleanPreference(
        key = "use_catppuccin_theme",
        defaultValue = false
    )

    val CatppuccinVariant = StringPreference(
        key = "catppuccin_variant",
        defaultValue = "Mocha"
    )

    val CatppuccinAccent = StringPreference(
        key = "catppuccin_accent",
        defaultValue = "Blue"
    )

    val DefaultFeed = IntPreference(
        key = "default_feed",
        defaultValue = 0
    )

    val ALL = listOf(DarkTheme, ShowLabelerAvatars, UseCatppuccinTheme, CatppuccinVariant, CatppuccinAccent, DefaultFeed)
}