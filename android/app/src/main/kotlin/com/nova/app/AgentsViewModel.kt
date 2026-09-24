package com.nova.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.app.data.AgentDto
import com.nova.app.data.ApprovalDto
import com.nova.app.data.BuildAgentRequest
import com.nova.app.data.CreateAgentRequest
import com.nova.app.data.DecideApprovalRequest
import com.nova.app.data.ExecutionDto
import com.nova.app.data.NovaApi
import com.nova.app.data.PatchAgentRequest
import com.nova.app.data.RunAgentRequest
import com.nova.app.data.ToolDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import retrofit2.HttpException

@Serializable
data class AgentScheduleDto(
  val type: String = "run_now",
  val frequency: String? = null,
  val time: String? = null,
  val timezone: String? = null,
)

@Serializable
data class AgentDraft(
  val name: String = "",
  val description: String = "",
  val goal: String = "",
  val instructions: String = "",
  val tools: List<String> = emptyList(),
  val schedule: AgentScheduleDto? = null,
)

enum class AgentOperationKind { IDLE, SAVING, RUNNING, ACTIVATING, PAUSING, CANCELLING }

data class AgentOperation(
  val kind: AgentOperationKind,
  val agentId: String? = null,
)

data class AgentsUiState(
  val agents: List<AgentDto> = emptyList(),
  val approvals: List<ApprovalDto> = emptyList(),
  val tools: List<ToolDto> = emptyList(),
  val toolsError: String? = null,
  val isLoading: Boolean = true,
  val isRefreshing: Boolean = false,
  val loadError: String? = null,
  val builderPrompt: String = "",
  val builderQuestions: List<String> = emptyList(),
  val draft: AgentDraft? = null,
  val isBuilding: Boolean = false,
  val builderError: String? = null,
  val isEditorOpen: Boolean = false,
  val editingAgentId: String? = null,
  val isSaving: Boolean = false,
  val selectedAgentId: String? = null,
  val selectedAgent: AgentDto? = null,
  val selectedExecutions: List<ExecutionDto> = emptyList(),
  val isLoadingAgent: Boolean = false,
  val detailError: String? = null,
  val operation: AgentOperation? = null,
  val approvalBusyId: String? = null,
  val runMessage: String? = null,
  val notice: String? = null,
)

class AgentsViewModel(private val savedState: SavedStateHandle) : ViewModel() {
  private val json = Json { ignoreUnknownKeys = true }
  private val restoredSelection = savedState.get<String>(SELECTED_KEY)
  private val restoredDraft = savedState.get<String>(DRAFT_KEY)?.let { encoded ->
    runCatching { json.decodeFromString<AgentDraft>(encoded) }.getOrNull()
  }
  private val _state = MutableStateFlow(
    AgentsUiState(
      builderPrompt = savedState.get<String>(PROMPT_KEY).orEmpty(),
      draft = restoredDraft,
      editingAgentId = savedState.get<String>(EDITOR_ID_KEY),
      selectedAgentId = restoredSelection,
      isLoadingAgent = restoredSelection != null,
    ),
  )
  val state: StateFlow<AgentsUiState> = _state.asStateFlow()

  private var api: NovaApi? = null
  private var refreshJob: Job? = null
  private var detailJob: Job? = null
  private var runJob: Job? = null

  fun configureApi(nextApi: NovaApi) {
    if (api === nextApi) return
    api = nextApi
    refresh()
    _state.value.selectedAgentId?.let(::loadAgentDetail)
  }

  fun setPrompt(value: String) {
    savedState[PROMPT_KEY] = value
    savedState.remove<String>(DRAFT_KEY)
    savedState.remove<String>(EDITOR_ID_KEY)
    _state.update {
      it.copy(
        builderPrompt = value,
        builderQuestions = emptyList(),
        draft = null,
        builderError = null,
      )
    }
  }

  fun buildAgent() {
    val currentApi = api ?: return
    val prompt = _state.value.builderPrompt.trim()
    if (prompt.isBlank() || _state.value.isBuilding) return
    viewModelScope.launch {
      _state.update { it.copy(isBuilding = true, builderError = null, builderQuestions = emptyList(), draft = null, notice = null) }
      safeCall { currentApi.buildAgent(BuildAgentRequest(prompt)) }.fold(
        onSuccess = { payload ->
          val questions = parseQuestions(payload)
          if (questions.isNotEmpty()) {
            _state.update { it.copy(isBuilding = false, builderQuestions = questions, builderError = null) }
          } else {
            val draft = parseDraft(payload)
            if (draft == null) {
              _state.update { it.copy(isBuilding = false, builderError = "Nova needs a little more detail before it can draft this agent.") }
            } else {
              _state.update { it.copy(isBuilding = false, draft = draft, builderError = null) }
              persistDraft(draft)
            }
          }
        },
        onFailure = { error ->
          _state.update { it.copy(isBuilding = false, builderError = error.userMessage("Nova could not draft that yet. Try again in a moment.")) }
        },
      )
    }
  }

  fun clearBuilder() {
    savedState[PROMPT_KEY] = ""
    savedState.remove<String>(DRAFT_KEY)
    savedState.remove<String>(EDITOR_ID_KEY)
    _state.update { it.copy(builderPrompt = "", builderQuestions = emptyList(), draft = null, editingAgentId = null, builderError = null, notice = null) }
  }

  fun openEditor(agent: AgentDto? = null) {
    val draft = agent?.let(::draftFromAgent) ?: _state.value.draft ?: return
    _state.update {
      it.copy(
        draft = draft,
        editingAgentId = agent?.id ?: it.editingAgentId,
        isEditorOpen = true,
        builderError = null,
        detailError = null,
      )
    }
    persistDraft(draft, _state.value.editingAgentId)
  }

  fun closeEditor() {
    if (_state.value.editingAgentId != null) {
      savedState.remove<String>(DRAFT_KEY)
      savedState.remove<String>(EDITOR_ID_KEY)
      _state.update { it.copy(isEditorOpen = false, draft = null, editingAgentId = null, builderError = null) }
    } else {
      _state.update { it.copy(isEditorOpen = false, builderError = null) }
    }
  }

  fun updateDraftName(value: String) = updateDraft { it.copy(name = value) }
  fun updateDraftDescription(value: String) = updateDraft { it.copy(description = value) }
  fun updateDraftGoal(value: String) = updateDraft { it.copy(goal = value) }
  fun updateDraftInstructions(value: String) = updateDraft { it.copy(instructions = value) }

  fun toggleDraftTool(toolId: String) = updateDraft {
    val tools = if (toolId in it.tools) it.tools - toolId else it.tools + toolId
    it.copy(tools = tools)
  }

  fun setDraftSchedule(type: String) = updateDraft {
    val timezone = java.time.ZoneId.systemDefault().id
    val schedule = when (type) {
      "once" -> AgentScheduleDto(type = "once", frequency = "once", timezone = timezone)
      "daily" -> AgentScheduleDto(type = "recurring", frequency = "daily", timezone = timezone)
      "weekdays" -> AgentScheduleDto(type = "recurring", frequency = "weekdays", timezone = timezone)
      "weekly" -> AgentScheduleDto(type = "recurring", frequency = "weekly", timezone = timezone)
      else -> AgentScheduleDto(type = "run_now", timezone = timezone)
    }
    it.copy(schedule = schedule)
  }

  fun updateDraftTime(value: String) = updateDraft {
    it.copy(schedule = it.schedule?.copy(time = value.takeIf(String::isNotBlank)))
  }

  fun saveDraft(activate: Boolean) {
    val currentApi = api ?: return
    val snapshot = _state.value
    val draft = snapshot.draft ?: return
    if (snapshot.isSaving) return
    val name = draft.name.trim()
    val goal = draft.goal.trim()
    val instructions = draft.instructions.trim().ifBlank { goal }
    if (name.isBlank() || goal.isBlank()) {
      _state.update { it.copy(builderError = "Add a name and a clear goal before saving.") }
      return
    }
    if (draft.tools.isEmpty()) {
      _state.update { it.copy(builderError = "Choose at least one tool or connection for this agent.") }
      return
    }
    val editingId = snapshot.editingAgentId
    _state.update { it.copy(isSaving = true, builderError = null, detailError = null, operation = AgentOperation(AgentOperationKind.SAVING, editingId), notice = null) }
    viewModelScope.launch {
      val result = safeCall<Pair<String, Boolean>> {
        if (editingId == null) {
          val created = currentApi.createAgent(
            CreateAgentRequest(
              name = name,
              goal = goal,
              instructions = instructions,
              tools = draft.tools,
              description = draft.description.trim().ifBlank { null },
              schedule = draft.schedule?.let(::scheduleJson),
            ),
          )
          val activated = if (activate) safeCall { currentApi.activateAgent(created.id) }.isSuccess else false
          created.id to activated
        } else {
          currentApi.updateAgent(
            editingId,
            PatchAgentRequest(
              name = name,
              goal = goal,
              instructions = instructions,
              tools = draft.tools,
              description = draft.description.trim().ifBlank { null },
              schedule = draft.schedule?.let(::scheduleJson),
            ),
          )
          val activated = if (activate) safeCall { currentApi.activateAgent(editingId) }.isSuccess else true
          editingId to activated
        }
      }
      result.fold(
        onSuccess = { (id, activated) ->
          _state.update {
            it.copy(
              isSaving = false,
              operation = null,
              draft = null,
              builderPrompt = "",
              builderQuestions = emptyList(),
              isEditorOpen = false,
              editingAgentId = null,
              selectedAgentId = id,
              notice = when {
                activate && !activated -> "Saved, but activation failed. The agent remains in its previous state."
                activate -> "Agent activated."
                editingId != null -> "Changes saved."
                else -> "Agent saved as a draft."
              },
            )
          }
          savedState[PROMPT_KEY] = ""
          savedState.remove<String>(DRAFT_KEY)
          savedState.remove<String>(EDITOR_ID_KEY)
          if (id.isNotBlank()) loadAgentDetail(id)
          refresh()
        },
        onFailure = { error ->
          _state.update { it.copy(isSaving = false, operation = null, builderError = error.userMessage("The agent could not be saved. Check the details and retry.")) }
        },
      )
    }
  }

  fun selectAgent(id: String) {
    if (_state.value.selectedAgentId == id && _state.value.selectedAgent != null && _state.value.detailError == null) return
    savedState[SELECTED_KEY] = id
    _state.update { it.copy(selectedAgentId = id, selectedAgent = null, selectedExecutions = emptyList(), isLoadingAgent = true, detailError = null, runMessage = null) }
    loadAgentDetail(id)
  }

  fun closeAgent() {
    detailJob?.cancel()
    savedState.remove<String>(SELECTED_KEY)
    _state.update { it.copy(selectedAgentId = null, selectedAgent = null, selectedExecutions = emptyList(), isLoadingAgent = false, detailError = null, runMessage = null, operation = null) }
  }

  fun refresh() {
    val currentApi = api ?: return
    refreshJob?.cancel()
    refreshJob = viewModelScope.launch {
      _state.update { it.copy(isRefreshing = true, loadError = null) }
      val results = coroutineScope {
        val agents = async { safeCall { currentApi.agents() } }
        val approvals = async { safeCall { currentApi.approvals() } }
        val tools = async { safeCall { currentApi.tools() } }
        Triple(agents.await(), approvals.await(), tools.await())
      }
      val agentResult = results.first
      val approvalResult = results.second
      val toolResult = results.third
      _state.update { previous ->
        val nextAgents = agentResult.getOrNull()?.agents ?: previous.agents
        val selectedId = previous.selectedAgentId
        val selected = selectedId?.let { id ->
          previous.selectedAgent ?: nextAgents.firstOrNull { it.id == id }
        }
        previous.copy(
          agents = nextAgents,
          approvals = approvalResult.getOrNull()?.approvals ?: previous.approvals,
          tools = toolResult.getOrNull()?.tools ?: previous.tools,
          toolsError = toolResult.exceptionOrNull()?.userMessage("The tool catalogue could not be loaded."),
          selectedAgent = selected,
          isLoading = false,
          isRefreshing = false,
          loadError = when {
            agentResult.isFailure && approvalResult.isFailure -> "Nova could not reach the agent service. Check your connection and retry."
            agentResult.isFailure -> "The agent list could not be refreshed. Showing the last known list."
            approvalResult.isFailure -> "Approval requests could not be refreshed. Showing the last known list."
            else -> null
          },
        )
      }
    }
  }

  fun setAgentActive(agent: AgentDto, active: Boolean) {
    val currentApi = api ?: return
    val kind = if (active) AgentOperationKind.ACTIVATING else AgentOperationKind.PAUSING
    _state.update { it.copy(operation = AgentOperation(kind, agent.id), detailError = null, notice = null) }
    viewModelScope.launch {
      safeCall { if (active) currentApi.activateAgent(agent.id) else currentApi.pauseAgent(agent.id) }.fold(
        onSuccess = {
          _state.update { it.copy(operation = null, notice = if (active) "Agent is live." else "Agent paused.") }
          refresh()
          if (_state.value.selectedAgentId == agent.id) loadAgentDetail(agent.id)
        },
        onFailure = { error ->
          _state.update { it.copy(operation = null, detailError = error.userMessage("The agent status could not be changed. Retry when you are back online.")) }
        },
      )
    }
  }

  fun runAgent(agent: AgentDto, background: Boolean) {
    val currentApi = api ?: return
    val activeOperation = _state.value.operation
    if (activeOperation?.kind == AgentOperationKind.RUNNING && activeOperation.agentId == agent.id) return
    _state.update { it.copy(operation = AgentOperation(AgentOperationKind.RUNNING, agent.id), runMessage = null, detailError = null, notice = null) }
    runJob?.cancel()
    runJob = viewModelScope.launch {
      try {
        safeCall { currentApi.runAgent(agent.id, RunAgentRequest(background)) }.fold(
          onSuccess = { started ->
            val initial = safeCall { currentApi.execution(started.executionId) }.getOrNull()
            val message = started.output?.takeIf { it.isNotBlank() } ?: "Run started. Activity will show each step."
            _state.update {
              if (it.selectedAgentId == agent.id) it.copy(runMessage = message, selectedExecutions = listOfNotNull(initial) + it.selectedExecutions) else it
            }
            if (background || initial?.status in setOf("QUEUED", "RUNNING", "WAITING_FOR_APPROVAL")) {
              pollExecution(agent.id, started.executionId, currentApi, background)
            }
            refresh()
          },
          onFailure = { error ->
            _state.update { it.copy(operation = null, runMessage = error.userMessage("The run could not start. Retry shortly.")) }
          },
        )
      } finally {
        _state.update { if (it.operation?.agentId == agent.id) it.copy(operation = null) else it }
      }
    }
  }

  fun cancelSelectedRun() {
    val currentApi = api ?: return
    val execution = _state.value.selectedExecutions.firstOrNull { it.status in ACTIVE_EXECUTION_STATES } ?: return
    _state.update { it.copy(operation = AgentOperation(AgentOperationKind.CANCELLING, _state.value.selectedAgentId), detailError = null) }
    viewModelScope.launch {
      safeCall { currentApi.cancelExecution(execution.id) }.fold(
        onSuccess = {
          val updated = execution.copy(status = "CANCELLED")
          _state.update { state ->
            state.copy(operation = null, runMessage = "Run cancelled.", selectedExecutions = listOf(updated) + state.selectedExecutions.filterNot { it.id == execution.id })
          }
          refresh()
        },
        onFailure = { error -> _state.update { it.copy(operation = null, detailError = error.userMessage("The run could not be cancelled. Retry when you are back online.")) } },
      )
    }
  }

  fun decideApproval(approval: ApprovalDto, decision: String) {
    val currentApi = api ?: return
    if (_state.value.approvalBusyId != null) return
    _state.update { it.copy(approvalBusyId = approval.id, notice = null) }
    viewModelScope.launch {
      safeCall { currentApi.decideApproval(approval.id, DecideApprovalRequest(decision)) }.fold(
        onSuccess = { response ->
          val message = if (decision == "approve" && response.resumed == false) {
            "Approval recorded. This run is still waiting for the backend worker."
          } else if (decision == "approve") {
            "Approved. The agent can continue."
          } else {
            "Rejected. The agent will not take this action."
          }
          _state.update { it.copy(approvalBusyId = null, notice = message) }
          refresh()
        },
        onFailure = { error ->
          _state.update { it.copy(approvalBusyId = null, notice = error.userMessage("The decision could not be saved. Try again.")) }
        },
      )
    }
  }

  fun clearNotice() {
    _state.update { it.copy(notice = null) }
  }

  private fun loadAgentDetail(id: String) {
    val currentApi = api ?: return
    detailJob?.cancel()
    detailJob = viewModelScope.launch {
      _state.update { it.copy(isLoadingAgent = true, detailError = null) }
      val agentResult = safeCall { currentApi.agent(id) }
      val executionsResult = safeCall { currentApi.executions(id) }
      _state.update { previous ->
        previous.copy(
          selectedAgent = agentResult.getOrNull() ?: previous.selectedAgent,
          selectedExecutions = executionsResult.getOrNull()?.executions ?: previous.selectedExecutions,
          isLoadingAgent = false,
          detailError = when {
            agentResult.isFailure -> agentResult.exceptionOrNull()?.userMessage("This agent could not be loaded.")
            executionsResult.isFailure -> executionsResult.exceptionOrNull()?.userMessage("Run history could not be loaded.")
            else -> null
          },
        )
      }
    }
  }

  private suspend fun pollExecution(agentId: String, executionId: String, currentApi: NovaApi, keepPolling: Boolean) {
    repeat(if (keepPolling) 120 else 1) {
      delay(if (keepPolling) 5_000 else 250)
      val result = safeCall { currentApi.execution(executionId) }.getOrNull() ?: return
      _state.update { previous ->
        if (previous.selectedAgentId != agentId) {
          previous
        } else {
          val withoutOld = previous.selectedExecutions.filterNot { it.id == result.id }
          previous.copy(selectedExecutions = listOf(result) + withoutOld, runMessage = result.output?.takeIf { it.isNotBlank() } ?: previous.runMessage)
        }
      }
      if (result.status !in ACTIVE_EXECUTION_STATES) {
        _state.update { if (it.selectedAgentId == agentId) it.copy(operation = null) else it }
        return
      }
    }
    _state.update { if (it.selectedAgentId == agentId) it.copy(operation = null, runMessage = "Still running. Open Activity for the latest steps.") else it }
  }

  private fun updateDraft(transform: (AgentDraft) -> AgentDraft) {
    _state.update { state -> state.copy(draft = state.draft?.let(transform), builderError = null) }
    _state.value.draft?.let { persistDraft(it, _state.value.editingAgentId) }
  }

  private fun persistDraft(draft: AgentDraft, editingAgentId: String? = _state.value.editingAgentId) {
    savedState[DRAFT_KEY] = json.encodeToString(draft)
    if (editingAgentId.isNullOrBlank()) savedState.remove<String>(EDITOR_ID_KEY) else savedState[EDITOR_ID_KEY] = editingAgentId
  }

  private fun parseQuestions(payload: JsonObject): List<String> = runCatching {
    payload["questions"]?.jsonArray?.mapNotNull { item ->
      item.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
    }.orEmpty()
  }.getOrDefault(emptyList())

  private fun parseDraft(payload: JsonObject): AgentDraft? {
    val name = payload.string("name") ?: return null
    val goal = payload.string("goal") ?: return null
    if (name.isBlank() || goal.isBlank()) return null
    return AgentDraft(
      name = name.trim(),
      description = payload.string("description").orEmpty(),
      goal = goal.trim(),
      instructions = payload.string("instructions").orEmpty().ifBlank { goal.trim() },
      tools = payload.strings("tools"),
      schedule = parseSchedule(payload["schedule"]),
    )
  }

  private fun draftFromAgent(agent: AgentDto): AgentDraft = AgentDraft(
    name = agent.name,
    description = agent.description.orEmpty(),
    goal = agent.goal,
    instructions = agent.instructions.ifBlank { agent.goal },
    tools = agent.tools,
    schedule = parseSchedule(agent.schedule),
  )

  private fun parseSchedule(value: JsonElement?): AgentScheduleDto? {
    val objectValue = value as? JsonObject ?: return null
    val type = objectValue.string("type") ?: "run_now"
    return AgentScheduleDto(
      type = type,
      frequency = objectValue.string("frequency"),
      time = objectValue.string("time"),
      timezone = objectValue.string("timezone"),
    )
  }

  private fun scheduleJson(schedule: AgentScheduleDto): JsonObject = buildJsonObject {
    put("type", JsonPrimitive(schedule.type))
    schedule.frequency?.let { put("frequency", JsonPrimitive(it)) }
    schedule.time?.let { put("time", JsonPrimitive(it)) }
    schedule.timezone?.let { put("timezone", JsonPrimitive(it)) }
  }

  private fun JsonObject.string(key: String): String? = runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
  private fun JsonObject.strings(key: String): List<String> = runCatching { this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.filter { it.isNotBlank() }.orEmpty() }.getOrDefault(emptyList())

  private suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (error: Throwable) {
    Result.failure(error)
  }

  private fun Throwable.userMessage(fallback: String): String {
    val code = (this as? HttpException)?.code()
    return when (code) {
      401 -> "Your session expired. Sign in again to continue."
      403 -> "Nova does not have permission to do that yet."
      404 -> "That agent is no longer available."
      429 -> "Nova is busy right now. Wait a moment and retry."
      else -> fallback
    }
  }

  private companion object {
    const val PROMPT_KEY = "agents_prompt"
    const val DRAFT_KEY = "agents_draft"
    const val EDITOR_ID_KEY = "agents_editor_id"
    const val SELECTED_KEY = "agents_selected"
    val ACTIVE_EXECUTION_STATES = setOf("QUEUED", "RUNNING", "WAITING_FOR_APPROVAL")
  }
}
