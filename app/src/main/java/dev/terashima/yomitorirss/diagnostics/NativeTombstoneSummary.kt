package dev.terashima.yomitorirss.diagnostics

import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val MAX_NATIVE_TOMBSTONE_BYTES = 4 * 1024 * 1024
private const val MAX_TOMBSTONE_THREADS = 512
private const val MAX_BACKTRACE_FRAMES = 32
private const val MAX_PROTO_STRING_BYTES = 32 * 1024

internal data class NativeTombstoneSummary(
  val signalNumber: Int?,
  val signalName: String?,
  val signalCodeName: String?,
  val crashingTid: Int?,
  val crashingThreadName: String?,
  val frames: List<NativeBacktraceFrame>,
)

internal data class NativeBacktraceFrame(
  val relativePc: Long?,
  val functionName: String?,
  val functionOffset: Long?,
  val fileName: String?,
  val buildId: String?,
)

internal fun readNativeTombstoneSummary(inputStream: InputStream): String {
  val bytes = readBoundedBytes(inputStream, MAX_NATIVE_TOMBSTONE_BYTES)
    ?: return "nativeTombstone=too_large(limitBytes=$MAX_NATIVE_TOMBSTONE_BYTES)"
  if (bytes.isEmpty()) return "nativeTombstone=empty"

  return runCatching { parseNativeTombstone(bytes).render() }
    .getOrElse { "nativeTombstone=parse_failed" }
}

internal fun parseNativeTombstone(bytes: ByteArray): NativeTombstoneSummary {
  val reader = TombstoneProtoReader(bytes)
  var crashingTid: Int? = null
  var signalNumber: Int? = null
  var signalName: String? = null
  var signalCodeName: String? = null
  val threads = LinkedHashMap<Int, TombstoneThread>()

  while (reader.hasRemaining()) {
    val tag = reader.readTag()
    when (tag.fieldNumber) {
      6 -> {
        tag.requireWireType(0)
        crashingTid = reader.readVarint().toInt()
      }

      10 -> {
        tag.requireWireType(2)
        val signal = parseSignal(reader.readSubReader())
        signalNumber = signal.number
        signalName = signal.name
        signalCodeName = signal.codeName
      }

      16 -> {
        tag.requireWireType(2)
        if (threads.size < MAX_TOMBSTONE_THREADS) {
          parseThreadEntry(reader.readSubReader())?.let { (tid, thread) -> threads[tid] = thread }
        } else {
          reader.skipLengthDelimited()
        }
      }

      else -> reader.skip(tag.wireType)
    }
  }

  val crashingThread = crashingTid?.let(threads::get)
  return NativeTombstoneSummary(
    signalNumber = signalNumber,
    signalName = signalName,
    signalCodeName = signalCodeName,
    crashingTid = crashingTid,
    crashingThreadName = crashingThread?.name,
    frames = crashingThread?.frames.orEmpty(),
  )
}

internal fun NativeTombstoneSummary.render(): String = buildString {
  appendLine("nativeTombstone:")
  if (signalNumber != null || !signalName.isNullOrBlank()) {
    append("signal=")
    signalNumber?.let(::append)
    signalName?.takeIf(String::isNotBlank)?.let { name ->
      if (signalNumber != null) append(' ')
      append(cleanTombstoneText(name, 128))
    }
    appendLine()
  }
  signalCodeName?.takeIf(String::isNotBlank)?.let { codeName ->
    appendLine("signalCode=${cleanTombstoneText(codeName, 128)}")
  }
  crashingTid?.let { tid ->
    append("thread=$tid")
    crashingThreadName?.takeIf(String::isNotBlank)?.let { name ->
      append(' ')
      append(cleanTombstoneText(name, 128))
    }
    appendLine()
  }
  if (frames.isNotEmpty()) {
    appendLine("backtrace:")
    frames.forEachIndexed { index, frame ->
      append('#')
      append(index.toString().padStart(2, '0'))
      frame.relativePc?.let { relativePc ->
        append(" relPc=0x")
        append(java.lang.Long.toUnsignedString(relativePc, 16))
      }
      frame.fileName?.takeIf(String::isNotBlank)?.let { fileName ->
        append(' ')
        append(displayLibraryName(fileName))
      }
      frame.functionName?.takeIf(String::isNotBlank)?.let { functionName ->
        append(' ')
        append(cleanTombstoneText(functionName, 512))
        frame.functionOffset?.let { offset ->
          append("+")
          append(java.lang.Long.toUnsignedString(offset))
        }
      }
      frame.buildId?.takeIf(String::isNotBlank)?.let { buildId ->
        append(" buildId=")
        append(cleanTombstoneText(buildId, 128))
      }
      appendLine()
    }
  }
}

private fun parseSignal(reader: TombstoneProtoReader): TombstoneSignal {
  var number: Int? = null
  var name: String? = null
  var codeName: String? = null
  while (reader.hasRemaining()) {
    val tag = reader.readTag()
    when (tag.fieldNumber) {
      1 -> {
        tag.requireWireType(0)
        number = reader.readVarint().toInt()
      }

      2 -> {
        tag.requireWireType(2)
        name = reader.readString()
      }

      4 -> {
        tag.requireWireType(2)
        codeName = reader.readString()
      }

      else -> reader.skip(tag.wireType)
    }
  }
  return TombstoneSignal(number, name, codeName)
}

private fun parseThreadEntry(reader: TombstoneProtoReader): Pair<Int, TombstoneThread>? {
  var key: Int? = null
  var thread: TombstoneThread? = null
  while (reader.hasRemaining()) {
    val tag = reader.readTag()
    when (tag.fieldNumber) {
      1 -> {
        tag.requireWireType(0)
        key = reader.readVarint().toInt()
      }

      2 -> {
        tag.requireWireType(2)
        thread = parseThread(reader.readSubReader())
      }

      else -> reader.skip(tag.wireType)
    }
  }
  val resolvedThread = thread ?: return null
  val resolvedKey = key ?: resolvedThread.id ?: return null
  return resolvedKey to resolvedThread
}

private fun parseThread(reader: TombstoneProtoReader): TombstoneThread {
  var id: Int? = null
  var name: String? = null
  val frames = ArrayList<NativeBacktraceFrame>()
  while (reader.hasRemaining()) {
    val tag = reader.readTag()
    when (tag.fieldNumber) {
      1 -> {
        tag.requireWireType(0)
        id = reader.readVarint().toInt()
      }

      2 -> {
        tag.requireWireType(2)
        name = reader.readString()
      }

      4 -> {
        tag.requireWireType(2)
        if (frames.size < MAX_BACKTRACE_FRAMES) {
          frames += parseBacktraceFrame(reader.readSubReader())
        } else {
          reader.skipLengthDelimited()
        }
      }

      else -> reader.skip(tag.wireType)
    }
  }
  return TombstoneThread(id, name, frames)
}

private fun parseBacktraceFrame(reader: TombstoneProtoReader): NativeBacktraceFrame {
  var relativePc: Long? = null
  var functionName: String? = null
  var functionOffset: Long? = null
  var fileName: String? = null
  var buildId: String? = null
  while (reader.hasRemaining()) {
    val tag = reader.readTag()
    when (tag.fieldNumber) {
      1 -> {
        tag.requireWireType(0)
        relativePc = reader.readVarint()
      }

      4 -> {
        tag.requireWireType(2)
        functionName = reader.readString()
      }

      5 -> {
        tag.requireWireType(0)
        functionOffset = reader.readVarint()
      }

      6 -> {
        tag.requireWireType(2)
        fileName = reader.readString()
      }

      8 -> {
        tag.requireWireType(2)
        buildId = reader.readString()
      }

      else -> reader.skip(tag.wireType)
    }
  }
  return NativeBacktraceFrame(relativePc, functionName, functionOffset, fileName, buildId)
}

private data class TombstoneSignal(
  val number: Int?,
  val name: String?,
  val codeName: String?,
)

private data class TombstoneThread(
  val id: Int?,
  val name: String?,
  val frames: List<NativeBacktraceFrame>,
)

private data class TombstoneProtoTag(
  val fieldNumber: Int,
  val wireType: Int,
) {
  fun requireWireType(expected: Int) {
    check(wireType == expected) {
      "Tombstone protobuf wire type mismatch: field=$fieldNumber actual=$wireType expected=$expected"
    }
  }
}

private class TombstoneProtoReader(
  private val bytes: ByteArray,
  private var position: Int = 0,
  private val limit: Int = bytes.size,
) {
  fun hasRemaining(): Boolean = position < limit

  fun readTag(): TombstoneProtoTag {
    val raw = readVarint()
    val fieldNumber = (raw ushr 3).toInt()
    val wireType = (raw and 0x07).toInt()
    check(fieldNumber > 0) { "Invalid tombstone protobuf field number" }
    return TombstoneProtoTag(fieldNumber, wireType)
  }

  fun readVarint(): Long {
    var result = 0L
    var shift = 0
    repeat(10) {
      check(position < limit) { "Truncated tombstone protobuf varint" }
      val value = bytes[position++].toInt() and 0xff
      if (shift == 63) check(value <= 1) { "Tombstone protobuf varint exceeds 64 bits" }
      result = result or ((value and 0x7f).toLong() shl shift)
      if ((value and 0x80) == 0) return result
      shift += 7
    }
    error("Tombstone protobuf varint is too long")
  }

  fun readString(): String {
    val value = readLengthDelimitedBytes()
    check(value.size <= MAX_PROTO_STRING_BYTES) { "Tombstone protobuf string is too long" }
    return value.toString(Charsets.UTF_8)
  }

  fun readSubReader(): TombstoneProtoReader {
    val length = readLength()
    val start = position
    position += length
    return TombstoneProtoReader(bytes, start, start + length)
  }

  fun skipLengthDelimited() {
    val length = readLength()
    position += length
  }

  fun skip(wireType: Int) {
    when (wireType) {
      0 -> readVarint()
      1 -> skipBytes(8)
      2 -> skipLengthDelimited()
      5 -> skipBytes(4)
      else -> error("Unsupported tombstone protobuf wire type: $wireType")
    }
  }

  private fun readLengthDelimitedBytes(): ByteArray {
    val length = readLength()
    val result = bytes.copyOfRange(position, position + length)
    position += length
    return result
  }

  private fun readLength(): Int {
    val raw = readVarint()
    check(raw <= Int.MAX_VALUE.toLong()) { "Tombstone protobuf length is too large" }
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
      "Tombstone protobuf read is out of bounds"
    }
  }
}

private fun readBoundedBytes(inputStream: InputStream, maxBytes: Int): ByteArray? {
  val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
  val buffer = ByteArray(16 * 1024)
  while (true) {
    val remainingWithOverflowByte = maxBytes + 1 - output.size()
    if (remainingWithOverflowByte <= 0) return null
    val read = inputStream.read(buffer, 0, minOf(buffer.size, remainingWithOverflowByte))
    if (read < 0) break
    if (read == 0) continue
    output.write(buffer, 0, read)
    if (output.size() > maxBytes) return null
  }
  return output.toByteArray()
}

private fun displayLibraryName(value: String): String =
  cleanTombstoneText(value.substringAfterLast('/').substringAfterLast('!'), 256)

private fun cleanTombstoneText(value: String, maxLength: Int): String =
  value
    .replace('\r', ' ')
    .replace('\n', ' ')
    .replace('\t', ' ')
    .take(maxLength)
