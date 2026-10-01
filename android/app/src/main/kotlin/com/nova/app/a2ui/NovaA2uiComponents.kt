package com.nova.app.a2ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.ui.A2uiComponent
import coil3.compose.AsyncImage
import com.nova.app.MinTouchTarget
import com.nova.app.NovaCard
import com.nova.app.NovaEyebrow
import com.nova.app.NovaGlass
import com.nova.app.NovaMono
import com.nova.app.NovaMotion
import com.nova.app.NovaPalette
import com.nova.app.NovaRadius
import com.nova.app.NovaSkeleton
import com.nova.app.NovaSpace
import com.nova.app.NovaStatusPill
import com.nova.app.NovaTabular
import com.nova.app.NovaTone
import com.nova.app.NovaDisplay
import com.nova.app.novaDark
import com.nova.app.novaFaint
import com.nova.app.novaGlassEdge
import com.nova.app.novaGlassFill

// §45b — Nova's A2UI components.
//
// These are the surfaces the agent can paint in chat. They exist because the
// generic Material catalog cannot express what a trip card actually is: a
// destination over dates over a flight number, with mono figures and no chrome.
// Every one of them obeys the same contract as the hand-rolled §45 blocks:
//
//   1. Colours are scheme tokens only. A card must stay legible on the #202020
//      panel in dark and on paper in light without a per-card theme.
//   2. Hierarchy comes from type size and mono figures, never a glow, gradient
//      or saturated badge.
//   3. Progressive rendering is real: every field is bound, not required-at-once.
//      A field that has not arrived renders as a skeleton bar, so a card fills
//      in the way the video does — the frame lands first, then the content.
//   4. Anything tappable is 48dp and carries a role and a spoken state.

// ── shared property vocabulary ───────────────────────────────────────────────
//
// Reused across components so the agent's schema stays small. A `dynamic`
// property is one the agent fills from the data model and that can change while
// the user is looking at it; a static one is baked into the component.

internal val PropEyebrow = A2uiProperty.string("eyebrow")
internal val PropTitle = A2uiProperty.dynamicString("title", true)
internal val PropSubtitle = A2uiProperty.dynamicString("subtitle")
internal val PropMeta = A2uiProperty.dynamicString("meta")
internal val PropAction = A2uiProperty.action("action")

/**
 * Reads a list-of-objects data binding into typed rows.
 *
 * The protocol hands dynamic values over as plain JSON, so this is the one
 * place that knows the shape. Returns empty rather than throwing: a malformed
 * row must not take the whole surface down, and the surface already has its
 * own error boundary.
 */
private fun Any?.a2uiRows(vararg keys: String): List<Map<String, Any?>> {
  @Suppress("UNCHECKED_CAST")
  val list = this as? List<Any?> ?: return emptyList()
  return list.mapNotNull { row ->
    val map = row as? Map<*, *> ?: return@mapNotNull null
    // A row is only useful if at least one of the requested keys is present.
    if (keys.none { map.containsKey(it) }) null
    else map.entries.associate { (k, v) -> k.toString() to v }
  }
}

private fun Map<String, Any?>.str(key: String): String? = this[key] as? String

/** A value that has not arrived yet draws as a bar, not as an empty string. */
@Composable
private fun PendingOr(value: String?, height: androidx.compose.ui.unit.Dp = 14.dp, width: androidx.compose.ui.unit.Dp = 120.dp) {
  if (value.isNullOrBlank()) NovaSkeleton(Modifier.width(width).height(height)) else Text(
    value,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurface,
  )
}

// ── NovaTrip: the destination card ───────────────────────────────────────────

/**
 * A trip in three lines: where, when, and the flight that gets you there.
 * The flight line is the one piece of hard data, so it is mono and tabular.
 */
internal object NovaTripComponent : A2uiComponent {
  private val DestProp = A2uiProperty.dynamicString("destination", true)
  private val DatesProp = A2uiProperty.dynamicString("dates")
  private val LegProp = A2uiProperty.dynamicString("leg")
  private val LegStatusProp = A2uiProperty.dynamicString("legStatus")
  private val NoteProp = A2uiProperty.dynamicString("note")

  override val name = "NovaTrip"
  override val description = "A destination with its dates and the flight that gets there. Use for a single trip, not a list."
  override val properties = listOf(PropEyebrow, DestProp, DatesProp, LegProp, LegStatusProp, NoteProp)

  @Composable
  override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
    // Not ready until the destination lands. Everything else may stream in after.
    properties.bind(DestProp) != null

  @Composable
  override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
    val destination = properties.bind(DestProp)
    val dates = properties.bind(DatesProp)
    val leg = properties.bind(LegProp)
    val legStatus = properties.bind(LegStatusProp)
    val note = properties.bind(NoteProp)

    NovaCard(modifier) {
      if (properties[PropEyebrow] != null) {
        NovaEyebrow(properties[PropEyebrow]!!)
        Spacer(Modifier.height(NovaSpace.xs))
      }
      PendingOr(destination, height = 28.dp, width = 200.dp)
      if (!dates.isNullOrBlank()) {
        Spacer(Modifier.height(NovaSpace.xs))
        Text(
          dates,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (!leg.isNullOrBlank()) {
        Spacer(Modifier.height(NovaSpace.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.Flight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(NovaSpace.sm))
          Text(
            leg,
            style = MaterialTheme.typography.bodyMedium.merge(NovaTabular),
            fontFamily = NovaMono,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (!legStatus.isNullOrBlank()) {
            Spacer(Modifier.width(NovaSpace.sm))
            NovaStatusPill(legStatus, NovaTone.Success)
          }
        }
      }
      if (!note.isNullOrBlank()) {
        Spacer(Modifier.height(NovaSpace.md))
        Text(
          note,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

// ── NovaStay: where you are sleeping, with the actual photo ─────────────────

/**
 * A hotel or rental. Carries a real photograph when the agent supplies one,
 * because a place you are staying is the one thing in a trip you want to see.
 */
internal object NovaStayComponent : A2uiComponent {
  private val NameProp = A2uiProperty.dynamicString("name", true)
  private val AddressProp = A2uiProperty.dynamicString("address")
  private val CheckInProp = A2uiProperty.dynamicString("checkIn")
  private val CheckOutProp = A2uiProperty.dynamicString("checkOut")
  private val ImageUrlProp = A2uiProperty.dynamicString("imageUrl")

  override val name = "NovaStay"
  override val description = "Accommodation with an address, check-in and check-out times, and an optional photo."
  override val properties = listOf(PropEyebrow, NameProp, AddressProp, CheckInProp, CheckOutProp, ImageUrlProp)

  @Composable
  override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
    properties.bind(NameProp) != null

  @Composable
  override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
    val stayName = properties.bind(NameProp)
    val address = properties.bind(AddressProp)
    val checkIn = properties.bind(CheckInProp)
    val checkOut = properties.bind(CheckOutProp)
    val imageUrl = properties.bind(ImageUrlProp)

    NovaCard(modifier, contentPadding = PaddingValues(0.dp)) {
      if (!imageUrl.isNullOrBlank()) {
        AsyncImage(
          model = imageUrl,
          contentDescription = null,
          contentScale = androidx.compose.ui.layout.ContentScale.Crop,
          modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        )
      }
      Column(Modifier.padding(NovaSpace.xl)) {
        if (properties[PropEyebrow] != null) {
          NovaEyebrow(properties[PropEyebrow]!!)
          Spacer(Modifier.height(NovaSpace.xs))
        }
        PendingOr(stayName, height = 24.dp, width = 180.dp)
        if (!address.isNullOrBlank()) {
          Spacer(Modifier.height(NovaSpace.xs))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Default.Place,
              contentDescription = null,
              tint = novaFaint(),
              modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(NovaSpace.xs))
            Text(
              address,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (!checkIn.isNullOrBlank() || !checkOut.isNullOrBlank()) {
          Spacer(Modifier.height(NovaSpace.md))
          Row(horizontalArrangement = Arrangement.spacedBy(NovaSpace.xl)) {
            StayFact("Check-in", checkIn)
            StayFact("Check-out", checkOut)
          }
        }
      }
    }
  }
}

@Composable
private fun StayFact(label: String, value: String?) {
  Column {
    NovaEyebrow(label, novaFaint())
    Spacer(Modifier.height(2.dp))
    if (value.isNullOrBlank()) {
      NovaSkeleton(Modifier.width(72.dp).height(13.dp))
    } else {
      Text(
        value,
        style = MaterialTheme.typography.bodyMedium.merge(NovaTabular),
        fontFamily = NovaMono,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

// ── NovaWeather: the headline number and its hour strip ─────────────────────

/**
 * Temperature, the four numbers that qualify it, and the next few hours.
 *
 * The hour strip is drawn rather than composed: an agent sends whatever hours
 * it has, and the row lays them out at equal weight so a three-hour answer and
 * a twelve-hour answer read the same. Below 15 degrees a bar appears under each
 * column, drawn with stable caps so nothing reshapes mid-stream.
 */
internal object NovaWeatherComponent : A2uiComponent {
  private val PlaceProp = A2uiProperty.dynamicString("place", true)
  private val NowProp = A2uiProperty.dynamicString("now", true)
  private val ConditionProp = A2uiProperty.dynamicString("condition")
  private val HighProp = A2uiProperty.dynamicString("high")
  private val LowProp = A2uiProperty.dynamicString("low")
  private val PrecipProp = A2uiProperty.dynamicString("precip")
  private val WindProp = A2uiProperty.dynamicString("wind")
  private val HoursProp = A2uiProperty.dynamicValue("hours")

  override val name = "NovaWeather"
  override val description = "Current conditions with high/low, precipitation, wind, and an hourly strip."
  override val properties =
    listOf(PropEyebrow, PlaceProp, NowProp, ConditionProp, HighProp, LowProp, PrecipProp, WindProp, HoursProp)

  @Composable
  override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
    properties.bind(NowProp) != null

  @Composable
  override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
    val now = properties.bind(NowProp)
    val condition = properties.bind(ConditionProp)
    val hours = properties.bind(HoursProp).a2uiRows("label", "temp")

    NovaCard(modifier) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          if (properties[PropEyebrow] != null) {
            NovaEyebrow(properties[PropEyebrow]!!)
          } else if (!properties.bind(PlaceProp).isNullOrBlank()) {
            NovaEyebrow(properties.bind(PlaceProp)!!)
          }
          Spacer(Modifier.height(NovaSpace.xs))
          Text(
            now ?: "—",
            fontFamily = NovaDisplay,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (!condition.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
              condition,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Icon(
          Icons.Default.WbCloudy,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.size(40.dp),
        )
      }

      val facts = listOf(
        "High" to properties.bind(HighProp),
        "Low" to properties.bind(LowProp),
        "Rain" to properties.bind(PrecipProp),
        "Wind" to properties.bind(WindProp),
      ).filter { !it.second.isNullOrBlank() }
      if (facts.isNotEmpty()) {
        Spacer(Modifier.height(NovaSpace.md))
        Row(horizontalArrangement = Arrangement.spacedBy(NovaSpace.xl)) {
          facts.forEach { (label, value) ->
            Column {
              NovaEyebrow(label, novaFaint())
              Spacer(Modifier.height(2.dp))
              Text(
                value!!,
                style = MaterialTheme.typography.bodyMedium.merge(NovaTabular),
                fontFamily = NovaMono,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }

      if (hours.isNotEmpty()) {
        Spacer(Modifier.height(NovaSpace.lg))
        HourStrip(hours)
      }
    }
  }
}

/**
 * The hourly row. Widths are weighted equally and the numbers are tabular, so a
 * temperature landing mid-stream shifts nothing around it. Nothing animates
 * into place: the strip is present from the first hour the agent sends.
 */
@Composable
private fun HourStrip(hours: List<Map<String, Any?>>) {
  val edge = novaGlassEdge()
  Column(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NovaSpace.sm)) {
      hours.forEach { hour ->
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            hour.str("label").orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = novaFaint(),
            maxLines = 1,
          )
          Spacer(Modifier.height(NovaSpace.xs))
          Text(
            hour.str("temp").orEmpty(),
            style = MaterialTheme.typography.bodyMedium.merge(NovaTabular),
            fontFamily = NovaMono,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
          )
        }
      }
    }
    Spacer(Modifier.height(NovaSpace.sm))
    Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(NovaRadius.hair)).background(edge))
  }
}

// ── NovaAgenda: a day of commitments ─────────────────────────────────────────

/**
 * A day, with its rows. The time is the anchor and it is mono and accent-tinted;
 * the title is the largest type in the row; the place is the quietest. That
 * order is what makes a list of six scannable without reading it.
 */
internal object NovaAgendaComponent : A2uiComponent {
  private val HeadingProp = A2uiProperty.dynamicString("heading", true)
  private val EventsProp = A2uiProperty.dynamicValue("events")

  override val name = "NovaAgenda"
  override val description = "A day's schedule: a heading and a list of timed entries, each with an optional place."
  override val properties = listOf(PropEyebrow, HeadingProp, EventsProp)

  @Composable
  override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
    properties.bind(HeadingProp) != null

  @Composable
  override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
    val heading = properties.bind(HeadingProp)
    val events = properties.bind(EventsProp).a2uiRows("title")

    NovaCard(modifier) {
      if (properties[PropEyebrow] != null) {
        NovaEyebrow(properties[PropEyebrow]!!)
        Spacer(Modifier.height(NovaSpace.xs))
      }
      PendingOr(heading, height = 22.dp, width = 160.dp)
      if (events.isNotEmpty()) {
        Spacer(Modifier.height(NovaSpace.md))
        Column(verticalArrangement = Arrangement.spacedBy(NovaSpace.md)) {
          events.forEach { event ->
            Row(Modifier.fillMaxWidth()) {
              Text(
                event.str("time").orEmpty(),
                style = MaterialTheme.typography.titleMedium.merge(NovaTabular),
                fontFamily = NovaMono,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(64.dp),
              )
              Column(Modifier.weight(1f)) {
                Text(
                  event.str("title").orEmpty(),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                event.str("place")?.takeIf { it.isNotBlank() }?.let { place ->
                  Spacer(Modifier.height(2.dp))
                  Text(
                    place,
                    style = MaterialTheme.typography.bodySmall,
                    color = novaFaint(),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

// ── NovaChecklist: the one interactive surface ───────────────────────────────

/**
 * A list the user can tick, written back to the agent.
 *
 * This is the component that proves two-way binding is wired: the tick writes
 * through `bindUpdater` into the surface's data model, which the agent sees as
 * a `dataModelUpdate`. When the agent handed us a literal list rather than a
 * bound path, `bindUpdater` returns null and the rows render read-only rather
 * than pretending to be interactive. That distinction is the whole point: a
 * control that cannot persist must not look like one that can.
 */
internal object NovaChecklistComponent : A2uiComponent {
  private val ItemsProp = A2uiProperty.dynamicStringList("items", true)
  // There is no dynamicBooleanList in the runtime — a list of flags is a
  // dynamic *value*. The two-way bind is what actually needs a writable path,
  // and that works on any dynamic property, so a list of booleans goes in as
  // one and is narrowed here.
  private val DoneProp = A2uiProperty.dynamicValue("done")
  private val NoteProp = A2uiProperty.dynamicString("note")

  override val name = "NovaChecklist"
  override val description = "A tickable list. Writes back to the agent when 'done' is bound to a data path."
  override val properties = listOf(PropEyebrow, ItemsProp, DoneProp, NoteProp)

  @Composable
  override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
    !properties.bind(ItemsProp).isNullOrEmpty()

  @Composable
  override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
    val items = properties.bind(ItemsProp).orEmpty()
    val done = (properties.bind(DoneProp) as? List<*>)?.map { it == true } ?: emptyList()
    // null means the agent sent a literal list: render it, but do not pretend
    // the tick will survive a round trip.
    val writeBack = properties.bindUpdater(DoneProp)

    NovaCard(modifier) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (properties[PropEyebrow] != null) {
          NovaEyebrow(properties[PropEyebrow]!!, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        properties.bind(NoteProp)?.takeIf { it.isNotBlank() }?.let {
          Text(it, style = MaterialTheme.typography.labelSmall, color = novaFaint())
        }
      }
      Spacer(Modifier.height(NovaSpace.sm))
      items.forEachIndexed { index, label ->
        val isDone = done.getOrNull(index) == true
        ChecklistRow(
          label = label,
          checked = isDone,
          enabled = writeBack != null,
          onToggle = {
            writeBack?.invoke(done.toMutableList().also { list -> list[index] = !isDone } as Any)
          },
        )
      }
    }
  }
}

@Composable
private fun ChecklistRow(label: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val stateText = if (checked) "checked" else "not checked"
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = MinTouchTarget)
      .clip(RoundedCornerShape(NovaRadius.row))
      .then(
        if (enabled) Modifier.clickable(onClick = onToggle) else Modifier
      )
      .semantics {
        role = Role.Checkbox
        this.stateDescription = stateText
        contentDescription = if (enabled) label else "$label, read only"
      }
      .padding(vertical = NovaSpace.xs),
  ) {
    Box(
      Modifier
        .size(22.dp)
        .clip(RoundedCornerShape(NovaRadius.sm))
        .background(if (checked) scheme.primary else Color0)
        .then(
          if (enabled) Modifier.clickable(onClick = onToggle) else Modifier
        ),
      contentAlignment = Alignment.Center,
    ) {
      if (checked) {
        Icon(
          Icons.Default.Check,
          contentDescription = null,
          tint = scheme.onPrimary,
          modifier = Modifier.size(15.dp),
        )
      } else {
        Box(
          Modifier
            .fillMaxWidth(0.6f)
            .height(1.5.dp)
            .background(novaGlassEdge())
            .align(Alignment.Center),
        )
      }
    }
    Spacer(Modifier.width(NovaSpace.md))
    Text(
      label,
      style = MaterialTheme.typography.bodyLarge,
      color = if (checked) novaFaint() else scheme.onSurface,
      textDecoration = if (checked) TextDecoration.LineThrough else null,
    )
  }
}

/** Transparent. An unticked box is an outline, not a fill. */
private val Color0 = androidx.compose.ui.graphics.Color.Transparent

// ── NovaActions: the follow-up chips ─────────────────────────────────────────

/**
 * The buttons the agent offers at the end of a turn. Each one dispatches an
 * action back rather than navigating, so the agent decides what happens next.
 *
 * They are pills, because a suggestion is not a command: same shape as the
 * composer, quieter than a filled button. Tapping fills the composer's draft
 * (the host wires that) so the user can see and edit the prompt before it goes.
 */
internal object NovaActionsComponent : A2uiComponent {
  private val PromptsProp = A2uiProperty.dynamicValue("prompts")

  override val name = "NovaActions"
  override val description = "Suggested next steps. Each entry is a label plus an action dispatched to the agent."
  override val properties = listOf(PropEyebrow, PromptsProp)

  @Composable
  override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
    properties.bind(PromptsProp) != null

  @Composable
  override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
    val prompts = properties.bind(PromptsProp).a2uiRows("label", "action")
    if (prompts.isEmpty()) return

    // Wrapping, not scrolling: a horizontal strip hides the options past the
    // first screen, and these are the cheapest way to redirect the conversation.
    androidx.compose.foundation.layout.FlowRow(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(NovaSpace.sm),
      verticalArrangement = Arrangement.spacedBy(NovaSpace.sm),
    ) {
      prompts.forEach { prompt ->
        val label = prompt.str("label").orEmpty()
        if (label.isBlank()) return@forEach
        ActionChip(label) {
          @Suppress("UNCHECKED_CAST")
          val action = prompt["action"] as? Map<String, Any?>
          if (action != null) dispatchAction(action)
        }
      }
    }
  }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = novaGlassFill(),
    border = BorderStroke(1.dp, novaGlassEdge()),
    modifier = Modifier.heightIn(min = MinTouchTarget),
  ) {
    Box(Modifier.padding(horizontal = NovaSpace.lg, vertical = NovaSpace.md)) {
      Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

// ── NovaApproval: the one surface that spends money ─────────────────────────

/**
 * A purchase the agent wants to make on the user's behalf.
 *
 * The state is a four-step machine the agent drives: `pending` asks,
 * `verifying` is the biometric round trip, `approved` is done, `declined` is
 * the user's no. Each state gets its own sentence, because a silent spinner
 * on a payment is the worst thing a payment screen can do.
 *
 * This is the one place the Nova law bends: the amount is set large and in
 * tabular figures because it is the single number the user must be certain of.
 * No colour announces it — the size and the mono figures do.
 */
internal object NovaApprovalComponent : A2uiComponent {
  private val ProviderProp = A2uiProperty.dynamicString("provider", true)
  private val SummaryProp = A2uiProperty.dynamicString("summary")
  private val AmountProp = A2uiProperty.dynamicString("amount", true)
  private val InstrumentProp = A2uiProperty.dynamicString("instrument")
  private val StateProp = A2uiProperty.stringEnum("state", listOf("pending", "verifying", "approved", "declined"))
  private val ConfirmProp = A2uiProperty.action("confirm")
  private val DeclineProp = A2uiProperty.action("decline")

  override val name = "NovaApproval"
  override val description = "A purchase confirmation with a four-state flow: pending, verifying, approved, declined."
  override val properties =
    listOf(ProviderProp, SummaryProp, AmountProp, InstrumentProp, StateProp, ConfirmProp, DeclineProp)

  @Composable
  override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
    properties.bind(AmountProp) != null

  @Composable
  override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
    val provider = properties.bind(ProviderProp)
    val summary = properties.bind(SummaryProp)
    val amount = properties.bind(AmountProp)
    val instrument = properties.bind(InstrumentProp)
    val state = properties[StateProp] ?: "pending"
    val confirm = properties[ConfirmProp]
    val decline = properties[DeclineProp]

    NovaCard(modifier) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          provider ?: "—",
          fontFamily = NovaDisplay,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        NovaStatusPill(state, stateTone(state))
      }
      if (!summary.isNullOrBlank()) {
        Spacer(Modifier.height(NovaSpace.xs))
        Text(
          summary,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(Modifier.height(NovaSpace.md))
      Text(
        amount ?: "—",
        fontFamily = NovaDisplay,
        style = MaterialTheme.typography.displaySmall.merge(NovaTabular),
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (!instrument.isNullOrBlank()) {
        Spacer(Modifier.height(NovaSpace.sm))
        Box(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NovaRadius.row))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = NovaSpace.md, vertical = NovaSpace.md),
        ) {
          Text(
            instrument,
            style = MaterialTheme.typography.bodyMedium.merge(NovaTabular),
            fontFamily = NovaMono,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      Spacer(Modifier.height(NovaSpace.md))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Default.Fingerprint,
          contentDescription = null,
          tint = novaFaint(),
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(NovaSpace.sm))
        Text(
          stateSentence(state),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (state == "pending" && (confirm != null || decline != null)) {
        Spacer(Modifier.height(NovaSpace.md))
        Row(horizontalArrangement = Arrangement.spacedBy(NovaSpace.sm)) {
          if (confirm != null) {
            // Filled: this is the one action the user came here to take, so it
            // gets the weight. The refusal beside it is a text action, not a
            // second button of equal size.
            Surface(
              onClick = { dispatchAction(confirm) },
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.heightIn(min = MinTouchTarget),
            ) {
              Box(Modifier.padding(horizontal = NovaSpace.xl, vertical = NovaSpace.md)) {
                Text(
                  "Confirm",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
              }
            }
          }
          if (decline != null) {
            ActionChip("Not now") { dispatchAction(decline) }
          }
        }
      }
    }
  }
}

private fun stateTone(state: String): NovaTone = when (state) {
  "approved" -> NovaTone.Success
  "declined" -> NovaTone.Danger
  "verifying" -> NovaTone.Warning
  else -> NovaTone.Muted
}

private fun stateSentence(state: String): String = when (state) {
  "pending" -> "Waiting for you to confirm"
  "verifying" -> "Verifying on your device"
  "approved" -> "Done"
  "declined" -> "You declined this"
  else -> "Waiting for you to confirm"
}
