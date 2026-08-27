package dev.terashima.yomitorirss.core.aiinference

enum class AiStructuredToolArgumentType {
  STRING,
  INTEGER,
  NUMBER,
  BOOLEAN,
  STRING_ARRAY,
}

data class AiStructuredToolArgument(
  val name: String,
  val description: String,
  val required: Boolean = false,
  val type: AiStructuredToolArgumentType = AiStructuredToolArgumentType.STRING,
) {
  init {
    require(name.isNotBlank()) { "Tool argument name must not be blank" }
    require(description.isNotBlank()) { "Tool argument description must not be blank" }
  }
}

data class AiStructuredTool(
  val name: String,
  val description: String,
  val arguments: List<AiStructuredToolArgument>,
  val allowAdditionalArguments: Boolean = false,
) {
  init {
    require(name.isNotBlank()) { "Tool name must not be blank" }
    require(description.isNotBlank()) { "Tool description must not be blank" }
    require(arguments.map(AiStructuredToolArgument::name).distinct().size == arguments.size) {
      "Tool argument names must be unique"
    }
  }
}

data class AiStructuredToolCall(
  val name: String,
  val arguments: Map<String, String>,
)

/**
 * Provider-neutral one-shot structured text output.
 *
 * This is intentionally separate from [AiTextInference]: callers that only need free-form text do
 * not depend on tool-calling support, while structured-output tasks can require an explicit tool
 * call instead of parsing model prose as JSON.
 */
interface AiStructuredTextInference {
  suspend fun generateToolCall(
    systemInstruction: String,
    userMessage: String,
    tool: AiStructuredTool,
  ): AiStructuredToolCall?
}
