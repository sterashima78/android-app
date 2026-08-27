package dev.terashima.yomitorirss.core.airuntime

import android.app.Service
import android.content.ComponentName
import android.content.Context
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
private const val TEXT_INFERENCE_PROCESS_IDLE_MILLIS = 30_000L
private const val PROCESS_DEATH_WAIT_MILLIS = 10_000L
private const val MAX_ERROR_CHARS = 500

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
  private val _progress = MutableStateFlow<AiTextInferenceProgress?>(null)
  private val remote = RemoteLocalTextInferenceClient(context.applicationContext) { progress ->
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
      remote.generate(prompt)
    } finally {
      _progress.value = null
    }
  }
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

  suspend fun generate(prompt: String): String = requestMutex.withLock {
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
        val response = active.generate(prompt)
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

  suspend fun generate(prompt: String): RemoteTextInferenceResponse {
    val response = CompletableDeferred<Bundle>()
    check(pendingResponse.compareAndSet(null, response)) { "ローカルAI推論要求が重複しています" }
    return try {
      val message = Message.obtain(null, MSG_GENERATE).apply {
        data = Bundle().apply {
          putString(KEY_PROMPT, prompt)
        }
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
  private var modelManager: LocalModelManager? = null
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

  override fun onBind(intent: Intent?): IBinder = messenger.binder

  override fun onDestroy() {
    val activeInference = inFlight.get()
    scope.cancel()
    if (!activeInference) {
      runCatching { modelManager?.close() }
    }
    modelManager = null
    super.onDestroy()
    Process.killProcess(Process.myPid())
  }

  private suspend fun handleRequest(
    bundle: Bundle,
    replyTo: Messenger,
  ): Bundle {
    inFlight.set(true)
    val result = try {
      val prompt = decodeRequest(bundle)
      val manager = modelManager ?: LocalModelManager.shared(applicationContext).also { modelManager = it }
      coroutineScope {
        val progressJob = launch {
          manager.inferenceProgress
            .filterNotNull()
            .collect { progress -> sendProgress(replyTo, progress) }
        }
        try {
          manager.generate(prompt).also { output ->
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
    }

    val retire = batchPolicy.requestFinished()
    return if (result is String) {
      successResponse(result, retire)
    } else {
      errorResponse((result as Throwable).textInferenceUserMessage(), retire)
    }
  }

  private fun decodeRequest(bundle: Bundle): String {
    val prompt = requireNotNull(bundle.getString(KEY_PROMPT)) { "推論プロンプトがありません" }
    require(prompt.isNotBlank()) { "推論プロンプトを入力してください" }
    require(prompt.length <= TEXT_INFERENCE_IPC_MAX_CHARS) { "推論プロンプトが長すぎます" }
    return prompt
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
): Bundle = Bundle().apply {
  putBoolean(KEY_SUCCESS, true)
  putBoolean(KEY_RETIRE, retire)
  putString(KEY_OUTPUT, output)
}

private fun errorResponse(
  error: String,
  retire: Boolean,
): Bundle = Bundle().apply {
  putBoolean(KEY_SUCCESS, false)
  putBoolean(KEY_RETIRE, retire)
  putString(KEY_ERROR, error.take(MAX_ERROR_CHARS))
}

private fun decodeResponse(bundle: Bundle): RemoteTextInferenceResponse {
  val retire = bundle.getBoolean(KEY_RETIRE)
  if (!bundle.getBoolean(KEY_SUCCESS)) {
    return RemoteTextInferenceResponse(
      output = null,
      error = bundle.getString(KEY_ERROR) ?: "ローカルAI推論に失敗しました",
      retireAfterResponse = retire,
    )
  }
  return RemoteTextInferenceResponse(
    output = requireNotNull(bundle.getString(KEY_OUTPUT)) { "ローカルAI推論結果がありません" },
    error = null,
    retireAfterResponse = retire,
  )
}

private fun Throwable.textInferenceUserMessage(): String = when (this) {
  is IllegalArgumentException, is IllegalStateException -> message?.takeIf(String::isNotBlank)?.take(MAX_ERROR_CHARS)
  else -> null
} ?: "ローカルAI推論に失敗しました (${javaClass.simpleName})"
