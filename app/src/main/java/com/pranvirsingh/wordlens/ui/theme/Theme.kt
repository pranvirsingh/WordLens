package com.pranvirsingh.wordlens.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** The colour of a real highlighter pen — the app's signature accent. */
val Highlighter = Color(0xFFFFD54A)

private val PaperLight = lightColorScheme(
    primary = Color(0xFF2F4B8A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE4FA),
    onPrimaryContainer = Color(0xFF14295A),
    secondary = Color(0xFF8E5410),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFBE3C4),
    onSecondaryContainer = Color(0xFF3B2200),
    background = Color(0xFFF7F2E8),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFCF5),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEFE7D8),
    onSurfaceVariant = Color(0xFF5E5649),
    // Menus, dialogs and chips sit on these: keep them paper-toned, not Material's default tint.
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFFAF5EC),
    surfaceContainer = Color(0xFFF6F0E5),
    surfaceContainerHigh = Color(0xFFF3ECDF),
    surfaceContainerHighest = Color(0xFFEDE5D6),
    surfaceBright = Color(0xFFFFFCF5),
    surfaceDim = Color(0xFFE6DED0),
    outline = Color(0xFFCFC3AE),
    outlineVariant = Color(0xFFE2D8C6),
    error = Color(0xFFB3261E),
)

private val InkDark = darkColorScheme(
    primary = Color(0xFFA9BEF5),
    onPrimary = Color(0xFF0F2250),
    primaryContainer = Color(0xFF263E74),
    onPrimaryContainer = Color(0xFFDCE4FA),
    secondary = Color(0xFFE6B36E),
    onSecondary = Color(0xFF3B2200),
    secondaryContainer = Color(0xFF5A3A0E),
    onSecondaryContainer = Color(0xFFFBE3C4),
    background = Color(0xFF14120F),
    onBackground = Color(0xFFEEE6D8),
    surface = Color(0xFF1E1B17),
    onSurface = Color(0xFFEEE6D8),
    surfaceVariant = Color(0xFF2A2620),
    onSurfaceVariant = Color(0xFFB8AE9C),
    surfaceContainerLowest = Color(0xFF110F0C),
    surfaceContainerLow = Color(0xFF1A1814),
    surfaceContainer = Color(0xFF221F1A),
    surfaceContainerHigh = Color(0xFF26231E),
    surfaceContainerHighest = Color(0xFF302C26),
    surfaceBright = Color(0xFF3A352E),
    surfaceDim = Color(0xFF14120F),
    outline = Color(0xFF5A5246),
    outlineVariant = Color(0xFF3A352D),
    error = Color(0xFFF2B8B5),
)

private val base = Typography()

private val WordLensTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
)

/** Small-caps style label used for parts of speech ("VERB"). */
val PartOfSpeechStyle = TextStyle(
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.4.sp,
)

@Composable
fun WordLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) InkDark else PaperLight,
        typography = WordLensTypography,
        content = content,
    )
}

/** Sets whether the status/navigation bar icons are dark (for light screens) or light. */
@Composable
fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = dark
            isAppearanceLightNavigationBars = dark
        }
    }
}
