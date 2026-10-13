package dev.terashima.yomitorirss.core.airuntime

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.tool
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

internal class LiteRtLmInference(
  file: File,
  cacheDirectory: File,
  backend: LocalInferenceBackend,
  contextTokens: Int,
  speculativeDecodingEnabled: Boolean,
  visionEnabled: Boolean,
) : AutoCloseable {
  private val engine = createEngine(
    file = file,
    cacheDirectory = cacheDirectory,
    backend = backend,
    contextTokens = contextTokens,
    speculativeDecodingEnabled = speculativeDecodingEnabled,
    visionEnabled = visionEnabled,
  )

  fun generate(prompt: String): String =
    engine.createConversation().use { conversation ->
      conversation.sendMessage(prompt).toString()
    }

  fun generateWithImage(prompt: String, imageBytes: ByteArray): String =
    engine.createConversation().use { conversation ->
      conversation.sendMessage(
        Contents.of(
          Content.ImageBytes(imageBytes),
          Content.Text(prompt),
        ),
      ).toString()
    }

  fun generateImageToolCall(
    systemInstruction: String,
    userMessage: String,
    imageBytes: ByteArray,
    tools: List<LocalInferenceTool>,
  ): LocalInferenceToolCall? {
    val config = ConversationConfig(
      systemInstruction = Contents.of(systemInstruction),
      tools = tools.map { definition -> tool(LocalOpenApiTool(definition)) },
      automaticToolCalling = false,
    )
    return engine.createConversation(config).use { conversation ->
      val response = conversation.sendMessage(
        Contents.of(
          Content.ImageBytes(imageBytes),
          Content.Text(userMessage),
        ),
      )
      response.toolCalls.singleOrNull()?.let { call ->
        LocalInferenceToolCall(call.name, call.arguments)
      }
    }
  }

  fun generateStreaming(prompt: String, onChunk: (String) -> Unit): String =
    engine.createConversation().use { conversation ->
      collectStreamingResponse(conversation.sendMessageAsync(prompt), onChunk)
    }

  fun generateConversation(
    request: LocalInferenceConversationRequest,
    streaming: Boolean,
    onChunk: (String) -> Unit,
  ): String {
    val config = ConversationConfig(
      systemInstruction = Contents.of(request.systemInstruction),
      initialMessages = request.initialMessages.map { message ->
        when (message.role) {
          LocalInferenceMessageRole.USER -> Message.user(message.content)
          LocalInferenceMessageRole.MODEL -> Message.model(message.content)
        }
      },
      tools = request.tools.map { definition -> tool(LocalOpenApiTool(definition)) },
      automaticToolCalling = true,
    )
    return engine.createConversation(config).use { conversation ->
      if (streaming) {
        collectStreamingResponse(conversation.sendMessageAsync(request.userMessage), onChunk)
      } else {
        conversation.sendMessage(request.userMessage).toString()
      }
    }
  }

  private fun collectStreamingResponse(
    messages: kotlinx.coroutines.flow.Flow<Message>,
    onChunk: (String) -> Unit,
  ): String {
    val response = StringBuilder()
    runBlocking {
      messages.collect { message ->
        val chunk = message.toString()
        if (chunk.isNotEmpty()) {
          response.append(chunk)
          onChunk(chunk)
        }
      }
    }
    return response.toString()
  }

  override fun close() {
    engine.close()
  }

  @OptIn(ExperimentalApi::class)
  private fun createEngine(
    file: File,
    cacheDirectory: File,
    backend: LocalInferenceBackend,
    contextTokens: Int,
    speculativeDecodingEnabled: Boolean,
    visionEnabled: Boolean,
  ): Engine = engineInitializationLock.withLock {
    val previous = ExperimentalFlags.enableSpeculativeDecoding
    ExperimentalFlags.enableSpeculativeDecoding = speculativeDecodingEnabled
    try {
      val textBackend = when (backend) {
        LocalInferenceBackend.CPU -> Backend.CPU()
        LocalInferenceBackend.GPU -> Backend.GPU()
      }
      Engine(
        EngineConfig(
          modelPath = file.absolutePath,
          backend = textBackend,
          visionBackend = Backend.GPU().takeIf { visionEnabled },
          cacheDir = cacheDirectory.absolutePath,
          maxNumTokens = contextTokens,
        ),
      ).also { it.initialize() }
    } finally {
      ExperimentalFlags.enableSpeculativeDecoding = previous
    }
  }

  companion object {
    private val engineInitializationLock = ReentrantLock()
  }
}
