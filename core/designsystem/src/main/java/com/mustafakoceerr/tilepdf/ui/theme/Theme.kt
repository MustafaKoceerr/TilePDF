package com.mustafakoceerr.tilepdf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.mustafakoceerr.thememanager.ThemeSettings
import com.mustafakoceerr.thememanager.compose.ManagedTheme
import com.mustafakoceerr.tilepdf.model.AppPalette

internal val VioletDark = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

internal val VioletLight = lightColorScheme(
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

/**
 * The app theme in the user's choice of light, dark or system and [palette],
 * with Material You colours instead when they turned it on (Android 12+); the
 * default follows the system with TilePDF's own violet, which is also what
 * previews show.
 */
@Composable
fun TilePDFTheme(
    settings: ThemeSettings = ThemeSettings(),
    palette: AppPalette = AppPalette.Violet,
    content: @Composable () -> Unit,
) {
    ManagedTheme(
        settings = settings,
        lightColorScheme = palette.lightScheme,
        darkColorScheme = palette.darkScheme,
        typography = Typography,
        content = content,
    )
}
