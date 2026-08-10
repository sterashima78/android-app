package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmazonLibraryImporterTest {
  private val importer = AmazonLibraryImporter()

  @Test
  fun `Kindle CSV の引用符と改行を解析する`() {
    val csv = """
      ﻿Title,Authors,ASIN,Publisher,Description,ISBN13
      "Book, One","Alice; Bob",B001,Example,"line1
      line2",9781234567890
    """.trimIndent()

    val books = importer.parse(LibrarySource.KINDLE, "kindle.csv", csv.toByteArray())

    assertEquals(1, books.size)
    assertEquals("B001", books.single().sourceId)
    assertEquals("Book, One", books.single().title)
    assertEquals(listOf("Alice", "Bob"), books.single().authors)
    assertEquals("line1\nline2", books.single().description)
    assertEquals("9781234567890", books.single().isbn13)
  }

  @Test
  fun `Audible TSV では著者とナレーターを保持する`() {
    val tsv = "Title\tAuthor\tNarrator\tASIN\tRelease Date\nAudio Book\tWriter\tReader\tAUDIO1\t2026-01-02\n"

    val books = importer.parse(LibrarySource.AUDIBLE, "audible.tsv", tsv.toByteArray())

    assertEquals(1, books.size)
    assertEquals("AUDIO1", books.single().sourceId)
    assertEquals(listOf("Writer", "Reader"), books.single().authors)
    assertEquals("2026-01-02", books.single().publishedDate)
  }

  @Test
  fun `ASIN が無い場合は同じ入力から安定した ID を生成する`() {
    val csv = "Title,Authors,Publication Date\nNo Id Book,Writer,2025-03-01\n"

    val first = importer.parse(LibrarySource.KINDLE, "books.csv", csv.toByteArray()).single()
    val second = importer.parse(LibrarySource.KINDLE, "books.csv", csv.toByteArray()).single()

    assertTrue(first.sourceId.startsWith("derived:"))
    assertEquals(first.sourceId, second.sourceId)
  }

  @Test
  fun `ZIP 内の CSV を解析する`() {
    val bytes = ByteArrayOutputStream().use { output ->
      ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry("audible/library.csv"))
        zip.write("Title,ASIN\nZipped Audio,AUDIO2\n".toByteArray())
        zip.closeEntry()
      }
      output.toByteArray()
    }

    val books = importer.parse(LibrarySource.AUDIBLE, "export.zip", bytes)

    assertEquals(1, books.size)
    assertEquals("Zipped Audio", books.single().title)
    assertEquals("AUDIO2", books.single().sourceId)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `認識できない形式では既存蔵書を置換するための空リストを返さない`() {
    importer.parse(LibrarySource.KINDLE, "unknown.csv", "foo,bar\na,b\n".toByteArray())
  }
}
