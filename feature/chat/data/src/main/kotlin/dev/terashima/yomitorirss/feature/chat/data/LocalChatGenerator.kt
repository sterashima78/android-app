package dev.terashima.yomitorirss.feature.chat.data

import dev.terashima.yomitorirss.core.airuntime.LocalInferenceStage
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.airuntime.LocalPromptFormat
import dev.terashima.yomitorirss.feature.chat.AgentSkill
import dev.terashima.yomitorirss.feature.chat.AgentTool
import dev.terashima.yomitorirss.feature.chat.ChatContextBlock
import dev.terashima.yomitorirss.feature.chat.ChatContextProvider
import dev.terashima.yomitorirss.feature.chat.ChatGenerator
import dev.terashima.yomitorirss.feature.chat.ChatModelStatus
import dev.terashima.yomitorirss.feature.chat.ChatProgress
import dev.terashima.yomitorirss.feature.chat.ChatRole
import dev.terashima.yomitorirss.feature.chat.ChatTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
  private val _streamingReply = MutableStateFlow("")

  init {
    val toolCount = skills.sumOf { it.tools.size }
    require(toolsByName.size == toolCount) { "Agent tool names must be unique" }
  }

  override val selectedModel: Flow<ChatModelStatus?> = modelManager.models.map { models ->
    models.firstOrNull { it.selected }?.let { ChatModelStatus(id = it.id, name = it.name) }
  }

  override val progress: Flow<ChatProgress?> = modelManager.inferenceProgress.map { progress ->
    progress?.let {
      ChatProgress(
        stage = when (it.stage) {
          LocalInferenceStage.PREPARING_MODEL -> "preparing_model"
          LocalInferenceStage.GENERATING_RESPONSE -> "generating_reply"
        },
        modelName = it.modelName,
        estimatedStageDurationMillis = it.estimatedStageDurationMillis,
      )
    }
  }

  override val streamingReply: Flow<String> = _streamingReply.asStateFlow()

  override suspend fun reply(turns: List<ChatTurn>): String = withContext(Dispatchers.IO) {
    val query = turns.lastOrNull { it.role == ChatRole.USER }?.content.orEmpty()
    require(query.isNotBlank()) { "メッセージを入力してください" }
    val baseContext = buildList {
      for (provider in contextProviders) addAll(provider.contextFor(query))
    }

    if (skills.isEmpty()) {
      return@withContext generateResponse(turns, baseContext)
    }

    val observations = mutableListOf<AgentObservation>()
    for (step in 0 until MAX_TOOL_STEPS) {
      val response = generateResponse(
        turns = turns,
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

    val finalResponse = generateResponse(
      turns = turns,
      context = agentContext(baseContext, observations, allowToolCalls = false),
    )
    if (AgentProtocol.containsToolCallMarker(finalResponse)) {
      "ツール実行回数の上限に達したため、回答を完了できませんでした。質問を絞って再試行してください。"
    } else {
      finalResponse
    }
  }

  private fun generateResponse(
    turns: List<ChatTurn>,
    context: List<ChatContextBlock>,
  ): String {
    val model = modelManager.selectedModel() ?: error("AIモデルをダウンロードして選択してください")
    val prompt = ChatPrompt.render(
      turns = turns,
      context = context,
      maxInputChars = model.promptBudgetChars,
      chatMl = model.promptFormat == LocalPromptFormat.CHAT_ML,
    )
    _streamingReply.value = ""
    val raw = modelManager.generate(prompt, streaming = true) { partialRaw ->
      val visible = ChatResponseStream.partial(partialRaw)
      _streamingReply.value = if (AgentProtocol.shouldHideFromUser(visible, agentNames)) "" else visible
    }
    return ChatResponseStream.complete(raw).also { reply ->
      _streamingReply.value = if (AgentProtocol.shouldHideFromUser(reply, agentNames)) "" else reply
    }
  }

  private fun agentContext(
    baseContext: List<ChatContextBlock>,
    observations: List<AgentObservation>,
    allowToolCalls: Boolean,
  ): List<ChatContextBlock> = buildList {
    add(
      ChatContextBlock(
        sourceId = "agent-skills",
        label = "Agent Skills",
        content = AgentProtocol.renderContext(skills, observations, allowToolCalls),
      ),
    )
    addAll(baseContext)
  }
}

private const val MAX_TOOL_STEPS = 4
private const val MAX_TOOL_RESULT_CHARS = 8_000
