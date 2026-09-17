package com.nova.app

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.app.data.SessionToken
import com.nova.app.data.createNovaApi

private data class NovaDestination(val route: String, val label: String, val icon: ImageVector)

private val primaryDestinations = listOf(
  NovaDestination("chat", "Chat", Icons.Outlined.ChatBubbleOutline),
  NovaDestination("agents", "Agents", Icons.Outlined.AutoAwesome),
  NovaDestination("activity", "Activity", Icons.Outlined.History),
  NovaDestination("connections", "Connections", Icons.Outlined.Link),
)

/** Keep chat as the root, preserve tab state, and never stack repeated tab taps. */
internal fun NavHostController.openNovaDestination(route: String) {
  if (currentDestination?.route == route) return
  navigate(route) {
    popUpTo("chat") { saveState = true }
    launchSingleTop = true
    restoreState = true
  }
}

/** Presentation seam: navigation can be tested without starting a backend session. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NovaAppFrame(
  currentRoute: String?,
  onDestination: (String) -> Unit,
  keyboardVisible: Boolean = WindowInsets.isImeVisible,
  content: @Composable (Modifier) -> Unit,
) {
  val showNavigation = primaryDestinations.any { it.route == currentRoute }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp
    Scaffold(
      containerColor = MaterialTheme.colorScheme.surface,
      bottomBar = {
        if (showNavigation && !useRail && !keyboardVisible) {
          NavigationBar(
            modifier = Modifier.testTag("nova-bottom-navigation"),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
          ) {
            primaryDestinations.forEach { destination ->
              NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onDestination(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  selectedTextColor = MaterialTheme.colorScheme.primary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                  unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
              )
            }
          }
        }
      },
    ) { padding ->
      Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
        if (showNavigation && useRail) {
          NavigationRail(
            modifier = Modifier.fillMaxHeight().width(112.dp)
              .testTag("nova-navigation-rail").verticalScroll(rememberScrollState()),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            windowInsets = WindowInsets(0, 0, 0, 0),
            header = {
              Text(
                "Nova",
                modifier = Modifier.padding(vertical = 24.dp),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            },
          ) {
            primaryDestinations.forEach { destination ->
              NavigationRailItem(
                modifier = Modifier.padding(vertical = 8.dp),
                selected = currentRoute == destination.route,
                onClick = { onDestination(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  selectedTextColor = MaterialTheme.colorScheme.primary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                  unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
              )
            }
          }
        }
        content(Modifier.weight(1f).fillMaxHeight())
      }
    }
  }
}

@Composable
fun NovaNav(session: SessionToken, onAccentChanged: () -> Unit = {}) {
  val nav = rememberNavController()
  val api = remember { createNovaApi(session) }
  // Keep the existing session-wide view model: tab changes must not replace a chat.
  val chatVm: ChatViewModel = viewModel()
  var authenticated by remember { mutableStateOf(session.get() != null) }
  if (!authenticated) {
    LoginScreen(api) { token -> session.save(token); authenticated = true }
    return
  }
  val entry by nav.currentBackStackEntryAsState()
  NovaAppFrame(
    currentRoute = entry?.destination?.route,
    onDestination = { nav.openNovaDestination(it) },
  ) { contentModifier ->
    NavHost(nav, startDestination = "chat", modifier = contentModifier) {
      composable(
        "chat",
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) },
      ) {
        ChatScreen(
          api, session,
          onSettingsClick = { nav.navigate("settings") { launchSingleTop = true } },
          onConnectionsClick = { nav.openNovaDestination("connections") },
          onAgentsClick = { nav.openNovaDestination("agents") },
          onActivityClick = { nav.openNovaDestination("activity") },
          onAllChatsClick = { nav.navigate("allchats") { launchSingleTop = true } },
          vm = chatVm,
        )
      }
      composable(
        "allchats",
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) },
      ) {
        AllChatsScreen(
          api,
          onOpen = { id, history ->
            chatVm.openConversation(id, history)
            nav.popBackStack()
          },
          onBack = { nav.popBackStack() },
        )
      }
      composable(
        "agents",
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) },
      ) { AgentsScreen(api, onBack = { nav.popBackStack() }) }
      composable(
        "activity",
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) },
      ) { ActivityScreen(api, onBack = { nav.popBackStack() }) }
      composable(
        "connections",
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) },
      ) { ConnectionsScreen(api, session, onBack = { nav.popBackStack() }) }
      composable(
        "settings",
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) },
      ) {
        SettingsScreen(
          session,
          onLogout = { session.clear(); authenticated = false },
          onBack = { nav.popBackStack() },
          onAccentChanged = onAccentChanged,
          // This is a nested visit: Back returns to Settings, not the chat root.
          onConnectionsClick = { nav.navigate("connections") { launchSingleTop = true } },
        )
      }
    }
  }
}
