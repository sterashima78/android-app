package dev.terashima.yomitorirss.core.airuntime

import com.google.ai.edge.litertlm.OpenApiTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class LocalInferenceMessageRole {
  USER,
  MODEL,
}

data class LocalInferenceMessage(
  val role: LocalInferenceMessageRole,
  val content: String,
)

enum class LocalInferenceToolArgumentType(val schemaType: String) {
  STRING("string"),
  INTEGER("integer"),
  NUMBER("number"),
  BOOLEAN("boolean"),
  STRING_ARRAY("array"),
}

data class LocalInferenceToolArgument(
  val name: String,
  val description: String,
  val required: Boolean = false,
  val type: LocalInferenceToolArgumentType = LocalInferenceToolArgumentType.STRING,
)

data class LocalInferenceTool(
  val name: String,
  val description: String,
  val arguments: List<LocalInferenceToolArgument> = emptyList(),
  val allowAdditionalArguments: Boolean = true,
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

data class LocalInferenceToolCall(
  val name: String,
  val arguments: Map<String, Any?>,
)

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

interface LocalConversationInference {
  val models: Flow<List<LocalModelStatus>>
  val progress: Flow<LocalInferenceProgress?>

  fun selectedModel(): LocalModelStatus?

  suspend fun generateConversation(
    request: LocalInferenceConversationRequest,
    streaming: Boolean = false,
    onPartial: (String) -> Unit = {},
  ): String
}

internal data class LocalInferenceToolDefinition(
  val name: String,
  val description: String,
  val arguments: List<LocalInferenceToolArgument>,
  val allowAdditionalArguments: Boolean,
) {
  fun toTool(execute: suspend (Map<String, String>) -> String): LocalInferenceTool =
    LocalInferenceTool(
      name = name,
      description = description,
      arguments = arguments,
      allowAdditionalArguments = allowAdditionalArguments,
      execute = execute,
    )
}

internal fun parseToolDefinitionJson(value: String): LocalInferenceToolDefinition {
  val root = TOOL_JSON.parseToJsonElement(value).jsonObject
  val name = root.getValue("name").jsonPrimitive.content
  val description = root.getValue("description").jsonPrimitive.content
  val parameters = root.getValue("parameters").jsonObject
  val requiredNames = parameters["required"]
    ?.let { element -> element as? JsonArray }
    ?.map { element -> element.jsonPrimitive.content }
    ?.toSet()
    .orEmpty()
  val allowAdditionalArguments = parameters["additionalProperties"]
    ?.jsonPrimitive
    ?.content
    ?.toBooleanStrictOrNull()
    ?: true
  val properties = parameters.getValue("properties").jsonObject
  val arguments = properties.map { (argumentName, element) ->
    val schema = element.jsonObject
    val schemaType = schema.getValue("type").jsonPrimitive.content
    val type = when (schemaType) {
      "string" -> LocalInferenceToolArgumentType.STRING
      "integer" -> LocalInferenceToolArgumentType.INTEGER
      "number" -> LocalInferenceToolArgumentType.NUMBER
      "boolean" -> LocalInferenceToolArgumentType.BOOLEAN
      "array" -> LocalInferenceToolArgumentType.STRING_ARRAY
      else -> throw IllegalArgumentException("Unsupported tool argument type: $schemaType")
    }
    LocalInferenceToolArgument(
      name = argumentName,
      description = schema.getValue("description").jsonPrimitive.content,
      required = argumentName in requiredNames,
      type = type,
    )
  }
  return LocalInferenceToolDefinition(
    name = name,
    description = description,
    arguments = arguments,
    allowAdditionalArguments = allowAdditionalArguments,
  )
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
      toolResultJson(definition.execute(arguments))
    }.getOrElse {
      toolErrorJson()
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
      if (!tool.allowAdditionalArguments) put("additionalProperties", false)
      put(
        "properties",
        buildJsonObject {
          tool.arguments.forEach { argument ->
            put(
              argument.name,
              buildJsonObject {
                put("type", argument.type.schemaType)
                if (argument.type == LocalInferenceToolArgumentType.STRING_ARRAY) {
                  put(
                    "items",
                    buildJsonObject {
                      put("type", "string")
                    },
                  )
                }
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

internal fun toolResultJson(result: String): String = buildJsonObject {
  put("result", result)
}.toString()

internal fun toolErrorJson(): String = buildJsonObject {
  put("error", "ツール実行に失敗しました。")
}.toString()

private val TOOL_JSON = Json { isLenient = true }
