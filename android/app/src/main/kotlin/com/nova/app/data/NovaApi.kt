package com.nova.app.data

import kotlinx.serialization.Serializable

// Retrofit API — real backend calls only, no fake AI (§61). SSE for /v1/chat/stream.
interface NovaApi {
  // TODO: @POST("/v1/chat/stream") streaming via OkHttp SSE
  // @GET("/v1/conversations") search/rename/archive (§8)
  // @GET("/v1/agents") @POST("/v1/agents/{id}/run") Run Now (§33)
  // @GET("/v1/executions") history + steps (§34-§35)
  // @POST("/v1/approvals/{id}") approve/reject (§36)
}

@Serializable data class ConversationDto(val id: String, val title: String)
