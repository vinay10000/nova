package com.nova.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Nova design language: warm ink on paper, one bronze accent, serif display + neutral body.
// Deliberately avoids cool blue-charcoal, blue-purple gradients, glows, and pill-everything.

val NovaDisplay = FontFamily.Serif
val NovaBody = FontFamily.SansSerif
val NovaMono = FontFamily.Monospace

object NovaPalette {
  // Warm near-black (brown-black), not blue-charcoal.
  val DarkBg = Color(0xFF14110D)
  val DarkSurface = Color(0xFF1D1A14)
  val DarkRaised = Color(0xFF26211A)
  val DarkLine = Color(0xFF2E2920)
  // Barely-warm paper white, not cream.
  val LightBg = Color(0xFFFBF9F4)
  val LightSurface = Color(0xFFF2EEE5)
  val LightRaised = Color(0xFFE9E2D3)
  val LightLine = Color(0xFFDDD5C2)

  // One tonal bronze accent, desaturated. Never saturated poster color.
  val BronzeDark = Color(0xFFE2B26B)
  val BronzeOnDark = Color(0xFF14110D)
  val BronzeLight = Color(0xFF7A5320)
  val BronzeLightFill = Color(0xFF2A2114)

  val SageDark = Color(0xFF9DB89A)
  val SageLight = Color(0xFF3E6B3A)
  val ClayDark = Color(0xFFE08E7E)
  val ClayLight = Color(0xFF9C3B2A)

  val InkDark = Color(0xFFF3EDE1)
  val InkDimDark = Color(0xFFA79E8D)
  val FaintDark = Color(0xFF6E6656)
  val InkLight = Color(0xFF211C14)
  val InkDimLight = Color(0xFF6B6252)
  val FaintLight = Color(0xFF9A917E)
}

private val NovaLightScheme = lightColorScheme(
  primary = Color(0xFF7A5320),
  onPrimary = Color(0xFFFFFBF0),
  primaryContainer = Color(0xFFE9E2D3),
  onPrimaryContainer = Color(0xFF211C14),
  secondary = Color(0xFF3E6B3A),
  onSecondary = Color.White,
  surface = Color(0xFFFBF9F4),
  onSurface = Color(0xFF211C14),
  onSurfaceVariant = Color(0xFF6B6252),
  surfaceVariant = Color(0xFFF2EEE5),
  surfaceContainer = Color(0xFFF2EEE5),
  surfaceContainerHigh = Color(0xFFE9E2D3),
  outline = Color(0xFFDDD5C2),
  outlineVariant = Color(0xFFE7DFCC),
  error = Color(0xFF9C3B2A),
  errorContainer = Color(0xFFF5DCD4),
)

private val NovaDarkScheme = darkColorScheme(
  primary = Color(0xFFE2B26B),
  onPrimary = Color(0xFF14110D),
  primaryContainer = Color(0xFF2A2114),
  onPrimaryContainer = Color(0xFFF3EDE1),
  secondary = Color(0xFF9DB89A),
  onSecondary = Color(0xFF14110D),
  surface = Color(0xFF14110D),
  onSurface = Color(0xFFF3EDE1),
  onSurfaceVariant = Color(0xFFA79E8D),
  surfaceVariant = Color(0xFF1D1A14),
  surfaceContainer = Color(0xFF1D1A14),
  surfaceContainerHigh = Color(0xFF26211A),
  surfaceContainerLow = Color(0xFF181410),
  outline = Color(0xFF2E2920),
  outlineVariant = Color(0xFF26211A),
  error = Color(0xFFE08E7E),
  errorContainer = Color(0xFF3A1F18),
)

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
  val dark = isSystemInDarkTheme()
  MaterialTheme(
    colorScheme = if (dark) NovaDarkScheme else NovaLightScheme,
    typography = Typography(
      displayLarge = Typography().displayLarge.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.W600),
      displayMedium = Typography().displayMedium.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.W600),
      headlineLarge = Typography().headlineLarge.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.W600),
      headlineMedium = Typography().headlineMedium.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.W600),
      headlineSmall = Typography().headlineSmall.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.SemiBold),
      titleLarge = Typography().titleLarge.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.SemiBold),
      titleMedium = Typography().titleMedium.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.SemiBold),
    ),
    content = content,
  )
}

/** Self-colored hairline: 1dp stroke in the surface's own tone, not a contrasting outline. */
fun selfEdge(dark: Boolean): Color = if (dark) Color(0x14F3EDE1) else Color(0x14211C14)

val ScreenGutter = 20.dp
