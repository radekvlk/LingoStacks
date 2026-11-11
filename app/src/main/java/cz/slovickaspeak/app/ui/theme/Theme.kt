package cz.slovickaspeak.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Global reactive theme state (persisted in ProgressStateHolder)
var isDarkThemeActive by mutableStateOf(false)

// Apple iOS Cupertino System Palette
val AppleBlue = Color(0xFF0A84FF)
val AppleBlueDark = Color(0xFF0051D5)
val AppleBlueTint: Color
    get() = if (isDarkThemeActive) Color(0xFF0E294B) else Color(0xFFEBF4FF)

val AppleGreen = Color(0xFF34C759)
val AppleGreenDark = Color(0xFF248A3D)
val AppleGreenTint: Color
    get() = if (isDarkThemeActive) Color(0xFF0F331C) else Color(0xFFEAF9EE)

val AppleRed = Color(0xFFFF3B30)
val AppleRedDark = Color(0xFFD70015)
val AppleRedTint: Color
    get() = if (isDarkThemeActive) Color(0xFF3B1416) else Color(0xFFFFECEB)

val AppleOrange = Color(0xFFFF9F0A)
val AppleIndigo = Color(0xFF5E5CE6)
val ApplePurple = Color(0xFFBF5AF2)

// Backward-compatible aliases used across screens
val DuoGreen = AppleGreen
val DuoGreenDark = AppleGreenDark
val DuoGreenBg: Color
    get() = AppleGreenTint

val DuoRed = AppleRed
val DuoRedDark = AppleRedDark
val DuoRedBg: Color
    get() = AppleRedTint

val DuoBlue = AppleBlue
val DuoBlueDark = AppleBlueDark
val DuoBlueBg: Color
    get() = AppleBlueTint

val DuoOrange = AppleOrange
val DuoPurple = ApplePurple

// iOS System Grouped Background, Cards & Labels (Reactive Dark / Light Mode)
val SurfaceBg: Color
    get() = if (isDarkThemeActive) Color(0xFF000000) else Color(0xFFF2F2F7)

val CardSurface: Color
    get() = if (isDarkThemeActive) Color(0xFF1C1C1E) else Color.White

val CardElevatedSurface: Color
    get() = if (isDarkThemeActive) Color(0xFF3A3A3C) else Color.White

val AppleSecondaryFill: Color
    get() = if (isDarkThemeActive) Color(0xFF161618) else Color(0xFFE5E5EA)

val CardBorderGray: Color
    get() = if (isDarkThemeActive) Color(0xFF38383A) else Color(0xFFD1D1D6)

val TextDark: Color
    get() = if (isDarkThemeActive) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)

val TextMuted: Color
    get() = if (isDarkThemeActive) Color(0xFF98989F) else Color(0xFF8E8E93)

private val LightColorScheme = lightColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    secondary = AppleIndigo,
    onSecondary = Color.White,
    error = AppleRed,
    background = Color(0xFFF2F2F7),
    surface = Color.White,
    onBackground = Color(0xFF1C1C1E),
    onSurface = Color(0xFF1C1C1E)
)

private val DarkColorScheme = darkColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    secondary = AppleIndigo,
    onSecondary = Color.White,
    error = AppleRed,
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    onBackground = Color(0xFFF2F2F7),
    onSurface = Color(0xFFF2F2F7)
)

@Composable
fun SlovickaSpeakTheme(
    darkTheme: Boolean = isDarkThemeActive,
    content: @Composable () -> Unit
) {
    isDarkThemeActive = darkTheme
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
