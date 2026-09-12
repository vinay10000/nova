package com.nova.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import com.nova.app.data.SessionToken
import com.nova.app.data.createNovaApi

// §5 primary navigation: Chat | Agents | Activity | Connections | Settings. Chat first + New Chat.
@Composable
fun NovaNav(session: SessionToken) {
  val nav = rememberNavController()
  val api = remember { createNovaApi(session) }
  var authenticated by remember { mutableStateOf(session.get() != null) }
  if (!authenticated) {
    LoginScreen(api) { token -> session.save(token); authenticated = true }
    return
  }
  val tabs = listOf("chat", "agents", "activity", "connections", "settings")
  Scaffold(
    bottomBar = {
      NavigationBar {
        tabs.forEach { t ->
          NavigationBarItem(
            selected = false,
            onClick = { nav.navigate(t) },
            label = { Text(t.replaceFirstChar { it.uppercase() }) },
            icon = {}
          )
        }
      }
    }
  ) { pad ->
    NavHost(nav, startDestination = "chat", Modifier.padding(pad)) {
      composable("chat") { ChatScreen(api, session) }
      composable("agents") { AgentsScreen(onCreate = { nav.navigate("chat") }) }
      composable("activity") { ActivityScreen() }
      composable("connections") { ConnectionsScreen() }
      composable("settings") { SettingsScreen() }
    }
  }
}
