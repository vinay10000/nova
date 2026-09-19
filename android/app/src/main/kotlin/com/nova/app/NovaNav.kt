package com.nova.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.*
import com.nova.app.data.SessionToken
import com.nova.app.data.createNovaApi

/**
 * App shell. §5 top-level destinations live in a bottom navigation bar — they
 * were previously reachable only through the chat drawer, which hides four
 * primary surfaces behind one tap and gives no sense of place. The drawer now
 * holds only conversation history, which is chat context, not app navigation.
 *
 * Navigation rules kept here so screens never touch the controller:
 *   - Tab tap  : single instance, per-tab state saved and restored.
 *   - Reselect : unwinds that tab to its root.
 *   - Back     : on any tab other than Chat, goes to Chat before exiting.
 *   - IME open : the bar slides away so the composer owns the keyboard inset.
 */
private data class NovaTab(val route: String, val label: String, val icon: ImageVector)

/** Routes that are children of a tab: the bar stays as the way out. */
private val childRoutes = mapOf("allchats" to "chat", "connections" to "settings")

@Composable
fun NovaNav(session: SessionToken, onPreferencesChanged: () -> Unit = {}) {
  val nav = rememberNavController()
  val api = remember { createNovaApi(session) }
  // One ChatViewModel for the whole session, so All Chats can open a thread
  // into the same chat screen state.
  val chatVm: ChatViewModel = viewModel()
  var authenticated by remember { mutableStateOf(session.get() != null) }
  if (!authenticated) {
    LoginScreen(api) { token -> session.save(token); authenticated = true }
    return
  }

  val tabs = remember {
    listOf(
      NovaTab("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
      NovaTab("agents", "Agents", Icons.Default.AccountTree),
      NovaTab("activity", "Activity", Icons.Default.History),
      NovaTab("settings", "Settings", Icons.Default.Settings),
    )
  }
  val backStackEntry by nav.currentBackStackEntryAsState()
  val route = backStackEntry?.destination?.route
  val tabRoutes = tabs.map { it.route }
  val selectedTab = when {
    route == null -> "chat"
    route in tabRoutes -> route
    else -> childRoutes[route] ?: "chat"
  }
  // Keyboard visible => the bar yields. Chat composer and search fields then
  // own the whole bottom inset instead of stacking two paddings.
  val density = LocalDensity.current
  val imeVisible = WindowInsets.ime.getBottom(density) > 0
  val showBar = !imeVisible

  fun goToTab(target: String) {
    if (target == selectedTab) {
      // Reselect: unwind the tab to its root instead of stacking duplicates.
      nav.popBackStack(target, inclusive = false)
      return
    }
    nav.navigate(target) {
      // Route-based popUpTo keeps one instance per tab on every navigation
      // version (no findStartDestination dependency).
      popUpTo("chat") { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  // Back from a secondary tab lands on Chat first — the standard Android
  // bottom-nav contract, and cheaper than killing the app.
  BackHandler(enabled = route != null && route != "chat" && route in tabRoutes) {
    nav.navigate("chat") {
      popUpTo("chat") { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.surface,
    bottomBar = {
      AnimatedVisibility(
        visible = showBar,
        enter = slideInVertically(tween(NovaMotion.Standard)) { it } + fadeIn(tween(NovaMotion.Standard)),
        exit = slideOutVertically(tween(NovaMotion.Quick)) { it } + fadeOut(tween(NovaMotion.Quick)),
      ) {
        NovaBottomBar(tabs = tabs, selected = selectedTab, onSelect = ::goToTab)
      }
    },
  ) { pad ->
    NavHost(nav, startDestination = "chat", modifier = Modifier.padding(pad)) {
      tabComposable("chat") {
        ChatScreen(
          api, session,
          onConnectionsClick = { nav.navigate("connections") },
          onAllChatsClick = { nav.navigate("allchats") },
          vm = chatVm,
        )
      }
      childComposable("allchats") {
        AllChatsScreen(
          api,
          onOpen = { id, history ->
            chatVm.openConversation(id, history)
            nav.popBackStack()
          },
          onBack = { nav.popBackStack() },
        )
      }
      tabComposable("agents") { AgentsScreen(api) }
      tabComposable("activity") { ActivityScreen(api) }
      childComposable("connections") { ConnectionsScreen(api, session, onBack = { nav.popBackStack() }) }
      tabComposable("settings") {
        SettingsScreen(
          session,
          onLogout = { session.clear(); authenticated = false },
          onPreferencesChanged = onPreferencesChanged,
          onConnectionsClick = { nav.navigate("connections") },
        )
      }
    }
  }
}

/** Tab destination: siblings cross-fade. A slide here would imply a hierarchy. */
private fun NavGraphBuilder.tabComposable(route: String, content: @Composable () -> Unit) {
  composable(
    route,
    enterTransition = { fadeIn(tween(NovaMotion.Standard)) },
    exitTransition = { fadeOut(tween(NovaMotion.Quick)) },
    popEnterTransition = { fadeIn(tween(NovaMotion.Standard)) },
    popExitTransition = { fadeOut(tween(NovaMotion.Quick)) },
  ) { content() }
}

/** Child destination: slides in from the edge, so "one level deeper" reads. */
private fun NavGraphBuilder.childComposable(route: String, content: @Composable () -> Unit) {
  composable(
    route,
    enterTransition = { slideInHorizontally(tween(NovaMotion.Standard)) { it / 4 } + fadeIn(tween(NovaMotion.Standard)) },
    exitTransition = { fadeOut(tween(NovaMotion.Quick)) },
    popEnterTransition = { fadeIn(tween(NovaMotion.Standard)) },
    popExitTransition = { slideOutHorizontally(tween(NovaMotion.Standard)) { it / 4 } + fadeOut(tween(NovaMotion.Standard)) },
  ) { content() }
}

@Composable
private fun NovaBottomBar(tabs: List<NovaTab>, selected: String, onSelect: (String) -> Unit) {
  val scheme = MaterialTheme.colorScheme
  NavigationBar(
    containerColor = if (novaDark()) NovaPalette.DarkSurface else scheme.surface,
    tonalElevation = 0.dp,
  ) {
    tabs.forEach { tab ->
      val isSelected = tab.route == selected
      NavigationBarItem(
        selected = isSelected,
        onClick = { onSelect(tab.route) },
        icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
        alwaysShowLabel = true,
        modifier = Modifier.semantics {
          role = Role.Tab
          contentDescription = if (isSelected) "${tab.label}, selected" else tab.label
        },
      )
    }
  }
}