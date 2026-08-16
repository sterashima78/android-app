package dev.terashima.yomitorirss.core.airuntime

import java.io.File
import java.util.PriorityQueue

internal class SentencePieceBpeTokenCounter private constructor(
  private val model: SentencePieceModelDefinition,
) {
  private val piecesByText: Map<String, SentencePieceDefinition> = buildMap(model.pieces.size) {
    model.pieces.forEach { piece ->
      check(put(piece.text, piece) == null) { "SentencePiece tokenizer に重複pieceがあります: ${piece.text}" }
    }
  }
  private val userDefinedPieces = model.pieces
    .asSequence()
    .filter { it.type == SentencePieceType.USER_DEFINED }
    .map { it.text }
    .sortedByDescending { it.toByteArray(Charsets.UTF_8).size }
    .toList()

  init {
    check(model.modelType == BPE_MODEL_TYPE) {
      "Gemma 4 tokenizer はBPEを想定しています: modelType=${model.modelType}"
    }
    check(!model.normalizer.hasPrecompiledCharsMap) {
      "precompiled charsmap を含むSentencePiece normalizerには未対応です"
    }
    check(model.normalizer.name.isBlank() || model.normalizer.name == "identity") {
      "identity以外のSentencePiece normalizerには未対応です: ${model.normalizer.name}"
    }
  }

  fun count(text: String): Int {
    val normalized = normalize(text)
    if (normalized.isEmpty()) return 0

    val nodes = initialSymbols(normalized)
    if (nodes.isEmpty()) return 0

    val reverseMerges = HashMap<String, Pair<String, String>>()
    val agenda = PriorityQueue<BpeCandidate> { first, second ->
      val scoreOrder = second.score.compareTo(first.score)
      if (scoreOrder != 0) scoreOrder else first.left.compareTo(second.left)
    }

    fun addCandidate(leftIndex: Int) {
      if (leftIndex < 0 || leftIndex >= nodes.size) return
      val left = nodes[leftIndex]
      if (!left.active || left.frozen || left.next < 0) return
      val rightIndex = left.next
      val right = nodes[rightIndex]
      if (!right.active || right.frozen) return

      val mergedText = left.text + right.text
      val mergedPiece = piecesByText[mergedText] ?: return
      if (!mergedPiece.type.isMergeable()) return

      agenda.add(
        BpeCandidate(
          score = mergedPiece.score,
          left = leftIndex,
          right = rightIndex,
          mergedText = mergedText,
        ),
      )
      if (mergedPiece.type == SentencePieceType.UNUSED) {
        reverseMerges[mergedText] = left.text to right.text
      }
    }

    for (index in 0 until nodes.lastIndex) addCandidate(index)

    while (agenda.isNotEmpty()) {
      val candidate = agenda.remove()
      val left = nodes[candidate.left]
      val right = nodes[candidate.right]
      if (!left.active || !right.active || left.next != candidate.right) continue
      if (left.text + right.text != candidate.mergedText) continue

      left.text = candidate.mergedText
      left.next = right.next
      if (right.next >= 0) nodes[right.next].previous = candidate.left
      right.active = false
      right.text = ""

      addCandidate(left.previous)
      addCandidate(candidate.left)
    }

    var tokenCount = 0
    var previousWasUnknown = false

    fun consume(pieceText: String) {
      val piece = piecesByText[pieceText]
      val isUnknown = piece == null || piece.type == SentencePieceType.UNKNOWN
      if (isUnknown) {
        if (model.byteFallback) {
          val bytes = pieceText.toByteArray(Charsets.UTF_8).size
          check(tokenCount <= Int.MAX_VALUE - bytes) { "トークン数がIntの上限を超えました" }
          tokenCount += bytes
        } else if (!previousWasUnknown) {
          check(tokenCount < Int.MAX_VALUE) { "トークン数がIntの上限を超えました" }
          tokenCount += 1
        }
      } else {
        check(tokenCount < Int.MAX_VALUE) { "トークン数がIntの上限を超えました" }
        tokenCount += 1
      }
      previousWasUnknown = isUnknown
    }

    fun consumeResegmented(pieceText: String, depth: Int, recurse: (String, Int) -> Unit) {
      val piece = piecesByText[pieceText]
      if (depth > MAX_RESEGMENT_DEPTH || piece?.type != SentencePieceType.UNUSED) {
        consume(pieceText)
        return
      }
      val original = reverseMerges[pieceText]
      if (original == null) {
        consume(pieceText)
        return
      }
      recurse(original.first, depth + 1)
      recurse(original.second, depth + 1)
    }

    lateinit var resegment: (String, Int) -> Unit
    resegment = { pieceText, depth -> consumeResegmented(pieceText, depth, resegment) }

    var index = 0
    while (index >= 0 && index < nodes.size) {
      val node = nodes[index]
      if (node.active) resegment(node.text, 0)
      index = node.next
    }
    return tokenCount
  }

  private fun normalize(text: String): String {
    if (text.isEmpty()) return ""

    val normalized = if (model.normalizer.removeExtraWhitespaces) {
      buildString(text.length) {
        var pendingSpace = false
        var hasContent = false
        text.forEach { character ->
          if (character == ' ') {
            if (hasContent) pendingSpace = true
          } else {
            if (pendingSpace) append(' ')
            append(character)
            pendingSpace = false
            hasContent = true
          }
        }
      }
    } else {
      text
    }

    if (normalized.isEmpty()) return ""

    val withDummyPrefix = if (model.normalizer.addDummyPrefix) {
      if (model.treatWhitespaceAsSuffix) "$normalized " else " $normalized"
    } else {
      normalized
    }

    return if (model.normalizer.escapeWhitespaces) {
      withDummyPrefix.replace(" ", SPACE_SYMBOL)
    } else {
      withDummyPrefix
    }
  }

  private fun initialSymbols(normalized: String): MutableList<BpeNode> {
    val nodes = ArrayList<BpeNode>()
    var offset = 0
    while (offset < normalized.length) {
      val userDefined = userDefinedPieces.firstOrNull { normalized.startsWith(it, offset) }
      val text = if (userDefined != null) {
        userDefined
      } else {
        val codePoint = Character.codePointAt(normalized, offset)
        normalized.substring(offset, offset + Character.charCount(codePoint))
      }
      val index = nodes.size
      nodes += BpeNode(
        text = text,
        previous = index - 1,
        next = -1,
        frozen = userDefined != null,
      )
      if (index > 0) nodes[index - 1].next = index
      offset += text.length
    }
    return nodes
  }

  companion object {
    private const val BPE_MODEL_TYPE = 2
    private const val MAX_RESEGMENT_DEPTH = 100
    private const val SPACE_SYMBOL = "▁"

    fun fromFile(file: File): SentencePieceBpeTokenCounter =
      SentencePieceBpeTokenCounter(SentencePieceModelProtoReader.parse(file))

    internal fun fromDefinition(definition: SentencePieceModelDefinition): SentencePieceBpeTokenCounter =
      SentencePieceBpeTokenCounter(definition)
  }
}

internal data class SentencePieceModelDefinition(
  val pieces: List<SentencePieceDefinition>,
  val modelType: Int,
  val byteFallback: Boolean,
  val treatWhitespaceAsSuffix: Boolean,
  val normalizer: SentencePieceNormalizerDefinition,
)

internal data class SentencePieceDefinition(
  val text: String,
  val score: Float,
  val type: SentencePieceType,
)

internal data class SentencePieceNormalizerDefinition(
  val name: String = "",
  val hasPrecompiledCharsMap: Boolean = false,
  val addDummyPrefix: Boolean = true,
  val removeExtraWhitespaces: Boolean = true,
  val escapeWhitespaces: Boolean = true,
)

internal enum class SentencePieceType(val wireValue: Int) {
  NORMAL(1),
  UNKNOWN(2),
  CONTROL(3),
  USER_DEFINED(4),
  UNUSED(5),
  BYTE(6),
  ;

  fun isMergeable(): Boolean =
    this == NORMAL || this == USER_DEFINED || this == UNUSED

  companion object {
    fun fromWireValue(value: Int): SentencePieceType =
      entries.firstOrNull { it.wireValue == value }
        ?: error("未対応のSentencePiece piece typeです: $value")
  }
}

private data class BpeNode(
  var text: String,
  var previous: Int,
  var next: Int,
  val frozen: Boolean,
  var active: Boolean = true,
)

private data class BpeCandidate(
  val score: Float,
  val left: Int,
  val right: Int,
  val mergedText: String,
)

private object SentencePieceModelProtoReader {
  private const val MAX_PIECES = 1_000_000
  private const val MAX_PIECE_BYTES = 8_000

  fun parse(file: File): SentencePieceModelDefinition {
    check(file.isFile) { "SentencePiece tokenizer modelが見つかりません" }
    val reader = ProtoReader(file.readBytes())
    val pieces = ArrayList<SentencePieceDefinition>()
    var modelType = 1
    var byteFallback = false
    var treatWhitespaceAsSuffix = false
    var normalizer = SentencePieceNormalizerDefinition()

    while (reader.hasRemaining()) {
      val tag = reader.readTag()
      when (tag.fieldNumber) {
        1 -> {
          tag.requireWireType(2)
          check(pieces.size < MAX_PIECES) { "SentencePiece tokenizerのpiece数が上限を超えました" }
          pieces += parsePiece(reader.readSubReader())
        }
        2 -> {
          tag.requireWireType(2)
          val trainer = parseTrainer(reader.readSubReader())
          modelType = trainer.modelType
          byteFallback = trainer.byteFallback
          treatWhitespaceAsSuffix = trainer.treatWhitespaceAsSuffix
        }
        3 -> {
          tag.requireWireType(2)
          normalizer = parseNormalizer(reader.readSubReader())
        }
        else -> reader.skip(tag.wireType)
      }
    }
    check(pieces.isNotEmpty()) { "SentencePiece tokenizerにpieceがありません" }
    return SentencePieceModelDefinition(
      pieces = pieces,
      modelType = modelType,
      byteFallback = byteFallback,
      treatWhitespaceAsSuffix = treatWhitespaceAsSuffix,
      normalizer = normalizer,
    )
  }

  private fun parsePiece(reader: ProtoReader): SentencePieceDefinition {
    var text = ""
    var score = 0f
    var type = SentencePieceType.NORMAL
    while (reader.hasRemaining()) {
      val tag = reader.readTag()
      when (tag.fieldNumber) {
        1 -> {
          tag.requireWireType(2)
          val bytes = reader.readLengthDelimitedBytes()
          check(bytes.size < MAX_PIECE_BYTES) { "SentencePiece pieceが長すぎます" }
          text = bytes.toString(Charsets.UTF_8)
        }
        2 -> {
          tag.requireWireType(5)
          score = Float.fromBits(reader.readFixed32())
        }
        3 -> {
          tag.requireWireType(0)
          type = SentencePieceType.fromWireValue(reader.readVarint().toInt())
        }
        else -> reader.skip(tag.wireType)
      }
    }
    check(text.isNotEmpty()) { "SentencePiece tokenizerに空pieceがあります" }
    check(score.isFinite()) { "SentencePiece tokenizerに不正なscoreがあります" }
    return SentencePieceDefinition(text = text, score = score, type = type)
  }

  private fun parseTrainer(reader: ProtoReader): TrainerDefinition {
    var modelType = 1
    var byteFallback = false
    var treatWhitespaceAsSuffix = false
    while (reader.hasRemaining()) {
      val tag = reader.readTag()
      when (tag.fieldNumber) {
        3 -> {
          tag.requireWireType(0)
          modelType = reader.readVarint().toInt()
        }
        24 -> {
          tag.requireWireType(0)
          treatWhitespaceAsSuffix = reader.readVarint() != 0L
        }
        35 -> {
          tag.requireWireType(0)
          byteFallback = reader.readVarint() != 0L
        }
        else -> reader.skip(tag.wireType)
      }
    }
    return TrainerDefinition(modelType, byteFallback, treatWhitespaceAsSuffix)
  }

  private fun parseNormalizer(reader: ProtoReader): SentencePieceNormalizerDefinition {
    var name = ""
    var hasPrecompiledCharsMap = false
    var addDummyPrefix = true
    var removeExtraWhitespaces = true
    var escapeWhitespaces = true
    while (reader.hasRemaining()) {
      val tag = reader.readTag()
      when (tag.fieldNumber) {
        1 -> {
          tag.requireWireType(2)
          name = reader.readLengthDelimitedBytes().toString(Charsets.UTF_8)
        }
        2 -> {
          tag.requireWireType(2)
          hasPrecompiledCharsMap = reader.skipLengthDelimited() > 0
        }
        3 -> {
          tag.requireWireType(0)
          addDummyPrefix = reader.readVarint() != 0L
        }
        4 -> {
          tag.requireWireType(0)
          removeExtraWhitespaces = reader.readVarint() != 0L
        }
        5 -> {
          tag.requireWireType(0)
          escapeWhitespaces = reader.readVarint() != 0L
        }
        else -> reader.skip(tag.wireType)
      }
    }
    return SentencePieceNormalizerDefinition(
      name = name,
      hasPrecompiledCharsMap = hasPrecompiledCharsMap,
      addDummyPrefix = addDummyPrefix,
      removeExtraWhitespaces = removeExtraWhitespaces,
      escapeWhitespaces = escapeWhitespaces,
    )
  }

  private data class TrainerDefinition(
    val modelType: Int,
    val byteFallback: Boolean,
    val treatWhitespaceAsSuffix: Boolean,
  )
}

private data class ProtoTag(
  val fieldNumber: Int,
  val wireType: Int,
) {
  fun requireWireType(expected: Int) {
    check(wireType == expected) {
      "SentencePiece protobufのwire typeが不正です: field=$fieldNumber actual=$wireType expected=$expected"
    }
  }
}

private class ProtoReader(
  private val bytes: ByteArray,
  private var position: Int = 0,
  private val limit: Int = bytes.size,
) {
  fun hasRemaining(): Boolean = position < limit

  fun readTag(): ProtoTag {
    val raw = readVarint()
    val fieldNumber = (raw ushr 3).toInt()
    val wireType = (raw and 0x07).toInt()
    check(fieldNumber > 0) { "SentencePiece protobufのfield numberが不正です" }
    return ProtoTag(fieldNumber, wireType)
  }

  fun readVarint(): Long {
    var result = 0L
    var shift = 0
    repeat(10) {
      check(position < limit) { "SentencePiece protobufのvarintが途中で終了しました" }
      val value = bytes[position++].toInt() and 0xff
      if (shift == 63) check(value <= 1) { "SentencePiece protobufのvarintが64bitを超えています" }
      result = result or ((value and 0x7f).toLong() shl shift)
      if ((value and 0x80) == 0) return result
      shift += 7
    }
    error("SentencePiece protobufのvarintが長すぎます")
  }

  fun readFixed32(): Int {
    requireAvailable(4)
    val value =
      (bytes[position].toInt() and 0xff) or
        ((bytes[position + 1].toInt() and 0xff) shl 8) or
        ((bytes[position + 2].toInt() and 0xff) shl 16) or
        ((bytes[position + 3].toInt() and 0xff) shl 24)
    position += 4
    return value
  }

  fun readLengthDelimitedBytes(): ByteArray {
    val length = readLength()
    val result = bytes.copyOfRange(position, position + length)
    position += length
    return result
  }

  fun readSubReader(): ProtoReader {
    val length = readLength()
    val start = position
    position += length
    return ProtoReader(bytes, start, start + length)
  }

  fun skipLengthDelimited(): Int {
    val length = readLength()
    position += length
    return length
  }

  fun skip(wireType: Int) {
    when (wireType) {
      0 -> readVarint()
      1 -> skipBytes(8)
      2 -> skipLengthDelimited()
      5 -> skipBytes(4)
      else -> error("未対応のSentencePiece protobuf wire typeです: $wireType")
    }
  }

  private fun readLength(): Int {
    val raw = readVarint()
    check(raw <= Int.MAX_VALUE.toLong()) { "SentencePiece protobufのlengthが大きすぎます" }
    val length = raw.toInt()
    requireAvailable(length)
    return length
  }

  private fun skipBytes(count: Int) {
    requireAvailable(count)
    position += count
  }

  private fun requireAvailable(count: Int) {
    check(count >= 0 && position.toLong() + count <= limit.toLong()) {
      "SentencePiece protobufの読み取り位置が範囲外です"
    }
  }
}
