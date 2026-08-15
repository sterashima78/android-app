package dev.terashima.yomitorirss.core.airuntime

import com.google.ai.edge.litertlm.OpenApiTool
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

enum class LocalInferenceMessageRole {
  USER,
  MODEL,
}

data class LocalInferenceMessage(
  val role: LocalInferenceMessageRole,
  val content: String,
)

data class LocalInferenceToolArgument(
  val name: String,
  val description: String,
  val required: Boolean = false,
)

data class LocalInferenceTool(
  val name: String,
  val description: String,
  val arguments: List<LocalInferenceToolArgument> = emptyList(),
  val execute: suspend (Map<String, String>) -> String,
) {
  init {
    require(name.isNotBlank()) { "Tool name must not be blank" }
    require(description.isNotBlank()) { "Tool description must not be blank" }
    require(arguments.map(LocalInferenceToolArgument::name).distinct().size == arguments.size) {
      "Tool argument names must be unique: $name"
    }
  }
}

data class LocalInferenceConversationRequest(
  val systemInstruction: String,
  val initialMessages: List<LocalInferenceMessage>,
  val userMessage: String,
  val tools: List<LocalInferenceTool> = emptyList(),
) {
  init {
    require(systemInstruction.isNotBlank()) { "System instruction must not be blank" }
    require(userMessage.isNotBlank()) { "User message must not be blank" }
    require(tools.map(LocalInferenceTool::name).distinct().size == tools.size) {
      "Tool names must be unique"
    }
  }
}

internal class LocalOpenApiTool(
  private val definition: LocalInferenceTool,
) : OpenApiTool {
  override fun getToolDescriptionJsonString(): String = toolDescriptionJson(definition)

  override fun execute(paramsJsonString: String): String = runBlocking {
    runCatching {
      val parsed = parseToolArguments(paramsJsonString)
      val allowedNames = definition.arguments.map(LocalInferenceToolArgument::name).toSet()
      val arguments = parsed.filterKeys { it in allowedNames }
      val missingRequired = definition.arguments
        .filter(LocalInferenceToolArgument::required)
        .map(LocalInferenceToolArgument::name)
        .filterNot(arguments::containsKey)
      check(missingRequired.isEmpty()) { "Required tool arguments are missing" }
      definition.execute(arguments)
    }.getOrElse {
      "ツール実行に失敗しました。"
    }
  }
}

internal fun toolDescriptionJson(tool: LocalInferenceTool): String = buildJsonObject {
  put("name", tool.name)
  put("description", tool.description)
  put(
    "parameters",
    buildJsonObject {
      put("type", "object")
      put(
        "properties",
        buildJsonObject {
          tool.arguments.forEach { argument ->
            put(
              argument.name,
              buildJsonObject {
                put("type", "string")
                put("description", argument.description)
              },
            )
          }
        },
      )
      val required = tool.arguments.filter(LocalInferenceToolArgument::required)
      if (required.isNotEmpty()) {
        put(
          "required",
          buildJsonArray {
            required.forEach { argument -> add(JsonPrimitive(argument.name)) }
          },
        )
      }
    },
  )
}.toString()

internal fun parseToolArguments(value: String): Map<String, String> {
  val root = TOOL_JSON.parseToJsonElement(value).jsonObject
  return root.mapValues { (_, element) ->
    when (element) {
      is JsonPrimitive -> element.content
      is JsonObject,
      is JsonArray -> element.toString()
      else -> element.toString()
    }
  }
}

private val TOOL_JSON = Json { isLenient = true }
