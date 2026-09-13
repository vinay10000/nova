package com.nova.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import coil3.compose.AsyncImage
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

  val scheme = MaterialTheme.colorScheme
  val ScreenBg = scheme.surface
  val CardBg = scheme.surfaceVariant
  val Accent = scheme.primary
  val TextWhite = scheme.onSurface
  val TextGray = scheme.onSurfaceVariant

  var appeared by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { appeared = true }

  Column(
    Modifier
      .fillMaxSize()
      .background(ScreenBg)
      .padding(32.dp),
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
          color = Accent,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          if (registering) "Create your account" else "Welcome back",
          style = MaterialTheme.typography.bodyLarge,
          color = TextGray,
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
      colors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = CardBg,
        focusedContainerColor = CardBg,
        unfocusedTextColor = TextWhite,
        focusedTextColor = TextWhite,
        cursorColor = Accent,
        unfocusedBorderColor = scheme.outline,
        focusedBorderColor = Accent,
      ),
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
      colors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = CardBg,
        focusedContainerColor = CardBg,
        unfocusedTextColor = TextWhite,
        focusedTextColor = TextWhite,
        cursorColor = Accent,
        unfocusedBorderColor = scheme.outline,
        focusedBorderColor = Accent,
      ),
    )
    AnimatedVisibility(visible = error != null) {
      Text(
        error ?: "",
        color = scheme.error,
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
      colors = ButtonDefaults.buttonColors(containerColor = Accent),
    ) {
      if (loading) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = scheme.onPrimary)
      } else {
        Text(
          if (registering) "Create account" else "Sign in",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = scheme.onPrimary,
        )
      }
    }
    Spacer(Modifier.height(16.dp))
    TextButton(onClick = { registering = !registering; error = null }) {
      Text(if (registering) "Already have an account? Sign in" else "New to Nova? Create an account", color = TextGray)
    }
  }
}

// §8 entities (backend-owned; Room cache mirrors these).
@kotlinx.serialization.Serializable
data class Message(val id: String = "", val role: String, val content: String, val attachments: List<com.nova.app.data.AttachmentInfo> = emptyList())
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
  private val _streamingMsg = MutableStateFlow<Message?>(null)
  val streamingMsg: StateFlow<Message?> = _streamingMsg.asStateFlow()
  private val _streaming = MutableStateFlow(false)
  val streaming: StateFlow<Boolean> = _streaming.asStateFlow()
  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  var model: String? = null          // §7 model selection; null = backend default

  private var job: Job? = null
  private var lastUserText: String? = null
  private var uploadApi: NovaApi? = null // set via configureApi(); uploads go through the same authenticated client

  // §10 attachments pending on the next message. Ids are backend-issued; nothing is trusted client-side.
  data class PendingAttachment(val id: String, val filename: String, val size: Long, val mime: String = "")
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
        _pending.value = _pending.value + PendingAttachment(dto.id, dto.filename, dto.size, dto.mime)
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
    val attachmentInfos = _pending.value.map { com.nova.app.data.AttachmentInfo(it.id, it.filename, it.mime, it.size) }
    _pending.value = emptyList()

    _messages.value += Message(role = "user", content = text, attachments = attachmentInfos)
    _streamingMsg.value = Message(role = "assistant", content = "")
    _error.value = null
    _streaming.value = true

    job = viewModelScope.launch {
      client.stream(cid, text, model, attachmentIds)
        .catch { _error.value = it.message ?: "stream_failed" }
        .collect { chunk ->
          when (chunk.type) {
            "token" -> {
              val cur = _streamingMsg.value ?: return@collect
              _streamingMsg.value = cur.copy(content = cur.content + chunk.text)
            }
            "error" -> {
              _error.value = chunk.code ?: "stream_error"
              _streaming.value = false
              _streamingMsg.value = null
            }
            "done" -> {
              val done = _streamingMsg.value
              if (done != null && done.content.isNotEmpty()) {
                _messages.value = _messages.value + done
              }
              _streamingMsg.value = null
              _streaming.value = false
            }
          }
        }
      _streamingMsg.value = null
      _streaming.value = false
    }
  }


  /** §6 stop generation — cancels the SSE call, keeping partial output. */
  fun stop() {
    job?.cancel()
    job = null
    val partial = _streamingMsg.value
    if (partial != null && partial.content.isNotEmpty()) {
      _messages.value = _messages.value + partial
    }
    _streamingMsg.value = null
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
          showHeader = true,
        )
      },
    ),
    colors = markdownColor(
      text = MaterialTheme.colorScheme.onSurface,
      codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
    typography = markdownTypography(),
    modifier = Modifier.padding(vertical = 2.dp),
  )
}

@Composable
private fun BareAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  description: String,
  onClick: () -> Unit,
) {
  IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
    Icon(icon, contentDescription = description, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
  val infiniteTransition = rememberInfiniteTransition(label = "typing")
  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    repeat(3) { index ->
      val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
          animation = tween(520, delayMillis = index * 170),
          repeatMode = RepeatMode.Reverse,
        ),
        label = "dot$index",
      )
      Box(
        Modifier
          .size(6.dp)
          .graphicsLayer { this.alpha = alpha }
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary)
      )
    }
  }
}

private val ChatSuggestions = listOf(
  "Draft a morning brief from my inbox" to "Inbox triage, summarized before coffee.",
  "Explain this code, line by line" to "Paste code below, get a plain-language walkthrough.",
  "Plan a focused week" to "Turn a scattered list into three clear days.",
)

@Composable
fun ChatScreen(api: NovaApi, session: SessionToken, onSettingsClick: () -> Unit = {}, vm: ChatViewModel = viewModel()) {
  LaunchedEffect(session) { vm.configureSession(session) }
  LaunchedEffect(api) { vm.configureApi(api) }
  var conversations by remember { mutableStateOf<List<ConversationDto>>(emptyList()) }
  var models by remember { mutableStateOf<List<ModelDto>>(emptyList()) }
  var model by remember { mutableStateOf<String?>(null) }
  var editIndex by remember { mutableStateOf<Int?>(null) }
  var offline by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val scheme = MaterialTheme.colorScheme

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
        vm.openConversation(detail.id, detail.messages.map { Message(it.id, it.role, it.content, it.attachments) })
      } else {
        vm.setConversation(api.createConversation().id)
        refreshConversations()
      }
    }.onFailure { offline = true }
  }
  val messages by vm.messages.collectAsState()
  val streamingMsg by vm.streamingMsg.collectAsState()
  val streaming by vm.streaming.collectAsState()
  val error by vm.error.collectAsState()
  val pending by vm.pending.collectAsState()
  val uploading by vm.uploading.collectAsState()
  var input by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(DrawerValue.Closed)

  val voice = remember { AndroidVoiceInput(context) { input = it } }
  var micGranted by remember {
    mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED)
  }
  val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    micGranted = granted
    if (granted) voice.start()
  }
  var cameraGranted by remember {
    mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
  }
  DisposableEffect(Unit) { onDispose { voice.destroy() } }
  val tts = remember { VoiceOutput(context) }
  DisposableEffect(Unit) { onDispose { tts.shutdown() } }
  val remoteTts = remember { RemoteVoiceOutput({ session.get() }) }
  fun speakOut(text: String) {
    scope.launch { if (!remoteTts.speak(context, text)) tts.speak(text) }
  }

  val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let { vm.addAttachment(context, it) }
  }
  val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let { vm.addAttachment(context, it) }
  }
  var cameraUri by remember { mutableStateOf<Uri?>(null) }
  val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
    if (success) cameraUri?.let { vm.addAttachment(context, it) }
  }
  fun launchCamera() {
    val file = java.io.File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
    cameraUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    cameraUri?.let { takePicture.launch(it) }
  }
  val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    cameraGranted = granted
    if (granted) launchCamera()
  }
  var showAttachMenu by remember { mutableStateOf(false) }

  LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, streamingMsg?.content?.length) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
  }

  fun shortModel(id: String): String = id.removePrefix("gemini-").removePrefix("models/").take(18)

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet(
        drawerContainerColor = scheme.surface,
        drawerContentColor = scheme.onSurface,
        modifier = Modifier.width(300.dp),
      ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Library", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { drawerState.close() }; onSettingsClick() }, modifier = Modifier.size(32.dp)) {
              Icon(Icons.Default.Settings, contentDescription = "Settings", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
          }
          Spacer(Modifier.height(2.dp))
          Text(
            "${conversations.size} conversations",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = NovaMono,
            color = scheme.onSurfaceVariant,
          )
        }
        HorizontalDivider(color = scheme.outlineVariant)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
          items(conversations.size) { idx ->
            val c = conversations[idx]
            Row(
              Modifier
                .fillMaxWidth()
                .clickable {
                  scope.launch {
                    runCatching {
                      val detail = api.conversation(c.id)
                      vm.openConversation(c.id, detail.messages.map { Message(it.id, it.role, it.content, it.attachments) })
                    }
                    drawerState.close()
                  }
                }
                .padding(horizontal = 20.dp, vertical = 11.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                "${(idx + 1).toString().padStart(2, '0')}",
                fontFamily = NovaMono,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
                modifier = Modifier.width(28.dp),
              )
              Text(c.title, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
            }
          }
        }
        HorizontalDivider(color = scheme.outlineVariant)
        Row(
          Modifier
            .fillMaxWidth()
            .clickable {
              scope.launch {
                runCatching {
                  val created = api.createConversation()
                  vm.newChat(created.id)
                  refreshConversations()
                }
                drawerState.close()
              }
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Default.Add, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(10.dp))
          Text("Start a fresh chat", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        }
      }
    },
  ) {
  Column(
    Modifier
      .fillMaxSize()
      .background(scheme.surface)
  ) {
    // Masthead: wordmark left, model + new-chat right. Single composed row, no stacked hero.
    Surface(color = scheme.surface) {
      Column {
        Row(
          Modifier.fillMaxWidth().padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = { scope.launch { drawerState.open() } }) {
            Icon(Icons.Default.Menu, contentDescription = "Library", tint = scheme.onSurface)
          }
          Column(Modifier.weight(1f)) {
            Text("Nova", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
            Text(
              if (conversations.isNotEmpty()) "${conversations.size} threads" else "A quiet place to think",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = NovaMono,
              color = scheme.onSurfaceVariant,
            )
          }
          if (models.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            TextButton(onClick = { expanded = true }) {
              Text(
                model?.let { shortModel(it) } ?: "pick model",
                fontFamily = NovaMono,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
              )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
              models.forEach { m ->
                DropdownMenuItem(text = { Text(m.id, fontFamily = NovaMono, style = MaterialTheme.typography.bodySmall) }, onClick = {
                  model = m.id; vm.model = m.id; expanded = false
                })
              }
            }
          }
          if (messages.isNotEmpty()) {
            IconButton(onClick = {
              scope.launch {
                runCatching {
                  val created = api.createConversation()
                  vm.newChat(created.id)
                  refreshConversations()
                }
              }
            }) {
              Icon(Icons.Default.Edit, contentDescription = "New chat", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
          }
        }
        HorizontalDivider(color = scheme.outlineVariant)
      }
    }

    if (offline) {
      Surface(color = scheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
          "Offline. Showing what is cached. Reconnect to keep going.",
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
          style = MaterialTheme.typography.bodySmall,
          color = scheme.onSurface,
        )
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f).background(scheme.surface),
      state = listState,
      contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
    ) {
      if (messages.isEmpty()) {
        item {
          // Signature opener: left-aligned serif statement + numbered starters. No centered icon stack.
          Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(
              "35",
              fontFamily = NovaMono,
              style = MaterialTheme.typography.labelMedium,
              color = scheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
              "What needs\ndoing today?",
              fontFamily = NovaDisplay,
              style = MaterialTheme.typography.displaySmall,
              color = scheme.onSurface,
              lineHeight = MaterialTheme.typography.displaySmall.lineHeight,
            )
            Spacer(Modifier.height(10.dp))
            Text(
              "Nova reads, writes, searches and runs errands across your tools. Pick a starter or just write below.",
              style = MaterialTheme.typography.bodyMedium,
              color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            ChatSuggestions.forEachIndexed { i, (title, sub) ->
              Surface(
                shape = RoundedCornerShape(18.dp),
                color = scheme.surfaceVariant,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().clickable { input = title },
              ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    "0${i + 1}",
                    fontFamily = NovaMono,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                    modifier = Modifier.width(30.dp),
                  )
                  Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                  }
                  Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
              }
              Spacer(Modifier.height(10.dp))
            }
          }
        }
      }

      items(messages.size) { i ->
        val m = messages[i]
        if (editIndex == i) {
          var editText by remember(m.id) { mutableStateOf(m.content) }
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
          ) {
            OutlinedTextField(
              editText, { editText = it },
              modifier = Modifier.weight(1f),
              maxLines = 6,
              shape = RoundedCornerShape(16.dp),
              colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = scheme.surfaceVariant,
                focusedContainerColor = scheme.surfaceVariant,
                unfocusedTextColor = scheme.onSurface,
                focusedTextColor = scheme.onSurface,
                cursorColor = scheme.primary,
                unfocusedBorderColor = scheme.outlineVariant,
                focusedBorderColor = scheme.primary,
              ),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { editIndex = null; vm.editAndResend(i, editText) }, shape = RoundedCornerShape(14.dp)) { Text("Send") }
            TextButton(onClick = { editIndex = null }) { Text("Cancel", color = scheme.onSurfaceVariant) }
          }
        } else {
          val isUser = m.role == "user"

          if (isUser) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(start = 64.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
              horizontalAlignment = Alignment.End,
            ) {
              if (m.attachments.isNotEmpty()) {
                Column(
                  modifier = Modifier.padding(bottom = 4.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                  horizontalAlignment = Alignment.End,
                ) {
                  m.attachments.forEach { att ->
                    if (att.mime.startsWith("image/")) {
                      AsyncImage(
                        model = "${com.nova.app.BuildConfig.API_BASE_URL}/v1/files/${att.id}/raw",
                        contentDescription = att.filename,
                        modifier = Modifier
                          .size(80.dp)
                          .clip(RoundedCornerShape(8.dp)),
                      )
                    } else {
                      Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = scheme.surfaceVariant,
                      ) {
                        Row(
                          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                          verticalAlignment = Alignment.CenterVertically,
                        ) {
                          Icon(
                            Icons.Default.Attachment,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = scheme.onSurfaceVariant,
                          )
                          Spacer(Modifier.width(6.dp))
                          Text(
                            att.filename,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = NovaMono,
                            color = scheme.onSurface,
                            modifier = Modifier.widthIn(max = 140.dp),
                          )
                        }
                      }
                    }
                  }
                }
              }
              Surface(
                shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
                color = scheme.primary,
                contentColor = scheme.onPrimary,
              ) {
                Text(
                  m.content,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
              }
              Spacer(Modifier.height(4.dp))
              Text(
                "you \u00b7 tap to edit",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = NovaMono,
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.clickable { editIndex = i },
              )
            }
          } else {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nova", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                Spacer(Modifier.width(10.dp))
                Text(
                  java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = NovaMono,
                  color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
              }
              Spacer(Modifier.height(6.dp))
              MarkdownBody(m.content, false)
              Spacer(Modifier.height(2.dp))
              Row(Modifier.offset(x = (-8).dp)) {
                BareAction(Icons.Default.ContentCopy, "Copy") { clipboard.setText(AnnotatedString(m.content)) }
                BareAction(Icons.AutoMirrored.Filled.VolumeUp, "Read aloud") { speakOut(m.content) }
                BareAction(Icons.Default.Share, "Share") {
                  val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, m.content)
                  }
                  context.startActivity(Intent.createChooser(send, "Share response"))
                }
                BareAction(Icons.Default.Refresh, "Regenerate") { vm.regenerate() }
              }
            }
          }
        }
      }

      if (streamingMsg != null) {
        item(key = "streaming") {
          val sm = streamingMsg!!
          Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("Nova", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
              Spacer(Modifier.width(10.dp))
              Text(
                java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = NovaMono,
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
              )
            }
            Spacer(Modifier.height(6.dp))
            if (sm.content.isEmpty()) {
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Nova is writing", fontFamily = NovaDisplay, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                TypingIndicator()
              }
            } else {
              MarkdownBody(sm.content, true)
            }
          }
        }
      }

      error?.let { code ->
        item {
          Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            color = scheme.errorContainer,
          ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
              Text("That run failed ($code). ", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
              TextButton(onClick = { vm.clearError(); vm.retry() }) { Text("Retry", color = scheme.primary) }
            }
          }
        }
      }
    }

    if (pending.isNotEmpty() || uploading) {
      Row(
        Modifier.fillMaxWidth().background(scheme.surface).padding(horizontal = 20.dp, vertical = 6.dp)
          .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (uploading) {
          CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.primary)
          Spacer(Modifier.width(8.dp))
        }
        pending.forEach { p ->
          Row(
            Modifier.padding(end = 8.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceVariant)
              .clickable { vm.removeAttachment(p.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(p.filename, maxLines = 1, fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = scheme.onSurface, modifier = Modifier.widthIn(max = 160.dp))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ContentCopy, contentDescription = "Remove", modifier = Modifier.size(12.dp), tint = scheme.onSurfaceVariant)
          }
        }
      }
    }

    // Composer: one rounded field, bare leading icons, single circular ink send.
    Surface(color = scheme.surface, modifier = Modifier.imePadding()) {
      Column {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.Bottom,
        ) {
          Surface(
            shape = RoundedCornerShape(26.dp),
            color = scheme.surfaceVariant,
            modifier = Modifier.weight(1f),
          ) {
            Row(Modifier.padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
              Box {
                IconButton(onClick = { showAttachMenu = true }, enabled = !uploading && !streaming, modifier = Modifier.size(40.dp)) {
                  Icon(Icons.Default.Add, contentDescription = "Attach", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                  DropdownMenuItem(
                    text = { Text("Camera") },
                    leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = {
                      showAttachMenu = false
                      if (cameraGranted) {
                        launchCamera()
                      } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                      }
                    },
                  )
                  DropdownMenuItem(
                    text = { Text("Photos") },
                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { showAttachMenu = false; pickImage.launch("image/*") },
                  )
                  DropdownMenuItem(
                    text = { Text("Files") },
                    leadingIcon = { Icon(Icons.Default.Attachment, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { showAttachMenu = false; pickFile.launch("*/*") },
                  )
                  DropdownMenuItem(
                    text = { Text("Plugins") },
                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { showAttachMenu = false },
                  )
                }
              }
              if (micGranted && voice.available) {
                IconButton(onClick = { voice.start() }, modifier = Modifier.size(40.dp)) {
                  Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
              } else {
                IconButton(onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.size(40.dp)) {
                  Icon(Icons.Default.Mic, contentDescription = "Enable voice input", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
              }
              OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Nova…", color = scheme.onSurfaceVariant) },
                maxLines = 5,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  unfocusedContainerColor = Color.Transparent,
                  focusedContainerColor = Color.Transparent,
                  unfocusedTextColor = scheme.onSurface,
                  focusedTextColor = scheme.onSurface,
                  cursorColor = scheme.primary,
                  unfocusedBorderColor = Color.Transparent,
                  focusedBorderColor = Color.Transparent,
                ),
              )
              if (streaming) {
                FilledIconButton(
                  onClick = { vm.stop() },
                  colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.error, contentColor = scheme.surface),
                  modifier = Modifier.size(44.dp),
                ) {
                  Icon(Icons.Default.Refresh, contentDescription = "Stop", modifier = Modifier.size(18.dp))
                }
              } else {
                FilledIconButton(
                  onClick = { vm.send(input); input = "" },
                  enabled = input.isNotBlank(),
                  colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary, disabledContainerColor = scheme.outlineVariant),
                  modifier = Modifier.size(44.dp),
                ) {
                  Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                }
              }
            }
          }
        }
      }
    }
  }
  }
}

@Composable fun AgentsScreen(api: NovaApi) {
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
  val scheme = MaterialTheme.colorScheme

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
  val live = agents.count { it.status == "active" }

  LazyColumn(
    Modifier.fillMaxSize().background(scheme.surface).padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Text("$live live", fontFamily = NovaMono, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
      Spacer(Modifier.height(6.dp))
      Text("Agents at work.", fontFamily = NovaDisplay, style = MaterialTheme.typography.headlineLarge, color = scheme.onSurface)
      Spacer(Modifier.height(8.dp))
      Text(
        "Say what should happen on its own. Nova asks what is missing, then hands you the plan to keep.",
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
      )
    }
    // Builder island: the one composed object on this screen.
    item {
      Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceVariant, tonalElevation = 2.dp) {
        Column(Modifier.padding(20.dp)) {
          Text("Describe the job", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
          Spacer(Modifier.height(4.dp))
          Text("One sentence is enough. Amend after Nova drafts it.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
            nl, { nl = it },
            placeholder = { Text("Brief me on Android news every morning") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2, maxLines = 4,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
              unfocusedContainerColor = scheme.surface, focusedContainerColor = scheme.surface,
              unfocusedTextColor = scheme.onSurface, focusedTextColor = scheme.onSurface,
              cursorColor = scheme.primary, unfocusedBorderColor = scheme.outlineVariant, focusedBorderColor = scheme.primary,
            ),
          )
          Spacer(Modifier.height(12.dp))
          Button(
            enabled = nl.isNotBlank() && !building,
            onClick = {
              scope.launch {
                building = true; error = null; built = null
                runCatching { api.buildAgent(com.nova.app.data.BuildAgentRequest(nl)) }
                  .onSuccess { built = it }
                  .onFailure { error = "Builder failed. Check connection and retry." }
                building = false
              }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
          ) { Text(if (building) "Listening…" else "Draft the agent") }
        }
      }
    }
    built?.let { obj ->
      item {
        val questions = obj["questions"]?.let { runCatching { it.jsonArray.map { q -> q.jsonPrimitive.content } }.getOrNull() }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerHigh) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (questions != null) {
              Text("Two things first", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
              questions.forEachIndexed { i, q ->
                Text("${i + 1}.  $q", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
              }
              Text("Answer in the box above and draft again.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            } else {
              Text(obj["name"]?.jsonPrimitive?.contentOrNull ?: "New agent", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
              Text(obj["goal"]?.jsonPrimitive?.contentOrNull ?: "", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
              Text(
                (obj["tools"]?.jsonArray?.map { it.jsonPrimitive.content }?.joinToString("  ·  ") ?: "none"),
                fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = scheme.primary,
              )
              Spacer(Modifier.height(4.dp))
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
                  }.onFailure { error = "Save failed. The draft named a tool that does not exist yet." }
                }
              }, shape = RoundedCornerShape(14.dp)) { Text("Keep this setup") }
            }
          }
        }
      }
    }
    if (approvals.isNotEmpty()) {
      item {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = scheme.errorContainer) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Needs your call", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text("${approvals.size} tool use waiting", fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            approvals.forEach { ap ->
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(ap.toolId, Modifier.weight(1f), fontFamily = NovaMono, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
                TextButton(onClick = {
                  scope.launch {
                    runCatching { api.decideApproval(ap.id, com.nova.app.data.DecideApprovalRequest("reject")) }
                    refresh()
                  }
                }) { Text("Dismiss", color = scheme.onSurfaceVariant) }
                TextButton(onClick = {
                  scope.launch {
                    runCatching { api.decideApproval(ap.id, com.nova.app.data.DecideApprovalRequest("approve")) }
                    refresh()
                  }
                }) { Text("Allow", color = scheme.primary, fontWeight = FontWeight.Bold) }
              }
            }
          }
        }
      }
    }
    if (loading) { item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.outlineVariant) } }
    error?.let { item { Text(it, color = scheme.error, style = MaterialTheme.typography.bodyMedium) } }
    if (!loading && agents.isEmpty() && error == null) {
      item {
        Column(Modifier.padding(vertical = 12.dp)) {
          HorizontalDivider(color = scheme.outlineVariant)
          Spacer(Modifier.height(14.dp))
          Text("No agents yet.", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
          Spacer(Modifier.height(4.dp))
          Text("The first one takes about a minute: describe it above.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
      }
    }
    items(agents.size) { i ->
      val a = agents[i]
      val open = selected?.id == a.id
      Surface(
        Modifier.fillMaxWidth().clickable {
          selected = if (open) null else a; runOutput = null
          scope.launch { runCatching { api.agent(a.id) }.onSuccess { selected = it } }
        },
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceVariant,
        tonalElevation = 1.dp,
      ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.name, Modifier.weight(1f), fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(
              when (a.status) { "active" -> "Live"; "paused" -> "Paused"; else -> "Draft" },
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = if (a.status == "active") scheme.secondary else scheme.onSurfaceVariant,
            )
          }
          Spacer(Modifier.height(4.dp))
          Text(a.tools.joinToString("  ·  "), fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 2)
          if (open) {
            Spacer(Modifier.height(10.dp))
            selected?.let { d ->
              Text(d.goal, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
              Spacer(Modifier.height(14.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !running, onClick = {
                  scope.launch {
                    running = true; runOutput = null
                    runCatching { api.runAgent(d.id) }
                      .onSuccess { runOutput = "[${it.status}] ${it.output ?: ""}" }
                      .onFailure { runOutput = "[failed] Run failed. Retry shortly." }
                    running = false; refresh()
                  }
                }, shape = RoundedCornerShape(14.dp)) { Text(if (running) "Running…" else "Run now") }
                Spacer(Modifier.width(4.dp))
                if (d.status != "active") TextButton(onClick = {
                  scope.launch { runCatching { api.activateAgent(d.id) }; refresh() }
                }) { Text("Activate", color = scheme.primary) }
                if (d.status == "active") TextButton(onClick = {
                  scope.launch { runCatching { api.pauseAgent(d.id) }; refresh() }
                }) { Text("Pause", color = scheme.onSurfaceVariant) }
              }
              runOutput?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }
  }
}

@Composable fun ActivityScreen(api: NovaApi) {
  val scope = rememberCoroutineScope()
  var executions by remember { mutableStateOf<List<com.nova.app.data.ExecutionDto>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var detail by remember { mutableStateOf<com.nova.app.data.ExecutionDto?>(null) }
  var filter by remember { mutableStateOf("All") }
  LaunchedEffect(Unit) {
    runCatching { api.executions() }
      .onSuccess { executions = it.executions }
      .onFailure { error = "Unable to load activity" }
    loading = false
  }
  val scheme = MaterialTheme.colorScheme
  fun statusColor(s: String) = when (s.lowercase()) {
    "completed", "succeeded", "success" -> scheme.secondary
    "failed", "error" -> scheme.error
    "running" -> scheme.primary
    else -> scheme.onSurfaceVariant
  }
  val shown = if (filter == "All") executions else executions.filter { it.status.equals(filter, true) }

  LazyColumn(
    Modifier.fillMaxSize().background(scheme.surface).padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Text("${executions.size} runs", fontFamily = NovaMono, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
      Spacer(Modifier.height(6.dp))
      Text("What happened.", fontFamily = NovaDisplay, style = MaterialTheme.typography.headlineLarge, color = scheme.onSurface)
      Spacer(Modifier.height(8.dp))
      Text("Every agent run, newest first. Open one for its steps.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("All", "Completed", "Failed", "Running").forEach { f ->
          val on = filter == f
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (on) scheme.primary else scheme.surfaceVariant,
            modifier = Modifier.clickable { filter = f },
          ) {
            Text(f, style = MaterialTheme.typography.labelMedium, color = if (on) scheme.onPrimary else scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
          }
        }
      }
    }
    if (loading) { item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.outlineVariant) } }
    error?.let { item { Text(it, color = scheme.error, style = MaterialTheme.typography.bodyMedium) } }
    if (!loading && shown.isEmpty() && error == null) {
      item {
        Column(Modifier.padding(vertical = 12.dp)) {
          HorizontalDivider(color = scheme.outlineVariant)
          Spacer(Modifier.height(14.dp))
          Text("Quiet so far.", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
          Spacer(Modifier.height(4.dp))
          Text("Run an agent and its trace will land here.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
      }
    }
    items(shown.size) { i ->
      val e = shown[i]
      val open = detail?.id == e.id
      Surface(
        Modifier.fillMaxWidth().clickable {
          detail = if (open) null else e
          scope.launch { runCatching { api.execution(e.id) }.onSuccess { detail = it } }
        },
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceVariant,
        tonalElevation = 1.dp,
      ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.agent?.name ?: "Agent", Modifier.weight(1f), fontFamily = NovaDisplay, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Text(e.status, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = statusColor(e.status))
          }
          Spacer(Modifier.height(2.dp))
          Text(
            ("${e.trigger}".ifBlank { "manual" } + "  ·  run ${e.id.take(6)}"),
            fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
          )
          if (open) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = scheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            detail?.steps?.forEachIndexed { si, s ->
              Text("${si + 1}.  ${s.label}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
            }
            detail?.output?.let {
              Spacer(Modifier.height(8.dp))
              Surface(shape = RoundedCornerShape(12.dp), color = scheme.surface) {
                Text(it, fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = scheme.onSurface, modifier = Modifier.padding(12.dp))
              }
            }
            detail?.error?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall) }
          }
        }
      }
    }
  }
}

@Composable fun ConnectionsScreen(api: NovaApi, session: SessionToken) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val scheme = MaterialTheme.colorScheme
  var connections by remember { mutableStateOf<List<com.nova.app.data.ConnectionDto>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var connecting by remember { mutableStateOf<String?>(null) }

  fun refresh() {
    scope.launch {
      loading = true; error = null
      runCatching { api.connections() }
        .onSuccess { connections = it.connections }
        .onFailure { error = "Unable to load connections" }
      loading = false
    }
  }

  // §38: Refresh on first load AND when the user returns from the OAuth browser flow.
  // OnResume fires every time the composable becomes visible — including after the browser redirect.
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
      if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // §17 providers — read-only descriptions. The real state comes from the backend.
  data class ProviderInfo(val id: String, val name: String, val desc: String, val glyph: String)
  val providers = listOf(
    ProviderInfo("github", "GitHub", "Issues and pull requests your agents can read.", "G"),
    ProviderInfo("gmail", "Gmail", "Search and draft mail, never send without asking.", "M"),
    ProviderInfo("slack", "Slack", "Summaries from the channels you pick.", "S"),
    ProviderInfo("notion", "Notion", "Pages and notes, kept in sync.", "N"),
    ProviderInfo("x", "X", "Listen for mentions that matter.", "X"),
  )

  val connectedProviders = connections.filter { it.status == "connected" }.map { it.provider }.toSet()
  val live = connectedProviders.size

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(scheme.surface).padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Text("$live of ${providers.size} live", fontFamily = NovaMono, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
      Spacer(Modifier.height(6.dp))
      Text("Tied together.", fontFamily = NovaDisplay, style = MaterialTheme.typography.headlineLarge, color = scheme.onSurface)
      Spacer(Modifier.height(8.dp))
      Text("Agents borrow these accounts. Nothing runs without your say.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
      Spacer(Modifier.height(6.dp))
    }
    if (loading) {
      item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.outlineVariant) }
    }
    error?.let { e ->
      item { Text(e, color = scheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
    items(providers.size) { i ->
      val p = providers[i]
      val connected = p.id in connectedProviders
      val conn = connections.firstOrNull { it.provider == p.id }
      val isConnecting = connecting == p.id
      Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceVariant, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(p.glyph, fontFamily = NovaDisplay, style = MaterialTheme.typography.headlineMedium, color = if (connected) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.width(34.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(p.name, fontFamily = NovaDisplay, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(p.desc, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            if (connected && conn?.providerLogin != null) {
              Text(
                "Connected as ${conn.providerLogin}",
                fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall,
                color = scheme.secondary,
              )
            } else {
              Text(
                if (connected) "Connected" else "Not connected",
                fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall,
                color = if (connected) scheme.secondary else scheme.onSurfaceVariant,
              )
            }
          }
          Spacer(Modifier.width(8.dp))
          if (connected) {
            // §38: disconnect removes the connection row server-side
            TextButton(onClick = {
              scope.launch {
                when (p.id) {
                  "github" -> runCatching { api.disconnectGitHub() }.onSuccess { refresh() }
                }
              }
            }) {
              Text("Disconnect", color = scheme.error, style = MaterialTheme.typography.labelMedium)
            }
          } else if (p.id == "github") {
            // §38: GitHub OAuth — redirect user to GitHub to authorize
            TextButton(
              enabled = !isConnecting,
              onClick = {
                scope.launch {
                  connecting = p.id
                  runCatching { api.githubAuthorize() }
                    .onSuccess { auth ->
                      // Open GitHub OAuth in the browser — user authorizes there.
                      // Backend callback redirects to nova://connections/github/connected.
                      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(auth.url))
                      context.startActivity(intent)
                    }
                    .onFailure { error = "Could not start GitHub connection. Check backend config." }
                  connecting = null
                }
              },
            ) {
              if (isConnecting) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = scheme.primary)
              } else {
                Text("Connect", color = scheme.primary, fontWeight = FontWeight.Bold)
              }
            }
          } else {
            // Other providers — not yet wired (Phase 4+)
            TextButton(enabled = false, onClick = {}) {
              Text("Soon", color = scheme.onSurfaceVariant.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
    }
    item {
      Text("Keys stay on the server. Revoke anytime from the source.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    }
  }
}

@Composable fun SettingsScreen(session: SessionToken, onLogout: () -> Unit, onBack: () -> Unit = {}, onAccentChanged: () -> Unit = {}) {
  val context = LocalContext.current
  var granted by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= 33)
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
      else true,
    )
  }
  val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

  val rawEmail = session.getEmail()
  val displayEmail = if (rawEmail != null && rawEmail.contains("@")) rawEmail else "Signed in"
  val initial = displayEmail.take(1).uppercase()
  val scheme = MaterialTheme.colorScheme

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(scheme.surface).padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Back", tint = scheme.onSurface, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text("Settings", fontFamily = NovaDisplay, style = MaterialTheme.typography.headlineLarge, color = scheme.onSurface)
      }
      Spacer(Modifier.height(4.dp))
      Text("Yours to tune. Nova stays out of the way.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }

    item {
      Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceVariant, tonalElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
          // Bare serif initial. No gradient tile, no filled avatar box.
          Text(initial, fontFamily = NovaDisplay, style = MaterialTheme.typography.displaySmall, color = scheme.primary, modifier = Modifier.width(44.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text("Account", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(displayEmail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
          }
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
      }
    }

    item {
      Text("How Nova reaches you", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    }

    item {
      Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceVariant, tonalElevation = 1.dp) {
        Column {
          Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Agent notifications", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = scheme.onSurface)
              Spacer(Modifier.height(2.dp))
              Text(
                if (granted) "On. Runs report back." else "Off. Runs stay silent.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
              )
            }
            Spacer(Modifier.width(12.dp))
            if (Build.VERSION.SDK_INT >= 33) {
              Switch(
                checked = granted,
                onCheckedChange = { on ->
                  if (on) request.launch(Manifest.permission.POST_NOTIFICATIONS)
                  else context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                  })
                },
              )
            } else {
              Text("System", fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
          }
          HorizontalDivider(color = scheme.outlineVariant)
          Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Read responses aloud", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = scheme.onSurface)
              Spacer(Modifier.height(2.dp))
              Text("Nova voice, on tap of any answer", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
          }
        }
      }
    }

    item {
      Text("Accent", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    }

    item {
      val currentAccentName = AccentPreferences.get(context)
      val currentAccent = NovaAccent.entries.find { it.name.equals(currentAccentName, ignoreCase = true) } ?: NovaAccent.BRONZE
      Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceVariant, tonalElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
          Text("Choose a tone", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = scheme.onSurface)
          Spacer(Modifier.height(14.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
          ) {
            NovaAccent.entries.forEach { accent ->
              val selected = accent == currentAccent
              val previewDark = isSystemInDarkTheme()
              val previewColor = remember(accent, previewDark) {
                val pairs = mapOf(
                  NovaAccent.BRONZE to (Color(0xFFE2B26B) to Color(0xFF7A5320)),
                  NovaAccent.SLATE to (Color(0xFF8EACBD) to Color(0xFF3A6070)),
                  NovaAccent.SAGE to (Color(0xFF9DB89A) to Color(0xFF3E6B3A)),
                  NovaAccent.TERRACOTTA to (Color(0xFFE08E7E) to Color(0xFF9C3B2A)),
                  NovaAccent.PLUM to (Color(0xFFC4A0D0) to Color(0xFF6B4080)),
                )
                if (previewDark) pairs[accent]!!.first else pairs[accent]!!.second
              }
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(previewColor)
                    .clickable {
                      AccentPreferences.set(context, accent.name)
                      onAccentChanged()
                    },
                  contentAlignment = Alignment.Center,
                ) {
                  if (selected) {
                    Surface(
                      shape = CircleShape,
                      color = Color.White.copy(alpha = 0.3f),
                      modifier = Modifier.size(20.dp),
                    ) {}
                  }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                  accent.label,
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = NovaMono,
                  color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }

    item {
      Text("About this copy", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    }

    item {
      Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceVariant, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nova", Modifier.weight(1f), fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text("v0.1.0", fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
          }
          Spacer(Modifier.height(6.dp))
          Text(
            "A personal assistant with voice, agents and ties into the tools you already use.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
          )
        }
      }
    }

    item {
      TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
        Text("Sign out", color = scheme.error, fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

