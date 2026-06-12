package com.example.ui.theme

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

private val DarkColorScheme =
  darkColorScheme(
    primary = FlameOrange,
    secondary = InnBucksGold,
    secondaryContainer = Color(0xFF3F2D27),
    onSecondaryContainer = Color(0xFFFFF7ED),
    tertiary = EcoCashGreen,
    background = DarkCharcoal,
    surface = SlateOverlay,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFFFFF7ED),
    onSurface = Color(0xFFFFF7ED)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = FlameOrange,
    secondary = InnBucksGold,
    secondaryContainer = NaturalBeige,
    onSecondaryContainer = NaturalOrangeDark,
    tertiary = EcoCashGreen,
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = NaturalTextSlate,
    onSurface = NaturalTextSlate
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  // Dynamic color is disabled to preserve the custom "Natural Tones" branding
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
