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
import dev.terashima.yomitorirss.core.aiinference.BackgroundAiTextInference
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceModel
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceProgress
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceStage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class LocalTextInferenceService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val requestMutex = Mutex()
  private val inFlight = AtomicBoolean(false)
  private val batchPolicy = TextInferenceProcessBatchPolicy()
  private val toolBridge = ChildToolExecutionBridge()
  private var isolatedContext: TextInferenceSnapshotContext? = null
  private var modelManager: LocalModelManager? = null
  private lateinit var processDiagnostics: LocalAiTextProcessDiagnosticSession
  private val messenger = Messenger(
    Handler(Looper.getMainLooper()) { message ->
      when (message.what) {
        MSG_GENERATE, MSG_GENERATE_CONVERSATION -> {
          if (message.replyTo == null) return@Handler false
          val replyTo = message.replyTo
          val request = message.data
          val requestType = message.what
          scope.launch {
            val response = requestMutex.withLock {
              if (requestType == MSG_GENERATE_CONVERSATION) {
                handleConversationRequest(request, replyTo)
              } else {
                handleRequest(request, replyTo)
              }
            }
            runCatching {
              replyTo.send(
                Message.obtain(null, MSG_RESULT).apply {
                  data = response
                },
              )
            }
          }
          true
        }
        MSG_TOOL_RESULT -> {
          toolBridge.complete(message.data)
          true
        }
        else -> false
      }
    },
  )

  override fun onCreate() {
    super.onCreate()
    processDiagnostics = LocalAiTextProcessDiagnostics.startSession(
      context = applicationContext,
      scope = scope,
      mode = LocalAiTextProcessMode.TEXT,
    )
    processDiagnostics.start()
  }

  override fun onBind(intent: Intent?): IBinder = messenger.binder

  override fun onDestroy() {
    val activeInference = inFlight.get()
    if (::processDiagnostics.isInitialized) processDiagnostics.stop()
    toolBridge.cancel()
    scope.cancel()
    if (!activeInference) {
      runCatching { modelManager?.close() }
    }
    modelManager = null
    isolatedContext = null
    applicationContext.deleteSharedPreferences(TEXT_INFERENCE_CHILD_MODEL_PREFERENCES_NAME)
    applicationContext.deleteSharedPreferences(TEXT_INFERENCE_CHILD_BENCHMARK_PREFERENCES_NAME)
    super.onDestroy()
    Process.killProcess(Process.myPid())
  }

  private suspend fun handleRequest(
    bundle: Bundle,
    replyTo: Messenger,
  ): Bundle {
    inFlight.set(true)
    var snapshot: TextInferenceExecutionSnapshot? = null
    val result = try {
      val request = decodeRequest(bundle)
      snapshot = request.snapshot
      processDiagnostics.setMode(LocalAiTextProcessMode.TEXT)
      processDiagnostics.mark(
        phase = LocalAiTextProcessPhase.REQUEST_RECEIVED,
        backend = request.snapshot.backend,
        contextTokens = request.snapshot.contextTokens,
        speculativeDecodingEnabled = request.snapshot.speculativeDecodingEnabled,
      )
      processDiagnostics.mark(LocalAiTextProcessPhase.PREPARING_MODEL)
      val manager = acquireManager(request.snapshot)
      coroutineScope {
        val progressJob = launch {
          manager.inferenceProgress
            .filterNotNull()
            .collect { progress ->
              when (progress.stage.name) {
                "PREPARING_MODEL" -> processDiagnostics.mark(LocalAiTextProcessPhase.PREPARING_MODEL)
                "GENERATING_RESPONSE" -> processDiagnostics.mark(LocalAiTextProcessPhase.GENERATING_RESPONSE)
              }
              sendProgress(replyTo, progress)
            }
        }
        try {
          manager.generate(request.prompt).also { output ->
            require(output.length <= TEXT_INFERENCE_IPC_MAX_CHARS) { "ローカルAI推論結果が長すぎます" }
          }
        } finally {
          progressJob.cancel()
        }
      }
    } catch (error: Throwable) {
      error
    } finally {
      inFlight.set(false)
      if (::processDiagnostics.isInitialized) {
        processDiagnostics.mark(LocalAiTextProcessPhase.COMPLETED)
      }
    }

    val retire = batchPolicy.requestFinished()
    val durations = snapshot?.let(::readChildStageDurations) ?: (null to null)
    return if (result is String) {
      successResponse(result, retire, durations)
    } else {
      errorResponse((result as Throwable).textInferenceUserMessage(), retire, durations)
    }
  }

  private suspend fun handleConversationRequest(
    bundle: Bundle,
    replyTo: Messenger,
  ): Bundle {
    inFlight.set(true)
    var snapshot: TextInferenceExecutionSnapshot? = null
    val result = try {
      val decoded = decodeConversationRequest(bundle)
      snapshot = decoded.snapshot
      processDiagnostics.setMode(LocalAiTextProcessMode.CHAT)
      processDiagnostics.mark(
        phase = LocalAiTextProcessPhase.REQUEST_RECEIVED,
        backend = decoded.snapshot.backend,
        contextTokens = decoded.snapshot.contextTokens,
        speculativeDecodingEnabled = decoded.snapshot.speculativeDecodingEnabled,
      )
      processDiagnostics.mark(LocalAiTextProcessPhase.PREPARING_MODEL)
      val manager = acquireManager(decoded.snapshot)
      val tools = decoded.toolDefinitions.map { definition ->
        definition.toTool { arguments ->
          toolBridge.execute(
            mainProcess = replyTo,
            childProcess = messenger,
            toolName = definition.name,
            arguments = arguments,
          )
        }
      }
      coroutineScope {
        val progressJob = launch {
          manager.inferenceProgress
            .filterNotNull()
            .collect { progress ->
              when (progress.stage.name) {
                "PREPARING_MODEL" -> processDiagnostics.mark(LocalAiTextProcessPhase.PREPARING_MODEL)
                "GENERATING_RESPONSE" -> processDiagnostics.mark(LocalAiTextProcessPhase.GENERATING_RESPONSE)
              }
              sendProgress(replyTo, progress)
            }
        }
        try {
          manager.generateConversation(
            request = LocalInferenceConversationRequest(
              systemInstruction = decoded.systemInstruction,
              initialMessages = decoded.initialMessages,
              userMessage = decoded.userMessage,
              tools = tools,
            ),
            streaming = decoded.streaming,
          ) { chunk ->
            sendStreamChunk(replyTo, chunk)
          }.also { output ->
            require(output.length <= TEXT_INFERENCE_IPC_MAX_CHARS) { "ローカルAI推論結果が長すぎます" }
          }
        } finally {
          progressJob.cancel()
        }
      }
    } catch (error: Throwable) {
      error
    } finally {
      inFlight.set(false)
      if (::processDiagnostics.isInitialized) {
        processDiagnostics.mark(LocalAiTextProcessPhase.COMPLETED)
      }
    }

    val retire = batchPolicy.requestFinished()
    val durations = snapshot?.let(::readChildStageDurations) ?: (null to null)
    return if (result is String) {
      successResponse(result, retire, durations)
    } else {
      errorResponse((result as Throwable).textInferenceUserMessage(), retire, durations)
    }
  }

  private fun acquireManager(snapshot: TextInferenceExecutionSnapshot): LocalModelManager {
    val context = isolatedContext ?: TextInferenceSnapshotContext(applicationContext).also {
      isolatedContext = it
    }
    context.applySnapshot(snapshot)
    return modelManager ?: LocalModelManager.shared(context).also { modelManager = it }
  }

  private fun readChildStageDurations(snapshot: TextInferenceExecutionSnapshot): Pair<Long?, Long?> {
    val context = isolatedContext ?: return null to null
    val preferences = context.getSharedPreferences(MAIN_MODEL_PREFERENCES_NAME, Context.MODE_PRIVATE)
    return preferences.getLong(stageDurationKey(PREPARING_MODEL_DURATION_KEY, snapshot.modelId), 0)
      .takeIf { it > 0 } to
      preferences.getLong(stageDurationKey(GENERATING_RESPONSE_DURATION_KEY, snapshot.modelId), 0)
        .takeIf { it > 0 }
  }
}

private class ChildToolExecutionBridge {
  private val nextCallId = AtomicLong(1L)
  private val pending = AtomicReference<Pair<Long, CompletableDeferred<Bundle>>?>(null)

  suspend fun execute(
    mainProcess: Messenger,
    childProcess: Messenger,
    toolName: String,
    arguments: Map<String, String>,
  ): String {
    val callId = nextCallId.getAndIncrement()
    val deferred = CompletableDeferred<Bundle>()
    val pendingCall = callId to deferred
    check(pending.compareAndSet(null, pendingCall)) { "tool call が重複しています" }
    return try {
      val entries = arguments.entries.sortedBy(Map.Entry<String, String>::key)
      mainProcess.send(
        Message.obtain(null, MSG_TOOL_CALL).apply {
          data = Bundle().apply {
            putLong(TEXT_KEY_TOOL_CALL_ID, callId)
            putString(TEXT_KEY_TOOL_NAME, toolName)
            putStringArrayList(TEXT_KEY_TOOL_ARGUMENT_NAMES, ArrayList(entries.map(Map.Entry<String, String>::key)))
            putStringArrayList(TEXT_KEY_TOOL_ARGUMENT_VALUES, ArrayList(entries.map(Map.Entry<String, String>::value)))
          }
          replyTo = childProcess
        },
      )
      val result = deferred.await()
      check(result.getBoolean(TEXT_KEY_SUCCESS)) { "tool execution に失敗しました" }
      requireNotNull(result.getString(TEXT_KEY_TOOL_RESULT)) { "tool result がありません" }
    } finally {
      pending.compareAndSet(pendingCall, null)
    }
  }

  fun complete(bundle: Bundle) {
    val callId = bundle.getLong(TEXT_KEY_TOOL_CALL_ID)
    val current = pending.get() ?: return
    if (current.first != callId) return
    if (pending.compareAndSet(current, null)) current.second.complete(bundle)
  }

  fun cancel() {
    pending.getAndSet(null)?.second?.completeExceptionally(DeadObjectException())
  }
}

private class TextInferenceSnapshotContext(base: Context) : ContextWrapper(base) {
  override fun getApplicationContext(): Context = this

  override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(
    when (name) {
      MAIN_MODEL_PREFERENCES_NAME -> TEXT_INFERENCE_CHILD_MODEL_PREFERENCES_NAME
      MAIN_CONTEXT_BENCHMARK_PREFERENCES_NAME -> TEXT_INFERENCE_CHILD_BENCHMARK_PREFERENCES_NAME
      else -> name
    },
    mode,
  )

  fun applySnapshot(snapshot: TextInferenceExecutionSnapshot) {
    val contextMode = isolatedContextSizeMode(snapshot.contextTokens)
    val editor = getSharedPreferences(MAIN_MODEL_PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .clear()
      .putString(SELECTED_MODEL_KEY, snapshot.modelId)
      .putString(INFERENCE_BACKEND_KEY, snapshot.backend.name)
      .putBoolean(SPECULATIVE_DECODING_ENABLED_KEY, snapshot.speculativeDecodingEnabled)
      .putString(CONTEXT_SIZE_MODE_KEY, contextMode.name)
    snapshot.modelRevisions.forEach { (modelId, revision) ->
      editor.putString("$MODEL_REVISION_KEY_PREFIX.$modelId", revision)
    }
    snapshot.preparingDurationMillis?.let {
      editor.putLong(stageDurationKey(PREPARING_MODEL_DURATION_KEY, snapshot.modelId), it)
    }
    snapshot.generatingDurationMillis?.let {
      editor.putLong(stageDurationKey(GENERATING_RESPONSE_DURATION_KEY, snapshot.modelId), it)
    }
    check(editor.commit()) { "ローカルAI推論設定の snapshot を作成できません" }
  }
}

private data class DecodedTextInferenceRequest(
  val prompt: String,
  val snapshot: TextInferenceExecutionSnapshot,
)

internal fun encodeRequest(
  prompt: String,
  snapshot: TextInferenceExecutionSnapshot,
): Bundle = Bundle().apply {
  putString(TEXT_KEY_PROMPT, prompt)
  putString(TEXT_KEY_MODEL_ID, snapshot.modelId)
  putString(TEXT_KEY_BACKEND, snapshot.backend.name)
  putBoolean(TEXT_KEY_SPECULATIVE_DECODING, snapshot.speculativeDecodingEnabled)
  putInt(TEXT_KEY_CONTEXT_TOKENS, snapshot.contextTokens)
  val revisionEntries = snapshot.modelRevisions.entries.sortedBy(Map.Entry<String, String>::key)
  putStringArrayList(TEXT_KEY_MODEL_REVISION_IDS, ArrayList(revisionEntries.map(Map.Entry<String, String>::key)))
  putStringArrayList(TEXT_KEY_MODEL_REVISION_VALUES, ArrayList(revisionEntries.map(Map.Entry<String, String>::value)))
  snapshot.preparingDurationMillis?.let { putLong(TEXT_KEY_PREPARING_DURATION_MILLIS, it) }
  snapshot.generatingDurationMillis?.let { putLong(TEXT_KEY_GENERATING_DURATION_MILLIS, it) }
}

private fun decodeRequest(bundle: Bundle): DecodedTextInferenceRequest {
  val prompt = requireNotNull(bundle.getString(TEXT_KEY_PROMPT)) { "推論プロンプトがありません" }
  require(prompt.isNotBlank()) { "推論プロンプトを入力してください" }
  require(prompt.length <= TEXT_INFERENCE_IPC_MAX_CHARS) { "推論プロンプトが長すぎます" }
  val modelId = requireNotNull(bundle.getString(TEXT_KEY_MODEL_ID)) { "AIモデルがありません" }
  val backendName = requireNotNull(bundle.getString(TEXT_KEY_BACKEND)) { "AI backend がありません" }
  val backend = runCatching { LocalInferenceBackend.valueOf(backendName) }
    .getOrElse { throw IllegalArgumentException("AI backend が不正です") }
  val contextTokens = bundle.getInt(TEXT_KEY_CONTEXT_TOKENS)
  isolatedContextSizeMode(contextTokens)
  val revisionIds = requireNotNull(bundle.getStringArrayList(TEXT_KEY_MODEL_REVISION_IDS)) {
    "AIモデル revision id がありません"
  }
  val revisionValues = requireNotNull(bundle.getStringArrayList(TEXT_KEY_MODEL_REVISION_VALUES)) {
    "AIモデル revision value がありません"
  }
  require(revisionIds.size == revisionValues.size) { "AIモデル revision snapshot が不正です" }
  val modelRevisions = revisionIds.zip(revisionValues).toMap()
  require(modelId in modelRevisions) { "選択したAIモデルの revision がありません" }
  return DecodedTextInferenceRequest(
    prompt = prompt,
    snapshot = TextInferenceExecutionSnapshot(
      modelId = modelId,
      backend = backend,
      speculativeDecodingEnabled = bundle.getBoolean(TEXT_KEY_SPECULATIVE_DECODING),
      contextTokens = contextTokens,
      modelRevisions = modelRevisions,
      preparingDurationMillis = bundle.getLong(TEXT_KEY_PREPARING_DURATION_MILLIS)
        .takeIf { bundle.containsKey(TEXT_KEY_PREPARING_DURATION_MILLIS) && it > 0 },
      generatingDurationMillis = bundle.getLong(TEXT_KEY_GENERATING_DURATION_MILLIS)
        .takeIf { bundle.containsKey(TEXT_KEY_GENERATING_DURATION_MILLIS) && it > 0 },
    ),
  )
}

private data class DecodedConversationRequest(
  val systemInstruction: String,
  val initialMessages: List<LocalInferenceMessage>,
  val userMessage: String,
  val toolDefinitions: List<LocalInferenceToolDefinition>,
  val streaming: Boolean,
  val snapshot: TextInferenceExecutionSnapshot,
)

internal fun encodeConversationRequest(
  request: LocalInferenceConversationRequest,
  streaming: Boolean,
  snapshot: TextInferenceExecutionSnapshot,
): Bundle = Bundle().apply {
  require(conversationIpcCharacterCount(request) <= TEXT_INFERENCE_IPC_MAX_CHARS) {
    "AIチャットの入力が長すぎます"
  }
  putString(TEXT_KEY_SYSTEM_INSTRUCTION, request.systemInstruction)
  putString(TEXT_KEY_USER_MESSAGE, request.userMessage)
  putStringArrayList(
    TEXT_KEY_INITIAL_MESSAGE_ROLES,
    ArrayList(request.initialMessages.map { it.role.name }),
  )
  putStringArrayList(
    TEXT_KEY_INITIAL_MESSAGE_CONTENTS,
    ArrayList(request.initialMessages.map(LocalInferenceMessage::content)),
  )
  putStringArrayList(
    TEXT_KEY_TOOL_SCHEMAS,
    ArrayList(request.tools.map(::toolDescriptionJson)),
  )
  putBoolean(TEXT_KEY_STREAMING, streaming)
  putString(TEXT_KEY_MODEL_ID, snapshot.modelId)
  putString(TEXT_KEY_BACKEND, snapshot.backend.name)
  putBoolean(TEXT_KEY_SPECULATIVE_DECODING, snapshot.speculativeDecodingEnabled)
  putInt(TEXT_KEY_CONTEXT_TOKENS, snapshot.contextTokens)
  val revisionEntries = snapshot.modelRevisions.entries.sortedBy(Map.Entry<String, String>::key)
  putStringArrayList(TEXT_KEY_MODEL_REVISION_IDS, ArrayList(revisionEntries.map(Map.Entry<String, String>::key)))
  putStringArrayList(TEXT_KEY_MODEL_REVISION_VALUES, ArrayList(revisionEntries.map(Map.Entry<String, String>::value)))
  snapshot.preparingDurationMillis?.let { putLong(TEXT_KEY_PREPARING_DURATION_MILLIS, it) }
  snapshot.generatingDurationMillis?.let { putLong(TEXT_KEY_GENERATING_DURATION_MILLIS, it) }
}

private fun decodeConversationRequest(bundle: Bundle): DecodedConversationRequest {
  val systemInstruction = requireNotNull(bundle.getString(TEXT_KEY_SYSTEM_INSTRUCTION)) {
    "system instruction がありません"
  }
  val userMessage = requireNotNull(bundle.getString(TEXT_KEY_USER_MESSAGE)) { "user message がありません" }
  val roles = requireNotNull(bundle.getStringArrayList(TEXT_KEY_INITIAL_MESSAGE_ROLES)) {
    "conversation role がありません"
  }
  val contents = requireNotNull(bundle.getStringArrayList(TEXT_KEY_INITIAL_MESSAGE_CONTENTS)) {
    "conversation message がありません"
  }
  require(roles.size == contents.size) { "conversation history が不正です" }
  val initialMessages = roles.indices.map { index ->
    LocalInferenceMessage(
      role = runCatching { LocalInferenceMessageRole.valueOf(roles[index]) }
        .getOrElse { throw IllegalArgumentException("conversation role が不正です") },
      content = contents[index],
    )
  }
  val toolDefinitions = bundle.getStringArrayList(TEXT_KEY_TOOL_SCHEMAS)
    .orEmpty()
    .map(::parseToolDefinitionJson)
  val modelId = requireNotNull(bundle.getString(TEXT_KEY_MODEL_ID)) { "AIモデルがありません" }
  val backendName = requireNotNull(bundle.getString(TEXT_KEY_BACKEND)) { "AI backend がありません" }
  val backend = runCatching { LocalInferenceBackend.valueOf(backendName) }
    .getOrElse { throw IllegalArgumentException("AI backend が不正です") }
  val contextTokens = bundle.getInt(TEXT_KEY_CONTEXT_TOKENS)
  isolatedContextSizeMode(contextTokens)
  val revisionIds = requireNotNull(bundle.getStringArrayList(TEXT_KEY_MODEL_REVISION_IDS)) {
    "AIモデル revision id がありません"
  }
  val revisionValues = requireNotNull(bundle.getStringArrayList(TEXT_KEY_MODEL_REVISION_VALUES)) {
    "AIモデル revision value がありません"
  }
  require(revisionIds.size == revisionValues.size) { "AIモデル revision snapshot が不正です" }
  val modelRevisions = revisionIds.zip(revisionValues).toMap()
  require(modelId in modelRevisions) { "選択したAIモデルの revision がありません" }
  val request = LocalInferenceConversationRequest(
    systemInstruction = systemInstruction,
    initialMessages = initialMessages,
    userMessage = userMessage,
    tools = toolDefinitions.map { definition -> definition.toTool { "" } },
  )
  require(conversationIpcCharacterCount(request) <= TEXT_INFERENCE_IPC_MAX_CHARS) {
    "AIチャットの入力が長すぎます"
  }
  return DecodedConversationRequest(
    systemInstruction = systemInstruction,
    initialMessages = initialMessages,
    userMessage = userMessage,
    toolDefinitions = toolDefinitions,
    streaming = bundle.getBoolean(TEXT_KEY_STREAMING),
    snapshot = TextInferenceExecutionSnapshot(
      modelId = modelId,
      backend = backend,
      speculativeDecodingEnabled = bundle.getBoolean(TEXT_KEY_SPECULATIVE_DECODING),
      contextTokens = contextTokens,
      modelRevisions = modelRevisions,
      preparingDurationMillis = bundle.getLong(TEXT_KEY_PREPARING_DURATION_MILLIS)
        .takeIf { bundle.containsKey(TEXT_KEY_PREPARING_DURATION_MILLIS) && it > 0 },
      generatingDurationMillis = bundle.getLong(TEXT_KEY_GENERATING_DURATION_MILLIS)
        .takeIf { bundle.containsKey(TEXT_KEY_GENERATING_DURATION_MILLIS) && it > 0 },
    ),
  )
}

private fun sendStreamChunk(
  replyTo: Messenger,
  chunk: String,
) {
  if (chunk.isEmpty() || chunk.length > TEXT_INFERENCE_IPC_MAX_CHARS) return
  runCatching {
    replyTo.send(
      Message.obtain(null, MSG_STREAM_CHUNK).apply {
        data = Bundle().apply {
          putString(TEXT_KEY_STREAM_CHUNK, chunk)
        }
      },
    )
  }
}

private fun sendProgress(
  replyTo: Messenger,
  progress: LocalInferenceProgress,
) {
  runCatching {
    replyTo.send(
      Message.obtain(null, MSG_PROGRESS).apply {
        data = Bundle().apply {
          putString(TEXT_KEY_STAGE, progress.stage.name)
          progress.modelName?.let { putString(TEXT_KEY_MODEL_NAME, it) }
          progress.estimatedStageDurationMillis?.let {
            putLong(TEXT_KEY_ESTIMATED_STAGE_DURATION_MILLIS, it)
          }
        }
      },
    )
  }
}

internal fun decodeProgress(bundle: Bundle): AiTextInferenceProgress? {
  val stage = bundle.getString(TEXT_KEY_STAGE)
    ?.let { runCatching { AiTextInferenceStage.valueOf(it) }.getOrNull() }
    ?: return null
  return AiTextInferenceProgress(
    stage = stage,
    modelName = bundle.getString(TEXT_KEY_MODEL_NAME),
    estimatedStageDurationMillis = bundle.getLong(TEXT_KEY_ESTIMATED_STAGE_DURATION_MILLIS)
      .takeIf { bundle.containsKey(TEXT_KEY_ESTIMATED_STAGE_DURATION_MILLIS) },
  )
}

private fun successResponse(
  output: String,
  retire: Boolean,
  durations: Pair<Long?, Long?>,
): Bundle = Bundle().apply {
  putBoolean(TEXT_KEY_SUCCESS, true)
  putBoolean(TEXT_KEY_RETIRE, retire)
  putString(TEXT_KEY_OUTPUT, output)
  durations.first?.let { putLong(TEXT_KEY_PREPARING_DURATION_MILLIS, it) }
  durations.second?.let { putLong(TEXT_KEY_GENERATING_DURATION_MILLIS, it) }
}

private fun errorResponse(
  error: String,
  retire: Boolean,
  durations: Pair<Long?, Long?>,
): Bundle = Bundle().apply {
  putBoolean(TEXT_KEY_SUCCESS, false)
  putBoolean(TEXT_KEY_RETIRE, retire)
  putString(TEXT_KEY_ERROR, error.take(MAX_ERROR_CHARS))
  durations.first?.let { putLong(TEXT_KEY_PREPARING_DURATION_MILLIS, it) }
  durations.second?.let { putLong(TEXT_KEY_GENERATING_DURATION_MILLIS, it) }
}

internal fun decodeResponse(bundle: Bundle): RemoteTextInferenceResponse {
  val retire = bundle.getBoolean(TEXT_KEY_RETIRE)
  val preparingDuration = bundle.getLong(TEXT_KEY_PREPARING_DURATION_MILLIS)
    .takeIf { bundle.containsKey(TEXT_KEY_PREPARING_DURATION_MILLIS) && it > 0 }
  val generatingDuration = bundle.getLong(TEXT_KEY_GENERATING_DURATION_MILLIS)
    .takeIf { bundle.containsKey(TEXT_KEY_GENERATING_DURATION_MILLIS) && it > 0 }
  if (!bundle.getBoolean(TEXT_KEY_SUCCESS)) {
    return RemoteTextInferenceResponse(
      output = null,
      error = bundle.getString(TEXT_KEY_ERROR) ?: "ローカルAI推論に失敗しました",
      retireAfterResponse = retire,
      preparingDurationMillis = preparingDuration,
      generatingDurationMillis = generatingDuration,
    )
  }
  return RemoteTextInferenceResponse(
    output = requireNotNull(bundle.getString(TEXT_KEY_OUTPUT)) { "ローカルAI推論結果がありません" },
    error = null,
    retireAfterResponse = retire,
    preparingDurationMillis = preparingDuration,
    generatingDurationMillis = generatingDuration,
  )
}

internal fun persistStageDurations(
  context: Context,
  snapshot: TextInferenceExecutionSnapshot,
  response: RemoteTextInferenceResponse,
) {
  if (response.preparingDurationMillis == null && response.generatingDurationMillis == null) return
  val editor = context.getSharedPreferences(MAIN_MODEL_PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
  response.preparingDurationMillis?.let {
    editor.putLong(stageDurationKey(PREPARING_MODEL_DURATION_KEY, snapshot.modelId), it)
  }
  response.generatingDurationMillis?.let {
    editor.putLong(stageDurationKey(GENERATING_RESPONSE_DURATION_KEY, snapshot.modelId), it)
  }
  editor.apply()
}

internal fun stageDurationKey(stage: String, modelId: String): String = "$stage.$modelId.duration_millis"

internal fun Throwable.hasLocalToolCallParseFailure(): Boolean =
  generateSequence(this) { error -> error.cause }
    .mapNotNull(Throwable::message)
    .any { message ->
      message.contains("Failed to parse tool calls", ignoreCase = true) ||
        message.contains("Failed to parse FC tool calls", ignoreCase = true)
    }

internal fun Throwable.textInferenceUserMessage(): String =
  if (hasLocalToolCallParseFailure()) {
    "Failed to parse tool calls"
  } else {
    when (this) {
      is IllegalArgumentException, is IllegalStateException ->
        message?.takeIf(String::isNotBlank)?.take(MAX_ERROR_CHARS)
      else -> null
    } ?: "ローカルAI推論に失敗しました (${javaClass.simpleName})"
  }