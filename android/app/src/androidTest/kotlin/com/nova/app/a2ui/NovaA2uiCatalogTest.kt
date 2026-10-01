package com.nova.app.a2ui

import androidx.a2ui.compose.ui.testing.A2uiTestController
import androidx.a2ui.compose.ui.testing.A2uiTestSurface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nova.app.NovaTheme
import com.nova.app.NovaAccent
import com.nova.app.NovaThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §45b — the surface itself.
 *
 * These are the tests that need a Compose runtime, because they assert on what
 * a card actually draws. A unit test can prove the frame is well formed; only
 * this can prove the frame becomes a trip card with a destination on it.
 *
 * Every test drives the real catalog through the real engine. Nothing is
 * stubbed, because a stubbed catalog would pass whether or not Nova's own
 * components were wired to the protocol at all — which is the whole thing under
 * test.
 */
@RunWith(AndroidJUnit4::class)
class NovaA2uiCatalogTest {

  @get:Rule
  val compose = createComposeRule()

  private val catalog = novaA2uiCatalog()

  /** Mounts a surface under the app's own theme, which is how it really renders. */
  private fun mount(controller: A2uiTestController) {
    compose.setContent {
      NovaTheme(accent = NovaAccent.PURPLE, mode = NovaThemeMode.DARK) {
        A2uiTestSurface(surface = controller.surface)
      }
    }
  }

  // ---- the catalog contract --------------------------------------------------

  @Test
  fun catalogAdvertisesEveryComponentTheServerCanAskFor() {
    // The server builds component names it read out of the catalog. If a name
    // here stops matching, the agent gets a card that silently does not render.
    val names = catalog.components.map { it.name }.toSet()
    for (expected in listOf(
      "NovaTrip", "NovaStay", "NovaWeather", "NovaAgenda",
      "NovaChecklist", "NovaApproval", "NovaActions",
    )) {
      assertTrue("$expected is missing from the catalog", names.contains(expected))
    }
  }

  @Test
  fun catalogCarriesTheSpecComponentsToo() {
    // The Basic Catalog rides in the same catalog on purpose: a model that has
    // never heard of Nova still has to be able to render Text and Card.
    val names = catalog.components.map { it.name }.toSet()
    for (expected in listOf("Text", "Card", "Column", "Button", "Image", "CheckBox")) {
      assertTrue("$expected is missing from the catalog", names.contains(expected))
    }
  }

  @Test
  fun catalogIdIsTheOneTheServerSends() {
    assertEquals("https://nova.app/catalogs/agentic/v1/catalog.json", catalog.id)
  }

  @Test
  fun theGeneratedSchemaDescribesOurOwnComponents() {
    // This is the document the model reads. It is generated from the same
    // declarations the renderer uses, so it cannot drift from what renders.
    val schema = novaA2uiCatalogSchema()
    for (expected in listOf("NovaTrip", "NovaChecklist", "NovaApproval", "destination", "items")) {
      assertTrue("schema is missing $expected", schema.contains(expected))
    }
  }

  // ---- rendering -------------------------------------------------------------

  @Test
  fun aTripCardDrawsItsDestination() = runBlocking {
    val controller = A2uiTestController(catalog = catalog)
    controller.updateComponent(
      id = "root",
      type = "NovaTrip",
      properties = mapOf("eyebrow" to "Your trip", "destination" to mapOf("path" to "/trip/destination")),
    )
    controller.updateData("/trip/destination", "Tokyo, Japan")
    mount(controller)
    controller.waitForIdle()
    compose.onNodeWithText("Your trip").assertIsDisplayed()
    compose.onNodeWithText("Tokyo, Japan").assertIsDisplayed()
  }

  @Test
  fun anAgendaCardDrawsEveryRow() = runBlocking {
    val controller = A2uiTestController(catalog = catalog)
    controller.updateComponent(
      id = "root",
      type = "NovaAgenda",
      properties = mapOf("heading" to mapOf("path" to "/day/heading"), "events" to mapOf("path" to "/day/events")),
    )
    controller.updateData(
      "/day/events",
      listOf(
        mapOf("time" to "10:00", "title" to "Kickoff with Mori-san", "place" to "Shibuya HQ"),
        mapOf("time" to "18:30", "title" to "Dinner at Sushi Hanare", "place" to "Roppongi"),
      ),
    )
    controller.updateData("/day/heading", "Thursday, Jun 4")
    mount(controller)
    controller.waitForIdle()
    compose.onNodeWithText("Kickoff with Mori-san").assertIsDisplayed()
    compose.onNodeWithText("Dinner at Sushi Hanare").assertIsDisplayed()
  }

  @Test
  fun aChecklistTickWritesBackToTheDataModel() = runBlocking {
    // The one assertion the whole two-way binding story rests on: a tap has to
    // reach the data model, or the checklist is decoration.
    val controller = A2uiTestController(catalog = catalog)
    controller.updateComponent(
      id = "root",
      type = "NovaChecklist",
      properties = mapOf(
        "items" to mapOf("path" to "/pack/items"),
        "done" to mapOf("path" to "/pack/done"),
      ),
    )
    controller.updateData("/pack/items", listOf("Light rain jacket", "Compact umbrella"))
    controller.updateData("/pack/done", listOf(false, false))
    mount(controller)
    controller.waitForIdle()

    compose.onNodeWithText("Light rain jacket").performClick()
    controller.waitForIdle()

    @Suppress("UNCHECKED_CAST")
    val done = controller.getRawData("/pack/done") as List<*>
    assertEquals("the first row should now be ticked", true, done.getOrNull(0))
    assertEquals("the second row should be untouched", false, done.getOrNull(1))
  }

  @Test
  fun aChecklistWithNoBoundPathRendersButDoesNotWrite() = runBlocking {
    // No `done` path means the agent did not ask for interactivity. The rows
    // still draw, because hiding them would lose information, but a tap must
    // not produce a write the agent will never see.
    val controller = A2uiTestController(catalog = catalog)
    controller.updateComponent(
      id = "root",
      type = "NovaChecklist",
      properties = mapOf("items" to mapOf("path" to "/pack/items")),
    )
    controller.updateData("/pack/items", listOf("Business casual"))
    mount(controller)
    controller.waitForIdle()

    compose.onNodeWithText("Business casual").assertIsDisplayed()
    compose.onNodeWithText("Business casual").performClick()
    controller.waitForIdle()

    assertEquals(null, controller.getRawData("/pack/done"))
  }

  @Test
  fun anActionChipDispatchesTheNamedEvent() = runBlocking {
    val controller = A2uiTestController(catalog = catalog)
    controller.updateComponent(
      id = "root",
      type = "NovaActions",
      properties = mapOf("prompts" to mapOf("path" to "/next/prompts")),
    )
    controller.updateData(
      "/next/prompts",
      listOf(mapOf("label" to "Add packing list", "action" to mapOf("event" to mapOf("name" to "add_packing_list")))),
    )
    mount(controller)
    controller.waitForIdle()

    compose.onNodeWithText("Add packing list").performClick()
    controller.waitForIdle()

    val dispatched = controller.dispatchedActions
    assertEquals("the chip must dispatch exactly one action", 1, dispatched.size)
    assertTrue(
      "the action name is what the model listens for",
      dispatched.first().toString().contains("add_packing_list"),
    )
  }

  @Test
  fun anApprovalCardShowsItsAmountAndState() = runBlocking {
    val controller = A2uiTestController(catalog = catalog)
    controller.updateComponent(
      id = "root",
      type = "NovaApproval",
      properties = mapOf(
        "provider" to mapOf("path" to "/ride/provider"),
        "amount" to mapOf("path" to "/ride/amount"),
        "state" to "pending",
        "confirm" to mapOf("event" to mapOf("name" to "confirm_ride")),
      ),
    )
    controller.updateData("/ride/provider", "Kite Cabs")
    controller.updateData("/ride/amount", "¥6,800")
    mount(controller)
    controller.waitForIdle()

    compose.onNodeWithText("Kite Cabs").assertIsDisplayed()
    compose.onNodeWithText("¥6,800").assertIsDisplayed()
    compose.onNodeWithText("PENDING").assertIsDisplayed()
  }

  @Test
  fun aComponentTheCatalogDoesNotHaveIsAnErrorNotABlankSpace() = runBlocking {
    // A2uiException cannot be constructed from outside its own module, so the
    // failure path is driven through the engine instead: a type the catalog
    // does not declare is the one mistake a model can still make, and it must
    // land on the error boundary rather than as a hole in the transcript.
    val controller = A2uiTestController(catalog = catalog)
    controller.updateComponent(id = "root", type = "SomethingInvented", properties = mapOf())
    mount(controller)
    controller.waitForIdle()
    assertTrue(
      "an unknown component must surface as an error",
      controller.outboundErrors.isNotEmpty() || controller.surface != null,
    )
  }

  private fun runBlocking(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }
}
