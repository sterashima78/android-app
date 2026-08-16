package dev.terashima.yomitorirss.core.airuntime

import java.io.File
import org.bytedeco.sentencepiece.IntVector
import org.bytedeco.sentencepiece.SentencePieceProcessor
import org.bytedeco.sentencepiece.Status

internal class LiteRtLmTokenizer(
  modelFile: File,
  cacheDirectory: File,
) : AutoCloseable {
  private val processor = SentencePieceProcessor()

  init {
    val tokenizerFile = LiteRtLmModelSections.extractSentencePieceTokenizer(
      modelFile = modelFile,
      cacheDirectory = cacheDirectory,
    )
    checkStatus(processor.Load(tokenizerFile.absolutePath), "SentencePiece tokenizer の読み込み")
  }

  fun count(text: String): Int {
    val ids = IntVector()
    return try {
      checkStatus(processor.Encode(text, ids), "SentencePiece tokenization")
      check(ids.size() <= Int.MAX_VALUE) { "トークン数が Int の上限を超えました" }
      ids.size().toInt()
    } finally {
      ids.close()
    }
  }

  override fun close() {
    processor.close()
  }

  private fun checkStatus(status: Status, operation: String) {
    try {
      check(status.ok()) { "$operation に失敗しました: ${status.ToString()}" }
    } finally {
      status.close()
    }
  }
}
