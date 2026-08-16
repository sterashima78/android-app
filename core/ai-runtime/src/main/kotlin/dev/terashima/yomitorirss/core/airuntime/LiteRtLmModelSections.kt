package dev.terashima.yomitorirss.core.airuntime

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class LiteRtLmSection(
  val beginOffset: Long,
  val endOffset: Long,
) {
  val sizeBytes: Long
    get() = endOffset - beginOffset
}

internal object LiteRtLmModelSections {
  private const val HEADER_PREFIX_SIZE = 32
  private const val SUPPORTED_MAJOR_VERSION = 1
  private const val SENTENCE_PIECE_SECTION_TYPE = 4
  private const val MAX_HEADER_SIZE_BYTES = 1024 * 1024
  private const val MAX_TOKENIZER_SIZE_BYTES = 64L * 1024 * 1024
  private const val COPY_BUFFER_SIZE = 64 * 1024
  private val MAGIC = "LITERTLM".toByteArray(Charsets.US_ASCII)

  fun sentencePieceTokenizer(modelFile: File): LiteRtLmSection {
    check(modelFile.isFile) { "LiteRT-LM モデルが見つかりません" }
    RandomAccessFile(modelFile, "r").use { input ->
      check(input.length() >= HEADER_PREFIX_SIZE) { "LiteRT-LM モデルのヘッダーが不足しています" }

      val prefix = ByteArray(HEADER_PREFIX_SIZE)
      input.readFully(prefix)
      check(prefix.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
        "LiteRT-LM モデルのmagic numberが不正です"
      }

      val prefixBuffer = ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN)
      val majorVersion = prefixBuffer.getInt(8)
      check(majorVersion == SUPPORTED_MAJOR_VERSION) {
        "未対応のLiteRT-LMモデル形式です: major=$majorVersion"
      }

      val headerEndOffset = prefixBuffer.getLong(24)
      val headerSize = headerEndOffset - HEADER_PREFIX_SIZE
      check(headerSize in 1..MAX_HEADER_SIZE_BYTES.toLong()) {
        "LiteRT-LM モデルのヘッダーサイズが不正です: $headerSize"
      }
      check(headerEndOffset <= input.length()) { "LiteRT-LM モデルのヘッダー終端がファイル範囲外です" }

      val headerBytes = ByteArray(headerSize.toInt())
      input.readFully(headerBytes)
      val section = LiteRtLmFlatBufferHeader(headerBytes).findSection(SENTENCE_PIECE_SECTION_TYPE)
        ?: error("LiteRT-LM モデルにSentencePiece tokenizerが含まれていません")

      check(section.beginOffset >= headerEndOffset) { "SentencePiece tokenizer の開始位置が不正です" }
      check(section.endOffset > section.beginOffset) { "SentencePiece tokenizer の範囲が不正です" }
      check(section.endOffset <= input.length()) { "SentencePiece tokenizer がモデルファイル範囲外です" }
      check(section.sizeBytes <= MAX_TOKENIZER_SIZE_BYTES) {
        "SentencePiece tokenizer が想定サイズを超えています: ${section.sizeBytes} bytes"
      }
      return section
    }
  }

  fun extractSentencePieceTokenizer(
    modelFile: File,
    cacheDirectory: File,
  ): File {
    val section = sentencePieceTokenizer(modelFile)
    cacheDirectory.mkdirs()
    check(cacheDirectory.isDirectory) { "SentencePiece tokenizer のcache directoryを作成できません" }
    val destination = File(
      cacheDirectory,
      "tokenizer-${modelFile.length()}-${modelFile.lastModified()}.model",
    )
    if (destination.isFile && destination.length() == section.sizeBytes) return destination

    cacheDirectory.listFiles()
      ?.filter { it.name.startsWith("tokenizer-") && it != destination }
      ?.forEach(File::delete)

    val temporary = File(cacheDirectory, "${destination.name}.part")
    temporary.delete()
    try {
      RandomAccessFile(modelFile, "r").use { input ->
        input.seek(section.beginOffset)
        temporary.outputStream().buffered(COPY_BUFFER_SIZE).use { output ->
          val buffer = ByteArray(COPY_BUFFER_SIZE)
          var remaining = section.sizeBytes
          while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(count > 0) { "SentencePiece tokenizer の抽出中にモデルファイルが終了しました" }
            output.write(buffer, 0, count)
            remaining -= count
          }
        }
      }
      check(temporary.length() == section.sizeBytes) { "SentencePiece tokenizer の抽出サイズが一致しません" }
      if (!temporary.renameTo(destination)) {
        temporary.copyTo(destination, overwrite = true)
        temporary.delete()
      }
      return destination
    } catch (error: Throwable) {
      temporary.delete()
      throw error
    }
  }
}

private class LiteRtLmFlatBufferHeader(bytes: ByteArray) {
  private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

  fun findSection(dataType: Int): LiteRtLmSection? {
    val rootTable = indirect(0)
    val sectionMetadataField = field(rootTable, 1) ?: return null
    val sectionMetadataTable = indirect(sectionMetadataField)
    val objectsField = field(sectionMetadataTable, 0) ?: return null
    val objectsVector = indirect(objectsField)
    val objectCount = intAt(objectsVector)
    check(objectCount >= 0) { "LiteRT-LM section metadata の要素数が不正です" }
    val maxObjectCount = (buffer.limit() - objectsVector - Int.SIZE_BYTES) / Int.SIZE_BYTES
    check(objectCount <= maxObjectCount) { "LiteRT-LM section metadata の要素数がヘッダー範囲を超えています" }

    for (index in 0 until objectCount) {
      val entry = objectsVector + Int.SIZE_BYTES + index * Int.SIZE_BYTES
      val sectionTable = indirect(entry)
      val sectionType = field(sectionTable, 3)?.let(::unsignedByteAt) ?: 0
      if (sectionType != dataType) continue

      val beginOffset = field(sectionTable, 1)?.let(::longAt)
        ?: error("LiteRT-LM section のbegin_offsetがありません")
      val endOffset = field(sectionTable, 2)?.let(::longAt)
        ?: error("LiteRT-LM section のend_offsetがありません")
      return LiteRtLmSection(beginOffset, endOffset)
    }
    return null
  }

  private fun field(table: Int, index: Int): Int? {
    check(index >= 0) { "LiteRT-LM FlatBuffer のfield indexが不正です" }
    requireRange(table, Int.SIZE_BYTES)
    val vtableDistance = intAt(table)
    check(vtableDistance != 0) { "LiteRT-LM FlatBuffer のvtable offsetが不正です" }
    val vtablePosition = table.toLong() - vtableDistance.toLong()
    check(vtablePosition in 0..Int.MAX_VALUE.toLong()) {
      "LiteRT-LM FlatBuffer のvtable参照先が範囲外です"
    }
    val vtable = vtablePosition.toInt()
    requireRange(vtable, Short.SIZE_BYTES * 2)
    val vtableSize = unsignedShortAt(vtable)
    check(vtableSize >= Short.SIZE_BYTES * 2) { "LiteRT-LM FlatBuffer のvtable sizeが不正です" }
    requireRange(vtable, vtableSize)

    val fieldEntryPosition = vtable.toLong() + Short.SIZE_BYTES * 2L + index * Short.SIZE_BYTES.toLong()
    val vtableEnd = vtable.toLong() + vtableSize
    if (fieldEntryPosition + Short.SIZE_BYTES > vtableEnd) return null
    check(fieldEntryPosition <= Int.MAX_VALUE.toLong()) { "LiteRT-LM FlatBuffer のfield参照先が範囲外です" }
    val fieldOffset = unsignedShortAt(fieldEntryPosition.toInt())
    if (fieldOffset == 0) return null

    val position = table.toLong() + fieldOffset
    check(position in 0..Int.MAX_VALUE.toLong()) { "LiteRT-LM FlatBuffer のfield位置が範囲外です" }
    requireRange(position.toInt(), 1)
    return position.toInt()
  }

  private fun indirect(position: Int): Int {
    val offset = intAt(position)
    check(offset > 0) { "LiteRT-LM FlatBuffer のoffsetが不正です" }
    val target = position.toLong() + offset
    check(target >= 0L && target < buffer.limit().toLong()) {
      "LiteRT-LM FlatBuffer の参照先が範囲外です"
    }
    return target.toInt()
  }

  private fun intAt(position: Int): Int {
    requireRange(position, Int.SIZE_BYTES)
    return buffer.getInt(position)
  }

  private fun longAt(position: Int): Long {
    requireRange(position, Long.SIZE_BYTES)
    return buffer.getLong(position)
  }

  private fun unsignedShortAt(position: Int): Int {
    requireRange(position, Short.SIZE_BYTES)
    return buffer.getShort(position).toInt() and 0xffff
  }

  private fun unsignedByteAt(position: Int): Int {
    requireRange(position, 1)
    return buffer.get(position).toInt() and 0xff
  }

  private fun requireRange(position: Int, size: Int) {
    check(position >= 0 && size >= 0 && position.toLong() + size <= buffer.limit()) {
      "LiteRT-LM FlatBuffer の読み取り位置が範囲外です"
    }
  }
}
