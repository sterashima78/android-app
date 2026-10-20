package dev.terashima.yomitorirss.core.airuntime

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import dev.terashima.yomitorirss.core.aiinference.BackgroundTextEmbedding
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskPriority
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import dev.terashima.yomitorirss.core.network.TrafficTelemetry
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A short-lived embedding runtime. It is closed before chapter generation loads the text LLM.
 * Downloads the public model bundle once into app-private storage; article content never leaves
 * the device from this adapter.
 */
class LiteRtTextEmbedding(context: Context) : BackgroundTextEmbedding {
  private val applicationContext = context.applicationContext
  private val artifactPreferences = applicationContext.getSharedPreferences("embedding_model_artifacts", Context.MODE_PRIVATE)

  override suspend fun embed(texts: List<String>): List<FloatArray> {
    if (texts.isEmpty()) return emptyList()
    val model = withContext(Dispatchers.IO) { ensureModel() }
    return LocalAiBackgroundTaskGate.withPermit(priority = LocalAiBackgroundTaskPriority.NORMAL) {
      withContext(Dispatchers.IO) {
        EmbeddingEngine(
          EmbeddingEngineConfig(
            modelPath = model.absolutePath,
            backend = Backend.CPU(),
            cacheDir = applicationContext.cacheDir.absolutePath,
            maxInputLength = 512,
          ),
        ).use { engine ->
          engine.initialize()
          texts.chunked(8).flatMap { batch ->
            engine.computeEmbeddingBatch(
              batch.map { listOf(InputData.Text(it)) },
              EmbeddingOptions(normalize = true, outputSize = 256),
            ).map { it.embedding }
          }
        }
      }
    }
  }

  private fun ensureModel(): File = synchronized(MODEL_DOWNLOAD_LOCK) {
    val directory = File(applicationContext.noBackupFilesDir, "embedding-models")
    check(directory.isDirectory || directory.mkdirs()) { "embedding model directory unavailable" }
    val file = File(directory, MODEL_FILE)
    if (
      artifactPreferences.getString(MODEL_REVISION_KEY, null) == MODEL_REVISION &&
      matchesSha256(file, MODEL_SHA256)
    ) return@synchronized file

    // An unverified or obsolete artifact must not be treated as the current model.
    file.delete()
    artifactPreferences.edit().remove(MODEL_REVISION_KEY).commit()
    val temporary = File(directory, "$MODEL_FILE.partial")
    temporary.delete()
    val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
      connectTimeout = 30_000
      readTimeout = 60_000
      instanceFollowRedirects = true
    }
    var downloadedBytes = 0L
    try {
      check(connection.responseCode in 200..299) { "embedding model download failed" }
      connection.inputStream.use { input ->
        temporary.outputStream().buffered().use { output -> downloadedBytes = input.copyTo(output) }
      }
      check(matchesSha256(temporary, MODEL_SHA256)) { "embedding model checksum mismatch" }
      check(temporary.renameTo(file)) { "failed to store embedding model" }
      check(artifactPreferences.edit().putString(MODEL_REVISION_KEY, MODEL_REVISION).commit()) {
        "failed to record embedding model revision"
      }
    } finally {
      connection.disconnect()
      TrafficTelemetry.record("model", downloadedBytes, 0L)
      temporary.delete()
    }
    file
  }

  private companion object {
    val MODEL_DOWNLOAD_LOCK = Any()
    const val MODEL_FILE = "embeddinggemma-2-text-270m.litertlm"
    const val MODEL_REVISION_KEY = "embedding_model_revision"
    const val MODEL_REVISION = "fa7d77ca8c97ea16ebe0255fde8934d717d0138a"
    const val MODEL_SHA256 = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb"
    const val MODEL_URL =
      "https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/$MODEL_REVISION/embeddinggemma-2-text-270m.litertlm"
  }
}

internal fun matchesSha256(file: File, expected: String): Boolean {
  if (!file.isFile) return false
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().buffered().use { input ->
    val buffer = ByteArray(64 * 1024)
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      digest.update(buffer, 0, count)
    }
  }
  return digest.digest().joinToString("") { byte ->
    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
  }.equals(expected, ignoreCase = true)
}
