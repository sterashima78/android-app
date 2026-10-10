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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A short-lived embedding runtime. It is closed before chapter generation loads the text LLM.
 * Downloads the public model bundle once into app-private storage; article content never leaves
 * the device from this adapter.
 */
class LiteRtTextEmbedding(context: Context) : BackgroundTextEmbedding {
  private val applicationContext = context.applicationContext

  override suspend fun embed(texts: List<String>): List<FloatArray> {
    if (texts.isEmpty()) return emptyList()
    return LocalAiBackgroundTaskGate.withPermit(priority = LocalAiBackgroundTaskPriority.NORMAL) {
      withContext(Dispatchers.IO) {
        val model = ensureModel()
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

  private fun ensureModel(): File {
    val directory = File(applicationContext.filesDir, "embedding-models")
    check(directory.isDirectory || directory.mkdirs()) { "embedding model directory unavailable" }
    val file = File(directory, MODEL_FILE)
    if (file.length() >= MIN_MODEL_BYTES) return file
    val temporary = File(directory, "$MODEL_FILE.partial")
    temporary.delete()
    val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
      connectTimeout = 30_000
      readTimeout = 60_000
      instanceFollowRedirects = true
    }
    try {
      check(connection.responseCode in 200..299) { "embedding model download failed" }
      connection.inputStream.use { input ->
        temporary.outputStream().buffered().use { output -> input.copyTo(output) }
      }
      check(temporary.length() >= MIN_MODEL_BYTES) { "embedding model download was incomplete" }
      check(temporary.renameTo(file)) { "failed to store embedding model" }
    } finally {
      connection.disconnect()
      temporary.delete()
    }
    return file
  }

  private companion object {
    const val MODEL_FILE = "embeddinggemma-2-text-270m.litertlm"
    const val MIN_MODEL_BYTES = 100_000_000L
    const val MODEL_URL =
      "https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/main/embeddinggemma-2-text-270m.litertlm"
  }
}
