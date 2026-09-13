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
  suspend fun conversations(): ConversationsResponse

  @retrofit2.http.POST("/v1/conversations")
  suspend fun createConversation(): ConversationDto

  @retrofit2.http.GET("/v1/conversations/{id}")
  suspend fun conversation(@retrofit2.http.Path("id") id: String): ConversationDetailDto

  @retrofit2.http.GET("/v1/models")
  suspend fun models(): ModelsResponse

  // §10 upload — backend validates magic bytes + size; the client only picks the file.
  @retrofit2.http.Multipart
  @retrofit2.http.POST("/v1/files")
  suspend fun uploadFile(@retrofit2.http.Part file: okhttp3.MultipartBody.Part): FileDto
}

// Mirrors backend StoredFile (metadata only — bytes stay server-side, §46).
@Serializable data class FileDto(val id: String, val filename: String, val mime: String, val size: Long, val status: String)

@Serializable data class ModelDto(val id: String)
@Serializable data class ModelsResponse(val models: List<ModelDto>)

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class AuthResponse(val token: String)
@Serializable data class ConversationsResponse(val conversations: List<ConversationDto>)
@Serializable data class MessageDto(val id: String = "", val role: String, val content: String)
@Serializable data class ConversationDetailDto(
  val id: String,
  val title: String,
  val messages: List<MessageDto> = emptyList(),
)
