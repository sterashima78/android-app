package dev.terashima.yomitorirss.feature.asset.data

import java.io.BufferedReader
import java.io.StringReader
import java.time.LocalDate
import org.junit.Assert.assertEquals
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
    assertEquals(null, rows[0].categoryHint)
  }

  @Test
  fun `CSVの引用符と任意列を読み込める`() {
    val rows = parse(
      """
        date,name,amount,account,category
        2026-08-18,"Asset, B","1,234",Account B,Category B
      """.trimIndent(),
    )

    assertEquals(1, rows.size)
    assertEquals("Asset, B", rows.single().name)
    assertEquals(1234L, rows.single().amount)
    assertEquals("Account B", rows.single().account)
    assertEquals("Category B", rows.single().categoryHint)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `閉じていないCSV引用符は拒否する`() {
    parse("2026-08-18,\"Asset A,100")
  }

  private fun parse(text: String): List<ParsedAssetRow> =
    BufferedReader(StringReader(text)).use(::parseDelimited)
}
