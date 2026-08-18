package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilteringTest {
  @Test
  fun `由来を指定しない場合は全蔵書を返す`() {
    val books = LibrarySource.entries.mapIndexed { index, source -> book(index.toString(), source) }

    assertEquals(books, filterLibraryBooksBySource(books, null))
  }

  @Test
  fun `指定したサービス由来の蔵書だけを返す`() {
    val books = listOf(
      book("google", LibrarySource.GOOGLE_PLAY_BOOKS),
      book("kindle", LibrarySource.KINDLE),
      book("audible", LibrarySource.AUDIBLE),
      book("kindle-2", LibrarySource.KINDLE),
    )

    assertEquals(
      listOf("kindle", "kindle-2"),
      filterLibraryBooksBySource(books, LibrarySource.KINDLE).map { it.sourceId },
    )
  }

  @Test
  fun `空の検索語では全蔵書を返す`() {
    val books = listOf(book("one"), book("two"))

    assertEquals(books, filterLibraryBooksByText(books, "  \t "))
  }

  @Test
  fun `タイトルを大文字小文字を区別せず部分一致で検索する`() {
    val books = listOf(
      book("one", title = "Kotlin Coroutines Guide"),
      book("two", title = "Android Architecture"),
    )

    assertEquals(
      listOf("one"),
      filterLibraryBooksByText(books, "coroutines").map { it.sourceId },
    )
  }

  @Test
  fun `著者や出版社やシリーズなどのメタデータも検索する`() {
    val books = listOf(
      book(
        id = "B012345678",
        title = "第一巻",
        authors = listOf("山田 太郎"),
        publisher = "架空出版",
        publishedDate = "2026-08-18",
        series = LibrarySeries(name = "検索シリーズ", position = 1),
        narrators = listOf("佐藤 花子"),
        isbn13 = "9781234567890",
      ),
      book("other", title = "別の本"),
    )

    listOf(
      "山田" to "B012345678",
      "架空出版" to "B012345678",
      "2026-08" to "B012345678",
      "検索シリーズ" to "B012345678",
      "佐藤" to "B012345678",
      "9781234" to "B012345678",
      "b012345" to "B012345678",
    ).forEach { (query, expectedId) ->
      assertEquals(
        query,
        listOf(expectedId),
        filterLibraryBooksByText(books, query).map { it.sourceId },
      )
    }
  }

  @Test
  fun `空白区切りの検索語はすべて一致する蔵書だけを返す`() {
    val books = listOf(
      book(
        id = "match",
        title = "Kotlin入門",
        authors = listOf("山田 太郎"),
      ),
      book(
        id = "title-only",
        title = "Kotlin実践",
        authors = listOf("佐藤 花子"),
      ),
    )

    assertEquals(
      listOf("match"),
      filterLibraryBooksByText(books, "Kotlin 山田").map { it.sourceId },
    )
  }

  private fun book(
    id: String,
    source: LibrarySource = LibrarySource.KINDLE,
    title: String = id,
    authors: List<String> = emptyList(),
    publisher: String? = null,
    publishedDate: String? = null,
    series: LibrarySeries? = null,
    narrators: List<String> = emptyList(),
    isbn13: String? = null,
  ) = LibraryBook(
    source = source,
    sourceId = id,
    title = title,
    authors = authors,
    publisher = publisher,
    publishedDate = publishedDate,
    description = null,
    isbn10 = null,
    isbn13 = isbn13,
    thumbnailUrl = null,
    infoUrl = null,
    series = series,
    narrators = narrators,
  )
}
