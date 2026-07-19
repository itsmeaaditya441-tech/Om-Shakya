package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = AuraPrimaryDark,
    secondary = AuraPrimaryLight,
    tertiary = AuraGreen,
    background = AuraBackgroundDark,
    surface = AuraSurfaceDark,
    surfaceVariant = AuraSurfaceVariantDark,
    onBackground = AuraOnBackgroundDark,
    onSurface = AuraOnSurfaceDark,
    primaryContainer = AuraPrimaryContainerDark,
    onPrimaryContainer = AuraOnPrimaryContainerDark,
    error = AuraError
  )

private val LightColorScheme =
  lightColorScheme(
    primary = AuraPrimary,
    secondary = AuraPrimaryLight,
    tertiary = AuraGreen,
    background = AuraBackground,
    surface = AuraSurface,
    surfaceVariant = AuraSurfaceVariant,
    onBackground = AuraOnBackground,
    onSurface = AuraOnSurface,
    primaryContainer = AuraPrimaryContainer,
    onPrimaryContainer = AuraOnPrimaryContainer,
    error = AuraError
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled by default to let our custom Geometric Balance theme shine
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
