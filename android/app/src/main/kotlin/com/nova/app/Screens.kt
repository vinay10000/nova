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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.nova.app.data.ChatStreamClient
import com.nova.app.data.ChatStreamRequest
import com.nova.app.data.ConversationDto
import com.nova.app.data.GenerationSelection
import com.nova.app.data.LoginRequest
import com.nova.app.data.ModelDto
import com.nova.app.data.NovaApi
import com.nova.app.data.SessionToken
import com.nova.app.data.UiBlockDto
import com.nova.app.voice.AndroidVoiceInput
import com.nova.app.voice.RemoteVoiceOutput
import com.nova.app.voice.VoiceOutput
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
  val focus = LocalFocusManager.current

  // Maps a backend failure to a sentence. A 401 means the credentials are
  // wrong; no exception code means the request never left the phone.
  fun friendlyError(t: Throwable): String {
    val code = (t as? retrofit2.HttpException)?.code()
    return when {
      code == 409 -> "That email is already registered. Sign in instead."
      code == 401 -> if (registering) "That email is already registered." else "Wrong email or password."
      code == 400 -> "Check the email format and use at least 8 characters."
      code == null -> "No connection to Nova. Check your network and retry."
      else -> "Sign-in is busy right now. Try again in a moment."
    }
  }

  fun submit() {
    if (loading || email.isBlank() || password.length < 8) return
    focus.clearFocus()
    scope.launch {
      error = null
      loading = true
      runCatching {
        if (registering) api.register(LoginRequest(email.trim(), password)) else api.login(LoginRequest(email.trim(), password))
      }.onSuccess { onAuthenticated(it.token) }
        .onFailure { error = friendlyError(it) }
      loading = false
    }
  }

  // Edge-to-edge means the form must dodge the status bar, and a scrolling
  // column means the keyboard cannot push the sign-in button off screen.
  // No opaque background — the ambient field shows through; the form is glass.
  Column(
    Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .imePadding()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = NovaSpace.xxl, vertical = NovaSpace.xl),
  ) {
    Spacer(Modifier.height(NovaSpace.xxl))
    // Visible by default — never gate the headline behind an entrance reveal.
    Column {
      Text(
        "Nova",
        fontFamily = NovaDisplay,
        style = MaterialTheme.typography.displayMedium,
        color = scheme.primary,
      )
      Spacer(Modifier.height(NovaSpace.sm))
      Text(
        if (registering) "Create your account." else "Welcome back.",
        fontFamily = NovaDisplay,
        style = MaterialTheme.typography.headlineSmall,
        color = scheme.onSurface,
      )
      Spacer(Modifier.height(NovaSpace.xs))
      Text(
        if (registering) "One account holds your chats, agents and connections." else "Pick up where you left off.",
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
      )
    }

    Spacer(Modifier.height(NovaSpace.xxl))

    GlassPanel(Modifier.fillMaxWidth(), corner = RoundedCornerShape(NovaRadius.xl)) {
      Column(Modifier.padding(NovaSpace.xl)) {
    OutlinedTextField(
      email, { email = it },
      label = { Text("Email") },
      leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp)) },
      singleLine = true,
      isError = error != null,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(NovaRadius.md),
      keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
        imeAction = androidx.compose.ui.text.input.ImeAction.Next,
      ),
      colors = novaFieldColors(),
    )
    Spacer(Modifier.height(14.dp))
    // Show/hide password — typing blind on a phone keyboard is a typo factory.
    var showPassword by remember { mutableStateOf(false) }
    OutlinedTextField(
      password, { password = it },
      label = { Text("Password") },
      leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
      singleLine = true,
      visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
      trailingIcon = {
        IconButton(onClick = { showPassword = !showPassword }) {
          Icon(
            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (showPassword) "Hide password" else "Show password",
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      },
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(NovaRadius.md),
      colors = novaFieldColors(),
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
    NovaButton(
      text = if (registering) "Create account" else "Sign in",
      onClick = { submit() },
      modifier = Modifier.fillMaxWidth(),
      enabled = email.isNotBlank() && password.length >= 8,
      loading = loading,
    )
    Spacer(Modifier.height(16.dp))
    TextButton(onClick = { registering = !registering; error = null }) {
      Text(if (registering) "Already have an account? Sign in" else "New to Nova? Create an account", color = scheme.onSurfaceVariant)
    }
      }
    }
  }
}

// §8 entities (backend-owned; Room cache mirrors these).
@kotlinx.serialization.Serializable
data class Message(val id: String = "", val role: String, val content: String, val attachments: List<com.nova.app.data.AttachmentInfo> = emptyList(), val reasoning: String = "", val ui: List<UiBlockDto> = emptyList(), val createdAt: String? = null)
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
  private val _conversationId = MutableStateFlow<String?>(conversationId)
  val activeConversationId: StateFlow<String?> = _conversationId.asStateFlow()
  private val _messages = MutableStateFlow<List<Message>>(emptyList())
  val messages: StateFlow<List<Message>> = _messages.asStateFlow()
  private val _streamingMsg = MutableStateFlow<Message?>(null)
  val streamingMsg: StateFlow<Message?> = _streamingMsg.asStateFlow()
  private val _streaming = MutableStateFlow(false)
  val streaming: StateFlow<Boolean> = _streaming.asStateFlow()
  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()
  private val _currentStep = MutableStateFlow<String?>(null)
  val currentStep: StateFlow<String?> = _currentStep.asStateFlow()
  // Sticky user-facing notice (reconnect needed, approval waiting). Kept apart
  // from currentStep so a warning survives the end of the stream.
  private val _notice = MutableStateFlow<String?>(null)
  val notice: StateFlow<String?> = _notice.asStateFlow()

  private val _generation = MutableStateFlow(GenerationSelection())
  val generation: StateFlow<GenerationSelection> = _generation.asStateFlow()
  private val _models = MutableStateFlow<List<ModelDto>>(emptyList())
  val models: StateFlow<List<ModelDto>> = _models.asStateFlow()
  private val _catalogError = MutableStateFlow(false)
  val catalogError: StateFlow<Boolean> = _catalogError.asStateFlow()

  private var job: Job? = null
  private var lastUserText: String? = null
  private var lastSelection: GenerationSelection? = null
  private var uploadApi: NovaApi? = null
  private var configuredApi: NovaApi? = null

  // §10 attachments pending on the next message. Ids are backend-issued; nothing is trusted client-side.
  data class PendingAttachment(val id: String, val filename: String, val size: Long, val mime: String = "")
  private val _pending = MutableStateFlow<List<PendingAttachment>>(emptyList())
  val pending: StateFlow<List<PendingAttachment>> = _pending.asStateFlow()
  private val _uploading = MutableStateFlow(false)
  val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

  fun configureApi(api: NovaApi) {
    if (configuredApi === api) return
    configuredApi = api
    uploadApi = api
    loadModelCatalog()
  }

  fun loadModelCatalog() {
    val api = uploadApi ?: return
    viewModelScope.launch {
      _catalogError.value = false
      runCatching { api.models() }
        .onSuccess { response ->
          _models.value = response.models
          _generation.value = _generation.value.validated(response.models)
        }
        .onFailure { _catalogError.value = true }
    }
  }

  fun selectModel(modelId: String?) {
    val next = _generation.value.selectModel(modelId)
    _generation.value = if (_models.value.isEmpty()) next else next.validated(_models.value)
  }

  fun selectEffort(effort: String?) {
    val next = _generation.value.selectEffort(effort)
    _generation.value = if (_models.value.isEmpty()) next else next.validated(_models.value)
  }

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

  fun send(text: String, selectionOverride: GenerationSelection? = null) {
    if (text.isBlank() || _streaming.value) return
    val selection = selectionOverride ?: _generation.value
    lastUserText = text
    lastSelection = selection
    val attachmentIds = _pending.value.map { it.id } // consumed once, then cleared
    val attachmentInfos = _pending.value.map { com.nova.app.data.AttachmentInfo(it.id, it.filename, it.mime, it.size) }
    _pending.value = emptyList()

    _messages.value += Message(role = "user", content = text, attachments = attachmentInfos, createdAt = java.time.Instant.now().toString())
    // The streaming stub carries the send time, so when it is promoted into the
    // transcript (done / stop / stream-close) it keeps a truthful clock instead
    // of the render-time one.
    _streamingMsg.value = Message(role = "assistant", content = "", createdAt = java.time.Instant.now().toString())
    _error.value = null
    _streaming.value = true
    _currentStep.value = null
    _notice.value = null

    job = viewModelScope.launch {
      // Lazy conversation creation: the first message of a session creates the
      // chat server-side. Opening the app never litters the history with
      // empty conversations the user never asked for.
      val cid = conversationId ?: runCatching { uploadApi?.createConversation()?.id }
        .getOrElse {
          _error.value = "No connection to Nova. Check your network and retry."
          _streaming.value = false
          _streamingMsg.value = null
          _messages.value = _messages.value.dropLast(1) // roll back the optimistic echo
          null
        }
      if (cid == null) return@launch
      conversationId = cid
      _conversationId.value = cid
      client.stream(ChatStreamRequest(cid, text, selection.modelId, selection.effort, attachmentIds))
        .catch { _error.value = it.message ?: "stream_failed" }
        .collect { chunk ->
          when (chunk.type) {
            "token" -> {
              val cur = _streamingMsg.value ?: return@collect
              _streamingMsg.value = cur.copy(content = cur.content + chunk.text)
            }
            "ui" -> {
              val cur = _streamingMsg.value ?: return@collect
              _streamingMsg.value = cur.copy(ui = (cur.ui + chunk.blocks).distinctBy { it.id }.take(8))
            }
            "reasoning" -> {
              val cur = _streamingMsg.value ?: return@collect
              _streamingMsg.value = cur.copy(reasoning = cur.reasoning + (chunk.text ?: ""))
            }
            "step" -> {
              _currentStep.value = chunk.label
            }
            "notice" -> {
              // F1: surface reconnect/retry/plugin notices instead of dropping them.
              // These go to a sticky banner, not the step row: a warning that
              // vanishes when the answer finishes is a warning nobody reads.
              val hint = chunk.message ?: chunk.provider?.let { "$it needs attention" } ?: "notice"
              _notice.value = if (chunk.code == "reconnect") "Reconnect ${chunk.provider ?: "provider"} to continue" else hint
            }
            "approval" -> {
              _notice.value = "Waiting for your approval: ${chunk.toolId ?: "action"}"
            }
            "error" -> {
              _error.value = chunk.code ?: "stream_error"
              _streaming.value = false
              _streamingMsg.value = null
              _currentStep.value = null
            }
            "done" -> {
              val done = _streamingMsg.value
              if (done != null && (done.content.isNotEmpty() || done.ui.isNotEmpty() || done.reasoning.isNotEmpty())) {
                _messages.value = _messages.value + done
              }
              _streamingMsg.value = null
              _streaming.value = false
              _currentStep.value = null
            }
          }
        }
      // Fallback: if the stream closed without a 'done' event, promote whatever
      // was accumulated so the response is not silently dropped.
      val leftover = _streamingMsg.value
      if (leftover != null && (leftover.content.isNotEmpty() || leftover.ui.isNotEmpty() || leftover.reasoning.isNotEmpty())) {
        _messages.value = _messages.value + leftover
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
    if (partial != null && (partial.content.isNotEmpty() || partial.ui.isNotEmpty() || partial.reasoning.isNotEmpty())) {
      _messages.value = _messages.value + partial
    }
    _streamingMsg.value = null
    _streaming.value = false
    _currentStep.value = null
  }

  /** §6 regenerate — drop the assistant reply and resend the same prompt. */
  fun regenerate() {
    val prompt = lastUserText ?: return
    stop()
    val list = _messages.value.toMutableList()
    if (list.lastOrNull()?.role == "assistant") list.removeAt(list.lastIndex)
    if (list.lastOrNull()?.role == "user") list.removeAt(list.lastIndex)
    _messages.value = list
    send(prompt, lastSelection)
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
    _conversationId.value = id
    lastUserText = null
    lastSelection = null
    _error.value = null
    _messages.value = emptyList()
  }

  fun setConversation(id: String) {
    // Switch properly instead of silently ignoring the call when a chat is
    // already open — the old guard made this a no-op after the first chat.
    if (id.isBlank() || id == conversationId) return
    stop()
    conversationId = id
    _conversationId.value = id
    _messages.value = emptyList()
  }

  fun configureSession(session: SessionToken) {
    client = ChatStreamClient(tokenProvider = { session.get() })
  }

  fun openConversation(id: String, history: List<Message>) {
    stop()
    conversationId = id
    _conversationId.value = id
    _messages.value = history
    // A stale prompt from the previous chat would be resent by "regenerate"
    // here — it belongs to a conversation that is no longer on screen.
    lastUserText = null
    lastSelection = null
    _pending.value = emptyList()
  }

  fun clearError() { _error.value = null }
  fun dismissNotice() { _notice.value = null }
}

// Highlight @plugin mentions in sent user bubbles — bubble background is
// primaryContainer, so paint mentions a step darker for contrast.
@Composable
fun highlightPluginMentions(text: String): AnnotatedString {
  val scheme = MaterialTheme.colorScheme
  return highlightPluginMentions(text, scheme.primary)
}

// Non-composable variant for VisualTransformation (typing-time highlight in the input).
fun highlightPluginMentions(text: String, accent: Color): AnnotatedString {
  return buildAnnotatedString {
    append(text)
    // All @plugin ids (§42) — an unstyled mention reads as plain text and users
    // report the plugin as "not showing", so every id must be covered here.
    val pattern = Regex("@(github|gmail|leetcode|calendar|drive|docs|sheets|vercel|supabase|browser)\\b", RegexOption.IGNORE_CASE)
    for (match in pattern.findAll(text)) {
      addStyle(SpanStyle(fontWeight = FontWeight.Bold, background = accent.copy(alpha = 0.30f), fontFamily = NovaMono), match.range.first, match.range.last + 1)
    }
  }
}

// §6 Markdown rendering; highlighted code fences with copy button (§6 "copy code").
@Composable
fun MarkdownBody(content: String, streaming: Boolean) {
  // §39: read the resolved theme, not the system one — a pinned dark app on a
  // light phone must still get dark code fences (and vice versa).
  val dark = novaDark()
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
      codeBackground = novaGlassFill(),
    ),
    typography = markdownTypography(),
    modifier = Modifier.padding(vertical = 2.dp),
  )
}

// §45 generative UI lives in GenerativeUi.kt — ten typed cards (summary,
// metrics, list, table, progress, timeline, comparison, code, chart, links),
// each with explicit theme-token contrast for dark and light panels.

@Composable
private fun BareAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  description: String,
  onClick: () -> Unit,
) {
  // 48dp minimum — the icon stays small, the tap target does not.
  IconButton(onClick = onClick, modifier = Modifier.size(MinTouchTarget)) {
    Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** Drawer menu row: h52, 24dp tertiary icon, 16sp label. */
@Composable
private fun DrawerMenuRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .clip(RoundedCornerShape(NovaRadius.md))
      .clickable { onClick() }
      .padding(horizontal = 4.dp, vertical = 12.dp)
      .semantics { role = Role.Button; contentDescription = label },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
    Spacer(Modifier.width(12.dp))
    Text(label, fontSize = 16.sp, color = scheme.onSurface)
  }
}

/**
 * Action icon row: copy, thumbs-up, thumbs-down, speaker, share, more —
 * 24dp glyphs under every completed assistant message. Like/dislike are
 * local toggles (no backend signal); more reveals refresh.
 */
@Composable
private fun AssistantActionRow(
  onCopy: () -> Unit,
  onSpeak: () -> Unit,
  onShare: () -> Unit,
  onRegenerate: () -> Unit,
  showRegenerate: Boolean,
) {
  val scheme = MaterialTheme.colorScheme
  var liked by remember { mutableStateOf(false) }
  var disliked by remember { mutableStateOf(false) }
  var menu by remember { mutableStateOf(false) }
  Row(
    Modifier.offset(x = (-12).dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onCopy, modifier = Modifier.size(MinTouchTarget)) {
      Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
    IconButton(
      onClick = { liked = !liked; if (liked) disliked = false },
      modifier = Modifier.size(MinTouchTarget),
    ) {
      Icon(
        Icons.Default.ThumbUp,
        contentDescription = "Good response",
        tint = if (liked) scheme.primary else scheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp),
      )
    }
    IconButton(
      onClick = { disliked = !disliked; if (disliked) liked = false },
      modifier = Modifier.size(MinTouchTarget),
    ) {
      Icon(
        Icons.Default.ThumbDown,
        contentDescription = "Bad response",
        tint = if (disliked) scheme.primary else scheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp),
      )
    }
    IconButton(onClick = onSpeak, modifier = Modifier.size(MinTouchTarget)) {
      Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
    IconButton(onClick = onShare, modifier = Modifier.size(MinTouchTarget)) {
      Icon(Icons.Default.Share, contentDescription = "Share", tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
    Box {
      IconButton(onClick = { menu = true }, modifier = Modifier.size(MinTouchTarget)) {
        Icon(Icons.Default.MoreVert, contentDescription = "More actions", tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
      }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = novaGlassFill()) {
        if (showRegenerate) {
          DropdownMenuItem(
            text = { Text("Regenerate") },
            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp)) },
            onClick = { menu = false; onRegenerate() },
          )
        } else {
          DropdownMenuItem(text = { Text("No further actions") }, onClick = { menu = false })
        }
      }
    }
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
          animation = tween(520, delayMillis = index * 170, easing = NovaMotion.Pulse),
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


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(api: NovaApi, session: SessionToken, onSettingsClick: () -> Unit = {}, onConnectionsClick: () -> Unit = {}, onAgentsClick: (String?) -> Unit = {}, onActivityClick: () -> Unit = {}, onAllChatsClick: () -> Unit = {}, onDrawerOpenChanged: (Boolean) -> Unit = {}, vm: ChatViewModel = viewModel()) {
  LaunchedEffect(session) { vm.configureSession(session) }
  LaunchedEffect(api) { vm.configureApi(api) }
  var conversations by remember { mutableStateOf<List<ConversationDto>>(emptyList()) }
  var convLoading by remember { mutableStateOf(false) }
  var editIndex by remember { mutableStateOf<Int?>(null) }
  var offline by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val scheme = MaterialTheme.colorScheme

  suspend fun loadConversations() {
    if (convLoading) return
    convLoading = true
    runCatching {
      // Sidebar shows only the 10 most recent chats; the full history lives on
      // the All Chats screen (§8 list) so the drawer stays scannable.
      conversations = api.conversations(limit = 10, offset = 0).conversations
      offline = false
    }.onFailure {
      offline = true
    }
    convLoading = false
  }
  LaunchedEffect(Unit) {
    loadConversations()
    // No eager createConversation() here: the chat is created on the first
    // send. Creating on launch put one empty conversation in the history
    // every time the app was opened and abandoned.
  }
  val messages by vm.messages.collectAsState()
  val streamingMsg by vm.streamingMsg.collectAsState()
  val streaming by vm.streaming.collectAsState()
  val error by vm.error.collectAsState()
  val currentStep by vm.currentStep.collectAsState()
  val notice by vm.notice.collectAsState()
  val activeConversationId by vm.activeConversationId.collectAsState()
  val pending by vm.pending.collectAsState()
  val uploading by vm.uploading.collectAsState()
  val generation by vm.generation.collectAsState()
  val models by vm.models.collectAsState()
  val catalogError by vm.catalogError.collectAsState()
  var generationPicker by rememberSaveable { mutableStateOf<String?>(null) }
  val selectedModel = models.firstOrNull { it.id == generation.modelId }
  val effortOptions = selectedModel?.efforts?.takeIf { it.isNotEmpty() }
    ?: models.flatMap { it.efforts }.distinct()
  val effortLabel = generation.effort?.let { effortDisplayLabel(it) } ?: "Default"
  fun openGenerationPicker(kind: String) {
    focusManager.clearFocus()
    keyboardController?.hide()
    generationPicker = kind
  }
  // A lazily created chat (first send) or a brand-new "New chat" is absent
  // from the drawer list until it is refreshed.
  LaunchedEffect(activeConversationId) {
    if (activeConversationId != null && conversations.none { it.id == activeConversationId }) {
      loadConversations()
    }
  }
  var input by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val drawerOpenCallback by rememberUpdatedState(onDrawerOpenChanged)
  LaunchedEffect(drawerState) {
    snapshotFlow { drawerState.currentValue }.collect { drawerOpenCallback(it != DrawerValue.Closed) }
  }
  DisposableEffect(drawerState) {
    onDispose { drawerOpenCallback(false) }
  }
  var creatingChat by remember { mutableStateOf(false) }
  var drawerDelete by remember { mutableStateOf<ConversationDto?>(null) }

  // Both new-chat controls share this guard so rapid taps cannot create a
  // pile of empty conversations while the first request is still in flight.
  // An empty current chat is reused instead: tapping "New chat" while already
  // on a blank chat must not spawn another server-side conversation.
  fun createFreshChat() {
    if (creatingChat) return
    if (messages.isEmpty() && !streaming) {
      // Stay on the current (blank) conversation — keeping its id means the
      // next send still works. Only the local state resets.
      vm.newChat(vm.activeConversationId.value)
      return
    }
    creatingChat = true
    scope.launch {
      runCatching { api.createConversation() }
        .onSuccess { created ->
          vm.newChat(created.id)
          loadConversations()
        }
        .onFailure { vm.clearError() }
      creatingChat = false
    }
  }

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

  // §42 @ mention picker: typing "@" (at the end of the message) offers plugins
  // as tappable chips; tapping inserts the mention. Ready state comes from the
  // backend so an unconnected plugin says so instead of silently failing.
  var plugins by remember { mutableStateOf<List<com.nova.app.data.PluginDto>>(emptyList()) }
  LaunchedEffect(Unit) { runCatching { plugins = api.plugins().plugins } }
  val trailingMention = Regex("@([a-zA-Z]*)$").find(input)?.groupValues?.get(1)
  val mentionCandidates = if (trailingMention != null) {
    plugins.filter { it.id.startsWith(trailingMention, ignoreCase = true) }
  } else emptyList()
  // The @ trigger button needs to drop focus back into the field after it
  // appends "@", so the keyboard stays up and typing continues seamlessly.
  val composerFocus = remember { androidx.compose.ui.focus.FocusRequester() }

  // Scroll model: follow the stream only while the reader is already at the
  // bottom. Pull away to read back and the view stays put; a "Latest" pill
  // offers the way down. A queued animateScrollToItem per token fought the
  // finger and janked, so this jumps instead of animating.
  val lastUserRequest = messages.lastOrNull { it.role == "user" }?.content?.takeIf { it.isNotBlank() }
  val atBottom by remember {
    derivedStateOf {
      val info = listState.layoutInfo
      val last = info.visibleItemsInfo.lastOrNull()
      last == null || (last.index >= info.totalItemsCount - 1 &&
        last.offset + last.size <= info.viewportEndOffset + 24)
    }
  }
  val followStream = remember { mutableStateOf(true) }
  LaunchedEffect(atBottom) { followStream.value = atBottom }
  LaunchedEffect(messages.size, streamingMsg?.content?.length, streamingMsg?.ui?.size) {
    val total = listState.layoutInfo.totalItemsCount
    if (total > 0 && followStream.value) listState.scrollToItem(total - 1)
  }

  // Deleting from the library is destructive and cannot be undone server-side,
  // so the row stages it and this confirms.
  drawerDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { drawerDelete = null },
      containerColor = novaGlassFill(),
      title = { Text("Delete chat?", fontFamily = NovaDisplay) },
      text = {
        Text(
          "\"${target.title.ifBlank { "New chat" }}\" and its messages will be removed permanently.",
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          drawerDelete = null
          scope.launch {
            runCatching { api.deleteConversation(target.id) }
              .onSuccess {
                conversations = conversations.filter { it.id != target.id }
                if (activeConversationId == target.id) createFreshChat()
              }
              .onFailure { vm.clearError() }
          }
        }) { Text("Delete", color = scheme.error, fontWeight = FontWeight.Bold) }
      },
      dismissButton = { TextButton(onClick = { drawerDelete = null }) { Text("Keep", color = scheme.onSurfaceVariant) } },
    )
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      // Fill-only glass: drawers are a separate window layer — no fake blur.
      ModalDrawerSheet(
        drawerContainerColor = novaGlassFill(),
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.width(310.dp),
      ) {
        // Header: Nova 24 bold + search circle, then menu rows.
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
              "Nova",
              modifier = Modifier.weight(1f),
              fontSize = 24.sp,
              fontWeight = FontWeight.Bold,
              color = scheme.onSurface,
            )
            SpecCircleButton(
              Icons.Default.Search,
              "Search chats",
              onClick = { scope.launch { drawerState.close() }; onAllChatsClick() },
              onBlack = false,
            )
          }
          Spacer(Modifier.height(8.dp))
          DrawerMenuRow(Icons.Default.PhotoLibrary, "Images") { scope.launch { drawerState.close() }; pickImage.launch("image/*") }
          DrawerMenuRow(Icons.AutoMirrored.Filled.MenuBook, "Library") { scope.launch { drawerState.close() }; onAllChatsClick() }
          DrawerMenuRow(Icons.Default.Folder, "Projects") { scope.launch { drawerState.close() }; onAgentsClick(null) }
          DrawerMenuRow(Icons.Default.Schedule, "Scheduled") { scope.launch { drawerState.close() }; onActivityClick() }
          DrawerMenuRow(Icons.Default.AlternateEmail, "Plugins") { scope.launch { drawerState.close() }; onConnectionsClick() }
        }
        HorizontalDivider(color = scheme.outlineVariant)
        val drawerListState = rememberLazyListState()
        LazyColumn(state = drawerListState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
          if (conversations.isEmpty() && !convLoading) {
            item {
              Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 28.dp)) {
                Text("Nothing here yet", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("Start a conversation and it will appear here.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
              }
            }
          }
          items(conversations.size) { idx ->
            val c = conversations[idx]
            val selected = activeConversationId == c.id
            Row(
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NovaRadius.md))
                .background(if (selected) scheme.primaryContainer.copy(alpha = 0.72f) else Color.Transparent)
                .clickable {
                  scope.launch {
                    runCatching {
                      val detail = api.conversation(c.id)
                      vm.openConversation(c.id, detail.messages.map { Message(it.id, it.role, it.content, it.attachments, ui = it.ui, createdAt = it.createdAt) })
                    }
                    drawerState.close()
                  }
                }
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(Modifier.size(8.dp).background(if (selected) scheme.primary else scheme.outlineVariant, CircleShape))
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Text(c.title.ifBlank { "New chat" }, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                novaRelativeTime(c.updatedAt)?.let { when_ ->
                  Text(when_, fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = novaFaint())
                }
              }
              NovaIconAction(
                icon = Icons.Default.Delete,
                contentDescription = "Delete ${c.title.ifBlank { "chat" }}",
                tint = scheme.error,
                onClick = { drawerDelete = c },
              )
            }
          }
          if (convLoading && conversations.isNotEmpty()) {
            item {
              Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = scheme.onSurfaceVariant)
              }
            }
          }
        }
        HorizontalDivider(color = scheme.outlineVariant)
        // Floating bottom bar: accent Chat pill + avatar + waveform.
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Button(
            onClick = {
              createFreshChat()
              scope.launch { drawerState.close() }
            },
            enabled = !creatingChat,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = scheme.primary,
              contentColor = scheme.onPrimary,
            ),
          ) {
            if (creatingChat) {
              CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
            } else {
              Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Chat", fontWeight = FontWeight.SemiBold)
          }
          Spacer(Modifier.width(12.dp))
          val avatarInitial = session.getEmail()?.trim()?.take(1)?.uppercase() ?: "N"
          Surface(
            onClick = { scope.launch { drawerState.close() }; onSettingsClick() },
            shape = CircleShape,
            color = novaGlassFill(),
            border = androidx.compose.foundation.BorderStroke(1.dp, novaGlassEdge()),
            modifier = Modifier.size(MinTouchTarget).semantics { role = Role.Button; contentDescription = "Settings" },
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
              Text(avatarInitial, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = scheme.onSurface)
            }
          }
          Spacer(Modifier.width(8.dp))
          Surface(
            onClick = {
              if (micGranted && voice.available) voice.start()
              else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            shape = CircleShape,
            color = scheme.primary,
            modifier = Modifier.size(MinTouchTarget).semantics { role = Role.Button; contentDescription = "Voice input" },
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                listOf(8.dp, 13.dp, 9.dp).forEach { h ->
                  Box(Modifier.width(2.5.dp).height(h).clip(RoundedCornerShape(NovaRadius.hair)).background(scheme.onPrimary))
                }
              }
            }
          }
        }
      }
    },
  ) {
  // No opaque screen background — ambient field carries through the thread.
  Column(Modifier.fillMaxSize()) {
    // Top bar: transparent over ambient, glass circle actions.
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SpecCircleButton(
          Icons.Default.Menu,
          "Open library",
          onClick = { scope.launch { drawerState.open() } },
        )
        Spacer(Modifier.weight(1f))
        if (messages.isEmpty() && !streaming) {
          SpecCircleButton(
            Icons.Default.Edit,
            "New chat",
            onClick = { createFreshChat() },
          )
        } else {
          SpecCircleButton(
            Icons.Default.Edit,
            "New chat",
            onClick = { createFreshChat() },
          )
          Spacer(Modifier.width(8.dp))
          var topMenu by remember { mutableStateOf(false) }
          Box {
            SpecCircleButton(
              Icons.Default.MoreVert,
              "Chat options",
              onClick = { topMenu = true },
            )
            DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }, containerColor = novaGlassFill()) {
               DropdownMenuItem(
                 text = { Text("Search chats") },
                 leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                 onClick = { topMenu = false; onAllChatsClick() },
               )
               if (lastUserRequest != null) {
                 DropdownMenuItem(
                   text = { Text("Turn this into an agent") },
                   leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(20.dp)) },
                   onClick = {
                     topMenu = false
                     onAgentsClick("Create an agent based on this request: $lastUserRequest")
                   },
                 )
               }

            }
          }
        }
      }

    if (offline) {
      Surface(color = novaGlassFill(), modifier = Modifier.fillMaxWidth()) {
        Text(
          "Offline. Showing what is cached. Reconnect to keep going.",
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
          style = MaterialTheme.typography.bodySmall,
          color = scheme.onSurfaceVariant,
        )
      }
    }

    // Chat layout: the message list scrolls under a floating composer column.
    // Reserve the composer height PLUS the full bottom chrome (bar + margin +
    // nav inset) so the last message always rests clear above both.
    val density = LocalDensity.current
    var overlayPx by remember { mutableStateOf(0) }
    val overlayDp = with(density) { overlayPx.toDp() }
    val bottomChrome = LocalBottomChrome.current

    Box(Modifier.weight(1f)) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      state = listState,
      contentPadding = PaddingValues(top = 12.dp, bottom = overlayDp + bottomChrome + NovaSpace.lg),
    ) {
      if (messages.isEmpty()) {
        item {
          Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(
              "What needs\ndoing today?",
              fontFamily = NovaDisplay,
              style = MaterialTheme.typography.displaySmall,
              color = scheme.onSurface,
              lineHeight = MaterialTheme.typography.displaySmall.lineHeight,
            )
            Spacer(Modifier.height(10.dp))
            Text(
              "Nova reads, writes, searches and runs errands across your tools.",
              style = MaterialTheme.typography.bodyMedium,
              color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            // Two-column starter cards (ref 5): tap runs immediately.
            // Executable-chip behaviour kept; hidden while streaming/uploading.
            val startersEnabled = !streaming && !uploading
            listOf(
              "Summarize a file" to "Drop a doc and get the short version.",
              "Plan my day" to "Turn a messy list into a schedule.",
              "Draft a reply" to "Answer email in your voice.",
              "Search the web" to "Find it without leaving the chat.",
            ).chunked(2).forEach { pair ->
              Row(
                horizontalArrangement = Arrangement.spacedBy(NovaSpace.sm),
                modifier = Modifier.fillMaxWidth(),
              ) {
                pair.forEach { (prompt, blurb) ->
                  NovaCard(
                    onClick = { if (startersEnabled) vm.send(prompt) },
                    contentPadding = PaddingValues(horizontal = NovaSpace.md, vertical = NovaSpace.md),
                    modifier = Modifier.weight(1f),
                  ) {
                    Text(
                      prompt,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = scheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                      blurb,
                      style = MaterialTheme.typography.bodySmall,
                      color = scheme.onSurfaceVariant,
                      maxLines = 2,
                    )
                  }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
              }
              Spacer(Modifier.height(NovaSpace.sm))
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
              shape = RoundedCornerShape(NovaRadius.md),
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
            Button(onClick = { editIndex = null; vm.editAndResend(i, editText) }, shape = CircleShape) { Text("Send") }
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
                          .clip(RoundedCornerShape(NovaRadius.row)),
                      )
                    } else {
                      Surface(
                        shape = RoundedCornerShape(NovaRadius.sm),
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
              // User bubble: primaryContainer fill + onPrimaryContainer ink —
              // tonal accent, readable in both light and dark glass modes.
              Surface(
                shape = RoundedCornerShape(NovaRadius.bubble),
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
              ) {
                Text(
                  highlightPluginMentions(m.content),
                  style = MaterialTheme.typography.bodyMedium,
                  color = scheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
              }
              Spacer(Modifier.height(4.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                novaClockTime(m.createdAt)?.let { clock ->
                  Text(clock, style = MaterialTheme.typography.labelSmall, fontFamily = NovaMono, color = novaFaint())
                  Spacer(Modifier.width(NovaSpace.sm))
                }
                Text(
                  "Tap to edit",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = NovaMono,
                  color = novaFaint(),
                  modifier = Modifier
                    .clip(RoundedCornerShape(NovaRadius.sm))
                    .clickable { editIndex = i }
                    .padding(horizontal = NovaSpace.xs, vertical = 2.dp),
                )
              }
            }
          } else {
            // Spec S08: left-aligned white 16sp reply + action icon row.
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
              if (m.reasoning.isNotBlank()) {
                NovaReasoningBlock(m.reasoning, streaming = false)
                if (m.content.isNotEmpty()) Spacer(Modifier.height(NovaSpace.xs))
              }
              MarkdownBody(m.content, false)
              if (m.ui.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                GenerativeUiRenderer(m.ui, clipboard) { url ->
                  if (url.startsWith("https://")) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
              }
              AssistantActionRow(
                onCopy = { clipboard.setText(AnnotatedString(m.content)) },
                onSpeak = { speakOut(m.content) },
                onShare = {
                  val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, m.content)
                  }
                  context.startActivity(Intent.createChooser(send, "Share response"))
                },
                onRegenerate = { vm.regenerate() },
                showRegenerate = i == messages.lastIndex,
              )
            }
          }
        }
      }

      if (streamingMsg != null) {
        item(key = "streaming") {
          val sm = streamingMsg!!
          Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            // Spec S13 live status: globe + #B3B3B3 15sp text while searching.
            currentStep?.let { step ->
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  Icons.Default.Public,
                  contentDescription = null,
                  tint = scheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(step, fontSize = 15.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
              }
              Spacer(Modifier.height(6.dp))
            }

            if (sm.reasoning.isNotBlank()) {
              NovaReasoningBlock(sm.reasoning, streaming = true)
              Spacer(Modifier.height(NovaSpace.xs))
            }

            Spacer(Modifier.height(6.dp))
            if (sm.content.isEmpty() && currentStep == null) {
              // Spec S09: 12dp violet dot pulsing at the left margin.
              val pulse = rememberInfiniteTransition(label = "streamDot")
              val dotAlpha by pulse.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                  animation = tween(600, easing = NovaMotion.Pulse),
                  repeatMode = RepeatMode.Reverse,
                ),
                label = "streamDotAlpha",
              )
              Box(
                Modifier
                  .size(12.dp)
                  .graphicsLayer { alpha = dotAlpha }
                  .clip(CircleShape)
                  .background(scheme.primary)
                  .semantics { contentDescription = "Nova is writing" },
              )
            } else if (sm.content.isNotEmpty()) {
              MarkdownBody(sm.content, true)
            }
            if (sm.ui.isNotEmpty()) {
              Spacer(Modifier.height(8.dp))
              GenerativeUiRenderer(sm.ui, clipboard) { url ->
                if (url.startsWith("https://")) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
              }
            }
          }
        }
      }

      error?.let { code ->
        item {
          Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            shape = RoundedCornerShape(NovaRadius.md),
            color = novaGlassFill(),
            border = androidx.compose.foundation.BorderStroke(1.dp, novaGlassEdge()),
          ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
              Text(novaErrorText(code), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
              TextButton(onClick = { vm.clearError(); vm.retry() }) { Text("Retry", color = scheme.primary) }
            }
          }
        }
      }
    }

    // Floating jump control, parked above the composer column (composer
    // height + bottom chrome + gap) so it never hides behind the chrome.
    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomChrome + overlayDp + NovaSpace.md)) {
      NovaJumpToLatest(
        visible = !atBottom && messages.isNotEmpty(),
        onClick = {
          scope.launch {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) listState.animateScrollToItem(total - 1)
          }
        },
        label = if (streaming) "Following live" else "Latest",
      )
    }

    Column(
      Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(bottom = bottomChrome)
        .imePadding()
        .onGloballyPositioned { coords -> overlayPx = coords.size.height },
    ) {

    if (pending.isNotEmpty() || uploading) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
          .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
      ) {
              if (uploading) {
                NovaThinkingIndicator(liveStep = "Uploading…")
                Spacer(Modifier.width(8.dp))
              }
        pending.forEach { p ->
          Row(
            Modifier.padding(end = 8.dp).clip(RoundedCornerShape(NovaRadius.row)).background(novaGlassFill())
              .clickable { vm.removeAttachment(p.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(p.filename, maxLines = 1, fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = scheme.onSurface, modifier = Modifier.widthIn(max = 160.dp))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Close, contentDescription = "Remove attachment", modifier = Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
          }
        }
      }
    }

    // Sticky notice (reconnect / approval). Dismissible, and it survives the
    // end of the stream — unlike a step label, which is gone by then.
    notice?.let { text ->
      Surface(color = scheme.tertiary.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth()) {
        Row(
          Modifier.padding(start = NovaSpace.xl, end = NovaSpace.xs, top = NovaSpace.xs, bottom = NovaSpace.xs),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Default.Info, contentDescription = null, tint = scheme.tertiary, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(NovaSpace.sm))
          Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
          NovaIconAction(Icons.Default.Close, "Dismiss notice", tint = scheme.onSurfaceVariant) { vm.dismissNotice() }
        }
      }
    }

    // Composer: solid spec pill (#202020) floating over the thread — opaque
    // so message text can never read through it. Height animates as the
    // field grows (ChatGPT-mobile style): list padding tracks overlayDp via
    // onGloballyPositioned above, so messages stay clear of the taller box.
    GlassPanel(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 8.dp)
        .animateContentSize(animationSpec = tween(NovaMotion.Standard, easing = NovaMotion.Ease)),
      corner = RoundedCornerShape(NovaRadius.xl),
    ) {
      Column {
        if (mentionCandidates.isNotEmpty()) {
          Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            mentionCandidates.forEach { p ->
              Surface(
                shape = RoundedCornerShape(NovaRadius.sm),
                color = if (p.ready) scheme.secondary.copy(alpha = 0.14f) else novaGlassFill(),
                modifier = Modifier.clickable {
                  val drop = (trailingMention?.length ?: 0) + 1
                  input = input.dropLast(drop) + "@${p.id} "
                },
              ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                  Text(
                    "@${p.id}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = NovaMono,
                    color = if (p.ready) scheme.primary else scheme.onSurfaceVariant,
                  )
                  Text(
                    if (p.ready) p.blurb else "Not connected",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                  )
                }
              }
            }
          }
        } else if (trailingMention != null) {
          // Typed "@" + something with no match: silence reads as "the feature
          // is broken". Name the closest alternatives instead.
          Text(
            "No plugin named \u201C$trailingMention\u201D. Try @browser, @github, @gmail\u2026",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = NovaMono,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
          )
        }
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.Bottom,
        ) {
          // Composer row: + · field · mic · accent action. Transparent field
          // so the glass panel shows through. Constant corner radius (not
          // CircleShape) so a multi-line box keeps its shape instead of the
          // radius scaling with height and clipping the first line. Controls
          // sit on the bottom edge so the field grows upward past them.
          Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(NovaRadius.xl),
            color = Color.Transparent,
          ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 6.dp), verticalAlignment = Alignment.Bottom) {
              Box {
                IconButton(onClick = { showAttachMenu = true }, enabled = !uploading && !streaming, modifier = Modifier.size(48.dp)) {
                  Icon(Icons.Default.Add, contentDescription = "Attach", tint = scheme.onSurface, modifier = Modifier.size(24.dp))
                }
              }
              // ChatGPT-style: voice affordance only while the field is empty.
              // Once there is text the accent action is send; a second mic
              // next to it is noise (and voice lands its full transcript at
              // once, so nothing is mid-utterance while typing).
              if (input.isBlank()) {
                if (micGranted && voice.available) {
                  IconButton(onClick = { voice.start() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = scheme.onSurface, modifier = Modifier.size(24.dp))
                  }
                } else {
                  IconButton(onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Mic, contentDescription = "Enable voice input", tint = scheme.onSurface, modifier = Modifier.size(24.dp))
                  }
                }
              }
              val primaryColor = scheme.primary
              OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).focusRequester(composerFocus),
                visualTransformation = VisualTransformation { text ->
                  TransformedText(highlightPluginMentions(text.text, primaryColor), OffsetMapping.Identity)
                },
                placeholder = {
                  Text(
                    if (messages.isEmpty()) "Ask Nova" else "Reply to Nova",
                    fontSize = 16.sp,
                    color = scheme.onSurfaceVariant,
                  )
                },
                // Grows one line at a time; past six the field scrolls inside
                // itself (platform behaviour) instead of pushing the thread
                // off screen.
                minLines = 1,
                maxLines = 6,
                shape = RoundedCornerShape(NovaRadius.lg),
                // Sentence capitalisation: typing a prompt on a phone keyboard
                // should not start with a shift press every time.
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                  capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                  // Enter sends, like every chat app. Shift+Enter still inserts
                  // a newline because IME actions never fire for hardware shifts.
                  imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                  onSend = {
                    if (input.isNotBlank() && !streaming && !uploading) {
                      vm.send(input)
                      input = ""
                    }
                  },
                ),
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
              // Composer morph: waveform (voice) → up arrow (send) → stop.
              SpecAccentButton(
                state = when {
                  streaming -> SpecAccentState.STOP
                  input.isNotBlank() -> SpecAccentState.SEND_READY
                  else -> SpecAccentState.VOICE_IDLE
                },
                enabled = !uploading && (streaming || input.isNotBlank() || (micGranted && voice.available)),
                onClick = {
                  when {
                    streaming -> vm.stop()
                    input.isNotBlank() -> {
                      vm.send(input)
                      input = ""
                    }
                    micGranted && voice.available -> voice.start()
                    else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                  }
                },
              )
            }
          }
        }
        Row(
          Modifier.fillMaxWidth().padding(start = NovaSpace.sm, end = NovaSpace.sm, bottom = NovaSpace.xs),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          GenerationTrigger(
            value = selectedModel?.label ?: "Model: Auto",
            contentLabel = "Model",
            enabled = !streaming && !uploading,
            onClick = { openGenerationPicker("model") },
            modifier = Modifier.weight(1f, fill = false),
          )
          GenerationTrigger(
            value = "Effort: $effortLabel",
            contentLabel = "Thinking effort",
            enabled = !streaming && !uploading,
            onClick = { openGenerationPicker("effort") },
          )
        }
      }
    }
    } // overlay Column
    } // Box

    // Attach menu: ModalBottomSheet glass tiles (Camera / Photos / Files / Plugins).
    if (showAttachMenu) {
      ModalBottomSheet(
        onDismissRequest = { showAttachMenu = false },
        containerColor = novaGlassFill(),
        contentColor = scheme.onSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      ) {
        Column(
          Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 28.dp),
          verticalArrangement = Arrangement.spacedBy(NovaSpace.sm),
        ) {
          listOf(
            Triple(Icons.Default.CameraAlt, "Camera") {
              showAttachMenu = false
              if (cameraGranted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            Triple(Icons.Default.PhotoLibrary, "Photos") { showAttachMenu = false; pickImage.launch("image/*") },
            Triple(Icons.Default.Attachment, "Files") { showAttachMenu = false; pickFile.launch("*/*") },
            Triple(Icons.Default.Code, "Plugins") { showAttachMenu = false; onConnectionsClick() },
          ).forEach { (icon, label, action) ->
            Surface(
              onClick = action,
              shape = RoundedCornerShape(NovaRadius.md),
              color = Color.Transparent,
              border = androidx.compose.foundation.BorderStroke(1.dp, novaGlassEdge()),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(label, fontSize = 16.sp, color = scheme.onSurface)
              }
            }
          }
        }
      }
    }

    generationPicker?.let { picker ->
      GenerationPickerSheet(
        kind = picker,
        models = models,
        selection = generation,
        effortOptions = effortOptions,
        catalogError = catalogError,
        onRetry = vm::loadModelCatalog,
        onSelectModel = vm::selectModel,
        onSelectEffort = vm::selectEffort,
        onDismiss = { generationPicker = null },
      )
    }
  }
}
}

@Composable
private fun NovaReasoningBlock(reasoning: String, streaming: Boolean) {
  var expanded by rememberSaveable(streaming) { mutableStateOf(streaming) }
  val scheme = MaterialTheme.colorScheme
  Column(Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(NovaMotion.Standard, easing = NovaMotion.Ease))) {
    Row(
      Modifier
        .fillMaxWidth()
        .heightIn(min = MinTouchTarget)
        .toggleable(
          value = expanded,
          onValueChange = { expanded = it },
          role = Role.Button,
        )
        .semantics {
          contentDescription = "Reasoning"
          stateDescription = if (expanded) "Expanded" else "Collapsed"
        },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "Reasoning",
        style = MaterialTheme.typography.labelLarge,
        color = scheme.onSurfaceVariant,
      )
      Spacer(Modifier.weight(1f))
      Icon(
        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = null,
        tint = scheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }
    AnimatedVisibility(
      visible = expanded,
      enter = fadeIn(tween(NovaMotion.Quick, easing = NovaMotion.Ease)) + expandVertically(tween(NovaMotion.Quick, easing = NovaMotion.Ease)),
      exit = fadeOut(tween(NovaMotion.Quick, easing = NovaMotion.Ease)) + shrinkVertically(tween(NovaMotion.Quick, easing = NovaMotion.Ease)),
    ) {
      Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = NovaSpace.xs, end = NovaSpace.sm),
      ) {
        Box(
          Modifier
            .width(2.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(1.dp))
            .background(scheme.outlineVariant.copy(alpha = 0.55f)),
        )
        Text(
          reasoning.trim(),
          modifier = Modifier.padding(start = NovaSpace.md),
          style = MaterialTheme.typography.bodySmall,
          color = scheme.onSurfaceVariant,
          lineHeight = 19.sp,
        )
      }
    }
  }
}

@Composable
private fun GenerationTrigger(
  value: String,
  contentLabel: String,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  TextButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
      .heightIn(min = MinTouchTarget)
      .semantics {
        this.contentDescription = contentLabel
        stateDescription = value
      },
    contentPadding = PaddingValues(horizontal = NovaSpace.sm, vertical = NovaSpace.xs),
    colors = ButtonDefaults.textButtonColors(
      contentColor = scheme.onSurfaceVariant,
      disabledContentColor = scheme.onSurfaceVariant.copy(alpha = 0.85f),
    ),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        value,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelLarge,
      )
      Icon(
        Icons.Default.ExpandMore,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GenerationPickerSheet(
  kind: String,
  models: List<ModelDto>,
  selection: GenerationSelection,
  effortOptions: List<String>,
  catalogError: Boolean,
  onRetry: () -> Unit,
  onSelectModel: (String?) -> Unit,
  onSelectEffort: (String?) -> Unit,
  onDismiss: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = novaGlassFill(),
    contentColor = scheme.onSurface,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = ScreenGutter, vertical = NovaSpace.sm)
        .padding(bottom = NovaSpace.xl),
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          if (kind == "model") "Model" else "Thinking effort",
          modifier = Modifier.weight(1f),
          fontFamily = NovaDisplay,
          style = MaterialTheme.typography.titleLarge,
          color = scheme.onSurface,
        )
        NovaIconAction(Icons.Default.Close, "Close", onClick = onDismiss)
      }
      Spacer(Modifier.height(NovaSpace.sm))
      Column(Modifier.selectableGroup()) {
        if (kind == "model") {
          GenerationOptionRow(
            title = "Auto",
            description = "Let Nova choose the default model.",
            selected = selection.modelId == null,
            onClick = { onSelectModel(null) },
          )
          if (catalogError) {
            Row(
              Modifier.fillMaxWidth().padding(vertical = NovaSpace.sm),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                "Model list unavailable.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.error,
              )
              TextButton(onClick = onRetry) { Text("Retry") }
            }
          } else if (models.isEmpty()) {
            Row(
              Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.primary)
              Spacer(Modifier.width(NovaSpace.sm))
              Text("Loading models", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
          } else {
            models.forEach { model ->
              GenerationOptionRow(
                title = model.label,
                description = model.description,
                selected = selection.modelId == model.id,
                onClick = { onSelectModel(model.id) },
              )
            }
          }
        } else {
          GenerationOptionRow(
            title = "Default",
            description = "Use the model's own thinking depth.",
            selected = selection.effort == null,
            onClick = { onSelectEffort(null) },
          )
          effortOptions.forEach { effort ->
            GenerationOptionRow(
              title = effortDisplayLabel(effort),
              description = effortDescription(effort),
              selected = selection.effort == effort,
              onClick = { onSelectEffort(effort) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GenerationOptionRow(
  title: String,
  description: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 64.dp)
      .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
      .padding(vertical = NovaSpace.sm),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(
        title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = scheme.onSurface,
      )
      Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
      )
    }
    RadioButton(selected = selected, onClick = null)
  }
}

private fun effortDisplayLabel(effort: String): String = effort.replaceFirstChar { it.uppercase() }

private fun effortDescription(effort: String): String = when (effort) {
  "minimal" -> "Fastest responses for simple requests."
  "low" -> "Light reasoning for everyday work."
  "medium" -> "Balanced depth and speed."
  "high" -> "Deepest reasoning for complex work."
  else -> "Custom thinking depth."
}

@Composable fun AgentsScreen(
  api: NovaApi,
  onBack: (() -> Unit)? = null,
  initialPrompt: String? = null,
  onPromptConsumed: () -> Unit = {},
) {
  val viewModel: AgentsViewModel = viewModel()
  val state by viewModel.state.collectAsState()
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  var filter by rememberSaveable { mutableStateOf("All") }
  val scheme = MaterialTheme.colorScheme

  LaunchedEffect(api) { viewModel.configureApi(api) }
  LaunchedEffect(initialPrompt) {
    if (!initialPrompt.isNullOrBlank()) {
      viewModel.setPrompt(initialPrompt)
      onPromptConsumed()
    }
  }

  val liveCount = state.agents.count { it.status.equals("active", true) }
  val filteredAgents = remember(state.agents, filter) {
    when (filter) {
      "Live" -> state.agents.filter { it.status.equals("active", true) }
      "Drafts" -> state.agents.filter { it.status.equals("draft", true) }
      "Paused" -> state.agents.filter { it.status.equals("paused", true) }
      else -> state.agents
    }
  }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize().padding(horizontal = ScreenGutter),
    contentPadding = PaddingValues(top = NovaSpace.xl, bottom = NovaSpace.xxl + LocalBottomChrome.current),
    verticalArrangement = Arrangement.spacedBy(NovaSpace.lg),
  ) {
    item {
      NovaPageHeader(
        eyebrow = if (liveCount == 0) "Your workspace" else "$liveCount live",
        title = "Agents",
        subtitle = "Tell Nova what should happen. Review the tools and timing before anything runs.",
        onBack = onBack,
        trailing = {
          NovaIconAction(Icons.Default.Add, "Draft an agent") {
            viewModel.setPrompt("")
            scope.launch { listState.animateScrollToItem(if (state.notice != null) 2 else 1) }
          }
        },
      )
    }

    state.notice?.let { notice ->
      item {
        AgentsNotice(
          message = notice,
          onDismiss = viewModel::clearNotice,
        )
      }
    }

    item {
      AgentsBuilder(
        prompt = state.builderPrompt,
        questions = state.builderQuestions,
        error = state.builderError,
        isBuilding = state.isBuilding,
        onPromptChange = viewModel::setPrompt,
        onBuild = viewModel::buildAgent,
        onClear = viewModel::clearBuilder,
      )
    }

    state.draft?.let { draft ->
      item {
        AgentReviewCard(
          draft = draft,
          isSaving = state.isSaving,
          error = state.builderError,
          onEdit = { viewModel.openEditor() },
          onSaveDraft = { viewModel.saveDraft(false) },
          onActivate = { viewModel.saveDraft(true) },
          onDiscard = viewModel::clearBuilder,
        )
      }
    }

    if (state.approvals.isNotEmpty()) {
      item {
        AgentsApprovalPanel(
          approvals = state.approvals,
          busyId = state.approvalBusyId,
          onDecision = viewModel::decideApproval,
        )
      }
    }

    item {
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text("Your agents", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
          Text(
            if (state.agents.isEmpty()) "Start with one useful job." else "${state.agents.size} configured",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
          )
        }
        if (state.isRefreshing) {
          CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = scheme.primary)
        } else if (state.agents.isNotEmpty()) {
          NovaIconAction(Icons.Default.Refresh, "Refresh agents", onClick = viewModel::refresh)
        }
      }
    }

    item {
      NovaFilterChips(
        options = listOf("All", "Live", "Drafts", "Paused"),
        selected = filter,
        onSelect = { filter = it },
      )
    }

    state.loadError?.let { error ->
      item {
        AgentsInlineError(error, viewModel::refresh)
      }
    }

    if (state.isLoading && state.agents.isEmpty()) {
      item { NovaSkeletonCards(rows = 3, height = 96.dp) }
    } else if (!state.isLoading && filteredAgents.isEmpty() && state.loadError == null) {
      item {
        NovaEmptyState(
          title = if (state.agents.isEmpty()) "No agents yet" else "No $filter agents",
          body = if (state.agents.isEmpty()) "Describe one job above and Nova will draft the first version." else "Try another filter to see the rest of your workspace.",
        )
      }
    }

    items(filteredAgents.size, key = { index -> filteredAgents[index].id.ifBlank { "agent-$index" } }) { index ->
      val agent = filteredAgents[index]
      AgentListCard(
        agent = agent,
        onClick = { viewModel.selectAgent(agent.id) },
      )
    }
  }

  if (state.selectedAgentId != null) {
    AgentDetailSheet(
      agent = state.selectedAgent,
      executions = state.selectedExecutions,
      isLoading = state.isLoadingAgent,
      operation = state.operation,
      runMessage = state.runMessage,
      error = state.detailError,
      onDismiss = viewModel::closeAgent,
      onRetry = { agent -> viewModel.selectAgent(agent.id) },
      onRun = { agent -> viewModel.runAgent(agent, false) },
      onBackground = { agent -> viewModel.runAgent(agent, true) },
      onToggleActive = { agent -> viewModel.setAgentActive(agent, agent.status != "active") },
      onCancelRun = viewModel::cancelSelectedRun,
      onEdit = { agent ->
        viewModel.closeAgent()
        viewModel.openEditor(agent)
      },
    )
  }

  if (state.isEditorOpen && state.draft != null) {
    AgentEditorSheet(
      draft = state.draft!!,
      tools = state.tools,
      toolError = state.toolsError,
      isSaving = state.isSaving,
      error = state.builderError,
      isEditing = state.editingAgentId != null,
      onDismiss = viewModel::closeEditor,
      onNameChange = viewModel::updateDraftName,
      onDescriptionChange = viewModel::updateDraftDescription,
      onGoalChange = viewModel::updateDraftGoal,
      onInstructionsChange = viewModel::updateDraftInstructions,
      onToolToggle = viewModel::toggleDraftTool,
      onScheduleChange = viewModel::setDraftSchedule,
      onTimeChange = viewModel::updateDraftTime,
      onSave = { activate -> viewModel.saveDraft(activate) },
    )
  }
}

@Composable
private fun AgentsBuilder(
  prompt: String,
  questions: List<String>,
  error: String?,
  isBuilding: Boolean,
  onPromptChange: (String) -> Unit,
  onBuild: () -> Unit,
  onClear: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(NovaRadius.xl),
    color = novaGlassFill(),
    border = androidx.compose.foundation.BorderStroke(1.dp, novaGlassEdge()),
  ) {
    Column(Modifier.padding(NovaSpace.xl), verticalArrangement = Arrangement.spacedBy(NovaSpace.md)) {
      Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
          Text("What should Nova do next?", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
          Spacer(Modifier.height(NovaSpace.xs))
          Text("Start with the outcome. Nova will ask for the missing pieces.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Icon(Icons.Default.AccountTree, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(24.dp))
      }
      OutlinedTextField(
        value = prompt,
        onValueChange = onPromptChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Every weekday, check my GitHub issues and send me a digest") },
        minLines = 3,
        maxLines = 5,
        shape = RoundedCornerShape(NovaRadius.md),
        colors = novaFieldColors(),
        isError = error != null,
      )
      if (questions.isNotEmpty()) {
        Text("Nova needs a few answers", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        questions.forEachIndexed { index, question ->
          Text("${index + 1}. $question", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        Text("Add the answers to the brief above, then draft again.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
      }
      error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error) }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (prompt.isNotBlank() && !isBuilding) {
          TextButton(onClick = onClear, modifier = Modifier.heightIn(min = MinTouchTarget)) {
            Text("Clear", color = scheme.onSurfaceVariant)
          }
        }
        NovaButton(
          text = "Draft agent",
          onClick = onBuild,
          enabled = prompt.trim().isNotEmpty(),
          loading = isBuilding,
        )
      }
    }
  }
}

@Composable
private fun AgentReviewCard(
  draft: AgentDraft,
  isSaving: Boolean,
  error: String?,
  onEdit: () -> Unit,
  onSaveDraft: () -> Unit,
  onActivate: () -> Unit,
  onDiscard: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  GlassPanel(Modifier.fillMaxWidth(), corner = RoundedCornerShape(NovaRadius.lg)) {
    Column(Modifier.padding(NovaSpace.xl), verticalArrangement = Arrangement.spacedBy(NovaSpace.md)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Review before you save", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
          Text("Nothing is activated until you choose to activate it.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Icon(Icons.Default.Tune, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
      }
      AgentReviewField("Goal", draft.goal.ifBlank { "Add a clear outcome" })
      AgentReviewField("Timing", draft.schedule.toLabel())
      AgentReviewField("Tools", draft.tools.joinToString("  ·  ") { agentToolLabel(it) }.ifBlank { "No tools chosen yet" })
      if (draft.description.isNotBlank()) AgentReviewField("Notes", draft.description)
      if (draft.schedule?.type != null && draft.schedule.type != "run_now") {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Default.WarningAmber, contentDescription = null, tint = scheme.tertiary, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(NovaSpace.sm))
          Text("Recurring timing is saved with the agent. Run now is guaranteed; automatic delivery depends on a connected server worker.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
      }
      error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error) }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NovaSpace.sm), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDiscard, enabled = !isSaving, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Discard", color = scheme.onSurfaceVariant) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onEdit, enabled = !isSaving, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Edit details", color = scheme.primary) }
        NovaButton(text = "Activate", onClick = onActivate, loading = isSaving)
      }
      TextButton(onClick = onSaveDraft, enabled = !isSaving, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget)) {
        Text("Save as draft", color = scheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun AgentReviewField(label: String, value: String) {
  val scheme = MaterialTheme.colorScheme
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
    Text(label, Modifier.width(84.dp), fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = novaFaint())
    Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 4, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun AgentsApprovalPanel(
  approvals: List<com.nova.app.data.ApprovalDto>,
  busyId: String?,
  onDecision: (com.nova.app.data.ApprovalDto, String) -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(NovaRadius.lg),
    color = scheme.tertiaryContainer,
  ) {
    Column(Modifier.padding(NovaSpace.xl), verticalArrangement = Arrangement.spacedBy(NovaSpace.md)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(NovaSpace.sm))
        Column(Modifier.weight(1f)) {
          Text("Your call is needed", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onTertiaryContainer)
          Text("${approvals.size} ${if (approvals.size == 1) "action is" else "actions are"} waiting", style = MaterialTheme.typography.bodySmall, color = scheme.onTertiaryContainer)
        }
      }
      approvals.forEach { approval ->
        ApprovalRow(approval, busyId == approval.id, busyId == null, onDecision)
      }
    }
  }
}

@Composable
private fun ApprovalRow(
  approval: com.nova.app.data.ApprovalDto,
  busy: Boolean,
  enabled: Boolean,
  onDecision: (com.nova.app.data.ApprovalDto, String) -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NovaSpace.sm)) {
    Text(agentActionLabel(approval.toolId), fontFamily = NovaDisplay, style = MaterialTheme.typography.titleSmall, color = scheme.onTertiaryContainer)
    Text(approval.payload.toPreview(), style = MaterialTheme.typography.bodySmall, color = scheme.onTertiaryContainer, maxLines = 5, overflow = TextOverflow.Ellipsis)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = { onDecision(approval, "reject") }, enabled = enabled && !busy, modifier = Modifier.heightIn(min = MinTouchTarget)) {
        Text("Reject", color = scheme.onTertiaryContainer)
      }
      Spacer(Modifier.width(NovaSpace.sm))
      NovaButton(text = "Approve", onClick = { onDecision(approval, "approve") }, loading = busy, enabled = enabled)
    }
  }
}

@Composable
private fun AgentsInlineError(message: String, onRetry: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  Surface(shape = RoundedCornerShape(NovaRadius.md), color = scheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.padding(horizontal = NovaSpace.lg, vertical = NovaSpace.md), verticalAlignment = Alignment.CenterVertically) {
      Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onErrorContainer)
      TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Retry", color = scheme.onErrorContainer) }
    }
  }
}

@Composable
private fun AgentsNotice(message: String, onDismiss: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  Surface(shape = RoundedCornerShape(NovaRadius.md), color = scheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.padding(start = NovaSpace.lg, top = NovaSpace.sm, bottom = NovaSpace.sm, end = NovaSpace.sm), verticalAlignment = Alignment.CenterVertically) {
      Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSecondaryContainer)
      NovaIconAction(Icons.Default.Close, "Dismiss message", onClick = onDismiss)
    }
  }
}

@Composable
private fun AgentListCard(agent: com.nova.app.data.AgentDto, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
    shape = RoundedCornerShape(NovaRadius.lg),
    color = novaGlassFill(),
    border = androidx.compose.foundation.BorderStroke(1.dp, novaGlassEdge()),
  ) {
    Row(Modifier.padding(NovaSpace.lg), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NovaSpace.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(agent.name, Modifier.weight(1f), fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
          AgentStatusText(agent.status)
        }
        Text(agent.goal, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
          listOf(agent.tools.joinToString("  ·  ") { agentToolLabel(it) }, agent.schedule.toLabel()).filter { it.isNotBlank() }.joinToString("  ·  "),
          style = MaterialTheme.typography.labelSmall,
          color = novaFaint(),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.width(NovaSpace.sm))
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = novaFaint(), modifier = Modifier.size(22.dp))
    }
  }
}

@Composable
private fun AgentStatusText(status: String) {
  val tone = novaStatusTone(status)
  Text(
    agentStatusLabel(status),
    fontFamily = NovaMono,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.SemiBold,
    color = novaToneColor(tone),
  )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AgentDetailSheet(
  agent: com.nova.app.data.AgentDto?,
  executions: List<com.nova.app.data.ExecutionDto>,
  isLoading: Boolean,
  operation: AgentOperation?,
  runMessage: String?,
  error: String?,
  onDismiss: () -> Unit,
  onRetry: (com.nova.app.data.AgentDto) -> Unit,
  onRun: (com.nova.app.data.AgentDto) -> Unit,
  onBackground: (com.nova.app.data.AgentDto) -> Unit,
  onToggleActive: (com.nova.app.data.AgentDto) -> Unit,
  onCancelRun: () -> Unit,
  onEdit: (com.nova.app.data.AgentDto) -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = novaGlassFill(),
    contentColor = scheme.onSurface,
    dragHandle = { BottomSheetDefaults.DragHandle(color = novaFaint()) },
  ) {
    if (agent == null) {
      Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        if (isLoading) CircularProgressIndicator(color = scheme.primary) else Text("Agent unavailable", color = scheme.onSurfaceVariant)
      }
    } else {
      Column(
        Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = ScreenGutter, vertical = NovaSpace.lg),
        verticalArrangement = Arrangement.spacedBy(NovaSpace.lg),
      ) {
        Row(verticalAlignment = Alignment.Top) {
          Column(Modifier.weight(1f)) {
            AgentStatusText(agent.status)
            Spacer(Modifier.height(NovaSpace.xs))
            Text(agent.name, fontFamily = NovaDisplay, style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface)
          }
          NovaIconAction(Icons.Default.Close, "Close agent details", onClick = onDismiss)
        }
        AgentReviewField("Goal", agent.goal)
        if (!agent.description.isNullOrBlank()) AgentReviewField("Notes", agent.description)
        AgentReviewField("Timing", agent.schedule.toLabel())
        AgentReviewField("Tools", agent.tools.joinToString("  ·  ") { agentToolLabel(it) }.ifBlank { "No tools configured" })
        AgentReviewField("Permissions", agent.permissions.joinToString("  ·  ") { permissionLabel(it) }.ifBlank { "No extra permissions" })
        val latest = executions.firstOrNull()
        AgentReviewField("Last run", latest?.startedAt.toRunTime())
        if (latest != null) {
          Text(latest.output?.takeIf { it.isNotBlank() } ?: latest.error ?: "No result yet", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        runMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.primary) }
        error?.let { AgentsInlineError(it) { onRetry(agent) } }
        val running = operation?.kind == AgentOperationKind.RUNNING
        val changingStatus = operation?.kind == AgentOperationKind.ACTIVATING || operation?.kind == AgentOperationKind.PAUSING
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NovaSpace.sm), verticalAlignment = Alignment.CenterVertically) {
          NovaButton(
            text = "Run now",
            onClick = { onRun(agent) },
            enabled = !running && !changingStatus,
            loading = running,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = { onBackground(agent) }, enabled = !running && !changingStatus, modifier = Modifier.heightIn(min = MinTouchTarget)) {
            Text("Run in background", color = scheme.primary)
          }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NovaSpace.sm), verticalAlignment = Alignment.CenterVertically) {
          TextButton(onClick = { onToggleActive(agent) }, enabled = !running && !changingStatus, modifier = Modifier.heightIn(min = MinTouchTarget)) {
            Text(if (agent.status.equals("active", true)) "Pause agent" else "Activate agent", color = scheme.primary)
          }
          TextButton(onClick = { onEdit(agent) }, enabled = !running && !changingStatus, modifier = Modifier.heightIn(min = MinTouchTarget)) {
            Text("Edit", color = scheme.onSurfaceVariant)
          }
          if (latest?.status in setOf("QUEUED", "RUNNING", "WAITING_FOR_APPROVAL")) {
            TextButton(onClick = onCancelRun, enabled = operation?.kind != AgentOperationKind.CANCELLING, modifier = Modifier.heightIn(min = MinTouchTarget)) {
              Text("Cancel", color = scheme.error)
            }
          }
        }
        Spacer(Modifier.height(NovaSpace.sm))
      }
    }
  }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AgentEditorSheet(
  draft: AgentDraft,
  tools: List<com.nova.app.data.ToolDto>,
  toolError: String?,
  isSaving: Boolean,
  error: String?,
  isEditing: Boolean,
  onDismiss: () -> Unit,
  onNameChange: (String) -> Unit,
  onDescriptionChange: (String) -> Unit,
  onGoalChange: (String) -> Unit,
  onInstructionsChange: (String) -> Unit,
  onToolToggle: (String) -> Unit,
  onScheduleChange: (String) -> Unit,
  onTimeChange: (String) -> Unit,
  onSave: (Boolean) -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  var scheduleType by remember(draft.schedule) { mutableStateOf(draft.schedule.toChoice()) }
  ModalBottomSheet(
    onDismissRequest = { if (!isSaving) onDismiss() },
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = novaGlassFill(),
    contentColor = scheme.onSurface,
    dragHandle = { BottomSheetDefaults.DragHandle(color = novaFaint()) },
  ) {
    Column(
      Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = ScreenGutter, vertical = NovaSpace.lg),
      verticalArrangement = Arrangement.spacedBy(NovaSpace.md),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(if (isEditing) "Edit agent" else "Review and edit", fontFamily = NovaDisplay, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
          Text("The draft stays yours until you activate it.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        NovaIconAction(Icons.Default.Close, "Close editor", enabled = !isSaving, onClick = onDismiss)
      }
      OutlinedTextField(draft.name, onNameChange, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(NovaRadius.md), colors = novaFieldColors())
      OutlinedTextField(draft.description, onDescriptionChange, label = { Text("Description (optional)") }, minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(NovaRadius.md), colors = novaFieldColors())
      OutlinedTextField(draft.goal, onGoalChange, label = { Text("Goal") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(NovaRadius.md), colors = novaFieldColors())
      OutlinedTextField(draft.instructions, onInstructionsChange, label = { Text("Instructions") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(NovaRadius.md), colors = novaFieldColors())
      Text("Tools and connections", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
      Text("Choose only what this job needs. Write tools always ask for approval before they act.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
      if (tools.isEmpty()) {
        Text(toolError ?: "The tool catalogue is still loading. You can save the draft and choose tools after it refreshes.", style = MaterialTheme.typography.bodySmall, color = if (toolError != null) scheme.error else novaFaint())
      } else {
        tools.forEach { tool ->
          val checked = tool.id in draft.tools
          Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToolToggle(tool.id) }).padding(vertical = NovaSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
            Spacer(Modifier.width(NovaSpace.sm))
            Column(Modifier.weight(1f)) {
              Text(agentToolLabel(tool.id), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
              Text(tool.description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (tool.isWrite) Text("approval", fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = scheme.tertiary)
          }
        }
      }
      Text("When should it run?", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
      Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(NovaSpace.sm),
      ) {
        listOf("run_now" to "Run only", "daily" to "Daily", "weekdays" to "Weekdays", "weekly" to "Weekly").forEach { (value, label) ->
          FilterChip(
            selected = scheduleType == value,
            onClick = {
              scheduleType = value
              onScheduleChange(value)
            },
            label = { Text(label) },
            shape = RoundedCornerShape(NovaRadius.sm),
            modifier = Modifier.heightIn(min = MinTouchTarget),
          )
        }
      }
      if (scheduleType != "run_now") {
        OutlinedTextField(
          value = draft.schedule?.time.orEmpty(),
          onValueChange = onTimeChange,
          label = { Text("Time (optional, 24-hour)") },
          placeholder = { Text("09:00") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(NovaRadius.md),
          colors = novaFieldColors(),
        )
        Text("Recurring timing is stored with this agent. Automatic delivery still depends on a connected server worker.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
      }
      error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error) }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NovaSpace.sm), verticalAlignment = Alignment.CenterVertically) {
        if (!isEditing) {
          TextButton(onClick = { onSave(false) }, enabled = !isSaving, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Save draft", color = scheme.onSurfaceVariant) }
        }
        Spacer(Modifier.weight(1f))
        NovaButton(text = if (isEditing) "Save changes" else "Save and activate", onClick = { onSave(!isEditing) }, loading = isSaving)
      }
      Spacer(Modifier.height(NovaSpace.sm))
    }
  }
}

private fun agentToolLabel(toolId: String): String = when {
  toolId.startsWith("github_") -> "GitHub"
  toolId.startsWith("gmail_") -> "Gmail"
  toolId.startsWith("calendar_") -> "Calendar"
  toolId.startsWith("drive_") -> "Google Drive"
  toolId.startsWith("browser_") -> "Browser"
  toolId.startsWith("leetcode_") -> "LeetCode"
  toolId == "web_search" -> "Web search"
  toolId.startsWith("workspace_") -> "Workspace"
  else -> toolId.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun permissionLabel(permission: String): String = when {
  permission.startsWith("github.") -> permission.removePrefix("github.").replace('_', ' ').replaceFirstChar { it.uppercase() }
  permission.startsWith("gmail.") -> "Gmail " + permission.removePrefix("gmail.").replace('_', ' ')
  permission.startsWith("calendar.") -> "Calendar " + permission.removePrefix("calendar.").replace('_', ' ')
  permission == "web.search" -> "Web search"
  else -> permission.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun agentActionLabel(toolId: String): String = when (toolId) {
  "github_create_issue" -> "Create a GitHub issue"
  "github_comment_on_issue" -> "Comment on a GitHub issue"
  "github_list_issues" -> "Review GitHub issues"
  "github_list_pull_requests" -> "Review GitHub pull requests"
  "github_get_issue" -> "Read a GitHub issue"
  "gmail_send_email" -> "Send an email"
  "calendar_create_event" -> "Create a calendar event"
  "web_search" -> "Search the web"
  else -> agentToolLabel(toolId)
}

private fun agentStatusLabel(status: String): String = when (status.lowercase()) {
  "active" -> "Live"
  "paused" -> "Paused"
  "draft" -> "Draft"
  else -> status.lowercase().replaceFirstChar { it.uppercase() }
}

private fun JsonElement?.toLabel(): String {
  val value = this as? JsonObject ?: return "Run only"
  val type = value["type"]?.jsonPrimitive?.contentOrNull ?: return "Run only"
  val time = value["time"]?.jsonPrimitive?.contentOrNull
  val frequency = value["frequency"]?.jsonPrimitive?.contentOrNull
  return when {
    type == "once" -> "One time${time?.let { " at $it" }.orEmpty()}"
    type == "recurring" && frequency == "daily" -> "Every day${time?.let { " at $it" }.orEmpty()}"
    type == "recurring" && frequency == "weekdays" -> "Every weekday${time?.let { " at $it" }.orEmpty()}"
    type == "recurring" && frequency == "weekly" -> "Weekly${time?.let { " at $it" }.orEmpty()}"
    else -> "Recurring"
  }
}

private fun AgentScheduleDto?.toLabel(): String {
  val value = this ?: return "Run only"
  val time = value.time
  return when {
    value.type == "once" -> "One time${time?.let { " at $it" }.orEmpty()}"
    value.frequency == "daily" -> "Every day${time?.let { " at $it" }.orEmpty()}"
    value.frequency == "weekdays" -> "Every weekday${time?.let { " at $it" }.orEmpty()}"
    value.frequency == "weekly" -> "Weekly${time?.let { " at $it" }.orEmpty()}"
    else -> "Recurring"
  }
}

private fun JsonElement?.toChoice(): String {
  val value = this as? JsonObject ?: return "run_now"
  val type = value["type"]?.jsonPrimitive?.contentOrNull ?: return "run_now"
  val frequency = value["frequency"]?.jsonPrimitive?.contentOrNull
  return if (type == "once") "once" else frequency ?: "run_now"
}

private fun AgentScheduleDto?.toChoice(): String {
  val value = this ?: return "run_now"
  return if (value.type == "once") "once" else value.frequency ?: "run_now"
}

private fun JsonElement?.toPreview(): String {
  val value = this as? JsonObject ?: return "Review the action details before deciding."
  val title = value["title"]?.jsonPrimitive?.contentOrNull
  val body = value["body"]?.jsonPrimitive?.contentOrNull
  val repo = value["repo"]?.jsonPrimitive?.contentOrNull
  return when {
    !title.isNullOrBlank() -> buildString { if (!repo.isNullOrBlank()) append("$repo · "); append(title); if (!body.isNullOrBlank()) append(" — $body") }
    !body.isNullOrBlank() -> body
    else -> value.toString().take(320)
  }
}

private fun String?.toRunTime(): String {
  if (this.isNullOrBlank()) return "No runs yet"
  return runCatching {
    val instant = java.time.Instant.parse(this)
    java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a", java.util.Locale.getDefault())
      .withZone(java.time.ZoneId.systemDefault())
      .format(instant)
  }.getOrDefault(this)
}

@Composable fun ActivityScreen(api: NovaApi, onBack: (() -> Unit)? = null) {
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
    Modifier.fillMaxSize().padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp + LocalBottomChrome.current),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      NovaPageHeader(
        eyebrow = "${executions.size} runs",
        title = "What happened.",
        subtitle = "Every agent run, newest first. Open one for its steps.",
        onBack = onBack,
      )
      Spacer(Modifier.height(NovaSpace.md))
      NovaFilterChips(
        options = listOf("All", "Completed", "Failed", "Running"),
        selected = filter,
        onSelect = { filter = it },
      )
    }
    if (loading && shown.isEmpty()) { item { NovaSkeletonCards(rows = 4, height = 80.dp) } }
    error?.let { e ->
      item {
        Surface(shape = RoundedCornerShape(NovaRadius.md), color = scheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
          Row(Modifier.padding(horizontal = NovaSpace.lg, vertical = NovaSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Text(e, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
            TextButton(onClick = {
              scope.launch {
                loading = true
                runCatching { api.executions() }.onSuccess { executions = it.executions }
                error = null
                loading = false
              }
            }) { Text("Retry", color = scheme.primary) }
          }
        }
      }
    }
    if (!loading && shown.isEmpty() && error == null) {
      item {
        NovaEmptyState(
          glyph = "◷",
          title = if (filter == "All") "Quiet so far." else "Nothing $filter.",
          body = if (filter == "All") {
            "Run an agent and its trace will land here."
          } else {
            "No runs with that status yet. Switch back to All to see everything."
          },
        )
      }
    }
    items(shown.size) { i ->
      val e = shown[i]
      val open = detail?.id == e.id
      NovaCard(
        onClick = {
          detail = if (open) null else e
          scope.launch { runCatching { api.execution(e.id) }.onSuccess { detail = it } }
        },
        showChevron = !open,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(e.agent?.name ?: "Agent", Modifier.weight(1f), fontFamily = NovaDisplay, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
          NovaStatusPill(text = e.status, tone = novaStatusTone(e.status))
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
            // Timeline: done steps tick in secondary, current pulses primary.
            // Detail holds the full trace; list index 0 is treated as current
            // when the run is still live, otherwise every step reads done.
            val steps = detail?.steps ?: e.steps.map { com.nova.app.data.StepDto(label = it.label) }
            steps.forEachIndexed { si, s ->
              val tone = when {
                e.status.equals("running", true) && si == 0 -> NovaTone.Accent
                e.status.equals("failed", true) && si == steps.lastIndex -> NovaTone.Danger
                else -> NovaTone.Success
              }
              NovaTimelineStep(index = si, label = s.label, state = tone)
            }
            detail?.output?.let {
              Spacer(Modifier.height(8.dp))
              Surface(shape = RoundedCornerShape(NovaRadius.sm), color = novaGlassFill(), border = androidx.compose.foundation.BorderStroke(1.dp, novaGlassEdge())) {
                Text(it, fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = scheme.onSurface, modifier = Modifier.padding(12.dp))
              }
            }
            detail?.error?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall) }
          }
      }
    }
  }
}

/**
 * All Chats (§8 list): full history with server pagination and server-side
 * search. The drawer shows only the 10 most recent; this screen pages 25 at a
 * time and keeps a single in-flight request so scrolling fast cannot stack
 * duplicate fetches. Search and archive both use the backend's `q`/`archived`
 * params, so the query never runs against a stale local list.
 */
@Composable fun AllChatsScreen(api: NovaApi, onOpen: (String, List<Message>) -> Unit, onBack: () -> Unit) {
  val scope = rememberCoroutineScope()
  val scheme = MaterialTheme.colorScheme
  var chats by remember { mutableStateOf<List<ConversationDto>>(emptyList()) }
  var total by remember { mutableStateOf(0) }
  var hasMore by remember { mutableStateOf(true) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var query by remember { mutableStateOf("") }
  var showArchived by remember { mutableStateOf(false) }
  // Delete is destructive and irreversible from the API's point of view, so the
  // row only stages it; the dialog confirms.
  var pendingDelete by remember { mutableStateOf<ConversationDto?>(null) }
  var renaming by remember { mutableStateOf<ConversationDto?>(null) }

  fun loadFirstPage() {
    loading = true; error = null; hasMore = true
    scope.launch {
      runCatching { api.conversations(q = query.ifBlank { null }, archived = showArchived, limit = 25, offset = 0) }
        .onSuccess { res ->
          chats = res.conversations
          total = res.total
          hasMore = res.hasMore
        }
        .onFailure { error = "Unable to load chats"; hasMore = false }
      loading = false
    }
  }

  fun loadMore() {
    if (loading || !hasMore) return
    loading = true; error = null
    scope.launch {
      runCatching { api.conversations(q = query.ifBlank { null }, archived = showArchived, limit = 25, offset = chats.size) }
        .onSuccess { res ->
          val seen = chats.map { it.id }.toSet()
          chats = chats + res.conversations.filter { it.id !in seen }
          total = res.total
          hasMore = res.hasMore
        }
        .onFailure { error = "Unable to load chats"; hasMore = false }
      loading = false
    }
  }

  // Debounced: typing must not fire one request per keystroke.
  LaunchedEffect(query, showArchived) {
    kotlinx.coroutines.delay(if (query.isBlank()) 0 else 250)
    loadFirstPage()
  }

  fun open(c: ConversationDto) {
    scope.launch {
      runCatching { api.conversation(c.id) }
        .onSuccess { onOpen(c.id, it.messages.map { m -> Message(m.id, m.role, m.content, m.attachments, ui = m.ui, createdAt = m.createdAt) }) }
        .onFailure { error = "Could not open chat" }
    }
  }

  LazyColumn(
    Modifier.fillMaxSize().padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp + LocalBottomChrome.current),
  ) {
    item {
      NovaPageHeader(
        eyebrow = if (query.isBlank()) "$total conversations" else "$total matching",
        title = "All chats",
        subtitle = "Search titles, rename, or tuck old threads away.",
        onBack = onBack,
      )
      Spacer(Modifier.height(NovaSpace.md))
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(NovaRadius.md),
        placeholder = { Text("Search conversations", color = scheme.onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
          if (query.isNotEmpty()) {
            NovaIconAction(Icons.Default.Close, "Clear search") { query = "" }
          }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        colors = novaFieldColors(),
      )
      Spacer(Modifier.height(NovaSpace.md))
      NovaFilterChips(
        options = listOf("Current", "Archived"),
        selected = if (showArchived) "Archived" else "Current",
        onSelect = { showArchived = it == "Archived" },
      )
    }
    error?.let { e ->
      item {
        Surface(
          shape = RoundedCornerShape(NovaRadius.md),
          color = scheme.errorContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(Modifier.padding(horizontal = NovaSpace.lg, vertical = NovaSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Text(e, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
            TextButton(onClick = { loadFirstPage() }) { Text("Retry", color = scheme.primary) }
          }
        }
      }
    }
    // Skeleton rows hold the list's shape while loading, so nothing jumps.
    if (loading && chats.isEmpty()) {
      item { NovaSkeletonCards(rows = 5, height = 64.dp) }
    }
    if (!loading && chats.isEmpty() && error == null) {
      item {
        NovaEmptyState(
          glyph = if (query.isBlank()) null else "“$query”",
          title = if (query.isBlank() && !showArchived) "No chats yet." else "Nothing matches.",
          body = when {
            query.isNotBlank() -> "No conversation title contains that word. Try a shorter one."
            showArchived -> "Nothing archived yet. Archived chats live here instead of your main list."
            else -> "Start one from the Chat tab and it will show up here."
          },
        )
      }
    }
    items(chats.size) { i ->
      val c = chats[i]
      var menu by remember(c.id) { mutableStateOf(false) }
      NovaCard(
        modifier = Modifier.padding(vertical = 2.dp),
        onClick = { open(c) },
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(c.title.ifBlank { "New chat" }, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            val when_ = novaRelativeTime(c.updatedAt)
            if (when_ != null) {
              Spacer(Modifier.height(2.dp))
              Text(when_, fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = novaFaint())
            }
          }
          Box {
            NovaIconAction(Icons.Default.MoreVert, "Chat options for ${c.title.ifBlank { "this chat" }}") { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = novaGlassFill()) {
              DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = { menu = false; renaming = c },
              )
              DropdownMenuItem(
                text = { Text(if (c.archived) "Unarchive" else "Archive") },
                leadingIcon = { Icon(if (c.archived) Icons.Default.Unarchive else Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                  menu = false
                  scope.launch {
                    runCatching { api.patchConversation(c.id, com.nova.app.data.PatchConversationRequest(archived = !c.archived)) }
                      .onSuccess { chats = chats.filter { it.id != c.id }; total = (total - 1).coerceAtLeast(0) }
                      .onFailure { error = "Could not archive that chat" }
                  }
                },
              )
              DropdownMenuItem(
                text = { Text("Delete", color = scheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = scheme.error, modifier = Modifier.size(18.dp)) },
                onClick = { menu = false; pendingDelete = c },
              )
            }
          }
        }
      }
      // Paginate near the end instead of a manual "load more" button (§8).
      if (i == chats.lastIndex && hasMore) {
        LaunchedEffect(chats.size) { loadMore() }
      }
    }
    if (loading && chats.isNotEmpty()) {
      item {
        Row(Modifier.fillMaxWidth().padding(NovaSpace.lg), horizontalArrangement = Arrangement.Center) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = scheme.onSurfaceVariant)
        }
      }
    }
  }

  // Destructive action: confirm first. Deleting a conversation cannot be undone
  // from the API, so an accidental tap must not be enough.
  pendingDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      containerColor = novaGlassFill(),
      title = { Text("Delete chat?", fontFamily = NovaDisplay) },
      text = {
        Text(
          "\"${target.title.ifBlank { "New chat" }}\" and its messages will be removed permanently.",
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          val id = target.id
          pendingDelete = null
          scope.launch {
            runCatching { api.deleteConversation(id) }
              .onSuccess {
                chats = chats.filter { it.id != id }
                total = (total - 1).coerceAtLeast(0)
              }
              .onFailure { error = "Could not delete that chat" }
          }
        }) { Text("Delete", color = scheme.error, fontWeight = FontWeight.Bold) }
      },
      dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep", color = scheme.onSurfaceVariant) } },
    )
  }

  renaming?.let { target ->
    var title by remember(target.id) { mutableStateOf(target.title) }
    AlertDialog(
      onDismissRequest = { renaming = null },
      containerColor = novaGlassFill(),
      title = { Text("Rename chat", fontFamily = NovaDisplay) },
      text = {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          singleLine = true,
          label = { Text("Title") },
          shape = RoundedCornerShape(NovaRadius.md),
          colors = novaFieldColors(),
        )
      },
      confirmButton = {
        TextButton(
          enabled = title.isNotBlank(),
          onClick = {
            val id = target.id
            val next = title.trim()
            renaming = null
            scope.launch {
              runCatching { api.patchConversation(id, com.nova.app.data.PatchConversationRequest(title = next)) }
                .onSuccess { chats = chats.map { if (it.id == id) it.copy(title = next) else it } }
                .onFailure { error = "Could not rename that chat" }
            }
          },
        ) { Text("Save", color = scheme.primary, fontWeight = FontWeight.Bold) }
      },
      dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", color = scheme.onSurfaceVariant) } },
    )
  }
}

/** §38: map an OAuth deep-link error code to a line the user can act on. */
private fun oauthErrorText(code: String): String = when (code) {
  "access_denied" -> "Sign-in cancelled. Nothing was connected."
  "state_expired" -> "Sign-in took too long. Please try again."
  "invalid_state" -> "Sign-in session not recognised. Please try again."
  else -> "Connection failed: ${code.replace('_', ' ')}. Please try again."
}

@Composable fun ConnectionsScreen(api: NovaApi, session: SessionToken, onBack: () -> Unit = {}) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val scheme = MaterialTheme.colorScheme
  var connections by remember { mutableStateOf<List<com.nova.app.data.ConnectionDto>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var connecting by remember { mutableStateOf<String?>(null) }
  // Backend-owned catalogue (§38): id, blurb and an honest state — connected /
  // needs_reconnect / available / not_configured / not_built. No dead buttons.
  var providers by remember { mutableStateOf<List<com.nova.app.data.ProviderDto>>(emptyList()) }
  val live = providers.count { it.state == "connected" }
  val glyphOf = mapOf("github" to "G", "gmail" to "M", "calendar" to "C", "drive" to "D", "vercel" to "V", "supabase" to "S", "slack" to "S", "notion" to "N", "x" to "X")
  // F2 token-connect dialogs (Vercel/Supabase have no OAuth round-trip).
  var vercelDialog by remember { mutableStateOf(false) }
  var supabaseDialog by remember { mutableStateOf(false) }
  // §38: one-shot failure code carried by the OAuth deep link return.
  var oauthError by remember { mutableStateOf<String?>(null) }

  fun refresh() {
    scope.launch {
      loading = true; error = null
      runCatching { api.connections() }
        .onSuccess { connections = it.connections }
        .onFailure { error = "Unable to load connections" }
      runCatching { api.connectionProviders() }
        .onSuccess { providers = it.providers }
        .onFailure { error = "Unable to load providers" }
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

  // Consume the OAuth deep-link error once; kept apart from `error` so a
  // concurrent refresh() (which clears `error`) cannot wipe it.
  val oauthNotice = OAuthDeepLink.notice.value
  LaunchedEffect(oauthNotice) {
    if (oauthNotice != null) {
      OAuthDeepLink.notice.value = null
      oauthError = oauthNotice
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp + LocalBottomChrome.current),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      NovaPageHeader(
        eyebrow = "$live of ${providers.size} live",
        title = "Tied together.",
        subtitle = "Agents borrow these accounts. Nothing runs without your say.",
        onBack = onBack,
      )
    }
    if (loading && providers.isEmpty()) { item { NovaSkeletonCards(rows = 5, height = 88.dp) } }
    error?.let { e ->
      item { Text(e, color = scheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
    oauthError?.let { e ->
      item { Text(oauthErrorText(e), color = scheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
    items(providers.size) { i ->
      val p = providers[i]
      val connected = p.state == "connected"
      val needsReconnect = p.state == "needs_reconnect"
      val isConnecting = connecting == p.id
      GlassPanel(corner = RoundedCornerShape(NovaRadius.xl)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(
            glyphOf[p.id] ?: "?",
            fontFamily = NovaDisplay, style = MaterialTheme.typography.headlineMedium,
            color = when {
              connected -> scheme.primary
              needsReconnect -> scheme.tertiary
              else -> scheme.onSurfaceVariant
            },
            modifier = Modifier.width(34.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(p.name, fontFamily = NovaDisplay, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(p.blurb, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
              when {
                connected && p.login != null -> "Connected as ${p.login}"
                connected -> "Connected"
                needsReconnect -> "Session expired. Reconnect"
                p.state == "not_configured" -> "Not set up on the server yet"
                p.state == "not_built" -> "Coming in a later phase"
                else -> "Not connected"
              },
              fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall,
              color = when {
                connected -> scheme.secondary
                needsReconnect -> scheme.tertiary
                else -> scheme.onSurfaceVariant
              },
            )
          }
          Spacer(Modifier.width(8.dp))
          if (connected) {
            // §38: disconnect removes the connection row server-side
            TextButton(onClick = {
              scope.launch {
                when (p.id) {
                  "github" -> runCatching { api.disconnectGitHub() }.onSuccess { refresh() }
                  "gmail" -> runCatching { api.disconnectGmail() }.onSuccess { refresh() }
                  "calendar" -> runCatching { api.disconnectCalendar() }.onSuccess { refresh() }
                  "drive" -> runCatching { api.disconnectDrive() }.onSuccess { refresh() }
                  "vercel" -> runCatching { api.disconnectVercel() }.onSuccess { refresh() }
                  "supabase" -> runCatching { api.disconnectSupabase() }.onSuccess { refresh() }
                }
              }
            }) {
              Text("Disconnect", color = scheme.error, style = MaterialTheme.typography.labelMedium)
            }
          } else if (needsReconnect || (p.state == "available" && p.id in setOf("github", "gmail", "calendar", "drive", "vercel", "supabase"))) {
            // §38: OAuth — redirect user to the provider to authorize.
            // Backend callback redirects to nova://connections/{provider}/status;
            // the manifest intent-filter + MainActivity deliver that deep link
            // back here, and ON_RESUME refreshes state on return.
            // Vercel/Supabase are token-based: open a paste-a-key dialog instead.
            TextButton(
              enabled = !isConnecting,
              onClick = {
                when (p.id) {
                  "vercel" -> vercelDialog = true
                  "supabase" -> supabaseDialog = true
                  else -> scope.launch {
                    connecting = p.id
                    when (p.id) {
                      "github" -> runCatching { api.githubAuthorize() }
                        .onSuccess { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.url))) }
                        .onFailure { error = "Could not start GitHub connection. Check backend config." }
                      "calendar" -> runCatching { api.calendarAuthorize() }
                        .onSuccess { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.url))) }
                        .onFailure { error = "Could not start Calendar connection. Check backend config." }
                      "drive" -> runCatching { api.driveAuthorize() }
                        .onSuccess { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.url))) }
                        .onFailure { error = "Could not start Drive connection. Check backend config." }
                      else -> runCatching { api.gmailAuthorize() }
                        .onSuccess { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.url))) }
                        .onFailure { error = "Could not start Gmail connection. Check backend config." }
                    }
                    connecting = null
                  }
                }
              },
            ) {
              if (isConnecting) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = scheme.primary)
              } else {
                Text(if (needsReconnect) "Reconnect" else "Connect", color = scheme.primary, fontWeight = FontWeight.Bold)
              }
            }
          } else {
            // Backend said this provider is not offered — say so, honestly (§19).
            TextButton(enabled = false, onClick = {}) {
              Text("Soon", color = novaFaint(), style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
    }
    item {
      // §18 LeetCode — server-keyed, no connection needed. Always ready in chat.
      GlassPanel(corner = RoundedCornerShape(NovaRadius.xl)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
          Text("LC", fontFamily = NovaDisplay, style = MaterialTheme.typography.headlineMedium, color = scheme.primary, modifier = Modifier.width(34.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text("LeetCode", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text("Profiles, solved counts, contests, daily problem. Use @leetcode in chat.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text("Built in, no connection needed", fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = scheme.secondary)
          }
        }
      }
    }
    item {
      Text("Keys stay on the server. Revoke anytime from the source.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    }
    // F2 token-connect dialogs. Rendered as lazy items so they share the screen scope.
    item {
      if (vercelDialog) {
        var token by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var formError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
          onDismissRequest = { if (!busy) vercelDialog = false },
          containerColor = novaGlassFill(),
          title = { Text("Connect Vercel", fontFamily = NovaDisplay) },
          text = {
            Column {
              Text("Paste a Vercel token (Account Settings → Tokens). It is stored encrypted on the server.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
              Spacer(Modifier.height(10.dp))
              OutlinedTextField(token, { token = it }, label = { Text("Token") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(NovaRadius.md), colors = novaFieldColors())
              formError?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            }
          },
          confirmButton = {
            TextButton(enabled = token.isNotBlank() && !busy, onClick = {
              scope.launch {
                busy = true; formError = null
                runCatching { api.connectVercel(com.nova.app.data.ConnectTokenRequest(token.trim())) }
                  .onSuccess { vercelDialog = false; refresh() }
                  .onFailure { formError = "Could not save the token. Check it and retry." }
                busy = false
              }
            }) { Text("Connect", color = scheme.primary, fontWeight = FontWeight.Bold) }
          },
          dismissButton = { TextButton(enabled = !busy, onClick = { vercelDialog = false }) { Text("Cancel", color = scheme.onSurfaceVariant) } },
        )
      }
    }
    item {
      if (supabaseDialog) {
        var ref by remember { mutableStateOf("") }
        var key by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var formError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
          onDismissRequest = { if (!busy) supabaseDialog = false },
          containerColor = novaGlassFill(),
          title = { Text("Connect Supabase", fontFamily = NovaDisplay) },
          text = {
            Column {
              Text("Project ref plus a key (Project Settings → API). Stored encrypted on the server.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
              Spacer(Modifier.height(10.dp))
              OutlinedTextField(ref, { ref = it }, label = { Text("Project ref") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(NovaRadius.md), colors = novaFieldColors())
              Spacer(Modifier.height(8.dp))
              OutlinedTextField(key, { key = it }, label = { Text("Key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(NovaRadius.md), colors = novaFieldColors())
              formError?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            }
          },
          confirmButton = {
            TextButton(enabled = ref.isNotBlank() && key.isNotBlank() && !busy, onClick = {
              scope.launch {
                busy = true; formError = null
                runCatching { api.connectSupabase(com.nova.app.data.ConnectSupabaseRequest(ref.trim(), key.trim())) }
                  .onSuccess { supabaseDialog = false; refresh() }
                  .onFailure { formError = "Could not save the key. Check both fields and retry." }
                busy = false
              }
            }) { Text("Connect", color = scheme.primary, fontWeight = FontWeight.Bold) }
          },
          dismissButton = { TextButton(enabled = !busy, onClick = { supabaseDialog = false }) { Text("Cancel", color = scheme.onSurfaceVariant) } },
        )
      }
    }
  }
}

@Composable
private fun SettingsSectionLabel(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    modifier = modifier.padding(start = NovaSpace.xs),
    fontSize = 14.sp,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(NovaRadius.lg),
    color = novaGlassFill(),
    border = androidx.compose.foundation.BorderStroke(1.dp, novaGlassEdge()),
  ) {
    Column(content = content)
  }
}

@Composable
private fun SettingsGroupDivider() {
  Box(
    Modifier
      .fillMaxWidth()
      .padding(start = 60.dp)
      .height(1.dp)
      .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
  )
}

@Composable
private fun SettingsGroupRow(
  label: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  onClick: (() -> Unit)? = null,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  labelColor: Color = MaterialTheme.colorScheme.onSurface,
) {
  val content: @Composable () -> Unit = {
    Row(
      Modifier
        .fillMaxWidth()
        .heightIn(min = 60.dp)
        .padding(horizontal = NovaSpace.lg, vertical = NovaSpace.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      leading?.let {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { it() }
        Spacer(Modifier.width(NovaSpace.md))
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          label,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          color = labelColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
          Text(
            it,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      trailing?.let {
        Spacer(Modifier.width(NovaSpace.md))
        it()
      }
    }
  }
  if (onClick != null) {
    Surface(
      onClick = onClick,
      modifier = modifier.fillMaxWidth().semantics { role = Role.Button },
      shape = RoundedCornerShape(0.dp),
      color = Color.Transparent,
      content = content,
    )
  } else {
    Box(modifier.fillMaxWidth()) { content() }
  }
}

@Composable
fun SettingsScreen(session: SessionToken, onLogout: () -> Unit, onBack: (() -> Unit)? = null, onPreferencesChanged: () -> Unit = {}, onConnectionsClick: () -> Unit = {}) {
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
  val hasEmail = rawEmail?.contains("@") == true
  val accountEmail = if (hasEmail) rawEmail.orEmpty() else "Signed in"
  val initial = rawEmail?.trim()?.takeIf { it.isNotEmpty() }?.take(1)?.uppercase() ?: "N"
  val scheme = MaterialTheme.colorScheme
  var showSignOutConfirm by remember { mutableStateOf(false) }
  var themeMenu by remember { mutableStateOf(false) }
  var accentMenu by remember { mutableStateOf(false) }

  val currentMode = NovaThemeMode.entries.find {
    it.name.equals(AccentPreferences.getThemeMode(context), ignoreCase = true)
  } ?: NovaThemeMode.SYSTEM
  val modeLabels = listOf(
    NovaThemeMode.SYSTEM to "System (Default)",
    NovaThemeMode.LIGHT to "Light",
    NovaThemeMode.DARK to "Dark",
  )
  val currentAccentName = AccentPreferences.get(context)
  val currentAccent = NovaAccent.entries.find { it.name.equals(currentAccentName, ignoreCase = true) } ?: NovaAccent.PURPLE
  val bottomChrome = LocalBottomChrome.current

  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
      if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && Build.VERSION.SDK_INT >= 33) {
        granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Box(Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(start = NovaSpace.lg, end = NovaSpace.lg, bottom = bottomChrome),
      contentPadding = PaddingValues(top = NovaSpace.sm, bottom = NovaSpace.xxl),
      verticalArrangement = Arrangement.spacedBy(NovaSpace.sm),
    ) {
    item {
      Row(
        Modifier.fillMaxWidth().padding(top = NovaSpace.xs, bottom = NovaSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (onBack != null) {
          SpecCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
        } else {
          Spacer(Modifier.size(MinTouchTarget))
        }
        Column(
          Modifier.weight(1f),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
          Spacer(Modifier.height(2.dp))
          Text("Account and preferences", fontSize = 14.sp, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.size(MinTouchTarget))
      }
    }

    item { SettingsSectionLabel("Account", Modifier.padding(top = NovaSpace.sm)) }
    item {
      SettingsGroup {
        SettingsGroupRow(
          label = accountEmail,
          subtitle = if (hasEmail) "Your Nova account is active" else "Authenticated session",
          leading = {
            Surface(
              modifier = Modifier.size(32.dp),
              shape = CircleShape,
              color = scheme.primaryContainer,
              contentColor = scheme.onPrimaryContainer,
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(initial, fontSize = 16.sp, fontWeight = FontWeight.Bold)
              }
            }
          },
          trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Check, contentDescription = null, tint = scheme.secondary, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Active", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.secondary)
            }
          },
        )
      }
    }

    item { SettingsSectionLabel("Accounts", Modifier.padding(top = NovaSpace.sm)) }
    item {
      SettingsGroup {
        SettingsGroupRow(
          label = "Connected accounts",
          subtitle = "GitHub, Gmail, Calendar, Drive and more",
          onClick = onConnectionsClick,
          leading = {
            Icon(Icons.Default.Link, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
          },
          trailing = {
            Icon(
              Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = null,
              tint = scheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp),
            )
          },
        )
      }
    }

    item { SettingsSectionLabel("Notifications", Modifier.padding(top = NovaSpace.sm)) }
    item {
      SettingsGroup {
        SettingsGroupRow(
          label = "Agent notifications",
          subtitle = if (granted) "Runs report back when you return" else "Runs stay silent",
          leading = {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
          },
          trailing = {
            if (Build.VERSION.SDK_INT >= 33) {
              Switch(
                checked = granted,
                onCheckedChange = { on ->
                  if (on) request.launch(Manifest.permission.POST_NOTIFICATIONS)
                  else {
                    granted = false
                    context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                      putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    })
                  }
                },
              )
            } else {
              Text("System", fontSize = 14.sp, color = scheme.onSurfaceVariant)
            }
          },
        )
        SettingsGroupDivider()
        SettingsGroupRow(
          label = "Read responses aloud",
          subtitle = "Nova voice, on tap of any answer",
          leading = {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
          },
          trailing = {
            Text("On tap", fontSize = 14.sp, color = scheme.onSurfaceVariant)
          },
        )
      }
    }

    item { SettingsSectionLabel("Appearance", Modifier.padding(top = NovaSpace.sm)) }
    item {
      SettingsGroup {
        Box {
          SettingsGroupRow(
            label = "Theme",
            subtitle = modeLabels.first { it.first == currentMode }.second,
            onClick = { themeMenu = true },
            leading = {
              Icon(Icons.Default.DarkMode, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            },
            trailing = {
              Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
              )
            },
          )
          DropdownMenu(
            expanded = themeMenu,
            onDismissRequest = { themeMenu = false },
            containerColor = novaGlassFill(),
          ) {
            modeLabels.forEach { (mode, label) ->
              DropdownMenuItem(
                text = { Text(label, color = if (mode == currentMode) scheme.primary else scheme.onSurface) },
                trailingIcon = {
                  if (mode == currentMode) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = scheme.primary, modifier = Modifier.size(20.dp))
                  }
                },
                onClick = {
                  themeMenu = false
                  AccentPreferences.setThemeMode(context, mode.name)
                  onPreferencesChanged()
                },
              )
            }
          }
        }
        SettingsGroupDivider()
        Box {
          SettingsGroupRow(
            label = "Accent color",
            subtitle = currentAccent.label,
            onClick = { accentMenu = true },
            leading = {
              Box(Modifier.size(16.dp).clip(CircleShape).background(novaAccentSwatch(currentAccent)))
            },
            trailing = {
              Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
              )
            },
          )
          DropdownMenu(
            expanded = accentMenu,
            onDismissRequest = { accentMenu = false },
            containerColor = novaGlassFill(),
          ) {
            NovaAccent.entries.forEach { accent ->
              DropdownMenuItem(
                text = {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(novaAccentSwatch(accent)))
                    Spacer(Modifier.width(10.dp))
                    Text(
                      accent.label,
                      color = if (accent == currentAccent) scheme.primary else scheme.onSurface,
                    )
                  }
                },
                trailingIcon = {
                  if (accent == currentAccent) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = scheme.primary, modifier = Modifier.size(20.dp))
                  }
                },
                onClick = {
                  accentMenu = false
                  AccentPreferences.set(context, accent.name)
                  onPreferencesChanged()
                },
              )
            }
          }
        }
      }
    }

    item { SettingsSectionLabel("About", Modifier.padding(top = NovaSpace.sm)) }
    item {
      SettingsGroup {
        SettingsGroupRow(
          label = "Nova",
          subtitle = "v0.1.0 · Voice, agents, and the tools you use",
          leading = {
            Icon(Icons.Default.Info, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
          },
        )
      }
    }

    item {
      SettingsGroup {
        SettingsGroupRow(
          label = "Log out",
          onClick = { showSignOutConfirm = true },
          labelColor = scheme.error,
          leading = {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = scheme.error, modifier = Modifier.size(22.dp))
          },
        )
      }
    }
  }
  }

  if (showSignOutConfirm) {
    AlertDialog(
      onDismissRequest = { showSignOutConfirm = false },
      containerColor = novaGlassFill(),
      title = { Text("Log out of Nova?") },
      text = { Text("You can sign back in anytime. Your conversations remain on your account.", color = scheme.onSurfaceVariant) },
      confirmButton = {
        TextButton(onClick = { showSignOutConfirm = false; onLogout() }) {
          Text("Log out", color = scheme.error, fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel", color = scheme.onSurfaceVariant) }
      },
    )
  }
}

