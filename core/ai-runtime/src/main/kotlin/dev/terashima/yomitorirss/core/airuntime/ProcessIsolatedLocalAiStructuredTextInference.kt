package dev.terashima.yomitorirss.core.airuntime

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import dev.terashima.yomitorirss.core.aiinference.AiStructuredTextInference
import dev.terashima.yomitorirss.core.aiinference.AiStructuredTool
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolArgument
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolArgumentType
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolCall
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val STRUCTURED_TEXT_IPC_MAX_CHARS = 128 * 1024
private const val STRUCTURED_TEXT_MAX_ERROR_CHARS = 500
internal const val STRUCTURED_TEXT_PROCESS_MAX_ATTEMPTS = 2
private const val STRUCTURED_TEXT_MODEL_PREFERENCES_NAME = "local_summary_models"
private const val STRUCTURED_TEXT_BENCHMARK_PREFERENCES_NAME = "local_context_benchmarks"
private const val STRUCTURED_TEXT_CHILD_MODEL_PREFERENCES_NAME = "local_ai_structured_text_model_snapshot"
private const val STRUCTURED_TEXT_CHILD_BENCHMARK_PREFERENCES_NAME = "local_ai_structured_text_context_snapshot"
private const val STRUCTURED_TEXT_SELECTED_MODEL_KEY = "selected_model_id"
private const val STRUCTURED_TEXT_BACKEND_KEY = "inference_backend"
private const val STRUCTURED_TEXT_SPECULATIVE_DECODING_KEY = "speculative_decoding_enabled"
private const val STRUCTURED_TEXT_CONTEXT_SIZE_MODE_KEY = "context_size_mode"
private const val STRUCTURED_TEXT_MODEL_REVISION_PREFIX = "model_revision"

private const val MSG_STRUCTURED_GENERATE = 1
private const val MSG_STRUCTURED_RESULT = 2
private const val KEY_SYSTEM_INSTRUCTION = "system_instruction"
private const val KEY_USER_MESSAGE = "user_message"
private const val KEY_TOOL_NAME = "tool_name"
private const val KEY_TOOL_DESCRIPTION = "tool_description"
private const val KEY_TOOL_ALLOW_ADDITIONAL = "tool_allow_additional"
private const val KEY_TOOL_ARGUMENT_NAMES = "tool_argument_names"
private const val KEY_TOOL_ARGUMENT_DESCRIPTIONS = "tool_argument_descriptions"
private const val KEY_TOOL_ARGUMENT_TYPES = "tool_argument_types"
private const val KEY_TOOL_ARGUMENT_REQUIRED = "tool_argument_required"
private const val KEY_MODEL_ID = "model_id"
private const val KEY_BACKEND = "backend"
private const val KEY_SPECULATIVE_DECODING = "speculative_decoding"
private const val KEY_CONTEXT_TOKENS = "context_tokens"
private const val KEY_MODEL_REVISION_IDS = "model_revision_ids"
private const val KEY_MODEL_REVISION_VALUES = "model_revision_values"
private const val KEY_SUCCESS = "success"
private const val KEY_ERROR = "error"
private const val KEY_CALL_NAME = "call_name"
private const val KEY_CALL_ARGUMENT_NAMES = "call_argument_names"
private const val KEY_CALL_ARGUMENT_VALUES = "call_argument_values"

/**
 * Executes one-shot structured output in the same short-lived subprocess boundary as local text
 * generation. A fresh bound-service lifetime is used for each call so LiteRT-LM/native allocations
 * cannot accumulate in the main process or across a long library-organization batch.
 */
class ProcessIsolatedLocalAiStructuredTextInference(
  context: Context,
  private val manager: LocalModelManager,
) : AiStructuredTextInference {
  private val appContext = context.applicationContext

  override suspend fun generateToolCall(
    systemInstruction: String,
    userMessage: String,
    tool: AiStructuredTool,
  ): AiStructuredToolCall? {
    require(systemInstruction.isNotBlank()) { "System instruction must not be blank" }
    require(userMessage.isNotBlank()) { "User message must not be blank" }
    require(systemInstruction.length + userMessage.length <= STRUCTURED_TEXT_IPC_MAX_CHARS) {
      "構造化推論プロンプトが長すぎます"
    }

    val snapshot = captureStructuredTextSnapshot(appContext, manager)
    var lastRemoteError: RemoteException? = null
    repeat(STRUCTURED_TEXT_PROCESS_MAX_ATTEMPTS) { attempt ->
      val session = StructuredTextInferenceSession(appContext)
      try {
        session.connect()
        return session.generate(systemInstruction, userMessage, tool, snapshot)
      } catch (error: RemoteException) {
        lastRemoteError = error
        if (attempt == STRUCTURED_TEXT_PROCESS_MAX_ATTEMPTS - 1) throw error
      } finally {
        session.close()
      }
    }
    throw requireNotNull(lastRemoteError)
  }
}

private data class StructuredTextExecutionSnapshot(
  val modelId: String,
  val backend: LocalInferenceBackend,
  val speculativeDecodingEnabled: Boolean,
  val contextTokens: Int,
  val modelRevisions: Map<String, String>,
)

private fun captureStructuredTextSnapshot(
  context: Context,
  manager: LocalModelManager,
): StructuredTextExecutionSnapshot {
  val model = manager.selectedModel() ?: error("AIモデルをダウンロードして選択してください")
  val settings = manager.inferenceSettings.value
  val preferences = context.getSharedPreferences(STRUCTURED_TEXT_MODEL_PREFERENCES_NAME, Context.MODE_PRIVATE)
  val revisions = preferences.all.mapNotNull { (key, value) ->
    if (!key.startsWith("$STRUCTURED_TEXT_MODEL_REVISION_PREFIX.")) return@mapNotNull null
    val modelId = key.removePrefix("$STRUCTURED_TEXT_MODEL_REVISION_PREFIX.")
    val revision = value as? String ?: return@mapNotNull null
    modelId to revision
  }.toMap()
  check(model.id in revisions) { "選択したAIモデルの revision がありません" }
  return StructuredTextExecutionSnapshot(
    modelId = model.id,
    backend = settings.backend,
    speculativeDecodingEnabled = settings.speculativeDecodingEnabled,
    contextTokens = model.contextTokens,
    modelRevisions = revisions,
  )
}

private class StructuredTextInferenceSession(
  private val context: Context,
) : ServiceConnection {
  private val connected = CompletableDeferred<Messenger>()
  private val pendingResponse = AtomicReference<CompletableDeferred<Bundle>?>(null)
  private var bound = false
  private val replyMessenger = Messenger(
    Handler(Looper.getMainLooper()) { message ->
      if (message.what != MSG_STRUCTURED_RESULT) return@Handler false
      pendingResponse.getAndSet(null)?.complete(message.data)
      true
    },
  )

  suspend fun connect() {
    val intent = Intent(context, LocalStructuredTextInferenceService::class.java)
    if (!context.bindService(intent, this, Context.BIND_AUTO_CREATE)) {
      throw IllegalStateException("構造化ローカルAI推論プロセスを起動できません")
    }
    bound = true
    try {
      connected.await()
    } catch (error: Throwable) {
      close()
      throw error
    }
  }

  suspend fun generate(
    systemInstruction: String,
    userMessage: String,
    tool: AiStructuredTool,
    snapshot: StructuredTextExecutionSnapshot,
  ): AiStructuredToolCall? {
    val deferred = CompletableDeferred<Bundle>()
    check(pendingResponse.compareAndSet(null, deferred)) { "構造化ローカルAI推論要求が重複しています" }
    return try {
      val message = Message.obtain(null, MSG_STRUCTURED_GENERATE).apply {
        data = encodeStructuredRequest(systemInstruction, userMessage, tool, snapshot)
        replyTo = replyMessenger
      }
      connected.await().send(message)
      decodeStructuredResponse(deferred.await())
    } finally {
      pendingResponse.compareAndSet(deferred, null)
    }
  }

  fun close() {
    if (bound) {
      runCatching { context.unbindService(this) }
      bound = false
    }
  }

  override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
    if (service == null) {
      connected.completeExceptionally(IllegalStateException("構造化ローカルAI推論プロセスへ接続できません"))
    } else {
      connected.complete(Messenger(service))
    }
  }

  override fun onServiceDisconnected(name: ComponentName?) = onBinderDied()

  override fun onBindingDied(name: ComponentName?) = onBinderDied()

  override fun onNullBinding(name: ComponentName?) {
    connected.completeExceptionally(IllegalStateException("構造化ローカルAI推論プロセスが Binder を返しませんでした"))
  }

  private fun onBinderDied() {
    val error = DeadObjectException()
    if (!connected.isCompleted) connected.completeExceptionally(error)
    pendingResponse.getAndSet(null)?.completeExceptionally(error)
  }
}

class LocalStructuredTextInferenceService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private lateinit var processDiagnostics: LocalAiTextProcessDiagnosticSession
  private val messenger = Messenger(
    Handler(Looper.getMainLooper()) { message ->
      if (message.what != MSG_STRUCTURED_GENERATE || message.replyTo == null) return@Handler false
      val replyTo = message.replyTo
      val request = message.data
      scope.launch {
        val response = handleStructuredRequest(request)
        runCatching {
          replyTo.send(
            Message.obtain(null, MSG_STRUCTURED_RESULT).apply {
              data = response
            },
          )
        }
      }
      true
    },
  )

  override fun onCreate() {
    super.onCreate()
    processDiagnostics = LocalAiTextProcessDiagnostics.startSession(
      context = applicationContext,
      scope = scope,
      mode = LocalAiTextProcessMode.STRUCTURED,
    )
    processDiagnostics.start()
  }

  override fun onBind(intent: Intent?): IBinder = messenger.binder

  override fun onDestroy() {
    if (::processDiagnostics.isInitialized) processDiagnostics.stop()
    scope.cancel()
    applicationContext.deleteSharedPreferences(STRUCTURED_TEXT_CHILD_MODEL_PREFERENCES_NAME)
    applicationContext.deleteSharedPreferences(STRUCTURED_TEXT_CHILD_BENCHMARK_PREFERENCES_NAME)
    super.onDestroy()
    Process.killProcess(Process.myPid())
  }

  private fun handleStructuredRequest(bundle: Bundle): Bundle {
    val result = runCatching {
      val request = decodeStructuredRequest(bundle)
      processDiagnostics.mark(
        phase = LocalAiTextProcessPhase.REQUEST_RECEIVED,
        backend = request.snapshot.backend,
        contextTokens = request.snapshot.contextTokens,
        speculativeDecodingEnabled = request.snapshot.speculativeDecodingEnabled,
      )
      processDiagnostics.mark(LocalAiTextProcessPhase.PREPARING_MODEL)
      val context = StructuredTextSnapshotContext(applicationContext)
      context.applySnapshot(request.snapshot)
      LocalModelManager(context).use { manager ->
        val calls = mutableListOf<AiStructuredToolCall>()
        val localTool = request.tool.toLocalInferenceTool { arguments ->
          calls += AiStructuredToolCall(request.tool.name, arguments)
          "構造化出力を受理しました"
        }
        processDiagnostics.mark(LocalAiTextProcessPhase.GENERATING_RESPONSE)
        manager.generateConversation(
          LocalInferenceConversationRequest(
            systemInstruction = request.systemInstruction,
            initialMessages = emptyList(),
            userMessage = request.userMessage,
            tools = listOf(localTool),
          ),
        )
        require(calls.size == 1) { "構造化出力ツールは1回だけ呼び出してください" }
        calls.single()
      }
    }
    if (::processDiagnostics.isInitialized) {
      processDiagnostics.mark(LocalAiTextProcessPhase.COMPLETED)
    }
    return result.fold(
      onSuccess = ::structuredSuccessResponse,
      onFailure = { error -> structuredErrorResponse(error.structuredTextUserMessage()) },
    )
  }
}

private data class DecodedStructuredRequest(
  val systemInstruction: String,
  val userMessage: String,
  val tool: AiStructuredTool,
  val snapshot: StructuredTextExecutionSnapshot,
)

private fun AiStructuredTool.toLocalInferenceTool(
  execute: suspend (Map<String, String>) -> String,
): LocalInferenceTool = LocalInferenceTool(
  name = name,
  description = description,
  arguments = arguments.map { argument ->
    LocalInferenceToolArgument(
      name = argument.name,
      description = argument.description,
      required = argument.required,
      type = when (argument.type) {
        AiStructuredToolArgumentType.STRING -> LocalInferenceToolArgumentType.STRING
        AiStructuredToolArgumentType.INTEGER -> LocalInferenceToolArgumentType.INTEGER
        AiStructuredToolArgumentType.NUMBER -> LocalInferenceToolArgumentType.NUMBER
        AiStructuredToolArgumentType.BOOLEAN -> LocalInferenceToolArgumentType.BOOLEAN
        AiStructuredToolArgumentType.STRING_ARRAY -> LocalInferenceToolArgumentType.STRING_ARRAY
      },
    )
  },
  allowAdditionalArguments = allowAdditionalArguments,
  execute = execute,
)

private class StructuredTextSnapshotContext(base: Context) : ContextWrapper(base) {
  override fun getApplicationContext(): Context = this

  override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(
    when (name) {
      STRUCTURED_TEXT_MODEL_PREFERENCES_NAME -> STRUCTURED_TEXT_CHILD_MODEL_PREFERENCES_NAME
      STRUCTURED_TEXT_BENCHMARK_PREFERENCES_NAME -> STRUCTURED_TEXT_CHILD_BENCHMARK_PREFERENCES_NAME
      else -> name
    },
    mode,
  )

  fun applySnapshot(snapshot: StructuredTextExecutionSnapshot) {
    val contextMode = isolatedContextSizeMode(snapshot.contextTokens)
    val editor = getSharedPreferences(STRUCTURED_TEXT_MODEL_PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .clear()
      .putString(STRUCTURED_TEXT_SELECTED_MODEL_KEY, snapshot.modelId)
      .putString(STRUCTURED_TEXT_BACKEND_KEY, snapshot.backend.name)
      .putBoolean(STRUCTURED_TEXT_SPECULATIVE_DECODING_KEY, snapshot.speculativeDecodingEnabled)
      .putString(STRUCTURED_TEXT_CONTEXT_SIZE_MODE_KEY, contextMode.name)
    snapshot.modelRevisions.forEach { (modelId, revision) ->
      editor.putString("$STRUCTURED_TEXT_MODEL_REVISION_PREFIX.$modelId", revision)
    }
    check(editor.commit()) { "構造化ローカルAI推論設定の snapshot を作成できません" }
  }
}

private fun encodeStructuredRequest(
  systemInstruction: String,
  userMessage: String,
  tool: AiStructuredTool,
  snapshot: StructuredTextExecutionSnapshot,
): Bundle = Bundle().apply {
  putString(KEY_SYSTEM_INSTRUCTION, systemInstruction)
  putString(KEY_USER_MESSAGE, userMessage)
  putString(KEY_TOOL_NAME, tool.name)
  putString(KEY_TOOL_DESCRIPTION, tool.description)
  putBoolean(KEY_TOOL_ALLOW_ADDITIONAL, tool.allowAdditionalArguments)
  putStringArrayList(KEY_TOOL_ARGUMENT_NAMES, ArrayList(tool.arguments.map(AiStructuredToolArgument::name)))
  putStringArrayList(
    KEY_TOOL_ARGUMENT_DESCRIPTIONS,
    ArrayList(tool.arguments.map(AiStructuredToolArgument::description)),
  )
  putStringArrayList(KEY_TOOL_ARGUMENT_TYPES, ArrayList(tool.arguments.map { it.type.name }))
  putBooleanArray(KEY_TOOL_ARGUMENT_REQUIRED, tool.arguments.map(AiStructuredToolArgument::required).toBooleanArray())
  putString(KEY_MODEL_ID, snapshot.modelId)
  putString(KEY_BACKEND, snapshot.backend.name)
  putBoolean(KEY_SPECULATIVE_DECODING, snapshot.speculativeDecodingEnabled)
  putInt(KEY_CONTEXT_TOKENS, snapshot.contextTokens)
  val revisions = snapshot.modelRevisions.entries.sortedBy(Map.Entry<String, String>::key)
  putStringArrayList(KEY_MODEL_REVISION_IDS, ArrayList(revisions.map(Map.Entry<String, String>::key)))
  putStringArrayList(KEY_MODEL_REVISION_VALUES, ArrayList(revisions.map(Map.Entry<String, String>::value)))
}

private fun decodeStructuredRequest(bundle: Bundle): DecodedStructuredRequest {
  val systemInstruction = requireNotNull(bundle.getString(KEY_SYSTEM_INSTRUCTION)) { "system instruction がありません" }
  val userMessage = requireNotNull(bundle.getString(KEY_USER_MESSAGE)) { "user message がありません" }
  require(systemInstruction.isNotBlank() && userMessage.isNotBlank()) { "構造化推論プロンプトが空です" }
  require(systemInstruction.length + userMessage.length <= STRUCTURED_TEXT_IPC_MAX_CHARS) {
    "構造化推論プロンプトが長すぎます"
  }

  val argumentNames = requireNotNull(bundle.getStringArrayList(KEY_TOOL_ARGUMENT_NAMES)) { "tool arguments がありません" }
  val descriptions = requireNotNull(bundle.getStringArrayList(KEY_TOOL_ARGUMENT_DESCRIPTIONS)) { "tool argument descriptions がありません" }
  val types = requireNotNull(bundle.getStringArrayList(KEY_TOOL_ARGUMENT_TYPES)) { "tool argument types がありません" }
  val required = requireNotNull(bundle.getBooleanArray(KEY_TOOL_ARGUMENT_REQUIRED)) { "tool argument required flags がありません" }
  require(argumentNames.size == descriptions.size && argumentNames.size == types.size && argumentNames.size == required.size) {
    "tool argument schema が不正です"
  }
  val arguments = argumentNames.indices.map { index ->
    AiStructuredToolArgument(
      name = argumentNames[index],
      description = descriptions[index],
      required = required[index],
      type = runCatching { AiStructuredToolArgumentType.valueOf(types[index]) }
        .getOrElse { throw IllegalArgumentException("tool argument type が不正です") },
    )
  }
  val tool = AiStructuredTool(
    name = requireNotNull(bundle.getString(KEY_TOOL_NAME)) { "tool name がありません" },
    description = requireNotNull(bundle.getString(KEY_TOOL_DESCRIPTION)) { "tool description がありません" },
    arguments = arguments,
    allowAdditionalArguments = bundle.getBoolean(KEY_TOOL_ALLOW_ADDITIONAL),
  )

  val modelId = requireNotNull(bundle.getString(KEY_MODEL_ID)) { "AIモデルがありません" }
  val backend = requireNotNull(bundle.getString(KEY_BACKEND)) { "AI backend がありません" }
    .let { value -> runCatching { LocalInferenceBackend.valueOf(value) }.getOrElse { throw IllegalArgumentException("AI backend が不正です") } }
  val contextTokens = bundle.getInt(KEY_CONTEXT_TOKENS)
  isolatedContextSizeMode(contextTokens)
  val revisionIds = requireNotNull(bundle.getStringArrayList(KEY_MODEL_REVISION_IDS)) { "AIモデル revision id がありません" }
  val revisionValues = requireNotNull(bundle.getStringArrayList(KEY_MODEL_REVISION_VALUES)) { "AIモデル revision value がありません" }
  require(revisionIds.size == revisionValues.size) { "AIモデル revision snapshot が不正です" }
  val revisions = revisionIds.zip(revisionValues).toMap()
  require(modelId in revisions) { "選択したAIモデルの revision がありません" }

  return DecodedStructuredRequest(
    systemInstruction = systemInstruction,
    userMessage = userMessage,
    tool = tool,
    snapshot = StructuredTextExecutionSnapshot(
      modelId = modelId,
      backend = backend,
      speculativeDecodingEnabled = bundle.getBoolean(KEY_SPECULATIVE_DECODING),
      contextTokens = contextTokens,
      modelRevisions = revisions,
    ),
  )
}

private fun structuredSuccessResponse(call: AiStructuredToolCall): Bundle = Bundle().apply {
  putBoolean(KEY_SUCCESS, true)
  putString(KEY_CALL_NAME, call.name)
  val entries = call.arguments.entries.sortedBy(Map.Entry<String, String>::key)
  putStringArrayList(KEY_CALL_ARGUMENT_NAMES, ArrayList(entries.map(Map.Entry<String, String>::key)))
  putStringArrayList(KEY_CALL_ARGUMENT_VALUES, ArrayList(entries.map(Map.Entry<String, String>::value)))
}

private fun structuredErrorResponse(error: String): Bundle = Bundle().apply {
  putBoolean(KEY_SUCCESS, false)
  putString(KEY_ERROR, error.take(STRUCTURED_TEXT_MAX_ERROR_CHARS))
}

private fun decodeStructuredResponse(bundle: Bundle): AiStructuredToolCall? {
  if (!bundle.getBoolean(KEY_SUCCESS)) {
    throw IllegalStateException(bundle.getString(KEY_ERROR) ?: "構造化ローカルAI推論に失敗しました")
  }
  val name = bundle.getString(KEY_CALL_NAME) ?: return null
  val argumentNames = requireNotNull(bundle.getStringArrayList(KEY_CALL_ARGUMENT_NAMES)) { "tool call arguments がありません" }
  val argumentValues = requireNotNull(bundle.getStringArrayList(KEY_CALL_ARGUMENT_VALUES)) { "tool call argument values がありません" }
  require(argumentNames.size == argumentValues.size) { "tool call arguments が不正です" }
  return AiStructuredToolCall(name = name, arguments = argumentNames.zip(argumentValues).toMap())
}

private fun Throwable.structuredTextUserMessage(): String = when (this) {
  is IllegalArgumentException, is IllegalStateException -> message?.takeIf(String::isNotBlank)
  else -> null
}?.take(STRUCTURED_TEXT_MAX_ERROR_CHARS)
  ?: "構造化ローカルAI推論に失敗しました (${javaClass.simpleName})"