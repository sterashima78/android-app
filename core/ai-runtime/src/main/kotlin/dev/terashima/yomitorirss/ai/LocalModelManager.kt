package dev.terashima.yomitorirss.core.airuntime
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class LocalInferenceBackend {
  CPU,
  GPU,
}

data class LocalInferenceSettings(
  val backend: LocalInferenceBackend = LocalInferenceBackend.CPU,
  val thinkingEnabled: Boolean = false,
)

data class LocalModelStatus(
  val id: String,
  val name: String,
  val description: String,
  val source: String,
  val license: String,
  val quantization: String,
  val sizeBytes: Long,
  val downloadedBytes: Long,
  val downloaded: Boolean,
  val selected: Boolean,
  val recommended: Boolean,
  val memoryLow: Boolean,
  val supportsThinking: Boolean,
)

data class ModelDownloadProgress(
  val modelId: String,
  val phase: String,
  val downloadedBytes: Long,
  val totalBytes: Long,
  val estimatedRemainingMillis: Long? = null,
)

data class SummaryProgress(
  val stage: String,
  val modelName: String? = null,
  val estimatedStageDurationMillis: Long? = null,
)

data class ChatProgress(
  val stage: String,
  val modelName: String? = null,
  val estimatedStageDurationMillis: Long? = null,
)

class LocalModelManager(context: Context) : AutoCloseable {
  private val appContext = context.applicationContext
  private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private val downloadLock = ReentrantLock()
  private val inferenceLock = ReentrantLock()
  private val cancelRequested = AtomicBoolean(false)
  private var cachedInference: CachedInference? = null

  private val _models = MutableStateFlow<List<LocalModelStatus>>(emptyList())
  val models: StateFlow<List<LocalModelStatus>> = _models.asStateFlow()

  private val _downloadProgress = MutableStateFlow<ModelDownloadProgress?>(null)
  val downloadProgress: StateFlow<ModelDownloadProgress?> = _downloadProgress.asStateFlow()

  private val _summaryProgress = MutableStateFlow<SummaryProgress?>(null)
  val summaryProgress: StateFlow<SummaryProgress?> = _summaryProgress.asStateFlow()

  private val _chatProgress = MutableStateFlow<ChatProgress?>(null)
  val chatProgress: StateFlow<ChatProgress?> = _chatProgress.asStateFlow()

  private val _chatResponse = MutableStateFlow("")
  val chatResponse: StateFlow<String> = _chatResponse.asStateFlow()

  private val _summaryPrompt = MutableStateFlow(
    preferences.getString(SUMMARY_PROMPT_KEY, null)
      ?.let { runCatching { SummaryPrompt.normalize(it) }.getOrNull() }
      ?: SummaryPrompt.DEFAULT,
  )
  val summaryPrompt: StateFlow<String> = _summaryPrompt.asStateFlow()

  private val _inferenceSettings = MutableStateFlow(readInferenceSettings())
  val inferenceSettings: StateFlow<LocalInferenceSettings> = _inferenceSettings.asStateFlow()

  init {
    refreshModels()
  }

  fun isSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
      Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } &&
      deviceMemoryBytes() >= MINIMUM_DEVICE_MEMORY_BYTES

  fun refreshModels() {
    val selectedId = resolveSelectedModel()?.id
    _models.value = MODEL_CATALOG.map { model ->
      val file = modelFile(model)
      val downloaded = isValidModelFile(file, model)
      LocalModelStatus(
        id = model.id,
        name = model.name,
        description = model.description,
        source = model.source,
        license = model.license,
        quantization = model.quantization,
        sizeBytes = model.estimatedSizeBytes,
        downloadedBytes = if (downloaded) file.length() else 0,
        downloaded = downloaded,
        selected = downloaded && selectedId == model.id,
        recommended = model.recommended,
        memoryLow = deviceMemoryBytes() < model.minDeviceMemoryGb * BYTES_PER_GB,
        supportsThinking = model.supportsThinking,
      )
    }
  }

  fun selectedModel(): LocalModelStatus? = models.value.firstOrNull(LocalModelStatus::selected)

  fun updateSummaryPrompt(prompt: String) {
    val normalized = SummaryPrompt.normalize(prompt)
    preferences.edit().putString(SUMMARY_PROMPT_KEY, normalized).apply()
    _summaryPrompt.value = normalized
  }

  fun resetSummaryPrompt() {
    preferences.edit().remove(SUMMARY_PROMPT_KEY).apply()
    _summaryPrompt.value = SummaryPrompt.DEFAULT
  }

  fun setInferenceBackend(backend: LocalInferenceBackend) {
    preferences.edit().putString(INFERENCE_BACKEND_KEY, backend.name).apply()
    _inferenceSettings.value = _inferenceSettings.value.copy(backend = backend)
  }

  fun setThinkingEnabled(enabled: Boolean) {
    preferences.edit().putBoolean(THINKING_ENABLED_KEY, enabled).apply()
    _inferenceSettings.value = _inferenceSettings.value.copy(thinkingEnabled = enabled)
  }

  fun summaryCacheKey(modelId: String, prompt: String = _summaryPrompt.value): String {
    val model = MODEL_CATALOG.firstOrNull { it.id == modelId }
    val settings = currentInferenceSettings()
    val variant = if (model?.supportsThinking == true) {
      ThinkingMode.cacheVariant(settings.thinkingEnabled)
    } else {
      null
    }
    return SummaryPrompt.cacheKey(modelId, prompt, variant)
  }

  fun selectModel(modelId: String) {
    val model = requireModel(modelId)
    check(isValidModelFile(modelFile(model), model)) { "モデルがダウンロードされていません" }
    preferences.edit().putString(SELECTED_MODEL_KEY, model.id).apply()
    refreshModels()
  }

  fun deleteModel(modelId: String) {
    val model = requireModel(modelId)
    inferenceLock.withLock {
      if (cachedInference?.key?.modelId == model.id) releaseCachedInferenceLocked()
      modelFile(model).delete()
      temporaryModelFile(model).delete()
      modelCacheDirectory(model).deleteRecursively()
    }
    if (preferences.getString(SELECTED_MODEL_KEY, null) == model.id) {
      preferences.edit().remove(SELECTED_MODEL_KEY).apply()
    }
    refreshModels()
  }

  fun cancel() {
    cancelRequested.set(true)
  }

  fun downloadModel(modelId: String) {
    check(isSupported()) { "この端末ではローカルモデルを利用できません" }
    val model = requireModel(modelId)
    downloadLock.withLock {
      val destination = modelFile(model)
      if (isValidModelFile(destination, model)) {
        if (resolveSelectedModel() == null) preferences.edit().putString(SELECTED_MODEL_KEY, model.id).apply()
        refreshModels()
        return
      }
      destination.delete()
      val requiredBytes = model.estimatedSizeBytes + DOWNLOAD_STORAGE_MARGIN_BYTES
      val availableBytes = StatFs(modelsDirectory().absolutePath).availableBytes
      check(availableBytes >= requiredBytes) { "モデルを保存する空き容量が不足しています" }

      val temporary = temporaryModelFile(model)
      temporary.delete()
      var connection: HttpURLConnection? = null
      try {
        connection = openDownloadConnection(model.downloadUrl)
        val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: model.estimatedSizeBytes
        var downloadedBytes = 0L
        var lastProgressAt = 0L
        val startedAt = SystemClock.elapsedRealtime()
        _downloadProgress.value = ModelDownloadProgress(model.id, "downloading", 0, totalBytes)
        connection.inputStream.buffered(DOWNLOAD_BUFFER_SIZE).use { input ->
          temporary.outputStream().buffered(DOWNLOAD_BUFFER_SIZE).use { output ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
            while (true) {
              val count = input.read(buffer)
              if (count < 0) break
              output.write(buffer, 0, count)
              downloadedBytes += count
              val now = SystemClock.elapsedRealtime()
              if (now - lastProgressAt >= DOWNLOAD_PROGRESS_INTERVAL_MILLIS) {
                lastProgressAt = now
                _downloadProgress.value = ModelDownloadProgress(
                  model.id,
                  "downloading",
                  downloadedBytes,
                  totalBytes,
                  estimateRemainingMillis(downloadedBytes, totalBytes, now - startedAt),
                )
              }
            }
          }
        }
        _downloadProgress.value = ModelDownloadProgress(model.id, "verifying", downloadedBytes, totalBytes, 0)
        check(isValidModelFile(temporary, model)) { "ダウンロードしたモデルファイルが不正です" }
        if (!temporary.renameTo(destination)) {
          temporary.copyTo(destination, overwrite = true)
          temporary.delete()
        }
        if (resolveSelectedModel() == null) preferences.edit().putString(SELECTED_MODEL_KEY, model.id).apply()
        _downloadProgress.value = ModelDownloadProgress(model.id, "completed", destination.length(), destination.length(), 0)
        refreshModels()
      } catch (error: Throwable) {
        temporary.delete()
        throw IllegalStateException("モデルのダウンロードに失敗しました: ${error.message.orEmpty()}", error)
      } finally {
        connection?.disconnect()
      }
    }
  }

  fun summarize(text: String, prompt: String = _summaryPrompt.value): String {
    check(isSupported()) { "この端末ではローカルモデルを利用できません" }
    val model = resolveSelectedModel() ?: error("要約モデルをダウンロードして選択してください")
    val file = modelFile(model)
    check(isValidModelFile(file, model)) { "選択したモデルが見つかりません" }
    val promptTemplate = SummaryPrompt.normalize(prompt)
    cancelRequested.set(false)

    return inferenceLock.withLock {
      var lease: InferenceLease? = null
      try {
        val settings = currentInferenceSettings()
        val cacheHit = hasReusableInference(model, file, settings.backend)
        val preparationStartedAt = if (cacheHit) null else SystemClock.elapsedRealtime()
        if (!cacheHit) {
          _summaryProgress.value = SummaryProgress(
            "preparing_model",
            model.name,
            estimatedStageDurationMillis("preparing_model", model.id),
          )
        }
        val acquired = acquireInference(model, file, settings.backend)
        lease = acquired
        preparationStartedAt?.let { startedAt ->
          recordStageDuration("preparing_model", model.id, SystemClock.elapsedRealtime() - startedAt)
        }
        check(!cancelRequested.get()) { "要約をキャンセルしました" }
        val generationStartedAt = SystemClock.elapsedRealtime()
        _summaryProgress.value = SummaryProgress(
          "generating_summary",
          model.name,
          estimatedStageDurationMillis("generating_summary", model.id),
        )
        val promptWithMode = createSummaryPrompt(text, model.maxInputChars, model.runtime, promptTemplate)
          .withThinkingMode(model, settings)
        val raw = try {
          generateResponse(acquired.inference, promptWithMode)
        } catch (error: Throwable) {
          if (acquired.retained) invalidateRetainedInferenceLocked(acquired.inference)
          throw error
        }
        recordStageDuration("generating_summary", model.id, SystemClock.elapsedRealtime() - generationStartedAt)
        cleanSummary(raw)
      } finally {
        _summaryProgress.value = null
        lease?.takeUnless(InferenceLease::retained)?.inference?.let { inference ->
          runCatching { inference.close() }
        }
      }
    }
  }

  fun chat(
    turns: List<ChatTurn>,
    context: List<ChatContextBlock> = emptyList(),
  ): String {
    check(isSupported()) { "この端末ではローカルモデルを利用できません" }
    require(turns.any { it.role == ChatRole.USER && it.content.isNotBlank() }) { "メッセージを入力してください" }
    val model = resolveSelectedModel() ?: error("AIモデルをダウンロードして選択してください")
    val file = modelFile(model)
    check(isValidModelFile(file, model)) { "選択したモデルが見つかりません" }
    cancelRequested.set(false)
    _chatResponse.value = ""

    return inferenceLock.withLock {
      var lease: InferenceLease? = null
      try {
        val settings = currentInferenceSettings()
        val cacheHit = hasReusableInference(model, file, settings.backend)
        val preparationStartedAt = if (cacheHit) null else SystemClock.elapsedRealtime()
        if (!cacheHit) {
          _chatProgress.value = ChatProgress(
            "preparing_model",
            model.name,
            estimatedStageDurationMillis("preparing_model", model.id),
          )
        }
        val acquired = acquireInference(model, file, settings.backend)
        lease = acquired
        preparationStartedAt?.let { startedAt ->
          recordStageDuration("preparing_model", model.id, SystemClock.elapsedRealtime() - startedAt)
        }
        check(!cancelRequested.get()) { "チャットをキャンセルしました" }
        val generationStartedAt = SystemClock.elapsedRealtime()
        _chatProgress.value = ChatProgress(
          "generating_reply",
          model.name,
          estimatedStageDurationMillis("generating_reply", model.id),
        )
        val prompt = ChatPrompt.render(
          turns = turns,
          context = context,
          maxInputChars = maxOf(model.maxInputChars, model.contextTokens),
          chatMl = model.runtime == InferenceRuntime.MEDIAPIPE_TASK,
        ).withThinkingMode(model, settings)
        val streamedRaw = StringBuilder()
        val raw = try {
          generateResponseStreaming(acquired.inference, prompt) { chunk ->
            appendStreamChunk(streamedRaw, chunk)
            _chatResponse.value = ChatResponseStream.partial(streamedRaw.toString())
          }
        } catch (error: Throwable) {
          if (acquired.retained) invalidateRetainedInferenceLocked(acquired.inference)
          throw error
        }
        recordStageDuration("generating_reply", model.id, SystemClock.elapsedRealtime() - generationStartedAt)
        val reply = ChatResponseStream.complete(raw.ifBlank { streamedRaw.toString() })
        _chatResponse.value = reply
        reply
      } finally {
        _chatProgress.value = null
        lease?.takeUnless(InferenceLease::retained)?.inference?.let { inference ->
          runCatching { inference.close() }
        }
      }
    }
  }

  override fun close() {
    cancelRequested.set(true)
    inferenceLock.withLock {
      releaseCachedInferenceLocked()
    }
  }

  private fun hasReusableInference(
    model: ModelDefinition,
    file: File,
    backend: LocalInferenceBackend,
  ): Boolean {
    if (model.runtime != InferenceRuntime.LITERT_LM) return false
    return cachedInference?.key == inferenceCacheKey(model, file, backend)
  }

  private fun acquireInference(
    model: ModelDefinition,
    file: File,
    backend: LocalInferenceBackend,
  ): InferenceLease {
    if (model.runtime != InferenceRuntime.LITERT_LM) {
      return InferenceLease(createInference(model, file, backend), retained = false)
    }

    val key = inferenceCacheKey(model, file, backend)
    cachedInference?.takeIf { it.key == key }?.let { cached ->
      return InferenceLease(cached.inference, retained = true)
    }

    releaseCachedInferenceLocked()
    val inference = createInference(model, file, backend)
    cachedInference = CachedInference(key, inference)
    return InferenceLease(inference, retained = true)
  }

  private fun inferenceCacheKey(
    model: ModelDefinition,
    file: File,
    backend: LocalInferenceBackend,
  ) = InferenceCacheKey(
    modelId = model.id,
    backend = backend,
    fileLength = file.length(),
    fileModifiedAt = file.lastModified(),
  )

  private fun invalidateRetainedInferenceLocked(inference: AutoCloseable) {
    if (cachedInference?.inference === inference) releaseCachedInferenceLocked()
  }

  private fun releaseCachedInferenceLocked() {
    val cached = cachedInference ?: return
    cachedInference = null
    runCatching { cached.inference.close() }
  }

  private fun createInference(
    model: ModelDefinition,
    file: File,
    backend: LocalInferenceBackend,
  ): AutoCloseable = when (model.runtime) {
    InferenceRuntime.MEDIAPIPE_TASK -> {
      val options = LlmInference.LlmInferenceOptions.builder()
        .setModelPath(file.absolutePath)
        .setMaxTokens(model.contextTokens)
        .setMaxTopK(40)
        .setPreferredBackend(
          when (backend) {
            LocalInferenceBackend.CPU -> LlmInference.Backend.CPU
            LocalInferenceBackend.GPU -> LlmInference.Backend.GPU
          },
        )
        .build()
      LlmInference.createFromOptions(appContext, options)
    }
    InferenceRuntime.LITERT_LM -> LiteRtLmInference(
      file = file,
      cacheDirectory = modelBackendCacheDirectory(model, backend),
      backend = backend,
    )
  }

  private fun generateResponse(inference: AutoCloseable, prompt: String): String = when (inference) {
    is LlmInference -> inference.generateResponse(prompt)
    is LiteRtLmInference -> inference.generate(prompt)
    else -> error("未知の推論ランタイムです")
  }

  private fun generateResponseStreaming(
    inference: AutoCloseable,
    prompt: String,
    onChunk: (String) -> Unit,
  ): String = when (inference) {
    is LlmInference -> inference.generateResponseAsync(
      prompt,
      ProgressListener<String> { partial, _ -> onChunk(partial) },
    ).get()
    is LiteRtLmInference -> inference.generateStreaming(prompt, onChunk)
    else -> error("未知の推論ランタイムです")
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

  private fun resolveSelectedModel(): ModelDefinition? {
    val selectedId = preferences.getString(SELECTED_MODEL_KEY, null)
    MODEL_CATALOG.firstOrNull { it.id == selectedId && isValidModelFile(modelFile(it), it) }?.let { return it }
    val first = MODEL_CATALOG.firstOrNull { isValidModelFile(modelFile(it), it) }
    if (first != null) preferences.edit().putString(SELECTED_MODEL_KEY, first.id).apply()
    return first
  }

  private fun readInferenceSettings(): LocalInferenceSettings {
    val backend = preferences.getString(INFERENCE_BACKEND_KEY, null)
      ?.let { runCatching { LocalInferenceBackend.valueOf(it) }.getOrNull() }
      ?: LocalInferenceBackend.CPU
    return LocalInferenceSettings(
      backend = backend,
      thinkingEnabled = preferences.getBoolean(THINKING_ENABLED_KEY, false),
    )
  }

  private fun currentInferenceSettings(): LocalInferenceSettings =
    readInferenceSettings().also { settings ->
      if (_inferenceSettings.value != settings) _inferenceSettings.value = settings
    }

  private fun String.withThinkingMode(
    model: ModelDefinition,
    settings: LocalInferenceSettings,
  ): String = if (model.supportsThinking) {
    ThinkingMode.apply(this, settings.thinkingEnabled)
  } else {
    this
  }

  private fun openDownloadConnection(initialUrl: String): HttpURLConnection {
    var currentUrl = initialUrl
    repeat(MAX_REDIRECTS) {
      val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MILLIS
        readTimeout = DOWNLOAD_READ_TIMEOUT_MILLIS
        instanceFollowRedirects = false
        setRequestProperty("Accept-Encoding", "identity")
        setRequestProperty("User-Agent", "Yomitori-RSS-Reader/0.2")
      }
      val status = connection.responseCode
      if (status in 300..399) {
        val location = connection.getHeaderField("Location") ?: throw IOException("リダイレクト先がありません")
        currentUrl = URL(URL(currentUrl), location).toString()
        connection.disconnect()
      } else {
        if (status !in 200..299) {
          connection.disconnect()
          throw IOException("HTTP $status")
        }
        return connection
      }
    }
    throw IOException("リダイレクト回数が上限を超えました")
  }

  private fun createSummaryPrompt(
    text: String,
    maxInputChars: Int,
    runtime: InferenceRuntime,
    promptTemplate: String,
  ): String {
    val normalized = text.replace(Regex("\\s+"), " ").trim()
    val article = if (normalized.length <= maxInputChars) normalized else {
      val headLength = maxInputChars * 3 / 4
      normalized.take(headLength) + "\n（中略）\n" + normalized.takeLast(maxInputChars - headLength)
    }
    val instruction = SummaryPrompt.render(promptTemplate, article)
    return when (runtime) {
      InferenceRuntime.MEDIAPIPE_TASK -> """
        <|im_start|>system
        あなたはニュース記事の要約担当です。本文だけを根拠に、日本語で簡潔に回答してください。<|im_end|>
        <|im_start|>user
        $instruction<|im_end|>
        <|im_start|>assistant
      """.trimIndent()
      InferenceRuntime.LITERT_LM -> instruction
    }
  }

  private fun cleanSummary(value: String): String {
    val result = value
      .substringBefore("<|im_end|>")
      .substringBefore("<end_of_turn>")
      .replace(Regex("<think>.*?</think>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
      .replace(Regex("^要約[:：]?\\s*", RegexOption.IGNORE_CASE), "")
      .trim()
    check(result.isNotBlank()) { "要約結果が空です" }
    return result
  }

  private fun modelFile(model: ModelDefinition) = File(modelsDirectory(), model.fileName)
  private fun temporaryModelFile(model: ModelDefinition) = File(modelsDirectory(), "${model.fileName}.part")
  private fun modelsDirectory() = File(appContext.filesDir, "local-summary-models").apply { mkdirs() }
  private fun modelCacheDirectory(model: ModelDefinition) =
    File(appContext.cacheDir, "local-summary-models/${model.id}")
  private fun modelBackendCacheDirectory(model: ModelDefinition, backend: LocalInferenceBackend) =
    File(modelCacheDirectory(model), backend.name.lowercase()).apply { mkdirs() }

  private fun isValidModelFile(file: File, model: ModelDefinition): Boolean {
    if (!file.isFile) return false
    val minimum = (model.estimatedSizeBytes * MINIMUM_MODEL_SIZE_RATIO).toLong()
    val maximum = (model.estimatedSizeBytes * MAXIMUM_MODEL_SIZE_RATIO).toLong()
    return file.length() in minimum..maximum
  }

  private fun requireModel(modelId: String): ModelDefinition =
    MODEL_CATALOG.firstOrNull { it.id == modelId } ?: error("モデルが見つかりません: $modelId")

  private fun deviceMemoryBytes(): Long {
    val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      info.advertisedMem.takeIf { it > 0 } ?: info.totalMem
    } else {
      info.totalMem
    }
  }

  private fun estimatedStageDurationMillis(stage: String, modelId: String): Long? =
    preferences.getLong("$stage.$modelId.duration_millis", 0).takeIf { it > 0 }

  private fun recordStageDuration(stage: String, modelId: String, durationMillis: Long) {
    val key = "$stage.$modelId.duration_millis"
    val previous = preferences.getLong(key, 0)
    val smoothed = if (previous > 0) (previous * 3 + durationMillis) / 4 else durationMillis
    preferences.edit().putLong(key, smoothed).apply()
  }

  private fun estimateRemainingMillis(downloaded: Long, total: Long, elapsed: Long): Long? {
    if (downloaded <= 0 || total <= downloaded || elapsed < 1_500) return null
    val bytesPerMillis = downloaded.toDouble() / elapsed
    return ((total - downloaded) / bytesPerMillis).toLong().coerceAtLeast(0)
  }

  private enum class InferenceRuntime { MEDIAPIPE_TASK, LITERT_LM }

  private data class InferenceCacheKey(
    val modelId: String,
    val backend: LocalInferenceBackend,
    val fileLength: Long,
    val fileModifiedAt: Long,
  )

  private data class CachedInference(
    val key: InferenceCacheKey,
    val inference: AutoCloseable,
  )

  private data class InferenceLease(
    val inference: AutoCloseable,
    val retained: Boolean,
  )

  private data class ModelDefinition(
    val id: String,
    val name: String,
    val description: String,
    val source: String,
    val license: String,
    val quantization: String,
    val contextTokens: Int,
    val maxInputChars: Int,
    val estimatedSizeBytes: Long,
    val fileName: String,
    val downloadUrl: String,
    val minDeviceMemoryGb: Int,
    val runtime: InferenceRuntime,
    val recommended: Boolean = false,
    val supportsThinking: Boolean = false,
  )

  companion object {
    private const val DOWNLOAD_BUFFER_SIZE = 1024 * 1024
    private const val DOWNLOAD_CONNECT_TIMEOUT_MILLIS = 30_000
    private const val DOWNLOAD_READ_TIMEOUT_MILLIS = 60_000
    private const val DOWNLOAD_PROGRESS_INTERVAL_MILLIS = 250L
    private const val DOWNLOAD_STORAGE_MARGIN_BYTES = 256L * 1024 * 1024
    private const val MAX_REDIRECTS = 8
    private const val BYTES_PER_GB = 1024L * 1024 * 1024
    private const val MINIMUM_DEVICE_MEMORY_BYTES = 4L * BYTES_PER_GB
    private const val MINIMUM_MODEL_SIZE_RATIO = 0.98
    private const val MAXIMUM_MODEL_SIZE_RATIO = 1.02
    private const val PREFERENCES_NAME = "local_summary_models"
    private const val SELECTED_MODEL_KEY = "selected_model_id"
    private const val SUMMARY_PROMPT_KEY = "summary_prompt"
    private const val INFERENCE_BACKEND_KEY = "inference_backend"
    private const val THINKING_ENABLED_KEY = "thinking_enabled"

    private val MODEL_CATALOG = listOf(
      ModelDefinition(
        "qwen2.5-0.5b-q8",
        "Qwen2.5 0.5B 軽量",
        "待ち時間とメモリ使用量を抑えた軽量モデル。短い記事の要約に適しています。",
        "litert-community/Qwen2.5-0.5B-Instruct",
        "Apache-2.0",
        "Dynamic INT8",
        1280,
        700,
        546_660_344,
        "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
        4,
        InferenceRuntime.MEDIAPIPE_TASK,
        true,
      ),
      ModelDefinition(
        "gemma4-e2b-it",
        "Gemma 4 E2B",
        "長めの記事や日本語要約の品質を優先する軽量Gemma 4モデルです。",
        "litert-community/gemma-4-E2B-it-litert-lm",
        "Apache-2.0",
        "Mixed 2/4/8-bit",
        4096,
        2500,
        2_588_147_712,
        "gemma-4-E2B-it.litertlm",
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/7fa1d78473894f7e736a21d920c3aa80f950c0db/gemma-4-E2B-it.litertlm?download=true",
        8,
        InferenceRuntime.LITERT_LM,
      ),
      ModelDefinition(
        "qwen3-4b-mixed-int4",
        "Qwen3 4B",
        "品質と端末内実行のバランスを重視したモデル。Thinkingの切り替えに対応します。",
        "litert-community/Qwen3-4B",
        "Apache-2.0",
        "Mixed INT4",
        2048,
        1200,
        2_659_063_000,
        "qwen3_4b_mixed_int4.litertlm",
        "https://huggingface.co/litert-community/Qwen3-4B/resolve/main/qwen3_4b_mixed_int4.litertlm?download=true",
        8,
        InferenceRuntime.LITERT_LM,
        supportsThinking = true,
      ),
      ModelDefinition(
        "gemma4-e4b-it",
        "Gemma 4 E4B 高品質",
        "品質を優先するGemma 4モデルです。約3.7 GBの保存容量と12 GB級のメモリを推奨します。",
        "litert-community/gemma-4-E4B-it-litert-lm",
        "Apache-2.0",
        "Mixed 2/4/8-bit",
        4096,
        2500,
        3_659_530_240,
        "gemma-4-E4B-it.litertlm",
        "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/9695417f248178c63a9f318c6e0c56cb917cb837/gemma-4-E4B-it.litertlm?download=true",
        12,
        InferenceRuntime.LITERT_LM,
      ),
      ModelDefinition(
        "qwen2.5-1.5b-q8",
        "Qwen2.5 1.5B 高品質",
        "軽量版より大きく、要約の安定性を優先するモデルです。",
        "litert-community/Qwen2.5-1.5B-Instruct",
        "Apache-2.0",
        "Dynamic INT8",
        1280,
        700,
        1_625_493_432,
        "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
        6,
        InferenceRuntime.MEDIAPIPE_TASK,
      ),
    )
  }
}

private class LiteRtLmInference(
  file: File,
  cacheDirectory: File,
  backend: LocalInferenceBackend,
) : AutoCloseable {
  private val engine = Engine(
    EngineConfig(
      modelPath = file.absolutePath,
      backend = when (backend) {
        LocalInferenceBackend.CPU -> Backend.CPU()
        LocalInferenceBackend.GPU -> Backend.GPU()
      },
      cacheDir = cacheDirectory.absolutePath,
    ),
  ).also { it.initialize() }

  fun generate(prompt: String): String =
    engine.createConversation().use { conversation ->
      conversation.sendMessage(prompt).toString()
    }

  fun generateStreaming(prompt: String, onChunk: (String) -> Unit): String =
    engine.createConversation().use { conversation ->
      val response = StringBuilder()
      runBlocking {
        conversation.sendMessageAsync(prompt).collect { message ->
          val chunk = message.toString()
          response.append(chunk)
          onChunk(chunk)
        }
      }
      response.toString()
    }

  override fun close() {
    engine.close()
  }
}
