package io.github.turtlepaw.mindsky.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.LocalPreferences
import io.github.turtlepaw.mindsky.preferences.rememberPreference

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

private val LatteColorScheme = lightColorScheme(
    primary = LatteBlue,
    onPrimary = LatteBase,
    primaryContainer = LatteBlue,
    onPrimaryContainer = LatteBase,
    secondary = LattePink,
    onSecondary = LatteBase,
    secondaryContainer = LattePink,
    onSecondaryContainer = LatteBase,
    tertiary = LatteTeal,
    onTertiary = LatteBase,
    tertiaryContainer = LatteTeal,
    onTertiaryContainer = LatteBase,
    background = LatteBase,
    onBackground = LatteText,
    surface = LatteBase,
    onSurface = LatteText,
    surfaceVariant = LatteSurface0,
    onSurfaceVariant = LatteText,
    surfaceTint = LatteBlue,
    inverseSurface = LatteText,
    inverseOnSurface = LatteBase,
    error = LatteRed,
    onError = LatteBase,
    errorContainer = LatteRed,
    onErrorContainer = LatteBase,
    outline = LatteOverlay0,
    outlineVariant = LatteOverlay0,
    scrim = LatteOverlay1,
    surfaceBright = LatteSurface2,
    surfaceContainer = LatteSurface0,
    surfaceContainerHigh = LatteSurface1,
    surfaceContainerHighest = LatteSurface2,
    surfaceContainerLow = LatteMantle,
    surfaceContainerLowest = LatteCrust,
    surfaceDim = LatteSurface0,
)

private val MochaColorScheme = darkColorScheme(
    primary = MochaBlue,
    onPrimary = MochaBase,
    primaryContainer = MochaBlue,
    onPrimaryContainer = MochaBase,
    secondary = MochaPink,
    onSecondary = MochaBase,
    secondaryContainer = MochaPink,
    onSecondaryContainer = MochaBase,
    tertiary = MochaTeal,
    onTertiary = MochaBase,
    tertiaryContainer = MochaTeal,
    onTertiaryContainer = MochaBase,
    background = MochaBase,
    onBackground = MochaText,
    surface = MochaBase,
    onSurface = MochaText,
    surfaceVariant = MochaSurface0,
    onSurfaceVariant = MochaText,
    surfaceTint = MochaBlue,
    inverseSurface = MochaText,
    inverseOnSurface = MochaBase,
    error = MochaRed,
    onError = MochaBase,
    errorContainer = MochaRed,
    onErrorContainer = MochaBase,
    outline = MochaOverlay0,
    outlineVariant = MochaOverlay0,
    scrim = MochaOverlay1,
    surfaceBright = MochaSurface2,
    surfaceContainer = MochaSurface0,
    surfaceContainerHigh = MochaSurface1,
    surfaceContainerHighest = MochaSurface2,
    surfaceContainerLow = MochaMantle,
    surfaceContainerLowest = MochaCrust,
    surfaceDim = MochaSurface0,
)

private val FrappeColorScheme = darkColorScheme(
    primary = FrappeBlue,
    onPrimary = FrappeBase,
    primaryContainer = FrappeBlue,
    onPrimaryContainer = FrappeBase,
    secondary = FrappePink,
    onSecondary = FrappeBase,
    secondaryContainer = FrappePink,
    onSecondaryContainer = FrappeBase,
    tertiary = FrappeTeal,
    onTertiary = FrappeBase,
    tertiaryContainer = FrappeTeal,
    onTertiaryContainer = FrappeBase,
    background = FrappeBase,
    onBackground = FrappeText,
    surface = FrappeBase,
    onSurface = FrappeText,
    surfaceVariant = FrappeSurface0,
    onSurfaceVariant = FrappeText,
    surfaceTint = FrappeBlue,
    inverseSurface = FrappeText,
    inverseOnSurface = FrappeBase,
    error = FrappeRed,
    onError = FrappeBase,
    errorContainer = FrappeRed,
    onErrorContainer = FrappeBase,
    outline = FrappeOverlay0,
    outlineVariant = FrappeOverlay0,
    scrim = FrappeOverlay1,
    surfaceBright = FrappeSurface2,
    surfaceContainer = FrappeSurface0,
    surfaceContainerHigh = FrappeSurface1,
    surfaceContainerHighest = FrappeSurface2,
    surfaceContainerLow = FrappeMantle,
    surfaceContainerLowest = FrappeCrust,
    surfaceDim = FrappeSurface0,
)

private val MacchiatoColorScheme = darkColorScheme(
    primary = MacchiatoBlue,
    onPrimary = MacchiatoBase,
    primaryContainer = MacchiatoBlue,
    onPrimaryContainer = MacchiatoBase,
    secondary = MacchiatoPink,
    onSecondary = MacchiatoBase,
    secondaryContainer = MacchiatoPink,
    onSecondaryContainer = MacchiatoBase,
    tertiary = MacchiatoTeal,
    onTertiary = MacchiatoBase,
    tertiaryContainer = MacchiatoTeal,
    onTertiaryContainer = MacchiatoBase,
    background = MacchiatoBase,
    onBackground = MacchiatoText,
    surface = MacchiatoBase,
    onSurface = MacchiatoText,
    surfaceVariant = MacchiatoSurface0,
    onSurfaceVariant = MacchiatoText,
    surfaceTint = MacchiatoBlue,
    inverseSurface = MacchiatoText,
    inverseOnSurface = MacchiatoBase,
    error = MacchiatoRed,
    onError = MacchiatoBase,
    errorContainer = MacchiatoRed,
    onErrorContainer = MacchiatoBase,
    outline = MacchiatoOverlay0,
    outlineVariant = MacchiatoOverlay0,
    scrim = MacchiatoOverlay1,
    surfaceBright = MacchiatoSurface2,
    surfaceContainer = MacchiatoSurface0,
    surfaceContainerHigh = MacchiatoSurface1,
    surfaceContainerHighest = MacchiatoSurface2,
    surfaceContainerLow = MacchiatoMantle,
    surfaceContainerLowest = MacchiatoCrust,
    surfaceDim = MacchiatoSurface0,
)

@Composable
fun getCatppuccinAccentColor(variant: String, accent: String): Color {
    return when (variant) {
        "Latte" -> when (accent) {
            "Rosewater" -> LatteRosewater
            "Flamingo" -> LatteFlamingo
            "Pink" -> LattePink
            "Mauve" -> LatteMauve
            "Red" -> LatteRed
            "Maroon" -> LatteMaroon
            "Peach" -> LattePeach
            "Yellow" -> LatteYellow
            "Green" -> LatteGreen
            "Teal" -> LatteTeal
            "Sky" -> LatteSky
            "Sapphire" -> LatteSapphire
            "Blue" -> LatteBlue
            "Lavender" -> LatteLavender
            else -> LatteBlue
        }
        "Frappé" -> when (accent) {
            "Rosewater" -> FrappeRosewater
            "Flamingo" -> FrappeFlamingo
            "Pink" -> FrappePink
            "Mauve" -> FrappeMauve
            "Red" -> FrappeRed
            "Maroon" -> FrappeMaroon
            "Peach" -> FrappePeach
            "Yellow" -> FrappeYellow
            "Green" -> FrappeGreen
            "Teal" -> FrappeTeal
            "Sky" -> FrappeSky
            "Sapphire" -> FrappeSapphire
            "Blue" -> FrappeBlue
            "Lavender" -> FrappeLavender
            else -> FrappeBlue
        }
        "Macchiato" -> when (accent) {
            "Rosewater" -> MacchiatoRosewater
            "Flamingo" -> MacchiatoFlamingo
            "Pink" -> MacchiatoPink
            "Mauve" -> MacchiatoMauve
            "Red" -> MacchiatoRed
            "Maroon" -> MacchiatoMaroon
            "Peach" -> MacchiatoPeach
            "Yellow" -> MacchiatoYellow
            "Green" -> MacchiatoGreen
            "Teal" -> MacchiatoTeal
            "Sky" -> MacchiatoSky
            "Sapphire" -> MacchiatoSapphire
            "Blue" -> MacchiatoBlue
            "Lavender" -> MacchiatoLavender
            else -> MacchiatoBlue
        }
        else -> when (accent) { // Mocha
            "Rosewater" -> MochaRosewater
            "Flamingo" -> MochaFlamingo
            "Pink" -> MochaPink
            "Mauve" -> MochaMauve
            "Red" -> MochaRed
            "Maroon" -> MochaMaroon
            "Peach" -> MochaPeach
            "Yellow" -> MochaYellow
            "Green" -> MochaGreen
            "Teal" -> MochaTeal
            "Sky" -> MochaSky
            "Sapphire" -> MochaSapphire
            "Blue" -> MochaBlue
            "Lavender" -> MochaLavender
            else -> MochaBlue
        }
    }
}

@Composable
fun MindskyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkMode by rememberPreference(AppPrefs.DarkTheme)
    val useCatppuccin by rememberPreference(AppPrefs.UseCatppuccinTheme)
    val catppuccinVariant by rememberPreference(AppPrefs.CatppuccinVariant)
    val catppuccinAccent by rememberPreference(AppPrefs.CatppuccinAccent)
    
    val preferences = LocalPreferences.current
    val colorScheme = when {
        useCatppuccin -> {
             val accentColor = if (darkTheme) {
                 getCatppuccinAccentColor(catppuccinVariant, catppuccinAccent)
             } else {
                 getCatppuccinAccentColor("Latte", catppuccinAccent)
             }
             
             if (darkTheme) {
                 when (catppuccinVariant) {
                     "Frappé" -> FrappeColorScheme.copy(primary = accentColor)
                     "Macchiato" -> MacchiatoColorScheme.copy(primary = accentColor)
                     else -> MochaColorScheme.copy(primary = accentColor)
                 }
             } else {
                 LatteColorScheme.copy(primary = accentColor)
             }
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }.run {
        if (darkMode && darkTheme) {
            copy(
                surface = Color.Black,
                background = Color.Black,
            )
        } else copy()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography().withFontFamily(default),
        content = content
    )
}