package dev.terashima.yomitorirss.feature.chat

data class AgentToolArgument(
  val name: String,
  val description: String,
  val required: Boolean = false,
)

data class AgentToolDefinition(
  val name: String,
  val description: String,
  val arguments: List<AgentToolArgument> = emptyList(),
)

interface AgentTool {
  val definition: AgentToolDefinition

  suspend fun execute(arguments: Map<String, String>): String
}

data class AgentSkill(
  val name: String,
  val description: String,
  val instructions: String,
  val tools: List<AgentTool>,
) {
  init {
    require(name.isNotBlank()) { "Skill name must not be blank" }
    require(description.isNotBlank()) { "Skill description must not be blank" }
    require(tools.isNotEmpty()) { "Skill must expose at least one tool" }
  }
}
