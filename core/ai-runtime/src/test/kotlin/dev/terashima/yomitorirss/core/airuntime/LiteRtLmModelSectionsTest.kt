package dev.terashima.yomitorirss.core.airuntime

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LiteRtLmModelSectionsTest {
  @Test
  fun `LiteRT-LMヘッダーからSentencePieceセクションを取得する`() {
    withFakeModel(sectionType = SENTENCE_PIECE_SECTION_TYPE) { model, begin, end, _ ->
      val section = LiteRtLmModelSections.sentencePieceTokenizer(model)

      assertEquals(begin.toLong(), section.beginOffset)
      assertEquals(end.toLong(), section.endOffset)
    }
  }

  @Test
  fun `埋め込みSentencePieceをキャッシュへ抽出する`() {
    withFakeModel(sectionType = SENTENCE_PIECE_SECTION_TYPE) { model, _, _, payload ->
      val cacheDirectory = File(model.parentFile, "cache")

      val extracted = LiteRtLmModelSections.extractSentencePieceTokenizer(model, cacheDirectory)

      assertArrayEquals(payload, extracted.readBytes())
      assertEquals(extracted, LiteRtLmModelSections.extractSentencePieceTokenizer(model, cacheDirectory))
    }
  }

  @Test
  fun `SentencePieceセクションがないモデルは失敗する`() {
    withFakeModel(sectionType = GENERIC_BINARY_SECTION_TYPE) { model, _, _, _ ->
      assertThrows(IllegalStateException::class.java) {
        LiteRtLmModelSections.sentencePieceTokenizer(model)
      }
    }
  }

  private fun withFakeModel(
    sectionType: Int,
    block: (model: File, begin: Int, end: Int, payload: ByteArray) -> Unit,
  ) {
    val directory = Files.createTempDirectory("litertlm-tokenizer-test").toFile()
    try {
      val begin = 256
      val payload = byteArrayOf(1, 2, 3, 4, 5, 6)
      val end = begin + payload.size
      val header = buildFlatBufferHeader(sectionType, begin.toLong(), end.toLong())
      val prefix = ByteBuffer.allocate(HEADER_PREFIX_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("LITERTLM".toByteArray(Charsets.US_ASCII))
        putInt(1)
        putInt(6)
        putInt(0)
        putInt(0)
        putLong((HEADER_PREFIX_SIZE + header.size).toLong())
      }.array()
      val bytes = ByteArray(end)
      prefix.copyInto(bytes)
      header.copyInto(bytes, HEADER_PREFIX_SIZE)
      payload.copyInto(bytes, begin)
      val model = File(directory, "fake.litertlm").apply { writeBytes(bytes) }

      block(model, begin, end, payload)
    } finally {
      directory.deleteRecursively()
    }
  }

  private fun buildFlatBufferHeader(sectionType: Int, begin: Long, end: Long): ByteArray {
    val bytes = ByteArray(81)
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    buffer.putInt(0, 12)

    buffer.putShort(4, 8.toShort())
    buffer.putShort(6, 8.toShort())
    buffer.putShort(8, 0.toShort())
    buffer.putShort(10, 4.toShort())
    buffer.putInt(12, 8)
    buffer.putInt(16, 12)

    buffer.putShort(20, 6.toShort())
    buffer.putShort(22, 8.toShort())
    buffer.putShort(24, 4.toShort())
    buffer.putInt(28, 8)
    buffer.putInt(32, 4)

    buffer.putInt(36, 1)
    buffer.putInt(40, 16)

    buffer.putShort(44, 12.toShort())
    buffer.putShort(46, 25.toShort())
    buffer.putShort(48, 0.toShort())
    buffer.putShort(50, 8.toShort())
    buffer.putShort(52, 16.toShort())
    buffer.putShort(54, 24.toShort())
    buffer.putInt(56, 12)
    buffer.putLong(64, begin)
    buffer.putLong(72, end)
    buffer.put(80, sectionType.toByte())

    return bytes
  }

  companion object {
    private const val HEADER_PREFIX_SIZE = 32
    private const val GENERIC_BINARY_SECTION_TYPE = 1
    private const val SENTENCE_PIECE_SECTION_TYPE = 4
  }
}
