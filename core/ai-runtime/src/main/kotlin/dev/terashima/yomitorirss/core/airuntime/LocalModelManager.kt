package dev.terashima.yomitorirss.core.airuntime

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

class LocalModelManager(context: Context) : AutoCloseable {
  private val appContext = context.applicationContext
  private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private val contextBenchmarkStore = LocalContextBenchmarkStore(appContext)
  private val downloadLock = ReentrantLock()
  private val inferenceLock = ReentrantLock()
  private val tokenizerLock = ReentrantLock()
  private val cancelRequested = AtomicBoolean(false)
  private var cachedInference: CachedInference? = null
  private var cachedTokenizer: CachedTokenizer? = null
  private val inferenceSessions = LocalInferenceSessionTracker(
    idleTimeoutMillis = INFERENCE_IDLE_TIMEOUT_MILLIS,
    onIdle = ::releaseIdleInference,
  )

  private val _models = MutableStateFlow<List<LocalModelStatus>>(emptyList())
  val models: StateFlow<List<LocalModelStatus>> = _models.asStateFlow()

  private val _downloadProgress = MutableStateFlow<ModelDownloadProgress?>(null)
  val downloadProgress: StateFlow<ModelDownloadProgress?> = _downloadProgress.asStateFlow()

  private val _inferenceProgress = MutableStateFlow<LocalInferenceProgress?>(null)
  val inferenceProgress: StateFlow<LocalInferenceProgress?> = _inferenceProgress.asStateFlow()

  private val _inferenceSettings = MutableStateFlow(readInferenceSettings())
  val inferenceSettings: StateFlow<LocalInferenceSettings> = _inferenceSettings.asStateFlow()

  init {
    cleanupOutdatedModelArtifacts()
    refreshModels()
  }

  fun isSupported(): Boolean =
    Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } &&
      deviceMemoryBytes() >= MINIMUM_DEVICE_MEMORY_BYTES

  fun refreshModels() {
    val selectedId = resolveSelectedModel()?.id
    val settings = readInferenceSettings()
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
        supportsThinking = false,
        supportsSpeculativeDecoding = model.supportsSpeculativeDecoding,
        contextTokens = effectiveContextTokens(model, settings),
        maxInputChars = model.maxInputChars,
        promptBudgetChars = model.promptBudgetChars,
        promptFormat = LocalPromptFormat.PLAIN,
      )
    }
  }

  fun selectedModel(): LocalModelStatus? {
    refreshModels()
    return models.value.firstOrNull(LocalModelStatus::selected)
  }

  fun setInferenceBackend(backend: LocalInferenceBackend) {
    preferences.edit().putString(INFERENCE_BACKEND_KEY, backend.name).apply()
    _inferenceSettings.value = _inferenceSettings.value.copy(backend = backend)
    refreshModels()
  }

  fun setThinkingEnabled(enabled: Boolean) {
    preferences.edit().putBoolean(THINKING_ENABLED_KEY, enabled).apply()
    _inferenceSettings.value = _inferenceSettings.value.copy(thinkingEnabled = enabled)
  }

  fun setSpeculativeDecodingEnabled(enabled: Boolean) {
    preferences.edit().putBoolean(SPECULATIVE_DECODING_ENABLED_KEY, enabled).apply()
    _inferenceSettings.value = _inferenceSettings.value.copy(speculativeDecodingEnabled = enabled)
    refreshModels()
  }

  fun setContextSizeMode(mode: LocalContextSizeMode) {
    preferences.edit().putString(CONTEXT_SIZE_MODE_KEY, mode.name).apply()
    _inferenceSettings.value = _inferenceSettings.value.copy(contextSizeMode = mode)
    refreshModels()
  }

  fun inferenceCacheVariant(modelId: String): String {
    val model = requireModel(modelId)
    val settings = currentInferenceSettings()
    val contextTokens = effectiveContextTokens(model, settings)
    val speculativeDecodingEnabled = settings.speculativeDecodingEnabled && model.supportsSpeculativeDecoding
    return buildString {
      append("context-")
      append(contextTokens)
      if (speculativeDecodingEnabled) append("-speculative-decoding-v1")
    }
  }

  internal fun maxSupportedContextTokens(modelId: String): Int = requireModel(modelId).contextTokens

  internal fun releaseRetainedInferenceForBenchmark() {
    cancelRequested.set(true)
    inferenceLock.withLock {
      releaseCachedInferenceLocked()
    }
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
      tokenizerLock.withLock {
        if (cachedInference?.key?.modelId == model.id) releaseCachedInferenceLocked()
        if (cachedTokenizer?.key?.modelId == model.id) releaseCachedTokenizerLocked()
        modelFile(model).delete()
        temporaryModelFile(model).delete()
        modelCacheDirectory(model).deleteRecursively()
      }
    }
    preferences.edit().remove(modelRevisionKey(model)).apply()
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
      preferences.edit().remove(modelRevisionKey(model)).apply()
      val requiredBytes = model.estimatedSizeBytes + DOWNLOAD_STORAGE_MARGIN_BYTES
      val availableBytes = StatFs(modelsDirectory().absolutePath).availableBytes
      check(availableBytes >= requiredBytes) { "モデルを保存する空き容量が不足しています" }

      val temporary = temporaryModelFile(model)
      temporary.delete()
      var connection: HttpURLConnection? = null
      try {
        connection = openModelDownloadConnection(model.downloadUrl)
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
        check(isExpectedModelArtifact(temporary, model)) { "ダウンロードしたモデルファイルが不正です" }
        if (!temporary.renameTo(destination)) {
          temporary.copyTo(destination, overwrite = true)
          temporary.delete()
        }
        preferences.edit().putString(modelRevisionKey(model), model.artifactRevision).apply()
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

  fun countTokens(text: String): Int {
    check(isSupported()) { "この端末ではローカルモデルを利用できません" }
    val model = resolveSelectedModel() ?: error("AIモデルをダウンロードして選択してください")
    val file = modelFile(model)
    check(isValidModelFile(file, model)) { "選択したモデルが見つかりません" }
    return tokenizerLock.withLock {
      acquireTokenizer(model, file).count(text)
    }
  }

  fun generate(
    prompt: String,
    streaming: Boolean = false,
    onPartial: (String) -> Unit = {},
  ): String {
    require(prompt.isNotBlank()) { "推論プロンプトを入力してください" }
    return withInference { inference ->
      if (streaming) inference.generateStreaming(prompt, onPartial) else inference.generate(prompt)
    }
  }

  fun generateWithImage(
    prompt: String,
    imageBytes: ByteArray,
  ): String {
    require(prompt.isNotBlank()) { "推論プロンプトを入力してください" }
    require(imageBytes.isNotEmpty()) { "画像データがありません" }
    return withInference(visionEnabled = true) { inference ->
      inference.generateWithImage(prompt, imageBytes)
    }
  }

  fun generateImageToolCall(
    systemInstruction: String,
    userMessage: String,
    imageBytes: ByteArray,
    tools: List<LocalInferenceTool>,
  ): LocalInferenceToolCall? {
    require(systemInstruction.isNotBlank()) { "System instruction must not be blank" }
    require(userMessage.isNotBlank()) { "User message must not be blank" }
    require(imageBytes.isNotEmpty()) { "画像データがありません" }
    require(tools.isNotEmpty()) { "At least one tool is required" }
    require(tools.map(LocalInferenceTool::name).distinct().size == tools.size) { "Tool names must be unique" }
    return withInference(visionEnabled = true) { inference ->
      inference.generateImageToolCall(systemInstruction, userMessage, imageBytes, tools)
    }
  }

  fun generateConversation(
    request: LocalInferenceConversationRequest,
    streaming: Boolean = false,
    onPartial: (String) -> Unit = {},
  ): String = withInference { inference ->
    inference.generateConversation(request, streaming, onPartial)
  }

  override fun close() {
    inferenceSessions.close()
    cancelRequested.set(true)
    inferenceLock.withLock {
      releaseCachedInferenceLocked()
    }
    tokenizerLock.withLock {
      releaseCachedTokenizerLocked()
    }
  }

  private fun <T> withInference(
    visionEnabled: Boolean = false,
    block: (LiteRtLmInference) -> T,
  ): T {
    check(isSupported()) { "この端末ではローカルモデルを利用できません" }
    val model = resolveSelectedModel() ?: error("AIモデルをダウンロードして選択してください")
    val file = modelFile(model)
    check(isValidModelFile(file, model)) { "選択したモデルが見つかりません" }
    val session = inferenceSessions.openSession()

    return try {
      cancelRequested.set(false)
      inferenceLock.withLock {
        try {
          val settings = currentInferenceSettings()
          val contextTokens = effectiveContextTokens(model, settings)
          val speculativeDecodingEnabled =
            !visionEnabled && settings.speculativeDecodingEnabled && model.supportsSpeculativeDecoding
          val cacheHit = hasReusableInference(
            model = model,
            file = file,
            backend = settings.backend,
            contextTokens = contextTokens,
            speculativeDecodingEnabled = speculativeDecodingEnabled,
            visionEnabled = visionEnabled,
          )
          val preparationStartedAt = if (cacheHit) null else SystemClock.elapsedRealtime()
          if (!cacheHit) {
            _inferenceProgress.value = LocalInferenceProgress(
              LocalInferenceStage.PREPARING_MODEL,
              model.name,
              estimatedStageDurationMillis(PREPARING_MODEL_DURATION_KEY, model.id),
            )
          }
          val inference = acquireInference(
            model = model,
            file = file,
            backend = settings.backend,
            contextTokens = contextTokens,
            speculativeDecodingEnabled = speculativeDecodingEnabled,
            visionEnabled = visionEnabled,
          )
          if (visionEnabled && !cacheHit) {
            LocalAiMemoryDiagnostics.recordVisionInference(
              appContext,
              LocalAiMemoryDiagnosticPhase.VISION_AFTER_ENGINE_INIT,
            )
          }
          preparationStartedAt?.let { startedAt ->
            recordStageDuration(PREPARING_MODEL_DURATION_KEY, model.id, SystemClock.elapsedRealtime() - startedAt)
          }
          check(!cancelRequested.get()) { "推論をキャンセルしました" }

          val generationStartedAt = SystemClock.elapsedRealtime()
          _inferenceProgress.value = LocalInferenceProgress(
            LocalInferenceStage.GENERATING_RESPONSE,
            model.name,
            estimatedStageDurationMillis(GENERATING_RESPONSE_DURATION_KEY, model.id),
          )
          try {
            try {
              block(inference).also {
                recordStageDuration(
                  GENERATING_RESPONSE_DURATION_KEY,
                  model.id,
                  SystemClock.elapsedRealtime() - generationStartedAt,
                )
              }
            } finally {
              if (visionEnabled) {
                LocalAiMemoryDiagnostics.recordVisionInference(
                  appContext,
                  LocalAiMemoryDiagnosticPhase.VISION_AFTER_INFERENCE,
                )
              }
            }
          } catch (error: Throwable) {
            invalidateRetainedInferenceLocked(inference)
            throw error
          }
        } finally {
          _inferenceProgress.value = null
        }
      }
    } finally {
      session.close()
    }
  }

  private fun hasReusableInference(
    model: ModelDefinition,
    file: File,
    backend: LocalInferenceBackend,
    contextTokens: Int,
    speculativeDecodingEnabled: Boolean,
    visionEnabled: Boolean,
  ): Boolean = cachedInference?.key == inferenceCacheKey(
    model = model,
    file = file,
    backend = backend,
    contextTokens = contextTokens,
    speculativeDecodingEnabled = speculativeDecodingEnabled,
    visionEnabled = visionEnabled,
  )

  private fun acquireInference(
    model: ModelDefinition,
    file: File,
    backend: LocalInferenceBackend,
    contextTokens: Int,
    speculativeDecodingEnabled: Boolean,
    visionEnabled: Boolean,
  ): LiteRtLmInference {
    val key = inferenceCacheKey(
      model,
      file,
      backend,
      contextTokens,
      speculativeDecodingEnabled,
      visionEnabled,
    )
    cachedInference?.takeIf { it.key == key }?.let { cached -> return cached.inference }

    releaseCachedInferenceLocked()
    val inference = LiteRtLmInference(
      file = file,
      cacheDirectory = modelBackendCacheDirectory(
        model = model,
        backend = backend,
        contextTokens = contextTokens,
        speculativeDecodingEnabled = speculativeDecodingEnabled,
        visionEnabled = visionEnabled,
      ),
      backend = backend,
      contextTokens = contextTokens,
      speculativeDecodingEnabled = speculativeDecodingEnabled,
      visionEnabled = visionEnabled,
    )
    cachedInference = CachedInference(key, inference)
    return inference
  }

  private fun acquireTokenizer(
    model: ModelDefinition,
    file: File,
  ): LiteRtLmTokenizer {
    val key = tokenizerCacheKey(model, file)
    cachedTokenizer?.takeIf { it.key == key }?.let { cached -> return cached.tokenizer }

    releaseCachedTokenizerLocked()
    val tokenizer = LiteRtLmTokenizer(
      modelFile = file,
      cacheDirectory = modelTokenizerCacheDirectory(model),
    )
    cachedTokenizer = CachedTokenizer(key, tokenizer)
    return tokenizer
  }

  private fun inferenceCacheKey(
    model: ModelDefinition,
    file: File,
    backend: LocalInferenceBackend,
    contextTokens: Int,
    speculativeDecodingEnabled: Boolean,
    visionEnabled: Boolean,
  ) = InferenceCacheKey(
    modelId = model.id,
    backend = backend,
    contextTokens = contextTokens,
    speculativeDecodingEnabled = speculativeDecodingEnabled,
    visionEnabled = visionEnabled,
    fileLength = file.length(),
    fileModifiedAt = file.lastModified(),
  )

  private fun tokenizerCacheKey(
    model: ModelDefinition,
    file: File,
  ) = TokenizerCacheKey(
    modelId = model.id,
    fileLength = file.length(),
    fileModifiedAt = file.lastModified(),
  )

  private fun invalidateRetainedInferenceLocked(inference: LiteRtLmInference) {
    if (cachedInference?.inference === inference) releaseCachedInferenceLocked()
  }

  private fun releaseIdleInference() {
    inferenceLock.withLock {
      releaseCachedInferenceLocked()
    }
  }

  private fun releaseCachedInferenceLocked() {
    val cached = cachedInference ?: return
    cachedInference = null
    val closeError = runCatching { cached.inference.close() }.exceptionOrNull()
    if (cached.key.visionEnabled) {
      LocalAiMemoryDiagnostics.recordVisionInference(
        context = appContext,
        phase = LocalAiMemoryDiagnosticPhase.VISION_AFTER_ENGINE_RELEASE,
        engineCloseStatus = if (closeError == null) {
          LocalAiEngineCloseStatus.SUCCESS
        } else {
          LocalAiEngineCloseStatus.FAILED
        },
        engineCloseErrorClass = closeError?.javaClass?.name,
      )
    }
  }

  private fun releaseCachedTokenizerLocked() {
    val cached = cachedTokenizer ?: return
    cachedTokenizer = null
    runCatching { cached.tokenizer.close() }
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
    val contextSizeMode = preferences.getString(CONTEXT_SIZE_MODE_KEY, null)
      ?.let { runCatching { LocalContextSizeMode.valueOf(it) }.getOrNull() }
      ?: LocalContextSizeMode.AUTO
    return LocalInferenceSettings(
      backend = backend,
      thinkingEnabled = false,
      speculativeDecodingEnabled = preferences.getBoolean(SPECULATIVE_DECODING_ENABLED_KEY, false),
      contextSizeMode = contextSizeMode,
    )
  }

  private fun currentInferenceSettings(): LocalInferenceSettings =
    readInferenceSettings().also { settings ->
      if (_inferenceSettings.value != settings) _inferenceSettings.value = settings
    }

  private fun effectiveContextTokens(
    model: ModelDefinition,
    settings: LocalInferenceSettings,
  ): Int {
    val speculativeDecodingEnabled = settings.speculativeDecodingEnabled && model.supportsSpeculativeDecoding
    val recommendation = contextBenchmarkStore.recommendedContextTokens(
      modelId = model.id,
      backend = settings.backend,
      speculativeDecodingEnabled = speculativeDecodingEnabled,
    )
    return resolveContextTokens(
      mode = settings.contextSizeMode,
      maxSupportedContextTokens = model.contextTokens,
      benchmarkRecommendation = recommendation,
    )
  }


  private fun modelFile(model: ModelDefinition) = File(modelsDirectory(), model.fileName)
  private fun temporaryModelFile(model: ModelDefinition) = File(modelsDirectory(), "${model.fileName}.part")
  private fun modelsDirectory() = File(appContext.filesDir, "local-summary-models").apply { mkdirs() }
  private fun modelCacheDirectory(model: ModelDefinition) =
    File(appContext.cacheDir, "local-summary-models/${model.id}")
  private fun modelBackendCacheDirectory(
    model: ModelDefinition,
    backend: LocalInferenceBackend,
    contextTokens: Int,
    speculativeDecodingEnabled: Boolean,
    visionEnabled: Boolean,
  ) = File(
    modelCacheDirectory(model),
    "${backend.name.lowercase()}/${if (speculativeDecodingEnabled) "speculative" else "standard"}/" +
      "context-$contextTokens${if (visionEnabled) "-vision" else ""}",
  ).apply { mkdirs() }
  private fun modelTokenizerCacheDirectory(model: ModelDefinition) =
    File(modelCacheDirectory(model), "tokenizer").apply { mkdirs() }

  private fun cleanupOutdatedModelArtifacts() {
    MODEL_CATALOG.forEach { model ->
      val file = modelFile(model)
      if (file.exists() && !isValidModelFile(file, model)) {
        file.delete()
        temporaryModelFile(model).delete()
        modelCacheDirectory(model).deleteRecursively()
        preferences.edit().remove(modelRevisionKey(model)).apply()
      }
    }
  }

  private fun isExpectedModelArtifact(file: File, model: ModelDefinition): Boolean =
    file.isFile && file.length() == model.estimatedSizeBytes

  private fun isValidModelFile(file: File, model: ModelDefinition): Boolean =
    isExpectedModelArtifact(file, model) &&
      preferences.getString(modelRevisionKey(model), null) == model.artifactRevision

  private fun modelRevisionKey(model: ModelDefinition): String =
    "$MODEL_REVISION_KEY_PREFIX.${model.id}"

  private fun requireModel(modelId: String): ModelDefinition =
    MODEL_CATALOG.firstOrNull { it.id == modelId } ?: error("モデルが見つかりません: $modelId")

  private fun deviceMemoryBytes(): Long {
    val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    return info.advertisedMem.takeIf { it > 0 } ?: info.totalMem
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

  private data class InferenceCacheKey(
    val modelId: String,
    val backend: LocalInferenceBackend,
    val contextTokens: Int,
    val speculativeDecodingEnabled: Boolean,
    val visionEnabled: Boolean,
    val fileLength: Long,
    val fileModifiedAt: Long,
  )

  private data class CachedInference(
    val key: InferenceCacheKey,
    val inference: LiteRtLmInference,
  )

  private data class TokenizerCacheKey(
    val modelId: String,
    val fileLength: Long,
    val fileModifiedAt: Long,
  )

  private data class CachedTokenizer(
    val key: TokenizerCacheKey,
    val tokenizer: LiteRtLmTokenizer,
  )

  companion object {
    private const val DOWNLOAD_BUFFER_SIZE = 1024 * 1024
    private const val DOWNLOAD_PROGRESS_INTERVAL_MILLIS = 250L
    private const val DOWNLOAD_STORAGE_MARGIN_BYTES = 256L * 1024 * 1024
    private const val BYTES_PER_GB = 1024L * 1024 * 1024
    private const val MINIMUM_DEVICE_MEMORY_BYTES = 4L * BYTES_PER_GB
    private const val PREFERENCES_NAME = "local_summary_models"
    private const val SELECTED_MODEL_KEY = "selected_model_id"
    private const val INFERENCE_BACKEND_KEY = "inference_backend"
    private const val THINKING_ENABLED_KEY = "thinking_enabled"
    private const val SPECULATIVE_DECODING_ENABLED_KEY = "speculative_decoding_enabled"
    private const val CONTEXT_SIZE_MODE_KEY = "context_size_mode"
    private const val MODEL_REVISION_KEY_PREFIX = "model_revision"
    private const val PREPARING_MODEL_DURATION_KEY = "preparing_model"
    private const val GENERATING_RESPONSE_DURATION_KEY = "generating_response"
    private const val INFERENCE_IDLE_TIMEOUT_MILLIS = 5L * 60L * 1000L

    @Volatile
    private var sharedInferenceManager: LocalModelManager? = null

    fun shared(context: Context): LocalModelManager =
      sharedInferenceManager ?: synchronized(this) {
        sharedInferenceManager
          ?: LocalModelManager(context.applicationContext).also { sharedInferenceManager = it }
      }

  }
}
