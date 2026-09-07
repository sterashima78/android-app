package dev.terashima.yomitorirss.diagnostics

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTombstoneSummaryTest {
  @Test
  fun `native tombstone から signal と crashing thread の backtrace を抽出する`() {
    val frame = protoMessage(
      protoVarint(1, 0x3fa97f8L),
      protoString(4, "Object::_predelete()"),
      protoVarint(5, 945L),
      protoString(6, "/data/app/random/split_config.arm64_v8a.apk!libgodot_android.so"),
      protoString(8, "270121fef88100c19643517c328b5868e01d345c"),
    )
    val crashingThread = protoMessage(
      protoVarint(1, 15955L),
      protoString(2, "VkThread"),
      protoBytes(4, frame),
    )
    val threadEntry = protoMessage(
      protoVarint(1, 15955L),
      protoBytes(2, crashingThread),
    )
    val signal = protoMessage(
      protoVarint(1, 7L),
      protoString(2, "SIGBUS"),
      protoString(4, "BUS_ADRALN"),
    )
    val tombstone = protoMessage(
      protoVarint(6, 15955L),
      protoBytes(10, signal),
      protoBytes(16, threadEntry),
      protoString(500, "ignored-oem-field"),
    )

    val summary = parseNativeTombstone(tombstone)

    assertEquals(7, summary.signalNumber)
    assertEquals("SIGBUS", summary.signalName)
    assertEquals("BUS_ADRALN", summary.signalCodeName)
    assertEquals(15955, summary.crashingTid)
    assertEquals("VkThread", summary.crashingThreadName)
    assertEquals(1, summary.frames.size)
    assertEquals(0x3fa97f8L, summary.frames.single().relativePc)
    assertEquals("Object::_predelete()", summary.frames.single().functionName)
    assertEquals(945L, summary.frames.single().functionOffset)
  }

  @Test
  fun `共有レポートでは tombstone のインストールパスをライブラリ名へ縮退する`() {
    val summary = NativeTombstoneSummary(
      signalNumber = 7,
      signalName = "SIGBUS",
      signalCodeName = "BUS_ADRALN",
      crashingTid = 15955,
      crashingThreadName = "VkThread",
      frames = listOf(
        NativeBacktraceFrame(
          relativePc = 0x3fa97f8L,
          functionName = "Object::_predelete()",
          functionOffset = 945L,
          fileName = "/data/app/random/split_config.arm64_v8a.apk!libgodot_android.so",
          buildId = "synthetic-build-id",
        ),
      ),
    )

    val rendered = summary.render()

    assertTrue(rendered.contains("signal=7 SIGBUS"))
    assertTrue(rendered.contains("signalCode=BUS_ADRALN"))
    assertTrue(rendered.contains("thread=15955 VkThread"))
    assertTrue(rendered.contains("libgodot_android.so Object::_predelete()+945"))
    assertFalse(rendered.contains("/data/app/random"))
  }

  @Test
  fun `壊れた tombstone は診断全体を失敗させず parse failed として扱う`() {
    val malformed = byteArrayOf(0x52, 0x05, 0x01)

    val rendered = readNativeTombstoneSummary(ByteArrayInputStream(malformed))

    assertEquals("nativeTombstone=parse_failed", rendered)
  }

  private fun protoMessage(vararg fields: ByteArray): ByteArray =
    fields.fold(ByteArray(0)) { result, field -> result + field }

  private fun protoVarint(fieldNumber: Int, value: Long): ByteArray =
    encodeVarint((fieldNumber.toLong() shl 3) or 0L) + encodeVarint(value)

  private fun protoString(fieldNumber: Int, value: String): ByteArray =
    protoBytes(fieldNumber, value.toByteArray(Charsets.UTF_8))

  private fun protoBytes(fieldNumber: Int, value: ByteArray): ByteArray =
    encodeVarint((fieldNumber.toLong() shl 3) or 2L) + encodeVarint(value.size.toLong()) + value

  private fun encodeVarint(value: Long): ByteArray {
    var remaining = value
    val bytes = ArrayList<Byte>()
    while (true) {
      if ((remaining and -128L) == 0L) {
        bytes += remaining.toByte()
        return bytes.toByteArray()
      }
      bytes += ((remaining and 0x7fL) or 0x80L).toByte()
      remaining = remaining ushr 7
    }
  }
}
