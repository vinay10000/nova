package com.nova.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
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
//      rules), never from a glow, a gradient or a saturated badge.
//   3. Content is visible by default: nothing waits on an animation, and the
//      only hidden state is the model's own explicit "expand" action.

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
      when (block.type) {
        "summary" -> UiSummaryCard(block, clipboard, onOpen)
        "metrics" -> UiMetricsCard(block, clipboard, onOpen)
        "list" -> UiListCard(block, clipboard, onOpen)
        "table" -> UiTableCard(block, clipboard, onOpen)
        // Unknown future types stay invisible rather than half-rendered.
      }
    }
  }
}

private fun uiTypeIcon(type: String) = when (type) {
  "metrics" -> Icons.Default.BarChart
  "list" -> Icons.Default.FormatListBulleted
  "table" -> Icons.Default.TableChart
  else -> Icons.Default.Article
}

private fun uiTypeLabel(type: String) = when (type) {
  "metrics" -> "Metrics"
  "list" -> "List"
  "table" -> "Table"
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
        append(item.value ?: item.secondary.orEmpty())
      }
    }
  else -> block.body ?: block.title.orEmpty()
}

/**
 * Shared shell: panel + header (bare type icon, title, action icons) + body.
 * Header actions are real 48dp icon buttons with the model's label as their
 * content description, pulled 8dp in so the icon optically aligns with the
 * card's text column.
 */
@Composable
private fun UiCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  var expanded by remember(block.id, block.type) { mutableStateOf(false) }
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
        )
        if (block.actions.isNotEmpty()) {
          Row(Modifier.offset(x = -NovaSpace.sm)) {
            block.actions.forEach { action ->
              IconButton(
                onClick = {
                  when (action.type) {
                    "copy" -> clipboard.setText(AnnotatedString(action.value ?: blockCopyText(block)))
                    "open" -> action.value?.let(onOpen)
                    "expand", "filter" -> expanded = !expanded
                  }
                },
                modifier = Modifier.size(MinTouchTarget),
              ) {
                Icon(
                  when (action.type) {
                    "copy" -> Icons.Default.ContentCopy
                    "open" -> Icons.Default.OpenInNew
                    "filter" -> Icons.Default.FilterList
                    else -> if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore
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
      if (expanded || block.actions.none { it.type == "expand" }) content()
    }
  }
}

/** Prose + key/value facts. Values are mono (they are data), labels stay quiet. */
@Composable
private fun UiSummaryCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
) = UiCard(block, clipboard, onOpen) {
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
        ) {
          Text(
            item.label,
            style = MaterialTheme.typography.labelMedium,
            color = dim,
            modifier = Modifier.weight(1f),
          )
          val shown = item.value ?: item.secondary.orEmpty()
          if (shown.isNotEmpty()) {
            Text(
              shown,
              style = MaterialTheme.typography.labelMedium,
              fontFamily = NovaMono,
              fontWeight = FontWeight.Medium,
              color = ink,
              textAlign = TextAlign.End,
              modifier = Modifier.weight(1f),
            )
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
) = UiCard(block, clipboard, onOpen) {
  val ink = MaterialTheme.colorScheme.onSurface
  val dim = MaterialTheme.colorScheme.onSurfaceVariant
  val metrics = block.metrics
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
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = ink,
        )
        if (change != null && changeTone != null) {
          Spacer(Modifier.height(2.dp))
          Text(
            change,
            style = MaterialTheme.typography.labelSmall,
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

/** Scannable rows: mono index, label + supporting line, mono datum on the right. */
@Composable
private fun UiListCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
) = UiCard(block, clipboard, onOpen) {
  val ink = MaterialTheme.colorScheme.onSurface
  val dim = MaterialTheme.colorScheme.onSurfaceVariant

  block.items.forEachIndexed { i, item ->
    val index = i + 1
    val secondary = item.secondary
    val value = item.value
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
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
          modifier = Modifier.widthIn(max = 150.dp),
        )
      }
    }
  }
}

/**
 * Real table: mono header over a hairline rule, content-measured column
 * widths, faint zebra rows spanning the full table width. Rows shorter than
 * the header pad per column so every cell stays on its own grid line.
 */
@Composable
private fun UiTableCard(
  block: UiBlockDto,
  clipboard: ClipboardManager,
  onOpen: (String) -> Unit,
) = UiCard(block, clipboard, onOpen) {
  val ink = MaterialTheme.colorScheme.onSurface
  val dim = MaterialTheme.colorScheme.onSurfaceVariant
  val edge = MaterialTheme.colorScheme.outlineVariant
  val columns = block.columns
  if (columns.isEmpty()) return@UiCard

  // Measured once per block: mono-ish estimate at 14sp + cell padding,
  // clamped so one monster column cannot swallow the viewport.
  val widths = remember(block.id, columns, block.rows) {
    columns.mapIndexed { c, head ->
      val longest = (listOf(head) + block.rows.mapNotNull { it.getOrNull(c) }).maxOf { it.length }
      ((longest * 7.4f) + 24f).dp.coerceIn(84.dp, 240.dp)
    }
  }
  val totalWidth = widths.fold(0.dp) { acc, w -> acc + w }

  Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
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
            modifier = Modifier
              .width(widths[c])
              .padding(horizontal = 10.dp, vertical = 9.dp),
          )
        }
      }
    }
  }
}
