package com.nova.app

import com.nova.app.data.ChatStreamRequest
import com.nova.app.data.GenerationSelection
import com.nova.app.data.ModelDto
import com.nova.app.data.ModelsResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GenerationSelectionTest {
  private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

  private val catalog = listOf(
    ModelDto(id = "gemini-3.1-flash-lite", label = "3.1 Flash", description = "Fast", efforts = listOf("minimal", "low", "medium", "high")),
    ModelDto(id = "gemini-3.5-flash-lite", label = "3.5 Flash", description = "Tools", efforts = listOf("minimal", "low", "medium", "high")),
  )

  @Test
  fun requestSerializesModelAndEffortIndependently() {
    val encoded = json.encodeToString(
      ChatStreamRequest(
        conversationId = "c1",
        message = "hello",
        model = "gemini-3.5-flash-lite",
        effort = "high",
        attachmentIds = listOf("f1"),
      ),
    )
    val decoded = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(encoded)
    assertEquals("gemini-3.5-flash-lite", decoded["model"].toString().trim('"'))
    assertEquals("high", decoded["effort"].toString().trim('"'))
    assertEquals("[\"f1\"]", decoded["attachmentIds"].toString())
  }

  @Test
  fun requestOmitsAutomaticModelAndDefaultEffort() {
    val encoded = json.encodeToString(ChatStreamRequest(conversationId = "c1", message = "hello"))
    val decoded = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(encoded)
    assertFalse(decoded.containsKey("model"))
    assertFalse(decoded.containsKey("effort"))
  }

  @Test
  fun catalogDecodesWithLabelsAndEfforts() {
    val encoded = """
      {"models":[{"id":"gemini-3.1-flash-lite","label":"3.1 Flash","description":"Fast","efforts":["minimal","low","medium","high"]}]}
    """.trimIndent()
    val decoded = json.decodeFromString<ModelsResponse>(encoded)
    assertEquals("3.1 Flash", decoded.models.single().label)
    assertEquals(listOf("minimal", "low", "medium", "high"), decoded.models.single().efforts)
  }

  @Test
  fun modelChangeDoesNotMutateEffort() {
    val selection = GenerationSelection(modelId = "gemini-3.1-flash-lite", effort = "medium")
    assertEquals(GenerationSelection(modelId = "gemini-3.5-flash-lite", effort = "medium"), selection.selectModel("gemini-3.5-flash-lite"))
  }

  @Test
  fun effortChangeDoesNotMutateModel() {
    val selection = GenerationSelection(modelId = "gemini-3.5-flash-lite", effort = "low")
    assertEquals(GenerationSelection(modelId = "gemini-3.5-flash-lite", effort = "high"), selection.selectEffort("high"))
  }

  @Test
  fun validationDropsStaleModelAndUnsupportedEffort() {
    val selection = GenerationSelection(modelId = "removed-model", effort = "xhigh")
    assertEquals(GenerationSelection(modelId = null, effort = null), selection.validated(catalog))
  }

  @Test
  fun automaticModelKeepsEffortSupportedByAnyModel() {
    val selection = GenerationSelection(modelId = null, effort = "high")
    assertEquals(selection, selection.validated(catalog))
    assertNull(selection.validated(emptyList()).modelId)
  }
}
