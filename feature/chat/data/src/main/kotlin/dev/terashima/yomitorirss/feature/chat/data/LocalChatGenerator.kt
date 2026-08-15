package dev.terashima.yomitorirss.feature.chat.data

import dev.terashima.yomitorirss.core.airuntime.LocalInferenceConversationRequest
import dev.terashima.yomitorirss.core.airuntime.LocalInferenceMessage
import dev.terashima.yomitorirss.core.airuntime.LocalInferenceMessageRole
import dev.terashima.yomitorirss.core.airuntime.LocalInferenceStage
import dev.terashima.yomitorirss.core.airuntime.LocalInferenceTool
import dev.terashima.yomitorirss.core.airuntime.LocalInferenceToolArgument
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.feature.chat.AgentSkill
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
  private val inferenceTools = skills.toInferenceTools()
  private val _streamingReply = MutableStateFlow("")

  init {
    val toolCount = skills.sumOf { it.tools.size }
    require(inferenceTools.size == toolCount) { "Agent tool names must be unique" }
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
    val model = modelManager.selectedModel() ?: error("AIモデルをダウンロードして選択してください")
    val query = turns.lastOrNull { it.role == ChatRole.USER }?.content.orEmpty()
    require(query.isNotBlank()) { "メッセージを入力してください" }

    val context = buildList {
      for (provider in contextProviders) addAll(provider.contextFor(query))
    }
    val prompt = ChatPrompt.render(
      turns = turns,
      context = context,
      maxInputChars = model.maxInputChars,
    )
    val request = LocalInferenceConversationRequest(
      systemInstruction = prompt.systemInstruction,
      initialMessages = prompt.history.map { turn ->
        LocalInferenceMessage(
          role = when (turn.role) {
            ChatRole.USER -> LocalInferenceMessageRole.USER
            ChatRole.ASSISTANT -> LocalInferenceMessageRole.MODEL
          },
          content = turn.content,
        )
      },
      userMessage = prompt.userMessage,
      tools = inferenceTools,
    )

    _streamingReply.value = ""
    val streamedRaw = StringBuilder()
    val raw = modelManager.generateConversation(request, streaming = true) { chunk ->
      appendStreamChunk(streamedRaw, chunk)
      _streamingReply.value = ChatResponseStream.partial(streamedRaw.toString())
    }
    ChatResponseStream.complete(raw).also { reply ->
      _streamingReply.value = reply
    }
  }
}

private fun List<AgentSkill>.toInferenceTools(): List<LocalInferenceTool> {
  val tools = flatMap { skill ->
    skill.tools.map { agentTool ->
      LocalInferenceTool(
        name = agentTool.definition.name,
        description = buildString {
          append(agentTool.definition.description)
          append(" Skill: ")
          append(skill.name)
          append("。")
          append(skill.description)
          append(" 利用方針: ")
          append(skill.instructions)
        },
        arguments = agentTool.definition.arguments.map { argument ->
          LocalInferenceToolArgument(
            name = argument.name,
            description = argument.description,
            required = argument.required,
          )
        },
        execute = { arguments ->
          agentTool.execute(arguments).take(MAX_TOOL_RESULT_CHARS)
        },
      )
    }
  }
  require(tools.map(LocalInferenceTool::name).distinct().size == tools.size) {
    "Agent tool names must be unique"
  }
  return tools
}

private fun appendStreamChunk(buffer: StringBuilder, chunk: String) {
  if (chunk.isEmpty()) return
  val current = buffer.toString()
  if (current.isNotEmpty() && chunk.length >= current.length && chunk.startsWith(current)) {
    buffer.setLength(0)
    buffer.append(chunk)
  } else {
    buffer.append(chunk)
  }
}

private const val MAX_TOOL_RESULT_CHARS = 4_000
