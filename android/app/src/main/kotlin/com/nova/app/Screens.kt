package com.nova.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// §8 entities (backend-owned; Room cache mirrors these).
@kotlinx.serialization.Serializable
data class Message(val id: String = "", val role: String, val content: String)
@kotlinx.serialization.Serializable
data class Agent(val id: String = "", val name: String, val goal: String, val status: String = "draft")

// §6 chat VM: streaming, stop, regenerate, retry. Voice/files attach in Phase 2.
class ChatViewModel : ViewModel() {
  private val _messages = MutableStateFlow<List<Message>>(emptyList())
  val messages: StateFlow<List<Message>> = _messages
  private val _streaming = MutableStateFlow(false)
  val streaming: StateFlow<Boolean> = _streaming

  fun send(text: String) {
    viewModelScope.launch {
      _messages.value += Message(role = "user", content = text)
      _streaming.value = true
      // TODO Phase 1: Retrofit SSE to POST /v1/chat/stream, append chunks progressively (§6).
      _messages.value += Message(role = "assistant", content = "TODO: stream from backend Gemini.")
      _streaming.value = false
    }
  }
  fun stop() { _streaming.value = false /* TODO cancel SSE call */ }
  fun newChat() { _messages.value = emptyList() }
}

@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
  val messages by vm.messages.collectAsState()
  val streaming by vm.streaming.collectAsState()
  var input by remember { mutableStateOf("") }
  Column(Modifier.fillMaxSize().imePadding()) {
    // New Chat action (§5)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      TextButton(onClick = { vm.newChat() }) { Text("New Chat") }
    }
    LazyColumn(Modifier.weight(1f)) {
      items(messages) { m ->
        ListItem(headlineContent = { Text(m.content) }, supportingContent = { Text(m.role) },
          trailingContent = { TextButton(onClick = {}) { Text("Copy") } }) // copy/regenerate/retry/edit/share per §6
      }
      if (messages.isEmpty()) item { Text("Start chatting — no agent knowledge needed.", modifier = Modifier.padding(16.dp)) }
    }
    if (streaming) LinearProgressIndicator(Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth().padding(8.dp)) {
      // §7 input: text + attach + voice + model + send/stop
      OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Message Nova…") })
      if (streaming) Button(onClick = { vm.stop() }) { Text("Stop") }
      else Button(onClick = { vm.send(input); input = "" }) { Text("Send") }
    }
  }
}

@Composable fun AgentsScreen(onCreate: () -> Unit) {
  // §12-§14 builder entry: natural language -> config card -> Edit/Activate. Agents feel like chat extension (§5,§42).
  Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text("Agents", style = MaterialTheme.typography.headlineMedium)
    Text("Describe what to automate. AI asks missing questions, then shows config for review.")
    Spacer(Modifier.height(12.dp))
    Button(onClick = onCreate) { Text("Create Agent") }
  }
}
@Composable fun ActivityScreen() {
  // §44 Today list -> execution details (§34-§35).
  Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Activity", style = MaterialTheme.typography.headlineMedium); Text("TODO: executions SSE/poll.") }
}
@Composable fun ConnectionsScreen() {
  // §38 connect/reauthorize/disconnect + inspect scopes. Tokens stay backend-encrypted.
  Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Connections", style = MaterialTheme.typography.headlineMedium); Text("GitHub/Gmail/Slack/Notion/X/WhatsApp — TODO OAuth via backend.") }
}
@Composable fun SettingsScreen() {
  // Notifications prefs (§43), memory view/edit/delete (§39), model selection (§7).
  Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Settings", style = MaterialTheme.typography.headlineMedium); Text("TODO: notifications, memory, model.") }
}
