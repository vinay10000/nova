package com.nova.app

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.nova.app.data.ChatStreamClient
import com.nova.app.data.ConversationDto
import com.nova.app.data.LoginRequest
import com.nova.app.data.ModelDto
import com.nova.app.data.NovaApi
import com.nova.app.data.SessionToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(api: NovaApi, onAuthenticated: (String) -> Unit) {
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var registering by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
    Text("Welcome to Nova", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
    OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(12.dp))
    Button(enabled = email.isNotBlank() && password.length >= 8, onClick = {
      scope.launch {
        error = null
        runCatching {
          if (registering) api.register(LoginRequest(email, password)) else api.login(LoginRequest(email, password))
        }.onSuccess { onAuthenticated(it.token) }
          .onFailure { error = "Unable to ${if (registering) "create account" else "sign in"}" }
      }
    }) { Text(if (registering) "Create account" else "Sign in") }
    TextButton(onClick = { registering = !registering; error = null }) {
      Text(if (registering) "Already have an account? Sign in" else "New to Nova? Create an account")
    }
  }
}

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
  private var client: ChatStreamClient = ChatStreamClient(),
  private var conversationId: String? = null,
) : ViewModel() {
  private val _messages = MutableStateFlow<List<Message>>(emptyList())
  val messages: StateFlow<List<Message>> = _messages.asStateFlow()
  private val _streaming = MutableStateFlow(false)
  val streaming: StateFlow<Boolean> = _streaming.asStateFlow()
  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  var model: String? = null          // §7 model selection; null = backend default

  private var job: Job? = null
  private var lastUserText: String? = null

  fun send(text: String) {
    if (text.isBlank() || _streaming.value) return
    val cid = conversationId ?: run {
      _error.value = "No conversation. Create one first."
      return
    }
    lastUserText = text

    _messages.value += Message(role = "user", content = text)
    // Placeholder assistant row that tokens append into — gives live progress (§6).
    _messages.value += Message(role = "assistant", content = "")
    _error.value = null
    _streaming.value = true

    job = viewModelScope.launch {
      client.stream(cid, text, model)
        .catch { _error.value = it.message ?: "stream_failed" }
        .collect { chunk ->
          when (chunk.type) {
            "token" -> appendToLast(chunk.text.orEmpty())
            "error" -> {
              _error.value = chunk.code ?: "stream_error"
              _streaming.value = false
            }
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

  /** §6 The last user message, for edit prefills. */
  fun userTextAt(index: Int): String? = _messages.value.getOrNull(index)?.takeIf { it.role == "user" }?.content

  fun retry() = regenerate()

  fun newChat(id: String? = null) {
    stop()
    conversationId = id
    lastUserText = null
    _error.value = null
    _messages.value = emptyList()
  }

  fun setConversation(id: String) {
    if (conversationId == null && id.isNotBlank()) conversationId = id
  }

  fun configureSession(session: SessionToken) {
    client = ChatStreamClient(tokenProvider = { session.get() })
  }

  fun openConversation(id: String, history: List<Message>) {
    stop()
    conversationId = id
    _messages.value = history
  }

  fun clearError() { _error.value = null }
}

// §6 Markdown rendering; highlighted code fences with copy button (§6 "copy code").
@Composable
fun MarkdownBody(content: String, streaming: Boolean) {
  val dark = isSystemInDarkTheme()
  val highlights = remember(dark) { dev.snipme.highlights.Highlights.Builder().theme(dev.snipme.highlights.model.SyntaxThemes.atom(darkMode = dark)) }
  Markdown(
    content = content,
    components = markdownComponents(
      codeFence = {
        MarkdownHighlightedCodeFence(
          content = it.content,
          node = it.node,
          style = it.typography.code,
          highlightsBuilder = highlights,
          showHeader = true, // header carries the language + copy button
        )
      },
    ),
    colors = markdownColor(),
    typography = markdownTypography(),
  )
}

@Composable
fun ChatScreen(api: NovaApi, session: SessionToken, vm: ChatViewModel = viewModel()) {
  LaunchedEffect(session) { vm.configureSession(session) }
  var conversations by remember { mutableStateOf<List<ConversationDto>>(emptyList()) }
  var models by remember { mutableStateOf<List<ModelDto>>(emptyList()) }
  var model by remember { mutableStateOf<String?>(null) }
  var editIndex by remember { mutableStateOf<Int?>(null) }
  var offline by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current

  suspend fun refreshConversations() {
    runCatching {
      conversations = api.conversations().conversations
      offline = false
    }.onFailure { offline = true }
  }
  LaunchedEffect(Unit) {
    runCatching { models = api.models().models }
    refreshConversations()
    runCatching {
      val existing = conversations.firstOrNull()
      if (existing != null) {
        val detail = api.conversation(existing.id)
        vm.openConversation(detail.id, detail.messages.map { Message(it.id, it.role, it.content) })
      } else {
        vm.setConversation(api.createConversation().id)
        refreshConversations()
      }
    }.onFailure { offline = true }
  }
  val messages by vm.messages.collectAsState()
  val streaming by vm.streaming.collectAsState()
  val error by vm.error.collectAsState()
  var input by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(DrawerValue.Closed)

  // Follow the newest token as it streams in.
  LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet {
        Text("Chats", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        conversations.forEach { c ->
          ListItem(
            headlineContent = { Text(c.title) },
            modifier = Modifier.clickable {
              scope.launch {
                runCatching {
                  val detail = api.conversation(c.id)
                  vm.openConversation(c.id, detail.messages.map { Message(it.id, it.role, it.content) })
                }
                drawerState.close()
              }
            },
          )
        }
        ListItem(
          headlineContent = { Text("New Chat") },
          modifier = Modifier.clickable {
            scope.launch {
              runCatching {
                val created = api.createConversation()
                vm.newChat(created.id)
                refreshConversations()
              }
              drawerState.close()
            }
          },
        )
      }
    },
  ) {
  Column(Modifier.fillMaxSize().imePadding()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = { scope.launch { drawerState.open() } }) { Text("Chats") }
      TextButton(onClick = {
        scope.launch { runCatching { vm.setConversation(api.createConversation().id); refreshConversations() } }
      }) { Text("New Chat") }
      Spacer(Modifier.weight(1f))
      // §7 model selector.
      if (models.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        TextButton(onClick = { expanded = true }) { Text(model?.removePrefix("gemini-") ?: "model") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
          models.forEach { m ->
            DropdownMenuItem(text = { Text(m.id) }, onClick = {
              model = m.id; vm.model = m.id; expanded = false
            })
          }
        }
      }
    }

    // §4 offline state.
    if (offline) {
      Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
          "Offline — showing cached chats. Retry when connected.",
          modifier = Modifier.padding(12.dp),
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
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
        if (editIndex == i) {
          // §6 edit user message and resend.
          var text by remember(m.id) { mutableStateOf(m.content) }
          Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f), maxLines = 6)
            Spacer(Modifier.width(8.dp))
            Button(onClick = { editIndex = null; vm.editAndResend(i, text) }) { Text("Send") }
            TextButton(onClick = { editIndex = null }) { Text("Cancel") }
          }
        } else {
          ListItem(
            headlineContent = {
              if (m.role == "assistant" && m.content.isNotEmpty()) {
                MarkdownBody(m.content, streaming && i == messages.lastIndex)
              } else {
                Text(m.content.ifEmpty { if (streaming) "…" else "" })
              }
            },
            supportingContent = { Text(if (m.role == "user") "You" else "Nova") },
            trailingContent = {
              Row {
                if (m.role == "assistant" && m.content.isNotEmpty() && !streaming) {
                  IconButton(onClick = { clipboard.setText(AnnotatedString(m.content)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy message")
                  }
                  // §6 share response.
                  IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"; putExtra(Intent.EXTRA_TEXT, m.content)
                    }
                    context.startActivity(Intent.createChooser(send, "Share response"))
                  }) {
                    Icon(Icons.Default.Share, contentDescription = "Share response")
                  }
                  IconButton(onClick = { vm.regenerate() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Regenerate response")
                  }
                }
                if (m.role == "user" && !streaming) {
                  IconButton(onClick = { editIndex = i }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit message")
                  }
                }
              }
            },
          )
        }
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
      // §7 input: text + send/stop; attachments and voice arrive with Phase 2.
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
