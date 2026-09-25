package com.nova.app.data

data class GenerationSelection(
  val modelId: String? = null,
  val effort: String? = null,
) {
  fun selectModel(modelId: String?): GenerationSelection = copy(modelId = modelId)

  fun selectEffort(effort: String?): GenerationSelection = copy(effort = effort)

  fun validated(models: List<ModelDto>): GenerationSelection {
    if (models.isEmpty()) return copy(modelId = null)
    val allowedModels = models.mapTo(mutableSetOf()) { it.id }
    val allowedEfforts = models
      .filter { modelId == null || it.id == modelId }
      .flatMapTo(mutableSetOf()) { it.efforts }
    return copy(
      modelId = modelId?.takeIf(allowedModels::contains),
      effort = effort?.takeIf(allowedEfforts::contains),
    )
  }
}
