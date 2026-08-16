package dev.terashima.yomitorirss.core.airuntime

import java.io.File

internal class LiteRtLmTokenizer(
  modelFile: File,
  cacheDirectory: File,
) : AutoCloseable {
  private val counter: SentencePieceBpeTokenCounter

  init {
    val tokenizerFile = LiteRtLmModelSections.extractSentencePieceTokenizer(
      modelFile = modelFile,
      cacheDirectory = cacheDirectory,
    )
    counter = SentencePieceBpeTokenCounter.fromFile(tokenizerFile)
  }

  fun count(text: String): Int = counter.count(text)

  override fun close() = Unit
}
