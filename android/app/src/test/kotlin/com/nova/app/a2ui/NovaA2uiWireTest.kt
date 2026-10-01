package com.nova.app.a2ui

import androidx.a2ui.model.protocol.A2uiClientEventMessage
import com.nova.app.data.StreamChunk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §45b wire contract, on the JVM.
 *
 * These are the assertions that would fail quietly. Nothing here renders: a
 * mistake in the envelope shape, the escaping, or the action mapping does not
 * throw — it produces a chat bubble with an empty space where a card should be,
 * which is the hardest kind of bug to catch in review and the easiest to pin
 * from a test that only looks at bytes.
 */
class NovaA2uiWireTest {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  /** The `surfaceId` out of a hand-built envelope, decoded. */
  private fun String.surfaceIdField(): String? {
    val root = json.parseToJsonElement(this) as JsonObject
    val delete = root["deleteSurface"] as JsonObject
    return delete["surfaceId"]?.jsonPrimitive?.content
  }

  // ---- the SSE frame ---------------------------------------------------------

  @Test
  fun `a2ui chunk carries the frame through deserialization intact`() {
    val frame = """{"version":"v0.9.1","createSurface":{"surfaceId":"s_1","catalogId":"$NovaA2uiCatalogId"}}"""
    val chunk = json.decodeFromString<StreamChunk>("""{"type":"a2ui","frame":"${frame.replace("\"", "\\\"")}"}""")
    assertEquals("a2ui", chunk.type)
    // The frame crosses the wire as an untouched string. The engine owns
    // protocol versions we do not know about, so nothing here may re-parse it.
    assertEquals(frame, chunk.frame)
  }

  @Test
  fun `an older client chunk with no frame still deserializes`() {
    val chunk = json.decodeFromString<StreamChunk>("""{"type":"token","text":"hello"}""")
    assertEquals("token", chunk.type)
    assertNull("a missing frame must not break an older payload", chunk.frame)
  }

  @Test
  fun `a ui chunk is unaffected by the new field`() {
    val chunk = json.decodeFromString<StreamChunk>("""{"type":"ui","blocks":[]}""")
    assertEquals("ui", chunk.type)
    assertTrue(chunk.blocks.isEmpty())
  }

  // ---- reading a surface id out of an envelope --------------------------------

  @Test
  fun `every envelope key yields its surface id`() {
    val controller = NovaA2uiController()
    val body = """{"surfaceId":"s_42"}"""
    for (key in listOf("createSurface", "updateComponents", "updateDataModel", "deleteSurface")) {
      assertEquals(key, "s_42", controller.peekSurfaceId("""{"version":"v0.9.1","$key":$body}"""))
    }
  }

  @Test
  fun `a frame with no surface id is not claimed`() {
    val controller = NovaA2uiController()
    assertNull(controller.peekSurfaceId("""{"version":"v0.9.1","createSurface":{}}"""))
    assertNull(controller.peekSurfaceId("not json"))
    assertNull(controller.peekSurfaceId(""))
    assertNull(controller.peekSurfaceId("""{"version":"v0.9.1","somethingElse":{"surfaceId":"s_1"}}"""))
  }

  // ---- the one message Nova originates ---------------------------------------

  @Test
  fun `deleteSurface is a well formed envelope`() {
    val root = json.parseToJsonElement(deleteSurfaceMessage("s_1")) as JsonObject
    assertEquals(NovaA2uiProtocolVersion, root["version"]?.jsonPrimitive?.content)
    assertEquals("s_1", deleteSurfaceMessage("s_1").surfaceIdField())
  }

  @Test
  fun `a surface id with a quote and a backslash round trips`() {
    // A surface id is echoed back inside a hand-built envelope. A quote in it
    // would otherwise produce JSON the parser rejects, and the surface would
    // never be released — a leak the user would experience as buttons that
    // silently do nothing.
    val awkward = "weird" + '"' + "id" + '\\' + "with" + '\\' + "slash"
    assertEquals(awkward, deleteSurfaceMessage(awkward).surfaceIdField())
  }

  @Test
  fun `control characters are escaped and decode back unchanged`() {
    val control = buildString {
      append('a'); append(10.toChar()); append('b'); append(9.toChar())
      append('c'); append(1.toChar()); append('d')
    }
    assertEquals(control, deleteSurfaceMessage(control).surfaceIdField())
  }

  @Test
  fun `a surface id echoed with a quote still parses on the id reader`() {
    val controller = NovaA2uiController()
    val awkward = "a" + '"' + "b"
    assertEquals(awkward, controller.peekSurfaceId(deleteSurfaceMessage(awkward)))
  }

  // ---- actions ---------------------------------------------------------------

  @Test
  fun `an action reads its name from the event type and its label from context`() {
    // The engine lowers A2uiEventAction into A2uiClientEventMessage, renaming
    // eventName to type. This is the one place that mapping is relied on, so it
    // is pinned here rather than discovered on a device.
    val event = A2uiClientEventMessage(
      type = "add_packing_list",
      surfaceId = "s_1",
      componentId = "c2_actions",
      timestamp = 1_700_000_000_000L,
      context = mapOf("label" to "Add packing list", "surface" to "a2ui"),
    )
    val action = event.toSurfaceAction()
    assertNotNull(action)
    assertEquals("add_packing_list", action!!.name)
    assertEquals("Add packing list", action.label)
    assertEquals("s_1", action.surfaceId)
  }

  @Test
  fun `an action with no label falls back to a readable form of its name`() {
    val event = A2uiClientEventMessage(
      type = "book_an_airport_car",
      surfaceId = "s_1",
      componentId = "c0",
      timestamp = 0L,
    )
    assertEquals("Book an airport car", event.toSurfaceAction()!!.label)
  }

  @Test
  fun `an event with no name is not an action`() {
    val event = A2uiClientEventMessage(type = "", surfaceId = "s_1", componentId = "c0", timestamp = 0L)
    assertNull(event.toSurfaceAction())
  }

  // ---- the catalog id --------------------------------------------------------

  @Test
  fun `the catalog id is a versioned identifier and not a fetchable path`() {
    // The spec is explicit: it is an id, conventionally URI-shaped so two
    // vendors cannot collide. Nothing ever resolves it, and the version is in
    // the path so a schema break is a new id rather than a silent change.
    assertTrue(NovaA2uiCatalogId.startsWith("https://nova.app/catalogs/"))
    assertTrue(NovaA2uiCatalogId.endsWith("/v1/catalog.json"))
    assertEquals("v0.9.1", NovaA2uiProtocolVersion)
  }

  @Test
  fun `one catalog version is supported today and it is the one we advertise`() {
    assertEquals(listOf(NovaA2uiCatalogId), NovaSupportedA2uiCatalogIds)
    assertEquals(NovaSupportedA2uiCatalogIds, NovaA2uiController().supportedCatalogIds())
  }

  @Test
  fun `a fresh controller holds no surfaces and no order`() {
    // A surface id the engine has never seen is not live, so an action from it
    // must not be relayed. Nothing is submitted here, so the controller still
    // reports an empty world — which is the state a new conversation must be in.
    val controller = NovaA2uiController()
    assertTrue(controller.activeSurfaces.value.isEmpty())
    assertFalse(controller.surfaceOrder.value.isNotEmpty())
  }
}
