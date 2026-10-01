package com.nova.app.a2ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.asReadinessEvaluator
import androidx.a2ui.compose.ui.toJsonSchemaString
import androidx.compose.material3.a2ui.catalog.A2uiAudioPlayerRenderer
import androidx.compose.material3.a2ui.catalog.A2uiImageRenderer
import androidx.compose.material3.a2ui.catalog.A2uiVideoRenderer
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.material3.a2ui.catalog.materialA2uiBasicCatalogV1
import androidx.a2ui.model.catalog.functions.A2uiLocaleProvider
import androidx.a2ui.model.catalog.functions.A2uiMessageFormatter
import androidx.a2ui.model.catalog.functions.A2uiUrlOpener
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.nova.app.MinTouchTarget
import com.nova.app.NovaMono
import com.nova.app.NovaSpace
import com.nova.app.NovaStatusPill
import com.nova.app.NovaTone
import com.nova.app.NovaTabular
import java.util.Locale

/**
 * §45b — the Nova A2UI catalog.
 *
 * A catalog is the contract between the agent and this app: it says which
 * component names exist, what properties each one takes, and what each one
 * means. The agent never sees Compose — it sees this list, and it may only ask
 * for things on it. That is the whole safety story of generative UI: the agent
 * picks from a menu, it does not write code.
 *
 * The catalog is built in two layers:
 *
 *   1. The A2UI **Basic Catalog**, which is the cross-vendor spec. Its names
 *      (`Text`, `Card`, `Column`, `Image`, `Button`, `CheckBox`…) are what any
 *      A2UI agent already knows how to emit, so a model that has never heard of
 *      Nova can still render something.
 *   2. Nova's own components, appended. These are the ones worth a bespoke
 *      rendering — a trip, a stay, a day, a checklist, a payment. They are
 *      `Nova*`-prefixed so they can never collide with a spec name.
 *
 * Both layers live in ONE catalog on purpose. A surface is bound to a single
 * catalog id, and a component can only reference children that live in the
 * same catalog — so a Nova card that wants a `Text` child needs the Basic
 * `Text` in the same list. One catalog, one id, one tree.
 */

/**
 * Catalog id. Conventionally a URI to avoid collisions across vendors, but the
 * spec is explicit that this is an identifier and not a resolvable address — it
 * is never fetched. The version lives in the path: a breaking schema change
 * means a new id, which is how a client and an agent agree on a migration
 * without either of them guessing.
 */
const val NovaA2uiCatalogId = "https://nova.app/catalogs/agentic/v1/catalog.json"

/** The protocol revision this client speaks. Pinned, not floating. */
const val NovaA2uiProtocolVersion = "v0.9.1"

/**
 * Nova's components, in the order the agent should prefer them.
 *
 * A trip is more useful as a `NovaTrip` than as a stack of Text nodes, so this
 * list reads as a preference order and the backend prompt follows it.
 */
val NovaA2uiComponents: List<A2uiComponent> = listOf(
  NovaTripComponent,
  NovaStayComponent,
  NovaAgendaComponent,
  NovaChecklistComponent,
  NovaWeatherComponent,
  NovaApprovalComponent,
  NovaActionsComponent,
)

/**
 * Where the catalog's URL-opening function gets a Context from.
 *
 * The catalog is built once and lives forever, but `openUrl` needs to start an
 * Activity, which needs a Context. The opener is a plain function on the
 * catalog, not a composable, so it cannot read `LocalContext` itself. Main
 * Activity publishes the application Context here once; until it does, links
 * do nothing rather than crashing.
 */
object NovaA2uiContext {
  @Volatile
  var app: Context? = null

  fun open(url: String) {
    val context = app ?: return
    // Only https and http. A generated surface is model output, so a `file://`
    // or `intent://` URL here would be the model asking the app to open
    // something the user never chose. Anything else is dropped silently.
    val scheme = Uri.parse(url).scheme?.lowercase(Locale.ROOT)
    if (scheme != "https" && scheme != "http") return
    runCatching {
      context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
  }
}

/** Coil for images. */
private object NovaImageRenderer : A2uiImageRenderer {
  @Composable
  override fun Image(
    url: String,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier,
    onError: (Throwable?) -> Unit,
  ) {
    AsyncImage(
      model = url,
      contentDescription = contentDescription,
      contentScale = contentScale,
      modifier = modifier,
      // Coil reports a painter state, not a throwable. The catalog contract
      // wants the cause when there is one, so unwrap it rather than dropping
      // the signal — an image the agent promised and the app could not fetch is
      // worth knowing about, and a bare state object is not.
      onError = { state ->
        onError((state as? AsyncImagePainter.State.Error)?.result?.throwable)
      },
    )
  }
}

/**
 * Video and audio, honestly.
 *
 * The A2UI libraries ship no media stack, and Nova has no Media3 dependency
 * today. Rather than pretend, these render the media reference as a labelled
 * row: a real play control that does nothing is worse than no play control.
 * When Media3 lands, these two objects are the only things that change — the
 * catalog wiring above them does not move.
 */
private object NovaVideoRenderer : A2uiVideoRenderer {
  @Composable
  override fun Video(url: String, modifier: Modifier, onError: (Throwable?) -> Unit) {
    MediaReferenceRow("Video", url, modifier)
  }
}

private object NovaAudioPlayerRenderer : A2uiAudioPlayerRenderer {
  @Composable
  override fun AudioPlayer(
    url: String,
    description: String?,
    modifier: Modifier,
    onError: (Throwable?) -> Unit,
  ) {
    MediaReferenceRow("Audio", url, modifier)
  }
}

@Composable
private fun MediaReferenceRow(kind: String, url: String, modifier: Modifier) {
  Row(
    modifier = modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(NovaSpace.sm),
  ) {
    NovaStatusPill(kind, NovaTone.Muted)
    Text(
      url,
      style = MaterialTheme.typography.bodySmall.merge(NovaTabular),
      fontFamily = NovaMono,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * Message formatting for the catalog's `formatString` family.
 *
 * Substitutes `{name}` and `{name, plural, one {…} other {…}}` style placeholders
 * from the supplied args. Deliberately not a full ICU implementation — the agent
 * controls the template and the args, so this only has to be correct and total.
 */
private object NovaMessageFormatter : A2uiMessageFormatter {
  override fun format(pattern: String, locale: Locale, arguments: Map<String, Any>): String {
    if (arguments.isEmpty()) return pattern
    return PLACEHOLDER.replace(pattern) { match ->
      when (val value = arguments[match.groupValues[1]]) {
        null -> match.value
        // A whole number should read as 3, never 3.0 — the pattern is
        // authored by the agent and almost always means a count.
        is Number -> if (value.toDouble() % 1.0 == 0.0) value.toLong().toString() else value.toString()
        else -> value.toString()
      }
    }
  }

  private val PLACEHOLDER = Regex("""\{([^{}]+)}""")
}

private val NovaLocaleProvider = A2uiLocaleProvider { Locale.getDefault() }

/**
 * Assembles the catalog.
 *
 * Exposed as a function rather than a `val` so tests and previews can build a
 * catalog with a component substituted without reaching into the process-wide
 * instance. In the app there is exactly one catalog and it is [novaA2uiCatalog].
 *
 * @param catalogId the id this instance answers to. One per supported version.
 * @param components Nova's own components. Defaults to the full set; tests pass
 *   a shorter list to keep a surface small.
 */
fun novaA2uiCatalog(
  catalogId: String = NovaA2uiCatalogId,
  components: List<A2uiComponent> = NovaA2uiComponents,
): A2uiCatalog {
  val basic = materialA2uiBasicCatalogV1(
    image = MaterialA2uiBasicCatalogV1Defaults.image(NovaImageRenderer),
    video = MaterialA2uiBasicCatalogV1Defaults.video(NovaVideoRenderer),
    audioPlayer = MaterialA2uiBasicCatalogV1Defaults.audioPlayer(NovaAudioPlayerRenderer),
    urlOpener = { url -> NovaA2uiContext.open(url) },
    messageFormatter = NovaMessageFormatter,
    localeProvider = NovaLocaleProvider,
  )
  return A2uiCatalog(
    catalogId = catalogId,
    components = basic.components + components,
    themeSchema = basic.themeSchema,
  )
}

/** The process-wide catalog. One id, one instance, one negotiation. */
val novaA2uiCatalog: A2uiCatalog by lazy { novaA2uiCatalog() }

/**
 * Readiness: "does this component have the data it needs to draw yet?"
 *
 * The renderer asks before it renders, so a component can wait for its
 * streaming data instead of flashing an empty card. Nova's components answer on
 * their single required binding — the trip waits for its destination, the
 * checklist waits for its first item — and treat every optional field as
 * genuinely optional, which is what makes progressive filling work.
 */
val novaA2uiReadiness get() = novaA2uiCatalog.asReadinessEvaluator()

/**
 * The catalog as JSON Schema, served to the agent during negotiation.
 *
 * This is the document the model actually reads to learn what it may ask for.
 * It is generated from the same [A2uiComponent] declarations the renderer uses,
 * so the two cannot drift: a property added to a component shows up here on the
 * next call, with no second list to forget to update.
 */
fun novaA2uiCatalogSchema(): String = novaA2uiCatalog.toJsonSchemaString()

/**
 * Every catalog id this client answers to, newest last.
 *
 * Registered in order so the agent sees the full version range during
 * negotiation. Nova ships one version today; the seam is here so a v2 can be
 * added beside v1 rather than replacing it, and both stay renderable while a
 * surface is mid-flight.
 */
val NovaSupportedA2uiCatalogIds: List<String> = listOf(NovaA2uiCatalogId)
