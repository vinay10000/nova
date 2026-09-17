package com.nova.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Editorial headings, utilitarian controls, warm paper / AMOLED surfaces.
// Existing public names remain available to the screen implementations.
val NovaDisplay = FontFamily.Serif
val NovaBody = FontFamily.SansSerif
val NovaMono = FontFamily.Monospace

object NovaPalette {
  val DarkBg = Color(0xFF000000)
  val DarkSurface = Color(0xFF101012)
  val DarkRaised = Color(0xFF1C1C20)
  // Functional outlines must remain visible; decorative edges use selfEdge().
  val DarkLine = Color(0xFF77716A)
  val LightBg = Color(0xFFFBF9F4)
  val LightSurface = Color(0xFFF2EEE5)
  val LightRaised = Color(0xFFE9E2D3)
  val LightLine = Color(0xFF82796B)

  // More opaque scrims keep busy content from competing with composer text.
  val GlassScrimDark = Color(0xF21C1C20)
  val GlassEdgeDark = Color(0x38FFFFFF)
  val GlassSheenDark = Color(0x0DFFFFFF)
  val BronzeDark = Color(0xFFE2B26B)
  val BronzeOnDark = Color(0xFF000000)
  val BronzeLight = Color(0xFF7A5320)
  val BronzeLightFill = Color(0xFF17130C)
  val SageDark = Color(0xFF9DB89A)
  val SageLight = Color(0xFF3E6B3A)
  val ClayDark = Color(0xFFE08E7E)
  val ClayLight = Color(0xFF9C3B2A)
  val InkDark = Color(0xFFF3EDE1)
  val InkDimDark = Color(0xFFB9B0A2)
  val FaintDark = Color(0xFFADA498)
  val InkLight = Color(0xFF211C14)
  val InkDimLight = Color(0xFF62594B)
  val FaintLight = Color(0xFF6F6658)
}

enum class NovaAccent(val label: String) {
  BRONZE("Bronze"),
  SLATE("Slate"),
  SAGE("Sage"),
  TERRACOTTA("Terracotta"),
  PLUM("Plum"),
}

private data class AccentPair(
  val darkPrimary: Color,
  val darkContainer: Color,
  val lightPrimary: Color,
  val lightContainer: Color,
)

private val accentMap = mapOf(
  NovaAccent.BRONZE to AccentPair(Color(0xFFE2B26B), Color(0xFF382A18), Color(0xFF7A5320), Color(0xFFE9E2D3)),
  NovaAccent.SLATE to AccentPair(Color(0xFF8EACBD), Color(0xFF22313B), Color(0xFF3A6070), Color(0xFFD8E4EA)),
  NovaAccent.SAGE to AccentPair(Color(0xFF9DB89A), Color(0xFF243322), Color(0xFF3E6B3A), Color(0xFFD6E5D3)),
  NovaAccent.TERRACOTTA to AccentPair(Color(0xFFE08E7E), Color(0xFF40251F), Color(0xFF9C3B2A), Color(0xFFF0D5CE)),
  NovaAccent.PLUM to AccentPair(Color(0xFFC4A0D0), Color(0xFF34243C), Color(0xFF6B4080), Color(0xFFE4D4EC)),
)

/** Explicit semantic pairs prevent default Material purple leaking into the UI. */
internal fun buildNovaScheme(accent: NovaAccent, dark: Boolean): ColorScheme {
  val a = accentMap.getValue(accent)
  return if (dark) {
    darkColorScheme(
      primary = a.darkPrimary,
      onPrimary = NovaPalette.DarkBg,
      primaryContainer = a.darkContainer,
      onPrimaryContainer = NovaPalette.InkDark,
      inversePrimary = a.lightPrimary,
      secondary = NovaPalette.SageDark,
      onSecondary = Color(0xFF10190F),
      secondaryContainer = Color(0xFF243322),
      onSecondaryContainer = Color(0xFFD6E5D3),
      tertiary = Color(0xFFC9A882),
      onTertiary = Color(0xFF21170D),
      tertiaryContainer = Color(0xFF392A1C),
      onTertiaryContainer = Color(0xFFF0DDC6),
      background = NovaPalette.DarkBg,
      onBackground = NovaPalette.InkDark,
      surface = NovaPalette.DarkBg,
      onSurface = NovaPalette.InkDark,
      onSurfaceVariant = NovaPalette.InkDimDark,
      surfaceVariant = NovaPalette.DarkSurface,
      surfaceTint = a.darkPrimary,
      surfaceDim = NovaPalette.DarkBg,
      surfaceBright = Color(0xFF2A2A2E),
      surfaceContainerLowest = NovaPalette.DarkBg,
      surfaceContainerLow = Color(0xFF080809),
      surfaceContainer = NovaPalette.DarkSurface,
      surfaceContainerHigh = NovaPalette.DarkRaised,
      surfaceContainerHighest = Color(0xFF28282C),
      inverseSurface = NovaPalette.LightSurface,
      inverseOnSurface = NovaPalette.InkLight,
      outline = NovaPalette.DarkLine,
      outlineVariant = Color(0xFF38363A),
      error = NovaPalette.ClayDark,
      onError = Color(0xFF2B100B),
      errorContainer = Color(0xFF3A1F18),
      onErrorContainer = Color(0xFFFFDAD2),
      scrim = Color.Black,
    )
  } else {
    lightColorScheme(
      primary = a.lightPrimary,
      onPrimary = Color(0xFFFFFBF0),
      primaryContainer = a.lightContainer,
      onPrimaryContainer = NovaPalette.InkLight,
      inversePrimary = a.darkPrimary,
      secondary = NovaPalette.SageLight,
      onSecondary = Color.White,
      secondaryContainer = Color(0xFFD6E5D3),
      onSecondaryContainer = Color(0xFF172B15),
      tertiary = Color(0xFF7A6142),
      onTertiary = Color.White,
      tertiaryContainer = Color(0xFFF0DDC6),
      onTertiaryContainer = Color(0xFF2B1D0E),
      background = NovaPalette.LightBg,
      onBackground = NovaPalette.InkLight,
      surface = NovaPalette.LightBg,
      onSurface = NovaPalette.InkLight,
      onSurfaceVariant = NovaPalette.InkDimLight,
      surfaceVariant = NovaPalette.LightSurface,
      surfaceTint = a.lightPrimary,
      surfaceDim = Color(0xFFDED8CC),
      surfaceBright = NovaPalette.LightBg,
      surfaceContainerLowest = Color.White,
      surfaceContainerLow = Color(0xFFF7F4ED),
      surfaceContainer = NovaPalette.LightSurface,
      surfaceContainerHigh = NovaPalette.LightRaised,
      surfaceContainerHighest = Color(0xFFE0D8C8),
      inverseSurface = NovaPalette.DarkRaised,
      inverseOnSurface = NovaPalette.InkDark,
      outline = NovaPalette.LightLine,
      outlineVariant = Color(0xFFD4CCBD),
      error = NovaPalette.ClayLight,
      onError = Color.White,
      errorContainer = Color(0xFFF5DCD4),
      onErrorContainer = Color(0xFF3A1008),
      scrim = Color.Black,
    )
  }
}

private fun display(size: Int, line: Int) = TextStyle(
  fontFamily = NovaDisplay,
  fontWeight = FontWeight.SemiBold,
  fontSize = size.sp,
  lineHeight = line.sp,
  letterSpacing = (-0.3).sp,
)

private fun body(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
  fontFamily = NovaBody,
  fontWeight = weight,
  fontSize = size.sp,
  lineHeight = line.sp,
  letterSpacing = 0.sp,
)

// Serif is reserved for display: controls, card titles, and labels stay scannable.
private val novaTypography = Typography(
  displayLarge = display(48, 56),
  displayMedium = display(40, 48),
  displaySmall = display(36, 44),
  headlineLarge = display(32, 40),
  headlineMedium = display(28, 36),
  headlineSmall = display(24, 32),
  titleLarge = body(22, 28, FontWeight.SemiBold),
  titleMedium = body(16, 24, FontWeight.SemiBold),
  titleSmall = body(14, 20, FontWeight.SemiBold),
  bodyLarge = body(16, 26),
  bodyMedium = body(14, 22),
  bodySmall = body(12, 18),
  labelLarge = body(14, 20, FontWeight.SemiBold),
  labelMedium = body(12, 16, FontWeight.Medium),
  labelSmall = body(11, 16, FontWeight.Medium),
)

private val novaShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NovaTheme(accent: NovaAccent = NovaAccent.BRONZE, content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = buildNovaScheme(accent, isSystemInDarkTheme()),
    typography = novaTypography,
    shapes = novaShapes,
    content = content,
  )
}

/** Decorative only. Interactive boundaries should use colorScheme.outline. */
fun selfEdge(dark: Boolean): Color = if (dark) Color(0x24F3EDE1) else Color(0x24211C14)

val ScreenGutter = 20.dp

/** Readable, subtly layered panel; no blur that could smear text or controls. */
@Composable
fun GlassPanel(
  modifier: Modifier = Modifier,
  corner: RoundedCornerShape = RoundedCornerShape(26.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  val dark = isSystemInDarkTheme()
  val scrim = if (dark) NovaPalette.GlassScrimDark else Color(0xF7F2EEE5)
  val edge = if (dark) NovaPalette.GlassEdgeDark else Color(0x33211C14)
  Surface(
    modifier = modifier,
    shape = corner,
    color = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
    border = BorderStroke(1.dp, edge),
  ) {
    Box(Modifier.background(scrim)) {
      Box(
        Modifier.matchParentSize().background(
          Brush.verticalGradient(
            0.0f to if (dark) NovaPalette.GlassSheenDark else Color(0x1EFFFFFF),
            0.08f to Color.Transparent,
          ),
        ),
      )
      Column(content = content)
    }
  }
}
