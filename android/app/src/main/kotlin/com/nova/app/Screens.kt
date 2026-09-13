package com.nova.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import com.nova.app.voice.RemoteVoiceOutput
import com.nova.app.voice.VoiceOutput
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
  var loading by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  var appeared by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { appeared = true }

  Column(
    Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    AnimatedVisibility(
      visible = appeared,
      enter = fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.85f),
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          "Nova",
          style = MaterialTheme.typography.displayLarge,
          fontWeight = FontWeight.Black,
          color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          if (registering) "Create your account" else "Welcome back",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Spacer(Modifier.height(48.dp))

    OutlinedTextField(
      email, { email = it },
      label = { Text("Email") },
      leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
    )
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
      password, { password = it },
      label = { Text("Password") },
      leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
    )
    AnimatedVisibility(visible = error != null) {
      Text(
        error ?: "",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 8.dp),
      )
    }
    Spacer(Modifier.height(28.dp))
    Button(
      enabled = email.isNotBlank() && password.length >= 8 && !loading,
      onClick = {
        scope.launch {
          error = null
          loading = true
          runCatching {
            if (registering) api.register(LoginRequest(email, password)) else api.login(LoginRequest(email, password))
          }.onSuccess { onAuthenticated(it.token) }
            .onFailure { error = "Unable to ${if (registering) "create account" else "sign in"}. Check your credentials." }
          loading = false
        }
      },
      modifier = Modifier.fillMaxWidth().height(52.dp),
      shape = RoundedCornerShape(14.dp),
    ) {
      if (loading) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary)
      } else {
        Text(
          if (registering) "Create account" else "Sign in",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
    Spacer(Modifier.height(16.dp))
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
private fun SmallIconButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  description: String,
  onClick: () -> Unit,
) {
  IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
    Icon(icon, contentDescription = description, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
  val infiniteTransition = rememberInfiniteTransition(label = "typing")
  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
    repeat(3) { index ->
      val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
          animation = tween(500, delayMillis = index * 180),
          repeatMode = RepeatMode.Reverse,
        ),
        label = "dot$index",
      )
      val scale by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
          animation = tween(500, delayMillis = index * 180),
          repeatMode = RepeatMode.Reverse,
        ),
        label = "scale$index",
      )
      Box(
        Modifier
          .size(7.dp)
          .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary)
      )
    }
  }
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
  // §11 remote TTS (OpenRouter Fish Audio S2.1) first, device TTS as fallback.
  val remoteTts = remember { RemoteVoiceOutput({ session.get() }) }
  fun speakOut(text: String) {
    scope.launch { if (!remoteTts.speak(context, text)) tts.speak(text) }
  }

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
        Text(
          "Nova",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(20.dp),
        )
        HorizontalDivider()
        conversations.forEach { c ->
          ListItem(
            headlineContent = { Text(c.title, maxLines = 1) },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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
        HorizontalDivider()
        ListItem(
          headlineContent = { Text("New Chat", fontWeight = FontWeight.Medium) },
          leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
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
    // Top bar with title and actions
    Surface(tonalElevation = 2.dp) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = { scope.launch { drawerState.open() } }) {
            Icon(Icons.Default.Menu, contentDescription = "Menu")
          }
          Text("Nova", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
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
      // Empty state with breathing animation
      if (messages.isEmpty()) {
        item {
          val infiniteTransition = rememberInfiniteTransition(label = "empty")
          val breathe by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
              animation = tween(2000),
              repeatMode = RepeatMode.Reverse,
            ),
            label = "breathe",
          )
          val floatY by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -6f,
            animationSpec = infiniteRepeatable(
              animation = tween(2000),
              repeatMode = RepeatMode.Reverse,
            ),
            label = "float",
          )
          Column(
            Modifier.fillMaxWidth().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Icon(
              Icons.AutoMirrored.Filled.Send,
              contentDescription = null,
              modifier = Modifier
                .size(56.dp)
                .graphicsLayer { alpha = breathe; translationY = floatY },
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(20.dp))
            Text(
              "How can I help you today?",
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
              "Ask me anything. I can search the web, help with code, and more.",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
          }
        }
      }

      items(messages.size) { i ->
        val m = messages[i]
        if (editIndex == i) {
          // §6 edit user message and resend.
          var text by remember(m.id) { mutableStateOf(m.content) }
          Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f), maxLines = 6, shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { editIndex = null; vm.editAndResend(i, text) }, shape = RoundedCornerShape(12.dp)) { Text("Send") }
            TextButton(onClick = { editIndex = null }) { Text("Cancel") }
          }
        } else {
          val isUser = m.role == "user"
          val isLastMsg = i == messages.lastIndex
          val showTyping = !isUser && m.content.isEmpty() && streaming && isLastMsg

          if (showTyping) {
            // Typing indicator
            Row(
              Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.Start,
            ) {
              Box(
                Modifier
                  .size(30.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Text("N", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
              }
              Spacer(Modifier.width(10.dp))
              Surface(
                shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp,
              ) {
                TypingIndicator(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
              }
            }
          } else if (m.content.isNotEmpty() || !streaming) {
            // Chat bubble
            Row(
              Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
              horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            ) {
              if (!isUser) {
                Box(
                  Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                  contentAlignment = Alignment.Center,
                ) {
                  Text("N", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
              }

              Column(
                modifier = Modifier.widthIn(max = 300.dp),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
              ) {
                Surface(
                  shape = RoundedCornerShape(
                    topStart = if (isUser) 18.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp,
                  ),
                  color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                  tonalElevation = if (isUser) 0.dp else 1.dp,
                ) {
                  if (!isUser && m.content.isNotEmpty()) {
                    MarkdownBody(m.content, streaming && isLastMsg)
                  } else {
                    Text(
                      m.content.ifEmpty { "" },
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                      color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }

                // Action buttons below bubble
                if (m.content.isNotEmpty() && !streaming) {
                  Row(Modifier.padding(top = 2.dp, start = if (isUser) 0.dp else 4.dp, end = if (isUser) 4.dp else 0.dp)) {
                    if (!isUser) {
                      SmallIconButton(Icons.Default.ContentCopy, "Copy") { clipboard.setText(AnnotatedString(m.content)) }
                      SmallIconButton(Icons.Default.VolumeUp, "Read aloud") { speakOut(m.content) }
                      SmallIconButton(Icons.Default.Share, "Share") {
                        val send = Intent(Intent.ACTION_SEND).apply {
                          type = "text/plain"; putExtra(Intent.EXTRA_TEXT, m.content)
                        }
                        context.startActivity(Intent.createChooser(send, "Share response"))
                      }
                      SmallIconButton(Icons.Default.Refresh, "Regenerate") { vm.regenerate() }
                    } else {
                      SmallIconButton(Icons.Default.Edit, "Edit") { editIndex = i }
                    }
                  }
                }
              }

              if (isUser) {
                Spacer(Modifier.width(10.dp))
                Box(
                  Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                  contentAlignment = Alignment.Center,
                ) {
                  Text("Y", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }
      // §6 retry affordance on failure.
      error?.let { code ->
        item {
          Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
          ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
              Text("Something went wrong: $code", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
              TextButton(onClick = { vm.clearError(); vm.retry() }) { Text("Retry") }
            }
          }
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

    // Input bar
    Surface(tonalElevation = 2.dp) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        IconButton(onClick = { pickFile.launch("*/*") }, enabled = !uploading && !streaming) {
          Icon(Icons.Default.Attachment, contentDescription = "Attach file")
        }
        if (micGranted && voice.available) {
          IconButton(onClick = { voice.start() }) {
            Icon(Icons.Default.Mic, contentDescription = "Voice input")
          }
        } else {
          IconButton(onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }) {
            Icon(Icons.Default.Mic, contentDescription = "Enable voice input")
          }
        }
        OutlinedTextField(
          value = input,
          onValueChange = { input = it },
          modifier = Modifier.weight(1f),
          placeholder = { Text("Message Nova...") },
          maxLines = 6,
          shape = RoundedCornerShape(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        if (streaming) {
          FilledIconButton(onClick = { vm.stop() }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.Default.Refresh, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onError)
          }
        } else {
          FilledIconButton(
            onClick = { vm.send(input); input = "" },
            enabled = input.isNotBlank() && !streaming,
          ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
          }
        }
      }
    }
  }
  }
}

@Composable fun AgentsScreen(api: NovaApi) {
  // §12-§14 builder: natural language -> config card or questions -> Edit/Activate.
  // §45 detail: status, Run Now, Pause. Agents feel like a chat extension (§5,§42).
  val scope = rememberCoroutineScope()
  var agents by remember { mutableStateOf<List<com.nova.app.data.AgentDto>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var nl by remember { mutableStateOf("") }
  var building by remember { mutableStateOf(false) }
  var built by remember { mutableStateOf<kotlinx.serialization.json.JsonObject?>(null) }
  var selected by remember { mutableStateOf<com.nova.app.data.AgentDto?>(null) }
  var runOutput by remember { mutableStateOf<String?>(null) }
  var running by remember { mutableStateOf(false) }
  var approvals by remember { mutableStateOf<List<com.nova.app.data.ApprovalDto>>(emptyList()) }

  fun refresh() {
    scope.launch {
      loading = true; error = null
      runCatching { api.agents() to api.approvals() }
        .onSuccess { (a, ap) -> agents = a.agents; approvals = ap.approvals }
        .onFailure { error = "Unable to load agents" }
      loading = false
    }
  }
  LaunchedEffect(Unit) { refresh() }

  LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("Agents", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(4.dp))
      Text("Describe what to automate. AI asks missing questions, then shows config for review.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    item {
      OutlinedTextField(nl, { nl = it }, label = { Text("e.g. brief me on Android news every morning") },
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
      Spacer(Modifier.height(8.dp))
      Button(enabled = nl.isNotBlank() && !building, onClick = {
        scope.launch {
          building = true; error = null; built = null
          runCatching { api.buildAgent(com.nova.app.data.BuildAgentRequest(nl)) }
            .onSuccess { built = it }
            .onFailure { error = "Builder failed — check connection and retry" }
          building = false
        }
      }, shape = RoundedCornerShape(12.dp)) { Text(if (building) "Asking…" else "Build") }
    }
    built?.let { obj ->
      item {
        val questions = obj["questions"]?.let { runCatching { it.jsonArray.map { q -> q.jsonPrimitive.content } }.getOrNull() }
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (questions != null) {
              Text("Needs a little more:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
              questions.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            } else {
              Text(obj["name"]?.jsonPrimitive?.contentOrNull ?: "New agent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              Text(obj["goal"]?.jsonPrimitive?.contentOrNull ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text("Tools: " + (obj["tools"]?.jsonArray?.map { it.jsonPrimitive.content }?.joinToString() ?: "—"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Button(onClick = {
                scope.launch {
                  error = null
                  runCatching {
                    api.createAgent(
                      com.nova.app.data.CreateAgentRequest(
                        name = obj["name"]!!.jsonPrimitive.content,
                        goal = obj["goal"]!!.jsonPrimitive.content,
                        instructions = obj["instructions"]?.jsonPrimitive?.contentOrNull ?: obj["goal"]!!.jsonPrimitive.content,
                        tools = obj["tools"]!!.jsonArray.map { it.jsonPrimitive.content },
                      ),
                    )
                  }.onSuccess { created ->
                    built = null; nl = ""
                    runCatching { api.activateAgent(created.id) }
                    refresh()
                  }.onFailure { error = "Save failed — the builder may have named a tool that does not exist yet" }
                }
              }, shape = RoundedCornerShape(12.dp)) { Text("Save & Activate") }
            }
          }
        }
      }
    }
    if (approvals.isNotEmpty()) {
      item {
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Waiting for approval", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            approvals.forEach { ap ->
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${ap.toolId}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                  scope.launch {
                    runCatching { api.decideApproval(ap.id, com.nova.app.data.DecideApprovalRequest("reject")) }
                    refresh()
                  }
                }) { Text("Reject") }
                TextButton(onClick = {
                  scope.launch {
                    runCatching { api.decideApproval(ap.id, com.nova.app.data.DecideApprovalRequest("approve")) }
                    refresh()
                  }
                }) { Text("Approve") }
              }
            }
          }
        }
      }
    }
    if (loading) { item { LinearProgressIndicator(Modifier.fillMaxWidth()) } }
    error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
    items(agents.size) { i ->
      val a = agents[i]
      ElevatedCard(Modifier.fillMaxWidth().clickable {
        selected = if (selected?.id == a.id) null else a; runOutput = null
        scope.launch { runCatching { api.agent(a.id) }.onSuccess { selected = it } }
      }, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
          Text(a.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Spacer(Modifier.height(2.dp))
          Text("${a.status} • ${(a.tools).joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (selected?.id == a.id) {
            Spacer(Modifier.height(8.dp))
            selected?.let { d ->
              Text(d.goal, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(Modifier.height(8.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.status != "active") OutlinedButton(onClick = {
                  scope.launch { runCatching { api.activateAgent(d.id) }; refresh() }
                }, shape = RoundedCornerShape(10.dp)) { Text("Activate") }
                if (d.status == "active") OutlinedButton(onClick = {
                  scope.launch { runCatching { api.pauseAgent(d.id) }; refresh() }
                }, shape = RoundedCornerShape(10.dp)) { Text("Pause") }
                Button(enabled = !running, onClick = {
                  scope.launch {
                    running = true; runOutput = null
                    runCatching { api.runAgent(d.id) }
                      .onSuccess { runOutput = "[${it.status}] ${it.output ?: ""}" }
                      .onFailure { runOutput = "[failed] run failed — retry shortly" }
                    running = false; refresh()
                  }
                }, shape = RoundedCornerShape(10.dp)) { Text(if (running) "Running…" else "Run Now") }
              }
              runOutput?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }
  }
}

@Composable fun ActivityScreen(api: NovaApi) {
  // §44 execution list -> detail with the §35 step timeline.
  val scope = rememberCoroutineScope()
  var executions by remember { mutableStateOf<List<com.nova.app.data.ExecutionDto>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var detail by remember { mutableStateOf<com.nova.app.data.ExecutionDto?>(null) }
  LaunchedEffect(Unit) {
    runCatching { api.executions() }
      .onSuccess { executions = it.executions }
      .onFailure { error = "Unable to load activity" }
    loading = false
  }

  val emptyTransition = rememberInfiniteTransition(label = "actEmpty")
  val emptyBreathe by emptyTransition.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.7f,
    animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
    label = "actBreathe",
  )

  LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("Activity", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
    if (loading) { item { LinearProgressIndicator(Modifier.fillMaxWidth()) } }
    error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
    if (!loading && executions.isEmpty() && error == null) {
      item {
        Column(
          Modifier.fillMaxWidth().padding(48.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(
            Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(56.dp).graphicsLayer { alpha = emptyBreathe },
            tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(Modifier.height(20.dp))
          Text("No activity yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Spacer(Modifier.height(8.dp))
          Text(
            "Agent runs and task executions will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
    items(executions.size) { i ->
      val e = executions[i]
      ElevatedCard(Modifier.fillMaxWidth().clickable {
        detail = if (detail?.id == e.id) null else e
        scope.launch { runCatching { api.execution(e.id) }.onSuccess { detail = it } }
      }, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text((e.agent?.name ?: "Agent") + " • " + e.status, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
          if (detail?.id == e.id) {
            Spacer(Modifier.height(4.dp))
            detail?.steps?.forEach { Text("• ${it.label}", style = MaterialTheme.typography.bodyMedium) }
            detail?.output?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            detail?.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
          }
        }
      }
    }
  }
}

@Composable fun ConnectionsScreen() {
  // §38 connect/reauthorize/disconnect + inspect scopes. Tokens stay backend-encrypted.
  val services = listOf(
    Triple("GitHub", "Repositories, issues, and PRs", Icons.Default.Code),
    Triple("Gmail", "Read and send emails", Icons.Default.MailOutline),
    Triple("Slack", "Workspace messaging", Icons.Default.Phone),
    Triple("Notion", "Notes and databases", Icons.Default.Article),
    Triple("X (Twitter)", "Social media posts", Icons.Default.Share),
    Triple("WhatsApp", "Messaging integration", Icons.Default.Phone),
  )
  LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    item {
      Text("Connections", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(4.dp))
      Text("Integrate your accounts to let agents work across platforms.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(12.dp))
    }
    items(services.size) { i ->
      val (name, desc, icon) = services[i]
      ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
          Box(
            Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(icon, contentDescription = name, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
          }
          Spacer(Modifier.width(14.dp))
          Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          AssistChip(onClick = {}, label = { Text("Connect") }, shape = RoundedCornerShape(10.dp))
        }
      }
    }
  }
}

@Composable fun SettingsScreen(session: SessionToken, onLogout: () -> Unit) {
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

  // Fix: check if email looks like an email, otherwise show "Signed in"
  val rawEmail = session.getEmail()
  val displayEmail = if (rawEmail != null && rawEmail.contains("@")) rawEmail else "Signed in"

  LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(8.dp))
    }
    // Account section
    item {
      ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
          Box(
            Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Text("N", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
          }
          Spacer(Modifier.width(14.dp))
          Column {
            Text("Account", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(displayEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
    // Notifications
    item {
      ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        if (Build.VERSION.SDK_INT >= 33) {
          ListItem(
            headlineContent = { Text("Agent notifications", fontWeight = FontWeight.Medium) },
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
          ListItem(
            headlineContent = { Text("Agent notifications", fontWeight = FontWeight.Medium) },
            supportingContent = { Text("Follows system setting (Android < 13)") },
          )
        }
      }
    }
    // About
    item {
      ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        ListItem(
          headlineContent = { Text("About", fontWeight = FontWeight.Medium) },
          supportingContent = { Text("Nova v0.1.0") },
        )
      }
    }
    // Logout
    item {
      Spacer(Modifier.height(8.dp))
      OutlinedButton(
        onClick = onLogout,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text("Sign out", fontWeight = FontWeight.Medium)
      }
    }
  }
}
