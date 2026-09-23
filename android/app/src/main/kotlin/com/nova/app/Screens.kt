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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.nova.app.data.ConversationDto
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
    lastUserText = text
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
      client.stream(cid, text, model, attachmentIds)
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
    _conversationId.value = id
    lastUserText = null
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
fun ChatScreen(api: NovaApi, session: SessionToken, onSettingsClick: () -> Unit = {}, onConnectionsClick: () -> Unit = {}, onAgentsClick: () -> Unit = {}, onActivityClick: () -> Unit = {}, onAllChatsClick: () -> Unit = {}, vm: ChatViewModel = viewModel()) {
  LaunchedEffect(session) { vm.configureSession(session) }
  LaunchedEffect(api) { vm.configureApi(api) }
  var conversations by remember { mutableStateOf<List<ConversationDto>>(emptyList()) }
  var convTotal by remember { mutableStateOf(0) }
  var convHasMore by remember { mutableStateOf(false) }
  var convLoading by remember { mutableStateOf(false) }
  var models by remember { mutableStateOf<List<ModelDto>>(emptyList()) }
  var model by remember { mutableStateOf<String?>(null) }
  var editIndex by remember { mutableStateOf<Int?>(null) }
  var offline by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val scheme = MaterialTheme.colorScheme

  suspend fun loadConversations(reset: Boolean = false) {
    if (convLoading) return
    convLoading = true
    runCatching {
      val offset = if (reset) 0 else conversations.size
      // Sidebar shows only the 10 most recent chats; the full history lives on
      // the All Chats screen (§8 list) so the drawer stays scannable.
      val res = api.conversations(limit = 10, offset = offset)
      conversations = if (reset) res.conversations else conversations + res.conversations
      convTotal = res.total
      convHasMore = res.hasMore
      offline = false
    }.onFailure {
      offline = true
      convHasMore = false
    }
    convLoading = false
  }
  LaunchedEffect(Unit) {
    runCatching { models = api.models().models }
    loadConversations(reset = true)
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
  // A lazily created chat (first send) or a brand-new "New chat" is absent
  // from the drawer list until it is refreshed.
  LaunchedEffect(activeConversationId) {
    if (activeConversationId != null && conversations.none { it.id == activeConversationId }) {
      loadConversations(reset = true)
    }
  }
  var input by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(DrawerValue.Closed)
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
          loadConversations(reset = true)
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

  fun shortModel(id: String): String = id.removePrefix("gemini-").removePrefix("models/").take(18)

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
                convTotal = (convTotal - 1).coerceAtLeast(0)
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
          DrawerMenuRow(Icons.Default.Folder, "Projects") { scope.launch { drawerState.close() }; onAgentsClick() }
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
          // Load more trigger
          if (convHasMore && !convLoading) {
            item {
              LaunchedEffect(Unit) { loadConversations() }
              Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = scheme.onSurfaceVariant)
              }
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
              if (models.isNotEmpty()) {
                models.forEach { m ->
                  DropdownMenuItem(
                    text = { Text(m.id, fontFamily = NovaMono, style = MaterialTheme.typography.bodySmall) },
                    trailingIcon = if (model == m.id) {
                      { Icon(Icons.Default.Check, contentDescription = "Selected model", modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = {
                      model = m.id; vm.model = m.id; topMenu = false
                    },
                  )
                }
              }
              DropdownMenuItem(
                text = { Text("Search chats") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = { topMenu = false; onAllChatsClick() },
              )
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

            // Collapsible reasoning / thinking section
            if (sm.reasoning.isNotEmpty()) {
              Spacer(Modifier.height(6.dp))
              var reasoningExpanded by remember { mutableStateOf(false) }
              Surface(
                onClick = { reasoningExpanded = !reasoningExpanded },
                shape = RoundedCornerShape(NovaRadius.row),
                color = novaGlassFill(),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      Icons.Default.AccountTree,
                      contentDescription = null,
                      modifier = Modifier.size(14.dp),
                      tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                      "Thinking",
                      style = MaterialTheme.typography.labelMedium,
                      fontFamily = NovaDisplay,
                      color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                      if (reasoningExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                      tint = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                  }
                  if (reasoningExpanded) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                      sm.reasoning,
                      style = MaterialTheme.typography.bodySmall,
                      color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                      lineHeight = 18.sp,
                    )
                  }
                }
              }
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
    // so message text can never read through it.
    GlassPanel(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 8.dp),
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
          // Composer pill: + · placeholder · mic · accent action. Transparent
          // field so the glass panel behind shows through.
          Surface(
            modifier = Modifier.weight(1f),
            shape = CircleShape,
            color = Color.Transparent,
          ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
              Box {
                IconButton(onClick = { showAttachMenu = true }, enabled = !uploading && !streaming, modifier = Modifier.size(48.dp)) {
                  Icon(Icons.Default.Add, contentDescription = "Attach", tint = scheme.onSurface, modifier = Modifier.size(24.dp))
                }
              }
              if (micGranted && voice.available) {
                IconButton(onClick = { voice.start() }, modifier = Modifier.size(48.dp)) {
                  Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = scheme.onSurface, modifier = Modifier.size(24.dp))
                }
              } else {
                IconButton(onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.size(48.dp)) {
                  Icon(Icons.Default.Mic, contentDescription = "Enable voice input", tint = scheme.onSurface, modifier = Modifier.size(24.dp))
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
                maxLines = 5,
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
  }
}
}

@Composable fun AgentsScreen(api: NovaApi, onBack: (() -> Unit)? = null) {
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
    Modifier.fillMaxSize().padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp + LocalBottomChrome.current),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      NovaPageHeader(
        eyebrow = "$live live",
        title = "Agents at work.",
        subtitle = "Say what should happen on its own. Nova asks what is missing, then hands you the plan to keep.",
        onBack = onBack,
      )
    }
    // Builder island: the one composed object on this screen.
    item {
      GlassPanel(corner = RoundedCornerShape(NovaRadius.xl)) {
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
            shape = RoundedCornerShape(NovaRadius.md),
            colors = OutlinedTextFieldDefaults.colors(
              unfocusedContainerColor = novaGlassFill(), focusedContainerColor = novaGlassFill(),
              unfocusedTextColor = scheme.onSurface, focusedTextColor = scheme.onSurface,
              cursorColor = scheme.primary, unfocusedBorderColor = scheme.outlineVariant, focusedBorderColor = scheme.primary,
            ),
          )
          Spacer(Modifier.height(12.dp))
          NovaButton(
            text = "Draft the agent",
            onClick = {
              scope.launch {
                building = true; error = null; built = null
                runCatching { api.buildAgent(com.nova.app.data.BuildAgentRequest(nl)) }
                  .onSuccess { built = it }
                  .onFailure { error = "Builder failed. Check connection and retry." }
                building = false
              }
            },
            enabled = nl.isNotBlank(),
            loading = building,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
    built?.let { obj ->
      item {
        val questions = obj["questions"]?.let { runCatching { it.jsonArray.map { q -> q.jsonPrimitive.content } }.getOrNull() }
        GlassPanel(Modifier.fillMaxWidth(), corner = RoundedCornerShape(NovaRadius.lg)) {
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
                  // F5: never force-unwrap model output — a malformed draft shows
                  // an error, not a crash.
                  val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                  val goal = obj["goal"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                  val tools = runCatching { obj["tools"]?.jsonArray?.map { it.jsonPrimitive.content } }.getOrNull()?.filter { it.isNotBlank() }
                  if (name == null || goal == null || tools.isNullOrEmpty()) {
                    error = "That draft is incomplete. Describe the job with a little more detail and draft again."
                  } else {
                    runCatching {
                      api.createAgent(
                        com.nova.app.data.CreateAgentRequest(
                          name = name,
                          goal = goal,
                          instructions = obj["instructions"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: goal,
                          tools = tools,
                        ),
                      )
                    }.onSuccess { created ->
                      built = null; nl = ""
                      runCatching { api.activateAgent(created.id) }
                      refresh()
                    }.onFailure { error = "Save failed. The draft named a tool that does not exist yet." }
                  }
                }
              }, shape = CircleShape) { Text("Keep this setup") }
            }
          }
        }
      }
    }
    if (approvals.isNotEmpty()) {
      item {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(NovaRadius.lg), color = scheme.tertiary.copy(alpha = 0.16f)) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NovaEyebrow("Approval needed")
            Spacer(Modifier.height(2.dp))
            Text("Needs your call", fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text("${approvals.size} ${if (approvals.size == 1) "action" else "actions"} waiting", fontFamily = NovaMono, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            approvals.forEach { ap ->
              NovaApprovalRow(
                toolId = ap.toolId,
                onAllow = {
                  scope.launch {
                    runCatching { api.decideApproval(ap.id, com.nova.app.data.DecideApprovalRequest("approve")) }
                    refresh()
                  }
                },
                onDismiss = {
                  scope.launch {
                    runCatching { api.decideApproval(ap.id, com.nova.app.data.DecideApprovalRequest("reject")) }
                    refresh()
                  }
                },
              )
            }
          }
        }
      }
    }
    // Skeleton rows instead of a hairline progress bar: the list keeps its
    // shape, so arriving agents do not shove the builder card around.
    if (loading && agents.isEmpty()) { item { NovaSkeletonCards(rows = 3, height = 84.dp) } }
    error?.let { e ->
      item {
        Surface(shape = RoundedCornerShape(NovaRadius.md), color = scheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
          Row(Modifier.padding(horizontal = NovaSpace.lg, vertical = NovaSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Text(e, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
            TextButton(onClick = { refresh() }) { Text("Retry", color = scheme.primary) }
          }
        }
      }
    }
    if (!loading && agents.isEmpty() && error == null) {
      item {
        NovaEmptyState(
          glyph = "◎",
          title = "No agents yet.",
          body = "The first one takes about a minute: describe it above.",
        )
      }
    }
    items(agents.size) { i ->
      val a = agents[i]
      val open = selected?.id == a.id
      NovaCard(
        onClick = {
          selected = if (open) null else a; runOutput = null
          scope.launch { runCatching { api.agent(a.id) }.onSuccess { selected = it } }
        },
        showChevron = !open,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(a.name, Modifier.weight(1f), fontFamily = NovaDisplay, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
          NovaStatusPill(
            text = when (a.status) { "active" -> "Live"; "paused" -> "Paused"; else -> "Draft" },
            tone = novaStatusTone(a.status),
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
              NovaButton(
                text = if (running) "Running…" else "Run now",
                onClick = {
                  scope.launch {
                    running = true; runOutput = null
                    runCatching { api.runAgent(d.id) }
                      .onSuccess { runOutput = "[${it.status}] ${it.output ?: ""}" }
                      .onFailure { runOutput = "[failed] Run failed. Retry shortly." }
                    running = false; refresh()
                  }
                },
                enabled = !running,
                loading = running,
              )
              Spacer(Modifier.width(NovaSpace.xs))
              // §56 background=true: server keeps running after the response; poll the
              // execution row until it reaches a terminal status (10 min budget).
              TextButton(
                onClick = {
                  scope.launch {
                    running = true; runOutput = null
                    runCatching {
                      val started = api.runAgent(d.id, com.nova.app.data.RunAgentRequest(background = true))
                      runOutput = "[${started.status}] Running in background…"
                      var final: com.nova.app.data.ExecutionDto? = null
                      for (i in 0 until 120) {
                        delay(5000)
                        val e = runCatching { api.execution(started.executionId) }.getOrNull() ?: continue
                        if (e.status !in setOf("QUEUED", "RUNNING")) { final = e; break }
                      }
                      final
                    }
                      .onSuccess { e ->
                        runOutput = if (e != null) "[${e.status}] ${e.output ?: e.error ?: ""}"
                          else "[RUNNING] Still running. Track it in Activity."
                      }
                      .onFailure { runOutput = "[failed] Background run failed to start." }
                    running = false; refresh()
                  }
                },
                enabled = !running,
              ) { Text("In background", color = scheme.primary) }
              if (d.status != "active") TextButton(onClick = {
                scope.launch { runCatching { api.activateAgent(d.id) }; refresh() }
              }) { Text("Activate", color = scheme.primary) }
              if (d.status == "active") TextButton(onClick = {
                scope.launch { runCatching { api.pauseAgent(d.id) }; refresh() }
              }) { Text("Pause", color = scheme.onSurfaceVariant) }
            }
            runOutput?.let {
              Spacer(Modifier.height(NovaSpace.md))
              Surface(shape = RoundedCornerShape(NovaRadius.sm), color = novaGlassFill(), border = androidx.compose.foundation.BorderStroke(1.dp, novaGlassEdge())) {
                Text(
                  it,
                  fontFamily = NovaMono,
                  style = MaterialTheme.typography.labelSmall,
                  color = scheme.onSurface,
                  modifier = Modifier.padding(NovaSpace.md),
                )
              }
            }
          }
        }
      }
    }
  }
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

@Composable fun SettingsScreen(session: SessionToken, onLogout: () -> Unit, onBack: (() -> Unit)? = null, onPreferencesChanged: () -> Unit = {}, onConnectionsClick: () -> Unit = {}) {
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
  var showSignOutConfirm by remember { mutableStateOf(false) }

  // Permission can change in Android system settings while this screen is
  // paused. Refresh the switch when the user returns so it never lies.
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

  // Glass page: per-row glass cards, section labels, red log-out card at the end.
  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp + LocalBottomChrome.current),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    item {
      // Header: back circle left, account name 17px tertiary centered.
      Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (onBack != null) {
          SpecCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
        }
        Text(
          displayEmail,
          modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
          fontSize = 17.sp,
          color = scheme.onSurfaceVariant,
          maxLines = 1,
        )
      }
    }

    item { SpecSectionLabel("Account", modifier = Modifier.padding(top = 8.dp)) }

    item {
      SpecSettingsRow(
        label = displayEmail,
        subtitle = "Signed in",
        leading = {
          Text(initial, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = scheme.primary)
        },
      )
    }

    item { SpecSectionLabel("Model & accounts", modifier = Modifier.padding(top = 8.dp)) }

    item {
      // Model policy — what Nova runs on, stated plainly (§7).
      SpecSettingsRow(
        label = "Model",
        subtitle = "Gemini 3.1 Flash Lite for chat. Gemini 3.5 Flash Lite handles tools and images.",
      )
    }

    item {
      // Connected accounts — one tap to the Connections screen (§38).
      SpecSettingsRow(
        label = "Connected accounts",
        subtitle = "GitHub, Gmail, Calendar, Drive, Docs, Sheets, Vercel, Supabase, LeetCode",
        onClick = onConnectionsClick,
        leading = {
          Icon(Icons.Default.Link, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(24.dp))
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

    item { SpecSectionLabel("Notifications", modifier = Modifier.padding(top = 8.dp)) }

    item {
      SpecSettingsRow(
        label = "Agent notifications",
        subtitle = if (granted) "On. Runs report back." else "Off. Runs stay silent.",
        leading = {
          Icon(Icons.Default.Notifications, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(24.dp))
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
    }

    item {
      SpecSettingsRow(
        label = "Read responses aloud",
        subtitle = "Nova voice, on tap of any answer",
        leading = {
          Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(24.dp))
        },
        trailing = {
          Text("On tap", fontSize = 14.sp, color = scheme.onSurfaceVariant)
        },
      )
    }

    item { SpecSectionLabel("Appearance", modifier = Modifier.padding(top = 8.dp)) }

    item {
      // Spec S27 theme picker: System (Default) · Light · Dark rows.
      val currentMode = NovaThemeMode.entries.find {
        it.name.equals(AccentPreferences.getThemeMode(context), ignoreCase = true)
      } ?: NovaThemeMode.SYSTEM
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
          NovaThemeMode.SYSTEM to "System (Default)",
          NovaThemeMode.LIGHT to "Light",
          NovaThemeMode.DARK to "Dark",
        ).forEach { (mode, label) ->
          SpecSettingsRow(
            label = "Appearance",
            subtitle = label,
            onClick = {
              AccentPreferences.setThemeMode(context, mode.name)
              onPreferencesChanged()
            },
            leading = {
              Icon(Icons.Default.Settings, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(24.dp))
            },
            trailing = {
              if (mode == currentMode) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = scheme.primary, modifier = Modifier.size(20.dp))
              }
            },
          )
        }
      }
    }

    item {
      // Spec S28 accent picker: 16px dot + 15px label rows, check on the active.
      val currentAccentName = AccentPreferences.get(context)
      val currentAccent = NovaAccent.entries.find { it.name.equals(currentAccentName, ignoreCase = true) } ?: NovaAccent.PURPLE
      val dotColors = mapOf(
        NovaAccent.BLUE to Color(0xFF6B9BF5),
        NovaAccent.WHITE to Color(0xFFF0F0F0),
        NovaAccent.GREEN to Color(0xFF6FCF97),
        NovaAccent.YELLOW to Color(0xFFE3C568),
        NovaAccent.PINK to Color(0xFFF08BB8),
        NovaAccent.ORANGE to Color(0xFFF09A5C),
        NovaAccent.PURPLE to Color(0xFFA270F0),
      )
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          "Accent color: ${currentAccent.label}",
          fontSize = 15.sp,
          color = scheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 8.dp),
        )
        NovaAccent.entries.forEach { accent ->
          SpecSettingsRow(
            label = accent.label,
            onClick = {
              AccentPreferences.set(context, accent.name)
              onPreferencesChanged()
            },
            leading = {
              Box(
                Modifier.size(16.dp).clip(CircleShape).background(dotColors[accent] ?: Color.White),
              )
            },
            trailing = {
              if (accent == currentAccent) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = scheme.primary, modifier = Modifier.size(20.dp))
              }
            },
          )
        }
      }
    }

    item { SpecSectionLabel("About", modifier = Modifier.padding(top = 8.dp)) }

    item {
      SpecSettingsRow(
        label = "Nova",
        subtitle = "v0.1.0. Voice, agents and ties into the tools you already use.",
      )
    }

    item {
      // Log out: full-width h56 glass r12, error icon + error label.
      Surface(
        onClick = { showSignOutConfirm = true },
        shape = RoundedCornerShape(NovaRadius.md),
        color = novaGlassFill(),
        border = androidx.compose.foundation.BorderStroke(1.dp, novaGlassEdge()),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { role = Role.Button; contentDescription = "Log out" },
      ) {
        Row(
          Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.AutoMirrored.Filled.Logout,
            contentDescription = null,
            tint = scheme.error,
            modifier = Modifier.size(24.dp),
          )
          Spacer(Modifier.width(12.dp))
          Text("Log out", fontSize = 16.sp, color = scheme.error)
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

