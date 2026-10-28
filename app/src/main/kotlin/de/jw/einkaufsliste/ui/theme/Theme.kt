package de.jw.einkaufsliste.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0B4),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF52634F),
    tertiary = Color(0xFF38656A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD49A),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF145218),
    onPrimaryContainer = Color(0xFFB9F0B4),
    secondary = Color(0xFFB9CCB4),
    tertiary = Color(0xFFA0CFD4),
)

/** Material 3 theme; uses the wallpaper colors (Material You) on Android 12+, green otherwise. */
@Composable
fun EinkaufTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
