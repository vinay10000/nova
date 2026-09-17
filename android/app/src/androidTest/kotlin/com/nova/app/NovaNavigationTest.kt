package com.nova.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** No login, network, production account, or API key is needed by these tests. */
class NovaNavigationTest {
  @get:Rule val compose = createComposeRule()

  // Characterizes the existing theme contract before changing its visual tokens.
  @Test fun defaultThemeKeepsReadableBodyText() {
    var foreground = Color.Unspecified
    var background = Color.Unspecified
    compose.setContent {
      NovaTheme {
        foreground = MaterialTheme.colorScheme.onSurface
        background = MaterialTheme.colorScheme.surface
        Text("Nova")
      }
    }
    compose.onNodeWithText("Nova").assertIsDisplayed()
    compose.runOnIdle { assertTrue(contrast(foreground, background) >= 4.5f) }
  }

  // Characterizes Navigation Compose's existing chat-root / secondary-back contract.
  @Test fun secondaryScreenReturnsToChat() {
    lateinit var nav: NavHostController
    compose.setContent {
      val controller = rememberNavController()
      SideEffect { nav = controller }
      NavHost(controller, startDestination = "chat") {
        composable("chat") { Text("Chat content") }
        composable("settings") { Text("Settings content") }
      }
    }
    compose.runOnIdle { nav.navigate("settings") }
    compose.onNodeWithText("Settings content").assertIsDisplayed()
    compose.runOnIdle { assertTrue(nav.popBackStack()) }
    compose.onNodeWithText("Chat content").assertIsDisplayed()
  }

  @Test fun everyAccentHasReadableSemanticColorsInBothThemes() {
    NovaAccent.entries.forEach { accent ->
      listOf(false, true).forEach { dark ->
        val scheme = buildNovaScheme(accent, dark)
        listOf(
          scheme.onSurface to scheme.surface,
          scheme.onSurfaceVariant to scheme.surfaceContainerHigh,
          scheme.onPrimary to scheme.primary,
          scheme.onPrimaryContainer to scheme.primaryContainer,
          scheme.onSecondaryContainer to scheme.secondaryContainer,
          scheme.onTertiaryContainer to scheme.tertiaryContainer,
          scheme.onError to scheme.error,
          scheme.onErrorContainer to scheme.errorContainer,
        ).forEach { (foreground, background) ->
          assertTrue("Unreadable text for $accent, dark=$dark", contrast(foreground, background) >= 4.5f)
        }
        assertTrue("Control outline for $accent, dark=$dark", contrast(scheme.outline, scheme.surface) >= 3f)
      }
    }
  }

  @Test fun compactNavigationLabelsAndSelectionAreAccessible() {
    compose.setContent {
      NovaTheme {
        Box(Modifier.requiredWidth(360.dp)) {
          NovaAppFrame("chat", {}, keyboardVisible = false) { Text("Conversation") }
        }
      }
    }
    compose.onNodeWithTag("nova-bottom-navigation").assertIsDisplayed()
    compose.onNodeWithText("Chat").assertIsSelected()
    compose.onNodeWithText("Agents").assertIsDisplayed()
    compose.onNodeWithText("Activity").assertIsDisplayed()
    compose.onNodeWithText("Connections").assertIsDisplayed()
  }

  @Test fun wideNavigationUsesRail() {
    compose.setContent {
      NovaTheme {
        Box(Modifier.requiredWidth(840.dp)) {
          NovaAppFrame("agents", {}, keyboardVisible = false) { Text("Agent content") }
        }
      }
    }
    compose.onNodeWithTag("nova-navigation-rail").assertExists()
    compose.onNodeWithTag("nova-bottom-navigation").assertDoesNotExist()
  }

  @Test fun keyboardDoesNotCrowdCompactChat() {
    compose.setContent {
      NovaTheme {
        Box(Modifier.requiredWidth(360.dp)) {
          NovaAppFrame("chat", {}, keyboardVisible = true) { Text("Conversation") }
        }
      }
    }
    compose.onNodeWithTag("nova-bottom-navigation").assertDoesNotExist()
    compose.onNodeWithText("Conversation").assertIsDisplayed()
  }

  @Test fun secondaryScreensDoNotShowPrimaryNavigation() {
    compose.setContent {
      NovaTheme {
        NovaAppFrame("settings", {}, keyboardVisible = false) { Text("Settings content") }
      }
    }
    compose.onNodeWithTag("nova-bottom-navigation").assertDoesNotExist()
    compose.onNodeWithTag("nova-navigation-rail").assertDoesNotExist()
  }

  @Test fun switchingDestinationsDoesNotStackDuplicateScreens() {
    lateinit var nav: NavHostController
    compose.setContent {
      NovaTheme {
        val controller = rememberNavController()
        SideEffect { nav = controller }
        val entry = controller.currentBackStackEntryAsState().value
        Box(Modifier.requiredWidth(360.dp)) {
          NovaAppFrame(entry?.destination?.route, { controller.openNovaDestination(it) }, false) { modifier ->
            NavHost(controller, startDestination = "chat", modifier = modifier) {
              composable("chat") { Text("Chat content") }
              composable("agents") { Text("Agent content") }
              composable("activity") { Text("Activity content") }
              composable("connections") { Text("Connection content") }
            }
          }
        }
      }
    }
    compose.onNodeWithText("Agents").performClick()
    compose.onNodeWithText("Agents").performClick()
    compose.onNodeWithText("Activity").performClick()
    compose.onNodeWithText("Activity content").assertIsDisplayed()
    compose.onNodeWithText("Activity").assertIsSelected()
    compose.runOnIdle { assertTrue(nav.popBackStack()) }
    compose.onNodeWithText("Chat content").assertIsDisplayed()
    compose.runOnIdle { assertEquals("chat", nav.currentDestination?.route) }
  }

  private fun contrast(a: Color, b: Color): Float {
    val first = a.luminance()
    val second = b.luminance()
    return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
  }
}
