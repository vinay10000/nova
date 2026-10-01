package com.nova.app.a2ui

import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.model.processor.A2uiActionInterceptor
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.a2ui.model.protocol.A2uiClientToServerMessage
import androidx.a2ui.model.protocol.A2uiUserAction
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * §45b — the A2UI data layer.
 *
 * This is the boundary between the wire and the screen. An agent sends JSON; a
 * surface appears. Nothing in between is allowed to interpret the JSON: the
 * engine owns parsing, the engine owns the data model, and this class only
 * decides which surfaces are alive and what happens when the user touches one.
 *
 * The split matters. The renderer (see [NovaA2uiSurface]) knows about Compose
 * and nothing about JSON. The controller knows about JSON and nothing about
 * Compose. Swapping one for a different renderer, or feeding this from a
 * websocket instead of SSE, touches exactly one of them.
 */
class NovaA2uiController(
  /** Catalog versions to accept, in negotiation order. */
  catalogIds: List<String> = NovaSupportedA2uiCatalogIds,
  /**
   * Last word on a user action before it leaves the device.
   *
   * Returning the action sends it. Returning null drops it. This is the only
   * place a surface can be stopped from talking to the agent, so it is also the
   * only place a policy belongs.
   */
  private val actionPolicy: (suspend (A2uiUserAction) -> A2uiUserAction?) = { it },
) {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  /**
   * Fresh per controller. Two conversations must not share a data model: a
   * surface id from an old thread would resolve against the new thread's model
   * and render the wrong thing, quietly.
   */
  private val parser = A2uiMessageParser()

  private val interceptor = object : A2uiActionInterceptor {
    override suspend fun onInterceptAction(action: A2uiUserAction): A2uiUserAction? =
      // Belt first: an action from a surface that is no longer on screen is
      // stale by definition. The surface may have been deleted, or the user may
      // have started a new conversation while it was still mounted.
      if (action.surfaceId in liveSurfaces) actionPolicy(action) else null
  }

  private val processor = A2uiMessageProcessor(
    // One catalog instance per declared id. Today that is a single object; the
    // map is what lets a v2 sit beside a v1 during a migration instead of
    // replacing it out from under a surface that is already rendering.
    catalogs = catalogIds.map { id -> novaA2uiCatalog(catalogId = id) },
    interceptors = listOf(interceptor),
  )

  /** Surfaces the engine currently holds, oldest first. */
  val activeSurfaces: StateFlow<List<A2uiSurfaceModel>> = processor.activeSurfaces

  /** Ids we have seen created, in arrival order, so the chat layer can group them. */
  private val _surfaceOrder = MutableStateFlow<List<String>>(emptyList())
  val surfaceOrder: StateFlow<List<String>> = _surfaceOrder.asStateFlow()

  private val liveSurfaces: Set<String>
    get() = activeSurfaces.value.mapTo(mutableSetOf()) { it.id }

  /** Every outbound client message, ready to hand to the transport. */
  val outbound: Flow<A2uiClientToServerMessage> = processor.outboundEvents

  /**
   * Feed one raw protocol message.
   *
   * Returns the surface id it touched, so the caller can attach the surface to
   * the assistant turn that caused it. A malformed message returns null and is
   * dropped: one bad frame from a model must not take down a live surface.
   */
  fun submit(raw: String): String? {
    val surfaceId = peekSurfaceId(raw) ?: return null
    val message = runCatching { parser.parse(raw) }.getOrNull() ?: return null
    processor.processMessage(message)
    if (surfaceId !in _surfaceOrder.value) {
      _surfaceOrder.value = _surfaceOrder.value + surfaceId
    }
    return surfaceId
  }

  /**
   * Read the surface id out of an envelope without fully parsing it.
   *
   * The envelope is exactly one of four keys and every one of them carries
   * `surfaceId`, so this is a two-line walk — cheaper and far more forgiving
   * than parsing a message we might not be able to use.
   */
  fun peekSurfaceId(raw: String): String? = runCatching {
    val root = json.parseToJsonElement(raw).jsonObject
    ENVELOPE_KEYS.firstNotNullOfOrNull { key ->
      (root[key] as? JsonObject)?.get("surfaceId")?.jsonPrimitive?.content
    }
  }.getOrNull()

  /**
   * Tear a surface down.
   *
   * Sends a real `deleteSurface` message rather than just forgetting the id, so
   * the engine releases its components and data model instead of holding them
   * for the life of the process. A surface the user has scrolled past must not
   * still be able to dispatch actions.
   */
  fun dismiss(surfaceId: String) {
    submit(deleteSurfaceMessage(surfaceId))
    _surfaceOrder.value = _surfaceOrder.value - surfaceId
  }

  /** Forget every surface. Used when the conversation is replaced. */
  fun reset() {
    activeSurfaces.value.forEach { dismiss(it.id) }
    _surfaceOrder.value = emptyList()
  }

  /** The catalog ids this controller will honour, for a negotiation step. */
  fun supportedCatalogIds(): List<String> = NovaSupportedA2uiCatalogIds

  /**
   * Park on the engine's inbound pump.
   *
   * Suspends for the processor's lifetime. Exposed rather than called from the
   * constructor so the engine is never started behind the caller's back — a
   * test that only wants to push messages in does not get a stray coroutine.
   */
  suspend fun collectMessages() = processor.collectMessages()

  companion object {
    private val ENVELOPE_KEYS = listOf("createSurface", "updateComponents", "updateDataModel", "deleteSurface")
  }
}

/** The one message Nova originates itself. Everything else comes from the agent. */
internal fun deleteSurfaceMessage(surfaceId: String): String =
  """{"version":"$NovaA2uiProtocolVersion","deleteSurface":{"surfaceId":${quote(surfaceId)}}}"""

/**
 * Quote a string for a hand-built envelope.
 *
 * Only used for ids Nova itself generates or is echoing back, never for
 * anything the model produced — model output always arrives as an already
 * parsed message. Escaping is still done properly, because a surface id that
 * contains a quote would otherwise produce a frame the parser rejects and the
 * surface would never be released.
 */
private fun quote(value: String): String = buildString {
  append('"')
  value.forEach { c ->
    when (c) {
      '"' -> append("\\\"")
      '\\' -> append("\\\\")
      '\n' -> append("\\n")
      '\r' -> append("\\r")
      '\t' -> append("\\t")
      else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
    }
  }
  append('"')
}

/**
 * Start the two long-lived coroutines the engine needs.
 *
 * Separate from the constructor because a processor is inert until something
 * is collecting: [collectMessages] is the inbound pump and [outbound] is the
 * event stream, and neither runs unless a coroutine is parked on it.
 */
fun NovaA2uiController.start(
  scope: kotlinx.coroutines.CoroutineScope,
  onOutbound: suspend (A2uiClientToServerMessage) -> Unit,
) {
  // UNDISPATCHED: an action the user tapped before the first frame must not be
  // lost in a queue that has not started draining yet.
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    outbound.collect { onOutbound(it) }
  }
  scope.launch {
    // The engine throws A2uiException on a protocol violation. One bad frame
    // must not take the pump down for every later surface, so the failure is
    // swallowed here and the next message gets a chance.
    runCatching { collectMessages() }
  }
}
