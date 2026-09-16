package com.nova.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Nova design language: AMOLED true-black + liquid glass in dark mode, warm
// paper in light mode. One bronze accent, serif display + neutral body.
// Deliberately avoids blue-charcoal, blue-purple gradients, glows, and
// pill-everything. Glass = scrim + hairline edge + top sheen, never a bloom.

val NovaDisplay = FontFamily.Serif
val NovaBody = FontFamily.SansSerif
val NovaMono = FontFamily.Monospace

object NovaPalette {
  // AMOLED true black. Every dark surface is fully off-pixel black or one
  // tonal step above it — never blue-charcoal.
  val DarkBg = Color(0xFF000000)
  val DarkSurface = Color(0xFF0B0B0D)
  val DarkRaised = Color(0xFF141417)
  val DarkLine = Color(0xFF202024)
  // Barely-warm paper white, not cream.
  val LightBg = Color(0xFFFBF9F4)
  val LightSurface = Color(0xFFF2EEE5)
  val LightRaised = Color(0xFFE9E2D3)
  val LightLine = Color(0xFFDDD5C2)

  // Liquid-glass tokens (dark). Glass is a translucent scrim over content that
  // stays fully legible underneath, edged with a self-colored hairline and a
  // faint top highlight — never a glow, never a contrasting outline.
  val GlassScrimDark = Color(0xB3141417)
  val GlassEdgeDark = Color(0x29FFFFFF)
  val GlassSheenDark = Color(0x14FFFFFF)

  // One tonal bronze accent, desaturated. Never saturated poster color.
  val BronzeDark = Color(0xFFE2B26B)
  val BronzeOnDark = Color(0xFF000000)
  val BronzeLight = Color(0xFF7A5320)
  val BronzeLightFill = Color(0xFF17130C)

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

enum class NovaAccent(val label: String) {
  BRONZE("Bronze"),
  SLATE("Slate"),
  SAGE("Sage"),
  TERRACOTTA("Terracotta"),
  PLUM("Plum"),
}

// Tonal accent colors — desaturated, never poster-bright.
private data class AccentPair(val darkPrimary: Color, val darkContainer: Color, val lightPrimary: Color, val lightContainer: Color)

private val accentMap = mapOf(
  NovaAccent.BRONZE to AccentPair(
    darkPrimary = Color(0xFFE2B26B),
    darkContainer = Color(0xFF17130C),
    lightPrimary = Color(0xFF7A5320),
    lightContainer = Color(0xFFE9E2D3),
  ),
  NovaAccent.SLATE to AccentPair(
    darkPrimary = Color(0xFF8EACBD),
    darkContainer = Color(0xFF101418),
    lightPrimary = Color(0xFF3A6070),
    lightContainer = Color(0xFFD8E4EA),
  ),
  NovaAccent.SAGE to AccentPair(
    darkPrimary = Color(0xFF9DB89A),
    darkContainer = Color(0xFF0E130D),
    lightPrimary = Color(0xFF3E6B3A),
    lightContainer = Color(0xFFD6E5D3),
  ),
  NovaAccent.TERRACOTTA to AccentPair(
    darkPrimary = Color(0xFFE08E7E),
    darkContainer = Color(0xFF150D0B),
    lightPrimary = Color(0xFF9C3B2A),
    lightContainer = Color(0xFFF0D5CE),
  ),
  NovaAccent.PLUM to AccentPair(
    darkPrimary = Color(0xFFC4A0D0),
    darkContainer = Color(0xFF120D15),
    lightPrimary = Color(0xFF6B4080),
    lightContainer = Color(0xFFE4D4EC),
  ),
)

private fun buildNovaScheme(accent: NovaAccent, dark: Boolean): ColorScheme {
  val a = accentMap[accent]!!

  if (dark) {
    return darkColorScheme(
      primary = a.darkPrimary,
      onPrimary = NovaPalette.DarkBg,
      primaryContainer = a.darkContainer,
      onPrimaryContainer = NovaPalette.InkDark,
      secondary = Color(0xFF9DB89A),
      onSecondary = NovaPalette.DarkBg,
      tertiary = Color(0xFFC9A882),
      surface = NovaPalette.DarkBg,
      onSurface = NovaPalette.InkDark,
      onSurfaceVariant = NovaPalette.InkDimDark,
      surfaceVariant = NovaPalette.DarkSurface,
      surfaceContainer = NovaPalette.DarkSurface,
      surfaceContainerHigh = NovaPalette.DarkRaised,
      surfaceContainerLow = Color(0xFF060607),
      outline = NovaPalette.DarkLine,
      outlineVariant = NovaPalette.DarkRaised,
      error = NovaPalette.ClayDark,
      errorContainer = Color(0xFF3A1F18),
    )
  } else {
    return lightColorScheme(
      primary = a.lightPrimary,
      onPrimary = Color(0xFFFFFBF0),
      primaryContainer = a.lightContainer,
      onPrimaryContainer = NovaPalette.InkLight,
      secondary = Color(0xFF3E6B3A),
      onSecondary = Color.White,
      tertiary = Color(0xFF7A6142),
      surface = NovaPalette.LightBg,
      onSurface = NovaPalette.InkLight,
      onSurfaceVariant = NovaPalette.InkDimLight,
      surfaceVariant = NovaPalette.LightSurface,
      surfaceContainer = NovaPalette.LightSurface,
      surfaceContainerHigh = NovaPalette.LightRaised,
      outline = NovaPalette.LightLine,
      outlineVariant = Color(0xFFE7DFCC),
      error = NovaPalette.ClayLight,
      errorContainer = Color(0xFFF5DCD4),
    )
  }
}

@Composable
fun NovaTheme(accent: NovaAccent = NovaAccent.BRONZE, content: @Composable () -> Unit) {
  val dark = isSystemInDarkTheme()
  MaterialTheme(
    colorScheme = buildNovaScheme(accent, dark),
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

/**
 * Liquid-glass panel: a translucent scrim with a hairline self-colored edge
 * and a faint top sheen. Applied sparingly (composer, drawer, mention picker).
 * Deliberately no Modifier.blur here: blurring the panel would smear its own
 * text. The frosted read comes from the scrim + edge + sheen over the dark
 * surface behind it; a true backdrop-blur pass can layer under this later.
 */
@Composable
fun GlassPanel(
  modifier: Modifier = Modifier,
  corner: androidx.compose.foundation.shape.RoundedCornerShape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
  content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
  val dark = isSystemInDarkTheme()
  val scrim = if (dark) NovaPalette.GlassScrimDark else Color(0xD9F2EEE5)
  val edge = if (dark) NovaPalette.GlassEdgeDark else Color(0x29211C14)
  androidx.compose.material3.Surface(
    modifier = modifier,
    shape = corner,
    color = Color.Transparent,
    border = androidx.compose.foundation.BorderStroke(1.dp, edge),
  ) {
    androidx.compose.foundation.layout.Box(
      modifier = Modifier.background(scrim),
    ) {
      // Top sheen: a 1dp light lip along the upper edge, the premium part of glass.
      androidx.compose.foundation.layout.Box(
        modifier = Modifier
          .matchParentSize()
          .background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
              0.0f to if (dark) NovaPalette.GlassSheenDark else Color(0x1EFFFFFF),
              0.08f to Color.Transparent,
            ),
          ),
      )
      androidx.compose.foundation.layout.Column(content = content)
    }
  }
}
