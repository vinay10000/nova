package com.nova.app.data

import com.nova.app.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

@Serializable data class ConversationDto(val id: String, val title: String)

// Mirrors the backend StreamChunk union (see backend/src/ai/AIProvider.ts).
@Serializable
data class StreamChunk(
  val type: String,
  val text: String? = null,
  @SerialName("toolId") val toolId: String? = null,
  val code: String? = null,
  val retryable: Boolean? = null,
  @SerialName("interactionId") val interactionId: String? = null,
  @SerialName("label") val label: String? = null,
  // F1: backend notice/approval chunks carry these; previously dropped by the client.
  val provider: String? = null,
  val message: String? = null,
  @SerialName("approvalId") val approvalId: String? = null,
)

@Serializable private data class StreamRequest(
  val conversationId: String,
  val message: String,
  val model: String? = null,
  val attachmentIds: List<String> = emptyList(), // §10
)

/**
 * SSE client for POST /v1/chat/stream (§3, §6).
 * OkHttp's EventSource is the stdlib-of-choice here — no SSE dependency to own.
 */
class ChatStreamClient(
  private val baseUrl: String = BuildConfig.API_BASE_URL,
  private val tokenProvider: () -> String? = { null },
) {
  private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
  private val client = OkHttpClient.Builder()
    // Streaming must not time out mid-response; reads are long-lived by design.
    .readTimeout(0, TimeUnit.MILLISECONDS)
    .build()

  fun stream(conversationId: String, message: String, model: String? = null, attachmentIds: List<String> = emptyList()): Flow<StreamChunk> = callbackFlow {
    val payload = json.encodeToString(StreamRequest(conversationId, message, model, attachmentIds))
    val builder = Request.Builder()
      .url("$baseUrl/v1/chat/stream")
      .post(payload.toRequestBody("application/json".toMediaType()))
      .header("Accept", "text/event-stream")
    tokenProvider()?.let { builder.header("Authorization", "Bearer $it") }

    val listener = object : EventSourceListener() {
      override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        runCatching { json.decodeFromString<StreamChunk>(data) }
          .onSuccess { trySend(it) }
        // Malformed frame is dropped rather than killing the stream.
      }

      override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
        // 401 must surface as an error chunk so the UI can route to login (§29).
        trySend(StreamChunk(type = "error", code = if (response?.code == 401) "unauthenticated" else "network"))
        close()
      }

      override fun onClosed(eventSource: EventSource) {
        close()
      }
    }

    val source = EventSources.createFactory(client).newEventSource(builder.build(), listener)
    awaitClose { source.cancel() } // §6 stop generation
  }
}

// Auth calls (§29). Retrofit owns JSON here; the SSE path above is separate.
interface NovaApi {
  @retrofit2.http.POST("/v1/auth/login")
  suspend fun login(@retrofit2.http.Body body: LoginRequest): AuthResponse

  @retrofit2.http.POST("/v1/auth/register")
  suspend fun register(@retrofit2.http.Body body: LoginRequest): AuthResponse

  @retrofit2.http.GET("/v1/conversations")
  suspend fun conversations(
    @retrofit2.http.Query("limit") limit: Int = 50,
    @retrofit2.http.Query("offset") offset: Int = 0,
  ): ConversationsResponse

  @retrofit2.http.POST("/v1/conversations")
  suspend fun createConversation(): ConversationDto

  @retrofit2.http.GET("/v1/conversations/{id}")
  suspend fun conversation(@retrofit2.http.Path("id") id: String): ConversationDetailDto

  @retrofit2.http.DELETE("/v1/conversations/{id}")
  suspend fun deleteConversation(@retrofit2.http.Path("id") id: String): OkResponse

  @retrofit2.http.GET("/v1/models")
  suspend fun models(): ModelsResponse

  // §10 upload — backend validates magic bytes + size; the client only picks the file.
  @retrofit2.http.Multipart
  @retrofit2.http.POST("/v1/files")
  suspend fun uploadFile(@retrofit2.http.Part file: okhttp3.MultipartBody.Part): FileDto

  @retrofit2.http.GET("/v1/files/{id}/raw")
  suspend fun rawFile(@retrofit2.http.Path("id") id: String): okhttp3.ResponseBody

  // §52 agent framework.
  @retrofit2.http.GET("/v1/agents")
  suspend fun agents(): AgentsResponse

  @retrofit2.http.POST("/v1/agents/build")
  suspend fun buildAgent(@retrofit2.http.Body body: BuildAgentRequest): kotlinx.serialization.json.JsonObject

  @retrofit2.http.POST("/v1/agents")
  suspend fun createAgent(@retrofit2.http.Body body: CreateAgentRequest): AgentDto

  @retrofit2.http.GET("/v1/agents/{id}")
  suspend fun agent(@retrofit2.http.Path("id") id: String): AgentDto

  @retrofit2.http.POST("/v1/agents/{id}/activate")
  suspend fun activateAgent(@retrofit2.http.Path("id") id: String): OkResponse

  @retrofit2.http.POST("/v1/agents/{id}/pause")
  suspend fun pauseAgent(@retrofit2.http.Path("id") id: String): OkResponse

  @retrofit2.http.POST("/v1/agents/{id}/run")
  suspend fun runAgent(@retrofit2.http.Path("id") id: String): RunAgentResponse

  @retrofit2.http.GET("/v1/executions")
  suspend fun executions(): ExecutionsResponse

  @retrofit2.http.GET("/v1/executions/{id}")
  suspend fun execution(@retrofit2.http.Path("id") id: String): ExecutionDto

  @retrofit2.http.GET("/v1/approvals")
  suspend fun approvals(@retrofit2.http.Query("status") status: String = "pending"): ApprovalsResponse

  @retrofit2.http.POST("/v1/approvals/{id}")
  suspend fun decideApproval(
    @retrofit2.http.Path("id") id: String,
    @retrofit2.http.Body body: DecideApprovalRequest,
  ): OkResponse

  // §38 Connections — OAuth flow + status
  @retrofit2.http.GET("/v1/connections")
  suspend fun connections(): ConnectionsResponse

  @retrofit2.http.GET("/v1/connections/github/authorize")
  suspend fun githubAuthorize(): GitHubAuthorizeResponse

  @retrofit2.http.GET("/v1/connections/github/status")
  suspend fun githubStatus(): GitHubStatusResponse

  @retrofit2.http.DELETE("/v1/connections/github")
  suspend fun disconnectGitHub(): OkResponse

  @retrofit2.http.GET("/v1/connections/gmail/authorize")
  suspend fun gmailAuthorize(): GmailAuthorizeResponse

  @retrofit2.http.GET("/v1/connections/gmail/status")
  suspend fun gmailStatus(): GmailStatusResponse

  @retrofit2.http.DELETE("/v1/connections/gmail")
  suspend fun disconnectGmail(): OkResponse

  @retrofit2.http.GET("/v1/connections/calendar/authorize")
  suspend fun calendarAuthorize(): GmailAuthorizeResponse

  @retrofit2.http.GET("/v1/connections/calendar/status")
  suspend fun calendarStatus(): GmailStatusResponse

  @retrofit2.http.DELETE("/v1/connections/calendar")
  suspend fun disconnectCalendar(): OkResponse

  // F2: Drive OAuth + token-based providers
  @retrofit2.http.GET("/v1/connections/drive/authorize")
  suspend fun driveAuthorize(): GmailAuthorizeResponse

  @retrofit2.http.GET("/v1/connections/drive/status")
  suspend fun driveStatus(): GmailStatusResponse

  @retrofit2.http.DELETE("/v1/connections/drive")
  suspend fun disconnectDrive(): OkResponse

  @retrofit2.http.POST("/v1/connections/vercel")
  suspend fun connectVercel(@retrofit2.http.Body body: ConnectTokenRequest): OkResponse

  @retrofit2.http.GET("/v1/connections/vercel/status")
  suspend fun vercelStatus(): GmailStatusResponse

  @retrofit2.http.DELETE("/v1/connections/vercel")
  suspend fun disconnectVercel(): OkResponse

  @retrofit2.http.POST("/v1/connections/supabase")
  suspend fun connectSupabase(@retrofit2.http.Body body: ConnectSupabaseRequest): OkResponse

  @retrofit2.http.GET("/v1/connections/supabase/status")
  suspend fun supabaseStatus(): GmailStatusResponse

  @retrofit2.http.DELETE("/v1/connections/supabase")
  suspend fun disconnectSupabase(): OkResponse

  // §42 @plugin mention picker
  @retrofit2.http.GET("/v1/plugins")
  suspend fun plugins(): PluginsResponse

  // §38 provider catalogue — the Connections screen renders from this single call.
  @retrofit2.http.GET("/v1/connections/providers")
  suspend fun connectionProviders(): ConnectionProvidersResponse
}

// Mirrors backend StoredFile (metadata only — bytes stay server-side, §46).
@Serializable data class FileDto(val id: String, val filename: String, val mime: String, val size: Long, val status: String)

@Serializable data class ModelDto(val id: String)
@Serializable data class ModelsResponse(val models: List<ModelDto>)

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class AuthResponse(val token: String)
@Serializable data class ConversationsResponse(val conversations: List<ConversationDto>, val total: Int = 0, val hasMore: Boolean = false)
@Serializable data class MessageDto(val id: String = "", val role: String, val content: String, val attachments: List<AttachmentInfo> = emptyList())
@Serializable data class AttachmentInfo(val id: String, val filename: String, val mime: String, val size: Long = 0)
@Serializable data class ConversationDetailDto(
  val id: String,
  val title: String,
  val messages: List<MessageDto> = emptyList(),
)

// §52 agent DTOs. Build returns either a config or {questions[]} — kept as
// JsonObject so a model-shape drift shows as UI text, never a crash.
@Serializable data class OkResponse(val ok: Boolean = true)
@Serializable data class BuildAgentRequest(val prompt: String)
@Serializable data class CreateAgentRequest(
  val name: String,
  val goal: String,
  val instructions: String,
  val tools: List<String>,
  val description: String? = null,
)
@Serializable data class AgentDto(
  val id: String,
  val name: String,
  val goal: String,
  val instructions: String = "",
  val tools: List<String> = emptyList(),
  val permissions: List<String> = emptyList(),
  val status: String = "draft",
)
@Serializable data class AgentsResponse(val agents: List<AgentDto> = emptyList())
@Serializable data class RunAgentResponse(val executionId: String, val status: String, val output: String? = null)
@Serializable data class StepDto(val id: String = "", val label: String)
@Serializable data class ExecutionDto(
  val id: String,
  val status: String,
  val trigger: String = "",
  val output: String? = null,
  val error: String? = null,
  val steps: List<StepDto> = emptyList(),
  val agent: AgentNameDto? = null,
)
@Serializable data class AgentNameDto(val name: String = "")
@Serializable data class ExecutionsResponse(val executions: List<ExecutionDto> = emptyList())
@Serializable data class ApprovalDto(val id: String, val toolId: String, val status: String = "pending")
@Serializable data class ApprovalsResponse(val approvals: List<ApprovalDto> = emptyList())
@Serializable data class DecideApprovalRequest(val decision: String) // approve | reject

// §38 Connection DTOs
@Serializable data class ConnectionDto(val provider: String, val status: String, val scopes: List<String> = emptyList(), val providerLogin: String? = null)
@Serializable data class ConnectionsResponse(val connections: List<ConnectionDto> = emptyList())
@Serializable data class GitHubAuthorizeResponse(val url: String, val state: String)
@Serializable data class GitHubStatusResponse(val connected: Boolean, val login: String? = null, val scopes: List<String> = emptyList())
@Serializable data class GmailAuthorizeResponse(val url: String, val state: String)
@Serializable data class GmailStatusResponse(val connected: Boolean, val login: String? = null, val scopes: List<String> = emptyList())

// F2 token-connect bodies.
@Serializable data class ConnectTokenRequest(val token: String, val login: String? = null)
@Serializable data class ConnectSupabaseRequest(val projectRef: String, val key: String)

// §42 plugin catalogue for the @ mention picker.
@Serializable data class PluginDto(val id: String, val name: String, val blurb: String, val requires: String? = null, val ready: Boolean = false)
@Serializable data class PluginsResponse(val plugins: List<PluginDto> = emptyList())

// §38 provider catalogue (backend-owned; includes availability state).
@Serializable data class ProviderDto(val id: String, val name: String, val blurb: String, val login: String? = null, val state: String)
@Serializable data class ConnectionProvidersResponse(val providers: List<ProviderDto> = emptyList())
