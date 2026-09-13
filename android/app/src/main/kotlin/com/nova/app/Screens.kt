package com.nova.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
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
import com.nova.app.voice.AndroidVoiceInput
import com.nova.app.voice.VoiceOutput
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/** §10 display name of a picked document. */
private fun queryDisplayName(cr: android.content.ContentResolver, uri: Uri): String? =
  cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
    if (c.moveToFirst()) c.getString(0) else null
  }

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
  private var uploadApi: NovaApi? = null // set via configureApi(); uploads go through the same authenticated client

  // §10 attachments pending on the next message. Ids are backend-issued; nothing is trusted client-side.
  data class PendingAttachment(val id: String, val filename: String, val size: Long)
  private val _pending = MutableStateFlow<List<PendingAttachment>>(emptyList())
  val pending: StateFlow<List<PendingAttachment>> = _pending.asStateFlow()
  private val _uploading = MutableStateFlow(false)
  val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

  fun configureApi(api: NovaApi) { uploadApi = api }

  /** §10: upload a picked content:// uri, then hold its backend id for the next send. */
  fun addAttachment(context: android.content.Context, uri: android.net.Uri) {
    if (_uploading.value || _streaming.value) return
    val api = uploadApi ?: return
    viewModelScope.launch {
      _uploading.value = true
      _error.value = null
      runCatching {
        val cr = context.contentResolver
        // Cheap pre-check; the backend re-validates (trust boundary stays server-side).
        val size = cr.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (size > 20L * 1024 * 1024) throw IllegalStateException("file_too_large")
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("unreadable")
        val name = queryDisplayName(cr, uri) ?: "attachment"
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        api.uploadFile(okhttp3.MultipartBody.Part.createFormData("file", name, body))
      }.onSuccess { dto ->
        _pending.value = _pending.value + PendingAttachment(dto.id, dto.filename, dto.size)
      }.onFailure { _error.value = "upload_failed" }
      _uploading.value = false
    }
  }

  fun removeAttachment(id: String) {
    _pending.value = _pending.value.filter { it.id != id }
  }

  fun send(text: String) {
    if (text.isBlank() || _streaming.value) return
    val cid = conversationId ?: run {
      _error.value = "No conversation. Create one first."
      return
    }
    lastUserText = text
    val attachmentIds = _pending.value.map { it.id } // consumed once, then cleared
    _pending.value = emptyList()

    _messages.value += Message(role = "user", content = text)
    // Placeholder assistant row that tokens append into — gives live progress (§6).
    _messages.value += Message(role = "assistant", content = "")
    _error.value = null
    _streaming.value = true

    job = viewModelScope.launch {
      client.stream(cid, text, model, attachmentIds)
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
    _pending.value = emptyList()
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
  LaunchedEffect(api) { vm.configureApi(api) } // §10 uploads
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
  val pending by vm.pending.collectAsState()
  val uploading by vm.uploading.collectAsState()
  var input by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(DrawerValue.Closed)

  // §11 voice — modular engine wired here, never inside ChatViewModel.
  val voice = remember { AndroidVoiceInput(context) { input = it } }
  var micGranted by remember {
    mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED)
  }
  val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    micGranted = granted
    if (granted) voice.start()
  }
  DisposableEffect(Unit) { onDispose { voice.destroy() } }
  val tts = remember { VoiceOutput(context) }
  DisposableEffect(Unit) { onDispose { tts.shutdown() } }

  // §10 one picker for both images and documents — the backend sniffs the real type.
  val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let { vm.addAttachment(context, it) }
  }

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
                  // §11 output: read the response aloud (swappable VoiceOutput).
                  IconButton(onClick = { tts.speak(m.content) }) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Read aloud")
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

    // §10 pending attachments — tap a chip to remove it.
    if (pending.isNotEmpty() || uploading) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        if (uploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        pending.forEach { p ->
          AssistChip(
            onClick = { vm.removeAttachment(p.id) },
            label = { Text(p.filename, maxLines = 1) },
            modifier = Modifier.padding(horizontal = 4.dp),
          )
        }
      }
    }

    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
      // §7 input: text, attachments (§10), voice (§11), send/stop, model selector above.
      IconButton(onClick = { pickFile.launch("*/*") }, enabled = !uploading && !streaming) {
        Icon(Icons.Default.Attachment, contentDescription = "Attach file")
      }
      if (micGranted && voice.available) {
        IconButton(onClick = { voice.start() }) {
          Icon(Icons.Default.Mic, contentDescription = "Voice input")
        }
      } else {
        // §43/§11: mic permission requested in context — when the user reaches for the mic.
        IconButton(onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }) {
          Icon(Icons.Default.Mic, contentDescription = "Enable voice input")
        }
      }
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
  // §43: notifications permission is requested HERE, in context of the user asking for
  // notifications — never at first launch.
  val context = LocalContext.current
  var granted by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= 33)
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
      else true,
    )
  }
  val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
  Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text("Settings", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    if (Build.VERSION.SDK_INT >= 33) {
      ListItem(
        headlineContent = { Text("Agent notifications") },
        supportingContent = { Text(if (granted) "Allowed" else "Off — you will not hear about agent runs") },
        trailingContent = {
          Switch(
            checked = granted,
            onCheckedChange = { on ->
              if (on) request.launch(Manifest.permission.POST_NOTIFICATIONS)
              else context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
              })
            },
          )
        },
      )
    } else {
      Text("Notifications follow the system setting (Android < 13).")
    }
    // §39 memory view/edit/delete and §12 builder prefs land with Phases 3+.
  }
}
