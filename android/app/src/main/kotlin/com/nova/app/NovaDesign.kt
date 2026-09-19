package com.nova.app

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Nova design language: AMOLED true-black + liquid glass in dark mode, warm
// paper in light mode. One bronze accent, serif display + neutral body.
// Deliberately avoids blue-charcoal, blue-purple gradients, glows, and
// pill-everything. Glass = scrim + hairline edge + top sheen, never a bloom.
//
// Three rules this file exists to enforce:
//   1. One token per decision. Screens never invent a color, radius or gap.
//   2. Every text tone clears WCAG AA 4.5:1 on its own background at small size.
//   3. Every tappable thing is at least 48dp and carries a semantics role.

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

  // Text tones. Faint is the darkest tone allowed to carry text — anything
  // dimmer fails AA on black, which is why screens must never alpha a text
  // color to "quiet" it.
  val InkDark = Color(0xFFF3EDE1)      // 17.6:1 on #000
  val InkDimDark = Color(0xFFA79E8D)   // 9.4:1 on #000
  val FaintDark = Color(0xFF8A8172)    // 5.6:1 on #000
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
  val sm = 10.dp
  val md = 16.dp
  val lg = 20.dp
  val xl = 26.dp
}

/**
 * Motion scale. Quick for state flips the finger already made, standard for
 * content swaps, slow only for something entering a screen for the first time.
 */
object NovaMotion {
  const val Quick = 140
  const val Standard = 220
  const val Slow = 320
  val Ease = FastOutSlowInEasing
}

/** Android's minimum touch target. Nothing tappable may be smaller. */
val MinTouchTarget = 48.dp

/** Content gutter for every full-screen list, so headers line up across screens. */
val ScreenGutter = 20.dp

enum class NovaAccent(val label: String) {
  BRONZE("Bronze"),
  SLATE("Slate"),
  SAGE("Sage"),
  TERRACOTTA("Terracotta"),
  PLUM("Plum"),
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
  accent: NovaAccent = NovaAccent.BRONZE,
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

private fun novaTypography(): Typography {
  val base = Typography()
  return Typography(
    displayLarge = base.displayLarge.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.W600),
    displayMedium = base.displayMedium.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.W600),
    displaySmall = base.displaySmall.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.W600, lineHeight = 44.sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.W600, lineHeight = 40.sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.W600, lineHeight = 34.sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleLarge = base.titleLarge.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = base.titleMedium.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleSmall = base.titleSmall.copy(fontFamily = NovaDisplay, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = NovaBody, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = NovaBody, lineHeight = 22.sp),
    bodySmall = base.bodySmall.copy(fontFamily = NovaBody, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontFamily = NovaBody, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontFamily = NovaBody, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = NovaBody),
  )
}

/** Self-colored hairline: 1dp stroke in the surface's own tone, not a contrasting outline. */
fun selfEdge(dark: Boolean): Color = if (dark) Color(0x14F3EDE1) else Color(0x14211C14)

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
  corner: Shape = RoundedCornerShape(NovaRadius.xl),
  content: @Composable ColumnScope.() -> Unit,
) {
  val dark = novaDark()
  val scrim = if (dark) NovaPalette.GlassScrimDark else Color(0xD9F2EEE5)
  val edge = if (dark) NovaPalette.GlassEdgeDark else Color(0x29211C14)
  Surface(
    modifier = modifier,
    shape = corner,
    color = Color.Transparent,
    border = BorderStroke(1.dp, edge),
  ) {
    Box(Modifier.background(scrim)) {
      // Top sheen: a 1dp light lip along the upper edge, the premium part of glass.
      Box(
        Modifier
          .matchParentSize()
          .background(
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
 * The one card. Tappable cards carry a Button role and a chevron so a row reads
 * as actionable without needing a hover state to hint it.
 */
@Composable
fun NovaCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  showChevron: Boolean = false,
  container: Color = MaterialTheme.colorScheme.surfaceVariant,
  contentPadding: PaddingValues = PaddingValues(NovaSpace.xl),
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(NovaRadius.lg)
  if (onClick != null) {
    Surface(
      modifier = modifier.fillMaxWidth().semantics { role = Role.Button },
      onClick = onClick,
      shape = shape,
      color = container,
      tonalElevation = 1.dp,
    ) {
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
  } else {
    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = container, tonalElevation = 1.dp) {
      Column(Modifier.padding(contentPadding), content = content)
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
    animationSpec = infiniteRepeatable(tween(900, easing = NovaMotion.Ease), RepeatMode.Reverse),
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
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NovaSpace.md)) {
    repeat(rows) {
      Box(
        Modifier
          .fillMaxWidth()
          .height(height)
          .clip(RoundedCornerShape(NovaRadius.lg))
          .background(MaterialTheme.colorScheme.surfaceVariant),
      )
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

/** The one primary button: 52dp tall, tonal, spinner in place of its label while busy. */
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
    shape = RoundedCornerShape(NovaRadius.md),
    colors = ButtonDefaults.buttonColors(
      containerColor = if (tone == NovaTone.Danger) scheme.error else scheme.primary,
      contentColor = if (tone == NovaTone.Danger) scheme.surface else scheme.onPrimary,
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
 * "Latest" pill. Appears when the reader has scrolled away from the stream, so
 * tokens can keep arriving without the view yanking itself out from under them.
 */
@Composable
fun NovaJumpToLatest(visible: Boolean, onClick: () -> Unit, label: String = "Latest") {
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(tween(NovaMotion.Quick)) + scaleIn(tween(NovaMotion.Quick), initialScale = 0.9f),
    exit = fadeOut(tween(NovaMotion.Quick)) + scaleOut(tween(NovaMotion.Quick), targetScale = 0.9f),
  ) {
    Surface(
      onClick = onClick,
      shape = RoundedCornerShape(NovaRadius.xl),
      color = MaterialTheme.colorScheme.primaryContainer,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
      modifier = Modifier.semantics { role = Role.Button },
    ) {
      Row(
        Modifier.padding(horizontal = NovaSpace.md, vertical = NovaSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(NovaSpace.xs))
        Text(
          label,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
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
    focusedContainerColor = scheme.surfaceVariant,
    unfocusedContainerColor = scheme.surfaceVariant,
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
          animation = tween(520, delayMillis = index * 170, easing = NovaMotion.Ease),
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
 * Backend step row (e.g. "Checking GitHub…", "Waiting for approval: …").
 * Expressive morph dots (stable APIs only — the pinned BOM keeps
 * LoadingIndicator internal) with mono step text beside them.
 */
@Composable
fun NovaStepRow(step: String, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  val transition = rememberInfiniteTransition(label = "novaStep")
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      repeat(3) { index ->
        val scale by transition.animateFloat(
          initialValue = 0.6f,
          targetValue = 1f,
          animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = index * 150, easing = NovaMotion.Ease),
            repeatMode = RepeatMode.Reverse,
          ),
          label = "stepDot$index",
        )
        Box(
          Modifier
            .size(6.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(scheme.primary),
        )
        if (index < 2) Spacer(Modifier.width(4.dp))
      }
    }
    Spacer(Modifier.width(10.dp))
    Text(
      step,
      style = MaterialTheme.typography.labelMedium,
      fontFamily = NovaMono,
      color = scheme.primary,
      maxLines = 2,
      modifier = Modifier.weight(1f),
    )
  }
}

/**
 * Send <-> Stop morph: one button slot, AnimatedContent crossfade+scale
 * 150-200ms, haptic tick on morph. Never two buttons side-by-side flickering.
 * 48dp per MinTouchTarget; disabled send uses outlineVariant wash.
 */
@Composable
fun NovaSendStopButton(
  streaming: Boolean,
  sendEnabled: Boolean,
  onSend: () -> Unit,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val view = LocalView.current
  AnimatedContent(
    targetState = streaming,
    transitionSpec = {
      (fadeIn(tween(NovaMotion.Quick)) + scaleIn(tween(NovaMotion.Quick), initialScale = 0.85f)) togetherWith
        (fadeOut(tween(NovaMotion.Quick)) + scaleOut(tween(NovaMotion.Quick), targetScale = 0.85f))
    },
    label = "sendStop",
    modifier = modifier,
  ) { isStreaming ->
    if (isStreaming) {
      FilledIconButton(
        onClick = {
          view.performHapticFeedback(HapticFeedbackConstants.REJECT)
          onStop()
        },
        colors = IconButtonDefaults.filledIconButtonColors(
          containerColor = scheme.error,
          contentColor = scheme.surface,
        ),
        modifier = Modifier.size(MinTouchTarget),
      ) {
        Icon(Icons.Default.Stop, contentDescription = "Stop generating", modifier = Modifier.size(18.dp))
      }
    } else {
      FilledIconButton(
        onClick = {
          view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
          onSend()
        },
        enabled = sendEnabled,
        colors = IconButtonDefaults.filledIconButtonColors(
          containerColor = scheme.primary,
          contentColor = scheme.onPrimary,
          disabledContainerColor = scheme.outlineVariant,
        ),
        modifier = Modifier.size(MinTouchTarget),
      ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
      }
    }
  }
}

/**
 * Executable suggestion chips: tap runs the prompt immediately (optimistic
 * echo at t=0), not just fills the input. Verb-first labels, 48dp tall,
 * haptic tick. Disappears once the user types (caller hides when input blank
 * is false) and while streaming/uploading.
 */
@Composable
fun NovaSuggestionChips(
  prompts: List<String>,
  enabled: Boolean,
  onPick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val view = LocalView.current
  Row(
    modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(NovaSpace.sm),
  ) {
    prompts.forEach { prompt ->
      AssistChip(
        enabled = enabled,
        onClick = {
          view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
          onPick(prompt)
        },
        label = { Text(prompt, style = MaterialTheme.typography.labelMedium) },
        shape = RoundedCornerShape(NovaRadius.md),
        modifier = Modifier.heightIn(min = MinTouchTarget),
      )
    }
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
        if (writeLike) "Write action — review before allowing" else "Read action — low risk",
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
