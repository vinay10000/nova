package com.nova.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.app.data.ChatStreamClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

// §8 entities (backend-owned; Room cache mirrors these).
@kotlinx.serialization.Serializable
data class Message(val id: String = "", val role: String, val content: String)
@kotlinx.serialization.Serializable
data class Agent(val id: String = "", val name: String, val goal: String, val status: String = "draft")

/**
 * §6 chat VM: streams tokens progressively, supports stop/regenerate/retry/edit (§6).
 * No fake responses — every token comes from the backend (§61).
 */
class ChatViewModel(
  private val client: ChatStreamClient = ChatStreamClient(),
  private var conversationId: String? = null,
) : ViewModel() {
  private val _messages = MutableStateFlow<List<Message>>(emptyList())
  val messages: StateFlow<List<Message>> = _messages.asStateFlow()
  private val _streaming = MutableStateFlow(false)
  val streaming: StateFlow<Boolean> = _streaming.asStateFlow()
  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  private var job: Job? = null
  private var lastUserText: String? = null

  fun send(text: String) {
    if (text.isBlank() || _streaming.value) return
    val cid = conversationId ?: return _error.set("No conversation. Create one first.")
    lastUserText = text

    _messages.value += Message(role = "user", content = text)
    // Placeholder assistant row that tokens append into — gives live progress (§6).
    _messages.value += Message(role = "assistant", content = "")
    _error.value = null
    _streaming.value = true

    job = viewModelScope.launch {
      client.stream(cid, text)
        .catch { _error.value = it.message ?: "stream_failed" }
        .collect { chunk ->
          when (chunk.type) {
            "token" -> appendToLast(chunk.text.orEmpty())
            "error" -> _error.value = chunk.code ?: "stream_error"
            "done" -> _streaming.value = false
          }
        }
      _streaming.value = false
    }
  }

  private fun appendToLast(text: String) {
    val list = _messages.value.toMutableList()
    val last = list.lastOrNull() ?: return
    list[list.lastIndex] = last.copy(content = last.content + text)
    _messages.value = list
  }

  /** §6 stop generation — cancels the SSE call, keeping partial output. */
  fun stop() {
    job?.cancel()
    job = null
    _streaming.value = false
  }

  /** §6 regenerate — drop the assistant reply and resend the same prompt. */
  fun regenerate() {
    val prompt = lastUserText ?: return
    stop()
    val list = _messages.value.toMutableList()
    if (list.lastOrNull()?.role == "assistant") list.removeAt(list.lastIndex)
    if (list.lastOrNull()?.role == "user") list.removeAt(list.lastIndex)
    _messages.value = list
    send(prompt)
  }

  /** §6 edit user message — rewinds to that message and resends the edited text. */
  fun editAndResend(index: Int, newText: String) {
    stop()
    _messages.value = _messages.value.take(index)
    send(newText)
  }

  fun retry() = regenerate()

  fun newChat(id: String? = null) {
    stop()
    conversationId = id
    lastUserText = null
    _error.value = null
    _messages.value = emptyList()
  }

  fun openConversation(id: String, history: List<Message>) {
    stop()
    conversationId = id
    _messages.value = history
  }

  fun clearError() { _error.value = null }
}

@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
  val messages by vm.messages.collectAsState()
  val streaming by vm.streaming.collectAsState()
  val error by vm.error.collectAsState()
  val clipboard = LocalClipboardManager.current
  var input by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  // Follow the newest token as it streams in.
  LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
  }

  Column(Modifier.fillMaxSize().imePadding()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      TextButton(onClick = { vm.newChat() }) { Text("New Chat") }
    }

    LazyColumn(Modifier.weight(1f), state = listState) {
      if (messages.isEmpty()) {
        item {
          Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Start chatting — no agent knowledge needed.", style = MaterialTheme.typography.bodyLarge)
          }
        }
      }
      items(messages.size) { i ->
        val m = messages[i]
        ListItem(
          headlineContent = { Text(m.content.ifEmpty { if (streaming) "…" else "" }) },
          supportingContent = { Text(if (m.role == "user") "You" else "Nova") },
          trailingContent = {
            if (m.role == "assistant" && m.content.isNotEmpty()) {
              Row {
                IconButton(onClick = { clipboard.setText(AnnotatedString(m.content)) }) {
                  Icon(Icons.Default.ContentCopy, contentDescription = "Copy message")
                }
                IconButton(onClick = { vm.regenerate() }) {
                  Icon(Icons.Default.Refresh, contentDescription = "Regenerate response")
                }
              }
            }
          },
        )
      }
      // §6 retry affordance on failure.
      error?.let { code ->
        item {
          ListItem(
            headlineContent = { Text("Something went wrong: $code") },
            trailingContent = {
              Row {
                TextButton(onClick = { vm.clearError(); vm.retry() }) { Text("Retry") }
              }
            },
          )
        }
      }
    }

    if (streaming) LinearProgressIndicator(Modifier.fillMaxWidth())

    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
      // §7 input: text + attach + voice + model + send/stop.
      OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.weight(1f),
        placeholder = { Text("Message Nova...") },
        maxLines = 6, // expands naturally for longer messages (§7)
      )
      Spacer(Modifier.width(8.dp))
      if (streaming) {
        Button(onClick = { vm.stop() }) { Text("Stop") }
      } else {
        Button(
          onClick = { vm.send(input); input = "" },
          enabled = input.isNotBlank() && !streaming,
        ) { Text("Send") }
      }
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
