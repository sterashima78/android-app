package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryGroupingTest {
  @Test
  fun `シリーズごとにまとめ巻数順で並べる`() {
    val books = listOf(
      book(id = "3", title = "単独"),
      book(id = "2", title = "第二巻", series = LibrarySeries("テストシリーズ", 2)),
      book(id = "1", title = "第一巻", series = LibrarySeries("テストシリーズ", 1)),
    )

    val groups = groupLibraryBooks(books)

    assertEquals(listOf("テストシリーズ"), groups.series.map { it.name })
    assertEquals(listOf("第一巻", "第二巻"), groups.series.single().books.map { it.title })
    assertEquals(listOf("単独"), groups.ungrouped.map { it.title })
  }

  @Test
  fun `巻数がない本は巻数指定済みの本より後ろに並べる`() {
    val books = listOf(
      book(id = "2", title = "番外編", series = LibrarySeries("シリーズ", null)),
      book(id = "1", title = "本編", series = LibrarySeries("シリーズ", 3)),
    )

    val groups = groupLibraryBooks(books)

    assertEquals(listOf("本編", "番外編"), groups.series.single().books.map { it.title })
  }

  @Test
  fun `末尾の連続数字から同じシリーズを自動でまとめる`() {
    val groups = groupLibraryBooks(
      listOf(
        book(id = "2", title = "サンプル作品 12"),
        book(id = "1", title = "サンプル作品 3"),
      ),
    )

    assertEquals(listOf("サンプル作品"), groups.series.map { it.name })
    assertEquals(listOf("サンプル作品 3", "サンプル作品 12"), groups.series.single().books.map { it.title })
    assertEquals(listOf(3, 12), groups.series.single().books.map { it.series?.position })
  }

  @Test
  fun `半角と全角のカッコ付き数字を巻数として扱う`() {
    assertEquals(
      LibrarySeries("作品名", 1),
      inferLibrarySeriesFromTitle("作品名 (1)"),
    )
    assertEquals(
      LibrarySeries("作品名", 12),
      inferLibrarySeriesFromTitle("作品名（１２）"),
    )
  }

  @Test
  fun `推定シリーズが一冊だけでもグループを作る`() {
    val groups = groupLibraryBooks(
      listOf(
        book(id = "1", title = "Windows 11"),
        book(id = "2", title = "数字のない本"),
      ),
    )

    assertEquals(listOf("Windows"), groups.series.map { it.name })
    assertEquals(listOf("Windows 11"), groups.series.single().books.map { it.title })
    assertEquals(listOf("数字のない本"), groups.ungrouped.map { it.title })
  }

  @Test
  fun `手動設定はタイトルからの自動判定より優先する`() {
    val groups = groupLibraryBooks(
      listOf(
        book(
          id = "1",
          title = "自動シリーズ 1",
          series = LibrarySeries("手動シリーズ", 5),
        ),
        book(id = "2", title = "自動シリーズ 2"),
      ),
    )

    assertEquals(setOf("自動シリーズ", "手動シリーズ"), groups.series.map { it.name }.toSet())
    assertEquals(
      listOf("自動シリーズ 2"),
      groups.series.single { it.name == "自動シリーズ" }.books.map { it.title },
    )
    assertEquals(
      listOf("自動シリーズ 1"),
      groups.series.single { it.name == "手動シリーズ" }.books.map { it.title },
    )
    assertEquals(emptyList<LibraryBook>(), groups.ungrouped)
  }

  @Test
  fun `手動シリーズと同名なら自動判定も同じグループに入る`() {
    val groups = groupLibraryBooks(
      listOf(
        book(
          id = "1",
          title = "作品 1",
          series = LibrarySeries("作品", 1),
        ),
        book(id = "2", title = "作品 2"),
      ),
    )

    assertEquals(listOf("作品"), groups.series.map { it.name })
    assertEquals(listOf("作品 1", "作品 2"), groups.series.single().books.map { it.title })
  }

  @Test
  fun `自動判定から除外した本はグループに戻さない`() {
    val groups = groupLibraryBooks(
      listOf(
        book(id = "1", title = "作品 1", automaticSeriesExcluded = true),
        book(id = "2", title = "作品 2"),
      ),
    )

    assertEquals(listOf("作品"), groups.series.map { it.name })
    assertEquals(listOf("作品 2"), groups.series.single().books.map { it.title })
    assertEquals(listOf("作品 1"), groups.ungrouped.map { it.title })
  }

  @Test
  fun `タイトル全体が数字だけならシリーズ名を推定しない`() {
    assertNull(inferLibrarySeriesFromTitle("1984"))
  }

  private fun book(
    id: String,
    title: String,
    series: LibrarySeries? = null,
    automaticSeriesExcluded: Boolean = false,
  ) = LibraryBook(
    source = LibrarySource.GOOGLE_PLAY_BOOKS,
    sourceId = id,
    title = title,
    authors = emptyList(),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = null,
    series = series,
    automaticSeriesExcluded = automaticSeriesExcluded,
  )
}
