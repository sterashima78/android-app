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
import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceModel
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceProgress
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceStage
import java.util.concurrent.atomic.AtomicBoolean
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
private const val TEXT_INFERENCE_PROCESS_IDLE_MILLIS = 30_000L
private const val PROCESS_DEATH_WAIT_MILLIS = 10_000L
private const val MAX_ERROR_CHARS = 500

private const val MAIN_MODEL_PREFERENCES_NAME = "local_summary_models"
private const val MAIN_CONTEXT_BENCHMARK_PREFERENCES_NAME = "local_context_benchmarks"
private const val SELECTED_MODEL_KEY = "selected_model_id"
private const val INFERENCE_BACKEND_KEY = "inference_backend"
private const val SPECULATIVE_DECODING_ENABLED_KEY = "speculative_decoding_enabled"
private const val CONTEXT_SIZE_MODE_KEY = "context_size_mode"
private const val MODEL_REVISION_KEY_PREFIX = "model_revision"
private const val PREPARING_MODEL_DURATION_KEY = "preparing_model"
private const val GENERATING_RESPONSE_DURATION_KEY = "generating_response"

private const val MSG_GENERATE = 1
private const val MSG_RESULT = 2
private const val MSG_PROGRESS = 3
private const val KEY_PROMPT = "prompt"
private const val KEY_SUCCESS = "success"
private const val KEY_ERROR = "error"
private const val KEY_OUTPUT = "output"
private const val KEY_RETIRE = "retire"
private const val KEY_STAGE = "stage"
private const val KEY_MODEL_NAME = "model_name"
private const val KEY_ESTIMATED_STAGE_DURATION_MILLIS = "estimated_stage_duration_millis"
private const val KEY_MODEL_ID = "model_id"
private const val KEY_BACKEND = "backend"
private const val KEY_SPECULATIVE_DECODING = "speculative_decoding"
private const val KEY_CONTEXT_TOKENS = "context_tokens"
private const val KEY_MODEL_REVISION_IDS = "model_revision_ids"
private const val KEY_MODEL_REVISION_VALUES = "model_revision_values"
private const val KEY_PREPARING_DURATION_MILLIS = "preparing_duration_millis"
private const val KEY_GENERATING_DURATION_MILLIS = "generating_duration_millis"

/**
 * Local one-shot text inference whose generation engine lives in a short-lived app subprocess.
 *
 * Model metadata and token counting stay in the main process. Only generation crosses the Binder
 * boundary so Summary, Knowledge and Library keep the provider-neutral [AiTextInference] contract.
 */
class ProcessIsolatedLocalAiTextInference(
  context: Context,
  private val manager: LocalModelManager,
) : AiTextInference {
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

  override suspend fun generate(prompt: String): String {
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

internal data class TextInferenceExecutionSnapshot(
  val modelId: String,
  val backend: LocalInferenceBackend,
  val speculativeDecodingEnabled: Boolean,
  val contextTokens: Int,
  val modelRevisions: Map<String, String>,
  val preparingDurationMillis: Long?,
  val generatingDurationMillis: Long?,
)

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
        active = session ?: RemoteTextInferenceSession(appContext, onProgress).also { created ->
          created.connect()
          session = created
        }
        val response = active.generate(prompt, snapshot)
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

private data class RemoteTextInferenceResponse(
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

  suspend fun closeAndAwaitProcessDeath() {
    failPending(DeadObjectException())
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

class LocalTextInferenceService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val requestMutex = Mutex()
  private val inFlight = AtomicBoolean(false)
  private val batchPolicy = TextInferenceProcessBatchPolicy()
  private var isolatedContext: TextInferenceSnapshotContext? = null
  private var modelManager: LocalModelManager? = null
  private lateinit var processDiagnostics: LocalAiTextProcessDiagnosticSession
  private val messenger = Messenger(
    Handler(Looper.getMainLooper()) { message ->
      if (message.what != MSG_GENERATE || message.replyTo == null) return@Handler false
      val replyTo = message.replyTo
      val request = message.data
      scope.launch {
        val response = requestMutex.withLock {
          handleRequest(request, replyTo)
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

private fun encodeRequest(
  prompt: String,
  snapshot: TextInferenceExecutionSnapshot,
): Bundle = Bundle().apply {
  putString(KEY_PROMPT, prompt)
  putString(KEY_MODEL_ID, snapshot.modelId)
  putString(KEY_BACKEND, snapshot.backend.name)
  putBoolean(KEY_SPECULATIVE_DECODING, snapshot.speculativeDecodingEnabled)
  putInt(KEY_CONTEXT_TOKENS, snapshot.contextTokens)
  val revisionEntries = snapshot.modelRevisions.entries.sortedBy(Map.Entry<String, String>::key)
  putStringArrayList(KEY_MODEL_REVISION_IDS, ArrayList(revisionEntries.map(Map.Entry<String, String>::key)))
  putStringArrayList(KEY_MODEL_REVISION_VALUES, ArrayList(revisionEntries.map(Map.Entry<String, String>::value)))
  snapshot.preparingDurationMillis?.let { putLong(KEY_PREPARING_DURATION_MILLIS, it) }
  snapshot.generatingDurationMillis?.let { putLong(KEY_GENERATING_DURATION_MILLIS, it) }
}

private fun decodeRequest(bundle: Bundle): DecodedTextInferenceRequest {
  val prompt = requireNotNull(bundle.getString(KEY_PROMPT)) { "推論プロンプトがありません" }
  require(prompt.isNotBlank()) { "推論プロンプトを入力してください" }
  require(prompt.length <= TEXT_INFERENCE_IPC_MAX_CHARS) { "推論プロンプトが長すぎます" }
  val modelId = requireNotNull(bundle.getString(KEY_MODEL_ID)) { "AIモデルがありません" }
  val backendName = requireNotNull(bundle.getString(KEY_BACKEND)) { "AI backend がありません" }
  val backend = runCatching { LocalInferenceBackend.valueOf(backendName) }
    .getOrElse { throw IllegalArgumentException("AI backend が不正です") }
  val contextTokens = bundle.getInt(KEY_CONTEXT_TOKENS)
  isolatedContextSizeMode(contextTokens)
  val revisionIds = requireNotNull(bundle.getStringArrayList(KEY_MODEL_REVISION_IDS)) {
    "AIモデル revision id がありません"
  }
  val revisionValues = requireNotNull(bundle.getStringArrayList(KEY_MODEL_REVISION_VALUES)) {
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
      speculativeDecodingEnabled = bundle.getBoolean(KEY_SPECULATIVE_DECODING),
      contextTokens = contextTokens,
      modelRevisions = modelRevisions,
      preparingDurationMillis = bundle.getLong(KEY_PREPARING_DURATION_MILLIS)
        .takeIf { bundle.containsKey(KEY_PREPARING_DURATION_MILLIS) && it > 0 },
      generatingDurationMillis = bundle.getLong(KEY_GENERATING_DURATION_MILLIS)
        .takeIf { bundle.containsKey(KEY_GENERATING_DURATION_MILLIS) && it > 0 },
    ),
  )
}

private fun sendProgress(
  replyTo: Messenger,
  progress: LocalInferenceProgress,
) {
  runCatching {
    replyTo.send(
      Message.obtain(null, MSG_PROGRESS).apply {
        data = Bundle().apply {
          putString(KEY_STAGE, progress.stage.name)
          progress.modelName?.let { putString(KEY_MODEL_NAME, it) }
          progress.estimatedStageDurationMillis?.let {
            putLong(KEY_ESTIMATED_STAGE_DURATION_MILLIS, it)
          }
        }
      },
    )
  }
}

private fun decodeProgress(bundle: Bundle): AiTextInferenceProgress? {
  val stage = bundle.getString(KEY_STAGE)
    ?.let { runCatching { AiTextInferenceStage.valueOf(it) }.getOrNull() }
    ?: return null
  return AiTextInferenceProgress(
    stage = stage,
    modelName = bundle.getString(KEY_MODEL_NAME),
    estimatedStageDurationMillis = bundle.getLong(KEY_ESTIMATED_STAGE_DURATION_MILLIS)
      .takeIf { bundle.containsKey(KEY_ESTIMATED_STAGE_DURATION_MILLIS) },
  )
}

private fun successResponse(
  output: String,
  retire: Boolean,
  durations: Pair<Long?, Long?>,
): Bundle = Bundle().apply {
  putBoolean(KEY_SUCCESS, true)
  putBoolean(KEY_RETIRE, retire)
  putString(KEY_OUTPUT, output)
  durations.first?.let { putLong(KEY_PREPARING_DURATION_MILLIS, it) }
  durations.second?.let { putLong(KEY_GENERATING_DURATION_MILLIS, it) }
}

private fun errorResponse(
  error: String,
  retire: Boolean,
  durations: Pair<Long?, Long?>,
): Bundle = Bundle().apply {
  putBoolean(KEY_SUCCESS, false)
  putBoolean(KEY_RETIRE, retire)
  putString(KEY_ERROR, error.take(MAX_ERROR_CHARS))
  durations.first?.let { putLong(KEY_PREPARING_DURATION_MILLIS, it) }
  durations.second?.let { putLong(KEY_GENERATING_DURATION_MILLIS, it) }
}

private fun decodeResponse(bundle: Bundle): RemoteTextInferenceResponse {
  val retire = bundle.getBoolean(KEY_RETIRE)
  val preparingDuration = bundle.getLong(KEY_PREPARING_DURATION_MILLIS)
    .takeIf { bundle.containsKey(KEY_PREPARING_DURATION_MILLIS) && it > 0 }
  val generatingDuration = bundle.getLong(KEY_GENERATING_DURATION_MILLIS)
    .takeIf { bundle.containsKey(KEY_GENERATING_DURATION_MILLIS) && it > 0 }
  if (!bundle.getBoolean(KEY_SUCCESS)) {
    return RemoteTextInferenceResponse(
      output = null,
      error = bundle.getString(KEY_ERROR) ?: "ローカルAI推論に失敗しました",
      retireAfterResponse = retire,
      preparingDurationMillis = preparingDuration,
      generatingDurationMillis = generatingDuration,
    )
  }
  return RemoteTextInferenceResponse(
    output = requireNotNull(bundle.getString(KEY_OUTPUT)) { "ローカルAI推論結果がありません" },
    error = null,
    retireAfterResponse = retire,
    preparingDurationMillis = preparingDuration,
    generatingDurationMillis = generatingDuration,
  )
}

private fun persistStageDurations(
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

private fun stageDurationKey(stage: String, modelId: String): String = "$stage.$modelId.duration_millis"

private fun Throwable.textInferenceUserMessage(): String = when (this) {
  is IllegalArgumentException, is IllegalStateException -> message?.takeIf(String::isNotBlank)?.take(MAX_ERROR_CHARS)
  else -> null
} ?: "ローカルAI推論に失敗しました (${javaClass.simpleName})"