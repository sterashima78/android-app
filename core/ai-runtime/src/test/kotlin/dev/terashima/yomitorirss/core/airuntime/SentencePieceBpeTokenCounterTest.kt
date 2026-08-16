package dev.terashima.yomitorirss.core.airuntime

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SentencePieceBpeTokenCounterTest {
  @Test
  fun `scoreの高いBPE pieceへ結合してtoken数を数える`() {
    val counter = counter(
      piece("<unk>", type = SentencePieceType.UNKNOWN),
      piece("a"),
      piece("b"),
      piece("ab", score = 10f),
    )

    assertEquals(1, counter.count("ab"))
  }

  @Test
  fun `byte fallbackでは未知文字をUTF8 byte数で数える`() {
    val counter = counter(
      piece("<unk>", type = SentencePieceType.UNKNOWN),
      byteFallback = true,
    )

    assertEquals(2, counter.count("é"))
    assertEquals(3, counter.count("あ"))
  }

  @Test
  fun `byte fallbackなしでは連続する未知pieceを一つのunknown tokenとして数える`() {
    val counter = counter(
      piece("<unk>", type = SentencePieceType.UNKNOWN),
      byteFallback = false,
    )

    assertEquals(1, counter.count("éあ"))
  }

  @Test
  fun `dummy prefixとspace escapeをBPEへ反映する`() {
    val counter = counter(
      piece("<unk>", type = SentencePieceType.UNKNOWN),
      piece("▁"),
      piece("a"),
      piece("▁a", score = 10f),
      normalizer = SentencePieceNormalizerDefinition(
        name = "identity",
        addDummyPrefix = true,
        removeExtraWhitespaces = false,
        escapeWhitespaces = true,
      ),
    )

    assertEquals(1, counter.count("a"))
  }

  @Test
  fun `user defined pieceは分割や再結合をせず一tokenとして扱う`() {
    val counter = counter(
      piece("<unk>", type = SentencePieceType.UNKNOWN),
      piece("<tag>", score = 100f, type = SentencePieceType.USER_DEFINED),
      piece("<"),
      piece("tag"),
      piece(">"),
    )

    assertEquals(1, counter.count("<tag>"))
  }

  @Test
  fun `precompiled charsmapを含むnormalizerは近似せず失敗する`() {
    assertThrows(IllegalStateException::class.java) {
      counter(
        piece("<unk>", type = SentencePieceType.UNKNOWN),
        normalizer = SentencePieceNormalizerDefinition(
          name = "nmt_nfkc",
          hasPrecompiledCharsMap = true,
        ),
      )
    }
  }

  @Test
  fun `SentencePiece ModelProtoからBPE設定とpieceを読み込む`() {
    val modelBytes = modelProto(
      pieces = listOf(
        ProtoPiece("<unk>", 0f, SentencePieceType.UNKNOWN),
        ProtoPiece("a", 0f, SentencePieceType.NORMAL),
        ProtoPiece("b", 0f, SentencePieceType.NORMAL),
        ProtoPiece("ab", 10f, SentencePieceType.NORMAL),
      ),
      byteFallback = true,
    )
    val modelFile = Files.createTempFile("sentencepiece", ".model").toFile()
    try {
      modelFile.writeBytes(modelBytes)
      val counter = SentencePieceBpeTokenCounter.fromFile(modelFile)

      assertEquals(1, counter.count("ab"))
      assertEquals(3, counter.count("あ"))
    } finally {
      modelFile.delete()
    }
  }

  private fun counter(
    vararg pieces: SentencePieceDefinition,
    byteFallback: Boolean = false,
    normalizer: SentencePieceNormalizerDefinition = SentencePieceNormalizerDefinition(
      name = "identity",
      addDummyPrefix = false,
      removeExtraWhitespaces = false,
      escapeWhitespaces = false,
    ),
  ): SentencePieceBpeTokenCounter = SentencePieceBpeTokenCounter.fromDefinition(
    SentencePieceModelDefinition(
      pieces = pieces.toList(),
      modelType = 2,
      byteFallback = byteFallback,
      treatWhitespaceAsSuffix = false,
      normalizer = normalizer,
    ),
  )

  private fun piece(
    text: String,
    score: Float = 0f,
    type: SentencePieceType = SentencePieceType.NORMAL,
  ) = SentencePieceDefinition(text = text, score = score, type = type)

  private fun modelProto(
    pieces: List<ProtoPiece>,
    byteFallback: Boolean,
  ): ByteArray = proto {
    pieces.forEach { value ->
      message(1) {
        string(1, value.text)
        fixed32(2, value.score.toRawBits())
        varint(3, value.type.wireValue.toLong())
      }
    }
    message(2) {
      varint(3, 2)
      varint(35, if (byteFallback) 1 else 0)
    }
    message(3) {
      string(1, "identity")
      varint(3, 0)
      varint(4, 0)
      varint(5, 0)
    }
  }

  private fun proto(block: ProtoWriter.() -> Unit): ByteArray =
    ProtoWriter().apply(block).toByteArray()

  private data class ProtoPiece(
    val text: String,
    val score: Float,
    val type: SentencePieceType,
  )

  private class ProtoWriter {
    private val output = ByteArrayOutputStream()

    fun string(field: Int, value: String) {
      val bytes = value.toByteArray(Charsets.UTF_8)
      tag(field, 2)
      rawVarint(bytes.size.toLong())
      output.write(bytes)
    }

    fun varint(field: Int, value: Long) {
      tag(field, 0)
      rawVarint(value)
    }

    fun fixed32(field: Int, value: Int) {
      tag(field, 5)
      output.write(value and 0xff)
      output.write((value ushr 8) and 0xff)
      output.write((value ushr 16) and 0xff)
      output.write((value ushr 24) and 0xff)
    }

    fun message(field: Int, block: ProtoWriter.() -> Unit) {
      val bytes = ProtoWriter().apply(block).toByteArray()
      tag(field, 2)
      rawVarint(bytes.size.toLong())
      output.write(bytes)
    }

    fun toByteArray(): ByteArray = output.toByteArray()

    private fun tag(field: Int, wireType: Int) {
      rawVarint(((field shl 3) or wireType).toLong())
    }

    private fun rawVarint(value: Long) {
      var remaining = value
      do {
        var byte = (remaining and 0x7f).toInt()
        remaining = remaining ushr 7
        if (remaining != 0L) byte = byte or 0x80
        output.write(byte)
      } while (remaining != 0L)
    }
  }
}
