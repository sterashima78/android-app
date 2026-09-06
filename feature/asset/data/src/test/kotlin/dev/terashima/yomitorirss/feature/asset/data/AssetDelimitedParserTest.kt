package dev.terashima.yomitorirss.feature.asset.data

import java.io.BufferedReader
import java.io.StringReader
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetDelimitedParserTest {
  @Test
  fun `TSVの必須3列を読み込める`() {
    val rows = parse(
      """
        2026/8/17	Asset A	1000
        2026/8/18	Asset B	2500
      """.trimIndent(),
    )

    assertEquals(2, rows.size)
    assertEquals(LocalDate.of(2026, 8, 17), rows[0].date)
    assertEquals("Asset A", rows[0].name)
    assertEquals(1000L, rows[0].amount)
    assertEquals("", rows[0].account)
  }

  @Test
  fun `円記号とカンマを含む金額を読み込める`() {
    val rows = parse("2026-08-18\tDeposit\t¥12,345\tBank A")

    assertEquals(1, rows.size)
    assertEquals(12345L, rows.single().amount)
  }

  @Test
  fun `2列目と4列目を組み合わせて資産を識別する`() {
    val row = parse("2026-08-18\tDeposit\t12345\tBank A").single()

    assertEquals("Deposit / Bank A", row.name)
    assertEquals("Bank A", row.account)
  }

  @Test
  fun `4列目が空なら2列目だけを資産名にする`() {
    val row = parse("2026-08-18\tCash\t12345\t").single()

    assertEquals("Cash", row.name)
    assertEquals("", row.account)
  }

  @Test
  fun `5列目以降は取り込まない`() {
    val row = parse("2026-08-18\tDeposit\t12345\tBank A\tIgnored").single()

    assertEquals("Deposit / Bank A", row.name)
    assertEquals("Bank A", row.account)
  }

  @Test
  fun `負の金額はスナップショット置換対象から除外する`() {
    val rows = parse(
      """
        2026-08-18	Asset A	1000
        2026-08-18	Asset B	-250
        2026-08-18	Asset C	0
      """.trimIndent(),
    )

    val replacements = buildAssetSnapshotReplacements(rows)

    assertEquals(listOf(1000L, 0L), replacements.getValue(LocalDate.of(2026, 8, 18)).map { it.amount })
  }

  @Test
  fun `負の金額しかない日も既存スナップショットを置換する日付として保持する`() {
    val rows = parse("2026-08-18\tAsset A\t-250")

    val replacements = buildAssetSnapshotReplacements(rows)

    assertTrue(replacements.containsKey(LocalDate.of(2026, 8, 18)))
    assertTrue(replacements.getValue(LocalDate.of(2026, 8, 18)).isEmpty())
  }

  @Test(expected = IllegalStateException::class)
  fun `CSVは受け付けない`() {
    parse("2026-08-18,Asset A,12345")
  }

  private fun parse(text: String): List<ParsedAssetRow> =
    BufferedReader(StringReader(text)).use(::parseTsv)
}
