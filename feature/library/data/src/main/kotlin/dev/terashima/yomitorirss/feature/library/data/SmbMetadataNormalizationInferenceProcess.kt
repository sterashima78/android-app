package dev.terashima.yomitorirss.feature.library.data

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
import dev.terashima.yomitorirss.core.airuntime.LocalAiMemoryDiagnosticPhase
import dev.terashima.yomitorirss.core.airuntime.LocalAiMemoryDiagnostics
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.feature.library.SmbBookMetadataProposal
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal const val SMB_VISION_PROCESS_BATCH_SIZE = 2
private const val MAX_COVER_BYTES = 8L * 1024 * 1024
private const val MAX_FILE_NAME_CHARS = 2_048
private const val MAX_PROMPT_CHARS = 64 * 1024
private const val PROCESS_DEATH_WAIT_MILLIS = 10_000L

private const val MSG_SUGGEST = 1
private const val MSG_RESULT = 2
private const val KEY_FILE_NAME = "file_name"
private const val KEY_COVER_PATH = "cover_path"
private const val KEY_PROMPT_TEMPLATE = "prompt_template"
private const val KEY_SUCCESS = "success"
private const val KEY_ERROR = "error"
private const val KEY_RETIRE = "retire"
private const val KEY_PROPOSAL = "proposal"
private const val KEY_TITLE = "title"
private const val KEY_AUTHORS = "authors"
private const val KEY_PUBLISHER = "publisher"
private const val KEY_PUBLISHED_DATE = "published_date"
private const val KEY_ISBN10 = "isbn10"
private const val KEY_ISBN13 = "isbn13"
private const val KEY_SERIES_NAME = "series_name"
private const val KEY_SERIES_POSITION = "series_position"
private const val KEY_CONFIDENCE = "confidence"
private const val KEY_REASON = "reason"

internal class SmbVisionProcessBatchPolicy(
  private val maxItems: Int = SMB_VISION_PROCESS_BATCH_SIZE,
) {
  private var completedItems = 0

  init {
    require(maxItems > 0) { "maxItems must be positive" }
  }

  fun itemFinished(): Boolean {
    completedItems += 1
    return completedItems >= maxItems
  }
}

internal class RemoteSmbMetadataNormalizationSuggester(
  context: Context,
) {
  private val appContext = context.applicationContext
  private val requestMutex = Mutex()
  private var session: RemoteInferenceSession? = null

  suspend fun suggest(
    currentFileName: String,
    coverFile: File,
    promptTemplate: String,
  ): SmbBookMetadataProposal = requestMutex.withLock {
    var lastRemoteError: RemoteException? = null
    repeat(2) { attempt ->
      val active = session ?: RemoteInferenceSession(appContext).also { created ->
        created.connect()
        session = created
      }
      try {
        val response = active.suggest(
          currentFileName = currentFileName,
          coverFile = coverFile,
          promptTemplate = promptTemplate,
        )
        if (response.retireAfterResponse) retire(active)
        response.error?.let { throw IllegalArgumentException(it) }
        return@withLock requireNotNull(response.proposal) { "AI推論結果がありません" }
      } catch (error: RemoteException) {
        lastRemoteError = error
        retire(active)
        if (attempt == 1) throw error
      }
    }
    throw requireNotNull(lastRemoteError)
  }

  suspend fun close() = requestMutex.withLock {
    session?.closeAndAwaitProcessDeath()
    session = null
  }

  private suspend fun retire(active: RemoteInferenceSession) {
    if (session === active) session = null
    active.closeAndAwaitProcessDeath()
  }
}

private data class RemoteInferenceResponse(
  val proposal: SmbBookMetadataProposal?,
  val error: String?,
  val retireAfterResponse: Boolean,
)

private class RemoteInferenceSession(
  private val context: Context,
) : ServiceConnection {
  private val connected = CompletableDeferred<Messenger>()
  private val processDeath = CompletableDeferred<Unit>()
  private val pendingResponse = AtomicReference<CompletableDeferred<Bundle>?>(null)
  private val deathRecipient = IBinder.DeathRecipient { onBinderDied() }
  private var binder: IBinder? = null
  private var bound = false
  private val replyMessenger = Messenger(
    Handler(Looper.getMainLooper()) { message ->
      if (message.what != MSG_RESULT) return@Handler false
      pendingResponse.getAndSet(null)?.complete(message.data)
      true
    },
  )

  suspend fun connect() {
    val intent = Intent(context, SmbMetadataNormalizationInferenceService::class.java)
    if (!context.bindService(intent, this, Context.BIND_AUTO_CREATE)) {
      throw IllegalStateException("AI推論プロセスを起動できません")
    }
    bound = true
    try {
      connected.await()
    } catch (error: Throwable) {
      unbindAndDetach()
      throw error
    }
  }

  suspend fun suggest(
    currentFileName: String,
    coverFile: File,
    promptTemplate: String,
  ): RemoteInferenceResponse {
    val response = CompletableDeferred<Bundle>()
    check(pendingResponse.compareAndSet(null, response)) { "AI推論要求が重複しています" }
    return try {
      val message = Message.obtain(null, MSG_SUGGEST).apply {
        data = Bundle().apply {
          putString(KEY_FILE_NAME, currentFileName)
          putString(KEY_COVER_PATH, coverFile.absolutePath)
          putString(KEY_PROMPT_TEMPLATE, promptTemplate)
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
      connected.completeExceptionally(IllegalStateException("AI推論プロセスへ接続できません"))
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
    connected.completeExceptionally(IllegalStateException("AI推論プロセスが Binder を返しませんでした"))
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

class SmbMetadataNormalizationInferenceService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val requestMutex = Mutex()
  private val inFlight = AtomicBoolean(false)
  private val batchPolicy = SmbVisionProcessBatchPolicy()
  private var modelManager: LocalModelManager? = null
  private val messenger = Messenger(
    Handler(Looper.getMainLooper()) { message ->
      if (message.what != MSG_SUGGEST || message.replyTo == null) return@Handler false
      val replyTo = message.replyTo
      val request = message.data
      scope.launch {
        val response = requestMutex.withLock {
          handleRequest(request)
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

  private fun handleRequest(bundle: Bundle): Bundle {
    inFlight.set(true)
    var retire = false
    return try {
      val request = decodeRequest(bundle)
      LocalAiMemoryDiagnostics.recordVisionInference(
        applicationContext,
        LocalAiMemoryDiagnosticPhase.VISION_BEFORE,
      )
      val manager = modelManager ?: LocalModelManager.shared(applicationContext).also { modelManager = it }
      val proposal = LocalSmbMetadataNormalizationSuggester(manager).suggest(
        currentFileName = request.currentFileName,
        coverBytes = request.coverFile.readBytes(),
        promptTemplate = request.promptTemplate,
      )
      retire = batchPolicy.itemFinished()
      successResponse(proposal, retire)
    } catch (error: Throwable) {
      retire = batchPolicy.itemFinished()
      errorResponse(error.inferenceUserMessage(), retire)
    } finally {
      inFlight.set(false)
    }
  }

  private fun decodeRequest(bundle: Bundle): InferenceRequest {
    val currentFileName = requireNotNull(bundle.getString(KEY_FILE_NAME)) { "現在のファイル名がありません" }
    require(currentFileName.isNotBlank() && currentFileName.length <= MAX_FILE_NAME_CHARS) {
      "現在のファイル名が不正です"
    }
    val promptTemplate = requireNotNull(bundle.getString(KEY_PROMPT_TEMPLATE)) { "解析プロンプトがありません" }
    require(promptTemplate.length <= MAX_PROMPT_CHARS) { "解析プロンプトが長すぎます" }
    val coverPath = requireNotNull(bundle.getString(KEY_COVER_PATH)) { "表紙キャッシュがありません" }
    val coverFile = File(coverPath).canonicalFile
    val cacheRoot = applicationContext.cacheDir.canonicalFile
    require(coverFile.isUnder(cacheRoot) && coverFile.isFile) { "表紙キャッシュを読み取れません" }
    require(coverFile.length() in 1..MAX_COVER_BYTES) { "表紙画像が大きすぎます" }
    return InferenceRequest(currentFileName, coverFile, promptTemplate)
  }
}

private data class InferenceRequest(
  val currentFileName: String,
  val coverFile: File,
  val promptTemplate: String,
)

private fun File.isUnder(root: File): Boolean =
  path == root.path || path.startsWith(root.path + File.separator)

private fun successResponse(
  proposal: SmbBookMetadataProposal,
  retire: Boolean,
): Bundle = Bundle().apply {
  putBoolean(KEY_SUCCESS, true)
  putBoolean(KEY_RETIRE, retire)
  putBundle(KEY_PROPOSAL, proposal.toBundle())
}

private fun errorResponse(
  error: String,
  retire: Boolean,
): Bundle = Bundle().apply {
  putBoolean(KEY_SUCCESS, false)
  putBoolean(KEY_RETIRE, retire)
  putString(KEY_ERROR, error.take(500))
}

private fun decodeResponse(bundle: Bundle): RemoteInferenceResponse {
  val retire = bundle.getBoolean(KEY_RETIRE)
  if (!bundle.getBoolean(KEY_SUCCESS)) {
    return RemoteInferenceResponse(
      proposal = null,
      error = bundle.getString(KEY_ERROR) ?: "ローカルAI推論に失敗しました",
      retireAfterResponse = retire,
    )
  }
  val proposalBundle = requireNotNull(bundle.getBundle(KEY_PROPOSAL)) { "AI推論結果がありません" }
  return RemoteInferenceResponse(
    proposal = proposalBundle.toProposal(),
    error = null,
    retireAfterResponse = retire,
  )
}

internal fun SmbBookMetadataProposal.toBundle(): Bundle = Bundle().apply {
  putString(KEY_TITLE, title)
  putStringArrayList(KEY_AUTHORS, ArrayList(authors))
  publisher?.let { putString(KEY_PUBLISHER, it) }
  publishedDate?.let { putString(KEY_PUBLISHED_DATE, it) }
  isbn10?.let { putString(KEY_ISBN10, it) }
  isbn13?.let { putString(KEY_ISBN13, it) }
  seriesName?.let { putString(KEY_SERIES_NAME, it) }
  seriesPosition?.let { putInt(KEY_SERIES_POSITION, it) }
  confidence?.let { putFloat(KEY_CONFIDENCE, it) }
  reason?.let { putString(KEY_REASON, it) }
}

internal fun Bundle.toProposal(): SmbBookMetadataProposal = SmbBookMetadataProposal(
  title = requireNotNull(getString(KEY_TITLE)) { "AI推論結果にタイトルがありません" },
  authors = getStringArrayList(KEY_AUTHORS)?.toList().orEmpty(),
  publisher = getString(KEY_PUBLISHER),
  publishedDate = getString(KEY_PUBLISHED_DATE),
  isbn10 = getString(KEY_ISBN10),
  isbn13 = getString(KEY_ISBN13),
  seriesName = getString(KEY_SERIES_NAME),
  seriesPosition = getInt(KEY_SERIES_POSITION).takeIf { containsKey(KEY_SERIES_POSITION) },
  confidence = getFloat(KEY_CONFIDENCE).takeIf { containsKey(KEY_CONFIDENCE) },
  reason = getString(KEY_REASON),
)

private fun Throwable.inferenceUserMessage(): String = when (this) {
  is IllegalArgumentException -> message?.takeIf(String::isNotBlank)?.take(500)
  else -> null
} ?: "ローカルAI推論に失敗しました (${javaClass.simpleName})"
