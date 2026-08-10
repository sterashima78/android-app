package dev.terashima.yomitorirss.feature.chat.data

import dev.terashima.yomitorirss.core.airuntime.ChatContextBlock as RuntimeChatContextBlock
import dev.terashima.yomitorirss.core.airuntime.ChatRole as RuntimeChatRole
import dev.terashima.yomitorirss.core.airuntime.ChatTurn as RuntimeChatTurn
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.feature.chat.AgentSkill
import dev.terashima.yomitorirss.feature.chat.AgentTool
import dev.terashima.yomitorirss.feature.chat.ChatContextProvider
import dev.terashima.yomitorirss.feature.chat.ChatGenerator
import dev.terashima.yomitorirss.feature.chat.ChatModelStatus
import dev.terashima.yomitorirss.feature.chat.ChatProgress
import dev.terashima.yomitorirss.feature.chat.ChatRole
import dev.terashima.yomitorirss.feature.chat.ChatTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocalChatGenerator(
  private val modelManager: LocalModelManager,
  private val contextProviders: List<ChatContextProvider> = emptyList(),
  private val skills: List<AgentSkill> = emptyList(),
) : ChatGenerator {
  private val toolsByName: Map<String, AgentTool> = skills
    .flatMap(AgentSkill::tools)
    .associateBy { it.definition.name }
  private val agentNames: Set<String> = buildSet {
    skills.forEach { skill ->
      add(skill.name)
      skill.tools.forEach { tool -> add(tool.definition.name) }
    }
  }

  init {
    val toolCount = skills.sumOf { it.tools.size }
    require(toolsByName.size == toolCount) { "Agent tool names must be unique" }
  }

  override val selectedModel: Flow<ChatModelStatus?> = modelManager.models.map { models ->
    models.firstOrNull { it.selected }?.let { ChatModelStatus(id = it.id, name = it.name) }
  }

  override val progress: Flow<ChatProgress?> = modelManager.chatProgress.map { progress ->
    progress?.let {
      ChatProgress(
        stage = it.stage,
        modelName = it.modelName,
        estimatedStageDurationMillis = it.estimatedStageDurationMillis,
      )
    }
  }

  override val streamingReply: Flow<String> = modelManager.chatResponse.map { response ->
    if (AgentProtocol.shouldHideFromUser(response, agentNames)) "" else response
  }

  override suspend fun reply(turns: List<ChatTurn>): String = withContext(Dispatchers.IO) {
    val query = turns.lastOrNull { it.role == ChatRole.USER }?.content.orEmpty()
    val baseContext = buildList {
      for (provider in contextProviders) addAll(provider.contextFor(query))
    }.map { RuntimeChatContextBlock(it.sourceId, it.label, it.content) }
    val runtimeTurns = turns.map { RuntimeChatTurn(it.role.toRuntime(), it.content) }

    if (skills.isEmpty()) {
      return@withContext modelManager.chat(turns = runtimeTurns, context = baseContext)
    }

    val observations = mutableListOf<AgentObservation>()
    for (step in 0 until MAX_TOOL_STEPS) {
      val response = modelManager.chat(
        turns = runtimeTurns,
        context = agentContext(baseContext, observations, allowToolCalls = true),
      )
      val call = AgentProtocol.parseToolCall(response)
      if (call == null) {
        if (!AgentProtocol.containsToolCallMarker(response)) return@withContext response
        observations += AgentObservation(
          toolName = "agent-protocol",
          content = "ツール呼び出しを解析できませんでした。指定された <tool_call> JSON 形式で再試行してください。",
          error = true,
        )
        continue
      }

      val tool = toolsByName[call.name]
      if (tool == null) {
        observations += AgentObservation(
          toolName = call.name,
          content = "利用できないツールです。利用可能なSkillに記載されたツール名を選んでください。",
          error = true,
        )
        continue
      }

      observations += runCatching { tool.execute(call.arguments) }
        .fold(
          onSuccess = { result ->
            AgentObservation(toolName = call.name, content = result.take(MAX_TOOL_RESULT_CHARS))
          },
          onFailure = { error ->
            AgentObservation(
              toolName = call.name,
              content = error.message?.takeIf(String::isNotBlank) ?: "ツール実行に失敗しました。",
              error = true,
            )
          },
        )
    }

    val finalResponse = modelManager.chat(
      turns = runtimeTurns,
      context = agentContext(baseContext, observations, allowToolCalls = false),
    )
    if (AgentProtocol.containsToolCallMarker(finalResponse)) {
      "ツール実行回数の上限に達したため、回答を完了できませんでした。質問を絞って再試行してください。"
    } else {
      finalResponse
    }
  }

  private fun agentContext(
    baseContext: List<RuntimeChatContextBlock>,
    observations: List<AgentObservation>,
    allowToolCalls: Boolean,
  ): List<RuntimeChatContextBlock> = buildList {
    add(
      RuntimeChatContextBlock(
        sourceId = "agent-skills",
        label = "Agent Skills",
        content = AgentProtocol.renderContext(skills, observations, allowToolCalls),
      ),
    )
    addAll(baseContext)
  }
}

private fun ChatRole.toRuntime(): RuntimeChatRole = when (this) {
  ChatRole.USER -> RuntimeChatRole.USER
  ChatRole.ASSISTANT -> RuntimeChatRole.ASSISTANT
}

private const val MAX_TOOL_STEPS = 4
private const val MAX_TOOL_RESULT_CHARS = 8_000
