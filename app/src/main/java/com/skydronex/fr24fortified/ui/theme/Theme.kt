package com.skydronex.fr24fortified.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = darkColorScheme(
    primary              = SkyBlue,
    onPrimary            = Color(0xFF003354),
    primaryContainer     = SkyBlueDark,
    onPrimaryContainer   = SkyBluePale,
    secondary            = Teal,
    onSecondary          = Color(0xFF00201C),
    secondaryContainer   = TealDark,
    onSecondaryContainer = TealPale,
    tertiary             = Emerald,
    onTertiary           = Color(0xFF00291A),
    tertiaryContainer    = EmeraldDark,
    onTertiaryContainer  = Color(0xFFA7F3D0),
    error                = Rose,
    onError              = Color(0xFF7F1D1D),
    errorContainer       = Color(0xFF7F1D1D),
    onErrorContainer     = Color(0xFFFECACA),
    background           = BgDark,
    onBackground         = TextPrimary,
    surface              = SurfaceDark,
    onSurface            = TextPrimary,
    surfaceVariant       = Surface2,
    onSurfaceVariant     = TextMuted,
    outline              = BorderDark,
    outlineVariant       = Color(0xFF1E293B),
)

@Composable
fun FR24FortifiedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = Typography,
        content     = content
    )
}
