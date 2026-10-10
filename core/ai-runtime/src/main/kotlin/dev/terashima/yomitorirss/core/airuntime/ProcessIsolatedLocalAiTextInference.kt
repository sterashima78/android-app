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

internal const val TEXT_INFERENCE_PROCESS_BATCH_SIZE = 2
internal const val TEXT_INFERENCE_IPC_MAX_CHARS = 128 * 1024
internal const val TEXT_INFERENCE_CHILD_MODEL_PREFERENCES_NAME = "local_ai_text_model_snapshot"
internal const val TEXT_INFERENCE_CHILD_BENCHMARK_PREFERENCES_NAME = "local_ai_text_context_snapshot"
internal const val TEXT_INFERENCE_PROCESS_IDLE_MILLIS = 30_000L
internal const val TEXT_INFERENCE_CONNECT_TIMEOUT_MILLIS = 30_000L
internal const val TEXT_INFERENCE_DEFAULT_REQUEST_TIMEOUT_MILLIS = 10 * 60_000L
internal const val TEXT_INFERENCE_MIN_REQUEST_TIMEOUT_MILLIS = 5 * 60_000L
internal const val TEXT_INFERENCE_MAX_REQUEST_TIMEOUT_MILLIS = 20 * 60_000L
internal const val TEXT_INFERENCE_REQUEST_TIMEOUT_MULTIPLIER = 4L
internal const val TEXT_INFERENCE_REQUEST_TIMEOUT_GRACE_MILLIS = 60_000L
internal const val PROCESS_DEATH_WAIT_MILLIS = 10_000L
internal const val MAX_ERROR_CHARS = 500

internal const val MAIN_MODEL_PREFERENCES_NAME = "local_summary_models"
internal const val MAIN_CONTEXT_BENCHMARK_PREFERENCES_NAME = "local_context_benchmarks"
internal const val SELECTED_MODEL_KEY = "selected_model_id"
internal const val INFERENCE_BACKEND_KEY = "inference_backend"
internal const val SPECULATIVE_DECODING_ENABLED_KEY = "speculative_decoding_enabled"
internal const val CONTEXT_SIZE_MODE_KEY = "context_size_mode"
internal const val MODEL_REVISION_KEY_PREFIX = "model_revision"
internal const val PREPARING_MODEL_DURATION_KEY = "preparing_model"
internal const val GENERATING_RESPONSE_DURATION_KEY = "generating_response"

internal const val MSG_GENERATE = 1
internal const val MSG_RESULT = 2
internal const val MSG_PROGRESS = 3
internal const val MSG_GENERATE_CONVERSATION = 4
internal const val MSG_STREAM_CHUNK = 5
internal const val MSG_TOOL_CALL = 6
internal const val MSG_TOOL_RESULT = 7
internal const val KEY_PROMPT = "prompt"
internal const val KEY_SYSTEM_INSTRUCTION = "system_instruction"
internal const val KEY_USER_MESSAGE = "user_message"
internal const val KEY_INITIAL_MESSAGE_ROLES = "initial_message_roles"
internal const val KEY_INITIAL_MESSAGE_CONTENTS = "initial_message_contents"
internal const val KEY_TOOL_SCHEMAS = "tool_schemas"
internal const val KEY_STREAMING = "streaming"
internal const val KEY_STREAM_CHUNK = "stream_chunk"
internal const val KEY_TOOL_CALL_ID = "tool_call_id"
internal const val KEY_TOOL_NAME = "tool_name"
internal const val KEY_TOOL_ARGUMENT_NAMES = "tool_argument_names"
internal const val KEY_TOOL_ARGUMENT_VALUES = "tool_argument_values"
internal const val KEY_TOOL_RESULT = "tool_result"
internal const val KEY_SUCCESS = "success"
internal const val KEY_ERROR = "error"
internal const val KEY_OUTPUT = "output"
internal const val KEY_RETIRE = "retire"
internal const val KEY_STAGE = "stage"
internal const val KEY_MODEL_NAME = "model_name"
internal const val KEY_ESTIMATED_STAGE_DURATION_MILLIS = "estimated_stage_duration_millis"
internal const val KEY_MODEL_ID = "model_id"
internal const val KEY_BACKEND = "backend"
internal const val KEY_SPECULATIVE_DECODING = "speculative_decoding"
internal const val KEY_CONTEXT_TOKENS = "context_tokens"
internal const val KEY_MODEL_REVISION_IDS = "model_revision_ids"
internal const val KEY_MODEL_REVISION_VALUES = "model_revision_values"
internal const val KEY_PREPARING_DURATION_MILLIS = "preparing_duration_millis"
internal const val KEY_GENERATING_DURATION_MILLIS = "generating_duration_millis"

/**
 * Local one-shot text inference whose generation engine lives in a short-lived app subprocess.
 *
 * Model metadata and token counting stay in the main process. Only generation crosses the Binder
 * boundary so Summary, Knowledge and Library keep the provider-neutral [BackgroundAiTextInference] contract.
 */
class ProcessIsolatedLocalAiTextInference(
  context: Context,
  private val manager: LocalModelManager,
) : BackgroundAiTextInference() {
  private val appContext = context.applicationContext
  private val _progress = MutableStateFlow<AiTextInferenceProgress?>(null)
  private val remote = RemoteLocalTextInferenceClient(appContext) { progress ->
    _progress.value = progress
  }

  override val progress: Flow<AiTextInferenceProgress?> = _progress.asStateFlow()

  override fun selectedModel(): AiTextInferenceModel? {
    val model = manager.selectedModel() ?: return null
    return model.toAiTextInferenceModel(
      cacheVariant = manager.inferenceCacheVariant(model.id),
    )
  }

  override fun countTokens(text: String): Int = manager.countTokens(text)

  protected override suspend fun generateInBackground(prompt: String): String {
    require(prompt.isNotBlank()) { "推論プロンプトを入力してください" }
    require(prompt.length <= TEXT_INFERENCE_IPC_MAX_CHARS) { "推論プロンプトが長すぎます" }
    return try {
      remote.generate(prompt) {
        captureTextInferenceExecutionSnapshot(appContext, manager)
      }
    } finally {
      _progress.value = null
    }
  }
}

class ProcessIsolatedLocalAiConversationInference(
  context: Context,
  private val manager: LocalModelManager,
) : LocalConversationInference {
  private val appContext = context.applicationContext
  private val _progress = MutableStateFlow<LocalInferenceProgress?>(null)
  private val remote = RemoteLocalTextInferenceClient(appContext) { progress ->
    _progress.value = progress.toLocalInferenceProgress()
  }

  override val models: Flow<List<LocalModelStatus>> = manager.models
  override val progress: Flow<LocalInferenceProgress?> = _progress.asStateFlow()

  override fun selectedModel(): LocalModelStatus? = manager.selectedModel()

  override suspend fun generateConversation(
    request: LocalInferenceConversationRequest,
    streaming: Boolean,
    onPartial: (String) -> Unit,
  ): String {
    require(conversationIpcCharacterCount(request) <= TEXT_INFERENCE_IPC_MAX_CHARS) {
      "AIチャットの入力が長すぎます"
    }
    return try {
      remote.generateConversation(
        request = request,
        streaming = streaming,
        onPartial = onPartial,
      ) {
        captureTextInferenceExecutionSnapshot(appContext, manager)
      }
    } catch (error: RemoteException) {
      throw IllegalStateException(
        "ローカルAI推論プロセスが終了しました。モデルまたはコンテキスト設定を軽くして再試行してください。",
        error,
      )
    } finally {
      _progress.value = null
    }
  }
}

internal fun conversationIpcCharacterCount(request: LocalInferenceConversationRequest): Int =
  request.systemInstruction.length +
    request.userMessage.length +
    request.initialMessages.sumOf { message -> message.content.length } +
    request.tools.sumOf { toolDescriptionJson(it).length }

private fun AiTextInferenceProgress.toLocalInferenceProgress(): LocalInferenceProgress =
  LocalInferenceProgress(
    stage = when (stage) {
      AiTextInferenceStage.PREPARING_MODEL -> LocalInferenceStage.PREPARING_MODEL
      AiTextInferenceStage.GENERATING_RESPONSE -> LocalInferenceStage.GENERATING_RESPONSE
    },
    modelName = modelName,
    estimatedStageDurationMillis = estimatedStageDurationMillis,
  )

internal data class TextInferenceExecutionSnapshot(
  val modelId: String,
  val backend: LocalInferenceBackend,
  val speculativeDecodingEnabled: Boolean,
  val contextTokens: Int,
  val modelRevisions: Map<String, String>,
  val preparingDurationMillis: Long?,
  val generatingDurationMillis: Long?,
)

internal fun textInferenceRequestTimeoutMillis(snapshot: TextInferenceExecutionSnapshot): Long {
  val measuredDurationMillis = listOfNotNull(
    snapshot.preparingDurationMillis,
    snapshot.generatingDurationMillis,
  ).sum()
  if (measuredDurationMillis <= 0L) return TEXT_INFERENCE_DEFAULT_REQUEST_TIMEOUT_MILLIS

  val maxMeasuredDurationMillis =
    (TEXT_INFERENCE_MAX_REQUEST_TIMEOUT_MILLIS - TEXT_INFERENCE_REQUEST_TIMEOUT_GRACE_MILLIS) /
      TEXT_INFERENCE_REQUEST_TIMEOUT_MULTIPLIER
  val paddedDurationMillis =
    measuredDurationMillis.coerceAtMost(maxMeasuredDurationMillis) *
      TEXT_INFERENCE_REQUEST_TIMEOUT_MULTIPLIER +
      TEXT_INFERENCE_REQUEST_TIMEOUT_GRACE_MILLIS
  return paddedDurationMillis.coerceIn(
    TEXT_INFERENCE_MIN_REQUEST_TIMEOUT_MILLIS,
    TEXT_INFERENCE_MAX_REQUEST_TIMEOUT_MILLIS,
  )
}

internal fun isolatedContextSizeMode(contextTokens: Int): LocalContextSizeMode = when (contextTokens) {
  4_096 -> LocalContextSizeMode.CONTEXT_4K
  8_192 -> LocalContextSizeMode.CONTEXT_8K
  16_384 -> LocalContextSizeMode.CONTEXT_16K
  32_768 -> LocalContextSizeMode.CONTEXT_32K
  else -> throw IllegalArgumentException("unsupported isolated context size: $contextTokens")
}

private fun captureTextInferenceExecutionSnapshot(
  context: Context,
  manager: LocalModelManager,
): TextInferenceExecutionSnapshot {
  val model = manager.selectedModel() ?: error("AIモデルをダウンロードして選択してください")
  val settings = manager.inferenceSettings.value
  val preferences = context.getSharedPreferences(MAIN_MODEL_PREFERENCES_NAME, Context.MODE_PRIVATE)
  val revisions = preferences.all.mapNotNull { (key, value) ->
    if (!key.startsWith("$MODEL_REVISION_KEY_PREFIX.")) return@mapNotNull null
    val modelId = key.removePrefix("$MODEL_REVISION_KEY_PREFIX.")
    val revision = value as? String ?: return@mapNotNull null
    modelId to revision
  }.toMap()
  check(model.id in revisions) { "選択したAIモデルの revision がありません" }
  return TextInferenceExecutionSnapshot(
    modelId = model.id,
    backend = settings.backend,
    speculativeDecodingEnabled = settings.speculativeDecodingEnabled,
    contextTokens = model.contextTokens,
    modelRevisions = revisions,
    preparingDurationMillis = preferences.getLong(
      stageDurationKey(PREPARING_MODEL_DURATION_KEY, model.id),
      0,
    ).takeIf { it > 0 },
    generatingDurationMillis = preferences.getLong(
      stageDurationKey(GENERATING_RESPONSE_DURATION_KEY, model.id),
      0,
    ).takeIf { it > 0 },
  )
}

internal class TextInferenceProcessBatchPolicy(
  private val maxRequests: Int = TEXT_INFERENCE_PROCESS_BATCH_SIZE,
) {
  private var completedRequests = 0

  init {
    require(maxRequests > 0) { "maxRequests must be positive" }
  }

  fun requestFinished(): Boolean {
    completedRequests += 1
    return completedRequests >= maxRequests
  }
}

private class RemoteLocalTextInferenceClient(
  context: Context,
  private val onProgress: (AiTextInferenceProgress) -> Unit,
) {
  private val appContext = context.applicationContext
  private val requestMutex = Mutex()
  private val idleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var idleRetireJob: Job? = null
  private var session: RemoteTextInferenceSession? = null

  suspend fun generate(
    prompt: String,
    snapshotProvider: () -> TextInferenceExecutionSnapshot,
  ): String = requestMutex.withLock {
    val snapshot = snapshotProvider()
    idleRetireJob?.cancel()
    idleRetireJob = null
    var lastRemoteError: RemoteException? = null

    for (attempt in 0..1) {
      var active: RemoteTextInferenceSession? = null
      try {
        val current = session ?: RemoteTextInferenceSession(appContext, onProgress).also { created ->
          val connected = withTimeoutOrNull(TEXT_INFERENCE_CONNECT_TIMEOUT_MILLIS) {
            created.connect()
            true
          } ?: false
          if (!connected) {
            throw IllegalStateException("ローカルAI推論プロセスへの接続がタイムアウトしました")
          }
          session = created
        }
        active = current
        val response = withTimeoutOrNull(textInferenceRequestTimeoutMillis(snapshot)) {
          current.generate(prompt, snapshot)
        } ?: run {
          retire(current)
          throw IllegalStateException("ローカルAI推論がタイムアウトしました")
        }
        persistStageDurations(appContext, snapshot, response)
        if (response.retireAfterResponse) {
          retire(active)
        } else {
          scheduleIdleRetire(active)
        }
        response.error?.let { throw IllegalStateException(it) }
        return@withLock requireNotNull(response.output) { "ローカルAI推論結果がありません" }
      } catch (error: CancellationException) {
        active?.let { retire(it) }
        throw error
      } catch (error: RemoteException) {
        lastRemoteError = error
        active?.let { retire(it) }
        if (attempt == 1) throw error
      }
    }

    throw requireNotNull(lastRemoteError)
  }

  suspend fun generateConversation(
    request: LocalInferenceConversationRequest,
    streaming: Boolean,
    onPartial: (String) -> Unit,
    snapshotProvider: () -> TextInferenceExecutionSnapshot,
  ): String = requestMutex.withLock {
    val snapshot = snapshotProvider()
    idleRetireJob?.cancel()
    idleRetireJob = null
    var lastRemoteError: RemoteException? = null

    for (attempt in 0..1) {
      var active: RemoteTextInferenceSession? = null
      try {
        val current = session ?: RemoteTextInferenceSession(appContext, onProgress).also { created ->
          val connected = withTimeoutOrNull(TEXT_INFERENCE_CONNECT_TIMEOUT_MILLIS) {
            created.connect()
            true
          } ?: false
          if (!connected) {
            throw IllegalStateException("ローカルAI推論プロセスへの接続がタイムアウトしました")
          }
          session = created
        }
        active = current
        val response = withTimeoutOrNull(textInferenceRequestTimeoutMillis(snapshot)) {
          current.generateConversation(request, streaming, onPartial, snapshot)
        } ?: run {
          retire(current)
          throw IllegalStateException("ローカルAI推論がタイムアウトしました")
        }
        persistStageDurations(appContext, snapshot, response)
        if (response.retireAfterResponse) {
          retire(active)
        } else {
          scheduleIdleRetire(active)
        }
        response.error?.let { throw IllegalStateException(it) }
        return@withLock requireNotNull(response.output) { "ローカルAI推論結果がありません" }
      } catch (error: CancellationException) {
        active?.let { retire(it) }
        throw error
      } catch (error: RemoteException) {
        lastRemoteError = error
        active?.let { retire(it) }
        if (attempt == 1) throw error
      }
    }

    throw requireNotNull(lastRemoteError)
  }

  private fun scheduleIdleRetire(active: RemoteTextInferenceSession) {
    idleRetireJob?.cancel()
    idleRetireJob = idleScope.launch {
      delay(TEXT_INFERENCE_PROCESS_IDLE_MILLIS)
      requestMutex.withLock {
        if (session === active) retire(active)
      }
    }
  }

  private suspend fun retire(active: RemoteTextInferenceSession) {
    if (session === active) session = null
    active.closeAndAwaitProcessDeath()
  }
}

internal data class RemoteTextInferenceResponse(
  val output: String?,
  val error: String?,
  val retireAfterResponse: Boolean,
  val preparingDurationMillis: Long?,
  val generatingDurationMillis: Long?,
)

private class RemoteTextInferenceSession(
  private val context: Context,
  private val onProgress: (AiTextInferenceProgress) -> Unit,
) : ServiceConnection {
  private val connected = CompletableDeferred<Messenger>()
  private val processDeath = CompletableDeferred<Unit>()
  private val pendingResponse = AtomicReference<CompletableDeferred<Bundle>?>(null)
  private val activeTools = AtomicReference<Map<String, LocalInferenceTool>>(emptyMap())
  private val activePartialCallback = AtomicReference<((String) -> Unit)?>(null)
  private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val deathRecipient = IBinder.DeathRecipient { onBinderDied() }
  private var binder: IBinder? = null
  private var bound = false
  private val replyMessenger = Messenger(
    Handler(Looper.getMainLooper()) { message ->
      when (message.what) {
        MSG_RESULT -> {
          pendingResponse.getAndSet(null)?.complete(message.data)
          true
        }
        MSG_PROGRESS -> {
          if (pendingResponse.get() != null) {
            decodeProgress(message.data)?.let(onProgress)
          }
          true
        }
        MSG_STREAM_CHUNK -> {
          if (pendingResponse.get() != null) {
            message.data.getString(KEY_STREAM_CHUNK)?.let { chunk ->
              if (chunk.length <= TEXT_INFERENCE_IPC_MAX_CHARS) activePartialCallback.get()?.invoke(chunk)
            }
          }
          true
        }
        MSG_TOOL_CALL -> {
          if (pendingResponse.get() != null && message.replyTo != null) {
            val replyTo = message.replyTo
            val data = message.data
            callbackScope.launch {
              sendToolResult(replyTo, data)
            }
          }
          true
        }
        else -> false
      }
    },
  )

  suspend fun connect() {
    val intent = Intent(context, LocalTextInferenceService::class.java)
    if (!context.bindService(intent, this, Context.BIND_AUTO_CREATE)) {
      throw IllegalStateException("ローカルAI推論プロセスを起動できません")
    }
    bound = true
    try {
      connected.await()
    } catch (error: Throwable) {
      unbindAndDetach()
      throw error
    }
  }

  suspend fun generate(
    prompt: String,
    snapshot: TextInferenceExecutionSnapshot,
  ): RemoteTextInferenceResponse {
    val response = CompletableDeferred<Bundle>()
    check(pendingResponse.compareAndSet(null, response)) { "ローカルAI推論要求が重複しています" }
    return try {
      val message = Message.obtain(null, MSG_GENERATE).apply {
        data = encodeRequest(prompt, snapshot)
        replyTo = replyMessenger
      }
      connected.await().send(message)
      decodeResponse(response.await())
    } finally {
      pendingResponse.compareAndSet(response, null)
    }
  }

  suspend fun generateConversation(
    request: LocalInferenceConversationRequest,
    streaming: Boolean,
    onPartial: (String) -> Unit,
    snapshot: TextInferenceExecutionSnapshot,
  ): RemoteTextInferenceResponse {
    val response = CompletableDeferred<Bundle>()
    check(pendingResponse.compareAndSet(null, response)) { "ローカルAI推論要求が重複しています" }
    activeTools.set(request.tools.associateBy(LocalInferenceTool::name))
    activePartialCallback.set(onPartial)
    return try {
      val message = Message.obtain(null, MSG_GENERATE_CONVERSATION).apply {
        data = encodeConversationRequest(request, streaming, snapshot)
        replyTo = replyMessenger
      }
      connected.await().send(message)
      decodeResponse(response.await())
    } finally {
      activeTools.set(emptyMap())
      activePartialCallback.set(null)
      pendingResponse.compareAndSet(response, null)
    }
  }

  private suspend fun sendToolResult(
    replyTo: Messenger,
    data: Bundle,
  ) {
    val callId = data.getLong(KEY_TOOL_CALL_ID)
    val toolName = data.getString(KEY_TOOL_NAME)
    val names = data.getStringArrayList(KEY_TOOL_ARGUMENT_NAMES).orEmpty()
    val values = data.getStringArrayList(KEY_TOOL_ARGUMENT_VALUES).orEmpty()
    val tool = toolName?.let { activeTools.get()[it] }
    val result: Result<String> = if (names.size != values.size || tool == null) {
      Result.failure(IllegalStateException("tool call が不正です"))
    } else {
      runCatching { tool.execute(names.zip(values).toMap()) }
    }
    val response = Bundle().apply {
      putLong(KEY_TOOL_CALL_ID, callId)
      putBoolean(KEY_SUCCESS, result.isSuccess)
      result.getOrNull()?.take(TEXT_INFERENCE_IPC_MAX_CHARS)?.let { putString(KEY_TOOL_RESULT, it) }
    }
    runCatching {
      replyTo.send(
        Message.obtain(null, MSG_TOOL_RESULT).apply {
          this.data = response
        },
      )
    }
  }

  suspend fun closeAndAwaitProcessDeath() {
    failPending(DeadObjectException())
    callbackScope.cancel()
    unbind()
    if (binder == null) {
      processDeath.complete(Unit)
    } else {
      withTimeoutOrNull(PROCESS_DEATH_WAIT_MILLIS) { processDeath.await() }
    }
    detachDeathRecipient()
  }

  private fun unbind() {
    if (bound) {
      runCatching { context.unbindService(this) }
      bound = false
    }
  }

  private fun unbindAndDetach() {
    failPending(DeadObjectException())
    unbind()
    detachDeathRecipient()
  }

  private fun detachDeathRecipient() {
    binder?.let { serviceBinder ->
      runCatching { serviceBinder.unlinkToDeath(deathRecipient, 0) }
    }
    binder = null
  }

  override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
    if (service == null) {
      connected.completeExceptionally(IllegalStateException("ローカルAI推論プロセスへ接続できません"))
      return
    }
    binder = service
    runCatching { service.linkToDeath(deathRecipient, 0) }
      .onFailure {
        connected.completeExceptionally(DeadObjectException())
        return
      }
    connected.complete(Messenger(service))
  }

  override fun onServiceDisconnected(name: ComponentName?) = onBinderDied()

  override fun onBindingDied(name: ComponentName?) = onBinderDied()

  override fun onNullBinding(name: ComponentName?) {
    connected.completeExceptionally(IllegalStateException("ローカルAI推論プロセスが Binder を返しませんでした"))
  }

  private fun onBinderDied() {
    processDeath.complete(Unit)
    val error = DeadObjectException()
    if (!connected.isCompleted) connected.completeExceptionally(error)
    failPending(error)
  }

  private fun failPending(error: Throwable) {
    pendingResponse.getAndSet(null)?.completeExceptionally(error)
  }
}

