package com.nova.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.app.data.UiBlockDto

// §45 generative UI. Blocks arrive from present_ui (model-authored) or from
// allowlisted tool results, validated server-side; here they render as typed
// cards. Design contract:
//   1. Every text color is an explicit scheme token — never inherited, so the
//      card stays readable on the #202020 panel in dark and on paper in light.
//   2. Hierarchy comes from type and structure (big values, mono data, table
//      rules, tonal bars), never from a glow, a gradient or a saturated badge.
//   3. Content is visible by default: nothing waits on an animation. Motion
//      only moves things already on screen (bar fills, chart columns drawing
//      to their measured height) — never an opacity gate over live text.
//   4. Ten block types share one shell but not one body: each type has its own
//      silhouette so a dashboard of cards reads as composed data, not a stack
//      of identical boxes.

@Composable
fun GenerativeUiRenderer(
  blocks: List<UiBlockDto>,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit = {},
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(NovaSpace.md),
  ) {
    blocks.forEach { block ->
      key(block.id.ifBlank { "${block.type}-${block.hashCode()}" }) {
        when (block.type) {
          "summary" -> UiSummaryCard(block, clipboard, onOpen)
          "metrics" -> UiMetricsCard(block, clipboard, onOpen)
          "list" -> UiListCard(block, clipboard, onOpen)
          "table" -> UiTableCard(block, clipboard, onOpen)
          "progress" -> UiProgressCard(block, clipboard, onOpen)
          "timeline" -> UiTimelineCard(block, clipboard, onOpen)
          "comparison" -> UiComparisonCard(block, clipboard, onOpen)
          "code" -> UiCodeCard(block, clipboard, onOpen)
          "chart" -> UiChartCard(block, clipboard, onOpen)
          "links" -> UiLinksCard(block, clipboard, onOpen)
          // Unknown future types stay invisible rather than half-rendered.
        }
      }
    }
  }
}

private fun uiTypeIcon(type: String) = when (type) {
  "metrics", "chart" -> Icons.Default.BarChart
  "list" -> Icons.Default.FormatListBulleted
  "table" -> Icons.Default.TableChart
  "progress" -> Icons.Default.Timeline
  "timeline" -> Icons.Default.History
  "comparison" -> Icons.Default.CompareArrows
  "code" -> Icons.Default.Code
  "links" -> Icons.Default.Link
  else -> Icons.Default.Article
}

private fun uiTypeLabel(type: String) = when (type) {
  "metrics" -> "Metrics"
  "list" -> "List"
  "table" -> "Table"
  "progress" -> "Progress"
  "timeline" -> "Timeline"
  "comparison" -> "Comparison"
  "code" -> "Code"
  "chart" -> "Chart"
  "links" -> "Links"
  else -> "Summary"
}

/** Plain-text rendering of a block, used by the copy action. */
private fun blockCopyText(block: UiBlockDto): String = when (block.type) {
  "table" ->
    (listOf(block.columns.joinToString("\t")) + block.rows.map { it.joinToString("\t") })
      .joinToString("\n")
  "list" ->
    block.items.joinToString("\n") { item ->
      listOfNotNull(item.label, item.secondary, item.value).joinToString(" | ")
    }
  "metrics" ->
    block.metrics.joinToString("\n") { m ->
      if (m.change.isNullOrBlank()) "${m.label}: ${m.value}" else "${m.label}: ${m.value} (${m.change})"
    }
  "summary" ->
    buildString {
      append(block.body.orEmpty())
      block.metadata.forEach { item ->
        append('\n')
        append(item.label)
        append(": ")
        append(item.value?.takeIf { it.isNotBlank() } ?: item.secondary.orEmpty())
      }
    }
  "progress" -> {
    val fractions = progressFractions(block.progress)
    block.progress.mapIndexed { i, p ->
      val pct = percentLabel(fractions.getOrElse(i) { 0f })
      if (p.detail.isNullOrBlank()) "${p.label}: $pct" else "${p.label}: $pct (${p.detail})"
    }.joinToString("\n")
  }
  "timeline" ->
    block.steps.joinToString("\n") { s ->
      listOfNotNull("[${s.status}]", s.label, s.detail, s.value).joinToString(": ")
    }
  "comparison" -> buildString {
    appendLine("${block.compare?.leftLabel.orEmpty()}\t${block.compare?.rightLabel.orEmpty()}")
    block.compare?.rows?.forEach { r -> appendLine("${r.label}\t${r.left}\t${r.right}") }
  }
  "code" -> block.code.orEmpty()
  "chart" ->
    block.chart?.points.orEmpty().joinToString("\n") { p ->
      block.chart?.unit?.let { "${p.label}: ${p.value} $it" } ?: "${p.label}: ${p.value}"
    }
  "links" -> block.links.joinToString("\n") { "${it.title}\t${it.url}" }
  else -> block.body ?: block.title.orEmpty()
}

/** Display label for an already-normalized 0–1 fraction — never re-derives the scale. */
private fun percentLabel(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100f).toInt()}%"

/** Progress blocks sent entirely in 0–1 are fractions; anything above 1 is already percent. */
private fun progressFractions(items: List<com.nova.app.data.UiProgressItemDto>): List<Float> {
  if (items.isEmpty()) return emptyList()
  val allFractional = items.all { it.value <= 1f }
  return items.map { item ->
    val pct = if (allFractional) item.value * 100f else item.value
    (pct.coerceIn(0f, 100f)) / 100f
  }
}

/**
 * Shared shell: panel + header (bare type icon, title, functional meta, action
 * icons) + body. Header actions are real 48dp icon buttons with the model's
 * label as their content description, pulled 8dp in so the icon optically
 * aligns with the card's text column. Content is visible by default — the only
 * hidden state is a user collapse via an explicit expand action, and even then
 * the header stays so the card never vanishes.
 */
@Composable
private fun UiCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
  meta: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  var collapsed by remember(block.id) { mutableStateOf(false) }
  val ink = MaterialTheme.colorScheme.onSurface
  val dim = MaterialTheme.colorScheme.onSurfaceVariant

  GlassPanel(
    modifier = Modifier.fillMaxWidth(),
    corner = RoundedCornerShape(NovaRadius.lg),
  ) {
    Column(Modifier.fillMaxWidth().padding(NovaSpace.lg)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          uiTypeIcon(block.type),
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = novaFaint(),
        )
        Spacer(Modifier.width(NovaSpace.sm))
        Text(
          block.title ?: uiTypeLabel(block.type),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = ink,
          modifier = Modifier.weight(1f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (meta != null) {
          Text(
            meta,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = NovaMono,
            color = novaFaint(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = NovaSpace.sm, end = NovaSpace.xs),
          )
        }
        if (block.actions.isNotEmpty()) {
          Row(Modifier.offset(x = -NovaSpace.sm)) {
            block.actions.forEach { action ->
              // "filter" has no generic meaning across block types — no button
              // is drawn for it rather than shipping a control that does nothing.
              if (action.type == "filter") return@forEach
              IconButton(
                onClick = {
                  when (action.type) {
                    "copy" -> clipboard.setText(AnnotatedString(action.value ?: blockCopyText(block)))
                    "open" -> action.value?.let { url -> if (url.startsWith("http")) onOpen(url) }
                    "expand" -> collapsed = !collapsed
                  }
                },
                modifier = Modifier.size(MinTouchTarget),
              ) {
                Icon(
                  when (action.type) {
                    "copy" -> Icons.Default.ContentCopy
                    "open" -> Icons.Default.OpenInNew
                    else -> if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess
                  },
                  contentDescription = action.label,
                  modifier = Modifier.size(18.dp),
                  tint = dim,
                )
              }
            }
          }
        }
      }
      Spacer(Modifier.height(NovaSpace.md))
      if (!collapsed) content()
    }
  }
}

/** Label/value pair used by summary facts and comparison cells. */
@Composable
private fun factValue(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    fontFamily = NovaMono,
    fontWeight = FontWeight.Medium,
    color = MaterialTheme.colorScheme.onSurface,
    textAlign = TextAlign.End,
    modifier = modifier,
  )
}

/** Prose + key/value facts. Values are mono (they are data), labels stay quiet. */
@Composable
private fun UiSummaryCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
) = UiCard(block, clipboard, onOpen, meta = block.metadata.takeIf { it.isNotEmpty() }?.size?.let { "$it facts" }) {
  val ink = MaterialTheme.colorScheme.onSurface
  val dim = MaterialTheme.colorScheme.onSurfaceVariant

  Text(
    block.body.orEmpty(),
    style = MaterialTheme.typography.bodyMedium,
    color = ink,
    modifier = Modifier.fillMaxWidth(),
  )

  if (block.metadata.isNotEmpty()) {
    Spacer(Modifier.height(NovaSpace.md))
    Column(Modifier.fillMaxWidth()) {
      block.metadata.forEachIndexed { i, item ->
        if (i > 0) {
          HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = NovaSpace.sm),
          horizontalArrangement = Arrangement.spacedBy(NovaSpace.md),
          verticalAlignment = Alignment.Top,
        ) {
          Text(
            item.label,
            style = MaterialTheme.typography.labelMedium,
            color = dim,
            modifier = Modifier.weight(1f),
          )
          val shown = item.value?.takeIf { it.isNotBlank() } ?: item.secondary.orEmpty()
          if (shown.isNotEmpty()) {
            factValue(shown, Modifier.weight(1f))
          }
        }
      }
    }
  }
}

/**
 * Numbers get the loudest type on the card: label line, big value, signed
 * delta. Up to three metrics split the card into equal columns so the rows
 * share one grid; more than three become a measured horizontal strip.
 */
@Composable
private fun UiMetricsCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
) {
  val metrics = block.metrics.filter { it.value.isNotBlank() }
  UiCard(block, clipboard, onOpen, meta = if (metrics.size != block.metrics.size) "${metrics.size} shown" else null) {
    val ink = MaterialTheme.colorScheme.onSurface
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    if (metrics.isEmpty()) return@UiCard
    val fitsOnGrid = metrics.size <= 3

    Row(
      modifier = if (fitsOnGrid) Modifier.fillMaxWidth()
      else Modifier.horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(NovaSpace.lg),
    ) {
      metrics.forEach { metric ->
        val change = metric.change?.takeIf { it.isNotBlank() }
        val changeTone = when {
          change == null -> null
          change.trim().startsWith("-") -> NovaTone.Danger
          change.trim().startsWith("+") -> NovaTone.Success
          else -> NovaTone.Muted
        }
        // One TalkBack target per metric: label + value + delta read together.
        val cellModifier = if (fitsOnGrid) Modifier.weight(1f)
        else Modifier.widthIn(min = 104.dp, max = 180.dp)
        Column(
          modifier = cellModifier.semantics(mergeDescendants = true) {
            contentDescription = buildString {
              append(metric.label)
              append(": ")
              append(metric.value)
              if (change != null) {
                append(", ")
                append(change)
              }
            }
          },
        ) {
          Text(
            metric.label,
            style = MaterialTheme.typography.labelSmall,
            color = dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(Modifier.height(3.dp))
          Text(
            metric.value,
            style = MaterialTheme.typography.headlineSmall.merge(NovaTabular),
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (change != null && changeTone != null) {
            Spacer(Modifier.height(2.dp))
            Text(
              change,
              style = MaterialTheme.typography.labelSmall,
              fontFamily = NovaMono,
              fontWeight = FontWeight.Medium,
              color = novaToneColor(changeTone),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

/** Scannable rows: mono index, label + supporting line, mono datum on the right, hairline rhythm. */
@Composable
private fun UiListCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
) = UiCard(block, clipboard, onOpen, meta = "${block.items.size} items") {
  val ink = MaterialTheme.colorScheme.onSurface
  val dim = MaterialTheme.colorScheme.onSurfaceVariant
  val edge = MaterialTheme.colorScheme.outlineVariant

  block.items.forEachIndexed { i, item ->
    val index = i + 1
    if (i > 0) {
      HorizontalDivider(thickness = 1.dp, color = edge)
    }
    val secondary = item.secondary
    val value = item.value
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Text(
        if (index < 10) "0$index" else index.toString(),
        fontFamily = NovaMono,
        style = MaterialTheme.typography.labelSmall,
        color = novaFaint(),
        modifier = Modifier.width(24.dp).padding(top = 5.dp),
      )
      Spacer(Modifier.width(NovaSpace.md))
      Column(Modifier.weight(1f)) {
        Text(
          item.label,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color = ink,
        )
        if (!secondary.isNullOrBlank()) {
          Text(
            secondary,
            style = MaterialTheme.typography.bodySmall,
            color = dim,
          )
        }
      }
      if (!value.isNullOrBlank()) {
        Spacer(Modifier.width(NovaSpace.md))
        Text(
          value,
          style = MaterialTheme.typography.labelMedium,
          fontFamily = NovaMono,
          fontWeight = FontWeight.Medium,
          color = ink,
          textAlign = TextAlign.End,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.widthIn(max = 150.dp),
        )
      }
    }
  }
}

/**
 * Real table: mono header fixed above a scrolling body, content-measured
 * column widths, faint zebra rows spanning the full table width. Long tables
 * scroll vertically under a header that stays put; empty tables say so.
 */
@Composable
private fun UiTableCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
) = UiCard(
  block, clipboard, onOpen,
  meta = if (block.columns.isNotEmpty()) "${block.columns.size}×${block.rows.size}" else null,
) {
  val ink = MaterialTheme.colorScheme.onSurface
  val dim = MaterialTheme.colorScheme.onSurfaceVariant
  val edge = MaterialTheme.colorScheme.outlineVariant
  val columns = block.columns
  if (columns.isEmpty()) return@UiCard
  if (block.rows.isEmpty()) {
    Text("No rows", style = MaterialTheme.typography.bodySmall, color = dim)
    return@UiCard
  }

  // Measured once per block: mono-ish estimate at 14sp + cell padding,
  // clamped so one monster column cannot swallow the viewport.
  val widths = remember(block.id, columns, block.rows) {
    columns.mapIndexed { c, head ->
      val longest = (listOf(head) + block.rows.mapNotNull { it.getOrNull(c) }).maxOf { it.length }
      ((longest * 7.4f) + 24f).dp.coerceIn(84.dp, 240.dp)
    }
  }
  val totalWidth = widths.fold(0.dp) { acc, w -> acc + w }
  val vScroll = rememberScrollState()

  Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
    // Header sits outside the vertical scroll so column titles stay readable.
    Row(Modifier.width(totalWidth)) {
      columns.forEachIndexed { c, head ->
        Text(
          head.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          fontFamily = NovaMono,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.6.sp,
          color = dim,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .width(widths[c])
            .padding(horizontal = 10.dp, vertical = 8.dp),
        )
      }
    }
    HorizontalDivider(thickness = 1.dp, color = edge, modifier = Modifier.width(totalWidth))
    Column(
      Modifier.width(totalWidth)
        .heightIn(max = 360.dp)
        .verticalScroll(vScroll),
    ) {
      block.rows.forEachIndexed { r, row ->
        Row(
          modifier = Modifier
            .width(totalWidth)
            .background(if (r % 2 == 1) ink.copy(alpha = 0.035f) else Color.Transparent),
        ) {
          columns.forEachIndexed { c, _ ->
            Text(
              row.getOrNull(c).orEmpty(),
              style = MaterialTheme.typography.bodySmall,
              color = ink,
              maxLines = 4,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier
                .width(widths[c])
                .padding(horizontal = 10.dp, vertical = 9.dp),
            )
          }
        }
      }
    }
  }
}

/**
 * Percent bars: the track is always on screen, only the fill animates its
 * width to the measured target (stable caps, full track, one smooth ease —
 * never a scaleY that rounds its own caps mid-flight).
 */
@Composable
private fun UiProgressCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
) = UiCard(block, clipboard, onOpen, meta = if (block.progress.size > 1) "${block.progress.size} goals" else null) {
  val ink = MaterialTheme.colorScheme.onSurface
  val dim = MaterialTheme.colorScheme.onSurfaceVariant
  val items = block.progress
  if (items.isEmpty()) return@UiCard
  val fractions = progressFractions(items)

  items.forEachIndexed { i, item ->
    if (i > 0) Spacer(Modifier.height(NovaSpace.md))
    val fraction = fractions.getOrElse(i) { 0f }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        item.label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = ink,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!item.detail.isNullOrBlank()) {
        Text(
          item.detail,
          style = MaterialTheme.typography.labelSmall,
          color = dim,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = NovaSpace.sm),
        )
      }
      Text(
        percentLabel(fraction),
        style = MaterialTheme.typography.labelMedium,
        fontFamily = NovaMono,
        fontWeight = FontWeight.SemiBold,
        color = ink,
      )
    }
    Spacer(Modifier.height(6.dp))
    ProgressTrack(fraction = fraction, trackColor = ink.copy(alpha = 0.08f))
  }
}

@Composable
private fun ProgressTrack(fraction: Float, trackColor: Color) {
  val fill = MaterialTheme.colorScheme.primary
  val animated = remember { Animatable(0f) }
  LaunchedEffect(fraction) {
    animated.animateTo(fraction.coerceIn(0f, 1f), tween(NovaMotion.Standard, easing = NovaMotion.Ease))
  }
  Box(
    Modifier
      .fillMaxWidth()
      .height(6.dp)
      .clip(RoundedCornerShape(NovaRadius.hair))
      .background(trackColor),
  ) {
    // Width, not scaleY: caps stay circular for the whole transition.
    Box(
      Modifier
        .fillMaxHeight()
        .fillMaxWidth(animated.value)
        .clip(RoundedCornerShape(NovaRadius.hair))
        .background(fill),
    )
  }
}

/**
 * Status timeline: meaningful dots (done / active / todo / error) on a
 * rounded-cap rail — a real process log, not a decorative numbered list. The
 * rail only exists where a next step exists (never under the last row).
 */
@Composable
private fun UiTimelineCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
) = UiCard(block, clipboard, onOpen, meta = "${block.steps.size} steps") {
  val ink = MaterialTheme.colorScheme.onSurface
  val dim = MaterialTheme.colorScheme.onSurfaceVariant
  val edge = MaterialTheme.colorScheme.outlineVariant
  val success = novaToneColor(NovaTone.Success)
  val accent = MaterialTheme.colorScheme.primary
  val danger = MaterialTheme.colorScheme.error
  val steps = block.steps
  if (steps.isEmpty()) return@UiCard

  steps.forEachIndexed { i, step ->
    val last = i == steps.lastIndex
    val status = step.status.lowercase()
    val dotColor = when (status) {
      "done" -> success
      "active" -> accent
      "error" -> danger
      else -> Color.Transparent
    }
    val hollow = status == "todo"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NovaSpace.md)) {
      // Rail column: dot + connector, fixed width so text starts on one axis.
      Column(
        Modifier.width(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (hollow) Color.Transparent else dotColor)
            .border(
              width = if (hollow) 1.5.dp else 0.dp,
              color = if (hollow) novaFaint() else Color.Transparent,
              shape = CircleShape,
            ),
        )
        if (!last) {
          Box(
            Modifier
              .width(2.dp)
              .height(28.dp)
              .clip(RoundedCornerShape(NovaRadius.hair))
              .background(if (status == "done") success.copy(alpha = 0.45f) else edge),
          )
        }
      }
      Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else NovaSpace.md)) {
        Row(verticalAlignment = Alignment.Top) {
          Text(
            step.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (status == "active") FontWeight.SemiBold else FontWeight.Medium,
            color = if (status == "error") danger else ink,
            modifier = Modifier.weight(1f),
          )
          if (!step.value.isNullOrBlank()) {
            Spacer(Modifier.width(NovaSpace.sm))
            Text(
              step.value,
              style = MaterialTheme.typography.labelSmall,
              fontFamily = NovaMono,
              color = novaFaint(),
              maxLines = 1,
            )
          }
        }
        if (!step.detail.isNullOrBlank()) {
          Text(
            step.detail,
            style = MaterialTheme.typography.bodySmall,
            color = dim,
          )
        }
      }
    }
  }
}

/**
 * Two labeled columns on ONE shared row grid: every label, left cell and right
 * cell sits on the same baseline across the card, equal column weights, header
 * row included — a comparison that never goes ragged when copy lengths differ.
 * The winner column is marked tonally (weight + accent ink on its header),
 * never with a saturated badge or a tinted surface.
 */
@Composable
private fun UiComparisonCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
) {
  val compare = block.compare
  val meta = compare?.let { "${it.rows.size} rows" }
  UiCard(block, clipboard, onOpen, meta = meta) {
    if (compare == null || compare.rows.isEmpty()) return@UiCard
    val ink = MaterialTheme.colorScheme.onSurface
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val edge = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val win = compare.winner
    val labelWeight = if (win == null) 1.1f else 1f
    val sideWeight = 1f

    // Header row
    Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Spacer(Modifier.weight(labelWeight))
      Text(
        compare.leftLabel,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = NovaMono,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        color = if (win == "left") accent else novaFaint(),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(sideWeight).padding(horizontal = NovaSpace.sm),
      )
      Text(
        compare.rightLabel,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = NovaMono,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        color = if (win == "right") accent else novaFaint(),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(sideWeight).padding(horizontal = NovaSpace.sm),
      )
    }
    HorizontalDivider(thickness = 1.dp, color = edge, modifier = Modifier.padding(top = 6.dp))

    compare.rows.forEachIndexed { i, row ->
      if (i > 0) HorizontalDivider(thickness = 1.dp, color = edge)
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          row.label,
          style = MaterialTheme.typography.labelMedium,
          color = dim,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .weight(labelWeight)
            .padding(start = 0.dp, end = NovaSpace.sm, top = 6.dp, bottom = 6.dp),
        )
        // Same weights as the header — every cell stays on one shared grid.
        Text(
          row.left.ifBlank { "—" },
          style = MaterialTheme.typography.labelMedium,
          color = if (win == "left") ink else dim,
          fontWeight = if (win == "left") FontWeight.SemiBold else FontWeight.Normal,
          textAlign = TextAlign.Center,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .weight(sideWeight)
            .padding(horizontal = NovaSpace.sm, vertical = 6.dp),
        )
        Text(
          row.right.ifBlank { "—" },
          style = MaterialTheme.typography.labelMedium,
          color = if (win == "right") ink else dim,
          fontWeight = if (win == "right") FontWeight.SemiBold else FontWeight.Normal,
          textAlign = TextAlign.Center,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .weight(sideWeight)
            .padding(horizontal = NovaSpace.sm, vertical = 6.dp),
        )
      }
    }
  }
}

/**
 * Source listing in an inset well (darker than the panel in dark, raised in
 * light) — mono, both-axis scroll, capped height. Not a fake window: no
 * traffic lights, no filename tab. The language lives in the header meta.
 */
@Composable
private fun UiCodeCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
) {
  val code = block.code.orEmpty()
  if (code.isBlank()) return
  UiCard(block, clipboard, onOpen, meta = block.language?.uppercase()) {
    val ink = MaterialTheme.colorScheme.onSurface
    val well = if (novaDark()) Color(0xFF161616) else NovaPalette.LightRaised
    val wellEdge = if (novaDark()) Color(0x29FFFFFF) else NovaPalette.LightLine

    Box(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(NovaRadius.sm))
        .background(well)
        .border(1.dp, wellEdge, RoundedCornerShape(NovaRadius.sm))
        .heightIn(max = 360.dp)
        .verticalScroll(rememberScrollState())
        .horizontalScroll(rememberScrollState())
        .padding(NovaSpace.md),
    ) {
      Text(
        code.trimEnd(),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = ink,
        softWrap = true,
      )
    }
  }
}

/**
 * Bar chart drawn as real data (Canvas): measured scale from min/max, tonal
 * bars, mono category labels under the axis. Columns draw their height once —
 * labels and values are on screen from the first frame.
 */
@Composable
private fun UiChartCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
) {
  val chart = block.chart
  val points = chart?.points.orEmpty()
  if (points.isEmpty()) return
  val unitLabel = chart?.unit
  val peak = points.maxOfOrNull { it.value }?.let { v -> formatChartNumber(v) }
  UiCard(block, clipboard, onOpen, meta = listOfNotNull(peak?.let { "max $it" }, unitLabel).joinToString(" · ").ifBlank { null }) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val edge = MaterialTheme.colorScheme.outlineVariant
    val negColor = novaToneColor(NovaTone.Danger)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(points) { anim.animateTo(1f, tween(NovaMotion.Standard, easing = NovaMotion.Ease)) }

    val values = points.map { it.value }
    val maxV = values.maxOrNull()?.coerceAtLeast(0f) ?: 1f
    val minV = values.minOrNull()?.coerceAtMost(0f) ?: 0f
    val span = (maxV - minV).takeIf { it != 0f } ?: 1f

    val description = points.joinToString(", ") { "${it.label} ${formatChartNumber(it.value)}" }

    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
      contentDescription = "Bar chart. $description"
    }) {
      androidx.compose.foundation.Canvas(
        Modifier
          .fillMaxWidth()
          .height(140.dp),
      ) {
        val n = points.size
        val gap = 10.dp.toPx()
        val slot = (size.width - gap * (n - 1)) / n
        val barW = slot * 0.62f
        val inset = (slot - barW) / 2f
        // Zero baseline in chart space.
        val zeroY = size.height * (maxV / span)
        points.forEachIndexed { i, p ->
          val x = i * (slot + gap) + inset
          val frac = (p.value / span)
          val barH = (size.height * kotlin.math.abs(frac)).coerceAtMost(size.height)
          val top = if (p.value >= 0f) zeroY - barH * anim.value else zeroY
          val height = barH * anim.value
          drawRect(
            color = if (p.value < 0f) negColor else accent,
            topLeft = Offset(x, top),
            size = Size(barW, height.coerceAtLeast(if (height > 0f) 2f else 0f)),
          )
        }
        // Baseline rule at zero.
        drawLine(
          color = edge,
          start = Offset(0f, zeroY),
          end = Offset(size.width, zeroY),
          strokeWidth = 1.dp.toPx(),
        )
      }
      Spacer(Modifier.height(6.dp))
      Row(Modifier.fillMaxWidth()) {
        points.forEach { p ->
          Text(
            p.label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = NovaMono,
            color = dim,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
        }
      }
      if (unitLabel != null) {
        Spacer(Modifier.height(2.dp))
        Text(
          unitLabel,
          style = MaterialTheme.typography.labelSmall,
          color = novaFaint(),
          modifier = Modifier.align(Alignment.End),
        )
      }
    }
  }
}

private fun formatChartNumber(v: Float): String =
  if (v == v.toLong().toFloat()) v.toLong().toString() else "%.1f".format(v)

/**
 * Openable sources: title + domain + trailing open glyph. The whole row is a
 * 48dp+ target; only http(s) URLs ever reach the intent (server enforces too).
 */
@Composable
private fun UiLinksCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
) = UiCard(block, clipboard, onOpen, meta = "${block.links.size} links") {
  val ink = MaterialTheme.colorScheme.onSurface
  val dim = MaterialTheme.colorScheme.onSurfaceVariant
  val edge = MaterialTheme.colorScheme.outlineVariant
  val links = block.links
  if (links.isEmpty()) return@UiCard

  links.forEachIndexed { i, link ->
    if (i > 0) HorizontalDivider(thickness = 1.dp, color = edge)
    val url = link.url
    val canOpen = url.startsWith("http://") || url.startsWith("https://")
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .then(
          if (canOpen) Modifier
            .clip(RoundedCornerShape(NovaRadius.sm))
            .clickable { onOpen(url) }
          else Modifier,
        )
        .padding(vertical = 10.dp, horizontal = if (canOpen) NovaSpace.sm else 0.dp)
        .semantics(mergeDescendants = true) {
          contentDescription = if (canOpen) "Open ${link.title}" else link.title
        },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(
          link.title,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color = ink,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (!link.secondary.isNullOrBlank()) {
          Text(
            link.secondary,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = NovaMono,
            color = dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        } else if (canOpen) {
          Text(
            url.removePrefix("https://").removePrefix("http://").substringBefore('/'),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = NovaMono,
            color = novaFaint(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (canOpen) {
        Spacer(Modifier.width(NovaSpace.sm))
        Icon(
          Icons.Default.OpenInNew,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = novaFaint(),
        )
      }
    }
  }
}
