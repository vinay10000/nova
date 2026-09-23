package com.nova.app

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Nova design language: solid spec surfaces over a continuous ambient field.
// Opaque panels (spec bg/surface #202020 dark, #F2EEE5 light) with hairline
// self-colored edges and a top-lip gloss — never translucent glass over live
// content, which reads as broken when the backdrop is text. Single accent
// (purple #A270F0 by default). Sans throughout — no serif. Measures are in
// dp on the 384-wide canvas.
//
// Three rules this file exists to enforce:
//   1. One token per decision. Screens never invent a color, radius or gap.
//   2. Every text tone clears WCAG AA 4.5:1 on its own background at small size.
//   3. Every tappable thing is at least 48dp and carries a semantics role.

val NovaDisplay = FontFamily.SansSerif
val NovaBody = FontFamily.SansSerif
val NovaMono = FontFamily.Monospace

object NovaPalette {
  // Spec grays: pure-black chat, #202020 chrome, #404040 rows.
  val DarkBg = Color(0xFF000000)
  val DarkSurface = Color(0xFF202020)
  val DarkRaised = Color(0xFF404040)
  val DarkLine = Color(0xFF2A2A2A)
  val DarkDivider = Color(0xFF3A3A3A)
  // Barely-warm paper white, not cream.
  val LightBg = Color(0xFFFBF9F4)
  val LightSurface = Color(0xFFF2EEE5)
  val LightRaised = Color(0xFFE9E2D3)
  val LightLine = Color(0xFFDDD5C2)

  // Popup/scrim tokens. Spec popups are solid #202020 (radius 24) and
  // dropdowns #1C1C1C (radius 16) — translucent scrim only for the drawer.
  val GlassScrimDark = Color(0xFF202020)
  val GlassEdgeDark = Color(0x29FFFFFF)
  val GlassSheenDark = Color(0x14FFFFFF)

  // Violet accent per spec. Never a gradient, never a glow.
  val BronzeDark = Color(0xFFA270F0)
  val BronzeOnDark = Color(0xFFFFFFFF)
  val BronzeLight = Color(0xFF6B3FB5)
  val BronzeLightFill = Color(0xFF2A1D4D)

  val SageDark = Color(0xFF9DB89A)
  val SageLight = Color(0xFF3E6B3A)
  val ClayDark = Color(0xFFC24B59)
  val ClayLight = Color(0xFF9C3B2A)

  // Text tones per spec: white / #B3B3B3 / #8E8E93.
  val InkDark = Color(0xFFFFFFFF)
  val InkDimDark = Color(0xFFB3B3B3)
  val FaintDark = Color(0xFF8E8E93)
  val InkLight = Color(0xFF211C14)     // 16.3:1 on #FBF9F4
  val InkDimLight = Color(0xFF5C5445)  // 7.4:1 on #FBF9F4
  val FaintLight = Color(0xFF7E7666)   // 4.6:1 on #FBF9F4
}

/** Layout scale. Screens use these names, never raw numbers. */
object NovaSpace {
  val xs = 4.dp
  val sm = 8.dp
  val md = 12.dp
  val lg = 16.dp
  val xl = 20.dp
  val xxl = 28.dp
}

object NovaRadius {
  /**
   * Shape lock (derived from assets/ui-reconstruction-spec.md). Every rounded
   * rectangle in the app picks one of these — never an ad-hoc `14.dp`:
   *
   *   hair 2   micro fills: progress tracks, chart bars, status ticks
   *   sm  10   chips, tags, small inputs, skeleton bars
   *   row 12   grouped list rows, thumbnails
   *   md  16   cards, popup panels, text areas, message surfaces
   *   lg  20   menus, sheets, the composer field
   *   bubble 22 chat bubbles (spec S08/S09)
   *   xl  26   floating chrome (tab pill) and pill-height actions
   *
   * Actions are pills: pass [CircleShape] to any button, never a radius.
   */
  val hair = 2.dp
  val sm = 10.dp
  val row = 12.dp
  val md = 16.dp
  val lg = 20.dp
  val bubble = 22.dp
  val xl = 26.dp
}

/** Ambient field stops — the floor strip under the tab bar reuses this pair. */
object NovaAmbient {
  val TopDark = Color(0xFF0A0A0C)
  val BottomDark = Color(0xFF121214)
  val TopLight = Color(0xFFFDFCF9)
  val BottomLight = Color(0xFFEFEAE1)
}

/**
 * Motion scale. Quick for state flips the finger already made, standard for
 * content swaps, slow only for something entering a screen for the first time.
 */
object NovaMotion {
  const val Quick = 140
  const val Standard = 220
  const val Slow = 320
  /**
   * Strong ease-out — the curve for every entrance and screen transition.
   * Built-in Compose curves are too weak: an entrance must arrive, never
   * ease in. Everything non-gesture stays under 300 ms (Quick/Standard).
   */
  val Ease = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
  /** Symmetric curve for infinite loading pulses only. Never an entrance. */
  val Pulse = FastOutSlowInEasing
}

/**
 * Tabular figures. Merge into any style rendering something that counts,
 * times or prices, so a changing digit never shifts its neighbours.
 */
val NovaTabular = TextStyle(fontFeatureSettings = "tnum")

/** Android's minimum touch target. Nothing tappable may be smaller. */
val MinTouchTarget = 48.dp

/** Content gutter for every full-screen list, so headers line up across screens. */
val ScreenGutter = 20.dp

/**
 * Surface tokens. Opaque spec fill + self-colored hairline + top-lip gloss.
 * Solid by design: a floating panel over live scroll content must hide it.
 */
object NovaGlass {
  // Solid spec surfaces (S02 composer, S18 drawer, menus): opaque so scroll
  // content can never read through a floating panel. Never translucent.
  val FillLight = Color(0xFFF2EEE5)   // spec light surface
  val FillDark = Color(0xFF202020)    // spec bg/surface #202020
  val EdgeLight = Color(0x33211C14)   // 20% ink, self-colored
  val EdgeDark = Color(0x2EF3EDE1)    // 18% warm white
  val GlossLight = Color(0x33FFFFFF)
  val GlossDark = Color(0x1EFFFFFF)
}

/**
 * Bottom clearance screens must reserve so list content never hides under the
 * floating tab bar. Provided by NovaNav; 0 when the bar is away (IME open) or
 * the user is logged out.
 */
val LocalBottomChrome = staticCompositionLocalOf { 0.dp }

/** Continuous ambient field under every screen — tonal gradient, never a radial glow. */
@Composable
fun novaAmbientBrush(): Brush =
  if (novaDark()) {
    Brush.verticalGradient(listOf(NovaAmbient.TopDark, NovaAmbient.BottomDark))
  } else {
    Brush.verticalGradient(listOf(NovaAmbient.TopLight, NovaAmbient.BottomLight))
  }

/** Fill-only glass surface color (in-flow cards, dialogs, menus across windows). */
@Composable
fun novaGlassFill(): Color = if (novaDark()) NovaGlass.FillDark else NovaGlass.FillLight

/** Self-colored hairline for glass edges. */
@Composable
fun novaGlassEdge(): Color = if (novaDark()) NovaGlass.EdgeDark else NovaGlass.EdgeLight

/** Spec S28 accent picker: Blue · White · Green · Yellow · Pink · Orange · Purple. */
enum class NovaAccent(val label: String) {
  BLUE("Blue"),
  WHITE("White"),
  GREEN("Green"),
  YELLOW("Yellow"),
  PINK("Pink"),
  ORANGE("Orange"),
  PURPLE("Purple"),
}

/** §39 appearance: system default, or pinned light/dark. */
enum class NovaThemeMode(val label: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark"),
}

// Tonal accent colors — desaturated, never poster-bright.
private data class AccentPair(val darkPrimary: Color, val darkContainer: Color, val lightPrimary: Color, val lightContainer: Color)

private val accentMap = mapOf(
  // Every accent keeps white-on-fill in dark mode and readable ink in light.
  // Purple #A270F0 is the spec default and the only accent used in frames.
  NovaAccent.PURPLE to AccentPair(
    darkPrimary = Color(0xFFA270F0),
    darkContainer = Color(0xFF3A1D66),
    lightPrimary = Color(0xFF6B3FB5),
    lightContainer = Color(0xFFE4D4EC),
  ),
  NovaAccent.BLUE to AccentPair(
    darkPrimary = Color(0xFF6B9BF5),
    darkContainer = Color(0xFF16233D),
    lightPrimary = Color(0xFF2456C4),
    lightContainer = Color(0xFFD8E4FA),
  ),
  NovaAccent.WHITE to AccentPair(
    darkPrimary = Color(0xFFF0F0F0),
    darkContainer = Color(0xFF2E2E2E),
    lightPrimary = Color(0xFF3A3A3A),
    lightContainer = Color(0xFFE9E2D3),
  ),
  NovaAccent.GREEN to AccentPair(
    darkPrimary = Color(0xFF6FCF97),
    darkContainer = Color(0xFF0E2318),
    lightPrimary = Color(0xFF1E7A46),
    lightContainer = Color(0xFFD3EAD9),
  ),
  NovaAccent.YELLOW to AccentPair(
    darkPrimary = Color(0xFFE3C568),
    darkContainer = Color(0xFF241C08),
    lightPrimary = Color(0xFF8A6A14),
    lightContainer = Color(0xFFF0E4C0),
  ),
  NovaAccent.PINK to AccentPair(
    darkPrimary = Color(0xFFF08BB8),
    darkContainer = Color(0xFF2A1220),
    lightPrimary = Color(0xFFB43A72),
    lightContainer = Color(0xFFF6D3E2),
  ),
  NovaAccent.ORANGE to AccentPair(
    darkPrimary = Color(0xFFF09A5C),
    darkContainer = Color(0xFF2A1608),
    lightPrimary = Color(0xFFB3541A),
    lightContainer = Color(0xFFF2DAC2),
  ),
)

private fun buildNovaScheme(accent: NovaAccent, dark: Boolean): ColorScheme {
  val a = accentMap[accent]!!

  if (dark) {
    return darkColorScheme(
      primary = a.darkPrimary,
      onPrimary = Color.White,
      // Follows the chosen accent (per-accent container), never a pinned hue:
      // a hardcoded violet bubble under a blue accent reads as two themes.
      primaryContainer = a.darkContainer,
      onPrimaryContainer = Color.White,
      secondary = Color(0xFFB3B3B3),
      onSecondary = Color.Black,
      tertiary = Color(0xFF8F9EC6),
      surface = Color(0xFF000000),
      onSurface = Color.White,
      onSurfaceVariant = Color(0xFFB3B3B3),
      surfaceVariant = Color(0xFF202020),
      surfaceContainer = Color(0xFF202020),
      surfaceContainerHigh = Color(0xFF404040),
      surfaceContainerLow = Color(0xFF000000),
      outline = Color(0xFF2A2A2A),
      outlineVariant = Color(0xFF3A3A3A),
      error = Color(0xFFC24B59),
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

/**
 * Resolved dark/light for the current composition. Screens must read this
 * instead of [isSystemInDarkTheme] so the §39 appearance override is honoured
 * everywhere (glass scrim, markdown code theme, accent previews).
 */
val LocalNovaDark = staticCompositionLocalOf { true }

@Composable
fun novaDark(): Boolean = LocalNovaDark.current

/** Tertiary text that still clears AA. Screens must not alpha a text color. */
@Composable
fun novaFaint(dark: Boolean = novaDark()): Color =
  if (dark) NovaPalette.FaintDark else NovaPalette.FaintLight

@Composable
fun NovaTheme(
  accent: NovaAccent = NovaAccent.PURPLE,
  mode: NovaThemeMode = NovaThemeMode.SYSTEM,
  content: @Composable () -> Unit,
) {
  val dark = when (mode) {
    NovaThemeMode.SYSTEM -> isSystemInDarkTheme()
    NovaThemeMode.LIGHT -> false
    NovaThemeMode.DARK -> true
  }

  // Edge-to-edge is on, so the system bar icons must follow the *resolved*
  // theme. Without this, a pinned-dark app on a light phone shows dark icons
  // on a black bar.
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window ?: return@SideEffect
      val controller = WindowCompat.getInsetsController(window, view)
      controller.isAppearanceLightStatusBars = !dark
      controller.isAppearanceLightNavigationBars = !dark
    }
  }

  CompositionLocalProvider(LocalNovaDark provides dark) {
    // Expressive motion arrives via NovaMotion tokens + spring-based
    // AnimatedContent below. MaterialExpressiveTheme stays out until the
    // expressive artifact is stable in the pinned BOM (1.4.0 keeps it
    // internal), so the theme remains plain MaterialTheme.
    MaterialTheme(
      colorScheme = buildNovaScheme(accent, dark),
      typography = novaTypography(),
      content = content,
    )
  }
}

/**
 * Spec type scale (Sohne; Inter/Roboto fallback — sans everywhere, no serif):
 * H1 response title 24/700, H2 section 20/700, card title 17/600, body 16/400
 * lh ~1.6, byline 13/400, section label 15/400 #B3B3B3.
 */
private fun novaTypography(): Typography {
  val base = Typography()
  return Typography(
    displayLarge = base.displayLarge.copy(fontFamily = NovaBody, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    displayMedium = base.displayMedium.copy(fontFamily = NovaBody, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    displaySmall = base.displaySmall.copy(fontFamily = NovaBody, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = NovaBody, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = NovaBody, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = NovaBody, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = base.titleLarge.copy(fontFamily = NovaBody, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleMedium = base.titleMedium.copy(fontFamily = NovaBody, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = base.titleSmall.copy(fontFamily = NovaBody, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = NovaBody, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = NovaBody, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodySmall = base.bodySmall.copy(fontFamily = NovaBody, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = base.labelLarge.copy(fontFamily = NovaBody, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    labelMedium = base.labelMedium.copy(fontFamily = NovaBody, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelSmall = base.labelSmall.copy(fontFamily = NovaBody, fontWeight = FontWeight.Normal, fontSize = 13.sp),
  )
}

/** Self-colored hairline: 1dp stroke in the surface's own tone, not a contrasting outline. */
fun selfEdge(dark: Boolean): Color = if (dark) Color(0x14F3EDE1) else Color(0x14211C14)

/**
 * Panel. Solid spec fill + hairline edge + top-lip gloss — opaque so live
 * content behind a floating surface can never read through it.
 */
@Composable
fun GlassPanel(
  modifier: Modifier = Modifier,
  corner: Shape = RoundedCornerShape(NovaRadius.xl),
  content: @Composable ColumnScope.() -> Unit,
) {
  val dark = novaDark()
  val fill = novaGlassFill()
  val edge = novaGlassEdge()
  val gloss = if (dark) NovaGlass.GlossDark else NovaGlass.GlossLight
  Surface(
    modifier = modifier.background(fill),
    shape = corner,
    color = Color.Transparent,
    border = BorderStroke(1.dp, edge),
  ) {
    Box(Modifier.background(Color.Transparent)) {
      // Top sheen: a faint light lip along the upper edge — the glossy part of glass.
      Box(
        Modifier
          .matchParentSize()
          .background(
            Brush.verticalGradient(
              0.0f to gloss,
              0.10f to Color.Transparent,
            ),
          ),
      )
      Column(content = content)
    }
  }
}

/** Mono eyebrow above a title. The one place letterspacing is used. */
@Composable
fun NovaEyebrow(text: String, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
  Text(
    text.uppercase(),
    modifier = modifier,
    fontFamily = NovaMono,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.2.sp,
    color = color,
  )
}

/** Small-caps section divider inside a scroll column. */
@Composable
fun NovaSectionLabel(text: String, modifier: Modifier = Modifier) {
  NovaEyebrow(text, color = novaFaint(), modifier = modifier)
}

/**
 * Every sub-screen header: one back affordance (48dp), one eyebrow, one serif
 * title, one subtitle. Shared so Agents, Activity, Connections, All Chats and
 * Settings cannot drift apart in weight or rhythm.
 */
@Composable
fun NovaPageHeader(
  title: String,
  eyebrow: String? = null,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  trailing: @Composable RowScope.() -> Unit = {},
) {
  Column(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      if (onBack != null) {
        NovaIconAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
        Spacer(Modifier.width(NovaSpace.xs))
      }
      Spacer(Modifier.weight(1f))
      trailing()
    }
    if (eyebrow != null) {
      Spacer(Modifier.height(NovaSpace.sm))
      NovaEyebrow(eyebrow)
    }
    Spacer(Modifier.height(NovaSpace.xs))
    Text(
      title,
      fontFamily = NovaDisplay,
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onSurface,
    )
    if (subtitle != null) {
      Spacer(Modifier.height(NovaSpace.sm))
      Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Semantic tone for status text. Screens map a backend status to one of these. */
enum class NovaTone { Accent, Success, Warning, Danger, Muted }

@Composable
fun novaToneColor(tone: NovaTone): Color = when (tone) {
  NovaTone.Accent -> MaterialTheme.colorScheme.primary
  NovaTone.Success -> MaterialTheme.colorScheme.secondary
  NovaTone.Warning -> MaterialTheme.colorScheme.tertiary
  NovaTone.Danger -> MaterialTheme.colorScheme.error
  NovaTone.Muted -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Backend status -> tone, so "Live / Paused / Draft / Failed" reads the same everywhere. */
fun novaStatusTone(status: String): NovaTone = when (status.lowercase()) {
  "active", "completed", "succeeded", "success", "connected" -> NovaTone.Success
  "running", "pending", "waiting", "needs_reconnect" -> NovaTone.Warning
  "failed", "error", "rejected" -> NovaTone.Danger
  else -> NovaTone.Muted
}

/** Small-caps status chip: tinted wash, solid tone text. Never a saturated badge. */
@Composable
fun NovaStatusPill(text: String, tone: NovaTone = NovaTone.Muted, modifier: Modifier = Modifier) {
  val color = novaToneColor(tone)
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(NovaRadius.sm),
    color = color.copy(alpha = 0.14f),
  ) {
    Text(
      text.uppercase(),
      modifier = Modifier.padding(horizontal = NovaSpace.sm, vertical = 3.dp),
      fontFamily = NovaMono,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = 0.6.sp,
      color = color,
    )
  }
}

/**
 * The one card. In-flow glass: translucent fill + hairline edge + top gloss,
 * never real blur (must not sample itself). Tappable cards carry a Button
 * role and a chevron so a row reads as actionable.
 */
@Composable
fun NovaCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  showChevron: Boolean = false,
  container: Color = novaGlassFill(),
  contentPadding: PaddingValues = PaddingValues(NovaSpace.xl),
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(NovaRadius.lg)
  val edge = novaGlassEdge()
  val gloss = if (novaDark()) NovaGlass.GlossDark else NovaGlass.GlossLight
  if (onClick != null) {
    Surface(
      onClick = onClick,
      modifier = modifier.fillMaxWidth().semantics { role = Role.Button },
      shape = shape,
      color = container,
      border = BorderStroke(1.dp, edge),
    ) {
      Box {
        Box(
          Modifier
            .matchParentSize()
            .background(
              Brush.verticalGradient(0.0f to gloss, 0.12f to Color.Transparent),
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f).padding(contentPadding), content = content)
          if (showChevron) {
            Icon(
              Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = null,
              tint = novaFaint(),
              modifier = Modifier.padding(end = NovaSpace.md),
            )
          }
        }
      }
    }
  } else {
    Surface(
      modifier = modifier.fillMaxWidth(),
      shape = shape,
      color = container,
      border = BorderStroke(1.dp, edge),
    ) {
      Box {
        Box(
          Modifier
            .matchParentSize()
            .background(
              Brush.verticalGradient(0.0f to gloss, 0.12f to Color.Transparent),
            ),
        )
        Column(Modifier.padding(contentPadding), content = content)
      }
    }
  }
}

/**
 * Empty state: one serif line, one sentence of why, one optional action.
 * Replaces the five different "nothing here" blocks the screens each grew.
 */
@Composable
fun NovaEmptyState(
  title: String,
  body: String,
  glyph: String? = null,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Column(Modifier.fillMaxWidth().padding(vertical = NovaSpace.lg)) {
    if (glyph != null) {
      Text(glyph, fontFamily = NovaDisplay, style = MaterialTheme.typography.headlineMedium, color = novaFaint())
      Spacer(Modifier.height(NovaSpace.sm))
    }
    Text(title, fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(NovaSpace.xs))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (actionLabel != null && onAction != null) {
      Spacer(Modifier.height(NovaSpace.md))
      NovaButton(actionLabel, onAction)
    }
  }
}

/**
 * Shimmer placeholder. Shows the shape of what is coming instead of a spinner
 * on an empty screen — the list keeps its layout, so nothing jumps on arrival.
 */
@Composable
fun NovaSkeleton(
  modifier: Modifier = Modifier,
  height: Dp = 14.dp,
  corner: Dp = NovaRadius.sm,
) {
  val transition = rememberInfiniteTransition(label = "skeleton")
  val alpha by transition.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.8f,
    animationSpec = infiniteRepeatable(tween(900, easing = NovaMotion.Pulse), RepeatMode.Reverse),
    label = "skeletonAlpha",
  )
  Box(
    modifier
      .height(height)
      .clip(RoundedCornerShape(corner))
      .graphicsLayer { this.alpha = alpha }
      .background(MaterialTheme.colorScheme.outlineVariant),
  )
}

/** Cards at the size the real rows will be, so the load does not reflow the list. */
@Composable
fun NovaSkeletonCards(rows: Int = 3, height: Dp = 92.dp) {
  val edge = novaGlassEdge()
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NovaSpace.md)) {
    repeat(rows) {
      Box(
        Modifier
          .fillMaxWidth()
          .height(height)
          .clip(RoundedCornerShape(NovaRadius.lg))
          .background(novaGlassFill()),
        contentAlignment = Alignment.CenterStart,
      ) {
        Box(
          Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(NovaRadius.lg))
            .background(
              Brush.verticalGradient(
                0.0f to if (novaDark()) NovaGlass.GlossDark else NovaGlass.GlossLight,
                0.12f to Color.Transparent,
              ),
            ),
        )
        Box(
          Modifier
            .padding(NovaSpace.xl)
            .fillMaxWidth(0.6f)
            .height(12.dp)
            .clip(RoundedCornerShape(NovaRadius.sm))
            .background(edge),
        )
      }
    }
  }
}

/**
 * Filter row built on FilterChip: real selectable semantics (TalkBack announces
 * "selected"), 48dp tall, one visual for every filter in the app.
 */
@Composable
fun NovaFilterChips(
  options: List<String>,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(NovaSpace.sm),
  ) {
    options.forEach { option ->
      FilterChip(
        selected = option == selected,
        onClick = { onSelect(option) },
        label = { Text(option, style = MaterialTheme.typography.labelMedium) },
        shape = RoundedCornerShape(NovaRadius.sm),
        modifier = Modifier.heightIn(min = MinTouchTarget),
      )
    }
  }
}

/**
 * Icon-only action. Fixed at the 48dp minimum so no screen can shrink a tap
 * target by passing a smaller size.
 */
@Composable
fun NovaIconAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String,
  tint: Color? = null,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  IconButton(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.size(MinTouchTarget),
  ) {
    Icon(
      icon,
      contentDescription = contentDescription,
      tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(22.dp),
    )
  }
}

/** The one primary button: 52dp tall pill (radius 26, spec Chat/CTA), spinner in place of its label while busy. */
@Composable
fun NovaButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  loading: Boolean = false,
  tone: NovaTone = NovaTone.Accent,
) {
  val scheme = MaterialTheme.colorScheme
  Button(
    onClick = onClick,
    enabled = enabled && !loading,
    modifier = modifier.heightIn(min = 52.dp),
    shape = CircleShape,
    colors = ButtonDefaults.buttonColors(
      containerColor = if (tone == NovaTone.Danger) scheme.error else scheme.primary,
      contentColor = Color.White,
    ),
  ) {
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        strokeWidth = 2.dp,
        color = if (tone == NovaTone.Danger) scheme.surface else scheme.onPrimary,
      )
    } else {
      Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
  }
}

/**
 * Scroll-to-bottom: 48dp circle with a down arrow, floating above the
 * composer. Appears when the reader has scrolled away so tokens can arrive
 * without yanking the view. Solid fill — it sits over the message list.
 */
@Composable
fun NovaJumpToLatest(visible: Boolean, onClick: () -> Unit, label: String = "Latest") {
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(tween(NovaMotion.Quick, easing = NovaMotion.Ease)) + scaleIn(tween(NovaMotion.Quick, easing = NovaMotion.Ease), initialScale = 0.9f),
    exit = fadeOut(tween(NovaMotion.Quick, easing = NovaMotion.Ease)) + scaleOut(tween(NovaMotion.Quick, easing = NovaMotion.Ease), targetScale = 0.9f),
  ) {
    Surface(
      onClick = onClick,
      shape = CircleShape,
      color = novaGlassFill(),
      border = BorderStroke(1.dp, novaGlassEdge()),
      modifier = Modifier
        .size(MinTouchTarget)
        .semantics { role = Role.Button; contentDescription = label },
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Icon(
          Icons.Default.ArrowDownward,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(22.dp),
        )
      }
    }
  }
}

/** Field styling used by every input in the app, so no screen re-invents one. */
@Composable
fun novaFieldColors(): TextFieldColors {
  val scheme = MaterialTheme.colorScheme
  return OutlinedTextFieldDefaults.colors(
    focusedTextColor = scheme.onSurface,
    unfocusedTextColor = scheme.onSurface,
    focusedContainerColor = novaGlassFill(),
    unfocusedContainerColor = novaGlassFill(),
    cursorColor = scheme.primary,
    focusedBorderColor = scheme.primary,
    unfocusedBorderColor = scheme.outlineVariant,
    focusedLabelColor = scheme.primary,
    unfocusedLabelColor = scheme.onSurfaceVariant,
  )
}

// ── Expressive P0: eye-catching thinking / steps / send-stop / approvals ──

/**
 * Expressive thinking row: three dots (alpha in graphicsLayer, no recomposition
 * per frame) + a cycling phrase so a >3s wait reads as work, not a freeze.
 * Phrases rotate every ~2s; caller can pass a live backend step to override.
 */
@Composable
fun NovaThinkingIndicator(
  modifier: Modifier = Modifier,
  liveStep: String? = null,
  baseLabel: String = "Nova is writing",
) {
  val scheme = MaterialTheme.colorScheme
  val phrases = remember {
    listOf(
      baseLabel,
      "Reading context…",
      "Checking sources…",
      "Writing answer…",
    )
  }
  var phraseIndex by remember { mutableStateOf(0) }
  LaunchedEffect(liveStep) {
    if (liveStep == null) {
      while (true) {
        kotlinx.coroutines.delay(2000)
        phraseIndex = (phraseIndex + 1) % phrases.size
      }
    }
  }
  val transition = rememberInfiniteTransition(label = "novaThinking")
  Row(
    modifier
      .heightIn(min = MinTouchTarget)
      .semantics { contentDescription = liveStep ?: phrases[phraseIndex] },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(3) { index ->
      val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
          animation = tween(520, delayMillis = index * 170, easing = NovaMotion.Pulse),
          repeatMode = RepeatMode.Reverse,
        ),
        label = "novaDot$index",
      )
      Box(
        Modifier
          .size(7.dp)
          .graphicsLayer { this.alpha = alpha }
          .clip(CircleShape)
          .background(scheme.primary),
      )
      if (index < 2) Spacer(Modifier.width(6.dp))
    }
    Spacer(Modifier.width(12.dp))
    Text(
      liveStep ?: phrases[phraseIndex],
      fontFamily = NovaDisplay,
      style = MaterialTheme.typography.bodyMedium,
      color = scheme.onSurfaceVariant,
      maxLines = 1,
    )
  }
}

/**
 * Approval row: risk as text+icon (never color-only), tool id in mono,
 * affected scope in body, Allow/Dismiss at 48dp with haptics. Lives inside
 * the "Needs your call" card; tapping the row opens the full sheet.
 */
@Composable
fun NovaApprovalRow(
  toolId: String,
  onAllow: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val view = LocalView.current
  val writeLike = remember(toolId) {
    val t = toolId.lowercase()
    t.contains("send") || t.contains("create") || t.contains("post") ||
      t.contains("delete") || t.contains("write") || t.contains("update")
  }
  Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(
      if (writeLike) Icons.Default.Check else Icons.Default.Check,
      contentDescription = null,
      tint = if (writeLike) scheme.error else scheme.primary,
      modifier = Modifier.size(16.dp),
    )
    Spacer(Modifier.width(8.dp))
    Column(Modifier.weight(1f)) {
      Text(
        toolId,
        fontFamily = NovaMono,
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurface,
        maxLines = 1,
      )
      Text(
        if (writeLike) "Write action: review before allowing" else "Read action: low risk",
        style = MaterialTheme.typography.labelSmall,
        color = scheme.onSurfaceVariant,
      )
    }
    TextButton(
      onClick = {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        onDismiss()
      },
      modifier = Modifier.heightIn(min = MinTouchTarget),
    ) { Text("Dismiss", color = scheme.onSurfaceVariant) }
    TextButton(
      onClick = {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        onAllow()
      },
      modifier = Modifier.heightIn(min = MinTouchTarget),
    ) { Text("Allow", color = scheme.primary, fontWeight = FontWeight.Bold) }
  }
}

/**
 * Relative time for a backend ISO timestamp: "just now", "12m", "3h",
 * "Yesterday", "4 Mar". Returns null when the row has no timestamp (older rows
 * predate the field) — the caller then renders nothing rather than a fake time.
 */
fun novaRelativeTime(iso: String?): String? {
  if (iso.isNullOrBlank()) return null
  val then = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return null
  val now = java.time.Instant.now()
  val minutes = java.time.Duration.between(then, now).toMinutes()
  val zone = java.time.ZoneId.systemDefault()
  val date = then.atZone(zone).toLocalDate()
  val today = now.atZone(zone).toLocalDate()
  return when {
    minutes < 1 -> "just now"
    minutes < 60 -> "${minutes}m ago"
    minutes < 60 * 24 -> "${minutes / 60}h ago"
    date == today.minusDays(1) -> "Yesterday"
    minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
    else -> date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
  }
}

/**
 * Clock time for a message. Uses the server timestamp when present; a message
 * still streaming has none yet, so the caller passes the send time it captured.
 */
fun novaClockTime(iso: String?): String? {
  if (iso.isNullOrBlank()) return null
  val then = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return null
  return then.atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}

/**
 * User-facing copy for a backend error code. Turns "stream_failed" into a
 * sentence a person can act on, and keeps the raw code out of the interface.
 */
fun novaErrorText(code: String?): String = when (code?.lowercase()) {
  "unauthenticated" -> "Your session expired. Sign in again to continue."
  "rate_limited" -> "Too many requests just now. Wait a moment and retry."
  "network" -> "No connection to Nova. Check your network and retry."
  "stream_failed", "stream_error" -> "The reply stopped early. Retry returns you to it."
  "upload_failed" -> "That file did not upload. Files must be under 20 MB."
  "file_too_large" -> "That file is over the 20 MB limit."
  null, "" -> "Something went wrong. Retry returns you to it."
  else -> "Something went wrong ($code). Retry returns you to it."
}
/**
 * Glass circle button: 48dp (MinTouchTarget), translucent fill, hairline edge,
 * scheme ink — one visual for every top-bar action: hamburger, compose, dots,
 * back, search.
 */
@Composable
fun SpecCircleButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  description: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onBlack: Boolean = true,
) {
  val scheme = MaterialTheme.colorScheme
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = novaGlassFill(),
    border = BorderStroke(1.dp, novaGlassEdge()),
    modifier = modifier.size(MinTouchTarget).semantics { role = Role.Button; contentDescription = description },
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
      Icon(icon, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(20.dp))
    }
  }
}

/**
 * Spec composer accent button (S02/S05/S08/S09): violet circle with three
 * states — waveform bars (voice idle) → up arrow (send ready) → stop square
 * (streaming). Drawn with boxes/Icons, no image assets. 48dp per MinTouchTarget.
 */
@Composable
fun SpecAccentButton(
  state: SpecAccentState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val container = when (state) {
    SpecAccentState.SEND_READY, SpecAccentState.VOICE_IDLE, SpecAccentState.STOP ->
      MaterialTheme.colorScheme.primary
  }
  FilledIconButton(
    onClick = onClick,
    enabled = enabled,
    colors = IconButtonDefaults.filledIconButtonColors(
      containerColor = container,
      contentColor = Color.White,
      disabledContainerColor = container.copy(alpha = 0.45f),
    ),
    modifier = modifier.size(MinTouchTarget),
  ) {
    when (state) {
      SpecAccentState.VOICE_IDLE -> Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        listOf(8.dp, 13.dp, 9.dp).forEach { h ->
          Box(Modifier.width(2.5.dp).height(h).clip(RoundedCornerShape(NovaRadius.hair)).background(Color.White))
        }
      }
      SpecAccentState.SEND_READY -> Icon(
        androidx.compose.material.icons.Icons.Default.ArrowDownward,
        contentDescription = "Send",
        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 180f },
      )
      SpecAccentState.STOP -> Box(
        Modifier.size(12.dp).clip(RoundedCornerShape(NovaRadius.hair)).background(Color.White),
      )
    }
  }
}

enum class SpecAccentState { VOICE_IDLE, SEND_READY, STOP }

/**
 * Settings row card: glass fill, hairline edge, 56dp minimum, 24dp leading
 * icon, scheme label/subtitle, optional trailing affordance.
 */
@Composable
fun SpecSettingsRow(
  label: String,
  onClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  labelColor: Color = MaterialTheme.colorScheme.onSurface,
) {
  val scheme = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(NovaRadius.md)
  val content: @Composable () -> Unit = {
    Row(
      Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      leading?.let { Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { it() }; Spacer(Modifier.width(12.dp)) }
      Column(Modifier.weight(1f)) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Normal, color = labelColor, maxLines = 1)
        subtitle?.let {
          Spacer(Modifier.height(2.dp))
          Text(it, fontSize = 14.sp, color = scheme.onSurfaceVariant, maxLines = 2)
        }
      }
      trailing?.let { Spacer(Modifier.width(8.dp)); it() }
    }
  }
  if (onClick != null) {
    Surface(
      onClick = onClick,
      shape = shape,
      color = novaGlassFill(),
      border = BorderStroke(1.dp, novaGlassEdge()),
      modifier = modifier.fillMaxWidth().semantics { role = Role.Button },
    ) { content() }
  } else {
    Surface(
      shape = shape,
      color = novaGlassFill(),
      border = BorderStroke(1.dp, novaGlassEdge()),
      modifier = modifier.fillMaxWidth(),
    ) { content() }
  }
}

/** Section label: 15sp tertiary tone floating above a card group. */
@Composable
fun SpecSectionLabel(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    modifier = modifier,
    fontSize = 15.sp,
    fontWeight = FontWeight.Normal,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** Spec action icon row (S08): 24dp glyphs #B3B3B3, ~40dp spacing, left-aligned. */
@Composable
fun SpecActionRow(
  onCopy: () -> Unit,
  onLike: () -> Unit = {},
  onDislike: () -> Unit = {},
  onSpeak: () -> Unit,
  onShare: () -> Unit,
  onMore: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    SpecMiniAction(Icons.Default.ContentCopy, "Copy", onCopy)
    SpecMiniAction(Icons.Default.ThumbUp, "Like", onLike)
    SpecMiniAction(Icons.Default.ThumbDown, "Dislike", onDislike)
    SpecMiniAction(Icons.AutoMirrored.Filled.VolumeUp, "Read aloud", onSpeak)
    SpecMiniAction(Icons.Default.Share, "Share", onShare)
    SpecMiniAction(Icons.Default.MoreVert, "More", onMore)
  }
}

@Composable
private fun SpecMiniAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  description: String,
  onClick: () -> Unit,
) {
  IconButton(onClick = onClick, modifier = Modifier.size(MinTouchTarget)) {
    Icon(
      icon,
      contentDescription = description,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(24.dp),
    )
  }
}

@Composable
fun NovaTimelineStep(
  index: Int,
  label: String,
  state: NovaTone,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val dot = when (state) {
    NovaTone.Success -> scheme.secondary
    NovaTone.Accent -> scheme.primary
    NovaTone.Danger -> scheme.error
    else -> novaFaint()
  }
  Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(dot),
    )
    Spacer(Modifier.width(10.dp))
    Text(
      "${index + 1}.  $label",
      style = MaterialTheme.typography.bodyMedium,
      color = if (state == NovaTone.Muted) scheme.onSurfaceVariant else scheme.onSurface,
      modifier = Modifier.weight(1f),
    )
  }
}
